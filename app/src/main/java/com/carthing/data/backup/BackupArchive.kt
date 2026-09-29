package com.carthing.data.backup

import com.carthing.data.CarThingDatabase
import com.carthing.data.attachments.PhotoStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A parsed, validated backup ready to restore: the data plus the photo bytes it refers to. */
class ParsedBackup(val file: BackupFile, val photos: Map<String, ByteArray>)

/**
 * The backup file on disk: a zip with `backup.json` and `attachments/<name>.jpg`. Plain JSON
 * files from before photos existed are still accepted on restore.
 */
class BackupArchive(
    private val db: CarThingDatabase,
    private val backups: BackupRepository,
    private val photos: PhotoStore,
) {
    suspend fun writeTo(out: OutputStream) {
        val json = backups.export()
        val names = db.attachmentDao().allFileNames()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray())
            zip.closeEntry()
            for (name in names) {
                val f = photos.file(name)
                if (!f.exists()) continue // restore reports it as missing; the data is still worth saving
                zip.putNextEntry(ZipEntry(PHOTO_DIR + name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun writeBytes(): ByteArray = ByteArrayOutputStream().also { writeTo(it) }.toByteArray()

    /** Parses [bytes] (zip or legacy JSON) and validates everything without touching stored data. */
    fun read(bytes: ByteArray): ParsedBackup {
        if (!isZip(bytes)) return ParsedBackup(backups.read(bytes.decodeToString()), emptyMap())
        var json: String? = null
        val found = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val data = zip.readBytes()
                    total += data.size
                    if (total > MAX_TOTAL_BYTES) throw InvalidBackupException("The backup file is too large.")
                    when {
                        entry.name == JSON_ENTRY -> json = data.decodeToString()
                        entry.name.startsWith(PHOTO_DIR) -> found[entry.name.removePrefix(PHOTO_DIR)] = data
                    }
                }
            }
        } catch (e: IOException) {
            // ZipException for garbage, EOFException for a truncated file (e.g. an interrupted copy).
            throw InvalidBackupException("The backup file is damaged (not a readable zip).", e)
        }
        val file = backups.read(json ?: throw InvalidBackupException("This isn't a CarThing backup file."))
        val missing = file.attachments.map { it.fileName }.filterNot { it in found }
        if (missing.isNotEmpty()) throw InvalidBackupException("The backup file is damaged (${missing.size} photo(s) missing).")
        return ParsedBackup(file, file.attachments.associate { it.fileName to found.getValue(it.fileName) })
    }

    /** Replaces all data and photos with [parsed]. Photos are written first, old ones removed last. */
    suspend fun restore(parsed: ParsedBackup) {
        parsed.photos.forEach { (name, bytes) -> photos.write(name, bytes) }
        backups.replaceAll(parsed.file)
        photos.sweep(db.attachmentDao().allFileNames().toSet(), minAgeMillis = 0)
    }

    companion object {
        const val JSON_ENTRY = "backup.json"
        const val PHOTO_DIR = "attachments/"
        private const val MAX_TOTAL_BYTES = 1L shl 30 // 1 GB, a guard against corrupt or hostile files

        fun isZip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    }
}
