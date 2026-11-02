package com.myagent.app

import kotlin.math.max

/** Seedha-saadha backtest (long-only). Isse "kamai ki guarantee" mat samjho - ye sirf purane data par test hai. */
object Backtest {
    private const val FEE = 0.001 // 0.1% har taraf

    fun report(symbol: String, interval: String, cs: List<Candle>): String {
        if (cs.size < 80) return "Backtest ke liye kam se kam 80 candles chahiye (" + cs.size + " mile)."
        val c = DoubleArray(cs.size) { cs[it].c }
        val f = Ind.sma(c, 20)
        val s = Ind.sma(c, 50)
        val rsi = Ind.rsi(c)
        val a = runStrat("SMA20/50 crossover", cs, 50, { i -> f[i] > s[i] }, { i -> f[i] < s[i] })
        val b = runStrat("RSI mean-reversion (buy <30, sell >55)", cs, 15, { i -> rsi[i] < 30 }, { i -> rsi[i] > 55 })
        return symbol.uppercase() + " (" + interval + ") backtest, " + cs.size + " candles\n\n" + a + "\n\n" + b +
            "\n\nDHYAN: fees 0.1%/taraf maani gayi; slippage, tax aur gap nahi. Kam trades = bharosa kam. " +
            "Purane data par accha chalna future ki guarantee nahi (overfitting ka risk)."
    }

    private fun runStrat(name: String, cs: List<Candle>, start: Int, enter: (Int) -> Boolean, exit: (Int) -> Boolean): String {
        var inPos = false
        var entry = 0.0
        var eq = 1.0
        var peak = 1.0
        var mdd = 0.0
        var trades = 0
        var wins = 0
        var sum = 0.0
        for (j in start + 1 until cs.size) {
            if (!inPos && enter(j - 1)) {
                inPos = true
                entry = cs[j].o
            } else if (inPos && exit(j - 1)) {
                val ret = cs[j].o / entry - 1 - 2 * FEE
                eq *= 1 + ret; trades++; sum += ret
                if (ret > 0) wins++
                inPos = false
            }
            val now = if (inPos) eq * (cs[j].c / entry) else eq
            peak = max(peak, now)
            mdd = max(mdd, (peak - now) / peak)
        }
        if (inPos) {
            val ret = cs.last().c / entry - 1 - 2 * FEE
            eq *= 1 + ret; trades++; sum += ret
            if (ret > 0) wins++
        }
        val bh = cs.last().c / cs[start].o - 1
        val wr = if (trades > 0) wins * 100.0 / trades else 0.0
        val avg = if (trades > 0) sum * 100 / trades else 0.0
        return "▸ " + name + "\n  trades=" + trades + "  win-rate=" + String.format("%.0f", wr) + "%  avg/trade=" + String.format("%.2f", avg) +
            "%\n  strategy return=" + String.format("%.1f", (eq - 1) * 100) + "%  max drawdown=" + String.format("%.1f", mdd * 100) +
            "%\n  buy&hold return=" + String.format("%.1f", bh * 100) + "%"
    }
}
