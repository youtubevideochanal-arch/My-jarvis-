package com.myagent.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Scheduled kaam aur "watch" (OpenJarvis ke morning-digest aur monitor agents jaisa):
 *  - task: samay par agent ko kaam do (jaise roz subah 8 baje digest). Repeat ho sakta hai.
 *  - watch: bina AI token kharch kiye har N minute price dekho; shart poori ho to notification + awaaz.
 */
object Sched {
    private fun load(): JSONArray = try { JSONArray(Hub.prefs.str("sched", "[]")) } catch (e: Exception) { JSONArray() }
    private fun save(a: JSONArray) { Hub.prefs.putStr("sched", a.toString()) }

    private fun arm(ctx: Context, id: Int, atMs: Long) {
        val i = Intent(ctx, SchedReceiver::class.java).putExtra("id", id)
        val pi = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }

    private fun nextId(a: JSONArray): Int {
        var m = 100
        for (i in 0 until a.length()) m = maxOf(m, a.getJSONObject(i).optInt("id") + 1)
        return m
    }

    /** "HH:mm" ka agla samay (aaj ya kal). */
    fun nextAt(hhmm: String): Long {
        val p = hhmm.trim().split(':')
        val h = p.getOrNull(0)?.toIntOrNull() ?: 8
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, h.coerceIn(0, 23)); c.set(Calendar.MINUTE, m.coerceIn(0, 59)); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 5000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun addTask(text: String, at: String, inMinutes: Int, repeatMin: Int): String {
        if (text.isBlank()) return "ERROR: kaam ka text khaali"
        val a = load()
        val id = nextId(a)
        val whenMs = if (at.isNotBlank()) nextAt(at) else System.currentTimeMillis() + inMinutes.coerceIn(1, 60 * 24 * 30) * 60000L
        a.put(JSONObject().put("id", id).put("kind", "task").put("text", text.take(400)).put("at", whenMs).put("repeat", repeatMin.coerceIn(0, 60 * 24 * 30)))
        save(a)
        arm(Hub.app, id, whenMs)
        return "scheduled #$id: '" + text.take(50) + "' " + java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(whenMs)) +
            (if (repeatMin > 0) " (har $repeatMin min)" else "")
    }

    fun addWatch(symbol: String, op: String, level: Double, everyMin: Int, note: String): String {
        if (symbol.isBlank()) return "ERROR: symbol khaali"
        val o = if (op.lowercase() in listOf("below", "<", "neeche")) "below" else "above"
        val a = load()
        val id = nextId(a)
        val em = everyMin.coerceIn(5, 1440)
        val whenMs = System.currentTimeMillis() + em * 60000L
        a.put(JSONObject().put("id", id).put("kind", "watch").put("symbol", symbol.trim()).put("op", o).put("level", level).put("repeat", em).put("at", whenMs).put("text", note.take(120)))
        save(a)
        arm(Hub.app, id, whenMs)
        return "watch #$id: ${symbol.uppercase()} $o $level (har $em min check, token kharch nahi)"
    }

    fun list(): String {
        val a = load()
        if (a.length() == 0) return "(koi scheduled kaam/watch nahi)"
        val sb = StringBuilder()
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            sb.append('#').append(j.optInt("id")).append(' ').append(j.optString("kind")).append(": ")
            if (j.optString("kind") == "watch") sb.append(j.optString("symbol")).append(' ').append(j.optString("op")).append(' ').append(j.optDouble("level"))
            else sb.append(j.optString("text").take(60))
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    fun cancel(id: Int): String {
        val a = load()
        val out = JSONArray()
        var found = false
        for (i in 0 until a.length()) { val j = a.getJSONObject(i); if (j.optInt("id") == id) found = true else out.put(j) }
        save(out)
        try {
            val pi = PendingIntent.getBroadcast(Hub.app, id, Intent(Hub.app, SchedReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (pi != null) (Hub.app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        } catch (e: Exception) { }
        return if (found) "cancelled #$id" else "ERROR: #$id nahi mila"
    }

    /** Phone restart ke baad saare alarm dobara lagao. */
    fun rearmAll(ctx: Context) {
        val a = load()
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            var at = j.optLong("at")
            if (at < System.currentTimeMillis()) at = System.currentTimeMillis() + 60000
            arm(ctx, j.optInt("id"), at)
        }
    }

    fun notify(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("my_agent_remind", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(ctx, "my_agent_remind").setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        try { nm.notify((System.currentTimeMillis() % 100000).toInt(), n) } catch (e: Exception) { }
    }

    fun fire(ctx: Context, id: Int) {
        val a = load()
        var job: JSONObject? = null
        for (i in 0 until a.length()) if (a.getJSONObject(i).optInt("id") == id) job = a.getJSONObject(i)
        val j = job ?: return
        val repeat = j.optInt("repeat")
        var done = repeat <= 0
        if (j.optString("kind") == "watch") {
            val price = try { MarketData.fetch(j.optString("symbol"), "1d").last().c } catch (e: Exception) { Double.NaN }
            if (!price.isNaN()) {
                val hit = if (j.optString("op") == "below") price <= j.optDouble("level") else price >= j.optDouble("level")
                if (hit) {
                    val msg = j.optString("symbol").uppercase() + " " + j.optString("op") + " " + j.optDouble("level") + " ho gaya - abhi " + String.format("%.2f", price) + (if (j.optString("text").isNotBlank()) " (" + j.optString("text") + ")" else "")
                    notify(ctx, "📈 Watch alert", msg)
                    Hub.log("📈 $msg")
                    Hub.voice.speak(msg)
                    done = true
                }
            }
        } else {
            val text = j.optString("text")
            if (Hub.prefs.masterOn) {
                Hub.mainHandler.post { Hub.log("⏰ Scheduled: $text"); Hub.engine.submit("[SCHEDULED] $text") }
            } else {
                notify(ctx, "⏰ Scheduled kaam", text + " (agent OFF tha - ON karke bolo)")
            }
        }
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val x = a.getJSONObject(i)
            if (x.optInt("id") == id) {
                if (!done) {
                    val nxt = if (j.optString("kind") == "task" && repeat >= 1440 && repeat % 1440 == 0) System.currentTimeMillis() + repeat * 60000L else System.currentTimeMillis() + repeat * 60000L
                    x.put("at", nxt)
                    out.put(x)
                    arm(ctx, id, nxt)
                }
            } else out.put(x)
        }
        save(out)
    }
}

class SchedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        val pr = goAsync()
        Thread {
            try { Sched.fire(context.applicationContext, id) } catch (e: Exception) { } finally { pr.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try { Sched.rearmAll(context.applicationContext) } catch (e: Exception) { }
        try { if (Hub.prefs.masterOn) Hub.startAgentService() } catch (e: Exception) { }
    }
}
