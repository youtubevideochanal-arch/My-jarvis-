package com.myagent.app

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.AlarmClock
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Computer-agent" jaisi taakat, phone ke andar: shell (Termux), files, app info/uninstall, clipboard,
 * volume, torch, alarm, call/sms (sirf khol deta hai - bhejna/lagana user dabata hai), system stats.
 * Ye sab AGENT ke apne actions hain - screen par kuch dikhane ke liye nahi; AI inhe khud chalata hai.
 */
object PhoneTools {

    /** null = ye action meri nahi. */
    fun handle(ctx: Context, act: String, a: JSONObject): String? {
        return try {
            when (act) {
                "shell" -> shell(ctx, a.optString("cmd"))
                "file_list" -> fileList(a.optString("path"))
                "file_read" -> fileRead(a.optString("path"))
                "file_write" -> fileWrite(a.optString("path"), a.optString("content"), a.optBoolean("append", false))
                "file_delete" -> fileDelete(a.optString("path"))
                "file_move" -> fileMove(a.optString("from"), a.optString("to"))
                "file_find" -> fileFind(a.optString("path"), a.optString("query"))
                "app_info" -> appInfo(ctx, a.optString("app"))
                "uninstall_app" -> uninstall(ctx, a.optString("app"))
                "clipboard_get" -> clipboard(ctx)
                "volume" -> volume(ctx, a.optInt("level", -1), a.optString("stream", "music"))
                "torch" -> torch(ctx, a.optBoolean("on", true))
                "alarm" -> alarm(ctx, a.optInt("hour", 7), a.optInt("min", 0), a.optString("label", "Alarm"))
                "call" -> dial(ctx, a.optString("number"))
                "sms" -> sms(ctx, a.optString("number"), a.optString("text"))
                "sys_stats" -> sysStats(ctx)
                "goal_set" -> Goals.set(a.optString("text"), a.optDouble("target", 0.0), a.optString("unit"), a.optInt("hours", 24), a.optInt("check_min", 60))
                "goal_update" -> Goals.update(a.optInt("id", -1), a.optDouble("progress", 0.0), a.optString("note"))
                "goal_list" -> Goals.list()
                "goal_done" -> Goals.finish(a.optInt("id", -1))
                else -> null
            }
        } catch (e: Exception) {
            "ERROR: " + e.javaClass.simpleName + " " + (e.message ?: "")
        }
    }

    val ACTS = setOf(
        "shell", "file_list", "file_read", "file_write", "file_delete", "file_move", "file_find",
        "app_info", "uninstall_app", "clipboard_get", "volume", "torch", "alarm", "call", "sms", "sys_stats",
        "goal_set", "goal_update", "goal_list", "goal_done"
    )

    /** Hamesha user se poochhne wale (data/ app khatam). */
    fun alwaysRisky(act: String, a: JSONObject): Boolean {
        if (act == "uninstall_app" || act == "file_delete") return true
        if (act == "file_write" && File(resolve(a.optString("path"))).exists()) return true
        if (act == "file_move" && File(resolve(a.optString("to"))).exists()) return true
        if (act == "shell") return DANGER.containsMatchIn(a.optString("cmd"))
        return false
    }

    private val DANGER = Regex("\\brm\\b|\\brmdir\\b|\\bmkfs|\\bdd\\b|\\bchmod\\b|\\bchown\\b|\\bsu\\b|\\bsudo\\b|\\bpkg (uninstall|remove)|\\bkill\\b|>\\s*/|\\bcurl\\b.*\\|\\s*(ba)?sh|\\bwget\\b.*\\|\\s*(ba)?sh|\\bmv\\b|\\bformat\\b", RegexOption.IGNORE_CASE)

    // ------------------------------------------------------------ shell
    private fun shell(ctx: Context, cmd: String): String {
        if (cmd.isBlank()) return "ERROR: cmd khaali"
        return CodeRunner.run(ctx, "bash", "cmd.sh", cmd)
    }

    // ------------------------------------------------------------ files
    private fun root(): String = try { Environment.getExternalStorageDirectory().absolutePath } catch (e: Exception) { "/sdcard" }

    private fun resolve(p: String): String {
        var s = p.trim()
        if (s.isEmpty()) return root()
        if (s.startsWith("~/")) s = root() + "/" + s.substring(2)
        if (!s.startsWith("/")) s = root() + "/" + s
        return s
    }

    private fun noAccess(f: File): String? {
        if (Build.VERSION.SDK_INT >= 30 && f.absolutePath.startsWith(root()) && !Environment.isExternalStorageManager()) {
            return "ERROR: Files ka access nahi. Permissions screen -> 'Saari files ka access' ON karo (ek baar)."
        }
        return null
    }

    private fun fileList(p: String): String {
        val f = File(resolve(p))
        noAccess(f)?.let { return it }
        if (!f.exists()) return "ERROR: nahi mila: " + f.path
        if (f.isFile) return f.path + "  " + f.length() + " bytes"
        val arr = f.listFiles() ?: return "ERROR: folder padh nahi paya (permission?)"
        val sb = StringBuilder(f.path + " (" + arr.size + " items)\n")
        for (x in arr.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).take(80)) {
            sb.append(if (x.isDirectory) "📁 " else "📄 ").append(x.name)
            if (x.isFile) sb.append("  ").append(x.length() / 1024).append(" KB")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun fileRead(p: String): String {
        val f = File(resolve(p))
        noAccess(f)?.let { return it }
        if (!f.isFile) return "ERROR: file nahi mili: " + f.path
        if (f.length() > 600_000) return "ERROR: file bahut badi (" + f.length() / 1024 + " KB); shell se head/grep use karo"
        return f.readText().take(6000)
    }

    private fun fileWrite(p: String, content: String, append: Boolean): String {
        val f = File(resolve(p))
        noAccess(f)?.let { return it }
        f.parentFile?.mkdirs()
        if (append) f.appendText(content) else f.writeText(content)
        return "likh diya: " + f.path + " (" + f.length() + " bytes)"
    }

    private fun fileDelete(p: String): String {
        val f = File(resolve(p))
        noAccess(f)?.let { return it }
        if (!f.exists()) return "ERROR: nahi mila: " + f.path
        if (f.absolutePath == root() || f.absolutePath.length < 12) return "ERROR: ye bahut bada/zaroori folder hai, nahi hataunga"
        val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
        return if (ok) "hata diya: " + f.path else "ERROR: hata nahi paya"
    }

    private fun fileMove(from: String, to: String): String {
        val a = File(resolve(from)); val b = File(resolve(to))
        noAccess(a)?.let { return it }
        if (!a.exists()) return "ERROR: nahi mila: " + a.path
        b.parentFile?.mkdirs()
        if (a.renameTo(b)) return "moved: " + a.path + " -> " + b.path
        a.copyRecursively(b, true)
        a.deleteRecursively()
        return "moved (copy+delete): " + b.path
    }

    private fun fileFind(p: String, q: String): String {
        val base = File(resolve(p))
        noAccess(base)?.let { return it }
        if (q.isBlank()) return "ERROR: query khaali"
        val out = ArrayList<String>()
        val ql = q.lowercase()
        base.walkTopDown().maxDepth(6).onEnter { !it.name.startsWith(".") }.forEach {
            if (out.size < 40 && it.name.lowercase().contains(ql)) out.add(it.path)
        }
        return if (out.isEmpty()) "kuch nahi mila" else out.joinToString("\n")
    }

    // ------------------------------------------------------------ apps
    private fun appInfo(ctx: Context, q: String): String {
        val ai = Apps.find(ctx, q) ?: return "ERROR: app nahi mili: $q"
        val pm = ctx.packageManager
        val pi = pm.getPackageInfo(ai.pkg, PackageManager.GET_PERMISSIONS)
        val sb = StringBuilder()
        val f = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sb.append(ai.label).append(" (").append(ai.pkg).append(")\n")
        sb.append("Version: ").append(pi.versionName ?: "?").append('\n')
        sb.append("Installed: ").append(f.format(Date(pi.firstInstallTime))).append("  Updated: ").append(f.format(Date(pi.lastUpdateTime))).append('\n')
        try {
            val flags = pm.getApplicationInfo(ai.pkg, 0).flags
            sb.append("Type: ").append(if (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) "system" else "user").append('\n')
            sb.append("Size (apk): ").append(File(pm.getApplicationInfo(ai.pkg, 0).sourceDir).length() / 1048576).append(" MB\n")
        } catch (e: Exception) { }
        val perms = pi.requestedPermissions
        if (perms != null) {
            val risky = perms.filter { Regex("CAMERA|RECORD_AUDIO|LOCATION|CONTACTS|SMS|CALL_LOG|PHONE|STORAGE|SYSTEM_ALERT|BIND_ACCESSIBILITY|READ_PHONE").containsMatchIn(it) }
                .map { it.substringAfterLast('.') }
            sb.append("Permissions: ").append(perms.size).append(" total; sensitive: ").append(if (risky.isEmpty()) "none" else risky.take(12).joinToString(", "))
        }
        return sb.toString()
    }

    private fun uninstall(ctx: Context, q: String): String {
        val ai = Apps.find(ctx, q) ?: return "ERROR: app nahi mili: $q"
        if (ai.pkg == ctx.packageName) return "ERROR: apne aap ko nahi hataunga"
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + ai.pkg))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Uninstall dialog khul gaya (" + ai.label + "). Screen padh kar 'OK' dabao (click_text OK)."
    }

    // ------------------------------------------------------------ system
    private fun clipboard(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip
        if (c == null || c.itemCount == 0) return "clipboard khaali (ya Android ne background me padhne nahi diya; app ko saamne laao)"
        return c.getItemAt(0).coerceToText(ctx).toString().take(2000)
    }

    private fun volume(ctx: Context, level: Int, stream: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = when (stream.lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(st)
        if (level < 0) return "volume " + am.getStreamVolume(st) + "/" + max
        val v = (level.coerceIn(0, 100) * max / 100)
        am.setStreamVolume(st, v, 0)
        return "volume set: $v/$max"
    }

    private fun torch(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return "ERROR: camera/torch nahi mila"
        cm.setTorchMode(id, on)
        return if (on) "torch ON" else "torch OFF"
    }

    private fun alarm(ctx: Context, h: Int, m: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h.coerceIn(0, 23)).putExtra(AlarmClock.EXTRA_MINUTES, m.coerceIn(0, 59))
            .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "alarm set: " + h + ":" + m.toString().padStart(2, '0') + " - " + label
    }

    private fun dial(ctx: Context, n: String): String {
        if (n.isBlank()) return "ERROR: number khaali"
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Dialer khul gaya ($n). Call lagane ke liye green button dabao (click_text ya tap)."
    }

    private fun sms(ctx: Context, n: String, text: String): String {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "SMS draft khul gaya ($n). Bhejna user ya agla step (send button)."
    }

    private fun sysStats(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Tools.deviceInfo() + "\nRAM free: " + mi.availMem / 1048576 + " / " + mi.totalMem / 1048576 + " MB" + (if (mi.lowMemory) " (LOW)" else "")
    }
}

/** Din/ghante ka lakshya: "aaj 500 kamana", "20 trade-idea", "3 reels". Score yaad rehta hai; har check par agent kaam aage badhata hai. */
object Goals {
    private fun load(): org.json.JSONArray = try { org.json.JSONArray(Hub.prefs.str("goals", "[]")) } catch (e: Exception) { org.json.JSONArray() }
    private fun save(a: org.json.JSONArray) { Hub.prefs.putStr("goals", a.toString()) }

    fun set(text: String, target: Double, unit: String, hours: Int, checkMin: Int): String {
        if (text.isBlank()) return "ERROR: lakshya ka text do"
        val a = load()
        var id = 1
        for (i in 0 until a.length()) id = maxOf(id, a.getJSONObject(i).optInt("id") + 1)
        val o = JSONObject().put("id", id).put("text", text).put("target", target).put("unit", unit).put("progress", 0.0)
            .put("deadline", System.currentTimeMillis() + hours.coerceIn(1, 24 * 30) * 3600_000L).put("done", false)
        a.put(o)
        save(a)
        val every = checkMin.coerceIn(15, 720)
        val r = try {
            Sched.addTask("LAKSHYA #" + id + ": " + text + (if (target > 0) " (target " + target + " " + unit + ")" else "") +
                ". goal_list se score dekho, kaam aage badhao, goal_update{id,progress} karo. Poora ho to goal_done.", "", every, every)
        } catch (e: Exception) { "schedule fail: " + (e.message ?: "") }
        return "Lakshya #" + id + " set. Har " + every + " min agent khud check karke kaam karega. " + r
    }

    fun update(id: Int, progress: Double, note: String): String {
        val a = load()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optInt("id") == id) {
                o.put("progress", progress)
                if (note.isNotBlank()) o.put("note", note.take(200))
                save(a)
                return "score: " + progress + " / " + o.optDouble("target") + " " + o.optString("unit")
            }
        }
        return "ERROR: lakshya nahi mila"
    }

    fun finish(id: Int): String {
        val a = load()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optInt("id") == id) { o.put("done", true); save(a); return "lakshya #$id poora ✅" }
        }
        return "ERROR: lakshya nahi mila"
    }

    fun list(): String {
        val a = load()
        if (a.length() == 0) return "koi lakshya nahi"
        val sb = StringBuilder()
        val now = System.currentTimeMillis()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val left = (o.optLong("deadline") - now) / 3600000L
            sb.append('#').append(o.optInt("id")).append(if (o.optBoolean("done")) " ✅ " else " ⏳ ").append(o.optString("text"))
                .append(" | score ").append(o.optDouble("progress")).append('/').append(o.optDouble("target")).append(' ').append(o.optString("unit"))
                .append(" | ").append(if (left >= 0) "$left ghante bache" else "time khatam").append('\n')
        }
        return sb.toString()
    }
}
