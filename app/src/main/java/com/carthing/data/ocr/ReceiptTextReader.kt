package com.carthing.data.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.carthing.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition (ML Kit, bundled Latin model: offline, Czech diacritics included). */
class ReceiptTextReader(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The image's text as rows, top to bottom (see [TextRows]). */
    suspend fun readRows(image: File): List<String> {
        val input = InputImage.fromFilePath(context, Uri.fromFile(image))
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val pieces = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                line.boundingBox?.let { b -> TextPiece(line.text, b.left, b.top, b.right, b.bottom, line.angle) }
            }
        }
        return TextRows.group(pieces).also { rows ->
            // Receipt text stays out of release logs; in debug builds it helps tune the rules.
            if (BuildConfig.DEBUG) Log.d("CarThing", "Receipt rows:\n" + rows.joinToString("\n"))
        }
    }

    /** Reads [image] as a fuel receipt; fields that can't be read reliably are left null. */
    suspend fun readFuelReceipt(image: File): FuelReceipt = FuelReceiptParser.parse(readRows(image))

    /** Reads [image] as a service invoice or repair-shop receipt. */
    suspend fun readServiceInvoice(image: File): ServiceInvoice = ServiceInvoiceParser.parse(readRows(image))
}
