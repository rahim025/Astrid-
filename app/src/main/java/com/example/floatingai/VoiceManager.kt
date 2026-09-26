package com.example.floatingai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Gère la voix de Jarvis pour toute la durée du service de la bulle :
 * - écoute (SpeechRecognizer) pour transformer la voix de Rahim en texte
 * - synthèse (TextToSpeech) pour que Jarvis réponde à voix haute
 *
 * Nécessite la permission RECORD_AUDIO (demandée depuis MainActivity).
 */
class VoiceManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun init() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                ttsReady = true
            }
        }
    }

    fun isListeningAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onStart: () -> Unit = {},
        onEnd: () -> Unit = {}
    ) {
        if (!isListeningAvailable()) {
            onError("Reconnaissance vocale indisponible sur cet appareil.")
            return
        }
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onStart() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onEnd() }
            override fun onError(error: Int) {
                onEnd()
                onError(speechErrorMessage(error))
            }
            override fun onResults(results: Bundle?) {
                onEnd()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) onResult(text) else onError("Je n'ai rien entendu.")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.let {
            try { it.destroy() } catch (_: Exception) {}
        }
        speechRecognizer = null
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Je n'ai pas compris, réessaie."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune voix détectée."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Autorisation micro manquante."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Connexion réseau requise pour la reconnaissance vocale."
        else -> "Erreur de reconnaissance vocale."
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
