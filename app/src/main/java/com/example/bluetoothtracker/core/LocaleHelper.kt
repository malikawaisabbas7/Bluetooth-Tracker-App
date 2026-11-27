package com.example.bluetoothtracker.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Centralizes per-app locale switching and retrieval via AndroidX AppCompat.
 * Persists automatically via AppLocalesMetadataHolderService and Android 13+ localeConfig.
 */
object LocaleHelper {

    fun applyLanguage(language: AppLanguage) {
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (!currentTags.equals(language.tag, ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.tag)
            )
        }
    }

    /**
     * Returns true when [context]'s currently rendered configuration already uses [language]'s
     * primary language (e.g., first-launch English device selecting English UK), avoiding an
     * unnecessary Activity recreation during Continue -> Onboarding.
     */
    fun isLocaleAlreadyActive(context: Context, language: AppLanguage): Boolean {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales.toLanguageTags().equals(language.tag, ignoreCase = true)
        }
        val activeLang = context.resources.configuration.locales[0]?.language ?: return false
        return activeLang.equals(language.tag.substringBefore('-'), ignoreCase = true)
    }

    fun currentLanguage(fallbackTag: String? = null): AppLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!appLocales.isEmpty) {
            appLocales.toLanguageTags().substringBefore(',')
        } else {
            fallbackTag
        }
        return AppLanguage.fromTag(tag)
    }
}
