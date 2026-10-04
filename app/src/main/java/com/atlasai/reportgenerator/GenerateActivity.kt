package com.atlasai.reportgenerator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.atlasai.reportgenerator.databinding.ActivityGenerateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GenerateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
    }

    private lateinit var binding: ActivityGenerateBinding
    private var mode: ReportMode = ReportMode.STYLE_CLONE
    private var sampleText: String? = null
    private var sampleFileName: String? = null

    // GetContent is more reliable across file managers than OpenDocument alone
    private val pickDoc = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) loadDocument(uri)
        else Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
    }

    private val pickDocOpen = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            loadDocument(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = try {
            ReportMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: ReportMode.STYLE_CLONE.name)
        } catch (_: Exception) {
            ReportMode.STYLE_CLONE
        }

        applyModeUi()
        binding.scrollView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        binding.btnGenerate.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        binding.uploadZone.setOnClickListener { openPicker() }
        binding.btnChangePdf.setOnClickListener { openPicker() }
        binding.btnGenerate.setOnClickListener { onGenerate() }

        if (!AiSettings.isLiveAiEnabled(this)) {
            // Soft reminder once per screen open
            Toast.makeText(
                this,
                "Tip: open AI Settings and add Gemini/ChatGPT key so content is written by AI from your title",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun applyModeUi() {
        val (title, chip) = when (mode) {
            ReportMode.STYLE_CLONE -> getString(R.string.report_studio) to getString(R.string.preset_style_clone)
            ReportMode.EXECUTIVE -> getString(R.string.feature_executive) to getString(R.string.preset_executive)
            ReportMode.RESEARCH -> getString(R.string.feature_research) to getString(R.string.preset_research)
            ReportMode.CREATIVE -> getString(R.string.feature_creative) to getString(R.string.preset_creative)
        }
        binding.tvTitle.text = title
        binding.tvModeChip.text = chip
    }

    private fun openPicker() {
        // Prefer OpenDocument with explicit MIME list; fall back path is GetContent */*
        try {
            pickDocOpen.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword",
                    "application/octet-stream",
                    "*/*"
                )
            )
        } catch (_: Exception) {
            pickDoc.launch("*/*")
        }
    }

    private fun loadDocument(uri: Uri) {
        binding.btnGenerate.isEnabled = false
        binding.progressSection.visibility = View.VISIBLE
        binding.tvProgressStatus.setText(R.string.extracting_pdf)

        lifecycleScope.launch {
            try {
                // May use OCR for scanned PDFs — runs on background + ML Kit
                binding.tvProgressStatus.setText(R.string.extracting_pdf)
                val result = withContext(Dispatchers.IO) {
                    PdfTextExtractor.extract(this@GenerateActivity, uri)
                }
                sampleText = result.text
                sampleFileName = queryDisplayName(uri)
                    ?: if (result.isDocx) "sample.docx" else "sample.pdf"

                binding.uploadZone.visibility = View.GONE
                binding.sampleLoadedCard.visibility = View.VISIBLE
                binding.tvSampleFileName.text = sampleFileName
                val meta = getString(
                    R.string.pages_extracted, result.pageCount, result.charCount
                ) + if (result.usedOcr) " · OCR" else ""
                binding.tvSampleMeta.text = meta
                binding.imgFileType.setImageResource(
                    if (result.isDocx) R.drawable.ic_docx else R.drawable.ic_pdf
                )
                val msg = if (result.usedOcr) {
                    "Scanned PDF read with OCR · ${result.pageCount} pages · ${result.charCount} chars"
                } else {
                    "Loaded ${result.pageCount} pages · ${result.charCount} characters"
                }
                Toast.makeText(this@GenerateActivity, msg, Toast.LENGTH_LONG).show()
                // Suggest pages from sample length (~2800 chars/page)
                if (binding.etPageCount.text.isNullOrBlank()) {
                    val suggested = (result.charCount / 3200).coerceIn(5, 25)
                    binding.etPageCount.setText(suggested.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = e.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.error_pdf_read)
                Toast.makeText(this@GenerateActivity, msg, Toast.LENGTH_LONG).show()
                sampleText = null
                sampleFileName = null
                binding.uploadZone.visibility = View.VISIBLE
                binding.sampleLoadedCard.visibility = View.GONE
            } finally {
                binding.progressSection.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }

    private fun onGenerate() {
        val question = binding.etQuestion.text?.toString()?.trim().orEmpty()
        if (question.isEmpty()) {
            Toast.makeText(this, R.string.error_no_topic, Toast.LENGTH_SHORT).show()
            return
        }
        if (mode == ReportMode.STYLE_CLONE && sampleText.isNullOrBlank()) {
            Toast.makeText(this, R.string.error_no_pdf, Toast.LENGTH_SHORT).show()
            return
        }

        val pageCount = binding.etPageCount.text?.toString()?.trim()?.toIntOrNull()

        val format = when (binding.rgFormat.checkedRadioButtonId) {
            R.id.rbPdf -> OutputFormat.PDF
            R.id.rbDocx -> OutputFormat.DOCX
            else -> OutputFormat.BOTH
        }
        startGeneration(question, format, pageCount)
    }

    private fun startGeneration(question: String, format: OutputFormat, pageCount: Int?) {
        binding.btnGenerate.isEnabled = false
        binding.progressSection.visibility = View.VISIBLE
        binding.progressSection.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

        lifecycleScope.launch {
            try {
                updateStatus(R.string.style_analysis)
                delay(300)

                updateStatus(R.string.researching_web)
                val reportContent = try {
                    // Always research online for the TITLE first; AI only organizes if key present
                    updateStatus(R.string.calling_live_ai)
                    val text = AiClient.generateReport(
                        this@GenerateActivity,
                        sampleText,
                        question,
                        mode,
                        pageCount
                    )
                    withContext(Dispatchers.Main) {
                        val msg = if (AiSettings.isLiveAiEnabled(this@GenerateActivity)) {
                            "Online research + AI organized your title"
                        } else {
                            "Online research report (add AI key for polish)"
                        }
                        Toast.makeText(this@GenerateActivity, msg, Toast.LENGTH_LONG).show()
                    }
                    text
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@GenerateActivity,
                            "Research/AI issue: ${e.message?.take(100) ?: "fallback"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    updateStatus(R.string.content_generation)
                    try {
                        AiClient.generateFromWebOnly(question, pageCount, mode, sampleText)
                    } catch (_: Exception) {
                        withContext(Dispatchers.Default) {
                            ReportEngine.generate(sampleText, question, mode, pageCount)
                        }
                    }
                }
                delay(600)
                updateStatus(R.string.formatting)
                delay(350)
                updateStatus(R.string.almost_done)

                val pdfPath = if (format == OutputFormat.PDF || format == OutputFormat.BOTH) {
                    withContext(Dispatchers.IO) {
                        ReportExporter.exportPdf(this@GenerateActivity, reportContent)
                    }
                } else null

                val docxPath = if (format == OutputFormat.DOCX || format == OutputFormat.BOTH) {
                    withContext(Dispatchers.IO) {
                        ReportExporter.exportDocx(this@GenerateActivity, reportContent)
                    }
                } else null

                val title = question.take(80)
                val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                ReportLibrary.add(
                    this@GenerateActivity,
                    LibraryItem(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        mode = mode.name,
                        date = date,
                        content = reportContent,
                        pdfPath = pdfPath,
                        docxPath = docxPath
                    )
                )

                getSharedPreferences("atlas_ai", MODE_PRIVATE).edit()
                    .putString("last_report", title).apply()

                startActivity(Intent(this@GenerateActivity, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_CONTENT, reportContent)
                    putExtra(PreviewActivity.EXTRA_PDF_PATH, pdfPath)
                    putExtra(PreviewActivity.EXTRA_DOCX_PATH, docxPath)
                    putExtra(PreviewActivity.EXTRA_FORMAT, format.name)
                })
                overridePendingTransition(R.anim.slide_up, R.anim.fade_out)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@GenerateActivity, R.string.error_generate, Toast.LENGTH_LONG).show()
            } finally {
                binding.btnGenerate.isEnabled = true
                binding.progressSection.visibility = View.GONE
            }
        }
    }

    private fun updateStatus(resId: Int) {
        binding.tvProgressStatus.setText(resId)
    }

    enum class OutputFormat { PDF, DOCX, BOTH }
}
