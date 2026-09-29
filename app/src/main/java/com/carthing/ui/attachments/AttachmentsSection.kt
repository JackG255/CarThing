package com.carthing.ui.attachments

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch

/** A photo shown in the strip: already saved with the entry, or added in this form and not saved yet. */
data class PhotoItem(val file: File, val savedId: Long?)

/**
 * "Receipts" strip for a form: thumbnails, an add button (camera or gallery) and a full-screen viewer.
 * [importPhoto] copies a picked image into app storage and returns its file name.
 */
@Composable
fun AttachmentsSection(
    photos: List<PhotoItem>,
    newCameraUri: () -> Uri,
    importPhoto: suspend (Uri) -> String,
    onAdded: (fileName: String) -> Unit,
    onDelete: (PhotoItem) -> Unit,
    /** When set, the viewer offers reading the photo as a receipt (only ever on request). */
    onRead: ((PhotoItem) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<PhotoItem?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun import(uri: Uri) = scope.launch {
        importing = true
        error = runCatching { onAdded(importPhoto(uri)) }.exceptionOrNull()?.let {
            Log.w("CarThing", "Photo import failed for $uri", it)
            "Couldn't add the photo"
        }
        importing = false
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraUri?.takeIf { taken }?.let(::import)
        cameraUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::import) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Receipts & photos", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { p ->
                AsyncImage(
                    model = p.file, contentDescription = "Attached photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(THUMB).clip(RoundedCornerShape(8.dp)).clickable { viewing = p }
                )
            }
            Box {
                OutlinedCard(onClick = { menu = true }, modifier = Modifier.size(THUMB)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (importing) CircularProgressIndicator(Modifier.size(24.dp))
                        else Icon(Icons.Default.Add, contentDescription = "Add photo")
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Take photo") }, onClick = {
                        menu = false
                        newCameraUri().also { cameraUri = it; camera.launch(it) }
                    })
                    DropdownMenuItem(text = { Text("Choose from gallery") }, onClick = {
                        menu = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    viewing?.let { p ->
        PhotoViewer(p, onClose = { viewing = null }, onDelete = { viewing = null; onDelete(p) },
            onRead = onRead?.let { read -> { viewing = null; read(p) } })
    }
}

/** Full-screen photo with pinch-to-zoom and pan, for reading small print on receipts. */
@Composable
private fun PhotoViewer(photo: PhotoItem, onClose: () -> Unit, onDelete: () -> Unit, onRead: (() -> Unit)?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var confirmDelete by remember { mutableStateOf(false) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photo.file, contentDescription = "Photo", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().transformable(transform)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            )
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                if (onRead != null) TextButton(onClick = onRead) { Text("Read receipt", color = Color.White) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete photo", tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete photo?") },
        text = { Text(if (photo.savedId != null) "It's removed from this entry." else "It won't be attached.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

private val THUMB = 72.dp
