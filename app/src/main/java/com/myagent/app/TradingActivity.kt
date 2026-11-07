package com.myagent.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trading folder: chart + indicators + AI analysis, market research, aur training lessons. */
class TradingActivity : AppCompatActivity() {

    private lateinit var etSym: EditText
    private lateinit var spInt: Spinner
    private lateinit var chart: ChartView
    private lateinit var out: TextView
    private lateinit var chips: LinearLayout
    private var lastText = ""
    private val intervals = listOf("5m", "15m", "1h", "1d", "1wk")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pal = Palettes.current()
        val frame = FrameLayout(this)
        val bg = CircuitBackground(this)
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))
        val sv = ScrollView(this)
        sv.fitsSystemWindows = true
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(40))
        sv.addView(root)
        frame.addView(sv, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        val title = TextView(this)
        title.text = "📈 Trading Hub"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        root.addView(title)

        val note = TextView(this)
        note.text = "India: RELIANCE.NS, TCS.NS, ^NSEI (Nifty), ^NSEBANK · US: AAPL · Forex: EURUSD=X · Crypto: BTCUSDT ya BTC-USD"
        note.textSize = 11f
        note.setTextColor(Color.rgb(150, 160, 195))
        note.setPadding(0, dp(4), 0, dp(8))
        root.addView(note)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        etSym = EditText(this)
        etSym.setText(Hub.prefs.lastSymbol)
        etSym.hint = "Symbol"
        etSym.setTextColor(Color.WHITE)
        etSym.setHintTextColor(Color.GRAY)
        etSym.setSingleLine(true)
        row.addView(etSym, LinearLayout.LayoutParams(0, -2, 1f))
        spInt = Spinner(this)
        spInt.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)
        spInt.setSelection(3)
        row.addView(spInt)
        root.addView(row)

        chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        root.addView(hs)
        refreshChips()

        chart = ChartView(this)
        val clp = LinearLayout.LayoutParams(-1, dp(210))
        clp.topMargin = dp(10)
        root.addView(chart, clp)

        val legend = TextView(this)
        legend.text = "━ price   ━ SMA20   ━ SMA50   ━ Bollinger"
        legend.textSize = 11f
        legend.setTextColor(pal.c2)
        root.addView(legend)

        val b1 = LinearLayout(this)
        b1.orientation = LinearLayout.HORIZONTAL
        addBtn(b1, "📊 Analyze") { analyze() }
        addBtn(b1, "🔎 Research") { research() }
        addBtn(b1, "🎓 Train me") { train() }
        root.addView(b1)
        val b2 = LinearLayout(this)
        b2.orientation = LinearLayout.HORIZONTAL
        addBtn(b2, "⭐ Watchlist +") { addWatch() }
        addBtn(b2, "🧪 Backtest") { backtest() }
        addBtn(b2, "💾 Report save") { saveReport() }
        root.addView(b2)

        out = TextView(this)
        out.setTextColor(Color.rgb(221, 227, 255))
        out.textSize = 13.5f
        out.setTextIsSelectable(true)
        out.setPadding(0, dp(12), 0, 0)
        out.text = "Symbol daalo aur 📊 Analyze dabao. Indicators (RSI, MACD, SMA/EMA, Bollinger, ATR) phone par hi calculate hote hain."
        root.addView(out)
    }

    private fun addBtn(parent: LinearLayout, t: String, onClick: () -> Unit) {
        val pal = Palettes.current()
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(Color.rgb(5, 6, 13))
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(pal.c1, pal.c2))
        g.cornerRadius = dp(14).toFloat()
        b.background = g
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(3), dp(8), dp(3), 0)
        parent.addView(b, lp)
    }

    private fun refreshChips() {
        chips.removeAllViews()
        val pal = Palettes.current()
        for (s in Hub.prefs.watchlist.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val tv = TextView(this)
            tv.text = s
            tv.setTextColor(Color.WHITE)
            tv.setPadding(dp(12), dp(6), dp(12), dp(6))
            val g = GradientDrawable()
            g.setColor(Color.TRANSPARENT)
            g.setStroke(dp(1), pal.c1)
            g.cornerRadius = dp(16).toFloat()
            tv.background = g
            tv.setOnClickListener { etSym.setText(s) }
            tv.setOnLongClickListener {
                Hub.prefs.watchlist = Hub.prefs.watchlist.split(",").map { x -> x.trim() }.filter { x -> x != s }.joinToString(",")
                refreshChips()
                true
            }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(0, dp(8), dp(8), 0)
            chips.addView(tv, lp)
        }
    }

    private fun addWatch() {
        val s = etSym.text.toString().trim().uppercase()
        if (s.isEmpty()) return
        val list = Hub.prefs.watchlist.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!list.contains(s)) list.add(s)
        Hub.prefs.watchlist = list.joinToString(",")
        refreshChips()
        Toast.makeText(this, "Watchlist me add (hatane ke liye chip lamba dabao)", Toast.LENGTH_SHORT).show()
    }

    private fun saveReport() {
        if (lastText.isBlank()) { Toast.makeText(this, "Pehle kuch analyze karo", Toast.LENGTH_SHORT).show(); return }
        val sym = etSym.text.toString().trim().uppercase()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        try {
            val path = FileStore.save(this, "Trading", sym + "_" + stamp + ".md", lastText)
            Toast.makeText(this, "Saved: $path", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save fail: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun work(label: String, block: () -> String) {
        out.text = label
        Thread {
            val r = try {
                block()
            } catch (e: ApiException) {
                "❌ AI API [" + e.code + "] " + (e.message ?: "")
            } catch (e: Exception) {
                "❌ " + (e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { out.text = r; lastText = r }
        }.start()
    }

    private fun loadSummary(sym: String, iv: String): String {
        val cs = MarketData.fetch(sym, iv)
        runOnUiThread { chart.setData(cs) }
        return Ind.summarize(sym, iv, cs)
    }

    private fun analyze() {
        val sym = etSym.text.toString().trim().uppercase()
        val iv = intervals[spInt.selectedItemPosition]
        Hub.prefs.lastSymbol = sym
        work("⏳ $sym ka data la raha hoon...") {
            val sum = loadSummary(sym, iv)
            val ai = Ai.ask(Hub.prefs, listOf(
                Msg("system", "Tum market analyst ho. Diye gaye indicator data se Hinglish me chhota analysis do: trend, key levels (support/resistance), bullish aur bearish scenario, risk (ATR se stop-loss ka idea). Koi guarantee ya pakki bhavishyavani mat do."),
                Msg("user", sum)
            ))
            sum + "\n🤖 AI view:\n" + ai + "\n\n⚠ Sirf padhai/research ke liye. Ye financial advice nahi hai; nuksan ka risk aapka hai."
        }
    }

    private fun backtest() {
        val sym = etSym.text.toString().trim().uppercase()
        val iv = intervals[spInt.selectedItemPosition]
        work("⏳ $sym par backtest chal raha hai...") {
            val cs = MarketData.fetch(sym, iv)
            runOnUiThread { chart.setData(cs) }
            Backtest.report(sym, iv, cs)
        }
    }

    private fun research() {
        val sym = etSym.text.toString().trim().uppercase()
        val iv = intervals[spInt.selectedItemPosition]
        work("⏳ $sym par news aur data dhoondh raha hoon...") {
            val sum = try { loadSummary(sym, iv) } catch (e: Exception) { "(chart data nahi mila: " + (e.message ?: "") + ")" }
            val s = WebTools.search("$sym stock market news today")
            val urls = Regex("https?://\\S+").findAll(s).map { it.value }.take(2).toList()
            val pages = urls.joinToString("\n---\n") { u -> try { WebTools.fetchText(u, 1500) } catch (e: Exception) { "" } }
            val ai = Ai.ask(Hub.prefs, listOf(
                Msg("system", "Tum market researcher ho. Hinglish me brief do: 1) taaza news ka saar 2) sentiment 3) aane wale events 4) technical levels 5) risks. Sirf diye gaye data par bharosa karo; jo data me nahi hai wo mat banao."),
                Msg("user", "TECHNICALS:\n$sum\n\nSEARCH RESULTS:\n$s\n\nPAGES:\n$pages")
            ))
            "🔎 Research: $sym\n\n" + ai + "\n\nSources:\n" + s + "\n⚠ Financial advice nahi."
        }
    }

    private fun train() {
        val sym = etSym.text.toString().trim().uppercase()
        val iv = intervals[spInt.selectedItemPosition]
        val lvl = Hub.prefs.trainLevel
        val topic = TOPICS[lvl % TOPICS.size]
        Hub.prefs.trainLevel = lvl + 1
        work("🎓 Lesson ${lvl + 1}: $topic ...") {
            val sum = try { loadSummary(sym, iv) } catch (e: Exception) { "(live data nahi mila)" }
            val ai = Ai.ask(Hub.prefs, listOf(
                Msg("system", "Tum trading teacher ho (beginner, Hinglish). Topic ko saral bhasha me samjhao, diye gaye asli chart-data se ek example do, phir 3 quiz sawal aur ek paper-trade exercise do (entry, stop-loss, target, risk 1% rule). Profit ki guarantee kabhi mat do."),
                Msg("user", "Lesson ${lvl + 1}: $topic\n\nLive data:\n$sum")
            ))
            "🎓 Lesson ${lvl + 1}: $topic\n\n" + ai
        }
    }

    companion object {
        private val TOPICS = listOf(
            "Candlestick basics", "Support & Resistance", "Trend aur Moving Averages", "RSI",
            "MACD", "Bollinger Bands", "Volume", "Risk management & position sizing",
            "Stop-loss aur Risk:Reward", "Trading psychology"
        )
    }
}
