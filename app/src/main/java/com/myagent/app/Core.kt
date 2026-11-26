package com.myagent.app

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class App : Application() {
    companion object {
        lateinit var ctx: Context
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Voice.setup(ctx)
        Chat.load()
    }
}

data class Msg(val role: String, val text: String)

/** Chat log with saved history (like ChatGPT's sidebar). UI and overlay listen for changes. */
object Chat {
    const val USER = 0
    const val AGENT = 1
    const val INFO = 2

    class Item(val kind: Int, val text: String)
    private class Conv(val id: Long, var title: String, val items: ArrayList<Item>)

    private val convs = ArrayList<Conv>()
    private var cur: Conv? = null
    val listeners = CopyOnWriteArrayList<() -> Unit>()
    val state = CopyOnWriteArrayList<() -> Unit>()

    private fun file() = java.io.File(App.ctx.filesDir, "chats.json")

    @Synchronized
    fun load() {
        convs.clear()
        try {
            val f = file()
            if (!f.exists()) return
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val items = ArrayList<Item>()
                val ia = o.getJSONArray("items")
                for (j in 0 until ia.length()) {
                    val it = ia.getJSONObject(j)
                    items.add(Item(it.getInt("k"), it.getString("t")))
                }
                convs.add(Conv(o.getLong("id"), o.getString("title"), items))
            }
        } catch (e: Exception) {
        }
    }

    private fun save() {
        try {
            val a = JSONArray()
            for (c in convs) {
                val ia = JSONArray()
                for (it in c.items) ia.put(JSONObject().put("k", it.kind).put("t", it.text))
                a.put(JSONObject().put("id", c.id).put("title", c.title).put("items", ia))
            }
            file().writeText(a.toString())
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun add(kind: Int, text: String) {
        var c = cur
        if (c == null) {
            c = Conv(System.currentTimeMillis(), "New chat", ArrayList())
            convs.add(0, c)
            cur = c
            while (convs.size > 40) convs.removeAt(convs.size - 1)
        }
        c.items.add(Item(kind, text))
        if (kind == USER && c.title == "New chat") c.title = text.replace("\n", " ").take(40)
        if (c.items.size > 200) c.items.removeAt(0)
        save()
        fire()
    }

    @Synchronized
    fun snapshot(): List<Item> = ArrayList(cur?.items ?: ArrayList<Item>())

    @Synchronized
    fun history(): List<Pair<Long, String>> = convs.map { Pair(it.id, it.title) }

    @Synchronized
    fun currentId(): Long = cur?.id ?: -1L

    @Synchronized
    fun open(id: Long) {
        cur = convs.firstOrNull { it.id == id }
        fire()
    }

    @Synchronized
    fun newChat() {
        cur = null
        fire()
    }

    /** Deletes the current chat from history. */
    @Synchronized
    fun clear() {
        val c = cur
        if (c != null) convs.remove(c)
        cur = null
        save()
        fire()
    }

    private fun fire() {
        for (l in listeners) l()
    }

    fun fireState() {
        for (l in state) l()
    }
}

/** AES-GCM encryption with a key that lives in the Android Keystore. */
object Secure {
    private const val ALIAS = "myagent_secret_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return kg.generateKey()
    }

    fun enc(plain: String): String {
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            "e:" + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            "p:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun dec(stored: String): String {
        if (stored.length < 3) return ""
        return try {
            val raw = Base64.decode(stored.substring(2), Base64.NO_WRAP)
            if (stored.startsWith("p:")) return String(raw, Charsets.UTF_8)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

data class Persona(
    val name: String,
    val pitch: Float,
    val rate: Float,
    val lang: String,
    val voice: String,
    val slot: Int,
    val oai: String = "alloy",
    val gem: String = "Kore",
    val style: String = "",
    val sample: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("pitch", pitch.toDouble())
        .put("rate", rate.toDouble())
        .put("lang", lang)
        .put("voice", voice)
        .put("slot", slot)
        .put("oai", oai)
        .put("gem", gem)
        .put("style", style)
        .put("sample", sample)

    companion object {
        fun from(o: JSONObject) = Persona(
            o.optString("name", "Voice"),
            o.optDouble("pitch", 1.0).toFloat(),
            o.optDouble("rate", 1.0).toFloat(),
            o.optString("lang", "hi-IN"),
            o.optString("voice", ""),
            o.optInt("slot", 0),
            o.optString("oai", "alloy"),
            o.optString("gem", "Kore"),
            o.optString("style", ""),
            o.optString("sample", "")
        )
    }
}

object Prefs {
    val LLMS = listOf("CLAUDE", "OPENAI", "GEMINI", "CUSTOM")
    val LABEL = mapOf(
        "CLAUDE" to "Claude",
        "OPENAI" to "ChatGPT",
        "GEMINI" to "Gemini",
        "CUSTOM" to "Custom"
    )

    private fun sp(c: Context) = c.getSharedPreferences("agent", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun saver(c: Context) = sp(c).getBoolean("saver", true)
    fun setSaver(c: Context, v: Boolean) = sp(c).edit().putBoolean("saver", v).apply()

    fun smart(c: Context) = sp(c).getBoolean("smart", false)
    fun setSmart(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("smart", v).apply()
        for (p in LLMS) sp(c).edit().remove("m_$p").apply()
    }

    fun speak(c: Context) = sp(c).getBoolean("speak", true)
    fun setSpeak(c: Context, v: Boolean) = sp(c).edit().putBoolean("speak", v).apply()

    fun lang(c: Context): String = sp(c).getString("lang", "hi-IN") ?: "hi-IN"
    fun setLang(c: Context, v: String) = sp(c).edit().putString("lang", v).apply()

    fun maxSteps(c: Context) = sp(c).getInt("steps", 25)
    fun setMaxSteps(c: Context, v: Int) = sp(c).edit().putInt("steps", v).apply()

    fun scope(c: Context): String = sp(c).getString("scope", "") ?: ""
    fun setScope(c: Context, v: String) = sp(c).edit().putString("scope", v).apply()

    fun flag(c: Context, name: String) = sp(c).getBoolean("f_$name", false)
    fun setFlag(c: Context, name: String) = sp(c).edit().putBoolean("f_$name", true).apply()

    fun getInt(c: Context, name: String, def: Int) = sp(c).getInt(name, def)
    fun putInt(c: Context, name: String, v: Int) = sp(c).edit().putInt(name, v).apply()

    // ---- API keys (encrypted) ----
    private fun order(c: Context): MutableList<String> =
        (sp(c).getString("order", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    fun key(c: Context, p: String): String = Secure.dec(sp(c).getString("k_$p", "") ?: "")

    fun savedProviders(c: Context): List<String> = order(c).filter { key(c, it).isNotBlank() }

    fun llmOrder(c: Context): List<String> = savedProviders(c).filter { it in LLMS }

    fun setKey(c: Context, p: String, k: String) {
        val o = order(c)
        o.remove(p)
        o.add(p)
        sp(c).edit()
            .putString("k_$p", Secure.enc(k.trim()))
            .putString("order", o.joinToString(","))
            .remove("m_$p")
            .apply()
    }

    fun removeKey(c: Context, p: String) {
        val o = order(c)
        o.remove(p)
        sp(c).edit().remove("k_$p").remove("m_$p").putString("order", o.joinToString(",")).apply()
    }

    fun makePrimary(c: Context, p: String) {
        val o = order(c)
        o.remove(p)
        o.add(0, p)
        sp(c).edit().putString("order", o.joinToString(",")).apply()
    }

    fun customBase(c: Context): String = sp(c).getString("c_base", "") ?: ""
    fun customName(c: Context): String = sp(c).getString("c_name", "") ?: ""
    fun customModel(c: Context): String = sp(c).getString("c_model", "") ?: ""
    fun setCustom(c: Context, name: String, base: String, model: String) =
        sp(c).edit().putString("c_name", name).putString("c_base", base).putString("c_model", model)
            .remove("m_CUSTOM").apply()

    /** Apps the user has disconnected: the agent must not open or operate them. */
    fun blocked(c: Context): Set<String> =
        (sp(c).getString("blocked", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun setBlocked(c: Context, pkg: String, block: Boolean) {
        val s = blocked(c).toMutableSet()
        if (block) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putString("blocked", s.joinToString(",")).apply()
    }

    fun ttsModel(c: Context): String = sp(c).getString("m_TTS", "") ?: ""
    fun setTtsModel(c: Context, m: String) = sp(c).edit().putString("m_TTS", m).apply()

    fun model(c: Context, p: String): String = sp(c).getString("m_$p", "") ?: ""
    fun setModel(c: Context, p: String, m: String) = sp(c).edit().putString("m_$p", m).apply()

    // ---- Voice characters ----
    fun defaultCharacters() = listOf(
        Persona("Default", 1.0f, 1.0f, "hi-IN", "", 0, "alloy", "Kore", "warm, natural and friendly",
            "हाँ बताइए, मैं आपके लिए क्या कर सकता हूँ?"),
        Persona("Priya (bright girl)", 1.35f, 1.05f, "hi-IN", "", 1, "shimmer", "Leda", "bright, cheerful young woman",
            "हाय! आज का दिन कमाल का होने वाला है, चलो शुरू करते हैं!"),
        Persona("Raj (deep man)", 0.75f, 0.95f, "hi-IN", "", 2, "onyx", "Charon", "deep, calm, confident man",
            "सुनिए, जब तक मैं साथ हूँ, कोई काम मुश्किल नहीं है।"),
        Persona("Aria (English girl)", 1.25f, 1.0f, "en-US", "", 0, "nova", "Aoede", "energetic friendly woman",
            "Hey there! Ready when you are. Let's make something awesome."),
        Persona("Narrator", 0.8f, 0.88f, "en-IN", "", 1, "fable", "Gacrux", "rich storyteller, slow and expressive",
            "और फिर, उसी शाम, एक बिल्कुल नई कहानी शुरू हुई।"),
        Persona("Newsreader", 0.95f, 1.12f, "en-GB", "", 1, "ash", "Orus", "crisp professional newsreader",
            "Good evening. Here are the top stories this hour."),
        Persona("Robot", 0.5f, 1.1f, "en-US", "", 2, "echo", "Puck", "flat mechanical robot, monotone",
            "Task received. Processing. Beep boop. Task complete."),
        Persona("Kid", 1.8f, 1.15f, "en-US", "", 0, "coral", "Zephyr", "playful high-pitched child",
            "Yay! Can we do it again? Please, please, please!"),
        Persona("Grandpa", 0.6f, 0.75f, "hi-IN", "", 3, "ballad", "Umbriel", "old wise grandfather, slow and gravelly",
            "बेटा, सब्र रखो, हर चीज़ का अपना वक़्त होता है।"),
        Persona("Hype Coach", 1.1f, 1.4f, "en-US", "", 2, "ash", "Fenrir", "loud, fast, motivational coach",
            "Come on! You've got this! No excuses, let's go, go, go!"),
        Persona("Calm Sage", 0.85f, 0.8f, "hi-IN", "", 3, "sage", "Sulafat", "very calm, soothing wise teacher",
            "गहरी साँस लो। जो होना है, वो सहज ही हो जाएगा।"),
        Persona("Villain", 0.55f, 0.8f, "en-US", "", 3, "onyx", "Algenib", "menacing villain, slow and dramatic",
            "Hmm... you really thought you could stop me? How amusing.")
    )

    fun characters(c: Context): List<Persona> {
        val s = sp(c).getString("chars", null) ?: return defaultCharacters()
        return try {
            val a = JSONArray(s)
            val out = ArrayList<Persona>()
            for (i in 0 until a.length()) out.add(Persona.from(a.getJSONObject(i)))
            if (out.isEmpty()) defaultCharacters() else out
        } catch (e: Exception) {
            defaultCharacters()
        }
    }

    fun saveCharacters(c: Context, list: List<Persona>) {
        val a = JSONArray()
        for (x in list) a.put(x.toJson())
        sp(c).edit().putString("chars", a.toString()).apply()
    }

    fun activeIndex(c: Context): Int =
        sp(c).getInt("char", 0).coerceIn(0, (characters(c).size - 1).coerceAtLeast(0))

    fun setActiveIndex(c: Context, i: Int) = sp(c).edit().putInt("char", i).apply()

    fun activeCharacter(c: Context): Persona = characters(c)[activeIndex(c)]
}

class Resp(val code: Int, val headers: Map<String, List<String>>, val bytes: ByteArray) {
    val body: String get() = String(bytes, Charsets.UTF_8)
}

object Http {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun readLimited(s: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = s.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 30000,
        follow: Boolean = false,
        maxBytes: Int = 600_000
    ): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = follow
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val bytes = if (stream != null) stream.use { readLimited(it, maxBytes) } else ByteArray(0)
            val h = HashMap<String, List<String>>()
            for ((k, v) in c.headerFields) {
                if (k != null) h[k.lowercase()] = v
            }
            return Resp(code, h, bytes)
        } finally {
            c.disconnect()
        }
    }
}

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, fill: Int, radiusDp: Int, stroke: Int = 0, strokeDp: Int = 1): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        if (stroke != 0) g.setStroke(dp(c, strokeDp), stroke)
        return g
    }

    fun tv(c: Context, text: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    fun btn(c: Context, text: String, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = text
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.background = round(c, Color.parseColor("#1A1E3C"), 12, Color.parseColor("#2B3160"))
        b.minimumHeight = 0
        b.minHeight = 0
        b.minimumWidth = 0
        b.minWidth = 0
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        b.setOnClickListener { onClick() }
        return b
    }
}
