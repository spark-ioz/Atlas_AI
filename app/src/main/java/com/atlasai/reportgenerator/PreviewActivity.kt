package com.atlasai.reportgenerator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.atlasai.reportgenerator.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_PDF_PATH = "extra_pdf_path"
        const val EXTRA_DOCX_PATH = "extra_docx_path"
        const val EXTRA_FORMAT = "extra_format"
    }

    private lateinit var binding: ActivityPreviewBinding
    private var pdfPath: String? = null
    private var docxPath: String? = null
    private var content: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        pdfPath = intent.getStringExtra(EXTRA_PDF_PATH)
        docxPath = intent.getStringExtra(EXTRA_DOCX_PATH)
        val format = intent.getStringExtra(EXTRA_FORMAT) ?: "BOTH"

        setupUi(format)
        setupClickListeners()
        setupAnimations()
    }

    private fun setupUi(format: String) {
        binding.tvPreviewContent.text = content

        when (format) {
            "PDF" -> {
                binding.btnDownloadPdf.visibility = View.VISIBLE
                binding.btnDownloadDocx.visibility = View.GONE
            }
            "DOCX" -> {
                binding.btnDownloadPdf.visibility = View.GONE
                binding.btnDownloadDocx.visibility = View.VISIBLE
            }
            else -> {
                binding.btnDownloadPdf.visibility = View.VISIBLE
                binding.btnDownloadDocx.visibility = View.VISIBLE
            }
        }
    }

    private fun setupAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.successBanner.startAnimation(fadeIn)
        binding.scrollPreview.startAnimation(slideUp)
        binding.bottomActions.startAnimation(slideUp)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        binding.btnDownloadPdf.setOnClickListener {
            pdfPath?.let { path ->
                openOrShareFile(File(path), "application/pdf")
            } ?: Toast.makeText(this, "PDF not available", Toast.LENGTH_SHORT).show()
        }

        binding.btnDownloadDocx.setOnClickListener {
            docxPath?.let { path ->
                openOrShareFile(
                    File(path),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
            } ?: Toast.makeText(this, "DOCX not available", Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val fileToShare = when {
                pdfPath != null -> File(pdfPath!!)
                docxPath != null -> File(docxPath!!)
                else -> null
            }
            if (fileToShare != null && fileToShare.exists()) {
                val mime = if (fileToShare.extension == "pdf") "application/pdf"
                else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                shareFile(fileToShare, mime)
            } else {
                // Share text content as fallback
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                    putExtra(Intent.EXTRA_SUBJECT, "ATLAS AI Generated Report")
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
        }
    }

    private fun openOrShareFile(file: File, mimeType: String) {
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                shareFile(file, mimeType)
            }
            Toast.makeText(this, R.string.file_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            shareFile(file, mimeType)
        }
    }

    private fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unable to share file", Toast.LENGTH_SHORT).show()
        }
    }
}
