package com.example.bluetoothtracker.core

/**
 * All 24 supported application languages.
 * [tag] is the BCP-47 language tag used with AppCompat per-app locales.
 * [nativeName] is displayed as the primary label; [englishName] as a concise secondary label;
 * [flagEmoji] is the Unicode country flag emoji representing the language.
 */
enum class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val flagEmoji: String
) {
    URDU("ur", "اردو", "Urdu", "🇵🇰"),
    ENGLISH_UK("en-GB", "English (UK)", "English", "🇬🇧"),
    ARABIC("ar", "العربية", "Arabic", "🇸🇦"),
    SPANISH("es", "Español", "Spanish", "🇪🇸"),
    RUSSIAN("ru", "Русский", "Russian", "🇷🇺"),
    FRENCH("fr", "Français", "French", "🇫🇷"),
    GERMAN("de", "Deutsch", "German", "🇩🇪"),
    PORTUGUESE("pt", "Português", "Portuguese", "🇵🇹"),
    ITALIAN("it", "Italiano", "Italian", "🇮🇹"),
    INDONESIAN("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
    JAPANESE("ja", "日本語", "Japanese", "🇯🇵"),
    THAI("th", "ไทย", "Thai", "🇹🇭"),
    ROMANIAN("ro", "Română", "Romanian", "🇷🇴"),
    PERSIAN("fa", "فارسی", "Persian", "🇮🇷"),
    TURKISH("tr", "Türkçe", "Turkish", "🇹🇷"),
    HUNGARIAN("hu", "Magyar", "Hungarian", "🇭🇺"),
    SLOVAK("sk", "Slovenčina", "Slovak", "🇸🇰"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
    POLISH("pl", "Polski", "Polish", "🇵🇱"),
    BULGARIAN("bg", "Български", "Bulgarian", "🇧🇬"),
    HINDI("hi", "हिन्दी", "Hindi", "🇮🇳"),
    KOREAN("ko", "한국어", "Korean", "🇰🇷"),
    MALAY("ms", "Melayu", "Malay", "🇲🇾"),
    FINNISH("fi", "Suomi", "Finnish", "🇫🇮");

    companion object {
        val DEFAULT = ENGLISH_UK

        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return DEFAULT
            val normalized = tag.replace('_', '-')
            return entries.firstOrNull { it.tag.equals(normalized, ignoreCase = true) }
                ?: entries.firstOrNull {
                    normalized.substringBefore('-').equals(it.tag.substringBefore('-'), ignoreCase = true)
                }
                ?: DEFAULT
        }
    }
}
