package com.hakdaar.app.platform.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class AndroidTextToSpeech(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    var isEnabled: Boolean = true
    var speechRate: Float = 1.0f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setSpeechRate(speechRate)
        }
    }

    fun speak(text: String, languageCode: String = "hi-IN") {
        if (!isEnabled || !isInitialized) return

        val locale = if (languageCode.startsWith("hi", ignoreCase = true)) {
            Locale("hi", "IN")
        } else {
            Locale("en", "IN")
        }

        tts?.language = locale
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HAKDAAR_TTS_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
