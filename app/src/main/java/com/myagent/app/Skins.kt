package com.myagent.app

import android.graphics.Color

/**
 * Interface skins: wahi app, alag-alag "normal AI app" jaisi shakl. Settings -> Look me chuno.
 * (Naam/logo kisi company ka nahi - sirf rang aur layout ka andaaz.)
 */
class Skin(
    val name: String,
    val bgTop: Int, val bgBottom: Int,
    val text: Int, val sub: Int,
    val accent: Int, val accent2: Int,
    val bar: Int,
    val showOrb: Boolean, val showCircuit: Boolean,
    val mono: Boolean = false,
    val light: Boolean = false
)

object Skins {
    private fun c(v: Long): Int = v.toInt()

    val all: List<Skin> = listOf(
        Skin("0. Orb Neon (3D orb wala, default)", 0, 0, c(0xFFDDE3FF), c(0xFF7F89B0), 0, 0, c(0xFF141829), true, true),
        Skin("1. Simple Dark (seedha chat jaisa)", c(0xFF212121), c(0xFF171717), c(0xFFECECEC), c(0xFF9A9A9A), c(0xFF19C37D), c(0xFF19C37D), c(0xFF2F2F2F), false, false),
        Skin("2. Warm Paper (halka, kitaab jaisa)", c(0xFFF5F1EA), c(0xFFEFE9DF), c(0xFF2B2620), c(0xFF7A6F62), c(0xFFC96442), c(0xFFC96442), c(0xFFFFFFFF), false, false, false, true),
        Skin("3. Gradient Spark (neela-jamuni)", c(0xFF0F1115), c(0xFF090B10), c(0xFFE6E8F0), c(0xFF8A90A6), c(0xFF6EA8FF), c(0xFFB28DFF), c(0xFF1B1E27), false, false),
        Skin("4. Terminal (hacker, hara)", c(0xFF000000), c(0xFF000000), c(0xFF38F27A), c(0xFF1F9B4C), c(0xFF38F27A), c(0xFF38F27A), c(0xFF07130B), false, false, true),
        Skin("5. Messenger (hara chat)", c(0xFF0B141A), c(0xFF0B141A), c(0xFFE9EDEF), c(0xFF8696A0), c(0xFF00A884), c(0xFF00A884), c(0xFF1F2C34), false, false),
        Skin("6. Light Clean (safed)", c(0xFFFFFFFF), c(0xFFF3F5F9), c(0xFF1B1F2A), c(0xFF6B7285), c(0xFF2B6BFF), c(0xFF7B5BFF), c(0xFFEEF1F7), false, false, false, true)
    )

    fun current(): Skin = all[Hub.prefs.uiSkin.coerceIn(0, all.size - 1)]

    fun fg(s: Skin): Int = s.text
    fun alpha(color: Int, a: Int): Int = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
