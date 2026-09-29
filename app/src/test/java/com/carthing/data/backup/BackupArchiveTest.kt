package com.carthing.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.attachments.AttachmentOwner
import com.carthing.data.attachments.AttachmentRepository
import com.carthing.data.attachments.PhotoStore
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.SaveResult
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupArchiveTest {
    private lateinit var db: CarThingDatabase
    private lateinit var photos: PhotoStore
    private lateinit var archive: BackupArchive
    private lateinit var attachments: AttachmentRepository

    private val receipt = ByteArray(5_000) { (it % 251).toByte() }
    private val invoice = ByteArray(3_000) { (it % 13).toByte() }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        photos = PhotoStore(ApplicationProvider.getApplicationContext())
        photos.dir.listFiles()?.forEach { it.delete() }
        archive = BackupArchive(db, BackupRepository(db, clock = { 0 }), photos)
        attachments = AttachmentRepository(db, photos)
    }

    @After fun tearDown() = db.close()

    /** A vehicle with a fill-up and a service entry, each with one photo. */
    private suspend fun populate(): Pair<Long, Long> {
        val v = VehicleRepository(db).save(Vehicle(name = "Car"))
        val fuel = (FuelRepository(db.fuelEntryDao(), db.vehicleDao())
            .save(FuelEntry(vehicleId = v, dateEpochMillis = 1, odometerKm = 1_000.0, liters = 40.0)) as SaveResult.Saved).id
        val service = ServiceRepository(db).save(ServiceEntry(vehicleId = v, dateEpochMillis = 2, odometerKm = 1_100.0, type = "Oil"))
        photos.write("receipt-1.jpg", receipt)
        photos.write("invoice-1.jpg", invoice)
        attachments.attach(AttachmentOwner.Fuel(fuel), listOf("receipt-1.jpg"))
        attachments.attach(AttachmentOwner.Service(service), listOf("invoice-1.jpg"))
        return fuel to service
    }

    private fun zipEntries(bytes: ByteArray): List<String> = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        generateSequence { z.nextEntry }.map { it.name }.toList()
    }

    @Test fun zipContainsJsonAndPhotos() = runTest {
        populate()
        val bytes = archive.writeBytes()
        assertTrue(BackupArchive.isZip(bytes))
        assertEquals(setOf("backup.json", "attachments/receipt-1.jpg", "attachments/invoice-1.jpg"), zipEntries(bytes).toSet())
    }

    @Test fun roundTripRestoresPhotosAndLinks() = runTest {
        populate()
        val bytes = archive.writeBytes()
        // Wipe everything, including files, then restore.
        db.vehicleDao().getAll().forEach { db.vehicleDao().delete(it) }
        photos.dir.listFiles()?.forEach { it.delete() }

        val parsed = archive.read(bytes)
        assertEquals(2, parsed.file.attachments.size)
        archive.restore(parsed)

        assertArrayEquals(receipt, photos.file("receipt-1.jpg").readBytes())
        assertArrayEquals(invoice, photos.file("invoice-1.jpg").readBytes())
        val fuelId = db.fuelEntryDao().getForVehicle(db.vehicleDao().getAll().single().id).single().id
        assertEquals(listOf(fuelId), db.attachmentDao().getAll().mapNotNull { it.fuelEntryId })
    }

    @Test fun serviceBookPhotosRoundTrip() = runTest {
        populate()
        val v = db.vehicleDao().getAll().single().id
        photos.write("book-1.jpg", byteArrayOf(9, 8, 7))
        attachments.attach(AttachmentOwner.ServiceBook(v), listOf("book-1.jpg"))
        val bytes = archive.writeBytes()
        db.vehicleDao().getAll().forEach { db.vehicleDao().delete(it) }
        photos.dir.listFiles()?.forEach { it.delete() }

        archive.restore(archive.read(bytes))
        val restored = db.vehicleDao().getAll().single().id
        assertEquals(listOf("book-1.jpg"), db.attachmentDao().getAll().filter { it.vehicleId == restored }.map { it.fileName })
        assertTrue(photos.file("book-1.jpg").exists())
    }

    @Test fun restoreRemovesPhotosNotInTheBackup() = runTest {
        populate()
        val bytes = archive.writeBytes()
        photos.write("stray.jpg", byteArrayOf(1, 2, 3))
        archive.restore(archive.read(bytes))
        assertFalse(photos.file("stray.jpg").exists())
        assertTrue(photos.file("receipt-1.jpg").exists())
    }

    @Test fun legacyJsonBackupStillRestores() = runTest {
        populate()
        val json = BackupRepository(db).export()
            .replace("\"formatVersion\": 3", "\"formatVersion\": 2")
            .replace(Regex(""",\s*"attachments":\s*\[[^\]]*\]"""), "")
        val parsed = archive.read(json.toByteArray())
        assertTrue(parsed.file.attachments.isEmpty())
        archive.restore(parsed)
        assertEquals(1, db.vehicleDao().getAll().size)
    }

    @Test fun rejectsMissingPhotosDamagedZipsAndUnsafeNames() = runTest {
        populate()
        val good = archive.writeBytes()
        fun rejected(bytes: ByteArray) = try { archive.read(bytes); false } catch (_: InvalidBackupException) { true }

        // Drop one photo from the zip.
        val withoutPhoto = rezip(good) { name, data -> if (name == "attachments/invoice-1.jpg") null else data }
        assertTrue("missing photo", rejected(withoutPhoto))
        // Truncated file.
        assertTrue("damaged zip", rejected(good.copyOf(good.size / 2)))
        // A name that would escape the photo folder on restore.
        val traversal = rezip(good) { name, data ->
            if (name == "backup.json") data.decodeToString().replace("receipt-1.jpg", "../../databases/x.jpg").toByteArray() else data
        }
        assertTrue("unsafe name", rejected(traversal))
    }

    @Test fun deletingEntryDropsAttachmentAndSweepRemovesFile() = runTest {
        val (fuel, _) = populate()
        db.fuelEntryDao().delete(db.fuelEntryDao().getForVehicle(db.vehicleDao().getAll().single().id).single { it.id == fuel })
        assertTrue(db.attachmentDao().getAll().none { it.fuelEntryId == fuel })
        // Fresh files are kept (they may belong to an unsaved form)...
        assertEquals(0, attachments.sweepOrphans())
        // ...older orphans are removed.
        assertEquals(1, attachments.sweepOrphans(minAgeMillis = 0))
        assertFalse(photos.file("receipt-1.jpg").exists())
        assertTrue(photos.file("invoice-1.jpg").exists())
    }

    private fun rezip(bytes: ByteArray, change: (String, ByteArray) -> ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zo ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zi ->
                while (true) {
                    val e = zi.nextEntry ?: break
                    val data = change(e.name, zi.readBytes()) ?: continue
                    zo.putNextEntry(ZipEntry(e.name)); zo.write(data); zo.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

}
