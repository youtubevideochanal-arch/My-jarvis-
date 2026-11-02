package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Extra APIs ki chaabiyan (local, phone me) + public-apis repo se naye API dhundhna.
 * Model ko asli key kabhi nahi dikhti: wo {{KEY:naam}} likhta hai, app request se pehle badal deti hai.
 */
object Extras {
    private fun load(): JSONArray = try { JSONArray(Hub.prefs.str("extra_apis", "[]")) } catch (e: Exception) { JSONArray() }

    fun names(): List<String> {
        val a = load()
        return (0 until a.length()).map { a.getJSONObject(it).optString("name") }
    }

    fun put(name: String, key: String, base: String) {
        val n = name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (n.isEmpty()) return
        val a = load()
        val out = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("name") != n) out.put(a.getJSONObject(i))
        if (key.isNotBlank()) out.put(JSONObject().put("name", n).put("key", key.trim()).put("base", base.trim()))
        Hub.prefs.putStr("extra_apis", out.toString())
    }

    fun get(name: String): JSONObject? {
        val a = load()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("name") == name.trim().lowercase()) return a.getJSONObject(i)
        return null
    }

    /** {{KEY:naam}} -> asli key. */
    fun resolve(s: String): String {
        if (!s.contains("{{KEY:")) return s
        return Regex("\\{\\{KEY:([A-Za-z0-9_-]+)\\}\\}").replace(s) { m -> get(m.groupValues[1])?.optString("key") ?: m.value }
    }

    fun missingKeys(s: String): List<String> =
        Regex("\\{\\{KEY:([A-Za-z0-9_-]+)\\}\\}").findAll(s).map { it.groupValues[1] }.filter { get(it) == null }.toList()

    fun status(): String {
        val n = names()
        return if (n.isEmpty()) "(koi extra API key save nahi)" else "Saved keys: " + n.joinToString(", ") + "  (use: {{KEY:naam}})"
    }

    // ---- public-apis directory
    private fun cacheFile(): File = File(Hub.app.cacheDir, "public_apis_readme.md")

    private fun readme(): String {
        val f = cacheFile()
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 86400000L && f.length() > 1000) return f.readText()
        val urls = listOf(
            "https://raw.githubusercontent.com/public-apis/public-apis/master/README.md",
            "https://raw.githubusercontent.com/public-apis/public-apis/main/README.md"
        )
        var last = ""
        for (u in urls) {
            try {
                val r = WebTools.get(u, 3_000_000)
                if (r.code == 200 && r.body.length > 1000) {
                    f.writeText(r.body)
                    return r.body
                }
                last = "HTTP " + r.code
            } catch (e: Exception) { last = e.message ?: "" }
        }
        if (f.exists()) return f.readText()
        throw Exception("public-apis list nahi mili ($last)")
    }

    /** Keywords se milte hue free API: naam - kaam - auth - link. */
    fun findApi(query: String): String {
        val terms = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        if (terms.isEmpty()) return "ERROR: query me kaam ka shabd likho (jaise 'weather', 'crypto price', 'jokes')"
        val text = readme()
        var cat = ""
        val hits = ArrayList<Pair<Int, String>>()
        for (line in text.lineSequence()) {
            if (line.startsWith("### ")) { cat = line.removePrefix("### ").trim(); continue }
            if (!line.startsWith("| [")) continue
            val cols = line.split('|').map { it.trim() }
            if (cols.size < 5) continue
            val m = Regex("\\[(.+?)\\]\\((.+?)\\)").find(cols[1]) ?: continue
            val hay = (m.groupValues[1] + " " + cols[2] + " " + cat).lowercase()
            val sc = terms.count { hay.contains(it) }
            if (sc > 0) hits.add(Pair(sc * 10 + (if (cols[3].lowercase().contains("no") || cols[3].isBlank()) 3 else 0), m.groupValues[1] + " - " + cols[2].take(70) + " | auth=" + (cols[3].ifBlank { "none" }) + " | " + m.groupValues[2] + " [" + cat + "]"))
        }
        if (hits.isEmpty()) return "Koi API nahi mili '$query' ke liye. Dusre shabd try karo."
        return hits.sortedByDescending { it.first }.take(8).joinToString("\n") { it.second } +
            "\n(auth=apiKey ho to user se Settings -> Extra APIs me key daalne ko kaho; 'none' wali bina key chalti hain. Docs page fetch_url se padho, phir http_request.)"
    }
}
