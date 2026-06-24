package com.andwin.video.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    const val PREF_NAME = "language_settings"
    const val KEY_LANGUAGE = "language"

    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"

    /** 保存当前应用的 Locale，供 API < 33 的 applyOverrideConfiguration 使用 */
    private var currentLocale: Locale = Locale.SIMPLIFIED_CHINESE

    /**
     * 设置语言并返回包装后的 Context（API < 33 时需要）
     * API 33+ 通过 AppCompatDelegate.setApplicationLocales 自动生效，无需替换 Context
     */
    fun setLocale(context: Context, language: String): Context {
        persistLanguage(context, language)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)：使用系统级应用内语言切换
            val locale = toLocale(language)
            currentLocale = locale
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
            context // Context 不变，系统自动处理资源加载
        } else {
            updateResources(context, language)
        }
    }

    fun getLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ZH) ?: LANGUAGE_ZH
    }

    fun getPersistedLocale(context: Context): Locale {
        return toLocale(getLocale(context))
    }

    private fun toLocale(language: String): Locale {
        return if (language == LANGUAGE_ZH) Locale.SIMPLIFIED_CHINESE else Locale(language)
    }

    private fun persistLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * 旧版方式：通过 createConfigurationContext 包装 Context
     * 仅在 API < 33 时使用
     */
    fun updateResources(context: Context, language: String): Context {
        val locale = toLocale(language)
        currentLocale = locale

        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    /**
     * API 26 ~ 32 需要在 Activity 中重写此方法，
     * 否则 createConfigurationContext 设置的 locale 会被系统的 configuration 覆盖。
     * API 33+ 不需要此方法（由系统自动处理）。
     */
    fun applyOverrideConfiguration(overrideConfiguration: Configuration?): Configuration? {
        // 仅在 API 26 ~ 32 范围内处理
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.S_V2
            && overrideConfiguration != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                overrideConfiguration.setLocales(LocaleList(currentLocale))
            } else {
                @Suppress("DEPRECATION")
                overrideConfiguration.locale = currentLocale
            }
        }
        return overrideConfiguration
    }
}
