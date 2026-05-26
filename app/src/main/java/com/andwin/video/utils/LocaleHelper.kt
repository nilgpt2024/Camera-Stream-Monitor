package com.andwin.video.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    const val PREF_NAME = "language_settings"
    const val KEY_LANGUAGE = "language"

    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"

    fun setLocale(context: Context, language: String): Context {
        persistLanguage(context, language)
        return updateResources(context, language)
    }

    fun getLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ZH) ?: LANGUAGE_ZH
    }

    private fun persistLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        var locale = Locale(language)
        if (language == LANGUAGE_ZH) {
            locale = Locale.SIMPLIFIED_CHINESE
        }
        
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        return context.createConfigurationContext(configuration)
    }
}
