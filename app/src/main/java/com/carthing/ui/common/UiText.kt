package com.carthing.ui.common

import android.content.res.Resources
import androidx.annotation.AnyRes
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text chosen outside the UI (e.g. in a ViewModel) as a string resource plus its format args; a [UiText] arg is
 * resolved too. [id] is a plurals resource when [quantity] is set (see [plural]), a string resource otherwise.
 */
data class UiText(@AnyRes val id: Int, val args: List<Any> = emptyList(), val quantity: Int? = null) {
    companion object {
        /** A plural of [quantity]; the quantity is format arg 1, [args] follow. */
        fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any) = UiText(id, listOf<Any>(quantity) + args, quantity)
    }
}

fun UiText.resolve(res: Resources): String {
    val resolved = args.map { if (it is UiText) it.resolve(res) else it }.toTypedArray()
    return when {
        quantity != null -> res.getQuantityString(id, quantity, *resolved)
        resolved.isEmpty() -> res.getString(id)
        else -> res.getString(id, *resolved)
    }
}

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current.resources)
