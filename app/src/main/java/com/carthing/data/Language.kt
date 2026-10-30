package com.carthing.data

import java.util.Locale

/** Languages the app is translated into; every other locale falls back to [EN]. */
enum class Language {
    EN, CS, SK;

    companion object {
        /** Matches on [Locale.getLanguage] only, so `cs_CZ` and `cs` are both [CS]. */
        fun of(locale: Locale): Language = when (locale.language) {
            "cs" -> CS
            "sk" -> SK
            else -> EN
        }
    }
}
