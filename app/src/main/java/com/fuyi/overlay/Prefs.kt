package com.fuyi.overlay

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("fuyi", Context.MODE_PRIVATE)

    var apiBase: String
        get() = sp.getString("api_base", DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) {
            sp.edit().putString("api_base", value).apply()
        }

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) {
            sp.edit().putString("api_key", value).apply()
        }

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            sp.edit().putString("model", value).apply()
        }

    var glossary: String
        get() = sp.getString("glossary", "") ?: ""
        set(value) {
            sp.edit().putString("glossary", value).apply()
        }

    var smartSelect: Boolean
        get() = sp.getBoolean("smart_select", true)
        set(value) {
            sp.edit().putBoolean("smart_select", value).apply()
        }

    companion object {
        const val DEFAULT_BASE = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
