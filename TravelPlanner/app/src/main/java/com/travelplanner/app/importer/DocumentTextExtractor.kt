package com.travelplanner.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

data class ExtractedContent(val text: String, val barcodes: List<String>)

/**
 * Достаёт текст и содержимое штрихкодов из PDF, фото/скриншота или текстового файла.
 * Всё выполняется на устройстве, без отправки данных в интернет.
 */
class DocumentTextExtractor(private val context: Context) {

    suspend fun extract(file: File, mime: String?): ExtractedContent = withContext(Dispatchers.IO) {
        val type = mime ?: guessMime(file.name)
        when {
            type == "application/pdf" -> fromPdf(file)
            type.startsWith("image/") -> fromImage(InputImage.fromFilePath(context, Uri.fromFile(file)))
            else -> ExtractedContent(stripHtml(file.readText()), emptyList())
        }
    }

    private suspend fun fromPdf(file: File): ExtractedContent {
        val text = runCatching {
            PDDocument.load(file).use { doc -> PDFTextStripper().getText(doc) }
        }.getOrDefault("")

        val barcodes = mutableListOf<String>()
        val ocr = StringBuilder()
        val needOcr = text.isBlank() || text.length < 40
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until minOf(renderer.pageCount, 6)) {
                        renderer.openPage(i).use { page ->
                            val scale = 2000f / maxOf(page.width, page.height)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val image = InputImage.fromBitmap(bmp, 0)
                            barcodes += scanBarcodes(image)
                            if (needOcr) ocr.appendLine(recognize(image))
                            bmp.recycle()
                        }
                    }
                }
            }
        }
        return ExtractedContent(if (needOcr) text + "\n" + ocr else text, barcodes)
    }

    private suspend fun fromImage(image: InputImage): ExtractedContent =
        ExtractedContent(recognize(image), scanBarcodes(image))

    private suspend fun recognize(image: InputImage): String = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(image).await().text
        } finally {
            recognizer.close()
        }
    }.getOrDefault("")

    private suspend fun scanBarcodes(image: InputImage): List<String> = runCatching {
        val scanner = BarcodeScanning.getClient()
        try {
            scanner.process(image).await().mapNotNull { it.rawValue }.filter { it.isNotBlank() }
        } finally {
            scanner.close()
        }
    }.getOrDefault(emptyList())

    private fun stripHtml(s: String): String =
        if ("<html" in s.lowercase() || "<div" in s.lowercase()) {
            s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>"), "\n")
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
        } else s

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "text/plain"
    }
}
