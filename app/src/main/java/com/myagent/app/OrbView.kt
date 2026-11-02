package com.myagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Ghumta hua 3D gola: neural-network jaisa jaal + beech me ghumta bhanwar. */
class OrbView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val n = 150
    private val bx = FloatArray(n)
    private val by = FloatArray(n)
    private val bz = FloatArray(n)
    private val sx = FloatArray(n)
    private val sy = FloatArray(n)
    private val sz = FloatArray(n)
    private var pairs = IntArray(0)

    private var angle = 0f
    private var energy = 0.25f
    private var orbOn = false
    private var orbBusy = false
    private var lastT = 0L

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var lowPower = false
    private var c1 = Palettes.current().c1
    private var c2 = Palettes.current().c2
    private val tick = Runnable { invalidate() }
    private var rimShader: SweepGradient? = null
    private val rimMatrix = Matrix()
    private val density = resources.displayMetrics.density

    init {
        val golden = (Math.PI * (3.0 - Math.sqrt(5.0))).toFloat()
        for (i in 0 until n) {
            val y = 1f - 2f * (i + 0.5f) / n
            val r = sqrt(1f - y * y)
            val th = golden * i
            bx[i] = cos(th) * r
            by[i] = y
            bz[i] = sin(th) * r
        }
        val list = ArrayList<Int>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val dx = bx[i] - bx[j]
            val dy = by[i] - by[j]
            val dz = bz[i] - bz[j]
            if (dx * dx + dy * dy + dz * dz < 0.26f) { list.add(i); list.add(j) }
        }
        pairs = list.toIntArray()
    }

    fun setActive(v: Boolean) { orbOn = v; invalidate() }
    fun setBusy(v: Boolean) { orbBusy = v; invalidate() }

    fun applyPalette() {
        c1 = Palettes.current().c1
        c2 = Palettes.current().c2
        if (width > 0) rebuild(width, height)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild(w, h)
    }

    private fun rebuild(w: Int, h: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f * 0.82f
        if (r <= 0f) return
        glowPaint.shader = RadialGradient(
            cx, cy, r * 1.4f,
            intArrayOf(Palettes.alpha(c2, 150), Palettes.alpha(c1, 70), Palettes.alpha(c1, 0)),
            floatArrayOf(0.55f, 0.8f, 1f), Shader.TileMode.CLAMP
        )
        bodyPaint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.3f, r * 1.1f,
            intArrayOf(Palettes.alpha(c1, 70), Color.argb(40, 40, 30, 120), Palettes.alpha(c2, 110)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        rimShader = SweepGradient(
            cx, cy,
            intArrayOf(c1, c2, c1),
            null
        )
        rimPaint.shader = rimShader
        rimPaint.strokeWidth = 2.5f * density
        corePaint.shader = RadialGradient(
            cx, cy, r * 0.28f,
            intArrayOf(Color.argb(255, 255, 255, 255), Palettes.alpha(c1, 160), Palettes.alpha(c2, 0)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun drawNeural(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        glowPaint.alpha = (70 + 185 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 1.4f, glowPaint)
        bodyPaint.alpha = (120 + 135 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, bodyPaint)
        rimMatrix.setRotate(Math.toDegrees(angle.toDouble() * 2).toFloat(), cx, cy)
        rimShader?.setLocalMatrix(rimMatrix)
        rimPaint.alpha = (110 + 145 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, rimPaint)

        // 3D points
        val ca = cos(angle)
        val sa = sin(angle)
        val tilt = 0.35f
        val ct = cos(tilt)
        val st = sin(tilt)
        for (i in 0 until n) {
            val x1 = bx[i] * ca + bz[i] * sa
            val z1 = -bx[i] * sa + bz[i] * ca
            val y1 = by[i] * ct - z1 * st
            val z2 = by[i] * st + z1 * ct
            sx[i] = cx + x1 * r
            sy[i] = cy + y1 * r
            sz[i] = (z2 + 1f) / 2f
        }
        val lineBase = 150f * (0.35f + energy * 0.65f)
        var k = if (lowPower) pairs.size else 0
        while (k < pairs.size) {
            val a = pairs[k]
            val b = pairs[k + 1]
            val d = (sz[a] + sz[b]) / 2f
            val alpha = (d * lineBase).toInt().coerceIn(0, 255)
            linePaint.color = if ((a + b) % 3 == 0) Palettes.alpha(c2, alpha) else Palettes.alpha(c1, alpha)
            canvas.drawLine(sx[a], sy[a], sx[b], sy[b], linePaint)
            k += 2
        }
        for (i in 0 until n) {
            val d = sz[i]
            dotPaint.color = Color.argb((90 + 165 * d).toInt().coerceIn(0, 255), 200, 235, 255)
            canvas.drawCircle(sx[i], sy[i], (1.0f + d * 2.2f) * density, dotPaint)
        }

        // beech ka bhanwar (swirl)
        val arms = 2
        val per = 70
        for (arm in 0 until arms) {
            for (j in 0 until per) {
                val t = j / per.toFloat()
                val rad = r * 0.34f * t
                val th = t * 6.0f + angle * 4f * (0.6f + energy) + arm * Math.PI.toFloat()
                val px = cx + rad * cos(th)
                val py = cy + rad * sin(th) * 0.92f
                val fade = 1f - t * 0.6f
                swirlPaint.color = if (arm == 0)
                    Palettes.alpha(c1, (255 * fade).toInt())
                else
                    Palettes.alpha(c2, (255 * fade).toInt())
                canvas.drawCircle(px, py, (0.8f + (1f - t) * 2.0f) * density, swirlPaint)
            }
        }
        corePaint.alpha = (140 + 115 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 0.28f, corePaint)

    }

    // ------------------------------------------------------------ extra styles
    var styleOverride = -1
    private val style: Int get() = if (styleOverride >= 0) styleOverride else Hub.prefs.orbStyle
    private val tmp = FloatArray(3)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun rot(x: Float, y: Float, z: Float, ay: Float, ax: Float, az: Float) {
        val x1 = x * cos(ay) + z * sin(ay)
        val z1 = -x * sin(ay) + z * cos(ay)
        val y1 = y * cos(ax) - z1 * sin(ax)
        val z2 = y * sin(ax) + z1 * cos(ax)
        tmp[0] = x1 * cos(az) - y1 * sin(az)
        tmp[1] = x1 * sin(az) + y1 * cos(az)
        tmp[2] = z2
    }

    private fun softGlow(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        glowPaint.alpha = (70 + 185 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 1.4f, glowPaint)
        bodyPaint.alpha = (60 + 80 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, bodyPaint)
    }

    private fun core(canvas: Canvas, cx: Float, cy: Float, r: Float, k: Float) {
        corePaint.alpha = (140 + 115 * energy).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * k, corePaint)
    }

    /** Style 1: teen ghumti hui gyroscope-rings + chamakte dot. */
    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        softGlow(canvas, cx, cy, r)
        val a = angle * 3f
        val segs = if (lowPower) 36 else 64
        for (ring in 0 until 3) {
            val ay = a * (1f + ring * 0.35f) + ring * 1.1f
            val ax = 0.9f * ring + a * 0.4f
            val rr = r * (0.98f - ring * 0.14f)
            var px = 0f
            var py = 0f
            var first = true
            for (i in 0..segs) {
                val t = i * 6.2831855f / segs
                rot(cos(t) * rr, sin(t) * rr, 0f, ay, ax, 0.3f * ring)
                val x = cx + tmp[0]
                val y = cy + tmp[1]
                val d = (tmp[2] / rr + 1f) / 2f
                if (!first) {
                    strokeP.strokeWidth = (1.2f + d * 2.2f) * density
                    strokeP.color = Palettes.alpha(if (ring % 2 == 0) c1 else c2, (70 + 185 * d * (0.4f + energy * 0.6f)).toInt())
                    canvas.drawLine(px, py, x, y, strokeP)
                }
                px = x; py = y; first = false
            }
            // ring par daudta dot
            val t = a * 2.2f * (1f + ring) + ring * 2f
            rot(cos(t) * rr, sin(t) * rr, 0f, ay, ax, 0.3f * ring)
            fillP.color = Color.WHITE
            fillP.alpha = 230
            canvas.drawCircle(cx + tmp[0], cy + tmp[1], (2.5f + (tmp[2] / rr + 1f) * 1.6f) * density, fillP)
        }
        core(canvas, cx, cy, r, 0.34f)
    }

    private val cubeV = arrayOf(
        floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
        floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
    )
    private val cubeE = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7)
    private val cx2 = FloatArray(8)
    private val cy2 = FloatArray(8)
    private val cz2 = FloatArray(8)

    /** Style 2: hologram wire-cube (bada) + ulta ghumta chhota cube. */
    private fun drawCube(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        softGlow(canvas, cx, cy, r)
        for (layer in 0 until 2) {
            val sc = if (layer == 0) r * 0.58f else r * 0.30f
            val dir = if (layer == 0) 1f else -1.6f
            val col = if (layer == 0) c1 else c2
            for (i in 0 until 8) {
                rot(cubeV[i][0] * sc, cubeV[i][1] * sc, cubeV[i][2] * sc, angle * 3f * dir, 0.6f + angle * 1.4f * dir, 0.25f)
                cx2[i] = cx + tmp[0]; cy2[i] = cy + tmp[1]; cz2[i] = (tmp[2] / sc + 1.7f) / 3.4f
            }
            var k = 0
            while (k < cubeE.size) {
                val u = cubeE[k]
                val v = cubeE[k + 1]
                val d = (cz2[u] + cz2[v]) / 2f
                strokeP.strokeWidth = (1.5f + d * 2f) * density
                strokeP.color = Palettes.alpha(col, (90 + 165 * d * (0.5f + energy * 0.5f)).toInt())
                canvas.drawLine(cx2[u], cy2[u], cx2[v], cy2[v], strokeP)
                k += 2
            }
            for (i in 0 until 8) {
                fillP.color = Color.WHITE
                fillP.alpha = (120 + 135 * cz2[i]).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx2[i], cy2[i], (1.8f + cz2[i] * 2f) * density, fillP)
            }
        }
        // bahar ghumta scan-ring
        strokeP.strokeWidth = 1.5f * density
        strokeP.color = Palettes.alpha(c1, 110)
        canvas.drawCircle(cx, cy, r * (0.92f + 0.04f * sin(angle * 12f)), strokeP)
        core(canvas, cx, cy, r, 0.22f)
    }

    private val gN = 260
    private val gR = FloatArray(gN)
    private val gT = FloatArray(gN)
    private val gH = FloatArray(gN)
    private var gInit = false

    /** Style 3: ghumti hui galaxy (spiral arms). */
    private fun drawGalaxy(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        if (!gInit) {
            val rnd = java.util.Random(7)
            for (i in 0 until gN) {
                val t = rnd.nextFloat()
                gR[i] = 0.08f + t * 0.92f
                val arm = i % 3
                gT[i] = t * 7.5f + arm * 2.0944f + (rnd.nextFloat() - 0.5f) * 0.5f
                gH[i] = (rnd.nextFloat() - 0.5f) * 0.18f * (1f - t * 0.6f)
            }
            gInit = true
        }
        softGlow(canvas, cx, cy, r)
        val spin = angle * 5f
        for (i in 0 until gN) {
            val th = gT[i] + spin * (1.2f - gR[i] * 0.6f)
            rot(cos(th) * gR[i] * r, gH[i] * r, sin(th) * gR[i] * r, 0.2f, 1.05f, 0f)
            val d = (tmp[2] / r + 1f) / 2f
            fillP.color = if (i % 4 == 0) Palettes.alpha(c2, (90 + 165 * d).toInt()) else Palettes.alpha(c1, (70 + 165 * d).toInt())
            canvas.drawCircle(cx + tmp[0], cy + tmp[1], (0.8f + (1f - gR[i]) * 2.2f + d) * density, fillP)
        }
        core(canvas, cx, cy, r, 0.4f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f * 0.82f

        val now = SystemClock.uptimeMillis()
        val dt = if (lastT == 0L) 16L else (now - lastT).coerceAtMost(50L)
        lastT = now
        val target = if (orbOn) (if (orbBusy) 1f else 0.7f) else 0.25f
        energy += (target - energy) * 0.05f
        angle += dt * 0.0004f * (0.5f + energy * 1.5f)

        when (style) {
            1 -> drawRings(canvas, cx, cy, r)
            2 -> drawCube(canvas, cx, cy, r)
            3 -> drawGalaxy(canvas, cx, cy, r)
            else -> drawNeural(canvas, cx, cy, r)
        }

        val delay = when {
            orbBusy && !lowPower -> 16L
            lowPower || Hub.prefs.batterySaver -> 66L
            orbOn -> 33L
            else -> 50L
        }
        removeCallbacks(tick)
        postDelayed(tick, delay)
    }
}
