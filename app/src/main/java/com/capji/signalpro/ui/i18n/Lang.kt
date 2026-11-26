package com.capji.signalpro.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** In-app language, switchable instantly. Usage: `t("عربي", "English")`. */
class Lang(val isArabic: Boolean) {
    operator fun invoke(ar: String, en: String): String = if (isArabic) ar else en
}

val LocalLang = staticCompositionLocalOf { Lang(true) }
