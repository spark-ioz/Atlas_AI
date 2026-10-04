package com.atlasai.reportgenerator

import android.content.Context
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    private fun outputDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "AtlasAI_Reports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun isHeading(line: String, isFirst: Boolean): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (isFirst && t.length < 120) return true
        if (t.length in 3..90 && t == t.uppercase(Locale.getDefault()) && t.any { it.isLetter() }) return true
        if (t.matches(Regex("""^\d+[\.\)]\s+\S.+"""))) return true
        if (t.endsWith(":") && t.length < 80) return true
        // Title Case short lines
        if (t.length in 4..70 && t.split(" ").size <= 10 &&
            t[0].isUpperCase() && !t.contains(". ") &&
            t.split(" ").all { w -> !w.first().isLowerCase() || w.length <= 3 }
        ) {
            // likely heading if not a long sentence
            if (!t.endsWith(".") || t.split(" ").size <= 6) return true
        }
        return false
    }

    fun exportPdf(context: Context, content: String): String {
        val fileName = "AtlasAI_Report_${timestamp()}.pdf"
        val file = File(outputDir(context), fileName)

        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(54f, 54f, 54f, 54f)

        val font = PdfFontFactory.createFont()
        val accent = DeviceRgb(61, 90, 69)

        var isFirst = true
        for (line in content.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> document.add(Paragraph("\n").setMarginBottom(2f))
                isHeading(trimmed, isFirst) -> {
                    val p = Paragraph(trimmed)
                        .setFont(font)
                        .setBold()
                        .setFontSize(if (isFirst) 16f else 12.5f)
                        .setFontColor(if (isFirst) accent else ColorConstants.BLACK)
                        .setTextAlignment(if (isFirst) TextAlignment.CENTER else TextAlignment.LEFT)
                        .setMarginTop(if (isFirst) 0f else 12f)
                        .setMarginBottom(6f)
                    document.add(p)
                    isFirst = false
                }
                trimmed.startsWith("•") || trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    document.add(
                        Paragraph(trimmed).setFont(font).setFontSize(11f)
                            .setMarginLeft(14f).setMarginBottom(3f)
                    )
                }
                else -> {
                    document.add(
                        Paragraph(trimmed).setFont(font).setFontSize(11f)
                            .setMarginBottom(5f).setTextAlignment(TextAlignment.JUSTIFIED)
                    )
                }
            }
        }
        document.close()
        return file.absolutePath
    }

    fun exportDocx(context: Context, content: String): String {
        val fileName = "AtlasAI_Report_${timestamp()}.docx"
        val file = File(outputDir(context), fileName)

        XWPFDocument().use { doc ->
            var isFirst = true
            for (line in content.lines()) {
                val trimmed = line.trim()
                val para = doc.createParagraph()
                when {
                    trimmed.isEmpty() -> para.createRun().setText("")
                    isHeading(trimmed, isFirst) -> {
                        para.alignment = if (isFirst) ParagraphAlignment.CENTER else ParagraphAlignment.LEFT
                        val run = para.createRun()
                        run.isBold = true
                        run.fontSize = if (isFirst) 16 else 13
                        run.fontFamily = "Times New Roman"
                        run.setText(trimmed)
                        if (isFirst) run.color = "3D5A45"
                        isFirst = false
                    }
                    trimmed.startsWith("•") || trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                        para.indentationLeft = 200
                        val run = para.createRun()
                        run.fontSize = 11
                        run.fontFamily = "Times New Roman"
                        run.setText(trimmed)
                    }
                    else -> {
                        para.alignment = ParagraphAlignment.BOTH
                        val run = para.createRun()
                        run.fontSize = 11
                        run.fontFamily = "Times New Roman"
                        run.setText(trimmed)
                    }
                }
            }
            FileOutputStream(file).use { out -> doc.write(out) }
        }
        return file.absolutePath
    }
}
