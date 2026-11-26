package com.myagent.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ApiError(val code: Int, msg: String) : Exception(msg)

/**
 * Talks to Gemini / ChatGPT / Claude / your own OpenAI-compatible model.
 * The user only gives a key. The model is discovered from the provider's own list, and if a model
 * is retired, busy or renamed the app switches to the next best one by itself.
 */
object Llm {
    /** Short, human reason for a failure (raw API errors are never shown to the user). */
    fun short(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        val code = (e as? ApiError)?.code
        return when {
            "no longer available" in m || code == 404 -> "model badal gaya"
            code == 503 || code == 529 || "high demand" in m || "overloaded" in m -> "server busy"
            code == 429 || "quota" in m || "rate limit" in m -> "limit khatam"
            code == 401 || code == 403 || "api key" in m || "permission" in m -> "key galat ya band"
            "no protocol" in m || "url" in m -> "URL galat"
            "unable to resolve" in m || "timeout" in m || "timed out" in m || "connect" in m -> "server nahi mila"
            else -> m.replace("\n", " ").take(60)
        }
    }

    fun chat(c: Context, system: String, msgs: List<Msg>, maxTokens: Int): String {
        val order = Prefs.llmOrder(c)
        if (order.isEmpty() && !AppBrain.on(c)) {
            throw Exception("Koi API key nahi hai. Menu > API keys me key daalo.")
        }
        val errs = ArrayList<String>()
        for (p in order) {
            try {
                val out = call(c, p, system, normalize(msgs), maxTokens)
                if (out.isBlank()) throw Exception("khaali jawab")
                return out
            } catch (e: Exception) {
                errs.add((Prefs.LABEL[p] ?: p) + ": " + short(e))
            }
        }
        if (AppBrain.on(c)) {
            try {
                return AppBrain.ask(c, system, normalize(msgs))
            } catch (e: Exception) {
                errs.add("Apps: " + (e.message ?: "fail").take(60))
            }
        }
        throw Exception(errs.joinToString("\n"))
    }

    // ---------- coding specialist (your own / local model) ----------

    fun coderReady(c: Context): Boolean = "CUSTOM" in Prefs.savedProviders(c)

    fun coder(c: Context, q: String, maxTokens: Int): String {
        if (!coderReady(c)) return "coder model is not set up"
        val sys = "You are an expert programmer and debugger. Give correct, complete, minimal code and a brief explanation. For bugs: state the cause, then the fix."
        return try {
            call(c, "CUSTOM", sys, listOf(Msg("user", q)), maxTokens)
        } catch (e: Exception) {
            "coder failed: " + short(e)
        }
    }

    private fun normalize(m: List<Msg>): List<Msg> {
        val out = ArrayList<Msg>()
        for (x in m) {
            if (out.isNotEmpty() && out.last().role == x.role) {
                out[out.size - 1] = Msg(x.role, out.last().text + "\n" + x.text)
            } else {
                out.add(x)
            }
        }
        while (out.isNotEmpty() && out[0].role != "user") out.removeAt(0)
        return out
    }

    private fun suggested(msg: String): String? {
        val m = Regex("use\\s+models/\\s*([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE).findAll(msg).lastOrNull()
        return m?.groupValues?.get(1)
    }

    private fun call(c: Context, p: String, system: String, msgs: List<Msg>, maxTokens: Int): String {
        val key = Prefs.key(c, p)
        var model = if (p == "CUSTOM" && Prefs.customModel(c).isNotBlank()) Prefs.customModel(c) else Prefs.model(c, p)
        if (model.isBlank()) {
            model = loadCandidates(c, p, key).firstOrNull() ?: throw Exception("koi model nahi mila")
        }
        val tried = HashSet<String>()
        var last: Exception? = null
        for (attempt in 0 until 4) {
            try {
                val out = doCall(c, p, key, model, system, msgs, maxTokens)
                Prefs.setModel(c, p, model)
                return out
            } catch (e: java.io.IOException) {
                last = e
                if (attempt >= 1) throw e
                Thread.sleep(500)
                continue
            } catch (e: ApiError) {
                last = e
                val msg = e.message ?: ""
                val transient = e.code == 503 || e.code == 500 || e.code == 429 || e.code == 529
                val gone = e.code == 404 || (e.code == 400 && msg.contains("model", true))
                if (transient && attempt == 0) {
                    Thread.sleep(800)
                    continue
                }
                if (!transient && !gone) throw e
                tried.add(model)
                var next = suggested(msg)
                if (next != null && next in tried) next = null
                if (next == null) {
                    next = try {
                        loadCandidates(c, p, key).firstOrNull { it !in tried }
                    } catch (x: Exception) {
                        null
                    }
                }
                if (next == null) throw e
                model = next
            }
        }
        throw last ?: Exception("failed")
    }

    private fun errMsg(body: String): String {
        return try {
            val t = body.trim()
            val o: JSONObject? =
                if (t.startsWith("[")) JSONArray(t).optJSONObject(0) else JSONObject(t)
            val e = o?.opt("error")
            when (e) {
                is JSONObject -> e.optString("message", t.take(600))
                is String -> e
                else -> t.take(600)
            }
        } catch (x: Exception) {
            body.take(600)
        }
    }

    private fun cleanBase(c: Context): String {
        val base = Prefs.customBase(c).trim().trimEnd('/')
        if (!(base.startsWith("http://") || base.startsWith("https://"))) {
            throw Exception("URL galat (http:// ya https:// se shuru hona chahiye)")
        }
        return base
    }

    private fun openAiStyle(
        url: String, key: String, model: String, system: String, msgs: List<Msg>,
        tokenField: String, budget: Int, timeout: Int = 45000
    ): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", system))
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.text))
        val body = JSONObject().put("model", model).put("messages", arr).put(tokenField, budget)
        val r = Http.request(
            "POST", url,
            mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"),
            body.toString(), timeout
        )
        if (r.code != 200) throw ApiError(r.code, errMsg(r.body))
        val ch = JSONObject(r.body).getJSONArray("choices")
        return ch.getJSONObject(0).getJSONObject("message").optString("content", "")
    }

    private fun doCall(
        c: Context, p: String, key: String, model: String, system: String, msgs: List<Msg>, maxTokens: Int
    ): String {
        when (p) {
            "CLAUDE" -> {
                val arr = JSONArray()
                for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.text))
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", maxTokens)
                    .put("system", system)
                    .put("messages", arr)
                val r = Http.request(
                    "POST", "https://api.anthropic.com/v1/messages",
                    mapOf(
                        "x-api-key" to key,
                        "anthropic-version" to "2023-06-01",
                        "content-type" to "application/json"
                    ),
                    body.toString(), 40000
                )
                if (r.code != 200) throw ApiError(r.code, errMsg(r.body))
                val content = JSONObject(r.body).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.optString("text"))
                }
                return sb.toString()
            }
            "OPENAI" -> {
                // reasoning models spend part of the budget thinking, so give them more room
                val budget = if (model.startsWith("gpt-5") || model.startsWith("o")) maxTokens * 4 else maxTokens
                return openAiStyle(
                    "https://api.openai.com/v1/chat/completions", key, model, system, msgs,
                    "max_completion_tokens", budget
                )
            }
            "CUSTOM" -> {
                return openAiStyle(cleanBase(c) + "/chat/completions", key, model, system, msgs, "max_tokens", maxTokens, 180000)
            }
            "GEMINI" -> {
                val contents = JSONArray()
                for (m in msgs) {
                    contents.put(
                        JSONObject()
                            .put("role", if (m.role == "assistant") "model" else "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
                    )
                }
                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                    )
                    .put("contents", contents)
                    .put(
                        "generationConfig",
                        JSONObject().put("maxOutputTokens", maxOf(maxTokens * 6, 4096)).also {
                            if (model.startsWith("gemini-3")) it.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
                        }
                    )
                val r = Http.request(
                    "POST",
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"),
                    body.toString(), 30000
                )
                if (r.code != 200) throw ApiError(r.code, errMsg(r.body))
                val cands = JSONObject(r.body).optJSONArray("candidates")
                    ?: throw ApiError(500, "empty reply")
                val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    ?: throw ApiError(500, "empty reply")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (!part.optBoolean("thought", false)) sb.append(part.optString("text"))
                }
                if (sb.isBlank()) throw ApiError(500, "empty reply")
                return sb.toString()
            }
        }
        throw Exception("unknown provider")
    }

    // ---------------- model discovery (best first) ----------------

    private fun version(id: String, re: Regex): Double =
        re.find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    private val candCache = HashMap<String, List<String>>()

    private fun loadCandidates(c: Context, p: String, key: String): List<String> {
        val ck = p + "|" + Prefs.smart(c) + "|" + key.takeLast(6)
        candCache[ck]?.let { return it }
        val out = loadCandidates2(c, p, key)
        if (out.isNotEmpty()) candCache[ck] = out
        return out
    }

    private fun loadCandidates2(c: Context, p: String, key: String): List<String> {
        val smart = Prefs.smart(c)
        when (p) {
            "CLAUDE" -> {
                val r = Http.request(
                    "GET", "https://api.anthropic.com/v1/models?limit=100",
                    mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), null, 30000
                )
                if (r.code != 200) throw ApiError(r.code, errMsg(r.body))
                val data = JSONObject(r.body).getJSONArray("data")
                val ids = ArrayList<String>()
                for (i in 0 until data.length()) ids.add(data.getJSONObject(i).getString("id"))
                // the list comes newest-first
                val a = if (smart) "sonnet" else "haiku"
                val b = if (smart) "haiku" else "sonnet"
                return (ids.filter { a in it } + ids.filter { b in it } + ids.filter { "opus" in it }).distinct()
            }
            "OPENAI" -> {
                val r = Http.request(
                    "GET", "https://api.openai.com/v1/models",
                    mapOf("Authorization" to "Bearer $key"), null, 30000
                )
                if (r.code != 200) throw ApiError(r.code, errMsg(r.body))
                val data = JSONObject(r.body).getJSONArray("data")
                val bad = listOf(
                    "audio", "realtime", "image", "tts", "transcribe", "search", "instruct", "embedding",
                    "moderation", "codex", "preview", "chat-latest", "whisper", "vision", "computer",
                    "research", "oss", "pro"
                )
                val dated = Regex("-\\d{4}-\\d{2}-\\d{2}$")
                val short = Regex("-\\d{4}$")
                val ids = ArrayList<String>()
                for (i in 0 until data.length()) {
                    val id = data.getJSONObject(i).getString("id")
                    if (id.startsWith("gpt-") && bad.none { it in id } &&
                        !dated.containsMatchIn(id) && !short.containsMatchIn(id)
                    ) ids.add(id)
                }
                val re = Regex("gpt-(\\d+(?:\\.\\d+)?)")
                val cmp = compareByDescending<String> { version(it, re) }.thenBy { it.length }
                val mini = ids.filter { "mini" in it }.sortedWith(cmp)
                val big = ids.filter { "mini" !in it && "nano" !in it }.sortedWith(cmp)
                val nano = ids.filter { "nano" in it }.sortedWith(cmp)
                return (if (smart) big + mini + nano else mini + big + nano).distinct()
            }
            "GEMINI" -> {
                val r = Http.request(
                    "GET", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                    mapOf("x-goog-api-key" to key), null, 30000
                )
                if (r.code != 200) throw ApiError(r.code, errMsg(r.body))
                val data = JSONObject(r.body).getJSONArray("models")
                val bad = listOf(
                    "tts", "image", "embedding", "live", "audio", "robotics", "computer", "exp",
                    "vision", "learnlm", "gemma", "aqa", "imagen", "veo", "native"
                )
                val names = ArrayList<String>()
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var ok = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") ok = true
                        }
                    }
                    val n = m.getString("name").removePrefix("models/")
                    if (ok && n.startsWith("gemini-") && bad.none { it in n }) names.add(n)
                }
                val re = Regex("gemini-(\\d+(?:\\.\\d+)?)")
                // newest version first: older ones get retired for new users
                val flash = names.filter { "flash" in it && "lite" !in it }.sortedByDescending { version(it, re) }
                val lite = names.filter { "lite" in it }.sortedByDescending { version(it, re) }
                val pro = names.filter { "pro" in it }.sortedByDescending { version(it, re) }
                return (if (smart) pro + flash + lite else flash + pro + lite).distinct()
            }
            "CUSTOM" -> {
                val r = Http.request(
                    "GET", cleanBase(c) + "/models", mapOf("Authorization" to "Bearer $key"), null, 30000
                )
                if (r.code != 200) throw Exception("Model ka naam Custom me daalo")
                val data = JSONObject(r.body).getJSONArray("data")
                val ids = ArrayList<String>()
                for (i in 0 until data.length()) ids.add(data.getJSONObject(i).getString("id"))
                if (ids.isEmpty()) throw Exception("Custom API me koi model nahi")
                return ids
            }
        }
        throw Exception("unknown provider")
    }
}
