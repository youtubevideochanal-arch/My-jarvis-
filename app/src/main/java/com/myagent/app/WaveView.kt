package com.myagent.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/** The glowing purple-to-blue wave at the bottom of the home screen. Speeds up while the agent works. */
class WaveView(c: Context) : View(c) {
    private val p1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 7000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    var fast: Boolean = false
        set(v) {
            if (field != v) {
                field = v
                anim.duration = if (v) 2800 else 7000
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val colors1 = intArrayOf(0xFF5B3FD9.toInt(), 0xFF3A5BFF.toInt(), 0xFF1E88E5.toInt())
        val colors2 = intArrayOf(0xFF1E88E5.toInt(), 0xFF6C4DF0.toInt(), 0xFF3A2FB5.toInt())
        p1.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colors1, null, Shader.TileMode.CLAMP)
        p2.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colors2, null, Shader.TileMode.CLAMP)
        p1.alpha = 120
        p2.alpha = 80
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        wave(canvas, p1, w, h, h * 0.10f, h * 0.60f, 1.3f, phase)
        wave(canvas, p2, w, h, h * 0.08f, h * 0.68f, 1.9f, -phase * 1.3f + 1.5f)
    }

    private fun wave(c: Canvas, p: Paint, w: Float, h: Float, amp: Float, base: Float, freq: Float, ph: Float) {
        path.reset()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            val y = base + amp * sin((2 * PI * freq * x / w + ph).toDouble()).toFloat()
            path.lineTo(x, y)
            x += 10f
        }
        path.lineTo(w, h)
        path.close()
        c.drawPath(path, p)
    }
}
