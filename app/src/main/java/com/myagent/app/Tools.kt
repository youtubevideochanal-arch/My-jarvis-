package com.myagent.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chhote Jarvis-type tools: reminder, device info, HTTP request, link kholna, clipboard, long-term memory. */
object Tools {

    fun remind(minutes: Int, text: String): String {
        val m = minutes.coerceIn(1, 7 * 24 * 60)
        val ctx = Hub.app
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("text", text.take(200))
        val id = (System.currentTimeMillis() % 100000).toInt()
        val pi = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + m * 60000L, pi)
        return "reminder set: $m minute baad - " + text.take(60)
    }

    fun deviceInfo(): String {
        val ctx = Hub.app
        val sb = StringBuilder()
        sb.append("Time: ").append(SimpleDateFormat("EEE dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())).append('\n')
        try {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            if (lvl >= 0) sb.append("Battery: ").append(lvl * 100 / scale).append("%").append(if (charging) " (charging)" else "").append('\n')
        } catch (e: Exception) { }
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            sb.append("Free storage: ").append(st.availableBytes / 1048576).append(" MB\n")
        } catch (e: Exception) { }
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            sb.append("Internet: ").append(if (cm.activeNetwork != null) "connected" else "offline").append('\n')
        } catch (e: Exception) { }
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (Android ").append(Build.VERSION.RELEASE).append(")")
        return sb.toString()
    }

    fun httpRequest(method: String, url: String, headersJson0: String, body0: String): String {
        val miss = Extras.missingKeys(url + headersJson0 + body0)
        if (miss.isNotEmpty()) return "ERROR: key nahi mili: " + miss.joinToString(",") + ". User se Settings -> Extra APIs me is naam se key daalne ko kaho (API ka signup link bhi batao)."
        val headersJson = Extras.resolve(headersJson0)
        val body = Extras.resolve(body0)
        val u = Extras.resolve(url).trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "ERROR: sirf http/https URL"
        val mth = method.trim().uppercase().ifBlank { "GET" }
        if (mth !in listOf("GET", "POST", "PUT", "DELETE", "HEAD")) return "ERROR: method GET/POST/PUT/DELETE/HEAD"
        var conn: HttpURLConnection? = null
        try {
            val c = URL(u).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = mth
            c.connectTimeout = 12000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "MyAgent/4.0")
            if (headersJson.isNotBlank()) {
                val h = JSONObject(headersJson)
                for (k in h.keys()) c.setRequestProperty(k, h.getString(k))
            }
            if (body.isNotEmpty() && mth != "GET" && mth != "HEAD") {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { r ->
                val buf = CharArray(4096)
                val sb = StringBuilder()
                while (sb.length < 6000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            } ?: ""
            return "HTTP $code  content-type=" + (c.contentType ?: "?") + "\n" + text.take(3000)
        } catch (e: Exception) {
            return "ERROR: " + e.javaClass.simpleName + " " + (e.message ?: "")
        } finally {
            conn?.disconnect()
        }
    }

    fun openUrl(url: String): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "ERROR: sirf http/https link"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(u))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            val svc = PhoneAccessibilityService.instance
            if (svc != null) svc.startActivity(i) else Hub.app.startActivity(i)
            "opened $u"
        } catch (e: Exception) {
            "ERROR: link nahi khula: " + (e.message ?: "")
        }
    }

    private val OK_SCHEMES = setOf("http", "https", "tel", "sms", "smsto", "mailto", "geo", "market", "whatsapp", "tg", "intent-none", "vnd.youtube", "instagram", "twitter", "upi")

    /** Koi bhi link/scheme kholo: tel: sirf dial screen tak (call khud nahi lagta). upi: bhi sirf kholta hai, payment user karega. */
    fun viewUri(uri: String, pkg: String): String {
        val u = uri.trim()
        val scheme = u.substringBefore(':').lowercase()
        if (scheme !in OK_SCHEMES) return "ERROR: scheme '$scheme' allowed nahi"
        val action = if (scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW
        val i = Intent(action, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg.isNotBlank()) {
            val p = Apps.find(Hub.app, pkg)?.pkg ?: pkg.trim()
            i.setPackage(p)
        }
        return try {
            Hub.app.startActivity(i)
            "opened $u"
        } catch (e: Exception) {
            "ERROR: nahi khula: " + (e.message ?: "")
        }
    }

    /** Share-sheet ya seedha kisi app me text bhejne ka screen kholo (bhejna user/agent UI se confirm hota hai). */
    fun share(text: String, app: String): String {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (app.isNotBlank()) {
            val a = Apps.find(Hub.app, app) ?: return "ERROR: '$app' app nahi mili"
            i.setPackage(a.pkg)
        }
        return try {
            if (app.isBlank()) Hub.app.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else Hub.app.startActivity(i)
            "share screen khuli" + (if (app.isNotBlank()) " ($app)" else "") + " - ab UI se aakhri Send/Post dabana hai (risky:true)"
        } catch (e: Exception) {
            "ERROR: share nahi hua: " + (e.message ?: "")
        }
    }

    fun copyText(text: String): String {
        Handler(Looper.getMainLooper()).post {
            try {
                val cm = Hub.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("agent", text))
            } catch (e: Exception) { }
        }
        return "clipboard me copy ho gaya (" + text.length + " chars)"
    }
}

/** Lambi yaaddasht: user ki pasand/zaroori baatein jo har task me saath rehti hain. */
object Memo {
    private fun load(): JSONObject = try { JSONObject(Hub.prefs.str("memos", "{}")) } catch (e: Exception) { JSONObject() }

    fun save(key: String, value: String): String {
        if (key.isBlank()) return "ERROR: key khaali"
        val j = load()
        j.put(key.trim().take(40), value.take(300))
        while (j.length() > 30) {
            val k = j.keys().next()
            j.remove(k)
        }
        Hub.prefs.putStr("memos", j.toString())
        return "yaad rakh liya: " + key.trim()
    }

    fun dump(): String {
        val j = load()
        if (j.length() == 0) return "(khaali)"
        val sb = StringBuilder()
        for (k in j.keys()) sb.append("- ").append(k).append(": ").append(j.getString(k)).append('\n')
        return sb.toString().trimEnd()
    }

    fun clear() { Hub.prefs.putStr("memos", "{}") }

    /** Seedhi keyword-search (BM25 jaisa halka): yaaddasht me matching lines. */
    fun search(q: String): String {
        val terms = q.lowercase().split(Regex("[^a-z0-9\\u0900-\\u097F]+")).filter { it.length >= 2 }
        val j = load()
        val hits = ArrayList<Pair<Int, String>>()
        for (k in j.keys()) {
            val line = k + ": " + j.getString(k)
            val sc = terms.count { line.lowercase().contains(it) }
            if (sc > 0) hits.add(Pair(sc, line))
        }
        if (hits.isEmpty()) return "(yaaddasht me kuch nahi mila)"
        return hits.sortedByDescending { it.first }.take(6).joinToString("\n") { "- " + it.second }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Reminder"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("my_agent_remind", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val n = NotificationCompat.Builder(context, "my_agent_remind")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("⏰ Reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try { nm.notify((System.currentTimeMillis() % 100000).toInt(), n) } catch (e: Exception) { }
    }
}
