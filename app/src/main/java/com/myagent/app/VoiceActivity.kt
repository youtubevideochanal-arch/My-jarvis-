package com.myagent.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * Bina screen wala chhota activity: system ka voice dialog (Google app) kholta hai aur jo suna use agent ko deta hai.
 * Tile, panel aur mic fallback sab yahi use karte hain.
 */
class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Hub.prefs.lang)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, Hub.prefs.appName + " sun raha hai...")
        try {
            startActivityForResult(i, 77)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Voice dialog nahi mila. Google app / Speech Services install karo, ya type karo.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val l = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (l != null && l.isNotEmpty()) Hub.onHeard(l[0], true)
        }
        finish()
    }
}
