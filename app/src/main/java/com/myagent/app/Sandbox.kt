package com.myagent.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phone ke andar hi code chalane ka sandbox (Termux ki zarurat nahi).
 * JavaScript aur HTML/CSS/JS pages ek band WebView me chalte hain:
 * network band, file access band. Console output, errors, page text aur (Photo API ON ho to) screenshot ka visual check milta hai.
 */
object Sandbox {
    private val main = Handler(Looper.getMainLooper())
    private const val W = 720
    private const val H = 1280

    fun runJs(code: String): String {
        if (code.isBlank()) return "ERROR: code khaali hai"
        val safe = code.replace("</script>", "<\\/script>", ignoreCase = true)
        val html = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><script>\n" +
            "(async () => { try {\n" + safe + "\n} catch (e) { console.error('ERROR: ' + (e && e.stack ? e.stack : e)); } })();\n" +
            "</script></body></html>"
        return execute(html, false)
    }

    fun previewHtml(html: String): String {
        if (html.isBlank()) return "ERROR: html khaali hai"
        return execute(html, true)
    }

    private fun decode(v: String?): String {
        if (v == null || v == "null") return ""
        return try { JSONArray("[$v]").optString(0) } catch (e: Exception) { "" }
    }

    private fun execute(html: String, shot: Boolean): String {
        val latch = CountDownLatch(1)
        val logs = Collections.synchronizedList(ArrayList<String>())
        val errCount = AtomicInteger(0)
        val done = AtomicBoolean(false)
        val holder = arrayOfNulls<WebView>(1)
        val textBox = arrayOfNulls<String>(1)
        val shotBox = arrayOfNulls<String>(1)
        val attached = BooleanArray(1)
        val wm = Hub.app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        main.post {
            try {
                val wv = WebView(Hub.app)
                holder[0] = wv
                wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                val s = wv.settings
                s.javaScriptEnabled = true
                s.domStorageEnabled = true
                s.allowFileAccess = false
                s.allowContentAccess = false
                s.blockNetworkLoads = true
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                        if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) errCount.incrementAndGet()
                        if (logs.size < 60) logs.add(m.messageLevel().name + ": " + m.message() + " (line " + m.lineNumber() + ")")
                        return true
                    }
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (!done.compareAndSet(false, true)) return
                        main.postDelayed({
                            view.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { v ->
                                textBox[0] = decode(v)
                                if (shot) shotBox[0] = capture(view)
                                cleanup(wm, view, attached)
                                latch.countDown()
                            }
                        }, 1800)
                    }
                }
                if (Settings.canDrawOverlays(Hub.app)) {
                    try {
                        val lp = WindowManager.LayoutParams(
                            W, H, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                            PixelFormat.TRANSLUCENT
                        )
                        lp.gravity = Gravity.TOP or Gravity.START
                        lp.x = -W - 300
                        lp.y = 0
                        wm.addView(wv, lp)
                        attached[0] = true
                    } catch (e: Exception) {
                        attached[0] = false
                    }
                }
                if (!attached[0]) {
                    wv.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY))
                    wv.layout(0, 0, W, H)
                }
                wv.loadDataWithBaseURL("https://sandbox.local/", html, "text/html", "UTF-8", null)
            } catch (e: Throwable) {
                logs.add("ERROR: sandbox start nahi hua: " + (e.message ?: ""))
                errCount.incrementAndGet()
                latch.countDown()
            }
        }

        if (!latch.await(20, TimeUnit.SECONDS)) {
            logs.add("ERROR: timeout (20s) - infinite loop ya bahut bhaari code ho sakta hai")
            errCount.incrementAndGet()
            main.post { holder[0]?.let { cleanup(wm, it, attached) } }
        }

        val sb = StringBuilder()
        sb.append("RESULT: ").append(if (errCount.get() == 0) "OK (koi JS error nahi)" else "FAIL (" + errCount.get() + " error)").append('\n')
        sb.append("CONSOLE:\n")
        synchronized(logs) { if (logs.isEmpty()) sb.append("(kuch print nahi hua)\n") else for (l in logs) sb.append(l).append('\n') }
        val text = (textBox[0] ?: "").trim()
        sb.append("PAGE TEXT: ").append(if (text.isEmpty()) "(khaali)" else text.take(800)).append('\n')
        if (shot) {
            val b64 = shotBox[0]
            if (b64 == null) {
                sb.append("VISUAL CHECK: screenshot nahi ban saka\n")
            } else if (!Hub.prefs.photoOn || Hub.prefs.photoKey.isBlank()) {
                sb.append("VISUAL CHECK: skip (Settings me Photo API ON karo to design bhi check hoga)\n")
            } else {
                try {
                    val v = ApiClient.describeImage(
                        Hub.prefs, b64,
                        "Ye ek web-page/app preview ka screenshot hai. Visual bugs batao: overlap, kata hua text, khali page, kharab contrast, tuta layout. Chhoti list me, aur agar sab theek lage to 'theek hai' likho."
                    )
                    sb.append("VISUAL CHECK: ").append(v.take(900)).append('\n')
                } catch (e: ApiException) {
                    sb.append("VISUAL CHECK: Photo API fail (").append(e.code).append(")\n")
                }
            }
        }
        return sb.toString()
    }

    private fun capture(v: WebView): String? {
        return try {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanup(wm: WindowManager, v: WebView, attached: BooleanArray) {
        try { if (attached[0]) { wm.removeView(v); attached[0] = false } } catch (e: Exception) { }
        try { v.destroy() } catch (e: Exception) { }
    }
}
