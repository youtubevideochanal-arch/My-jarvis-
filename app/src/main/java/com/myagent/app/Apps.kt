package com.myagent.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class AppInfo(val label: String, val pkg: String, val system: Boolean)

/** Phone ki SAARI installed (launcher wali) apps: user ki downloaded + system dono. */
object Apps {
    @Volatile private var cache: List<AppInfo>? = null
    @Volatile private var cacheT = 0L

    fun all(ctx: Context, refresh: Boolean = false): List<AppInfo> {
        val c = cache
        if (!refresh && c != null && System.currentTimeMillis() - cacheT < 60_000) return c
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = try { pm.queryIntentActivities(main, PackageManager.MATCH_ALL) } catch (e: Exception) { pm.queryIntentActivities(main, 0) }
        val out = LinkedHashMap<String, AppInfo>()
        for (ri in list) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || out.containsKey(pkg)) continue
            val sys = (ri.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            out[pkg] = AppInfo(ri.loadLabel(pm).toString(), pkg, sys)
        }
        val r = out.values.sortedBy { it.label.lowercase() }
        cache = r
        cacheT = System.currentTimeMillis()
        return r
    }

    fun norm(s: String): String = s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")

    /** nickname list: "mw=com.x.marketwolf;insta=com.instagram.android" */
    fun nickMap(): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (part in Hub.prefs.nicknames.split(';', '\n')) {
            val kv = part.split('=', limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank() && kv[1].isNotBlank()) m[norm(kv[0])] = kv[1].trim()
        }
        return m
    }

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val t = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return dp[b.length]
    }

    fun find(ctx: Context, q: String): AppInfo? {
        val n = norm(q)
        if (n.isEmpty()) return null
        val apps = all(ctx)
        nickMap()[n]?.let { target -> apps.firstOrNull { it.pkg == target || norm(it.label) == norm(target) }?.let { return it } }
        apps.firstOrNull { norm(it.label) == n }?.let { return it }
        apps.firstOrNull { it.pkg.lowercase() == q.trim().lowercase() }?.let { return it }
        // user ki downloaded apps ko system se pehle
        val cont = apps.filter { norm(it.label).contains(n) || (n.length >= 4 && n.contains(norm(it.label)) && norm(it.label).length >= 4) }
        cont.sortedBy { it.system }.firstOrNull()?.let { return it }
        apps.firstOrNull { it.pkg.lowercase().contains(n) }?.let { return it }
        if (n.length >= 4) {
            val best = apps.minByOrNull { lev(norm(it.label), n) }
            if (best != null && lev(norm(best.label), n) <= (if (n.length >= 7) 2 else 1)) return best
        }
        return null
    }

    fun launch(ctx: Context, name: String): String {
        if (name.isBlank()) return "ERROR: app ka naam khaali"
        val app = find(ctx, name)
        if (app == null) {
            val similar = search(ctx, name.take(4), 6)
            // Play Store me dhundho
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(name))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e: Exception) { }
            return "ERROR: '$name' naam ki app installed nahi mili (Play Store khola hai). Milti-julti: $similar"
        }
        val li = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return "ERROR: ${app.label} launch nahi ho sakti"
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(li)
            "opened " + app.label + " (" + app.pkg + ")"
        } catch (e: Exception) {
            "ERROR: app nahi khuli: " + (e.message ?: "")
        }
    }

    fun search(ctx: Context, q: String, max: Int = 15): String {
        val n = norm(q)
        val apps = all(ctx)
        val hit = if (n.isEmpty()) apps else apps.filter { norm(it.label).contains(n) || it.pkg.lowercase().contains(n) }
        if (hit.isEmpty()) return "(koi nahi)"
        return hit.take(max).joinToString(", ") { it.label + (if (it.system) "" else " *") }
    }

    fun listText(ctx: Context, filter: String, userOnly: Boolean): String {
        val apps = all(ctx).filter { !userOnly || !it.system }
        val n = norm(filter)
        val hit = if (n.isEmpty()) apps else apps.filter { norm(it.label).contains(n) || it.pkg.contains(filter.trim().lowercase()) }
        return "Installed apps (" + hit.size + "): " + hit.take(80).joinToString("; ") { it.label + "=" + it.pkg }
    }

    fun labelOf(ctx: Context, pkg: String): String = all(ctx).firstOrNull { it.pkg == pkg }?.label ?: pkg

    fun selected(csv: String): MutableSet<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
}
