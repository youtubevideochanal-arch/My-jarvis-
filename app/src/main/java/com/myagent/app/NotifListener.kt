package com.myagent.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Aane wali notifications padhne ke liye (WhatsApp/Instagram/bank SMS jaisi). Sirf RAM me, 40 tak. */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        val ex = n.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        if (n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        synchronized(items) {
            items.add(Item(System.currentTimeMillis(), n.packageName, title.take(60), text.take(160)))
            while (items.size > 40) items.removeAt(0)
        }
    }

    class Item(val t: Long, val pkg: String, val title: String, val text: String)

    companion object {
        @Volatile var connected = false
        private val items = ArrayList<Item>()

        fun enabled(ctx: Context): Boolean {
            val s = try { Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") } catch (e: Exception) { null } ?: return false
            return s.contains(ComponentName(ctx, NotifListener::class.java).flattenToString())
        }

        fun dump(ctx: Context, filter: String, n: Int): String {
            if (!enabled(ctx)) return "ERROR: Notification access OFF. Permissions screen -> 'Notification padhna' ON karo (user ko batao)."
            val f = filter.trim().lowercase()
            val pm = ctx.packageManager
            val list = synchronized(items) { items.toList() }.asReversed()
                .filter { f.isEmpty() || it.pkg.lowercase().contains(f) || it.title.lowercase().contains(f) || it.text.lowercase().contains(f) }
                .take(n.coerceIn(1, 25))
            if (list.isEmpty()) return "(koi notification nahi" + (if (connected) "" else " - listener abhi connect nahi, OFF->ON karo") + ")"
            val tf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            return list.joinToString("\n") {
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(it.pkg, 0)).toString() } catch (e: Exception) { it.pkg }
                tf.format(java.util.Date(it.t)) + " " + label + ": " + it.title + " - " + it.text
            }
        }
    }
}
