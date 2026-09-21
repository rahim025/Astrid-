package com.example.floatingai

import android.content.Context

object Prefs {
    private const val NAME = "floating_ai_prefs"

    fun get(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getProvider(context: Context): String =
        get(context).getString("provider", "gemini") ?: "gemini"

    fun setProvider(context: Context, provider: String) {
        get(context).edit().putString("provider", provider).apply()
    }

    fun getApiKey(context: Context, provider: String): String =
        get(context).getString("api_key_$provider", "") ?: ""

    fun setApiKey(context: Context, provider: String, key: String) {
        get(context).edit().putString("api_key_$provider", key).apply()
    }

    fun getModel(context: Context, provider: String): String {
        val default = when (provider) {
            "anthropic" -> "claude-sonnet-4-6"
            "openai" -> "gpt-4o-mini"
            "groq" -> "llama-3.3-70b-versatile"
            else -> "gemini-2.5-flash"
        }
        return get(context).getString("model_$provider", default) ?: default
    }

    fun setModel(context: Context, provider: String, model: String) {
        get(context).edit().putString("model_$provider", model).apply()
    }
}
