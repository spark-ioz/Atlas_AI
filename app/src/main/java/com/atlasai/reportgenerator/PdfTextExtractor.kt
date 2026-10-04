package com.atlasai.reportgenerator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor as ITextExtractor
import kotlinx.coroutines.tasks.await
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Extracts text from PDF / DOCX.
 * 1) Tries native PDF text layer (fast)
 * 2) If empty (scanned PDF) → renders pages + ML Kit OCR
 * 3) DOCX via Apache POI
 */
object PdfTextExtractor {

    data class Result(
        val text: String,
        val pageCount: Int,
        val charCount: Int,
        val isDocx: Boolean = false,
        val usedOcr: Boolean = false
    )

    private const val MAX_PAGES = 25          // OCR is slower — cap pages
    private const val MAX_CHARS = 150_000
    private const val OCR_SCALE = 2           // render scale for better OCR

    suspend fun extract(context: Context, uri: Uri): Result {
        val name = displayName(context, uri)?.lowercase().orEmpty()
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()

        val isDocx = name.endsWith(".docx") || name.endsWith(".doc") ||
                mime.contains("wordprocessingml") || mime.contains("msword") ||
                mime.contains("officedocument.word")

        val isPdf = name.endsWith(".pdf") || mime.contains("pdf")

        return when {
            isDocx -> extractDocx(context, uri)
            isPdf -> extractPdfWithOcrFallback(context, uri)
            else -> {
                try {
                    extractDocx(context, uri)
                } catch (_: Exception) {
                    extractPdfWithOcrFallback(context, uri)
                }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment
    }

    private suspend fun extractPdfWithOcrFallback(context: Context, uri: Uri): Result {
        // Pass 1: selectable text
        val layer = try {
            extractPdfTextLayer(context, uri)
        } catch (_: Exception) {
            null
        }

        if (layer != null && layer.text.isNotBlank() && layer.charCount >= 40) {
            return layer
        }

        // Pass 2: OCR scanned pages
        return extractPdfWithOcr(context, uri)
    }

    private fun extractPdfTextLayer(context: Context, uri: Uri): Result {
        var input: InputStream? = null
        var reader: PdfReader? = null
        var pdfDoc: PdfDocument? = null
        try {
            input = BufferedInputStream(
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open PDF")
            )
            reader = PdfReader(input)
            reader.setUnethicalReading(true)
            pdfDoc = PdfDocument(reader)
            val totalPages = pdfDoc.numberOfPages
            if (totalPages < 1) throw IllegalStateException("PDF has no pages")

            val pagesToRead = minOf(totalPages, MAX_PAGES)
            val sb = StringBuilder()
            for (i in 1..pagesToRead) {
                try {
                    val pageText = ITextExtractor.getTextFromPage(pdfDoc.getPage(i))
                    if (!pageText.isNullOrBlank()) sb.append(pageText.trim()).append("\n\n")
                } catch (_: Exception) { }
                if (sb.length >= MAX_CHARS) break
            }
            var text = sb.toString().trim()
            if (text.length > MAX_CHARS) text = text.substring(0, MAX_CHARS)
            return Result(text, totalPages, text.length, isDocx = false, usedOcr = false)
        } finally {
            try { pdfDoc?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Copy URI to cache, open with PdfRenderer, OCR each page with ML Kit.
     */
    private suspend fun extractPdfWithOcr(context: Context, uri: Uri): Result {
        val cacheFile = copyToCache(context, uri, "ocr_scan.pdf")
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            if (totalPages < 1) {
                throw IllegalStateException("PDF has no pages to scan")
            }

            val pagesToRead = minOf(totalPages, MAX_PAGES)
            val sb = StringBuilder()

            for (i in 0 until pagesToRead) {
                var page: PdfRenderer.Page? = null
                var bitmap: Bitmap? = null
                try {
                    page = renderer.openPage(i)
                    val w = page.width * OCR_SCALE
                    val h = page.height * OCR_SCALE
                    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    val pageText = result.text?.trim().orEmpty()
                    if (pageText.isNotEmpty()) {
                        sb.append(pageText).append("\n\n")
                    }
                } catch (_: Exception) {
                    // skip page
                } finally {
                    try { page?.close() } catch (_: Exception) {}
                    try { bitmap?.recycle() } catch (_: Exception) {}
                }
                if (sb.length >= MAX_CHARS) break
            }

            var text = sb.toString().trim()
            if (text.length > MAX_CHARS) text = text.substring(0, MAX_CHARS)

            if (text.isBlank()) {
                throw IllegalStateException(
                    "OCR found no text. Try a clearer scan or a DOCX export."
                )
            }

            return Result(
                text = text,
                pageCount = totalPages,
                charCount = text.length,
                isDocx = false,
                usedOcr = true
            )
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { recognizer.close() } catch (_: Exception) {}
            try { cacheFile.delete() } catch (_: Exception) {}
        }
    }

    private fun copyToCache(context: Context, uri: Uri, name: String): File {
        val out = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open file for OCR")
        return out
    }

    private fun extractDocx(context: Context, uri: Uri): Result {
        var input: InputStream? = null
        try {
            input = BufferedInputStream(
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open DOCX")
            )
            XWPFDocument(input).use { doc ->
                val sb = StringBuilder()
                for (p in doc.paragraphs) {
                    val t = p.text?.trim().orEmpty()
                    if (t.isNotEmpty()) sb.append(t).append("\n\n")
                    if (sb.length >= MAX_CHARS) break
                }
                if (sb.length < MAX_CHARS) {
                    for (table in doc.tables) {
                        for (row in table.rows) {
                            val cells = row.tableCells
                                .map { it.text?.trim().orEmpty() }
                                .filter { it.isNotEmpty() }
                            if (cells.isNotEmpty()) {
                                sb.append(cells.joinToString(" | ")).append("\n")
                            }
                        }
                        sb.append("\n")
                        if (sb.length >= MAX_CHARS) break
                    }
                }
                if (sb.length < MAX_CHARS) {
                    for (hf in doc.headerList + doc.footerList) {
                        for (p in hf.paragraphs) {
                            val t = p.text?.trim().orEmpty()
                            if (t.isNotEmpty()) sb.append(t).append("\n")
                        }
                    }
                }
                var text = sb.toString().trim()
                if (text.length > MAX_CHARS) text = text.substring(0, MAX_CHARS)
                if (text.isBlank()) throw IllegalStateException("DOCX appears empty")
                val approxPages = maxOf(1, text.length / 2200)
                return Result(text, approxPages, text.length, isDocx = true, usedOcr = false)
            }
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }
}
