package com.example.floatingai

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun sendMessage(
        context: Context,
        history: List<Pair<String, String>>, // role to content
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val provider = Prefs.getProvider(context)

        // Mode hors ligne : tout se passe sur l'appareil, aucun réseau requis.
        if (provider == "offline") {
            LocalLlmClient.sendMessage(context, history, onResult, onError)
            return
        }

        val apiKey = Prefs.getApiKey(context, provider)
        if (apiKey.isBlank()) {
            onError("Aucune clé API configurée pour $provider. Ouvre l'appli et ajoute ta clé dans les paramètres.")
            return
        }

        val request = when (provider) {
            "anthropic" -> buildAnthropicRequest(context, apiKey, history)
            "openai" -> buildOpenAiRequest(context, apiKey, history)
            "groq" -> buildGroqRequest(context, apiKey, history)
            else -> buildGeminiRequest(context, apiKey, history)
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Erreur réseau : ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    if (!it.isSuccessful) {
                        onError("Erreur API (${it.code}): $body")
                        return
                    }
                    try {
                        val text = when (provider) {
                            "anthropic" -> parseAnthropicResponse(body)
                            "openai" -> parseOpenAiResponse(body)
                            "groq" -> parseOpenAiResponse(body)
                            else -> parseGeminiResponse(body)
                        }
                        onResult(text)
                    } catch (e: Exception) {
                        onError("Erreur de lecture de la réponse: ${e.message}")
                    }
                }
            }
        })
    }

    private fun buildAnthropicRequest(
        context: Context,
        apiKey: String,
        history: List<Pair<String, String>>
    ): Request {
        val model = Prefs.getModel(context, "anthropic")
        val messages = JSONArray()
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("system", Identity.SYSTEM_PROMPT)
            .put("messages", messages)

        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
    }

    private fun parseAnthropicResponse(body: String): String {
        val json = JSONObject(body)
        val content = json.getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") {
                sb.append(block.optString("text"))
            }
        }
        return sb.toString().ifBlank { "(réponse vide)" }
    }

    private fun buildOpenAiRequest(
        context: Context,
        apiKey: String,
        history: List<Pair<String, String>>
    ): Request {
        val model = Prefs.getModel(context, "openai")
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", Identity.SYSTEM_PROMPT))
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)

        return Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
    }

    private fun parseOpenAiResponse(body: String): String {
        val json = JSONObject(body)
        val choices = json.getJSONArray("choices")
        val message = choices.getJSONObject(0).getJSONObject("message")
        return message.optString("content").ifBlank { "(réponse vide)" }
    }

    private fun buildGroqRequest(
        context: Context,
        apiKey: String,
        history: List<Pair<String, String>>
    ): Request {
        val model = Prefs.getModel(context, "groq")
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", Identity.SYSTEM_PROMPT))
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)

        return Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
    }

    private fun buildGeminiRequest(
        context: Context,
        apiKey: String,
        history: List<Pair<String, String>>
    ): Request {
        val model = Prefs.getModel(context, "gemini")
        val contents = JSONArray()
        for ((role, content) in history) {
            val geminiRole = if (role == "assistant") "model" else "user"
            val parts = JSONArray().put(JSONObject().put("text", content))
            contents.put(JSONObject().put("role", geminiRole).put("parts", parts))
        }
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text", Identity.SYSTEM_PROMPT)))
        val payload = JSONObject()
            .put("system_instruction", systemInstruction)
            .put("contents", contents)

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        return Request.Builder()
            .url(url)
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
    }

    private fun parseGeminiResponse(body: String): String {
        val json = JSONObject(body)
        val candidates = json.getJSONArray("candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        return sb.toString().ifBlank { "(réponse vide)" }
    }
}
