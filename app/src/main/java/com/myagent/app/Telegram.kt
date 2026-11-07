package com.myagent.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telegram bot: (1) agent ke alert/jawab apne Telegram par, (2) Telegram se hi agent ko kaam do (Freqtrade jaisa remote control).
 * Sirf Settings me daali hui APNI chat-id ki baat suni jati hai. Bot token @BotFather se milta hai.
 */
object Telegram {
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var fromTelegram = false

    private val token: String get() = Hub.prefs.str("tg_token").trim()
    private val chat: String get() = Hub.prefs.str("tg_chat").trim()

    private fun call(method: String, body: JSONObject, readMs: Int = 15000): JSONObject {
        if (token.isEmpty()) throw Exception("Telegram bot token khaali")
        val c = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = readMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val j = JSONObject(text)
            if (!j.optBoolean("ok", false)) throw Exception("Telegram: " + j.optString("description", "HTTP $code"))
            return j
        } finally {
            c.disconnect()
        }
    }

    fun send(text: String, to: String = chat): String {
        if (to.isEmpty()) return "ERROR: Telegram chat-id set nahi (Settings -> Telegram)"
        return try {
            val t = text.trim()
            var i = 0
            while (i < t.length) {
                call("sendMessage", JSONObject().put("chat_id", to).put("text", t.substring(i, minOf(t.length, i + 3800))))
                i += 3800
            }
            "telegram bheja ($to)"
        } catch (e: Exception) {
            "ERROR: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun mirror(text: String) {
        if (!fromTelegram || chat.isEmpty() || token.isEmpty()) return
        Thread { send(text.replace(Regex("(?s)```.*?```"), "[code]").take(3500)) }.start()
    }

    /** Bot ko Telegram par ek message bhejo, phir ye dabao: chat-id apne aap mil jati hai. */
    fun detectChat(): String {
        return try {
            val r = call("getUpdates", JSONObject().put("limit", 20))
            val arr = r.getJSONArray("result")
            for (i in arr.length() - 1 downTo 0) {
                val m = arr.getJSONObject(i).optJSONObject("message") ?: continue
                val id = m.getJSONObject("chat").get("id").toString()
                Hub.prefs.putStr("tg_chat", id)
                return "✅ chat-id mil gayi: $id"
            }
            "Bot ko Telegram me koi message bhejo (jaise 'hi') phir dobara dabao."
        } catch (e: Exception) {
            "ERROR: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun start() {
        if (!Hub.prefs.bool("tg_control", false) || token.isEmpty() || chat.isEmpty()) { stop(); return }
        if (thread?.isAlive == true) return
        running = true
        val th = Thread({ loop() }, "tg-poll")
        th.isDaemon = true
        thread = th
        th.start()
    }

    fun stop() { running = false }

    private fun loop() {
        var offset = 0L
        while (running && Hub.prefs.masterOn && Hub.prefs.bool("tg_control", false)) {
            try {
                val r = call("getUpdates", JSONObject().put("timeout", 25).put("offset", offset).put("limit", 5), 35000)
                val arr = r.getJSONArray("result")
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    offset = u.getLong("update_id") + 1
                    val m = u.optJSONObject("message") ?: continue
                    val from = m.getJSONObject("chat").get("id").toString()
                    val text = m.optString("text").trim()
                    if (text.isEmpty()) continue
                    if (from != chat) { Hub.log("⚠ Telegram: anjaan chat ($from) ka message ignore kiya"); continue }
                    handle(text)
                }
            } catch (e: Exception) {
                try { Thread.sleep(10000) } catch (x: InterruptedException) { }
            }
        }
        running = false
    }

    private fun handle(text: String) {
        val l = text.lowercase()
        when {
            l == "/stop" -> { Hub.engine.pause(); send("⏹ ruk gaya (task save hai)") }
            l == "/status" -> send("Status: " + Hub.statusText + "\nAaj token: " + Hub.prefs.usageAll())
            l == "/start" || l == "/help" -> send("Mujhe seedha kaam likho (jaise 'RELIANCE ka analysis'). /status /stop")
            else -> {
                Hub.log("📨 Telegram: $text")
                Hub.mainHandler.post { Hub.engine.submit(text, true) }
            }
        }
    }
}

/** Macros (Tasker/Maestro jaise): kadam ek baar likh/seekh lo, baad me bina AI-token ke chalte hain. */
object Macros {
    private fun load(): JSONObject = try { JSONObject(Hub.prefs.str("macros", "{}")) } catch (e: Exception) { JSONObject() }

    fun save(name: String, steps: org.json.JSONArray): String {
        val n = name.lowercase().replace(Regex("[^a-z0-9_-]"), "").take(30)
        if (n.isEmpty() || steps.length() == 0) return "ERROR: naam aur steps do"
        val j = load()
        j.put(n, steps)
        while (j.length() > 30) j.remove(j.keys().next())
        Hub.prefs.putStr("macros", j.toString())
        return "macro '$n' save (" + steps.length() + " steps). macro_run{name:\"$n\"} se chalao."
    }

    fun get(name: String): org.json.JSONArray? = load().optJSONArray(name.lowercase().trim())

    fun list(): String {
        val j = load()
        if (j.length() == 0) return "(koi macro nahi)"
        return j.keys().asSequence().joinToString("\n") { it + ": " + j.getJSONArray(it).length() + " steps" }
    }

    fun delete(name: String): String {
        val j = load(); j.remove(name.lowercase().trim()); Hub.prefs.putStr("macros", j.toString()); return "macro hata diya"
    }
}
