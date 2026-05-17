package com.subaru.servicetool.data.model

import java.util.Locale

data class LocalizedText(
    val en: String,
    val ka: String = en,
    val ru: String = en,
    val es: String = en,
    val fr: String = en,
    val de: String = en,
)

fun LocalizedText.forLocale(): String = when (Locale.getDefault().language) {
    "ka" -> ka
    "ru" -> ru
    "es" -> es
    "fr" -> fr
    "de" -> de
    else -> en
}
