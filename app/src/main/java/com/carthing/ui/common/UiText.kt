package com.carthing.ui.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Text chosen outside the UI (e.g. in a ViewModel) as a string resource plus its format args; a [UiText] arg is resolved too. */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList())

fun UiText.resolve(res: Resources): String =
    if (args.isEmpty()) res.getString(id)
    else res.getString(id, *args.map { if (it is UiText) it.resolve(res) else it }.toTypedArray())

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current.resources)
