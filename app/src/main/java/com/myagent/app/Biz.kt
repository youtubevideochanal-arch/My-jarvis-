package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kamai ka khata: income/expense likho aur dekho ki kamai API kharch se zyada hui ya nahi. Agent khud paisa nahi bhejta/leta. */
object Ledger {
    private fun load(): JSONArray = try { JSONArray(Hub.prefs.str("ledger", "[]")) } catch (e: Exception) { JSONArray() }

    fun add(kind: String, amount: Double, note: String): String {
        val k = if (kind.lowercase().startsWith("e")) "expense" else "income"
        if (amount <= 0) return "ERROR: amount > 0 do"
        val a = load()
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("k", k).put("a", amount).put("n", note.take(80)))
        val out = JSONArray()
        val start = if (a.length() > 400) a.length() - 400 else 0
        for (i in start until a.length()) out.put(a.get(i))
        Hub.prefs.putStr("ledger", out.toString())
        return "$k ${String.format("%.2f", amount)} likh liya: " + note.take(40)
    }

    fun report(): String {
        val a = load()
        if (a.length() == 0) return "Khata khaali. Kamai aaye ya API/tool par kharch ho to ledger_add se likho."
        var inc = 0.0
        var exp = 0.0
        var api = 0.0
        val month = SimpleDateFormat("yyyyMM", Locale.US).format(Date())
        var mInc = 0.0
        var mExp = 0.0
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            val amt = j.optDouble("a")
            val mm = SimpleDateFormat("yyyyMM", Locale.US).format(Date(j.optLong("t")))
            if (j.optString("k") == "income") { inc += amt; if (mm == month) mInc += amt }
            else { exp += amt; if (mm == month) mExp += amt; if (j.optString("n").lowercase().contains("api")) api += amt }
        }
        val net = inc - exp
        val sb = StringBuilder()
        sb.append("Total income ").append(String.format("%.2f", inc)).append(" | kharch ").append(String.format("%.2f", exp)).append(" | net ").append(String.format("%.2f", net)).append('\n')
        sb.append("Is mahine: income ").append(String.format("%.2f", mInc)).append(" kharch ").append(String.format("%.2f", mExp)).append('\n')
        sb.append("API kharch (note me 'api'): ").append(String.format("%.2f", api)).append(" -> ")
            .append(if (api == 0.0) "abhi API paid nahi (free tier)" else if (inc >= api) "kamai API kharch se zyada: API khud ke paise se chal rahi hai" else "kamai abhi API kharch se kam hai").append('\n')
        sb.append("Aaj ke API tokens: ").append(Hub.prefs.usageAll()).append(" (").append(Hub.prefs.callsToday()).append(" calls)")
        return sb.toString()
    }
}

/** PC companion (pc_agent/pc_agent.py) se LAN par baat. Token zaroori. */
object PcLink {
    private val SAFE = Regex("^(ls|dir|pwd|cd|whoami|echo|cat|type|head|tail|python3? --version|node --version|git status|git log|git diff|df|free|uname|hostname|date)(\\s|$)")

    fun isSafeRun(cmd: String): Boolean = SAFE.containsMatchIn(cmd.trim()) && !Regex("[;&|><`$]").containsMatchIn(cmd)

    fun call(doWhat: String, arg: String, arg2: String): String {
        val base = Hub.prefs.pcUrl.trim().trimEnd('/')
        if (base.isEmpty()) return "ERROR: PC link set nahi. Settings -> PC link me PC ka URL (http://192.168.x.x:8765) aur token daalo; PC par pc_agent.py chalao."
        val ep = when (doWhat) { "run", "read", "write", "list", "shot", "type", "click", "open" -> doWhat; else -> return "ERROR: pc do= run|read|write|list|shot|type|click|open" }
        val body = JSONObject().put("arg", arg).put("arg2", arg2).toString()
        return try {
            val c = java.net.URL("$base/$ep").openConnection() as java.net.HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 6000
            c.readTimeout = if (ep == "run") 70000 else 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Token", Hub.prefs.pcToken)
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401) return "ERROR: PC ne token reject kiya (Settings me token check karo)"
            if (ep == "shot" && code == 200) {
                val j = JSONObject(text)
                val b64 = j.optString("png")
                if (b64.isEmpty()) return "ERROR: PC screenshot nahi de paya: " + j.optString("error")
                val saved = FileStore.saveBytes(Hub.app, "PC", "shot_" + System.currentTimeMillis() + ".png", android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                "PC screenshot saved: $saved"
            } else "HTTP $code " + text.take(2500)
        } catch (e: Exception) {
            "ERROR: PC se connect nahi hua (" + e.javaClass.simpleName + "): same WiFi aur pc_agent.py chal raha hai? " + (e.message ?: "")
        }
    }
}
