package com.myagent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** App band/background me bhi chalta rahe isliye foreground service. */
class AgentService : Service() {

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Agent", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val panel = PendingIntent.getActivity(
            this, 1, Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setContentTitle(Hub.prefs.appName + " ON hai")
            .setContentText(if (Hub.prefs.wakeAlways) "Naam bolo - main sun raha hoon" else "Tap karo ya 🎙 Bolo dabao")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentIntent(pi)
            .addAction(0, "🎙 Bolo", panel)
            .setOngoing(true)
            .build()
        val micOk = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        var started = false
        if (Build.VERSION.SDK_INT >= 30 && Hub.prefs.wakeAlways && micOk) {
            try {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                started = true
            } catch (e: Exception) {
                started = false
            }
        }
        if (!started) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        }
        wakeMicOk = started
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Hub.prefs.masterOn) Hub.engine.resumeIfNeeded()
        if (Hub.prefs.wakeAlways && Hub.prefs.masterOn) WakeListener.start(this) else WakeListener.stop()
        if (Hub.prefs.masterOn) Telegram.start() else Telegram.stop()
        if (Hub.prefs.masterOn) { PhoneAccessibilityService.selfEnable(this); watchdog() }
        return START_STICKY
    }

    private var dogOn = false
    /** Har minute: Accessibility gir gayi ho to khud jaga do (agar adb permission di hai). */
    private fun watchdog() {
        if (dogOn) return
        dogOn = true
        val h = Hub.mainHandler
        h.postDelayed(object : Runnable {
            override fun run() {
                if (!Hub.prefs.masterOn) { dogOn = false; return }
                if (PhoneAccessibilityService.instance == null) PhoneAccessibilityService.selfEnable(this@AgentService)
                h.postDelayed(this, 60000)
            }
        }, 20000)
    }

    override fun onDestroy() {
        dogOn = false
        WakeListener.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CH = "my_agent_channel"
        private const val NOTIF_ID = 77
        @Volatile var wakeMicOk = false
    }
}
