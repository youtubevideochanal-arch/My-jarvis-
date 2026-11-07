package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    /** Provider ka asli error saaf bhasha me + kya karna hai. */
    fun explain(slot: Slot, prefs: Prefs, e: ApiException): String {
        var raw = (e.message ?: "").trim()
        try {
            val j = JSONObject(raw)
            val eo = j.optJSONObject("error")
            raw = eo?.optString("message")?.ifBlank { null } ?: j.optString("error").ifBlank { null } ?: j.optString("message").ifBlank { raw }
        } catch (x: Exception) { }
        val key = prefs.key(slot).trim()
        val hint = when {
            e.code == 401 || (e.code == 400 && raw.contains("API key", true)) || e.code == 403 && raw.contains("key", true) -> {
                val fmt = if (slot.kind == Kind.GEMINI && !key.startsWith("AIza")) " Gemini key 'AIza' se shuru hoti hai - ye wo nahi lagti (shayad kisi aur provider ki key yahan paste ho gayi)." else ""
                " -> KEY GALAT / EXPIRE / REVOKE hai." + fmt + " Provider ke dashboard se nayi key banao aur poori copy karke paste karo (aage-peechhe space nahi)."
            }
            e.code == 429 -> " -> Limit/quota khatam; thodi der ruko ya doosri API ON rakho (apne aap badal jayegi)."
            e.code == 404 -> " -> Model/endpoint galat. 'Key se model khud pehchano' dabao."
            e.code == -1 -> " -> Internet/DNS dikkat."
            else -> ""
        }
        return "[" + e.code + "] " + raw.take(140) + hint
    }

    private class Res(val text: String, val tokens: Int)

    /** maxTok chhota rakho (tokens bachte hain). Agent step ke liye ~1200, baat-cheet ~700, code likhne ke liye 3500. */
    fun chat(slot: Slot, prefs: Prefs, msgs: List<Msg>, maxTok: Int = 1500): String {
        val key = prefs.key(slot).trim()
        val endpoint = prefs.endpoint(slot).trim()
        if (key.isEmpty()) throw ApiException(401, "API key khaali hai")
        if (endpoint.isEmpty() || prefs.model(slot).trim().isEmpty()) throw ApiException(400, "endpoint/model khaali hai")
        var healed = false
        while (true) {
            val model = prefs.model(slot).trim()
            try {
                val r = if (slot.kind == Kind.GEMINI) gemini(endpoint, key, model, msgs, maxTok) else openai(endpoint, key, model, msgs, maxTok)
                prefs.addUsage(slot.id, r.tokens)
                return r.text
            } catch (e: ApiException) {
                val modelProblem = e.code == 404 || (e.code == 400 && (e.message ?: "").contains("model", true)) ||
                    (e.code == 403 && (e.message ?: "").contains("model", true))
                if (modelProblem && !healed) {
                    healed = true
                    val pick = try { pickModel(slot, endpoint, key, model) } catch (x: Exception) { null }
                    if (pick != null && pick != model) {
                        prefs.setModel(slot, pick)
                        Hub.log("🛠 Model '$model' nahi mila -> '$pick' laga diya (khud theek kiya).")
                        continue
                    }
                }
                throw e
            }
        }
    }

    /** Provider ki /models list se milta-julta chalta hua model chuno. */
    private fun pickModel(slot: Slot, endpoint: String, key: String, old: String): String? {
        val ids = ArrayList<String>()
        if (slot.kind == Kind.GEMINI) {
            val base = endpoint.substringBefore(":generateContent").substringBeforeLast("/models") + "/models"
            val body = get(base, mapOf("x-goog-api-key" to key))
            val arr = JSONObject(body).optJSONArray("models") ?: return null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val methods = o.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                if (methods.contains("generateContent")) ids.add(o.optString("name").removePrefix("models/"))
            }
            return ids.firstOrNull { it.contains("flash") && !it.contains("preview") && !it.contains("exp") && !it.contains("lite") }
                ?: ids.firstOrNull { it.contains("flash") } ?: ids.firstOrNull()
        }
        val url = endpoint.replace(Regex("/chat/completions.*$"), "/models")
        val body = get(url, mapOf("Authorization" to "Bearer $key"))
        val arr = JSONObject(body).optJSONArray("data") ?: return null
        for (i in 0 until arr.length()) ids.add(arr.getJSONObject(i).optString("id"))
        val bad = Regex("whisper|guard|tts|embed|orpheus|playai|moderation|image|vision-only|rerank|transcri", RegexOption.IGNORE_CASE)
        val good = ids.filter { it.isNotBlank() && !bad.containsMatchIn(it) }
        val fam = old.lowercase().replace(Regex("^[^/]*/"), "").split('-', '.', ':', '_').firstOrNull { it.length >= 3 } ?: ""
        return good.firstOrNull { it.lowercase().contains("gpt-oss-120b") && old.contains("gpt-oss") }
            ?: good.firstOrNull { fam.isNotEmpty() && it.lowercase().contains(fam) && it != old }
            ?: good.firstOrNull { it != old }
    }

    /** Photo API: image + sawal -> jawab (Gemini vision). */
    fun describeImage(prefs: Prefs, b64: String, question: String, mime: String = "image/jpeg"): String {
        val key = prefs.photoKey.trim()
        if (key.isEmpty()) throw ApiException(401, "Photo API key khaali hai")
        val parts = JSONArray()
            .put(JSONObject().put("text", question))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", b64)))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 800))
        val res = post(geminiUrl(prefs.photoEndpoint.trim(), prefs.photoModel.trim()), mapOf("x-goog-api-key" to key), body.toString(), 60000)
        return geminiText(res).text
    }

    private fun openai(endpoint: String, key: String, model: String, msgs: List<Msg>, maxTok: Int): Res {
        val arr = JSONArray()
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.content))
        fun body(reason: Boolean): String {
            val b = JSONObject().put("model", model).put("messages", arr).put("temperature", 0.2).put("max_tokens", maxTok)
            // gpt-oss "reasoning" tokens bahut khate hain; low rakhne se Groq ke token jaldi khatam nahi hote
            if (reason && model.lowercase().contains("gpt-oss")) b.put("reasoning_effort", "low")
            return b.toString()
        }
        val hdr = mapOf("Authorization" to "Bearer $key")
        val res = try {
            post(endpoint, hdr, body(true))
        } catch (e: ApiException) {
            if (e.code == 400 && (e.message ?: "").contains("reason", true)) post(endpoint, hdr, body(false)) else throw e
        }
        try {
            val j = JSONObject(res)
            val msg = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val tokens = j.optJSONObject("usage")?.optInt("total_tokens", 0) ?: 0
            var text = msg.optString("content", "")
            if (text.isBlank()) text = msg.optString("reasoning", "")
            if (text.isBlank()) throw ApiException(-3, "khaali jawab aaya (max_tokens kam pad gaya ho sakta hai)")
            return Res(text, tokens)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(-3, "response samajh nahi aaya: " + res.take(150))
        }
    }

    private fun geminiUrl(base: String, model: String): String =
        if (base.contains(":generateContent")) base else base.trimEnd('/') + "/" + model + ":generateContent"

    private fun gemini(base: String, key: String, model: String, msgs: List<Msg>, maxTok: Int): Res {
        val sys = msgs.filter { it.role == "system" }.joinToString("\n") { it.content }
        val contents = JSONArray()
        var lastRole = ""
        var buf = StringBuilder()

        fun flush() {
            if (lastRole.isNotEmpty()) {
                contents.put(
                    JSONObject().put("role", lastRole)
                        .put("parts", JSONArray().put(JSONObject().put("text", buf.toString())))
                )
            }
            buf = StringBuilder()
        }

        for (m in msgs) {
            if (m.role == "system") continue
            val r = if (m.role == "assistant") "model" else "user"
            if (r != lastRole) { flush(); lastRole = r }
            if (buf.isNotEmpty()) buf.append("\n\n")
            buf.append(m.content)
        }
        flush()

        val body = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", maxTok))
        if (sys.isNotEmpty()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
        }
        val res = post(geminiUrl(base, model), mapOf("x-goog-api-key" to key), body.toString())
        return geminiText(res)
    }

    private fun geminiText(res: String): Res {
        try {
            val j = JSONObject(res)
            val parts = j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            if (sb.isEmpty()) throw ApiException(-3, "khaali jawab aaya")
            val tk = j.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0
            return Res(sb.toString(), tk)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(-3, "response samajh nahi aaya: " + res.take(150))
        }
    }

    private fun get(url: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "MyAgent/4.0")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (c.responseCode !in 200..299) throw ApiException(c.responseCode, "models list HTTP " + c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun retryHint(code: Int, header: String?, text: String): Long {
        if (code != 429 && code != 503) return 0
        header?.toDoubleOrNull()?.let { return (it * 1000).toLong() }
        val m = Regex("(?:try again|retry)[^0-9]{0,20}(?:in\\s*)?(?:(\\d+)m)?\\s*(\\d+(?:\\.\\d+)?)s", RegexOption.IGNORE_CASE).find(text)
        if (m != null) {
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toDoubleOrNull() ?: 0.0
            return min * 60000L + (sec * 1000).toLong()
        }
        return 8000
    }

    private fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int = 45000): String {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = timeoutMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "MyAgent/4.0")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(code, text.take(300), retryHint(code, c.getHeaderField("retry-after"), text))
            return text
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(-1, e.javaClass.simpleName + ": " + (e.message ?: ""))
        } finally {
            conn?.disconnect()
        }
    }
}
