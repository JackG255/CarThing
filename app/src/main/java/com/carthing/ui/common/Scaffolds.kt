package com.carthing.ui.common

import com.carthing.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Delete icon that asks for confirmation before calling [onConfirm]. */
@Composable
fun DeleteAction(title: String, message: String, onConfirm: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { confirming = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete)) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirming = false; onConfirm() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
