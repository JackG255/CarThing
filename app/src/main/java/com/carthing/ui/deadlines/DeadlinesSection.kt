package com.carthing.ui.deadlines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carthing.R
import com.carthing.data.deadlines.DeadlineStatus
import com.carthing.data.entity.Deadline
import com.carthing.data.maintenance.UrgencyBand
import com.carthing.data.repository.DeadlineWithStatus
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.urgencyColor

/** Adds a "Deadlines" header, one row per deadline and an add button to a lazy list. */
fun LazyListScope.deadlinesSection(
    deadlines: List<DeadlineWithStatus>,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onRenew: (Deadline) -> Unit,
) {
    item(key = "deadlines-header") {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.deadlines_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text(stringResource(R.string.deadlines_add)) }
        }
    }
    if (deadlines.isEmpty()) {
        item(key = "deadlines-empty") {
            Text(stringResource(R.string.deadlines_empty),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
    items(deadlines, key = { "deadline-${it.deadline.id}" }) { (deadline, status) ->
        ListItem(
            headlineContent = { Text(deadline.title) },
            supportingContent = {
                val due = formatDate(deadline.dueEpochMillis)
                val dueText = deadline.repeatMonths?.let { pluralStringResource(R.plurals.deadline_due_repeating, it, due, it) }
                    ?: stringResource(R.string.deadline_due, due)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dueText, style = MaterialTheme.typography.bodySmall)
                    Text(status.describeExpiry().replaceFirstChar { it.uppercase() }, color = status.color(),
                        style = MaterialTheme.typography.bodyMedium)
                }
            },
            trailingContent = { TextButton(onClick = { onRenew(deadline) }) { Text(stringResource(R.string.deadlines_renew)) } },
            modifier = Modifier.clickable { onEdit(deadline.id) }
        )
        HorizontalDivider()
    }
    item(key = "maintenance-header") {
        Text(stringResource(R.string.detail_tab_maintenance), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
    }
}

@Composable
private fun DeadlineStatus.color() = urgencyColor(UrgencyBand.of(level, fractionRemaining))

/** Asks for the new due date, defaulting to the current one plus the repeat interval. */
@Composable
fun RenewDialog(deadline: Deadline, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var due by rememberSaveable { mutableLongStateOf(DeadlineStatus.nextDue(deadline) ?: System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deadlines_renew_title, deadline.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.deadlines_renew_hint),
                    style = MaterialTheme.typography.bodyMedium)
                DateInput(due, { due = it }, stringResource(R.string.deadlines_new_due_date))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(due) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
