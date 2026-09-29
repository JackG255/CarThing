package com.carthing.data.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real image decoding, so import() is exercised end to end
class PhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: PhotoStore

    @Before fun setUp() {
        store = PhotoStore(context)
        store.dir.listFiles()?.forEach { it.delete() }
    }

    private fun imageFile(width: Int, height: Int): Uri {
        val f = File(context.cacheDir, "source-${width}x$height.png")
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    private fun size(name: String) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(store.file(name).path, it) }.let { it.outWidth to it.outHeight }

    @Test fun smallImageIsStoredAsIs() {
        val name = store.import(imageFile(800, 1200))
        assertTrue(store.file(name).exists())
        assertEquals(800 to 1200, size(name))
    }

    @Test fun largeImageIsScaledToMaxSide() {
        val name = store.import(imageFile(4000, 3000))
        val (w, h) = size(name)
        assertEquals(PhotoStore.MAX_SIDE, maxOf(w, h))
        assertEquals(1500, h)
    }

    @Test(expected = IOException::class)
    fun nonImageIsRejected() {
        val f = File(context.cacheDir, "not-an-image.jpg").apply { writeText("hello") }
        store.import(Uri.fromFile(f))
    }

    @Test fun sweepKeepsReferencedAndFreshFiles() {
        val keep = store.import(imageFile(10, 10))
        val orphan = store.import(imageFile(10, 10))
        assertEquals(0, store.sweep(setOf(keep))) // orphan is still fresh
        assertEquals(1, store.sweep(setOf(keep), minAgeMillis = 0))
        assertTrue(store.file(keep).exists())
        assertTrue(!store.file(orphan).exists())
    }
}
