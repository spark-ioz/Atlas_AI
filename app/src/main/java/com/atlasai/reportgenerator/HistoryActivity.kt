package com.atlasai.reportgenerator

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atlasai.reportgenerator.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        adapter = HistoryAdapter(
            onOpen = { item ->
                startActivity(Intent(this, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_CONTENT, item.content)
                    putExtra(PreviewActivity.EXTRA_PDF_PATH, item.pdfPath)
                    putExtra(PreviewActivity.EXTRA_DOCX_PATH, item.docxPath)
                    putExtra(PreviewActivity.EXTRA_FORMAT, when {
                        item.pdfPath != null && item.docxPath != null -> "BOTH"
                        item.docxPath != null -> "DOCX"
                        else -> "PDF"
                    })
                })
                overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
            },
            onDelete = { item ->
                ReportLibrary.remove(this, item.id)
                Toast.makeText(this, R.string.report_deleted, Toast.LENGTH_SHORT).show()
                reload()
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val items = ReportLibrary.all(this)
        adapter.submit(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
}

class HistoryAdapter(
    private val onOpen: (LibraryItem) -> Unit,
    private val onDelete: (LibraryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private var data: List<LibraryItem> = emptyList()

    fun submit(list: List<LibraryItem>) {
        data = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.title.text = item.title
        holder.meta.text = "${item.mode.replace('_', ' ')} · ${item.date}"
        holder.btnOpen.setOnClickListener { onOpen(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.itemView.setOnClickListener { onOpen(item) }
    }

    override fun getItemCount() = data.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val meta: TextView = v.findViewById(R.id.tvMeta)
        val btnOpen: TextView = v.findViewById(R.id.btnOpen)
        val btnDelete: TextView = v.findViewById(R.id.btnDelete)
    }
}
