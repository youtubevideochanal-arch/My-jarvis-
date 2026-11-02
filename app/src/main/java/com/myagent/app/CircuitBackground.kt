package com.myagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.Random

/** Colorful circuit-board background (static, battery-friendly: sirf ek baar banta hai). */
class CircuitBackground @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val paths = ArrayList<Path>()
    private val nodes = ArrayList<FloatArray>()
    private val bgPaint = Paint()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.4f }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    fun applyPalette() {
        if (width > 0) shaders(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        generate(w, h)
        shaders(w, h)
    }

    private fun generate(w: Int, h: Int) {
        paths.clear()
        nodes.clear()
        val rnd = Random(11)
        val step = w / 12f
        val rows = (h / step).toInt().coerceAtLeast(2)
        for (k in 0 until 46) {
            var x = rnd.nextInt(13) * step
            var y = rnd.nextInt(rows) * step
            val p = Path()
            p.moveTo(x, y)
            var horizontal = rnd.nextBoolean()
            val segs = 3 + rnd.nextInt(3)
            for (s in 0 until segs) {
                val len = step * (1 + rnd.nextInt(3))
                val dir = if (rnd.nextBoolean()) 1 else -1
                if (horizontal) x += dir * len else y += dir * len
                p.lineTo(x, y)
                horizontal = !horizontal
            }
            paths.add(p)
            nodes.add(floatArrayOf(x, y))
        }
    }

    private fun shaders(w: Int, h: Int) {
        val p = Palettes.current()
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), p.bgTop, p.bgBottom, Shader.TileMode.CLAMP)
        linePaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Palettes.alpha(p.c1, 80), Palettes.alpha(p.c2, 80), Shader.TileMode.CLAMP)
        nodePaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Palettes.alpha(p.c1, 150), Palettes.alpha(p.c2, 150), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        for (p in paths) canvas.drawPath(p, linePaint)
        for (n in nodes) canvas.drawCircle(n[0], n[1], 3.5f * density, nodePaint)
    }
}
