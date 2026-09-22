package com.example.floatingai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.util.concurrent.Executors

/**
 * Fait tourner un petit modèle de langage directement sur le téléphone, via
 * MediaPipe LLM Inference (LiteRT). Aucune requête réseau n'est jamais faite ici :
 * une fois le fichier .task importé, "Astrid" répond même en mode avion.
 *
 * L'utilisateur doit importer un modèle compatible (ex. Gemma 3 1B/2B "it" au format
 * .task, téléchargé une seule fois — voir le README) via le bouton dédié dans l'appli.
 * Ce fichier est ensuite stocké localement dans le stockage interne de l'appli.
 */
object LocalLlmClient {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var engine: LlmInference? = null
    @Volatile private var loadedModelPath: String? = null

    fun modelFile(context: Context): File = File(context.filesDir, "astrid_local_model.task")

    fun hasLocalModel(context: Context): Boolean = modelFile(context).exists()

    fun sendMessage(
        context: Context,
        history: List<Pair<String, String>>,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val appContext = context.applicationContext
        val modelPath = modelFile(appContext)

        if (!modelPath.exists()) {
            onError(
                "Aucun modèle hors ligne importé. Ouvre l'appli, onglet « Hors ligne », " +
                    "et importe un fichier modèle .task pour qu'Astrid puisse répondre sans internet."
            )
            return
        }

        executor.execute {
            try {
                val llm = getOrCreateEngine(appContext, modelPath.absolutePath)
                val prompt = buildPrompt(history)
                val response = llm.generateResponse(prompt)
                mainHandler.post { onResult(response.ifBlank { "(réponse vide)" }) }
            } catch (e: Exception) {
                mainHandler.post {
                    onError("Erreur du modèle local : ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    @Synchronized
    private fun getOrCreateEngine(context: Context, modelPath: String): LlmInference {
        val current = engine
        if (current != null && loadedModelPath == modelPath) return current

        current?.close()

        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setTopK(40)
            .setTemperature(0.7f)
            .build()

        val created = LlmInference.createFromOptions(context, options)
        engine = created
        loadedModelPath = modelPath
        return created
    }

    /**
     * Les petits modèles embarqués (Gemma "it" etc.) n'ont en général pas de rôle
     * "system" dédié : on injecte donc l'identité d'Astrid au début de l'historique,
     * puis on formate la conversation au format de tour Gemma.
     */
    private fun buildPrompt(history: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>user\n")
        sb.append(Identity.SYSTEM_PROMPT)
        sb.append("\n<end_of_turn>\n")
        sb.append("<start_of_turn>model\nCompris, je suis Astrid.<end_of_turn>\n")

        for ((role, content) in history) {
            val turnRole = if (role == "assistant") "model" else "user"
            sb.append("<start_of_turn>").append(turnRole).append("\n")
            sb.append(content)
            sb.append("\n<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    /** Libère le modèle de la mémoire (par ex. si l'utilisateur en importe un autre). */
    @Synchronized
    fun unload() {
        engine?.close()
        engine = null
        loadedModelPath = null
    }
}
