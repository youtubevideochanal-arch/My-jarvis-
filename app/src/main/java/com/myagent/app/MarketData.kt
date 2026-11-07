package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class Candle(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double, val v: Double)

object MarketData {
    /** Crypto: BTCUSDT (Binance). Baaki sab Yahoo: RELIANCE.NS, ^NSEI, AAPL, EURUSD=X, BTC-USD */
    fun fetch(symbol: String, interval: String): List<Candle> {
        val s = symbol.trim().uppercase()
        if (s.isEmpty()) throw Exception("symbol khaali")
        return if (s.endsWith("USDT") || s.endsWith("BUSD")) binance(s, interval) else yahoo(s, interval)
    }

    private fun yahoo(sym: String, interval: String): List<Candle> {
        val range = when (interval) {
            "5m" -> "5d"; "15m" -> "1mo"; "30m" -> "1mo"; "1h" -> "6mo"; "1wk" -> "5y"; else -> "1y"
        }
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8") +
            "?range=$range&interval=$interval"
        val r = WebTools.get(url, 3000000)
        if (r.code != 200) throw Exception("Yahoo HTTP " + r.code + " (symbol galat ho sakta hai: $sym)")
        val res = JSONObject(r.body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val ts = res.getJSONArray("timestamp")
        val q = res.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val o = q.getJSONArray("open"); val hi = q.getJSONArray("high"); val lo = q.getJSONArray("low")
        val c = q.getJSONArray("close"); val v = q.optJSONArray("volume")
        val out = ArrayList<Candle>()
        for (i in 0 until ts.length()) {
            if (c.isNull(i) || o.isNull(i) || hi.isNull(i) || lo.isNull(i)) continue
            val vol = if (v == null || v.isNull(i)) 0.0 else v.getDouble(i)
            out.add(Candle(ts.getLong(i), o.getDouble(i), hi.getDouble(i), lo.getDouble(i), c.getDouble(i), vol))
        }
        if (out.size < 5) throw Exception("data bahut kam mila ($sym, $interval)")
        return out
    }

    private fun binance(sym: String, interval: String): List<Candle> {
        val iv = if (interval == "1wk") "1w" else interval
        val r = WebTools.get("https://api.binance.com/api/v3/klines?symbol=$sym&interval=$iv&limit=300", 2000000)
        if (r.code != 200) throw Exception("Binance HTTP " + r.code + ". Try Yahoo symbol jaise BTC-USD")
        val arr = JSONArray(r.body)
        val out = ArrayList<Candle>()
        for (i in 0 until arr.length()) {
            val k = arr.getJSONArray(i)
            out.add(Candle(k.getLong(0) / 1000, k.getString(1).toDouble(), k.getString(2).toDouble(),
                k.getString(3).toDouble(), k.getString(4).toDouble(), k.getString(5).toDouble()))
        }
        return out
    }
}

object Ind {
    fun sma(v: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(v.size) { Double.NaN }
        var sum = 0.0
        for (i in v.indices) {
            sum += v[i]
            if (i >= n) sum -= v[i - n]
            if (i >= n - 1) out[i] = sum / n
        }
        return out
    }

    fun ema(v: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(v.size) { Double.NaN }
        if (v.size < n) return out
        var s = 0.0
        for (i in 0 until n) s += v[i]
        out[n - 1] = s / n
        val k = 2.0 / (n + 1)
        for (i in n until v.size) out[i] = v[i] * k + out[i - 1] * (1 - k)
        return out
    }

    fun rsi(c: DoubleArray, n: Int = 14): DoubleArray {
        val out = DoubleArray(c.size) { Double.NaN }
        if (c.size <= n) return out
        var g = 0.0
        var l = 0.0
        for (i in 1..n) {
            val d = c[i] - c[i - 1]
            if (d > 0) g += d else l -= d
        }
        var ag = g / n
        var al = l / n
        out[n] = if (al == 0.0) 100.0 else 100 - 100 / (1 + ag / al)
        for (i in n + 1 until c.size) {
            val d = c[i] - c[i - 1]
            val gg = if (d > 0) d else 0.0
            val ll = if (d < 0) -d else 0.0
            ag = (ag * (n - 1) + gg) / n
            al = (al * (n - 1) + ll) / n
            out[i] = if (al == 0.0) 100.0 else 100 - 100 / (1 + ag / al)
        }
        return out
    }

    class Macd(val line: DoubleArray, val signal: DoubleArray, val hist: DoubleArray)

    fun macd(c: DoubleArray): Macd {
        val e12 = ema(c, 12)
        val e26 = ema(c, 26)
        val line = DoubleArray(c.size) { if (e12[it].isNaN() || e26[it].isNaN()) Double.NaN else e12[it] - e26[it] }
        val signal = DoubleArray(c.size) { Double.NaN }
        val hist = DoubleArray(c.size) { Double.NaN }
        val s = 25
        if (c.size >= s + 9) {
            val part = ema(line.copyOfRange(s, c.size), 9)
            for (j in part.indices) signal[s + j] = part[j]
        }
        for (i in c.indices) if (!line[i].isNaN() && !signal[i].isNaN()) hist[i] = line[i] - signal[i]
        return Macd(line, signal, hist)
    }

    class Boll(val mid: DoubleArray, val up: DoubleArray, val lo: DoubleArray)

    fun boll(c: DoubleArray, n: Int = 20, k: Double = 2.0): Boll {
        val mid = sma(c, n)
        val up = DoubleArray(c.size) { Double.NaN }
        val lo = DoubleArray(c.size) { Double.NaN }
        for (i in n - 1 until c.size) {
            val m = mid[i]
            var ss = 0.0
            for (j in i - n + 1..i) ss += (c[j] - m) * (c[j] - m)
            val sd = sqrt(ss / n)
            up[i] = m + k * sd
            lo[i] = m - k * sd
        }
        return Boll(mid, up, lo)
    }

    fun atr(h: DoubleArray, l: DoubleArray, c: DoubleArray, n: Int = 14): DoubleArray {
        val out = DoubleArray(c.size) { Double.NaN }
        if (c.size <= n) return out
        val tr = DoubleArray(c.size)
        for (i in 1 until c.size) tr[i] = max(h[i] - l[i], max(abs(h[i] - c[i - 1]), abs(l[i] - c[i - 1])))
        var s = 0.0
        for (i in 1..n) s += tr[i]
        out[n] = s / n
        for (i in n + 1 until c.size) out[i] = (out[i - 1] * (n - 1) + tr[i]) / n
        return out
    }

    private fun f(d: Double): String = if (abs(d) >= 1) String.format("%.2f", d) else String.format("%.5f", d)

    fun summarize(symbol: String, interval: String, cs: List<Candle>): String {
        if (cs.size < 60) return "Data kam hai (" + cs.size + " candles); kam se kam 60 chahiye."
        val c = DoubleArray(cs.size) { cs[it].c }
        val h = DoubleArray(cs.size) { cs[it].h }
        val l = DoubleArray(cs.size) { cs[it].l }
        val last = c.size - 1
        val s20 = sma(c, 20); val s50 = sma(c, 50)
        val e12 = ema(c, 12); val e26 = ema(c, 26)
        val rs = rsi(c)
        val m = macd(c)
        val b = boll(c)
        val at = atr(h, l, c)
        val sb = StringBuilder()
        sb.append(symbol.uppercase()).append(" (").append(interval).append(") candles=").append(cs.size).append('\n')
        val chg = (c[last] / c[last - 1] - 1) * 100
        sb.append("Last close: ").append(f(c[last])).append("  (last bar ").append(String.format("%.2f", chg)).append("%)\n")
        sb.append("SMA20=").append(f(s20[last])).append(" SMA50=").append(f(s50[last])).append('\n')
        sb.append("EMA12=").append(f(e12[last])).append(" EMA26=").append(f(e26[last]))
            .append(if (e12[last] > e26[last]) " (bullish side)\n" else " (bearish side)\n")
        val r = rs[last]
        sb.append("RSI14=").append(String.format("%.1f", r))
            .append(if (r > 70) " (overbought zone)" else if (r < 30) " (oversold zone)" else " (neutral)").append('\n')
        sb.append("MACD=").append(f(m.line[last])).append(" signal=").append(f(m.signal[last])).append(" hist=").append(f(m.hist[last]))
            .append(if (m.hist[last] > 0) " (positive momentum)\n" else " (negative momentum)\n")
        val pb = (c[last] - b.lo[last]) / (b.up[last] - b.lo[last])
        sb.append("Bollinger: upper=").append(f(b.up[last])).append(" lower=").append(f(b.lo[last]))
            .append(" %B=").append(String.format("%.2f", pb)).append('\n')
        sb.append("ATR14=").append(f(at[last])).append(" (").append(String.format("%.2f", at[last] / c[last] * 100)).append("% of price)\n")
        var hi = -Double.MAX_VALUE
        var lo = Double.MAX_VALUE
        for (i in last - 19..last) { hi = max(hi, h[i]); lo = minOf(lo, l[i]) }
        sb.append("20-bar high (resistance)=").append(f(hi)).append("  low (support)=").append(f(lo)).append('\n')
        var vs = 0.0
        for (i in last - 19..last) vs += cs[i].v
        if (vs > 0) sb.append("Volume vs 20-avg: ").append(String.format("%.2f", cs[last].v / (vs / 20))).append("x\n")
        var bull = 0
        var bear = 0
        if (c[last] > s50[last]) bull++ else bear++
        if (s20[last] > s50[last]) bull++ else bear++
        if (e12[last] > e26[last]) bull++ else bear++
        if (m.hist[last] > 0) bull++ else bear++
        if (r > 50) bull++ else bear++
        val trend = if (c[last] > s20[last] && s20[last] > s50[last]) "UPTREND" else if (c[last] < s20[last] && s20[last] < s50[last]) "DOWNTREND" else "SIDEWAYS/MIXED"
        val cap = Hub.prefs.capital.toDouble()
        val riskAmt = cap * Hub.prefs.riskPct / 100.0
        val stopDist = 2 * at[last]
        if (stopDist > 0) {
            val qty = Math.floor(minOf(riskAmt / stopDist, cap / c[last]))
            sb.append("Sizing example: capital ").append(String.format("%.0f", cap)).append(", risk ").append(String.format("%.1f", Hub.prefs.riskPct.toDouble()))
                .append("% = ").append(String.format("%.0f", riskAmt)).append(" ; stop = 2xATR (").append(f(stopDist)).append(" door, price ").append(f(c[last] - stopDist))
                .append(") -> max qty ~ ").append(String.format("%.0f", qty)).append('\n')
        }
        sb.append("Trend: ").append(trend).append("   Indicator tally: bullish ").append(bull).append(" / bearish ").append(bear).append(" (of 5)\n")
        sb.append(Candles.report(cs))
        return sb.toString()
    }
}
