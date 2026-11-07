package com.myagent.app

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Internet search, page padhna, aur apni site ka passive security check. */
object WebTools {
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    class Resp(val code: Int, val headers: Map<String, List<String>>, val body: String) {
        fun h(name: String): String? = headers[name.lowercase()]?.firstOrNull()
    }

    fun get(url: String, maxBytes: Int = 300000, follow: Boolean = true, extra: Map<String, String> = emptyMap()): Resp {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.instanceFollowRedirects = follow
            c.connectTimeout = 12000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "en")
            for ((k, v) in extra) c.setRequestProperty(k, v)
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val bos = ByteArrayOutputStream()
            if (stream != null) {
                val buf = ByteArray(8192)
                var total = 0
                while (total < maxBytes) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    bos.write(buf, 0, n)
                    total += n
                }
                stream.close()
            }
            val hdrs = HashMap<String, List<String>>()
            for ((k, v) in c.headerFields) {
                if (k != null) hdrs[k.lowercase()] = v
            }
            return Resp(code, hdrs, bos.toString("UTF-8"))
        } finally {
            conn?.disconnect()
        }
    }

    fun download(url: String, maxBytes: Int): ByteArray {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode != 200) throw Exception("HTTP " + c.responseCode)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(16384)
            c.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    bos.write(buf, 0, n)
                    if (bos.size() > maxBytes) throw Exception("file bahut badi")
                }
            }
            return bos.toByteArray()
        } finally {
            conn?.disconnect()
        }
    }

    fun htmlToText(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#x27;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun search(q: String): String {
        if (q.isBlank()) return "ERROR: query khaali"
        val r = get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"), 400000)
        val links = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(r.body).toList()
        val snips = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(r.body).toList()
        if (links.isEmpty()) return "ERROR: search result nahi mile (HTTP " + r.code + "). Thodi der baad ya alag shabd try karo."
        val sb = StringBuilder()
        var n = 0
        for ((i, m) in links.withIndex()) {
            if (n >= 6) break
            var href = m.groupValues[1].replace("&amp;", "&")
            val u = Regex("uddg=([^&]+)").find(href)
            href = if (u != null) URLDecoder.decode(u.groupValues[1], "UTF-8") else if (href.startsWith("//")) "https:$href" else href
            val title = htmlToText(m.groupValues[2])
            val snip = if (i < snips.size) htmlToText(snips[i].groupValues[1]) else ""
            n++
            sb.append(n).append(". ").append(title).append("\n   ").append(href).append("\n   ").append(snip.take(220)).append("\n")
        }
        return sb.toString()
    }

    /** Page ka dhancha (links/headings/forms) - DOM-jaisa, bina screenshot ke. */
    fun extract(url: String, what: String): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "ERROR: sirf http/https URL"
        val r = get(u, 600000)
        val html = r.body
        val sb = StringBuilder("HTTP " + r.code + "\n")
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        when (what.lowercase()) {
            "headings" -> Regex("<h([1-3])[^>]*>(.*?)</h[1-3]>", opts).findAll(html).take(40)
                .forEach { sb.append("h").append(it.groupValues[1]).append(": ").append(htmlToText(it.groupValues[2]).take(100)).append('\n') }
            "forms" -> Regex("<form[^>]*>", RegexOption.IGNORE_CASE).findAll(html).take(10).forEach { sb.append(it.value.take(200)).append('\n') }
            else -> {
                val seen = HashSet<String>()
                Regex("<a\\s[^>]*href=[\"']([^\"'#][^\"']*)[\"'][^>]*>(.*?)</a>", opts).findAll(html).forEach {
                    val href = it.groupValues[1].trim()
                    val txt = htmlToText(it.groupValues[2]).trim().take(60)
                    if (txt.isNotEmpty() && seen.size < 40 && seen.add(href)) sb.append("- ").append(txt).append(" -> ").append(href.take(120)).append('\n')
                }
            }
        }
        return sb.toString()
    }

    fun fetchText(url: String, maxChars: Int = 3500): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "ERROR: sirf http/https URL"
        val r = get(u, 500000)
        return "HTTP " + r.code + "\n" + htmlToText(r.body).take(maxChars)
    }

    // ------------------------------------------------------------------ passive security audit
    fun audit(prefs: Prefs, input: String): String {
        val s = input.trim()
        val base = if (s.contains("://")) s else "https://$s"
        val host = try { java.net.URI(base).host } catch (e: Exception) { null }
            ?: return "ERROR: URL samajh nahi aaya"
        val mine = prefs.mySites.split(",", "\n", " ").map {
            it.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
        }.filter { it.isNotEmpty() }
        val h = host.lowercase()
        if (mine.none { h == it || h.endsWith(".$it") }) {
            return "REFUSED: '$host' 'Meri websites' list me nahi hai. Ye scan sirf aapki apni site/app ke liye hai. " +
                "Settings -> Meri websites me add karo (sirf wahi jo aapki hain)."
        }
        val f = ArrayList<String>()
        val r = try { get("https://$host/", 250000) } catch (e: Exception) { return "ERROR: site khuli nahi: " + (e.message ?: "") }
        f.add("[INFO] https://$host/ -> HTTP " + r.code)

        if (r.h("strict-transport-security") == null) f.add("[MED] HSTS header nahi (Strict-Transport-Security) -> downgrade/MITM risk")
        val csp = r.h("content-security-policy")
        if (csp == null) f.add("[MED] Content-Security-Policy nahi -> XSS ka asar badh jata hai")
        if (r.h("x-frame-options") == null && (csp == null || !csp.contains("frame-ancestors")))
            f.add("[LOW] Clickjacking protection nahi (X-Frame-Options / frame-ancestors)")
        if (r.h("x-content-type-options")?.lowercase() != "nosniff") f.add("[LOW] X-Content-Type-Options: nosniff nahi")
        if (r.h("referrer-policy") == null) f.add("[LOW] Referrer-Policy nahi")
        if (r.h("permissions-policy") == null) f.add("[INFO] Permissions-Policy nahi")
        val server = r.h("server")
        if (server != null && Regex("\\d").containsMatchIn(server)) f.add("[LOW] Server version leak: $server")
        val xp = r.h("x-powered-by")
        if (xp != null) f.add("[LOW] X-Powered-By leak: $xp")

        for (c in r.headers["set-cookie"] ?: emptyList()) {
            val name = c.substringBefore('=')
            val l = c.lowercase()
            if (!l.contains("secure")) f.add("[MED] Cookie '$name' me Secure flag nahi")
            if (!l.contains("httponly")) f.add("[MED] Cookie '$name' me HttpOnly flag nahi (JS se chori ho sakti hai)")
            if (!l.contains("samesite")) f.add("[LOW] Cookie '$name' me SameSite nahi (CSRF risk)")
        }

        try {
            val hr = get("http://$host/", 2000, false)
            val loc = hr.h("location") ?: ""
            if (hr.code !in 300..399 || !loc.startsWith("https://")) f.add("[MED] http:// se https:// par redirect nahi hota")
        } catch (e: Exception) { }

        try {
            val origin = "https://evil.example"
            val cr = get("https://$host/", 2000, false, mapOf("Origin" to origin))
            val acao = cr.h("access-control-allow-origin")
            val acac = cr.h("access-control-allow-credentials")?.lowercase() == "true"
            if (acao == origin && acac) f.add("[HIGH] CORS: koi bhi Origin reflect + credentials allowed")
            else if (acao == origin) f.add("[MED] CORS: koi bhi Origin reflect ho raha hai")
            else if (acao == "*") f.add("[INFO] CORS: Access-Control-Allow-Origin: * (public API ho to theek)")
        } catch (e: Exception) { }

        val mixed = Regex("(?i)(src|href)=[\"']http://[^\"']+").findAll(r.body).count()
        if (mixed > 0) f.add("[MED] Mixed content: $mixed http:// resource https page par")
        val noSri = Regex("(?i)<script[^>]+src=[\"']https?://[^\"']+[\"'][^>]*>").findAll(r.body)
            .count { !it.value.lowercase().contains("integrity") }
        if (noSri > 0) f.add("[LOW] $noSri external <script> bina SRI (integrity) ke")

        var soft = false
        try {
            soft = get("https://$host/zz-" + System.currentTimeMillis(), 2000, false).code == 200
        } catch (e: Exception) { }
        if (soft) {
            f.add("[INFO] Site har path par 200 deti hai, isliye exposed-file check bharosemand nahi - skip")
        } else {
            val paths = listOf("/.git/HEAD", "/.env", "/backup.zip", "/phpinfo.php", "/server-status", "/.DS_Store",
                "/openapi.json", "/api/swagger.json", "/admin/", "/.well-known/security.txt")
            for (p in paths) {
                try {
                    val pr = get("https://$host$p", 2000, false)
                    val body = pr.body.trim()
                    when (p) {
                        "/.git/HEAD" -> if (pr.code == 200 && body.startsWith("ref:")) f.add("[HIGH] $p public hai -> poora source code leak ho sakta hai")
                        "/.env" -> if (pr.code == 200 && body.contains("=") && !body.contains("<html", true)) f.add("[HIGH] $p public hai -> secrets/keys leak")
                        "/.well-known/security.txt" -> if (pr.code != 200) f.add("[INFO] security.txt nahi (bug-report contact dena accha hota hai)")
                        "/admin/" -> if (pr.code == 200) f.add("[MED] /admin/ bina login ke khula dikh raha hai") else if (pr.code == 401 || pr.code == 403) f.add("[INFO] /admin/ maujood hai (login/403)")
                        else -> if (pr.code == 200) f.add("[MED] $p public hai (HTTP 200) - zaroorat na ho to hatao")
                    }
                } catch (e: Exception) { }
            }
        }
        f.add("NOTE: ye sirf passive/non-intrusive check hai. SQLi/XSS/auth bugs ke liye code review aur proper pentest alag se karo.")
        return f.joinToString("\n")
    }
}
