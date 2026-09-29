package com.carthing.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Writes backups into a user-chosen folder (a Storage Access Framework document tree). */
class FolderBackup(private val resolver: ContentResolver) {

    /** Writes [json] as a new timestamped file in [tree] and prunes old automatic backups. */
    fun write(tree: Uri, json: String, now: LocalDateTime = LocalDateTime.now()) {
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = BackupPolicy.AUTO_PREFIX + now.format(STAMP) + ".json"
        val file = DocumentsContract.createDocument(resolver, folder, "application/json", name)
            ?: throw IOException("The folder doesn't accept new files")
        resolver.openOutputStream(file, "wt")?.use { it.write(json.toByteArray()) }
            ?: throw IOException("Can't write to the folder")
        prune(tree)
    }

    /** The folder's display name, for the settings dialog; null if it's no longer reachable. */
    fun folderName(tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun prune(tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = mutableMapOf<String, String>() // name -> document id
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { c ->
            while (c.moveToNext()) files[c.getString(1)] = c.getString(0)
        }
        for (name in BackupPolicy.autoBackupsToDelete(files.keys.toList())) {
            // Pruning is best-effort: a provider that refuses deletes just keeps more files.
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, files.getValue(name))) }
        }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
