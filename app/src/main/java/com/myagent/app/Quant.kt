package com.myagent.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Trading research ka "lab": Freqtrade/Jesse jaise tools ke idea (strategy compare, parameter search, Monte Carlo,
 * grid/DCA plan) - sab purane data par, bina look-ahead (signal bar close par, fill agli bar ke open par).
 * Ye kamai ki guarantee nahi; sirf samajhne ka tool hai.
 */
object Quant {
    private const val FEE = 0.001

    class Run(val trades: List<Double>, val eq: Double, val mdd: Double)

    private fun sim(cs: List<Candle>, from: Int, to: Int, enter: (Int) -> Boolean, exit: (Int) -> Boolean): Run {
        var inPos = false
        var entry = 0.0
        var eq = 1.0
        var peak = 1.0
        var mdd = 0.0
        val rets = ArrayList<Double>()
        val end = min(to, cs.size)
        for (j in max(from, 1) until end) {
            if (!inPos && enter(j - 1)) {
                inPos = true; entry = cs[j].o
            } else if (inPos && exit(j - 1)) {
                val r = cs[j].o / entry - 1 - 2 * FEE
                eq *= 1 + r; rets.add(r); inPos = false
            }
            val now = if (inPos) eq * (cs[j].c / entry) else eq
            peak = max(peak, now)
            mdd = max(mdd, (peak - now) / peak)
        }
        if (inPos) {
            val r = cs[end - 1].c / entry - 1 - 2 * FEE
            eq *= 1 + r; rets.add(r)
        }
        return Run(rets, eq, mdd)
    }

    class Strat(val name: String, val enter: (Int) -> Boolean, val exit: (Int) -> Boolean)

    private fun strategies(cs: List<Candle>): List<Strat> {
        val c = DoubleArray(cs.size) { cs[it].c }
        val h = DoubleArray(cs.size) { cs[it].h }
        val l = DoubleArray(cs.size) { cs[it].l }
        val s20 = Ind.sma(c, 20); val s50 = Ind.sma(c, 50)
        val e9 = Ind.ema(c, 9); val e21 = Ind.ema(c, 21)
        val rsi = Ind.rsi(c)
        val mc = Ind.macd(c)
        val bb = Ind.boll(c)
        fun hi(i: Int, n: Int): Double { var m = -1e18; for (k in max(0, i - n) until i) m = max(m, h[k]); return m }
        fun lo(i: Int, n: Int): Double { var m = 1e18; for (k in max(0, i - n) until i) m = min(m, l[k]); return m }
        return listOf(
            Strat("SMA 20/50 crossover", { i -> s20[i] > s50[i] }, { i -> s20[i] < s50[i] }),
            Strat("EMA 9/21 crossover", { i -> e9[i] > e21[i] }, { i -> e9[i] < e21[i] }),
            Strat("RSI reversion (buy<30, sell>55)", { i -> rsi[i] < 30 }, { i -> rsi[i] > 55 }),
            Strat("MACD histogram", { i -> mc.hist[i] > 0 }, { i -> mc.hist[i] < 0 }),
            Strat("Bollinger bounce", { i -> c[i] < bb.lo[i] }, { i -> c[i] > bb.mid[i] }),
            Strat("Donchian 20 breakout", { i -> i > 20 && c[i] > hi(i, 20) }, { i -> i > 10 && c[i] < lo(i, 10) }),
            Strat("Trend + RSI dip", { i -> c[i] > s50[i] && rsi[i] < 40 }, { i -> rsi[i] > 60 || c[i] < s50[i] })
        )
    }

    private fun ok(d: Double) = !d.isNaN()

    private fun pct(d: Double) = String.format("%.1f", d * 100) + "%"

    /** Saari strategies: poora data + out-of-sample (aakhri 30%) + best par Monte Carlo. */
    fun compare(symbol: String, interval: String, cs: List<Candle>): String {
        if (cs.size < 120) return "Strategy compare ke liye kam se kam 120 candles chahiye (" + cs.size + " mile)."
        val start = 55
        val split = start + (cs.size - start) * 7 / 10
        val bh = cs.last().c / cs[start].o - 1
        val bhTest = cs.last().c / cs[split].o - 1
        val rows = ArrayList<Triple<String, Run, Run>>()
        for (s in strategies(cs)) {
            val full = sim(cs, start, cs.size, s.enter, s.exit)
            val test = sim(cs, split, cs.size, s.enter, s.exit)
            rows.add(Triple(s.name, full, test))
        }
        rows.sortByDescending { it.second.eq }
        val sb = StringBuilder()
        sb.append(symbol.uppercase()).append(" (").append(interval).append(") ").append(cs.size).append(" candles; buy&hold ").append(pct(bh))
            .append(" (test hissa ").append(pct(bhTest)).append(")\n")
        for ((n, f, t) in rows) {
            val wr = if (f.trades.isEmpty()) 0.0 else f.trades.count { it > 0 } * 100.0 / f.trades.size
            sb.append("• ").append(n).append(": return ").append(pct(f.eq - 1)).append(", DD ").append(pct(f.mdd))
                .append(", trades ").append(f.trades.size).append(", win ").append(String.format("%.0f", wr)).append("% | out-of-sample ")
                .append(pct(t.eq - 1)).append(" (").append(t.trades.size).append(" trades)\n")
        }
        val best = rows.first()
        if (best.second.trades.size >= 5) sb.append("\n").append(monteCarlo(best.first, best.second.trades))
        else sb.append("\nBest strategy ke trades bahut kam (<5): Monte Carlo ka matlab nahi.")
        sb.append("\nDHYAN: fees 0.1%/taraf; slippage/tax nahi. Jo strategy out-of-sample me bhi theek ho wahi bharose ke kareeb; sirf poore data me accha = overfit ka shak.")
        return sb.toString()
    }

    private fun monteCarlo(name: String, trades: List<Double>): String {
        val rnd = java.util.Random(7)
        val finals = DoubleArray(400)
        val dds = DoubleArray(400)
        val t = trades.toMutableList()
        for (k in 0 until 400) {
            java.util.Collections.shuffle(t, rnd)
            var eq = 1.0; var peak = 1.0; var dd = 0.0
            for (r in t) { eq *= 1 + r; peak = max(peak, eq); dd = max(dd, (peak - eq) / peak) }
            finals[k] = eq; dds[k] = dd
        }
        finals.sort(); dds.sort()
        return "Monte Carlo ($name, trades ka order 400 baar badla): kharab case (5%) return " + pct(finals[20] - 1) +
            ", median " + pct(finals[200] - 1) + ", 95% case me max drawdown " + pct(dds[380])
    }

    /** SMA fast/slow ka chhota grid - pehle 70% par sabse accha, phir baaki 30% par jaanch (overfit pakadne ke liye). */
    fun optimize(symbol: String, interval: String, cs: List<Candle>): String {
        if (cs.size < 150) return "Optimize ke liye kam se kam 150 candles chahiye (" + cs.size + " mile)."
        val c = DoubleArray(cs.size) { cs[it].c }
        val fasts = listOf(5, 8, 10, 12, 15, 20)
        val slows = listOf(30, 40, 50, 60, 80, 100)
        val start = 105
        val split = start + (cs.size - start) * 7 / 10
        class Row(val f: Int, val s: Int, val train: Run, val test: Run)
        val rows = ArrayList<Row>()
        for (f in fasts) for (s in slows) {
            if (f >= s) continue
            val a = Ind.sma(c, f); val b = Ind.sma(c, s)
            val en = { i: Int -> ok(a[i]) && ok(b[i]) && a[i] > b[i] }
            val ex = { i: Int -> ok(a[i]) && ok(b[i]) && a[i] < b[i] }
            rows.add(Row(f, s, sim(cs, start, split, en, ex), sim(cs, split, cs.size, en, ex)))
        }
        rows.sortByDescending { it.train.eq }
        val sb = StringBuilder(symbol.uppercase() + " (" + interval + ") SMA fast/slow search - train 70% / test 30%\n")
        for (r in rows.take(4)) {
            sb.append("• SMA ").append(r.f).append("/").append(r.s).append(": train ").append(pct(r.train.eq - 1)).append(" (DD ").append(pct(r.train.mdd))
                .append(") -> test ").append(pct(r.test.eq - 1)).append(" (DD ").append(pct(r.test.mdd)).append(")\n")
        }
        val b = rows.first()
        sb.append(if (b.test.eq < 1.0 && b.train.eq > 1.05) "⚠ Train me accha par test me ghata: ye overfit lagta hai - is setting par bharosa mat karo."
        else "Test me bhi tikta dikha, par ye sirf ek daur ka data hai.")
        return sb.toString()
    }

    fun gridPlan(symbol: String, low: Double, high: Double, grids: Int, capital: Double): String {
        if (low <= 0 || high <= low) return "ERROR: low < high aur dono > 0 do."
        val n = grids.coerceIn(2, 100)
        val step = (high - low) / n
        val perGrid = capital / n
        val mid = (low + high) / 2
        val cyclePct = step / mid * 100 - 0.2
        val sb = StringBuilder(symbol.uppercase() + " grid plan: range " + low + "-" + high + ", " + n + " grid, step " + String.format("%.4f", step) + ", har grid par ~" + String.format("%.0f", perGrid) + " lagega\n")
        sb.append("Har poora buy->sell chakkar ka andaazan munafa ~").append(String.format("%.2f", cyclePct)).append("% (fees 0.1%/taraf ke baad)")
        if (cyclePct <= 0) sb.append(" ⚠ negative: grid kam karo ya range badi karo.")
        sb.append("\nLevels (neeche se): ")
        for (i in 0..min(n, 6)) sb.append(String.format("%.4f", low + step * i)).append(if (i < min(n, 6)) ", " else " ...")
        sb.append("\nRisk: price range ke bahar gaya to ya sab bik jata hai (munafa ruk jata) ya sab neeche phans jata hai. Range ke neeche stop-loss socho.")
        return sb.toString()
    }

    fun dcaPlan(symbol: String, total: Double, times: Int, dropPct: Double, price: Double): String {
        val n = times.coerceIn(2, 20)
        val each = total / n
        var qty = 0.0
        val sb = StringBuilder(symbol.uppercase() + " DCA plan: total " + total + ", " + n + " kisht, har kisht " + String.format("%.0f", each) + ", har " + dropPct + "% girne par\n")
        for (i in 0 until n) {
            val p = price * (1 - dropPct / 100).pow(i)
            qty += each / p
            sb.append("  #").append(i + 1).append(" @ ").append(String.format("%.4f", p)).append("\n")
        }
        sb.append("Poori kishten lagne par average entry ~").append(String.format("%.4f", total / qty)).append(". Agar price girta hi raha to nuksan badhta hai - DCA risk khatam nahi karta.")
        return sb.toString()
    }
}

/** Nakli (paper) trading: asli paise nahi, par asli price par - strategy ko pehle yahan aazmao. */
object Paper {
    private fun load(): JSONObject {
        val j = try { JSONObject(Hub.prefs.str("paper", "")) } catch (e: Exception) { null }
        if (j != null && j.has("start")) return j
        return JSONObject().put("start", Hub.prefs.capital.toDouble()).put("realized", 0.0).put("pos", JSONArray()).put("closed", JSONArray()).put("seq", 1)
    }

    private fun save(j: JSONObject) { Hub.prefs.putStr("paper", j.toString()) }

    private fun price(sym: String): Double {
        return try { MarketData.fetch(sym, "5m").last().c } catch (e: Exception) { MarketData.fetch(sym, "1d").last().c }
    }

    private fun f(d: Double) = if (abs(d) >= 1) String.format("%.2f", d) else String.format("%.5f", d)

    fun act(a: JSONObject): String {
        val j = load()
        val pos = j.getJSONArray("pos")
        when (a.optString("do", "status").lowercase()) {
            "reset" -> { Hub.prefs.putStr("paper", ""); return "paper account reset (capital " + Hub.prefs.capital.toInt() + ")" }
            "open" -> {
                val sym = a.optString("symbol").trim().uppercase()
                if (sym.isEmpty()) return "ERROR: symbol do"
                val side = if (a.optString("side").lowercase() in listOf("sell", "short")) -1 else 1
                val p = price(sym)
                val sl = a.optDouble("sl", Double.NaN)
                val tp = a.optDouble("tp", Double.NaN)
                var qty = a.optDouble("qty", Double.NaN)
                if (qty.isNaN()) {
                    if (sl.isNaN() || sl == p) return "ERROR: qty ya (sl + risk) do"
                    val equity = j.getDouble("start") + j.getDouble("realized")
                    qty = equity * (a.optDouble("risk_pct", Hub.prefs.riskPct.toDouble()) / 100.0) / abs(p - sl)
                }
                if (qty <= 0) return "ERROR: qty galat"
                val id = j.getInt("seq"); j.put("seq", id + 1)
                pos.put(JSONObject().put("id", id).put("sym", sym).put("dir", side).put("qty", qty).put("entry", p).put("sl", if (sl.isNaN()) JSONObject.NULL else sl).put("tp", if (tp.isNaN()) JSONObject.NULL else tp))
                save(j)
                return "PAPER #$id " + (if (side > 0) "BUY " else "SELL ") + sym + " qty " + String.format("%.4f", qty) + " @ " + f(p) + (if (!sl.isNaN()) " SL " + f(sl) else "") + (if (!tp.isNaN()) " TP " + f(tp) else "") + "  (nakli trade)"
            }
            "close" -> {
                val id = a.optInt("id", -1)
                val sym = a.optString("symbol").trim().uppercase()
                var n = 0
                val out = StringBuilder()
                for (i in pos.length() - 1 downTo 0) {
                    val p = pos.getJSONObject(i)
                    if ((id >= 0 && p.getInt("id") == id) || (id < 0 && sym.isNotEmpty() && p.getString("sym") == sym) || (id < 0 && sym.isEmpty())) {
                        out.append(closeOne(j, p, price(p.getString("sym")), "manual")).append("\n"); pos.remove(i); n++
                    }
                }
                save(j)
                return if (n == 0) "koi open paper position nahi mili" else out.toString().trim()
            }
            "check", "status" -> {
                val out = StringBuilder()
                var unreal = 0.0
                for (i in pos.length() - 1 downTo 0) {
                    val p = pos.getJSONObject(i)
                    val cur = try { price(p.getString("sym")) } catch (e: Exception) { continue }
                    val d = p.getInt("dir")
                    val sl = if (p.isNull("sl")) Double.NaN else p.getDouble("sl")
                    val tp = if (p.isNull("tp")) Double.NaN else p.getDouble("tp")
                    val hitSl = !sl.isNaN() && (if (d > 0) cur <= sl else cur >= sl)
                    val hitTp = !tp.isNaN() && (if (d > 0) cur >= tp else cur <= tp)
                    if (hitSl || hitTp) {
                        out.append(closeOne(j, p, if (hitSl) sl else tp, if (hitSl) "SL hit" else "TP hit")).append("\n"); pos.remove(i)
                    } else {
                        val u = (cur - p.getDouble("entry")) * d * p.getDouble("qty"); unreal += u
                        out.append("open #").append(p.getInt("id")).append(" ").append(p.getString("sym")).append(if (d > 0) " BUY" else " SELL").append(" @").append(f(p.getDouble("entry")))
                            .append(" abhi ").append(f(cur)).append(" P/L ").append(String.format("%.2f", u)).append("\n")
                    }
                }
                save(j)
                val closed = j.getJSONArray("closed")
                var wins = 0
                for (i in 0 until closed.length()) if (closed.getJSONObject(i).getDouble("pnl") > 0) wins++
                val eq = j.getDouble("start") + j.getDouble("realized") + unreal
                out.append("Equity ").append(String.format("%.2f", eq)).append(" (start ").append(String.format("%.0f", j.getDouble("start"))).append("), realized ")
                    .append(String.format("%.2f", j.getDouble("realized"))).append(", closed trades ").append(closed.length())
                if (closed.length() > 0) out.append(", win ").append(String.format("%.0f", wins * 100.0 / closed.length())).append("%")
                out.append("\n(nakli trading - asli paisa nahi)")
                return out.toString()
            }
        }
        return "ERROR: paper do= open|close|status|check|reset"
    }

    private fun closeOne(j: JSONObject, p: JSONObject, exit: Double, why: String): String {
        val d = p.getInt("dir")
        val qty = p.getDouble("qty")
        val entry = p.getDouble("entry")
        val pnl = (exit - entry) * d * qty - (entry + exit) * qty * 0.001
        j.put("realized", j.getDouble("realized") + pnl)
        val closed = j.getJSONArray("closed")
        closed.put(JSONObject().put("sym", p.getString("sym")).put("pnl", pnl))
        while (closed.length() > 200) closed.remove(0)
        return "closed #" + p.getInt("id") + " " + p.getString("sym") + " @" + f(exit) + " (" + why + ") P/L " + String.format("%.2f", pnl)
    }
}

/** TradingAgents-jaisa: bull vs bear bahas + risk manager ka faisla (2 AI call). */
object Debate {
    fun run(symbol: String, interval: String): String {
        val cs = MarketData.fetch(symbol, interval)
        val data = Ind.summarize(symbol, interval, cs) + "\n" + Candles.report(cs)
        val news = try { WebTools.search(symbol.uppercase() + " news today").take(1500) } catch (e: Exception) { "(news nahi mili)" }
        val r1 = Ai.ask(
            Hub.prefs,
            listOf(
                Msg("system", "Tum 2 analyst ho. Sirf diye gaye data se kaam karo, kuch invent mat karo. Hinglish me, chhota."),
                Msg("user", "DATA:\n$data\n\nNEWS:\n$news\n\nPehle BULL case (3 bullet), phir BEAR case (3 bullet), phir sentiment 1 line.")
            )
        )
        val r2 = Ai.ask(
            Hub.prefs,
            listOf(
                Msg("system", "Tum risk manager ho. Guarantee mat do. Hinglish me, chhota."),
                Msg("user", "Bahas:\n" + r1.take(1500) + "\n\nCapital " + Hub.prefs.capital.toInt() + ", risk " + Hub.prefs.riskPct + "% per trade. Faisla: WAIT / BUY-plan / SELL-plan, entry-SL-target zone, aur 2 line me kyun. Ye advice nahi, research hai - ye line likho.")
            )
        )
        return "BULL vs BEAR:\n" + r1.take(1400) + "\n\nRISK MANAGER:\n" + r2.take(900)
    }
}
