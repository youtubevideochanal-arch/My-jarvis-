package com.myagent.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Non-UI abilities the engine can use: web, files, apps, and scope-gated passive security checks. */
object Tools {

    fun strip(html: String): String {
        var s = html
        s = s.replace(Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?s)<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#x27;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** Returns an error string if the URL must not be fetched, else null. */
    private fun blocked(url: String): String? {
        val u = try {
            URL(url)
        } catch (e: Exception) {
            return "bad url"
        }
        if (u.protocol != "http" && u.protocol != "https") return "only http/https allowed"
        try {
            for (a in InetAddress.getAllByName(u.host)) {
                if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress) {
                    return "blocked: local network address"
                }
            }
        } catch (e: Exception) {
            return "cannot resolve host"
        }
        return null
    }

    fun search(q: String): String {
        return try {
            val r = Http.request(
                "GET", "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"),
                mapOf("User-Agent" to Http.UA), null, 20000, true
            )
            val re = Regex(
                "class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?class=\"result__snippet\"[^>]*>(.*?)</a>",
                RegexOption.DOT_MATCHES_ALL
            )
            val out = StringBuilder()
            var n = 0
            for (m in re.findAll(r.body)) {
                if (n >= 6) break
                var u = m.groupValues[1]
                val ud = Regex("uddg=([^&]+)").find(u)
                if (ud != null) u = URLDecoder.decode(ud.groupValues[1], "UTF-8")
                out.append("- ").append(strip(m.groupValues[2])).append(" | ").append(u).append("\n  ")
                    .append(strip(m.groupValues[3])).append('\n')
                n++
            }
            if (n == 0) "no results" else out.toString()
        } catch (e: Exception) {
            "search failed: ${e.message}"
        }
    }

    fun fetch(url: String, maxChars: Int): String {
        blocked(url)?.let { return it }
        return try {
            val r = Http.request("GET", url, mapOf("User-Agent" to Http.UA), null, 25000, true)
            "HTTP ${r.code}\n" + strip(r.body).take(maxChars)
        } catch (e: Exception) {
            "fetch failed: ${e.message}"
        }
    }

    fun openUrl(c: Context, url: String): String {
        return try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "opened"
        } catch (e: Exception) {
            "cannot open: ${e.message}"
        }
    }

    fun openApp(c: Context, name: String, bounds: android.graphics.Rect? = null): String {
        val pm = c.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(probe, 0)
        val q = name.trim().lowercase()
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "app not found: $name"
        if (hit.activityInfo.packageName in Prefs.blocked(c)) return "BLOCKED: user disconnected this app"
        val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "cannot launch $name"
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (bounds != null) {
                val o = android.app.ActivityOptions.makeBasic().setLaunchBounds(bounds)
                c.startActivity(li, o.toBundle())
            } else {
                c.startActivity(li)
            }
            "opened ${hit.loadLabel(pm)}"
        } catch (e: Exception) {
            "cannot open: ${e.message}"
        }
    }

    // ---- files live in Downloads/MyAgent (visible to the user in any file manager) ----

    fun write(c: Context, name: String, content: String, view: Boolean): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "file.txt" }
        val mime = when {
            safe.endsWith(".html") || safe.endsWith(".htm") -> "text/html"
            safe.endsWith(".txt") -> "text/plain"
            safe.endsWith(".json") -> "application/json"
            safe.endsWith(".css") -> "text/css"
            else -> "application/octet-stream"
        }
        return try {
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, safe)
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyAgent")
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: return "write failed"
            c.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            if (view && mime == "text/html") {
                try {
                    c.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                } catch (e: Exception) {
                }
            }
            "saved to Downloads/MyAgent/$safe"
        } catch (e: Exception) {
            "write failed: ${e.message}"
        }
    }

    fun read(c: Context, name: String): String {
        return try {
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val cur = c.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                arrayOf(safe),
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            ) ?: return "not found"
            cur.use {
                if (!it.moveToFirst()) return "not found"
                val id = it.getLong(0)
                val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                val txt = c.contentResolver.openInputStream(uri)?.use { s -> s.readBytes() }
                    ?.toString(Charsets.UTF_8) ?: ""
                txt.take(6000)
            }
        } catch (e: Exception) {
            "read failed: ${e.message}"
        }
    }

    // ---- passive, scope-gated security checks ----

    fun inScope(c: Context, host: String): Boolean {
        val h = host.lowercase()
        for (line in Prefs.scope(c).lines()) {
            val e = line.trim().lowercase().removePrefix("https://").removePrefix("http://")
                .removePrefix("*.").substringBefore('/').trim()
            if (e.isEmpty()) continue
            if (h == e || h.endsWith(".$e")) return true
        }
        return false
    }

    fun scan(c: Context, domainIn: String): String {
        val host = domainIn.trim().lowercase().removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore(':').trim()
        if (host.isEmpty()) return "no domain given"
        if (!inScope(c, host)) {
            return "OUT OF SCOPE: $host is not in the user's Allowed domains list. Do not test it. " +
                "Tell the user to add it only if they own it or an official program permits testing."
        }
        blocked("https://$host/")?.let { return it }
        val sb = StringBuilder("PASSIVE SCAN $host\n")
        try {
            var url = "https://$host/"
            var r = Http.request("GET", url, mapOf("User-Agent" to Http.UA), null, 20000, false, 200_000)
            var hops = 0
            while (r.code in 301..308 && hops < 3) {
                val loc = r.headers["location"]?.firstOrNull() ?: break
                val next = try {
                    URL(URL(url), loc).toString()
                } catch (e: Exception) {
                    break
                }
                val nh = URL(next).host.lowercase()
                if (!inScope(c, nh)) {
                    sb.append("redirects out of scope to $nh (stopped)\n")
                    break
                }
                url = next
                r = Http.request("GET", url, mapOf("User-Agent" to Http.UA), null, 20000, false, 200_000)
                hops++
            }
            sb.append("GET $url -> ${r.code}\n")
            val missing = ArrayList<String>()
            for (h in listOf(
                "strict-transport-security", "content-security-policy", "x-frame-options",
                "x-content-type-options", "referrer-policy"
            )) {
                if (!r.headers.containsKey(h)) missing.add(h)
            }
            if (missing.isNotEmpty()) sb.append("missing headers: ").append(missing.joinToString(", ")).append('\n')
            val srv = r.headers["server"]?.firstOrNull()
            if (srv != null && Regex("\\d").containsMatchIn(srv)) sb.append("server banner: $srv\n")
            val pw = r.headers["x-powered-by"]?.firstOrNull()
            if (pw != null) sb.append("x-powered-by: $pw\n")
            for (ck in r.headers["set-cookie"] ?: emptyList()) {
                val l = ck.lowercase()
                val flags = ArrayList<String>()
                if (!l.contains("secure")) flags.add("no Secure")
                if (!l.contains("httponly")) flags.add("no HttpOnly")
                if (flags.isNotEmpty()) sb.append("cookie ${ck.substringBefore('=')}: ").append(flags.joinToString(", ")).append('\n')
            }
            for (path in listOf("/robots.txt", "/.well-known/security.txt", "/.git/HEAD", "/.env")) {
                Thread.sleep(500)
                val x = Http.request("GET", "https://$host$path", mapOf("User-Agent" to Http.UA), null, 15000, false, 4000)
                val b = x.body
                val note = when {
                    x.code != 200 -> "-"
                    path == "/.git/HEAD" -> if (b.startsWith("ref:")) "EXPOSED .git directory" else "-"
                    path == "/.env" ->
                        if (b.lines().any { Regex("^[A-Z][A-Z0-9_]{2,}=").containsMatchIn(it) })
                            "POSSIBLE EXPOSED .env (contents deliberately not read)" else "-"
                    else -> "present"
                }
                sb.append("$path -> ${x.code} $note\n")
            }
        } catch (e: Exception) {
            sb.append("scan error: ${e.message}\n")
        }
        sb.append("(passive checks only; findings need manual verification before any report)")
        return sb.toString()
    }
}
