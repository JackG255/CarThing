package com.carthing.ui.attachments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carthing.data.attachments.AttachmentOwner
import com.carthing.ui.vehicles.VehicleDetailViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** File names of photos added in a form but not yet saved; survives rotation. */
@Composable
fun rememberPendingPhotos(): SnapshotStateList<String> =
    rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) { mutableListOf<String>().toMutableStateList() }

/**
 * Photo strip for a fuel or service form. Photos of an existing entry ([owner]) show immediately;
 * new ones go into [pending] and are linked by the form on save. Unsaved ones are cleaned up by the
 * orphan sweep if the form is abandoned.
 */
@Composable
fun EntryPhotos(owner: AttachmentOwner?, pending: SnapshotStateList<String>, viewModel: VehicleDetailViewModel) {
    val scope = rememberCoroutineScope()
    val savedFlow = remember(owner) { owner?.let(viewModel::observeAttachments) ?: flowOf(emptyList()) }
    val saved by savedFlow.collectAsStateWithLifecycle(emptyList())
    val items = saved.map { PhotoItem(viewModel.photoFile(it.fileName), it.id) } + pending.map { PhotoItem(viewModel.photoFile(it), null) }

    AttachmentsSection(
        photos = items,
        newCameraUri = viewModel::newCameraUri,
        importPhoto = viewModel::importPhoto,
        onAdded = { pending += it },
        onDelete = { item ->
            val attachment = saved.firstOrNull { it.id == item.savedId }
            if (attachment != null) scope.launch { viewModel.deleteAttachment(attachment) }
            else item.file.name.let { pending -= it; viewModel.discardPhoto(it) }
        }
    )
}
