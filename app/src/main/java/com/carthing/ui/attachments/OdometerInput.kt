package com.carthing.ui.attachments

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.formatKm
import com.carthing.ui.vehicles.VehicleDetailViewModel
import kotlinx.coroutines.launch

/**
 * Odometer field with a camera button: photograph the dashboard and pick the reading from the
 * numbers found. Nothing is filled without a pick, since a cluster shows many numbers. The photo
 * is kept with the entry via [onPhotoAdded] (the user can delete it from the photo strip).
 */
@Composable
fun OdometerInput(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    expectedKm: Double?,
    viewModel: VehicleDetailViewModel,
    onPhotoAdded: (fileName: String) -> Unit,
    hint: String? = null,
) {
    val scope = rememberCoroutineScope()
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var reading by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<Double>?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri?.takeIf { taken }
        cameraUri = null
        if (uri != null) scope.launch {
            reading = true
            choices = runCatching {
                val name = viewModel.importPhoto(uri)
                onPhotoAdded(name)
                viewModel.readOdometer(name, expectedKm)
            }.onFailure { Log.w("CarThing", "Dashboard photo failed", it) }.getOrDefault(emptyList())
            reading = false
        }
    }

    DecimalInput(value, onValueChange, "Odometer (km)", error, hint) {
        if (reading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else IconButton(onClick = { viewModel.newCameraUri().also { cameraUri = it; camera.launch(it) } }) {
            Icon(CameraIcon, contentDescription = "Read from dashboard photo")
        }
    }

    choices?.let { found ->
        AlertDialog(
            onDismissRequest = { choices = null },
            title = { Text("Odometer reading") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (found.isEmpty()) {
                        Text("Couldn't find a reading in the photo. Try again closer to the display, without glare.")
                    } else {
                        Text("Pick the reading shown on the odometer:", style = MaterialTheme.typography.bodyMedium)
                        found.forEach { km ->
                            OutlinedButton(onClick = { onValueChange(km.toLong().toString()); choices = null }) { Text(formatKm(km)) }
                        }
                    }
                    Text("The photo is attached to this entry.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { choices = null }) { Text(if (found.isEmpty()) "OK" else "None of these") } },
        )
    }
}

/** Material "photo camera" (the core icon set has none). */
private val CameraIcon: ImageVector = ImageVector.Builder("PhotoCamera", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
        "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
            "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
    ),
    fill = SolidColor(Color.Black),
).build()
