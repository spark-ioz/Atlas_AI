package com.atlasai.reportgenerator

import android.content.Context

enum class AiProvider {
    LOCAL,
    OPENAI,
    GEMINI
}

object AiSettings {
    private const val PREFS = "atlas_ai_settings"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"

    fun getProvider(context: Context): AiProvider {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROVIDER, AiProvider.LOCAL.name) ?: AiProvider.LOCAL.name
        return try {
            AiProvider.valueOf(name)
        } catch (_: Exception) {
            AiProvider.LOCAL
        }
    }

    fun setProvider(context: Context, provider: AiProvider) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    fun getApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODEL, null)
        if (!saved.isNullOrBlank()) return saved
        return when (getProvider(context)) {
            AiProvider.OPENAI -> "gpt-4o-mini"
            AiProvider.GEMINI -> "gemini-2.0-flash"
            AiProvider.LOCAL -> ""
        }
    }

    fun setModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun isLiveAiEnabled(context: Context): Boolean {
        val p = getProvider(context)
        return p != AiProvider.LOCAL && getApiKey(context).isNotBlank()
    }
}
