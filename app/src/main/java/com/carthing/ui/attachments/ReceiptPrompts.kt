package com.carthing.ui.attachments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Asks before reading a new photo; reading is opt-in since not every photo is a receipt. */
@Composable
fun ReadOffer(question: String, onRead: () -> Unit, onDismiss: () -> Unit) {
    PromptCard(question) {
        TextButton(onClick = onDismiss) { Text("No") }
        TextButton(onClick = onRead) { Text("Read") }
    }
}

/** Says what was filled from a photo, or that nothing could be read. */
@Composable
fun ReceiptBanner(message: String, onDismiss: () -> Unit) {
    PromptCard(message) { TextButton(onClick = onDismiss) { Text("OK") } }
}

@Composable
private fun PromptCard(text: String, actions: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            actions()
        }
    }
}
