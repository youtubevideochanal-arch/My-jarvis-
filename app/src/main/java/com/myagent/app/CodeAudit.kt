package com.myagent.app

import java.io.File

/** Workspace project ka static security/bug scan (line numbers ke saath). Sirf padhta hai, kuch chalata nahi. */
object CodeAudit {
    private class Rule(val sev: String, val title: String, val re: Regex, val fix: String, val exts: Set<String>? = null)

    private val WEB = setOf("js", "jsx", "ts", "tsx", "html", "htm", "vue", "php")
    private val TEXT_EXT = setOf(
        "js", "jsx", "ts", "tsx", "html", "htm", "vue", "php", "py", "java", "kt", "kts", "xml", "json", "yml", "yaml",
        "env", "properties", "gradle", "sh", "rb", "go", "rs", "cs", "txt", "md", "cfg", "ini", "toml", "sql", "css"
    )

    private val rules: List<Rule> = listOf(
        Rule("HIGH", "Hardcoded secret / API key",
            Regex("""(?i)(api[_-]?key|secret|passwd|password|access[_-]?token|auth[_-]?token)\s*[:=]\s*["'][A-Za-z0-9_\-/+=]{12,}["']"""),
            "Key ko code se hatao, environment variable / server-side config me rakho, aur jo key leak hui use turant rotate karo."),
        Rule("HIGH", "AWS access key", Regex("""AKIA[0-9A-Z]{16}"""), "Key rotate karo aur IAM role use karo."),
        Rule("HIGH", "Private key code me", Regex("""-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"""), "Repo se hatao, key badlo, git history saaf karo."),
        Rule("HIGH", "GitHub token", Regex("""gh[pousr]_[A-Za-z0-9]{30,}"""), "Token revoke karke naya banao."),
        Rule("HIGH", "Google API key", Regex("""AIza[0-9A-Za-z_\-]{35}"""), "Key ko restrict (app/domain) karo ya badlo."),
        Rule("HIGH", "eval / new Function (code injection)", Regex("""\beval\s*\(|new\s+Function\s*\("""),
            "eval hatao; JSON.parse ya safe parser use karo.", setOf("js", "jsx", "ts", "tsx", "html", "htm", "vue", "php", "py")),
        Rule("HIGH", "Command injection risk",
            Regex("""os\.system\(|subprocess\.[a-z]+\([^)]*shell\s*=\s*True|Runtime\.getRuntime\(\)\.exec|shell_exec\(|passthru\(|child_process"""),
            "User input kabhi shell command me mat jodo; argument list use karo aur input validate karo."),
        Rule("HIGH", "SQL injection (string jod kar query)",
            Regex("""(?i)\b(select|insert\s+into|update|delete\s+from)\b.*["']\s*\+\s*\w+|(?i)\b(execute|query)\(\s*f["']"""),
            "Parameterized query / prepared statement use karo."),
        Rule("HIGH", "TLS verification band",
            Regex("""verify\s*=\s*False|rejectUnauthorized\s*:\s*false|TrustAllCerts|ALLOW_ALL_HOSTNAME_VERIFIER|NODE_TLS_REJECT_UNAUTHORIZED"""),
            "Certificate verification ON rakho."),
        Rule("MED", "XSS risk (innerHTML / document.write)",
            Regex("""\.innerHTML\s*\+?=|document\.write\(|dangerouslySetInnerHTML|v-html"""),
            "textContent use karo ya input ko sanitize karo (DOMPurify).", WEB),
        Rule("MED", "Insecure deserialization", Regex("""pickle\.loads?\(|unserialize\(|ObjectInputStream"""), "Untrusted data ko deserialize mat karo; JSON use karo."),
        Rule("MED", "Weak hash (MD5/SHA1)", Regex("""(?i)\b(md5|sha1)\s*\(|MessageDigest\.getInstance\(\s*["'](MD5|SHA-?1)"""),
            "Password ke liye bcrypt/argon2, integrity ke liye SHA-256."),
        Rule("MED", "Debug mode ON", Regex("""debug\s*=\s*True|APP_DEBUG\s*=\s*true"""), "Production me debug band karo."),
        Rule("MED", "Cleartext http:// URL",
            Regex("""http://(?!localhost|127\.0\.0\.1|10\.|192\.168\.|schemas\.|www\.w3\.org|xmlns)[a-zA-Z0-9.-]+"""),
            "https:// use karo."),
        Rule("MED", "CORS sab ko allow", Regex("""(?i)access-control-allow-origin['"]?\s*[:,=]\s*['"]?\*|origin\s*:\s*['"]\*['"]"""),
            "Sirf apne domains allow karo."),
        Rule("MED", "Token localStorage me", Regex("""localStorage\.setItem\(\s*["'][^"']*(token|jwt|auth|session)"""),
            "HttpOnly + Secure cookie behtar hai (XSS se token chori nahi hota)."),
        Rule("MED", "Android: cleartext/backup/file access",
            Regex("""usesCleartextTraffic\s*=\s*"true"|allowBackup\s*=\s*"true"|addJavascriptInterface\(|MODE_WORLD_(READABLE|WRITEABLE)|setAllowFileAccessFromFileURLs\(true\)"""),
            "Cleartext/backup band karo; WebView bridge sirf trusted content par."),
        Rule("LOW", "Math.random se token/secret", Regex("""(?i)(token|otp|secret|session|password)[^\n]*Math\.random\(\)|Math\.random\(\)[^\n]*(token|otp|secret|session|password)"""),
            "crypto.getRandomValues / secrets module use karo.", WEB),
        Rule("LOW", "target=_blank bina noopener", Regex("""target\s*=\s*["']_blank["'](?![^>]*noopener)"""), "rel=\"noopener noreferrer\" lagao.", WEB),
        Rule("LOW", "Inline event handler (CSP ke khilaf)", Regex("""\son(click|load|error|mouseover)\s*="""),
            "addEventListener use karo taaki strict CSP lag sake.", setOf("html", "htm", "vue", "php")),
        Rule("LOW", "TODO/FIXME security se juda",
            Regex("""(?i)(todo|fixme|hack).*(auth|password|secure|validate|sanitize)"""), "Release se pehle poora karo.")
    )

    private val SENSITIVE_NAMES = listOf(
        Regex("""^\.env(\..+)?$""") to "[HIGH] .env file project me hai - agar repo public hai to secrets leak. .gitignore me daalo, keys badlo.",
        Regex("""(?i)^id_(rsa|ed25519|ecdsa)$|\.pem$""") to "[HIGH] private key jaisi file: ",
        Regex("""(?i)serviceaccount.*\.json$|credentials\.json$""") to "[HIGH] cloud credentials file: ",
        Regex("""(?i)\.(jks|keystore)$""") to "[MED] signing keystore repo me: ",
        Regex("""(?i)^google-services\.json$""") to "[INFO] google-services.json (key restrict karna na bhoolo): "
    )

    fun scan(project: String): String {
        val dir = Workspace.projectDir(project)
        val findings = ArrayList<String>()
        val counts = HashMap<String, Int>()
        var files = 0
        val walk = dir.walkTopDown().onEnter { it == dir || it.name !in Workspace.SKIP_DIRS }.filter { it.isFile }
        for (f in walk) {
            val rel = f.relativeTo(dir).path
            for ((re, msg) in SENSITIVE_NAMES) {
                if (re.containsMatchIn(f.name)) {
                    val line = if (msg.endsWith(": ")) msg + rel else "$msg ($rel)"
                    findings.add(line)
                    val sev = line.substring(1, line.indexOf(']'))
                    counts[sev] = (counts[sev] ?: 0) + 1
                }
            }
            val ext = f.extension.lowercase()
            if (ext !in TEXT_EXT || f.length() > 400000) continue
            files++
            val lines = try { f.readLines() } catch (e: Exception) { emptyList() }
            val perRule = HashMap<String, Int>()
            for ((i, line) in lines.withIndex()) {
                if (line.length > 600) continue
                for (r in rules) {
                    if (r.exts != null && ext !in r.exts) continue
                    if ((perRule[r.title] ?: 0) >= 3) continue
                    if (r.re.containsMatchIn(line)) {
                        perRule[r.title] = (perRule[r.title] ?: 0) + 1
                        counts[r.sev] = (counts[r.sev] ?: 0) + 1
                        if (findings.size < 120) {
                            findings.add("[" + r.sev + "] " + rel + ":" + (i + 1) + "  " + r.title + "\n    > " + line.trim().take(110) + "\n    FIX: " + r.fix)
                        }
                    }
                }
            }
        }
        val order = listOf("HIGH", "MED", "LOW", "INFO")
        findings.sortBy { x -> order.indexOfFirst { x.startsWith("[$it]") }.let { if (it < 0) 9 else it } }
        val head = "AUDIT '" + Workspace.safe(project) + "': " + files + " files scan hui. HIGH=" + (counts["HIGH"] ?: 0) +
            " MED=" + (counts["MED"] ?: 0) + " LOW=" + (counts["LOW"] ?: 0) + "\n"
        if (findings.isEmpty()) return head + "Koi pattern-based issue nahi mila. (Ye regex scan hai - logic bugs/auth flaws ke liye manual review bhi karo.)"
        return head + findings.joinToString("\n") + "\nNOTE: pattern-based scan hai; false positive ho sakte hain aur kuch bugs (logic/auth) isse nahi pakde jaate."
    }
}

object ProjectTools {
    /** index.html me local CSS/JS inline karke ek single page banata hai (sandbox offline hai). */
    fun bundleHtml(project: String, entry: String): String {
        var html = Workspace.read(project, entry, 400000)
        if (html.startsWith("ERROR")) return html
        val baseDir = entry.substringBeforeLast('/', "")
        fun rel(p: String): String = if (baseDir.isEmpty()) p else "$baseDir/$p"
        fun local(u: String): Boolean = !(u.startsWith("http") || u.startsWith("//") || u.startsWith("data:"))
        html = Regex("""<link[^>]+href=["']([^"']+\.css)["'][^>]*>""", RegexOption.IGNORE_CASE).replace(html) { m ->
            val u = m.groupValues[1]
            if (!local(u)) m.value else {
                val css = Workspace.read(project, rel(u), 400000)
                if (css.startsWith("ERROR")) m.value else "<style>$css</style>"
            }
        }
        html = Regex("""<script([^>]*)src=["']([^"']+\.js)["']([^>]*)></script>""", RegexOption.IGNORE_CASE).replace(html) { m ->
            val u = m.groupValues[2]
            if (!local(u)) m.value else {
                val js = Workspace.read(project, rel(u), 400000)
                if (js.startsWith("ERROR")) m.value else "<script>" + js.replace("</script>", "<\\/script>") + "</script>"
            }
        }
        return html
    }
}
