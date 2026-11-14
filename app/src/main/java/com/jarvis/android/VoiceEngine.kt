
package com.jarvis.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * JARVIS Voice Engine
 *
 * Handles Android text-to-speech.
 * Provides voice discovery, voice selection and speech controls.
 */
class VoiceEngine(
    context: Context,
    private val onReady: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private var selectedVoice: Voice? = null

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true

            textToSpeech?.language = Locale.getDefault()

            onReady?.invoke()
        }
    }

    /**
     * Speak a message using the selected voice.
     */
    fun speak(message: String) {
        if (!isInitialized || message.isBlank()) return

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JARVIS_UTTERANCE"
        )
    }

    /**
     * Get voices installed on this Android device.
     */
    fun getAvailableVoices(): List<Voice> {
        if (!isInitialized) return emptyList()

        return textToSpeech?.voices
            ?.filter { it.locale.language == "en" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Select an installed voice by its exact name.
     */
    fun selectVoice(voiceName: String): Boolean {
        if (!isInitialized) return false

        val voice = getAvailableVoices()
            .firstOrNull { it.name == voiceName }
            ?: return false

        selectedVoice = voice
        textToSpeech?.voice = voice

        return true
    }

    /**
     * Preview a selected voice.
     */
    fun previewVoice(voiceName: String) {
        if (selectVoice(voiceName)) {
            speak("Hello. I am JARVIS. Voice system is ready.")
        }
    }

    /**
     * Pause ongoing speech.
     */
    fun stop() {
        textToSpeech?.stop()
    }

    /**
     * Release the TTS engine when no longer needed.
     */
    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech = null
        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized
}
