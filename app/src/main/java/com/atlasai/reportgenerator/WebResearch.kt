package com.atlasai.reportgenerator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Heavy online research for report grounding.
 * Pulls Wikipedia extracts + related pages + DuckDuckGo notes.
 */
object WebResearch {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Pack(
        val query: String,
        val notes: String,
        val sources: List<String>,
        /** Structured chunks usable even without AI */
        val chunks: List<Chunk> = emptyList()
    )

    data class Chunk(
        val heading: String,
        val body: String,
        val source: String
    )

    suspend fun research(topic: String, keywords: List<String>): Pack = withContext(Dispatchers.IO) {
        coroutineScope {
            val query = (topic + " " + keywords.take(6).joinToString(" ")).trim()
            val chunks = mutableListOf<Chunk>()
            val sources = mutableListOf<String>()

            val wikiJob = async { collectWikipedia(query, keywords) }
            val ddgJob = async { duckDuckGoRich(query) }

            val wiki = wikiJob.await()
            chunks += wiki.first
            sources += wiki.second

            val ddg = ddgJob.await()
            if (ddg.first.isNotBlank()) {
                chunks += Chunk("Reference overview", ddg.first, "DuckDuckGo / related sources")
                sources += ddg.second
            }

            // Extra targeted wiki queries from title keywords
            for (kw in keywords.take(4)) {
                try {
                    val extra = collectWikipedia("$kw $topic", emptyList())
                    for (c in extra.first) {
                        if (chunks.none { it.heading == c.heading }) chunks += c
                    }
                    sources += extra.second
                } catch (_: Exception) { }
            }

            val notes = chunks.joinToString("\n\n") {
                "### ${it.heading}\n${it.body}\n(Source: ${it.source})"
            }.take(20000)

            Pack(
                query = query,
                notes = notes,
                sources = sources.distinct().take(15),
                chunks = chunks.distinctBy { it.heading + it.body.take(80) }.take(20)
            )
        }
    }

    private fun collectWikipedia(query: String, keywords: List<String>): Pair<List<Chunk>, List<String>> {
        val chunks = mutableListOf<Chunk>()
        val sources = mutableListOf<String>()
        val titles = linkedSetOf<String>()
        titles += wikiOpenSearch(query)
        keywords.take(3).forEach { titles += wikiOpenSearch("$it $query").take(2) }

        for (title in titles.take(6)) {
            val extract = wikiExtract(title) ?: continue
            if (extract.length < 80) continue
            chunks += Chunk(title, extract.take(3500), "Wikipedia: $title")
            sources += "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}"

            // sections if available via mobile sections API
            val sectionBodies = wikiSections(title)
            for ((name, body) in sectionBodies) {
                if (body.length > 100) {
                    chunks += Chunk("$title — $name", body.take(2500), "Wikipedia: $title § $name")
                }
            }
        }
        return chunks to sources
    }

    private fun wikiOpenSearch(q: String): List<String> {
        val url =
            "https://en.wikipedia.org/w/api.php?action=opensearch&limit=6&namespace=0&format=json&search=" +
                    URLEncoder.encode(q, "UTF-8")
        val body = httpGet(url) ?: return emptyList()
        val arr = JSONArray(body)
        if (arr.length() < 2) return emptyList()
        val titles = arr.getJSONArray(1)
        return (0 until titles.length()).map { titles.getString(it) }
    }

    /** Longer plain extract via REST summary + parse API */
    private fun wikiExtract(title: String): String? {
        // Try parse API for more text
        val parseUrl =
            "https://en.wikipedia.org/w/api.php?action=parse&page=" +
                    URLEncoder.encode(title, "UTF-8") +
                    "&prop=wikitext&format=json&formatversion=2"
        try {
            val raw = httpGet(parseUrl)
            if (raw != null) {
                val json = JSONObject(raw)
                val wikitext = json.optJSONObject("parse")?.optString("wikitext", "") ?: ""
                if (wikitext.length > 200) {
                    return cleanWikiText(wikitext).take(4000)
                }
            }
        } catch (_: Exception) { }

        val sumUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val sum = httpGet(sumUrl) ?: return null
        return JSONObject(sum).optString("extract", "").takeIf { it.isNotBlank() }
    }

    private fun wikiSections(title: String): List<Pair<String, String>> {
        val url =
            "https://en.wikipedia.org/api/rest_v1/page/mobile-sections/" +
                    URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val raw = httpGet(url) ?: return emptyList()
        return try {
            val json = JSONObject(raw)
            val out = mutableListOf<Pair<String, String>>()
            val remaining = json.optJSONArray("remaining") ?: JSONArray()
            for (i in 0 until minOf(remaining.length(), 8)) {
                val sec = remaining.optJSONObject(i) ?: continue
                val line = sec.optString("line", "Section")
                val text = cleanHtml(sec.optString("text", ""))
                if (text.length > 120) out += line to text
            }
            // lead
            val lead = json.optJSONObject("lead")
            val leadText = cleanHtml(lead?.optString("text", "") ?: "")
            if (leadText.length > 120) out.add(0, "Overview" to leadText)
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun duckDuckGoRich(q: String): Pair<String, List<String>> {
        val url = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=" +
                URLEncoder.encode(q, "UTF-8")
        val body = httpGet(url) ?: return "" to emptyList()
        val json = JSONObject(body)
        val sb = StringBuilder()
        val sources = mutableListOf<String>()
        val abs = json.optString("AbstractText", "")
        if (abs.isNotBlank()) {
            sb.append(abs).append("\n\n")
            val src = json.optString("AbstractURL", "")
            if (src.isNotBlank()) sources += src
        }
        val related = json.optJSONArray("RelatedTopics")
        if (related != null) {
            var n = 0
            fun addText(text: String, u: String?) {
                if (text.isBlank() || n >= 10) return
                sb.append("• ").append(text.take(400)).append("\n")
                if (!u.isNullOrBlank()) sources += u
                n++
            }
            for (i in 0 until related.length()) {
                val item = related.optJSONObject(i) ?: continue
                if (item.has("Topics")) {
                    val topics = item.getJSONArray("Topics")
                    for (j in 0 until topics.length()) {
                        val t = topics.optJSONObject(j) ?: continue
                        addText(t.optString("Text"), t.optString("FirstURL"))
                    }
                } else {
                    addText(item.optString("Text"), item.optString("FirstURL"))
                }
            }
        }
        return sb.toString().trim() to sources
    }

    private fun cleanWikiText(wt: String): String {
        var t = wt
        t = t.replace(Regex("""\{\{[^{}]*\}\}"""), " ")
        // crude multi-pass for nested templates
        repeat(3) { t = t.replace(Regex("""\{\{[^{}]*\}\}"""), " ") }
        t = t.replace(Regex("""\[\[([^|\]]*\|)?([^\]]+)\]\]"""), "$2")
        t = t.replace(Regex("""'''?"""), "")
        t = t.replace(Regex("""<ref[^>]*>.*?</ref>""", RegexOption.DOT_MATCHES_ALL), " ")
        t = t.replace(Regex("""<[^>]+>"""), " ")
        t = t.replace(Regex("""={2,}"""), "\n")
        t = t.replace(Regex("""\s+"""), " ")
        return t.trim()
    }

    private fun cleanHtml(html: String): String {
        var t = html.replace(Regex("""<script[\s\S]*?</script>"""), " ")
        t = t.replace(Regex("""<style[\s\S]*?</style>"""), " ")
        t = t.replace(Regex("""<[^>]+>"""), " ")
        t = t.replace(Regex("""&nbsp;|&#\d+;|&amp;|&lt;|&gt;|&quot;"""), " ")
        t = t.replace(Regex("""\s+"""), " ")
        return t.trim()
    }

    private fun httpGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AtlasAI-ReportStudio/1.1 (educational research)")
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
