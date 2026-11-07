package com.myagent.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Candlestick patterns, support/resistance aur trade-plan. Sab asli candles se nikalta hai - koi guarantee nahi. */
object Candles {
    private fun body(c: Candle) = abs(c.c - c.o)
    private fun range(c: Candle) = max(c.h - c.l, 1e-9)
    private fun upper(c: Candle) = c.h - max(c.o, c.c)
    private fun lower(c: Candle) = min(c.o, c.c) - c.l
    private fun bull(c: Candle) = c.c > c.o
    private fun bear(c: Candle) = c.c < c.o
    private fun mid(c: Candle) = (c.o + c.c) / 2

    private fun trendBefore(cs: List<Candle>, i: Int): Int {
        if (i < 4) return 0
        val a = cs[i - 4].c
        val b = cs[i - 1].c
        return if (b < a * 0.995) -1 else if (b > a * 1.005) 1 else 0
    }

    /** i-th candle par ban rahe patterns. */
    fun at(cs: List<Candle>, i: Int): List<String> {
        val out = ArrayList<String>()
        if (i < 2 || i >= cs.size) return out
        val c = cs[i]; val p = cs[i - 1]; val pp = cs[i - 2]
        val r = range(c); val b = body(c)
        val tr = trendBefore(cs, i)
        if (b <= 0.1 * r) out.add("Doji (faisla nahi - palatne ka sanket ho sakta hai)")
        if (lower(c) >= 2 * b && upper(c) <= 0.35 * b + 0.05 * r && b > 0.02 * r) {
            if (tr < 0) out.add("Hammer (BULLISH reversal sanket)") else if (tr > 0) out.add("Hanging Man (BEARISH sanket)")
        }
        if (upper(c) >= 2 * b && lower(c) <= 0.35 * b + 0.05 * r && b > 0.02 * r) {
            if (tr > 0) out.add("Shooting Star (BEARISH reversal sanket)") else if (tr < 0) out.add("Inverted Hammer (BULLISH sanket)")
        }
        if (b >= 0.9 * r) out.add(if (bull(c)) "Bullish Marubozu (mazboot kharidari)" else "Bearish Marubozu (mazboot bikwali)")
        if (bear(p) && bull(c) && c.o <= p.c && c.c >= p.o && body(c) > body(p)) out.add("Bullish Engulfing (BULLISH)")
        if (bull(p) && bear(c) && c.o >= p.c && c.c <= p.o && body(c) > body(p)) out.add("Bearish Engulfing (BEARISH)")
        if (bull(p) && bear(c) && c.o > p.c && c.c < mid(p) && c.c > p.o) out.add("Dark Cloud Cover (BEARISH)")
        if (bear(p) && bull(c) && c.o < p.c && c.c > mid(p) && c.c < p.o) out.add("Piercing Line (BULLISH)")
        if (bear(pp) && body(pp) > 0.5 * range(pp) && body(p) < 0.3 * range(p) && bull(c) && c.c > mid(pp)) out.add("Morning Star (BULLISH reversal)")
        if (bull(pp) && body(pp) > 0.5 * range(pp) && body(p) < 0.3 * range(p) && bear(c) && c.c < mid(pp)) out.add("Evening Star (BEARISH reversal)")
        if (bull(c) && bull(p) && bull(pp) && c.c > p.c && p.c > pp.c && c.o > p.o && p.o > pp.o && c.o < p.c && p.o < pp.c) out.add("Three White Soldiers (BULLISH)")
        if (bear(c) && bear(p) && bear(pp) && c.c < p.c && p.c < pp.c && c.o < p.o && p.o < pp.o && c.o > p.c && p.o > pp.c) out.add("Three Black Crows (BEARISH)")
        if (c.h < p.h && c.l > p.l) out.add("Inside Bar (consolidation, breakout ka intezaar)")
        return out
    }

    fun recent(cs: List<Candle>, n: Int = 5): String {
        val sb = StringBuilder()
        val last = cs.size - 1
        for (i in max(2, last - n + 1)..last) {
            val ps = at(cs, i)
            if (ps.isNotEmpty()) sb.append("  - ").append(if (i == last) "ab (last bar)" else "${last - i} bar pehle").append(": ").append(ps.joinToString("; ")).append('\n')
        }
        return if (sb.isEmpty()) "  (last 5 bars me koi saaf pattern nahi)\n" else sb.toString()
    }

    /** Swing high/low se support-resistance levels. */
    fun levels(cs: List<Candle>): Pair<List<Double>, List<Double>> {
        val n = cs.size
        val price = cs[n - 1].c
        val pts = ArrayList<Double>()
        val from = max(3, n - 150)
        for (i in from until n - 3) {
            val h = cs[i].h; val l = cs[i].l
            if ((1..3).all { cs[i - it].h < h && cs[i + it].h < h }) pts.add(h)
            if ((1..3).all { cs[i - it].l > l && cs[i + it].l > l }) pts.add(l)
        }
        // paas ke levels ko ek me milao
        val sorted = pts.sorted()
        val clusters = ArrayList<Double>()
        var grp = ArrayList<Double>()
        for (x in sorted) {
            if (grp.isEmpty() || abs(x - grp.average()) / price < 0.006) grp.add(x) else { clusters.add(grp.average()); grp = arrayListOf(x) }
        }
        if (grp.isNotEmpty()) clusters.add(grp.average())
        val sup = clusters.filter { it < price }.sortedDescending().take(3)
        val res = clusters.filter { it > price }.sorted().take(3)
        return Pair(sup, res)
    }

    private fun f(d: Double): String = if (abs(d) >= 1) String.format("%.2f", d) else String.format("%.5f", d)

    fun report(cs: List<Candle>): String {
        if (cs.size < 30) return ""
        val sb = StringBuilder()
        sb.append("Candle patterns:\n").append(recent(cs))
        val (sup, res) = levels(cs)
        sb.append("Support levels: ").append(if (sup.isEmpty()) "-" else sup.joinToString(", ") { f(it) }).append('\n')
        sb.append("Resistance levels: ").append(if (res.isEmpty()) "-" else res.joinToString(", ") { f(it) }).append('\n')
        val last = cs.size - 1
        val hh = cs[last].h > cs[last - 5].h && cs[last].l > cs[last - 5].l
        val ll = cs[last].h < cs[last - 5].h && cs[last].l < cs[last - 5].l
        sb.append("Structure (5 bar): ").append(if (hh) "higher-high/higher-low (bullish)" else if (ll) "lower-high/lower-low (bearish)" else "mixed").append('\n')
        return sb.toString()
    }
}

object TradePlan {
    private fun f(d: Double): String = if (abs(d) >= 1) String.format("%.2f", d) else String.format("%.5f", d)

    /** side: "buy" | "sell" | "" (khud decide). Sirf PLAN deta hai - order nahi lagata. */
    fun plan(symbol: String, interval: String, cs: List<Candle>, sideIn: String, capIn: Double): String {
        if (cs.size < 60) return "Plan ke liye kam se kam 60 candles chahiye."
        val c = DoubleArray(cs.size) { cs[it].c }
        val h = DoubleArray(cs.size) { cs[it].h }
        val l = DoubleArray(cs.size) { cs[it].l }
        val last = c.size - 1
        val at = Ind.atr(h, l, c)[last]
        val s20 = Ind.sma(c, 20)[last]
        val s50 = Ind.sma(c, 50)[last]
        val rs = Ind.rsi(c)[last]
        val m = Ind.macd(c).hist[last]
        var score = 0
        if (c[last] > s50) score++ else score--
        if (s20 > s50) score++ else score--
        if (m > 0) score++ else score--
        if (rs > 55) score++ else if (rs < 45) score--
        val pats = Candles.at(cs, last) + Candles.at(cs, last - 1)
        for (p in pats) { if (p.contains("BULLISH")) score++; if (p.contains("BEARISH")) score-- }
        val side = when (sideIn.lowercase()) { "buy", "long" -> "BUY"; "sell", "short" -> "SELL"; else -> if (score >= 0) "BUY" else "SELL" }
        val (sup, res) = Candles.levels(cs)
        val entry = c[last]
        val atrStop = 1.5 * at
        var stop = if (side == "BUY") entry - atrStop else entry + atrStop
        // swing level ke paar stop (agar paas ho)
        if (side == "BUY") sup.firstOrNull { it < entry && entry - it < 3 * at }?.let { stop = min(stop, it - 0.25 * at) }
        else res.firstOrNull { it > entry && it - entry < 3 * at }?.let { stop = max(stop, it + 0.25 * at) }
        val risk = abs(entry - stop)
        val t1 = if (side == "BUY") entry + 1.5 * risk else entry - 1.5 * risk
        val t2 = if (side == "BUY") entry + 2.5 * risk else entry - 2.5 * risk
        val cap = if (capIn > 0) capIn else Hub.prefs.capital.toDouble()
        val riskAmt = cap * Hub.prefs.riskPct / 100.0
        val qty = if (risk > 0) Math.floor(min(riskAmt / risk, cap / entry)) else 0.0
        val agree = if ((side == "BUY" && score > 0) || (side == "SELL" && score < 0)) "indicators/candles is side se SAHMAT" else "indicators is side se SAHMAT NAHI (counter-trend, risk zyada)"
        val sb = StringBuilder()
        sb.append("TRADE PLAN (sirf suggestion, order nahi laga): ").append(symbol.uppercase()).append(" ").append(interval).append('\n')
        sb.append("Side: ").append(side).append("   confluence score=").append(score).append(" -> ").append(agree).append('\n')
        sb.append("Entry ~").append(f(entry)).append("   Stop-loss ").append(f(stop)).append("   Target1 ").append(f(t1)).append(" (1.5R)   Target2 ").append(f(t2)).append(" (2.5R)\n")
        sb.append("Risk/unit ").append(f(risk)).append("   Capital ").append(String.format("%.0f", cap)).append("   risk ").append(String.format("%.1f", Hub.prefs.riskPct.toDouble())).append("% = ").append(String.format("%.0f", riskAmt))
            .append("   Qty ~").append(String.format("%.0f", qty)).append('\n')
        sb.append("Aadhaar: ATR=").append(f(at)).append(" RSI=").append(String.format("%.0f", rs)).append(" SMA20/50=").append(f(s20)).append("/").append(f(s50)).append(" MACDhist=").append(f(m)).append('\n')
        if (pats.isNotEmpty()) sb.append("Candles: ").append(pats.joinToString("; ")).append('\n')
        sb.append("Pehle chhota/paper trade karo. Market kabhi bhi ulta ja sakta hai - kamai ki koi guarantee nahi.")
        return sb.toString()
    }
}
