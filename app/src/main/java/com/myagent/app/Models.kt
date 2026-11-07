package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject

data class Msg(val role: String, val content: String)

class ApiException(val code: Int, message: String, val retryMs: Long = 0) : Exception(message)

enum class Kind { OPENAI, GEMINI }

data class Slot(
    val id: String,
    val defName: String,
    val kind: Kind,
    val defEndpoint: String,
    val defModel: String,
    val future: Boolean = false
)

object Slots {
    const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    val all: List<Slot> = listOf(
        Slot("groq", "Groq", Kind.OPENAI, "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-120b"),
        Slot("venice", "Venice", Kind.OPENAI, "https://api.venice.ai/api/v1/chat/completions", "llama-3.3-70b"),
        Slot("gemini", "Gemini", Kind.GEMINI, GEMINI_BASE, "gemini-2.0-flash"),
        // Free/extra raste: OpenRouter ke :free models, aur apne PC par Ollama/LM Studio (key me bas "none" likho)
        Slot("openrouter", "OpenRouter (free models)", Kind.OPENAI, "https://openrouter.ai/api/v1/chat/completions", "meta-llama/llama-3.3-70b-instruct:free"),
        Slot("ollama", "Local Ollama / LM Studio (PC par)", Kind.OPENAI, "", "llama3.2"),
        // Neeche ke slots future ke liye: naam aur endpoint aap Settings me daalo (OpenAI-compatible)
        Slot("future1", "Future API 1 (naam baad me)", Kind.OPENAI, "", "", true),
        Slot("future2", "Future API 2 (naam baad me)", Kind.OPENAI, "", "", true)
    )

    fun byId(id: String): Slot? = all.firstOrNull { it.id == id }

    /** Jo ON hain, jinki key bhari hai aur endpoint maujood hai. */
    fun available(p: Prefs): List<Slot> =
        all.filter { p.on(it) && p.key(it).isNotBlank() && p.endpoint(it).isNotBlank() && p.model(it).isNotBlank() }
}

/** Ek chalu task. History poori save hoti hai taaki API badalne par bhi kuch na bhoole. */
class Task(var goal: String) {
    val history = ArrayList<Msg>()
    val notes = ArrayList<String>()
    var step = 0
    /** running | confirm | ask | paused | done */
    var status = "running"
    var activeSlot = ""
    var pending = ""
    var approved = ""

    fun toJson(): JSONObject {
        val j = JSONObject()
        j.put("goal", goal)
        j.put("step", step)
        j.put("status", status)
        j.put("activeSlot", activeSlot)
        j.put("pending", pending)
        j.put("approved", approved)
        val h = JSONArray()
        val start = if (history.size > 60) history.size - 60 else 0
        for (i in start until history.size) {
            h.put(JSONObject().put("r", history[i].role).put("c", history[i].content))
        }
        j.put("history", h)
        val n = JSONArray()
        for (x in notes) n.put(x)
        j.put("notes", n)
        return j
    }

    companion object {
        fun fromJson(j: JSONObject): Task {
            val t = Task(j.optString("goal"))
            t.step = j.optInt("step")
            t.status = j.optString("status", "running")
            t.activeSlot = j.optString("activeSlot")
            t.pending = j.optString("pending")
            t.approved = j.optString("approved")
            val h = j.optJSONArray("history")
            if (h != null) for (i in 0 until h.length()) {
                val o = h.getJSONObject(i)
                t.history.add(Msg(o.optString("r"), o.optString("c")))
            }
            val n = j.optJSONArray("notes")
            if (n != null) for (i in 0 until n.length()) t.notes.add(n.getString(i))
            return t
        }
    }
}

class TaskStore(ctx: android.content.Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("my_agent_task", android.content.Context.MODE_PRIVATE)

    fun save(t: Task) { sp.edit().putString("task", t.toJson().toString()).apply() }

    fun load(): Task? {
        val s = sp.getString("task", null) ?: return null
        return try { Task.fromJson(JSONObject(s)) } catch (e: Exception) { null }
    }

    fun clear() { sp.edit().remove("task").apply() }
}
