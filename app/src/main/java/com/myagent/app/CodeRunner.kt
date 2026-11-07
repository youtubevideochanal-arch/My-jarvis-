package com.myagent.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Code Termux me chalata hai (python/node/bash), output + error + exit code wapas laata hai,
 * taaki agent khud bug dekh kar fix kare. Termux me ek baar: allow-external-apps=true.
 */
object CodeRunner {
    private class Res(val out: String, val err: String, val exit: Int, val sysErr: String)

    private val latches = ConcurrentHashMap<Int, CountDownLatch>()
    private val results = ConcurrentHashMap<Int, Res>()
    private val counter = AtomicInteger(4000)

    fun deliver(id: Int, out: String, err: String, exit: Int, sysErr: String) {
        results[id] = Res(out, err, exit, sysErr)
        latches[id]?.countDown()
    }

    fun run(ctx: Context, lang: String, file: String, code: String): String {
        try {
            ctx.packageManager.getPackageInfo("com.termux", 0)
        } catch (e: Exception) {
            return "ERROR: python/node/bash ke liye Termux chahiye (install nahi hai). JavaScript aur HTML app ke andar hi chalte hain: lang=js ya html use karo."
        }
        if (code.isBlank()) return "ERROR: code khaali hai"
        val runner = when (lang.trim().lowercase()) {
            "python", "py", "python3" -> "python"
            "node", "js", "javascript" -> "node"
            "bash", "sh", "shell" -> "bash"
            else -> return "ERROR: lang sirf python | node | bash"
        }
        val name = file.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "main.txt" }
        val script = "mkdir -p \"\$HOME/agent_work\" && cd \"\$HOME/agent_work\" && printf '%s' \"\$1\" > \"\$2\" && timeout 60 $runner \"\$2\""

        val id = counter.incrementAndGet()
        val latch = CountDownLatch(1)
        latches[id] = latch

        val i = Intent("com.termux.RUN_COMMAND")
        i.setClassName("com.termux", "com.termux.app.RunCommandService")
        i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script, "_", code, name))
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        val ri = Intent(ctx, TermuxResultReceiver::class.java)
        ri.putExtra("exec_id", id)
        val pi = PendingIntent.getBroadcast(ctx, id, ri, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)

        try {
            ctx.startForegroundService(i)
        } catch (e: Exception) {
            latches.remove(id)
            return "ERROR: Termux ko command nahi bhej paya (" + e.javaClass.simpleName +
                "). Permissions screen ki row 6 dekho aur Termux me: echo 'allow-external-apps=true' >> ~/.termux/termux.properties"
        }
        val ok = latch.await(80, TimeUnit.SECONDS)
        latches.remove(id)
        val r = results.remove(id) ?: return if (ok) "ERROR: result nahi mila" else "ERROR: timeout (80s) - Termux me python/node install hai?"
        val sys = if (r.sysErr.isNotBlank()) "\nTERMUX: " + r.sysErr else ""
        return "exit=" + r.exit + sys + "\nSTDOUT:\n" + r.out.take(2000) + "\nSTDERR:\n" + r.err.take(1500)
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("exec_id", -1)
        val b = intent.getBundleExtra("result")
        val out = b?.getString("stdout") ?: ""
        val err = b?.getString("stderr") ?: ""
        val exit = b?.getInt("exitCode", -1) ?: -1
        val sys = b?.getString("errmsg") ?: ""
        CodeRunner.deliver(id, out, err, exit, sys)
    }
}
