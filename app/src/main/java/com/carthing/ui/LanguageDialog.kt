package com.carthing.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.carthing.CarThingApp
import com.carthing.R
import com.carthing.data.Language

/** Picks the app's language. Each language is named in itself, so it can be found from any other. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as CarThingApp).container.languageSettings
    val current = settings.chosen
    val options = listOf(
        null to stringResource(R.string.language_system),
        Language.EN to stringResource(R.string.language_name_en),
        Language.CS to stringResource(R.string.language_name_cs),
        Language.SK to stringResource(R.string.language_name_sk),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                options.forEach { (language, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = language == current, role = Role.RadioButton) {
                                onDismiss()
                                if (language == current) return@selectable
                                settings.choose(language)
                                // Android 13+ recreates the screen itself; older phones need it done here.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) (context as? Activity)?.recreate()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Text(label, Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
