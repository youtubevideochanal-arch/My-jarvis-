package com.myagent.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Phone ka "Digital assistant app" bano: Settings -> Default apps -> Digital assistant -> is app ko chuno. Phir home long-press/swipe se panel khulega. */
class MyVoiceService : VoiceInteractionService()

class MyVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = MySession(this)
}

class MySession(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        MiniPanel.show(true)
        hide()
    }
}

/** Bina screen wala activity: panel dikhata hai aur turant band ho jata hai (tile/notification isi se panel kholte hain). */
class PanelActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MiniPanel.show(true)
        finish()
    }
}

/** Quick Settings tile: notification shade se ek tap me panel + mic. */
class AgentTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val t = qsTile ?: return
        t.state = if (Hub.prefs.masterOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = Hub.prefs.appName
        t.updateTile()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val i = Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(this, 5, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                startActivityAndCollapse(pi)
            } else {
                startActivityAndCollapse(i)
            }
        } catch (e: Exception) {
            MiniPanel.show(true)
        }
    }
}
