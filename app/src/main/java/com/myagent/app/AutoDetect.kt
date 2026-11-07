package com.myagent.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * API key paste karte hi: kaunsa provider hai, kaunse models milte hain, sabse achha chalta hua model kaun sa -
 * sab khud pehchan kar laga deta hai. Galat slot me key daali ho to sahi slot me bhej deta hai.
 */
object AutoDetect {

    class Prov(
        val id: String, val name: String, val slotId: String?,
        val chat: String, val models: String, val gemini: Boolean, val prefer: List<String>
    )

    class Result(val ok: Boolean, val msg: String, val routedTo: Slot?)

    private val PROVS = listOf(
        Prov("groq", "Groq", "groq", "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models", false,
            listOf("gpt-oss-120b", "llama-3.3-70b", "qwen3", "gpt-oss-20b", "llama-3.1-8b")),
        Prov("gemini", "Gemini", "gemini", Slots.GEMINI_BASE, Slots.GEMINI_BASE, true,
            listOf("gemini-2.5-flash", "gemini-2.0-flash", "flash-latest", "gemini-2.5-pro", "flash")),
        Prov("openrouter", "OpenRouter", "openrouter", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/models", false,
            listOf("llama-3.3-70b-instruct:free", "gpt-oss-120b:free", "qwen3", "deepseek", ":free")),
        Prov("venice", "Venice", "venice", "https://api.venice.ai/api/v1/chat/completions", "https://api.venice.ai/api/v1/models", false,
            listOf("llama-3.3-70b", "qwen", "mistral", "deepseek")),
        Prov("xai", "xAI Grok", null, "https://api.x.ai/v1/chat/completions", "https://api.x.ai/v1/models", false, listOf("grok-4", "grok-3", "grok")),
        Prov("cerebras", "Cerebras", null, "https://api.cerebras.ai/v1/chat/completions", "https://api.cerebras.ai/v1/models", false,
            listOf("gpt-oss-120b", "llama-3.3-70b", "qwen")),
        Prov("nvidia", "NVIDIA NIM", null, "https://integrate.api.nvidia.com/v1/chat/completions", "https://integrate.api.nvidia.com/v1/models", false,
            listOf("llama-3.3-70b", "llama-3.1-70b", "qwen")),
        Prov("hf", "HuggingFace", null, "https://router.huggingface.co/v1/chat/completions", "https://router.huggingface.co/v1/models", false,
            listOf("gpt-oss-120b", "llama-3.3-70b", "qwen")),
        Prov("openai", "OpenAI", null, "https://api.openai.com/v1/chat/completions", "https://api.openai.com/v1/models", false,
            listOf("gpt-5-mini", "gpt-4.1-mini", "gpt-4o-mini", "gpt-5", "gpt-4.1")),
        Prov("deepseek", "DeepSeek", null, "https://api.deepseek.com/chat/completions", "https://api.deepseek.com/models", false, listOf("deepseek-chat"))
    )

    private fun prov(id: String): Prov = PROVS.first { it.id == id }

    /** Key ke shuruaat se andaza (jahan pakka pata hai wahin). */
    fun guess(key: String): List<Prov> {
        val k = key.trim()
        return when {
            k.startsWith("gsk_") -> listOf(prov("groq"))
            k.startsWith("AIza") -> listOf(prov("gemini"))
            k.startsWith("sk-or-") -> listOf(prov("openrouter"))
            k.startsWith("xai-") -> listOf(prov("xai"))
            k.startsWith("csk-") -> listOf(prov("cerebras"))
            k.startsWith("nvapi-") -> listOf(prov("nvidia"))
            k.startsWith("hf_") -> listOf(prov("hf"))
            k.startsWith("sk-proj-") -> listOf(prov("openai"))
            k.startsWith("sk-") && !k.startsWith("sk-ant") -> listOf(prov("openai"), prov("deepseek"))
            else -> emptyList()
        }
    }

    private val BAD = Regex("embed|whisper|tts|guard|moderation|image|dall|rerank|ocr|speech|audio|transcri|stable|flux|safeguard|orpheus|playai|realtime|-live|imagen|veo|aqa|robotics|computer-use|learnlm|gemma-3n|vision-preview", RegexOption.IGNORE_CASE)

    private fun http(url: String, headers: Map<String, String>): Pair<Int, String> {
        var c: HttpURLConnection? = null
        return try {
            val h = URL(url).openConnection() as HttpURLConnection
            c = h
            h.connectTimeout = 12000
            h.readTimeout = 15000
            h.setRequestProperty("User-Agent", "MyAgent/4.0")
            for ((k, v) in headers) h.setRequestProperty(k, v)
            val code = h.responseCode
            val s = (if (code in 200..299) h.inputStream else h.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            Pair(code, s)
        } catch (e: Exception) {
            Pair(-1, e.javaClass.simpleName + " " + (e.message ?: ""))
        } finally {
            c?.disconnect()
        }
    }

    /** (code, models). code 200 = key sahi. */
    private fun listModels(p: Prov, key: String): Pair<Int, List<String>> {
        val headers = if (p.gemini) mapOf("x-goog-api-key" to key) else mapOf("Authorization" to "Bearer $key")
        val (code, body) = http(p.models, headers)
        if (code != 200) return Pair(code, emptyList())
        val out = ArrayList<String>()
        try {
            val j = JSONObject(body)
            if (p.gemini) {
                val a = j.optJSONArray("models")
                if (a != null) for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val m = o.optJSONArray("supportedGenerationMethods")
                    var gen = false
                    if (m != null) for (x in 0 until m.length()) if (m.getString(x) == "generateContent") gen = true
                    if (gen) out.add(o.optString("name").removePrefix("models/"))
                }
            } else {
                val a = j.optJSONArray("data")
                if (a != null) for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val type = o.optString("type")
                    if (type.isNotEmpty() && type != "text" && type != "chat") continue
                    val id = o.optString("id")
                    if (id.isNotEmpty()) out.add(id)
                }
            }
        } catch (e: Exception) {
            return Pair(-3, emptyList())
        }
        return Pair(200, out)
    }

    /** Achhe se bure ke kram me model ids. */
    fun rank(ids: List<String>, prefer: List<String>, freeOnly: Boolean): List<String> {
        var list = ids.filter { !BAD.containsMatchIn(it) }
        if (freeOnly && list.any { it.endsWith(":free") }) list = list.filter { it.endsWith(":free") }
        fun score(id: String): Double {
            val l = id.lowercase()
            val idx = prefer.indexOfFirst { l.contains(it) }
            var s = if (idx >= 0) 1000.0 - idx * 50 else 0.0
            val b = Regex("(\\d+(?:\\.\\d+)?)b").find(l)?.groupValues?.get(1)?.toDoubleOrNull()
            if (b != null) s += b.coerceAtMost(500.0) * 0.2
            if (Regex("preview|exp|beta|-latest$|deprecated").containsMatchIn(l)) s -= 8
            return s - l.length * 0.01
        }
        return list.sortedByDescending { score(it) }
    }

    private fun targetSlot(p: Prov, current: Slot, prefs: Prefs): Slot {
        if (p.slotId != null) return Slots.byId(p.slotId) ?: current
        if (current.future || current.id == "ollama") return current
        val free = Slots.all.firstOrNull { it.future && prefs.key(it).isBlank() }
        return free ?: Slots.all.first { it.id == "future1" }
    }

    /** Key paste hone par chalao (background thread me). */
    fun run(slot: Slot, keyIn: String, prefs: Prefs): Result {
        val key = keyIn.trim()
        if (key.length < 8) return Result(false, "Key bahut chhoti hai", null)
        if (key.startsWith("sk-ant")) return Result(false, "Anthropic key abhi seedhi support nahi (OpenAI-style API chahiye). OpenRouter ya Groq/Gemini key use karo.", null)

        var cands = guess(key)
        if (cands.isEmpty()) {
            // prefix se pata nahi: sirf isi slot ka apna provider (key kisi anjaan jagah nahi bhejte)
            val own = PROVS.firstOrNull { it.slotId == slot.id }
            if (own != null) cands = listOf(own)
            else if (slot.future || slot.id == "ollama") return localOrCustom(slot, key, prefs)
        }
        if (cands.isEmpty()) return Result(false, "Is key ka provider pehchan nahi paya. Endpoint khud daalo.", null)

        var lastCode = 0
        for (p in cands) {
            val (code, ids) = listModels(p, key)
            lastCode = code
            if (code == 401 || code == 403 || code == 400) continue
            if (code != 200) return Result(false, p.name + ": list nahi mili (" + code + "). Internet check karo.", null)
            val ranked = rank(ids, p.prefer, p.id == "openrouter")
            if (ranked.isEmpty()) return Result(false, p.name + " key sahi hai par chalne layak model nahi mila.", null)

            val target = targetSlot(p, slot, prefs)
            val oldModel = prefs.model(target)
            val oldEndpoint = prefs.endpoint(target)
            val routed = if (target.id != slot.id) target else null
            if (routed != null) {
                prefs.setKey(slot, "") // galat jagah se hatao
                if (prefs.on(slot) && slot.future) prefs.setOn(slot, false)
            }
            prefs.setKey(target, key)
            prefs.setEndpoint(target, p.chat)
            if (target.future || target.id == "ollama") prefs.setName(target, p.name)
            prefs.setOn(target, true)

            var tested = 0
            for (m in ranked.take(4)) {
                prefs.setModel(target, m)
                tested++
                try {
                    ApiClient.chat(target, prefs, listOf(Msg("user", "Reply with the single word OK")), 24)
                    return Result(true, "✅ " + p.name + " pehchani. Model laga diya: " + prefs.model(target) + "  (" + ids.size + " models mile)" +
                        (if (routed != null) "\n➡ Key sahi jagah (" + p.name + " card) me daal di." else ""), routed)
                } catch (e: ApiException) {
                    if (e.code == 429) {
                        return Result(true, "✅ " + p.name + " key sahi hai, model " + m + " laga diya (abhi rate-limit hai, thodi der baad chalega).", routed)
                    }
                    if (e.code == 401 || e.code == 403 || (e.code == 400 && (e.message ?: "").contains("key", true))) {
                        prefs.setModel(target, oldModel); prefs.setEndpoint(target, oldEndpoint)
                        return Result(false, "❌ " + p.name + " ne key reject ki " + ApiClient.explain(target, prefs, e), routed)
                    }
                }
            }
            return Result(false, "⚠ " + p.name + " key sahi hai par " + tested + " model try karke bhi jawab nahi mila. Model box me khud naam daalo.", routed)
        }
        return Result(false, "❌ Key kisi pehchane provider ne accept nahi ki (" + lastCode + "). Key dobara copy karo.", null)
    }

    /** Future slot / local server: user ke diye endpoint se /models padho. */
    private fun localOrCustom(slot: Slot, key: String, prefs: Prefs): Result {
        val ep = prefs.endpoint(slot)
        if (ep.isBlank()) return Result(false, "Pehle Endpoint daalo (https://.../v1/chat/completions), phir key se model pehchana jayega.", null)
        val modelsUrl = ep.substringBefore("/chat/completions").trimEnd('/') + "/models"
        val (code, body) = http(modelsUrl, mapOf("Authorization" to "Bearer $key"))
        if (code != 200) return Result(false, "Models list nahi mili [" + code + "]. Endpoint/key check karo.", null)
        val ids = ArrayList<String>()
        try {
            val a = JSONObject(body).optJSONArray("data")
            if (a != null) for (i in 0 until a.length()) ids.add(a.getJSONObject(i).optString("id"))
        } catch (e: Exception) { }
        val ranked = rank(ids, listOf("llama-3.3-70b", "qwen", "gpt-oss", "llama"), false)
        if (ranked.isEmpty()) return Result(false, "Endpoint chal raha hai par model list khaali.", null)
        prefs.setModel(slot, ranked[0])
        prefs.setOn(slot, true)
        return Result(true, "✅ Model laga diya: " + ranked[0] + "  (" + ids.size + " mile)", null)
    }
}
