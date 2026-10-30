package com.carthing.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.carthing.data.Language

/** The app's current language; recomposes when the locale changes. */
@Composable
fun currentLanguage(): Language = Language.of(LocalConfiguration.current.locales[0])
