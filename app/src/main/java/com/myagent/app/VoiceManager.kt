package com.myagent.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Bolne wali awaaz (TTS). Sunne wala hissa MainActivity me hai. */
class VoiceManager(ctx: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val main = Handler(Looper.getMainLooper())

    /** Bolna khatam hone par (agar auto-listen ON ho to MainActivity mic chalu karega). */
    @Volatile var onSpeakDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx.applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post { onSpeakDone?.invoke() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { onSpeakDone?.invoke() } }
            })
            applyConfig()
        }
    }

    fun applyConfig() {
        if (!ready) return
        tts?.setLanguage(Locale.forLanguageTag(prefs.lang))
        val ch = Characters.current()
        var pitch = prefs.pitch * ch.pitch
        var rate = prefs.rate * ch.rate
        val name = prefs.voiceName
        if (name.isNotBlank()) {
            val v = try { tts?.voices?.firstOrNull { it.name == name } } catch (e: Exception) { null }
            if (v != null) tts?.setVoice(v)
        }
        tts?.setSpeechRate(rate.coerceIn(0.3f, 3f))
        tts?.setPitch(pitch.coerceIn(0.3f, 2f))
    }

    fun availableVoices(): List<String> {
        val t = tts
        if (!ready || t == null) return emptyList()
        val lang = prefs.lang.substringBefore('-')
        return try {
            t.voices.filter { it.locale.language == lang }.map { it.name }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!ready) return
        if (Hub.inCall()) return
        if (!force && !prefs.ttsEnabled) return
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}*_#`]"), " ").trim()
        if (clean.isEmpty()) return
        applyConfig()
        val cap = if (prefs.tokenSaver) 700 else 1500
        tts?.speak(clean.take(cap), TextToSpeech.QUEUE_FLUSH, null, "myagent")
    }

    /** Character preview: abhi ka character laga kar demo bolo (TTS ON na ho tab bhi). */
    fun speakDemo(text: String) {
        speak(text, true)
    }

    fun isSpeaking(): Boolean = try { tts?.isSpeaking == true } catch (e: Exception) { false }

    fun stop() {
        tts?.stop()
    }
}
