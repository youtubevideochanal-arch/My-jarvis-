package com.myagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Simple line chart: price + SMA20 + SMA50 + Bollinger bands. */
class ChartView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var closes = DoubleArray(0)
    private var s20 = DoubleArray(0)
    private var s50 = DoubleArray(0)
    private var up = DoubleArray(0)
    private var lo = DoubleArray(0)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bg = Paint().apply { color = Color.argb(90, 10, 14, 30) }
    private val path = Path()

    fun setData(cs: List<Candle>) {
        val c = DoubleArray(cs.size) { cs[it].c }
        closes = c
        s20 = Ind.sma(c, 20)
        s50 = Ind.sma(c, 50)
        val b = Ind.boll(c)
        up = b.up
        lo = b.lo
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        val show = min(120, closes.size)
        if (show < 2) return
        val start = closes.size - show
        var mn = Double.MAX_VALUE
        var mx = -Double.MAX_VALUE
        for (i in start until closes.size) {
            mn = min(mn, closes[i]); mx = max(mx, closes[i])
            if (!lo[i].isNaN()) mn = min(mn, lo[i])
            if (!up[i].isNaN()) mx = max(mx, up[i])
        }
        if (mx <= mn) return
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 8f
        fun draw(arr: DoubleArray, color: Int, sw: Float) {
            path.reset()
            var started = false
            for (k in 0 until show) {
                val v = arr[start + k]
                if (v.isNaN()) { started = false; continue }
                val x = pad + (w - 2 * pad) * k / (show - 1)
                val y = (h - pad - ((v - mn) / (mx - mn) * (h - 2 * pad))).toFloat()
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            p.color = color
            p.strokeWidth = sw
            canvas.drawPath(path, p)
        }
        val pal = Palettes.current()
        draw(up, Palettes.alpha(pal.c2, 110), 2f)
        draw(lo, Palettes.alpha(pal.c2, 110), 2f)
        draw(s50, Color.rgb(255, 190, 70), 2.5f)
        draw(s20, Color.rgb(120, 255, 160), 2.5f)
        draw(closes, pal.c1, 4f)
    }
}
