package com.myagent.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Bada ON/OFF button (photo wala metallic style). */
class PowerToggle @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var onChange: ((Boolean) -> Unit)? = null
    var checked: Boolean = false
        private set
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private var accent = Color.rgb(90, 216, 255)

    fun setAccent(c: Int) { accent = c; invalidate() }

    private val density = resources.displayMetrics.density
    private val outer = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 12, 24) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(90, 216, 255) }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(184, 190, 201) }
    private val knobIn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(228, 232, 240) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val box = RectF()
    private val box2 = RectF()

    fun setChecked(v: Boolean, animate: Boolean) {
        checked = v
        val target = if (v) 1f else 0f
        anim?.cancel()
        if (animate) {
            val a = ValueAnimator.ofFloat(pos, target)
            a.duration = 220
            a.addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            anim = a
            a.start()
        } else {
            pos = target
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        outer.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(205, 210, 220), Color.rgb(90, 96, 110), Color.rgb(170, 176, 188)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        text.textSize = h * 0.36f
        rim.strokeWidth = 1.5f * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 3f * density
        box.set(pad, pad, w - pad, h - pad)
        canvas.drawRoundRect(box, h / 2f, h / 2f, outer)
        val ring = 6f * density
        box2.set(box.left + ring, box.top + ring, box.right - ring, box.bottom - ring)
        canvas.drawRoundRect(box2, box2.height() / 2f, box2.height() / 2f, inner)
        rim.color = accent
        rim.alpha = if (checked) 220 else 70
        canvas.drawRoundRect(box2, box2.height() / 2f, box2.height() / 2f, rim)

        val kr = box2.height() / 2f - 3f * density
        val left = box2.left + 3f * density + kr
        val right = box2.right - 3f * density - kr
        val kx = left + (right - left) * pos
        val ky = box2.centerY()

        // text knob ke dusri taraf
        val freeCx = if (checked) (box2.left + (kx - kr)) / 2f else ((kx + kr) + box2.right) / 2f
        val label = if (checked) "ON" else "OFF"
        if (checked) {
            text.color = accent
            text.setShadowLayer(14f * density, 0f, 0f, accent)
        } else {
            text.color = Color.rgb(110, 118, 140)
            text.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
        val baseline = ky - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, freeCx, baseline, text)

        canvas.drawCircle(kx, ky, kr, knob)
        canvas.drawCircle(kx, ky, kr * 0.72f, knobIn)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        setChecked(!checked, true)
        onChange?.invoke(checked)
        return true
    }
}
