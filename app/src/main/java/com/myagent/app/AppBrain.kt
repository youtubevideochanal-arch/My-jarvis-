package com.myagent.app

import android.content.Context

/**
 * Fallback brain: when no API answers, ask an AI app already installed on the phone
 * (ChatGPT, Gemini, Claude, Qwen...) by typing the question into it through accessibility
 * and reading its reply from the screen. Slower and more fragile than an API, so it is only a backup.
 */
object AppBrain {
    /** Off by default: only used when the user switches it on in the menu. */
    fun on(c: Context): Boolean = Prefs.getInt(c, "brain_on", 0) == 1
    fun setOn(c: Context, v: Boolean) = Prefs.putInt(c, "brain_on", if (v) 1 else 0)

    /** Open the AI app as a small floating window in a corner (needs freeform windows; otherwise opens normally). */
    fun small(c: Context): Boolean = Prefs.getInt(c, "brain_small", 1) == 1
    fun setSmall(c: Context, v: Boolean) = Prefs.putInt(c, "brain_small", if (v) 1 else 0)

    private fun cornerBounds(c: Context): android.graphics.Rect? {
        if (!small(c)) return null
        val dm = c.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        return android.graphics.Rect((w * 0.40f).toInt(), (h * 0.50f).toInt(), w, (h * 0.90f).toInt())
    }

    fun apps(c: Context): String =
        c.getSharedPreferences("agent", Context.MODE_PRIVATE).getString("brain_apps", "ChatGPT,Gemini,Claude,Qwen") ?: ""

    fun setApps(c: Context, v: String) =
        c.getSharedPreferences("agent", Context.MODE_PRIVATE).edit().putString("brain_apps", v).apply()

    private val lineRe = Regex("^\\[(\\d+)] (\\S*) \"(.*)\" (-?\\d+),(-?\\d+)(.*)$")

    private class El(val i: Int, val cls: String, val text: String, val y: Int, val flags: String)

    private fun parse(dump: String): List<El> {
        val out = ArrayList<El>()
        for (ln in dump.lines()) {
            val m = lineRe.find(ln) ?: continue
            out.add(El(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3], m.groupValues[5].toInt(), m.groupValues[6]))
        }
        return out
    }

    fun ask(c: Context, system: String, msgs: List<Msg>): String {
        val acc = AgentAccessibilityService.get(c) ?: throw Exception("accessibility band")
        val last = msgs.takeLast(2).joinToString("\n") { it.role.uppercase() + ": " + it.text }
        val prompt = (system + "\n\n" + last + "\n\nReply with ONLY the JSON object.").take(7000)
        val prev = acc.currentPackage()
        val names = apps(c).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val errs = ArrayList<String>()
        for (n in names) {
            try {
                val r = Tools.openApp(c, n, cornerBounds(c))
                if (!r.startsWith("opened")) {
                    errs.add("$n: nahi mila")
                    continue
                }
                Chat.add(Chat.INFO, "API se jawab nahi mila, $n app se pooch raha hu")
                val out = driveOne(acc, prompt)
                goBack(c, prev)
                return out
            } catch (e: Exception) {
                errs.add(n + ": " + (e.message ?: "fail"))
            }
        }
        goBack(c, prev)
        throw Exception(errs.joinToString("; "))
    }

    private fun goBack(c: Context, pkg: String) {
        try {
            if (pkg == "?" || pkg.isEmpty()) return
            val li = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
            li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            c.startActivity(li)
            Thread.sleep(1200)
        } catch (e: Exception) {
        }
    }

    private fun driveOne(acc: AgentAccessibilityService, prompt: String): String {
        Thread.sleep(2500)
        var els = parse(acc.dump(150, 600))
        var field = els.lastOrNull { " edit" in it.flags }
        if (field == null) {
            // some apps show a collapsed bar first; tap near the bottom to open the text box
            val dm = acc.resources.displayMetrics
            acc.tapXY(dm.widthPixels * 0.4f, dm.heightPixels * 0.93f)
            Thread.sleep(1000)
            els = parse(acc.dump(150, 600))
            field = els.lastOrNull { " edit" in it.flags }
        }
        if (field == null) throw Exception("text box nahi mila")
        if (!acc.typeText(prompt, field.i).startsWith("typed")) throw Exception("type nahi hua")
        Thread.sleep(700)
        els = parse(acc.dump(150, 600))
        val send = els.lastOrNull { " tap" in it.flags && Regex("(?i)\\bsend|submit|भेज").containsMatchIn(it.text) }
        if (send != null) acc.tapIndex(send.i) else acc.key("enter")
        Thread.sleep(2500)

        val key = prompt.take(20)
        var lastSig = ""
        var stable = 0
        var result = ""
        for (round in 0 until 30) {
            val dump = acc.dump(200, 3000)
            val e = parse(dump)
            val generating = e.any { " tap" in it.flags && Regex("(?i)\\bstop").containsMatchIn(it.text) }
            val texts = replyLines(e, key)
            val sig = texts.joinToString("\n")
            if (!generating && sig.isNotBlank() && sig == lastSig) stable++ else stable = 0
            lastSig = sig
            result = sig
            if (stable >= 1) break
            Thread.sleep(2000)
        }
        if (result.isBlank()) throw Exception("jawab nahi padh paya")
        return result
    }

    /** Text lines shown after the user's own bubble, without buttons or the input box. */
    private fun replyLines(e: List<El>, key: String): List<String> {
        val body = e.filter { " tap" !in it.flags && " edit" !in it.flags && it.text.isNotBlank() }
        val idx = body.indexOfLast { it.text.startsWith(key) }
        val after = if (idx >= 0) body.drop(idx + 1) else body.filter { it.text.length > 25 }
        return after.map { it.text }.filter { it.length > 1 }
    }
}
