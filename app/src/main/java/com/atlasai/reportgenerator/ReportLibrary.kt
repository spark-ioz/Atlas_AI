package com.atlasai.reportgenerator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryItem(
    val id: String,
    val title: String,
    val mode: String,
    val date: String,
    val content: String,
    val pdfPath: String?,
    val docxPath: String?
)

object ReportLibrary {
    private const val PREFS = "atlas_library"
    private const val KEY = "items"
    private const val MAX = 40

    fun add(context: Context, item: LibraryItem) {
        val list = all(context).toMutableList()
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.lastIndex)
        save(context, list)
    }

    fun all(context: Context): List<LibraryItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                LibraryItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    mode = o.optString("mode"),
                    date = o.optString("date"),
                    content = o.optString("content"),
                    pdfPath = o.optString("pdfPath").ifBlank { null },
                    docxPath = o.optString("docxPath").ifBlank { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filter { it.id != id })
    }

    private fun save(context: Context, list: List<LibraryItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("mode", item.mode)
                put("date", item.date)
                put("content", item.content.take(8000)) // keep prefs lean
                put("pdfPath", item.pdfPath ?: "")
                put("docxPath", item.docxPath ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
