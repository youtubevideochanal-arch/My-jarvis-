package com.myagent.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Base64
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Speech output: Android TTS per "character", with a different installed voice + pitch/speed per character. */
object Voice {
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false
    private val pending = ArrayList<Pair<String, Persona>>()
    private var player: MediaPlayer? = null

    @Synchronized
    fun setup(c: Context) {
        if (tts != null) return
        tts = TextToSpeech(c.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) flush()
        }
    }

    @Synchronized
    private fun flush() {
        val t = tts ?: return
        for ((text, ch) in pending) say(t, text, ch)
        pending.clear()
    }

    fun voiceNames(): List<String> {
        val t = tts ?: return emptyList()
        return try {
            (t.voices ?: emptySet()).filter { it.locale.language == "hi" || it.locale.language == "en" }
                .map { it.name }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun speak(text: String) {
        val c = App.ctx
        if (!Prefs.speak(c)) return
        speakWith(Prefs.activeCharacter(c), text)
    }

    val OAI_VOICES = listOf("alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer")
    val GEM_VOICES = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr", "Callirrhoe", "Autonoe",
        "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi",
        "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird",
        "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )

    /** Real AI voice via the user's ChatGPT/Gemini key when present; otherwise the phone's own voice. */
    fun speakWith(ch: Persona, text: String) {
        val c = App.ctx
        val clean = text.replace(Regex("[*_`#]"), "").take(450)
        if (clean.isBlank()) return
        val providers = Prefs.llmOrder(c).filter { it == "OPENAI" || it == "GEMINI" }
        if (providers.isEmpty()) {
            local(clean, ch)
            return
        }
        Thread {
            var played = false
            for (p in providers) {
                try {
                    val r = if (p == "OPENAI") openaiTts(c, clean, ch) else geminiTts(c, clean, ch)
                    if (r != null) {
                        play(c, r.first, r.second)
                        played = true
                        break
                    }
                } catch (e: Exception) {
                }
            }
            if (!played) local(clean, ch)
        }.start()
    }

    private fun openaiTts(c: Context, text: String, ch: Persona): Pair<ByteArray, String>? {
        val key = Prefs.key(c, "OPENAI")
        for (model in listOf("gpt-4o-mini-tts", "tts-1")) {
            val o = JSONObject().put("model", model).put("input", text).put("voice", ch.oai)
                .put("response_format", "mp3")
            if (model == "gpt-4o-mini-tts" && ch.style.isNotBlank()) o.put("instructions", "Speak as: " + ch.style)
            val r = Http.request(
                "POST", "https://api.openai.com/v1/audio/speech",
                mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"),
                o.toString(), 60000, false, 5_000_000
            )
            if (r.code == 200 && r.bytes.isNotEmpty()) return Pair(r.bytes, "mp3")
        }
        return null
    }

    private fun geminiTts(c: Context, text: String, ch: Persona): Pair<ByteArray, String>? {
        val key = Prefs.key(c, "GEMINI")
        var model = Prefs.ttsModel(c)
        if (model.isBlank()) {
            val r = Http.request(
                "GET", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                mapOf("x-goog-api-key" to key), null, 30000
            )
            if (r.code != 200) return null
            val arr = JSONObject(r.body).getJSONArray("models")
            val names = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i).getString("name").removePrefix("models/")
                if (n.contains("tts")) names.add(n)
            }
            model = names.firstOrNull { it.contains("flash") } ?: names.firstOrNull() ?: return null
            Prefs.setTtsModel(c, model)
        }
        val prompt = if (ch.style.isNotBlank()) "Say in a ${ch.style} voice: $text" else text
        val body = JSONObject()
            .put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", org.json.JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put(
                                "prebuiltVoiceConfig", JSONObject().put("voiceName", ch.gem)
                            )
                        )
                    )
            )
        val r = Http.request(
            "POST", "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"),
            body.toString(), 60000, false, 8_000_000
        )
        if (r.code != 200) return null
        val parts = JSONObject(r.body).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val data = parts.optJSONObject(0)?.optJSONObject("inlineData")?.optString("data") ?: return null
        if (data.isEmpty()) return null
        return Pair(wav(Base64.decode(data, Base64.DEFAULT), 24000), "wav")
    }

    /** Wraps raw 16-bit mono PCM in a WAV header so MediaPlayer can play it. */
    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun le(v: Int, n: Int) {
            for (i in 0 until n) out.write((v shr (8 * i)) and 0xFF)
        }
        out.write("RIFF".toByteArray())
        le(36 + pcm.size, 4)
        out.write("WAVEfmt ".toByteArray())
        le(16, 4); le(1, 2); le(1, 2); le(rate, 4); le(rate * 2, 4); le(2, 2); le(16, 2)
        out.write("data".toByteArray())
        le(pcm.size, 4)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun play(c: Context, bytes: ByteArray, ext: String) {
        val f = File(c.cacheDir, "voice.$ext")
        f.writeBytes(bytes)
        Handler(Looper.getMainLooper()).post {
            try {
                tts?.stop()
                player?.release()
                val mp = MediaPlayer()
                mp.setDataSource(f.path)
                mp.setOnCompletionListener { it.release() }
                mp.prepare()
                mp.start()
                player = mp
            } catch (e: Exception) {
            }
        }
    }

    @Synchronized
    private fun local(text: String, ch: Persona) {
        setup(App.ctx)
        val t = tts
        if (t == null || !ready) {
            pending.add(Pair(text, ch))
            return
        }
        say(t, text, ch)
    }

    private fun say(t: TextToSpeech, text: String, ch: Persona) {
        try {
            t.setLanguage(Locale.forLanguageTag(ch.lang))
            val all: Set<android.speech.tts.Voice> = t.voices ?: emptySet()
            var v = if (ch.voice.isNotBlank()) all.firstOrNull { it.name == ch.voice } else null
            if (v == null) {
                // different characters get different installed voices (when the phone has several)
                val lang = Locale.forLanguageTag(ch.lang).language
                val pool = all.filter { it.locale.language == lang && !it.isNetworkConnectionRequired }
                    .sortedBy { it.name }
                if (pool.isNotEmpty()) v = pool[ch.slot % pool.size]
            }
            if (v != null) t.setVoice(v)
            t.setPitch(ch.pitch)
            t.setSpeechRate(ch.rate)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
        Handler(Looper.getMainLooper()).post {
            try {
                player?.stop()
                player?.release()
            } catch (e: Exception) {
            }
            player = null
        }
    }
}

/** Push-to-talk speech recognition. Must be created/used on the main thread. */
class VoiceInput(private val ctx: Context, private val cb: Callback) {
    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onDone(error: String?)
    }

    private var sr: SpeechRecognizer? = null
    private var last = ""
    private var finished = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            cb.onDone("Speech recognition is not available on this phone")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val l = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!l.isNullOrEmpty()) {
                    last = l[0]
                    cb.onPartial(last)
                }
            }

            override fun onResults(results: Bundle?) {
                val l = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val t = if (!l.isNullOrEmpty()) l[0] else last
                finish(t, null)
            }

            override fun onError(error: Int) {
                if (last.isNotBlank()) {
                    finish(last, null)
                } else {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice needs internet"
                        else -> "Voice error $error"
                    }
                    finish("", msg)
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.lang(ctx))
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        r.startListening(i)
    }

    /** Button released: stop listening; the recognizer then delivers the final text. */
    fun stop() {
        try {
            sr?.stopListening()
        } catch (e: Exception) {
        }
    }

    fun cancel() {
        finished = true
        release()
    }

    private fun finish(text: String, err: String?) {
        if (finished) return
        finished = true
        release()
        if (text.isNotBlank()) cb.onFinal(text.trim())
        cb.onDone(err)
    }

    private fun release() {
        try {
            sr?.destroy()
        } catch (e: Exception) {
        }
        sr = null
    }
}
