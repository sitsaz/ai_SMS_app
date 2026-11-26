package com.example.core.localization

import java.util.Locale

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    PERSIAN("fa");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

fun AppLanguage.resolveIsPersian(): Boolean = when (this) {
    AppLanguage.PERSIAN -> true
    AppLanguage.ENGLISH -> false
    AppLanguage.SYSTEM -> Locale.getDefault().language.lowercase(Locale.ROOT) == "fa"
}
