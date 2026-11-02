package com.myagent.app

import android.content.Context

/** Saari settings yahin save hoti hain (phone ke andar hi, kahin upload nahi). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("my_agent_prefs", Context.MODE_PRIVATE)

    fun str(key: String, def: String = ""): String = sp.getString(key, def) ?: def
    fun putStr(key: String, v: String) { sp.edit().putString(key, v).apply() }
    fun bool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply() }
    fun int(key: String, def: Int): Int = sp.getInt(key, def)
    fun putInt(key: String, v: Int) { sp.edit().putInt(key, v).apply() }
    fun float(key: String, def: Float): Float = sp.getFloat(key, def)
    fun putFloat(key: String, v: Float) { sp.edit().putFloat(key, v).apply() }

    // ---- General ----
    var appName: String
        get() = str("app_name", "MY AGENT")
        set(v) { putStr("app_name", if (v.isBlank()) "MY AGENT" else v.trim()) }

    var masterOn: Boolean
        get() = bool("master_on", false)
        set(v) { putBool("master_on", v) }

    // ---- Voice ----
    var ttsEnabled: Boolean
        get() = bool("tts_on", true)
        set(v) { putBool("tts_on", v) }
    var lang: String
        get() = str("lang", "hi-IN")
        set(v) { putStr("lang", v) }
    var rate: Float
        get() = float("rate", 1.0f)
        set(v) { putFloat("rate", v) }
    var pitch: Float
        get() = float("pitch", 1.0f)
        set(v) { putFloat("pitch", v) }
    var autoListen: Boolean
        get() = bool("auto_listen", false)
        set(v) { putBool("auto_listen", v) }

    // ---- Agent ----
    var collab: Boolean
        get() = bool("collab", false)
        set(v) { putBool("collab", v) }
    var confirmRisky: Boolean
        get() = bool("confirm_risky", true)
        set(v) { putBool("confirm_risky", v) }
    var maxSteps: Int
        get() = int("max_steps", 40)
        set(v) { putInt("max_steps", v) }
    var stepDelayMs: Int
        get() = int("step_delay", 800)
        set(v) { putInt("step_delay", v) }

    // ---- API slots ----
    fun on(s: Slot): Boolean = bool("slot.${s.id}.on", true)
    fun setOn(s: Slot, v: Boolean) = putBool("slot.${s.id}.on", v)
    fun key(s: Slot): String = str("slot.${s.id}.key")
    fun setKey(s: Slot, v: String) = putStr("slot.${s.id}.key", v.trim())
    fun model(s: Slot): String { val v = str("slot.${s.id}.model"); return if (v.isBlank()) s.defModel else v }
    fun setModel(s: Slot, v: String) = putStr("slot.${s.id}.model", v.trim())
    fun endpoint(s: Slot): String { val v = str("slot.${s.id}.endpoint"); return if (v.isBlank()) s.defEndpoint else v }
    fun setEndpoint(s: Slot, v: String) = putStr("slot.${s.id}.endpoint", v.trim())
    fun name(s: Slot): String { val v = str("slot.${s.id}.name"); return if (v.isBlank()) s.defName else v }
    fun setName(s: Slot, v: String) = putStr("slot.${s.id}.name", v.trim())

    // ---- Photo API ----
    var photoOn: Boolean
        get() = bool("photo_on", false)
        set(v) { putBool("photo_on", v) }
    var photoKey: String
        get() = str("photo_key")
        set(v) { putStr("photo_key", v.trim()) }
    var photoModel: String
        get() { val v = str("photo_model"); return if (v.isBlank()) "gemini-2.0-flash" else v }
        set(v) { putStr("photo_model", v.trim()) }
    var photoEndpoint: String
        get() { val v = str("photo_endpoint"); return if (v.isBlank()) Slots.GEMINI_BASE else v }
        set(v) { putStr("photo_endpoint", v.trim()) }

    // ---- V2 ----
    var themeIdx: Int
        get() = int("theme_idx", 0)
        set(v) { putInt("theme_idx", v) }
    var voiceStyle: Int
        get() = int("voice_style", 0)
        set(v) { putInt("voice_style", v) }
    var voiceName: String
        get() = str("voice_name")
        set(v) { putStr("voice_name", v) }
    var floatingOn: Boolean
        get() = bool("floating_on", true)
        set(v) { putBool("floating_on", v) }
    var batterySaver: Boolean
        get() = bool("battery_saver", false)
        set(v) { putBool("battery_saver", v) }
    var mySites: String
        get() = str("my_sites")
        set(v) { putStr("my_sites", v) }
    var watchlist: String
        get() = str("watchlist", "^NSEI,RELIANCE.NS,BTCUSDT")
        set(v) { putStr("watchlist", v) }
    var lastSymbol: String
        get() = str("last_symbol", "^NSEI")
        set(v) { putStr("last_symbol", v) }
    var trainLevel: Int
        get() = int("train_level", 0)
        set(v) { putInt("train_level", v) }
    // ---- V3 ----
    var requireName: Boolean
        get() = bool("require_name", false)
        set(v) { putBool("require_name", v) }
    var capital: Float
        get() = float("capital", 100000f)
        set(v) { putFloat("capital", v) }
    var riskPct: Float
        get() = float("risk_pct", 1f)
        set(v) { putFloat("risk_pct", v) }

    // ---- V4 ----
    var orbStyle: Int
        get() = int("orb_style", 0)
        set(v) { putInt("orb_style", v) }
    var character: Int
        get() = int("character", 0)
        set(v) { putInt("character", v) }
    var wakeAlways: Boolean
        get() = bool("wake_always", false)
        set(v) { putBool("wake_always", v) }
    var tokenSaver: Boolean
        get() = bool("token_saver", true)
        set(v) { putBool("token_saver", v) }
    var tradeApps: String
        get() = str("trade_apps")
        set(v) { putStr("trade_apps", v) }
    var socialApps: String
        get() = str("social_apps")
        set(v) { putStr("social_apps", v) }
    var nicknames: String
        get() = str("nicknames")
        set(v) { putStr("nicknames", v) }
    var pcUrl: String
        get() = str("pc_url")
        set(v) { putStr("pc_url", v.trim()) }
    var pcToken: String
        get() = str("pc_token")
        set(v) { putStr("pc_token", v.trim()) }
    var ownerName: String
        get() = str("owner_name")
        set(v) { putStr("owner_name", v.trim()) }
    var playbookUrl: String
        get() = str("playbook_url")
        set(v) { putStr("playbook_url", v.trim()) }
    var panelOn: Boolean
        get() = bool("panel_on", true)
        set(v) { putBool("panel_on", v) }
    var banner: Boolean
        get() = bool("banner_on", true)
        set(v) { putBool("banner_on", v) }

    private fun today(): String = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    /** Aaj kitne token use hue (API ke asli usage se). */
    fun addUsage(slotId: String, tokens: Int) {
        if (tokens <= 0) return
        val k = "usage.${today()}.$slotId"
        putInt(k, int(k, 0) + tokens)
        val a = "usage.${today()}.all"
        putInt(a, int(a, 0) + tokens)
        putInt("usage.calls.${today()}", int("usage.calls.${today()}", 0) + 1)
    }

    fun usageToday(slotId: String): Int = int("usage.${today()}.$slotId", 0)
    fun usageAll(): Int = int("usage.${today()}.all", 0)
    fun callsToday(): Int = int("usage.calls.${today()}", 0)

    fun exportAll(): String {
        val j = org.json.JSONObject()
        for ((k, v) in sp.all) {
            if (k.startsWith("usage.") || k == "memos" || k.endsWith(".key") || k == "photo_key" || k == "pc_token" || k == "extra_apis" || k == "ledger") continue
            j.put(k, v)
        }
        return j.toString(2)
    }

    fun clearAll() { sp.edit().clear().apply() }
}
