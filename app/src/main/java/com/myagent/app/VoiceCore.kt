package com.myagent.app

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent

/** Naam pehchanan: "doremon", "doraemon", "डोरेमोन", "dora mon" sab ek hi maane jaate hain. */
object NameMatch {
    private val dev = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ट' to "t", 'ठ' to "th",
        'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "f",
        'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ं' to "n", 'ँ' to "n", '़' to ""
    )

    /** Roman me badal ke, vowels hata kar "dhwani-kunji" banao (doremon/doraemon/dorimon -> drmn). */
    fun key(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val m = dev[ch]
            if (m != null) sb.append(m) else if (ch in 'a'..'z' || ch in '0'..'9') sb.append(ch)
        }
        return sb.toString().replace(Regex("[aeiouy]"), "").replace(Regex("(.)\\1+"), "$1")
    }

    private fun lev(a: String, b: String): Int {
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

    /** Agar sunne wale text me naam hai to naam hata kar baaki text lautao, warna null. */
    fun strip(heard: String, name: String): String? {
        val words = heard.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val nk = key(name)
        if (nk.isEmpty() || words.isEmpty()) return null
        val nameWords = name.trim().split(Regex("\\s+")).size
        for (span in listOf(nameWords, nameWords + 1, 1, 2)) {
            if (span < 1 || span > words.size) continue
            for (i in 0..words.size - span) {
                val k = key(words.subList(i, i + span).joinToString(""))
                if (k.isEmpty()) continue
                val tol = if (nk.length >= 5) 1 else 0
                if (k == nk || lev(k, nk) <= tol) {
                    val rest = words.take(i) + words.drop(i + span)
                    return rest.joinToString(" ").trim().trim(',', '.', '!', '?', ' ')
                }
            }
        }
        return null
    }
}

/** Awaaz ke character: pitch/speed ke preset. Asli ladka/ladki awaaz phone me installed voices par nirbhar hai. */
data class Character(val name: String, val pitch: Float, val rate: Float, val gender: Int, val demo: String)

object Characters {
    // gender: 0 = koi bhi, 1 = purush jaisi, 2 = stri jaisi
    val all: List<Character> = listOf(
        Character("Default (meri awaaz)", 1.0f, 1.0f, 0, "Namaste, main aapka assistant hoon."),
        Character("Jarvis - gehri, shaant", 0.78f, 0.95f, 1, "Good evening sir. Sab systems taiyaar hain."),
        Character("Friday - saaf, tez ladki", 1.12f, 1.08f, 2, "Boss, aapka kaam ho gaya."),
        Character("Chhota bachcha", 1.65f, 1.08f, 0, "Main bhi tumhari madad karunga!"),
        Character("Robot", 0.55f, 0.85f, 1, "Command accepted. Executing task."),
        Character("Cartoon - hasmukh", 1.5f, 1.25f, 0, "Hehe! Chalo kaam shuru karte hain!"),
        Character("News anchor", 0.95f, 1.12f, 0, "Ab dekhiye aaj ki sabse badi khabar."),
        Character("Dada ji - dheema, bhaari", 0.62f, 0.78f, 1, "Beta, aaram se karo, jaldi kis baat ki."),
        Character("Dadi ma - narm", 0.95f, 0.8f, 2, "Beta, khana khaya ki nahi?"),
        Character("Sports commentator", 1.0f, 1.35f, 0, "Aur yeh gaya chhakka!")
    )

    fun current(): Character = all[Hub.prefs.character.coerceIn(0, all.size - 1)]
}

/** Mic: SpeechRecognizer ko sambhaalta hai, har error ko saaf Hindi text me batata hai, fallback deta hai. */
class VoiceIn(private val ctx: Context) {
    interface CB {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onState(text: String)
        /** needFallback = true: system voice dialog (RecognizerIntent) try karo. */
        fun onFail(msg: String, needFallback: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var cb: CB? = null
    @Volatile var listening = false
        private set
    private var lang = Hub.prefs.lang
    private var retries = 0
    private var muted = false
    private var beepVol = -1

    fun available(): Boolean = try { SpeechRecognizer.isRecognitionAvailable(ctx) } catch (e: Exception) { false }

    private fun muteBeep(on: Boolean) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (on && !muted) {
                beepVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                muted = true
            } else if (!on && muted) {
                if (beepVol >= 0) am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, beepVol, 0)
                muted = false
            }
        } catch (e: Exception) { muted = false }
    }

    fun start(callback: CB) {
        main.post {
            cb = callback
            retries = 0
            lang = Hub.prefs.lang
            begin()
        }
    }

    private fun begin() {
        destroy()
        if (!available()) {
            cb?.onFail("Is phone me speech-recognition service nahi mili.", true)
            return
        }
        val r = try { SpeechRecognizer.createSpeechRecognizer(ctx) } catch (e: Exception) { null }
        if (r == null) { cb?.onFail("Mic service start nahi hui.", true); return }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { cb?.onState("🎙 boliye...") }
            override fun onBeginningOfSpeech() { cb?.onState("👂 sun raha hoon...") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { cb?.onState("⏳ samajh raha hoon...") }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val l = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (l != null && l.isNotEmpty()) cb?.onPartial(l[0])
            }

            override fun onResults(results: Bundle?) {
                listening = false
                muteBeep(false)
                val l = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (l != null && l.isNotEmpty() && l[0].isNotBlank()) cb?.onFinal(l[0])
                else cb?.onFail("Kuch samajh nahi aaya, dobara bolo.", false)
            }

            override fun onError(error: Int) {
                listening = false
                muteBeep(false)
                handleError(error)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        try {
            muteBeep(true)
            r.startListening(i)
            listening = true
            cb?.onState("🎙 mic ON...")
        } catch (e: Exception) {
            muteBeep(false)
            cb?.onFail("Mic start nahi hua: " + (e.message ?: ""), true)
        }
    }

    private fun handleError(code: Int) {
        when (code) {
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH ->
                cb?.onFail("Awaaz nahi suni. Mic ke paas thoda zor se bolo, phir mic dabao.", false)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                if (retries++ < 2) main.postDelayed({ begin() }, 700) else cb?.onFail("Mic busy hai, kuch second baad try karo.", false)
            }
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                if (lang != "en-IN") { lang = "en-IN"; cb?.onState("🌐 Hindi nahi mili, English-India try..."); main.postDelayed({ begin() }, 300) }
                else cb?.onFail("Bhasha support nahi. Phone ki Google voice settings me language download karo.", true)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                cb?.onFail("Mic ki permission nahi hai. Permissions screen me Microphone allow karo.", false)
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                cb?.onFail("Speech service tak internet nahi pahunch raha (error $code). Net check karo; ya system voice dialog try karta hoon.", true)
            SpeechRecognizer.ERROR_AUDIO ->
                cb?.onFail("Mic audio error. Koi aur app mic use kar rahi ho to band karo.", false)
            else ->
                if (retries++ < 1) main.postDelayed({ begin() }, 500)
                else cb?.onFail("Mic error ($code). System voice dialog try karta hoon.", true)
        }
    }

    fun stop() {
        main.post {
            try { rec?.stopListening() } catch (e: Exception) { }
            listening = false
            muteBeep(false)
        }
    }

    fun destroy() {
        try { rec?.cancel() } catch (e: Exception) { }
        try { rec?.destroy() } catch (e: Exception) { }
        rec = null
        listening = false
        muteBeep(false)
    }
}
