package com.atlasai.reportgenerator

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Headings come from sample. CONTENT is generated per heading strictly for the user TITLE.
 * Avoids dumping unrelated Wikipedia text under the right headings.
 */
object AiClient {

    private const val CHARS_PER_A4 = 3200

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun generateReport(
        context: Context,
        sampleText: String?,
        question: String,
        mode: ReportMode,
        targetPages: Int?
    ): String = withContext(Dispatchers.IO) {
        val title = question.trim()
        require(title.length >= 3) { "Enter a clear title." }

        val learned = SampleStructure.learn(sampleText)
        val pages = targetPages?.coerceIn(3, 40) ?: learned.estimatedPages.coerceIn(8, 20)
        val targetChars = pages * CHARS_PER_A4
        val domain = detectDomain(title, sampleText)
        val headings = learned.headings.ifEmpty { SampleStructure.defaultHeadings() }
        val perSection = (targetChars / headings.size.coerceAtLeast(1)).coerceIn(700, 4500)

        val provider = AiSettings.getProvider(context)
        val apiKey = AiSettings.getApiKey(context)
        val useAi = provider != AiProvider.LOCAL && apiKey.isNotBlank()
        val model = if (useAi) AiSettings.getModel(context).ifBlank { defaultModel(provider) } else ""

        val sb = StringBuilder()
        sb.appendLine(buildCover(title, learned).trim())
        sb.appendLine()

        val sources = linkedSetOf<String>()
        val titleKeys = titleKeywords(title)

        for ((index, heading) in headings.withIndex()) {
            // Research ONLY for this heading + this title + domain
            val research = try {
                WebResearch.research(
                    "$title $heading $domain",
                    titleKeys + headingKeywords(heading)
                )
            } catch (_: Exception) {
                WebResearch.Pack("$title $heading", "", emptyList(), emptyList())
            }
            sources += research.sources

            // Keep only research text that actually mentions title keywords or heading idea
            val filteredNotes = filterRelevant(research.notes, titleKeys, heading)
            val filteredChunks = research.chunks.filter { c ->
                isRelevant(c.body + " " + c.heading, titleKeys, heading)
            }

            val label = if (learned.hasNumberedHeadings) "${index + 1}. $heading" else heading.uppercase()
            sb.appendLine(label)
            sb.appendLine()

            val sectionBody = if (useAi) {
                try {
                    val prompt = """
Write ONLY the content for this one section of a student mini-project report.

REPORT TITLE: "$title"
DOMAIN: $domain
SECTION HEADING: "$heading"
LENGTH: about ${perSection / 6} words.

WHAT THIS SECTION MUST DO:
${sectionInstruction(heading, title)}

CRITICAL:
- Content must be about "$title" only ($domain meaning).
- Do NOT write about a different subject that only shares one common word.
- Do NOT copy irrelevant encyclopedia text about unrelated topics.
- If the heading is Coding/Implementation, give pseudocode or steps for "$title".
- If Materials/Requirements, list items needed for "$title".
- If Introduction, introduce "$title" only.

OPTIONAL RESEARCH (use only if it clearly matches "$title"; ignore the rest):
${filteredNotes.take(5000).ifBlank { "(no reliable web match — use correct $domain knowledge for \"$title\")" }}

Output the section body only (you may include the heading line once).
""".trimIndent()

                    val ai = stripPreamble(
                        chat(provider, apiKey, model, systemFor(title, heading, domain), prompt)
                    )
                    delay(350)
                    if (ai.length > 80 && isRelevant(ai, titleKeys, heading)) {
                        ai
                    } else {
                        // Second try: stricter
                        val retry = stripPreamble(
                            chat(
                                provider, apiKey, model, systemFor(title, heading, domain),
                                """Write the "$heading" section for a report titled "$title".
Only about "$title" ($domain). About ${perSection / 6} words. No unrelated topics."""
                            )
                        )
                        delay(300)
                        if (retry.length > 80) retry else fallbackSection(title, heading, filteredChunks, filteredNotes, perSection)
                    }
                } catch (_: Exception) {
                    fallbackSection(title, heading, filteredChunks, filteredNotes, perSection)
                }
            } else {
                fallbackSection(title, heading, filteredChunks, filteredNotes, perSection)
            }

            sb.appendLine(sectionBody.trim())
            sb.appendLine()
            sb.appendLine()
        }

        if (sources.isNotEmpty()) {
            sb.appendLine(if (learned.hasNumberedHeadings) "${headings.size + 1}. References" else "REFERENCES")
            sb.appendLine()
            sources.take(15).forEachIndexed { i, s -> sb.appendLine("${i + 1}. $s") }
        }

        var report = sb.toString().trim()

        // Global relevance fix if whole report barely mentions title
        if (useAi && titleKeys.count { report.lowercase().contains(it.lowercase()) } < 2) {
            try {
                report = stripPreamble(
                    chat(
                        provider, apiKey, model, systemFor(title, "full", domain),
                        """Rewrite this report so every section is clearly about "$title" ($domain).
Keep the same headings. Remove off-topic paragraphs. Return the full report.

${report.take(14000)}"""
                    )
                )
            } catch (_: Exception) { }
        }

        report.trim()
    }

    suspend fun generateFromWebOnly(
        title: String,
        targetPages: Int?,
        mode: ReportMode,
        sampleText: String? = null
    ): String = withContext(Dispatchers.IO) {
        // Reuse main path without AI by calling logic with empty key path
        val learned = SampleStructure.learn(sampleText)
        val pages = targetPages?.coerceIn(3, 40) ?: 12
        val domain = detectDomain(title, sampleText)
        val headings = learned.headings
        val per = (pages * CHARS_PER_A4 / headings.size.coerceAtLeast(1)).coerceIn(700, 4500)
        val titleKeys = titleKeywords(title)
        val sb = StringBuilder()
        sb.appendLine(buildCover(title, learned))
        sb.appendLine()
        val sources = linkedSetOf<String>()
        for ((i, h) in headings.withIndex()) {
            val pack = try {
                WebResearch.research("$title $h $domain", titleKeys + headingKeywords(h))
            } catch (_: Exception) {
                WebResearch.Pack("", "", emptyList(), emptyList())
            }
            sources += pack.sources
            val notes = filterRelevant(pack.notes, titleKeys, h)
            val chunks = pack.chunks.filter { isRelevant(it.body, titleKeys, h) }
            sb.appendLine(if (learned.hasNumberedHeadings) "${i + 1}. $h" else h.uppercase())
            sb.appendLine()
            sb.appendLine(fallbackSection(title, h, chunks, notes, per))
            sb.appendLine()
        }
        if (sources.isNotEmpty()) {
            sb.appendLine("REFERENCES")
            sources.forEachIndexed { i, s -> sb.appendLine("${i + 1}. $s") }
        }
        sb.toString().trim()
    }

    private fun sectionInstruction(heading: String, title: String): String {
        val h = heading.lowercase()
        return when {
            h.contains("abstract") || h.contains("summary") ->
                "Short overview of what \"$title\" is and what the report covers."
            h.contains("intro") ->
                "Define \"$title\", why it matters, and what the reader will learn."
            h.contains("problem") ->
                "State the problem that \"$title\" addresses."
            h.contains("objective") ->
                "Bullet list of objectives for studying/building \"$title\"."
            h.contains("scope") ->
                "What is included and excluded in this work on \"$title\"."
            h.contains("literature") || h.contains("background") || h.contains("survey") ->
                "Background concepts required to understand \"$title\" only."
            h.contains("material") || h.contains("hardware") || h.contains("software") || h.contains("requirement") ->
                "List materials/tools/software required to implement or simulate \"$title\"."
            h.contains("design") || h.contains("architecture") ->
                "Architecture/design of \"$title\": blocks, signals, flow."
            h.contains("method") ->
                "Step-by-step method used for \"$title\"."
            h.contains("implement") || h.contains("working") || h.contains("procedure") ->
                "How \"$title\" is implemented or simulated in practice."
            h.contains("coding") || h.contains("source code") || h.contains("program") ->
                "Pseudocode or structured steps/code related to \"$title\" only."
            h.contains("test") ->
                "How to test \"$title\" and what to observe."
            h.contains("result") || h.contains("observation") ->
                "Expected results/observations for \"$title\"."
            h.contains("discussion") ->
                "Interpret results and design choices for \"$title\"."
            h.contains("advantage") ->
                "Advantages of \"$title\" / this approach."
            h.contains("limitation") || h.contains("disadvantage") ->
                "Limitations of \"$title\"."
            h.contains("application") ->
                "Real uses of \"$title\"."
            h.contains("future") ->
                "Future enhancements to \"$title\"."
            h.contains("conclusion") ->
                "Conclude only on \"$title\"."
            h.contains("reference") ->
                "References related to \"$title\"."
            else ->
                "Explain \"$heading\" specifically in the context of \"$title\"."
        }
    }

    private fun fallbackSection(
        title: String,
        heading: String,
        chunks: List<WebResearch.Chunk>,
        notes: String,
        perSection: Int
    ): String {
        // Keyless mode must still produce topic-specific, readable report prose.
        // Older versions started almost every topic with the same domain paragraph;
        // this version builds the section from the heading purpose + researched facts.
        val keys = titleKeywords(title)
        val domain = detectDomain(title, null)
        val research = selectResearchSentences(title, heading, chunks, notes)
        val sb = StringBuilder()

        // Keep the existing high-quality domain material only when it actually matches
        // the detected project domain. This prevents bus/Logisim text appearing in
        // unrelated reports such as AI, DBMS, networking, or web projects.
        val specialized = domain != "computer science engineering" ||
                title.lowercase().contains("computer science")
        if (specialized) {
            val core = DomainContent.paragraph(title, heading, domain)
            if (core.isNotBlank()) sb.append(core.trim())
        } else {
            sb.append(sectionLead(title, heading))
        }

        if (research.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(research.joinToString("\n\n"))
        }

        // Add a concise interpretation paragraph so the section reads like a report,
        // not a pasted search result.
        val bridge = sectionBridge(title, heading, keys, research)
        if (bridge.isNotBlank() && !sb.contains(bridge.take(45))) {
            sb.append("\n\n").append(bridge)
        }

        return sb.toString().replace(Regex("\\s+"), " ").trim().take(perSection)
    }

    private fun sectionLead(title: String, heading: String): String {
        val h = heading.lowercase()
        return when {
            h.contains("abstract") || h.contains("summary") ->
                "This report examines $title and explains its purpose, working principles, implementation approach, and practical relevance. The section connects the topic to the objectives of the project and establishes the context for the detailed discussion that follows."
            h.contains("intro") ->
                "$title is the central subject of this project. The topic is examined from a practical and technical perspective, with attention to the problem it addresses, the concepts required to understand it, and the way those concepts are applied in the proposed work."
            h.contains("problem") ->
                "The problem addressed by $title can be understood by identifying the need, the limitations of existing approaches, and the specific gap that the project attempts to address. A clear problem definition also provides measurable criteria for evaluating the proposed solution."
            h.contains("objective") ->
                "The objectives of $title are focused on understanding the core concept, designing a suitable solution, implementing the required workflow, and evaluating the resulting behaviour. These objectives keep the project focused on demonstrable outcomes rather than only theoretical discussion."
            h.contains("scope") ->
                "The scope of $title defines the concepts and practical activities covered by the project. It establishes what the implementation is expected to demonstrate while separating those goals from advanced features that are outside the present academic work."
            h.contains("method") || h.contains("procedure") ->
                "The methodology for $title follows a structured sequence: understand the requirements, identify the relevant concepts, design the solution, implement the main components, test representative cases, and evaluate the observations against the project objectives."
            h.contains("implement") || h.contains("working") ->
                "The implementation of $title converts the project requirements into a sequence of concrete components and operations. Each stage has a defined purpose so that the final system can be developed, tested, and explained systematically."
            h.contains("result") || h.contains("observation") ->
                "The results for $title should be interpreted against the expected behaviour defined during the design and testing stages. Observations should identify what worked correctly, where limitations appeared, and what evidence supports the stated outcome."
            h.contains("discussion") ->
                "The discussion of $title interprets the findings rather than simply repeating them. It considers why the observed behaviour occurred, how the design decisions affected the result, and what the findings imply for practical use or further development."
            h.contains("limitation") || h.contains("disadvantage") ->
                "The limitations of $title depend on the assumptions, resources, data, tools, and implementation boundaries used in the project. Identifying these constraints makes the report more realistic and clarifies which conclusions should not be generalized beyond the tested conditions."
            h.contains("advantage") ->
                "The advantages of $title are best evaluated in relation to the problem being solved. The important benefits include the aspects that improve usability, efficiency, clarity, reliability, scalability, or learning value within the chosen project scope."
            h.contains("application") ->
                "The applications of $title depend on how its underlying concepts can be transferred from the academic implementation to practical situations. The most relevant uses are those that match the capabilities demonstrated by the project."
            h.contains("future") ->
                "Future development of $title can focus on improving capability, reliability, usability, scalability, and real-world integration. The most useful extensions should address limitations observed during implementation and testing."
            h.contains("conclusion") ->
                "The project on $title brings together the concepts, implementation decisions, and observations discussed in the preceding sections. The conclusion should therefore summarize the evidence and the main learning outcome without introducing unrelated material."
            else ->
                "This section explains $heading in the specific context of $title. The discussion focuses on the role of this aspect in the project, the important technical considerations, and the way it contributes to the overall objective."
        }
    }

    private fun selectResearchSentences(
        title: String,
        heading: String,
        chunks: List<WebResearch.Chunk>,
        notes: String
    ): List<String> {
        val keys = titleKeywords(title).map { it.lowercase() }
        val headingKeys = headingKeywords(heading).map { it.lowercase() }
        val candidates = mutableListOf<Pair<Int, String>>()

        val bodies = chunks.map { it.body } + notes
        for (body in bodies) {
            val sentences = body
                .replace(Regex("\\s+"), " ")
                .split(Regex("(?<=[.!?])\\s+"))
                .map { it.trim() }
                .filter { it.length in 70..650 }
            for (sentence in sentences) {
                val low = sentence.lowercase()
                val keyHits = keys.count { low.contains(it) }
                val headingHits = headingKeys.count { low.contains(it) }
                if (keyHits == 0 && headingHits == 0) continue
                if (low.contains("cookie") || low.contains("privacy policy") || low.contains("sign in")) continue
                val score = keyHits * 5 + headingHits * 2 + sentence.length.coerceAtMost(300) / 100
                candidates += score to sentence
            }
        }

        return candidates
            .sortedByDescending { it.first }
            .map { it.second.replace(Regex("\\s+"), " ").trim() }
            .distinctBy { it.lowercase().replace(Regex("[^a-z0-9]"), "").take(120) }
            .take(4)
    }

    private fun sectionBridge(title: String, heading: String, keys: List<String>, research: List<String>): String {
        if (research.isEmpty()) return "The researched points above should be read in relation to $title and the specific purpose of the $heading section. They provide supporting context while the project implementation remains the primary basis for the report."
        return "Taken together, these points show how the researched concepts relate to $title. For this project, the important consideration is how the general concept is translated into the specific design, implementation, or evaluation described in the report."
    }

    private fun filterRelevant(notes: String, titleKeys: List<String>, heading: String): String {
        if (notes.isBlank()) return ""
        val blocks = notes.split(Regex("""\n###\s+"""))
        val kept = blocks.filter { isRelevant(it, titleKeys, heading) }
        return kept.joinToString("\n\n").ifBlank {
            // If nothing matches tightly, still allow short snippets that contain any title key
            blocks.filter { b ->
                titleKeys.any { b.contains(it, true) }
            }.joinToString("\n\n")
        }.take(6000)
    }

    private fun isRelevant(text: String, titleKeys: List<String>, heading: String): Boolean {
        if (text.length < 40) return false
        val t = text.lowercase()
        val keyHits = titleKeys.count { t.contains(it.lowercase()) }
        val headHits = headingKeywords(heading).count { t.contains(it.lowercase()) }
        // Need some link to title OR (heading + at least one title key)
        return keyHits >= 1 || (headHits >= 1 && keyHits >= 1) || keyHits + headHits >= 2
    }

    private fun headingKeywords(heading: String): List<String> =
        heading.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s-]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 3 }
            .distinct()
            .take(6)

    private fun buildCover(title: String, learned: SampleStructure.Learned): String {
        val sb = StringBuilder()
        sb.appendLine(title.uppercase())
        sb.appendLine()
        sb.appendLine("MINI PROJECT REPORT")
        sb.appendLine()
        sb.appendLine("Title: $title")
        sb.appendLine()
        // Keep only clean cover lines (student/college); skip OCR junk already filtered in SampleStructure
        val clean = learned.coverLines.filter { line ->
            val low = line.lowercase()
            line.length in 5..90 &&
                !low.contains("front page") &&
                !low.startsWith("###") &&
                line.split(" ").size >= 2
        }.take(8)
        if (clean.isNotEmpty()) {
            sb.appendLine("(Edit the lines below in DOCX — from your sample)")
            clean.forEach { sb.appendLine(it) }
        } else {
            sb.appendLine("Department of Computer Science and Engineering")
            sb.appendLine("Submitted by: [Student Name]")
            sb.appendLine("Register No: [Register Number]")
            sb.appendLine("Guided by: [Guide Name]")
            sb.appendLine("Year / Semester: [Year / Semester]")
        }
        sb.appendLine()
        return sb.toString()
    }

    private fun detectDomain(title: String, sample: String?): String {
        val blob = ((sample ?: "") + " " + title).lowercase()
        return when {
            listOf("logisim", "memory-mapped", "mmio", "seven-segment", "led", "fpga", "verilog", "vhdl", "decoder")
                .any { blob.contains(it) } -> "digital logic computer architecture simulation"
            listOf("bus arbitration", "system bus", "address bus", "data bus")
                .any { blob.contains(it) } -> "computer architecture system bus"
            title.lowercase().contains("bus") && title.lowercase().contains("simulat") ->
                "computer architecture digital system bus simulation"
            listOf("network", "tcp", "osi", "routing").any { blob.contains(it) } -> "computer networks"
            else -> "computer science engineering"
        }
    }

    private fun titleKeywords(text: String): List<String> {
        val stop = setOf(
            "a", "an", "the", "and", "or", "of", "in", "on", "for", "with", "to", "using",
            "report", "mini", "project", "study", "based", "about"
        )
        return text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s-]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 2 && it !in stop }
            .distinct()
            .take(12)
    }

    private fun systemFor(title: String, heading: String, domain: String) =
        """You write one report section. Title="$title". Heading="$heading". Domain=$domain.
Wrong content is worse than short content. Stay on title."""

    private fun defaultModel(provider: AiProvider) = when (provider) {
        AiProvider.OPENAI -> "gpt-4o-mini"
        AiProvider.GEMINI -> "gemini-2.0-flash"
        AiProvider.LOCAL -> ""
    }

    private fun stripPreamble(text: String): String {
        var t = text.trim()
        for (p in listOf("Sure", "Certainly", "Here is", "Here's", "Below is", "Of course")) {
            if (t.startsWith(p, ignoreCase = true)) {
                val nl = t.indexOf('\n')
                if (nl > 0) t = t.substring(nl + 1).trim()
            }
        }
        return t.trim()
    }

    private fun chat(
        provider: AiProvider,
        apiKey: String,
        model: String,
        system: String,
        user: String
    ): String = when (provider) {
        AiProvider.OPENAI -> callOpenAi(apiKey, model, system, user)
        AiProvider.GEMINI -> callGemini(apiKey, model, system, user)
        AiProvider.LOCAL -> throw IllegalStateException("Local")
    }

    private fun callOpenAi(apiKey: String, model: String, system: String, user: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.25)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(parseError("OpenAI", response.code, raw))
            return JSONObject(raw)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
                .ifBlank { throw IllegalStateException("OpenAI empty") }
        }
    }

    private fun callGemini(apiKey: String, model: String, system: String, user: String): String {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "SYSTEM:\n$system\n\nUSER:\n$user"))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.25).put("maxOutputTokens", 4096)
            )
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(parseError("Gemini", response.code, raw))
            val parts = JSONObject(raw)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            return sb.toString().trim().ifBlank { throw IllegalStateException("Gemini empty") }
        }
    }

    private fun parseError(name: String, code: Int, raw: String): String {
        return try {
            val err = JSONObject(raw)
            val msg = when (val e = err.opt("error")) {
                is JSONObject -> e.optString("message", e.toString())
                else -> raw.take(240)
            }
            "$name error $code: $msg"
        } catch (_: Exception) {
            "$name error $code: ${raw.take(240)}"
        }
    }
}
