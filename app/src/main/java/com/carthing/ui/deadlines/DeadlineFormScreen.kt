package com.carthing.ui.deadlines

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.deadlines.DeadlineTemplates
import com.carthing.data.entity.Deadline
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.DeleteAction
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.TextInput
import com.carthing.ui.vehicles.VehicleDetailUiState
import com.carthing.ui.vehicles.VehicleDetailViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Add ([deadlineId] = 0) or edit a deadline. */
@Composable
fun DeadlineFormScreen(
    vehicleId: Long,
    deadlineId: Long,
    onDone: () -> Unit,
    viewModel: VehicleDetailViewModel = viewModel(factory = VehicleDetailViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state as? VehicleDetailUiState.Loaded ?: return LoadingBox()
    val existing = remember { s.deadlines.firstOrNull { it.deadline.id == deadlineId }?.deadline }
    DeadlineForm(existing, viewModel, onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineForm(existing: Deadline?, viewModel: VehicleDetailViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var due by rememberSaveable {
        mutableLongStateOf(existing?.dueEpochMillis ?: LocalDate.now(zone).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
    var repeat by rememberSaveable { mutableStateOf(existing?.repeatMonths?.toString().orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val repeatValue = repeat.trim().toIntOrNull()
    val titleError = if (submitted && title.isBlank()) "Title is required" else null
    val repeatError = if (repeat.isNotBlank() && (repeatValue ?: 0) <= 0) "Enter a whole number of months" else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add deadline" else "Edit deadline") },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (existing != null) DeleteAction("Delete ${existing.title}?", "You won't be reminded about it any more.") {
                        scope.launch { viewModel.deleteDeadline(existing); onDone() }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextInput(title, { title = it }, "Title *", error = titleError)
            if (existing == null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeadlineTemplates.all.forEach { t ->
                        SuggestionChip(onClick = { title = t.title; repeat = t.repeatMonths?.toString().orEmpty() }, label = { Text(t.title) })
                    }
                }
            }
            DateInput(due, { due = it }, "Due date (last valid day)")
            TextInput(repeat, { repeat = it }, "Repeats every (months)", error = repeatError, keyboardType = KeyboardType.Number)
            TextInput(note, { note = it }, "Note", singleLine = false)
            Button(
                onClick = {
                    submitted = true
                    if (title.isBlank() || repeatError != null) return@Button
                    val base = existing ?: Deadline(vehicleId = 0, title = "", dueEpochMillis = 0)
                    val deadline = base.copy(
                        title = title, dueEpochMillis = due, repeatMonths = repeatValue, note = note.trim().ifEmpty { null },
                        // A moved due date starts a new reminder cycle.
                        notifiedLevel = if (existing != null && existing.dueEpochMillis == due) existing.notifiedLevel else 0
                    )
                    scope.launch { viewModel.saveDeadline(deadline); onDone() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}
