package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Meri social accounts ki jaankari (sirf phone me), content drafts, aur free image banana. */
object Social {
    val platforms = listOf(
        "insta" to "Instagram handle (@...)",
        "yt" to "YouTube channel naam/link",
        "li" to "LinkedIn profile/page",
        "x" to "X (Twitter) handle",
        "fb" to "Facebook page",
        "tg" to "Telegram channel/username",
        "other" to "Koi aur (naam: handle)"
    )

    fun get(k: String): String = Hub.prefs.str("social.$k")
    fun set(k: String, v: String) = Hub.prefs.putStr("social.$k", v.trim())

    fun hasProfile(): Boolean = (platforms.map { it.first } + listOf("niche", "tone", "goal")).any { get(it).isNotBlank() }

    /** Agent ko chhota context: kaun se account, kis vishay par, kaisi bhasha me. */
    fun profile(): String {
        val sb = StringBuilder("MERE SOCIAL ACCOUNTS (user ke apne):\n")
        var any = false
        for ((k, label) in platforms) {
            val v = get(k)
            if (v.isNotBlank()) { sb.append("- ").append(label.substringBefore(" (").substringBefore(" naam")).append(": ").append(v).append('\n'); any = true }
        }
        for ((k, l) in listOf("niche" to "Vishay/niche", "tone" to "Bhasha/andaaz", "goal" to "Lakshya", "schedule" to "Posting routine")) {
            val v = get(k)
            if (v.isNotBlank()) { sb.append("- ").append(l).append(": ").append(v).append('\n'); any = true }
        }
        return if (any) sb.toString().trimEnd() else "(user ne social accounts abhi Settings me nahi bhare - zarurat ho to poochho)"
    }

    // ---- drafts (post karne se pehle ka maal)
    private fun drafts(): JSONArray = try { JSONArray(Hub.prefs.str("drafts", "[]")) } catch (e: Exception) { JSONArray() }

    fun draftSave(platform: String, title: String, text: String): String {
        if (text.isBlank()) return "ERROR: text khaali"
        val a = drafts()
        val id = (System.currentTimeMillis() / 1000L).toString()
        a.put(JSONObject().put("id", id).put("p", platform.take(20)).put("t", title.take(60)).put("x", text.take(3000)))
        val out = JSONArray()
        val start = if (a.length() > 40) a.length() - 40 else 0
        for (i in start until a.length()) out.put(a.get(i))
        Hub.prefs.putStr("drafts", out.toString())
        val path = try { FileStore.save(Hub.app, "Social", "draft_" + id + ".txt", platform + " | " + title + "\n\n" + text) } catch (e: Exception) { "" }
        return "draft save #" + id + (if (path.isNotEmpty()) " ($path)" else "")
    }

    fun draftList(): String {
        val a = drafts()
        if (a.length() == 0) return "(koi draft nahi)"
        val sb = StringBuilder()
        for (i in a.length() - 1 downTo maxOf(0, a.length() - 12)) {
            val o = a.getJSONObject(i)
            sb.append('#').append(o.optString("id")).append(" [").append(o.optString("p")).append("] ").append(o.optString("t")).append(": ")
                .append(o.optString("x").take(90).replace("\n", " ")).append('\n')
        }
        return sb.toString().trimEnd()
    }

    fun draftGet(id: String): String {
        val a = drafts()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("id") == id.trim().removePrefix("#")) return a.getJSONObject(i).optString("x")
        return "ERROR: draft nahi mila"
    }

    /** Free image (Pollinations, bina key). Download/MyAgent/Images me save. */
    fun genImage(prompt: String, w: Int, h: Int): String {
        if (prompt.isBlank()) return "ERROR: prompt do"
        var c: HttpURLConnection? = null
        return try {
            val u = "https://image.pollinations.ai/prompt/" + URLEncoder.encode(prompt.take(400), "UTF-8") +
                "?width=" + w.coerceIn(256, 1536) + "&height=" + h.coerceIn(256, 1536) + "&nologo=true"
            val con = URL(u).openConnection() as HttpURLConnection
            c = con
            con.connectTimeout = 15000
            con.readTimeout = 90000
            con.setRequestProperty("User-Agent", "MyAgent/4.0")
            if (con.responseCode != 200) return "ERROR: image service HTTP " + con.responseCode + " (thodi der baad try karo)"
            val bytes = con.inputStream.use { it.readBytes() }
            if (bytes.size < 2000) return "ERROR: image nahi bani"
            val saved = FileStore.saveBytes(Hub.app, "Images", "img_" + System.currentTimeMillis() + ".jpg", bytes)
            "image saved: $saved (" + bytes.size / 1024 + " KB)"
        } catch (e: Exception) {
            "ERROR: " + e.javaClass.simpleName + " " + (e.message ?: "")
        } finally {
            c?.disconnect()
        }
    }
}
