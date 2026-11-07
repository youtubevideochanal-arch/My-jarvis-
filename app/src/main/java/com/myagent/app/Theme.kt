package com.myagent.app

data class Palette(val name: String, val c1: Int, val c2: Int, val bgTop: Int, val bgBottom: Int)

object Palettes {
    private fun c(v: Long): Int = v.toInt()

    val all: List<Palette> = listOf(
        Palette("Neon (cyan + purple)", c(0xFF5AD8FF), c(0xFFB06BFF), c(0xFF0E1530), c(0xFF05060D)),
        Palette("Sunset (orange + pink)", c(0xFFFFA24D), c(0xFFFF4FA3), c(0xFF2A1030), c(0xFF0D0610)),
        Palette("Matrix (green + aqua)", c(0xFF4DFF9A), c(0xFF2ED1FF), c(0xFF06220F), c(0xFF020805)),
        Palette("Ocean (blue + teal)", c(0xFF4D9BFF), c(0xFF2EF2D0), c(0xFF08183A), c(0xFF030812)),
        Palette("Rose (pink + violet)", c(0xFFFF6BD6), c(0xFF8F6BFF), c(0xFF260C33), c(0xFF0A0412))
    )

    fun current(): Palette = all[Hub.prefs.themeIdx.coerceIn(0, all.size - 1)]

    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
}
