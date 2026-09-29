package com.carthing.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    /** Shown under the field when there's no error, e.g. "From receipt". */
    hint: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun DecimalInput(
    value: String, onValueChange: (String) -> Unit, label: String, error: String? = null, hint: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) = TextInput(value, onValueChange, label, error = error, keyboardType = KeyboardType.Decimal, hint = hint, trailingIcon = trailingIcon)

/** Read-only date field with a picker; changing the date keeps the time of day of [epochMillis]. With [onClear], also offers a clear button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(epochMillis: Long, onChange: (Long) -> Unit, label: String, onClear: (() -> Unit)? = null) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(epochMillis).atZone(zone)

    OutlinedTextField(
        value = formatDate(epochMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (onClear != null) IconButton(onClick = onClear) { Icon(Icons.Default.Clear, contentDescription = "Clear date") }
                IconButton(onClick = { showPicker = true }) { Icon(Icons.Default.DateRange, contentDescription = "Pick date") }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (showPicker) {
        // DatePicker works in UTC midnights; convert to and from the local date explicitly.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(LocalDateTime.of(date, current.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
