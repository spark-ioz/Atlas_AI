package com.atlasai.reportgenerator

/**
 * Builds a solid report primarily from online research chunks.
 * Used when AI is unavailable, or as the factual backbone AI must stick to.
 */
object OnlineReportBuilder {

    fun build(
        title: String,
        research: WebResearch.Pack,
        pages: Int,
        mode: ReportMode
    ): String {
        val sb = StringBuilder()
        sb.appendLine(title.uppercase())
        sb.appendLine("REPORT")
        sb.appendLine()
        sb.appendLine("ABSTRACT")
        sb.appendLine()
        sb.appendLine(
            "This report presents a structured study of \"$title\". " +
                    "Content is organized from publicly available technical reference material " +
                    "and rewritten into an academic mini-project format. " +
                    "The sections below define the topic, related concepts, organization, " +
                    "practical considerations, and conclusions specific to \"$title\"."
        )
        sb.appendLine()

        sb.appendLine("INTRODUCTION")
        sb.appendLine()
        sb.appendLine(
            "The subject of this document is exactly \"$title\". " +
                    "The following material introduces definitions, context, and technical ideas " +
                    "drawn from online reference sources and arranged for academic reading."
        )
        sb.appendLine()

        // Map research chunks into body sections
        val chunks = research.chunks.ifEmpty {
            research.notes.split(Regex("""\n###\s+"""))
                .mapNotNull { block ->
                    val lines = block.trim().lines()
                    if (lines.isEmpty()) return@mapNotNull null
                    val h = lines.first().removePrefix("###").trim()
                    val b = lines.drop(1).joinToString(" ").trim()
                    if (b.length < 60) null else WebResearch.Chunk(h, b, "")
                }
        }

        val used = linkedSetOf<String>()
        fun addSection(heading: String, bodies: List<String>) {
            val text = bodies.filter { it.length > 40 }.joinToString("\n\n")
            if (text.isBlank()) return
            sb.appendLine(heading.uppercase())
            sb.appendLine()
            sb.appendLine(text.take(4500))
            sb.appendLine()
        }

        // Primary online content
        if (chunks.isNotEmpty()) {
            addSection(
                "Background and Online Reference Material",
                chunks.take(4).map { "${it.heading}: ${it.body}" }
            )
            addSection(
                "Core Technical Content Related to the Title",
                chunks.drop(4).take(5).map { "${it.heading}\n${it.body}" }
            )
            addSection(
                "Extended Discussion from Sources",
                chunks.drop(9).take(5).map { it.body }
            )
        } else {
            addSection(
                "Background",
                listOf(research.notes.take(3000).ifBlank {
                    "Online sources for \"$title\" should be consulted for definitions, architecture, and applications."
                })
            )
        }

        sb.appendLine("PROBLEM STATEMENT")
        sb.appendLine()
        sb.appendLine(
            "Understanding \"$title\" requires connecting theory with a clear operational model. " +
                    "Students often know high-level definitions but need organized detail about components, " +
                    "data paths, control behavior, and verification. This report addresses that gap for \"$title\"."
        )
        sb.appendLine()

        sb.appendLine("OBJECTIVES")
        sb.appendLine()
        sb.appendLine("1. Explain the meaning and importance of \"$title\".")
        sb.appendLine("2. Organize reference concepts that support \"$title\".")
        sb.appendLine("3. Describe practical structure, procedure, and evaluation aspects.")
        sb.appendLine("4. Summarize advantages, limitations, and possible extensions.")
        sb.appendLine()

        sb.appendLine("METHODOLOGY")
        sb.appendLine()
        sb.appendLine(
            "The methodology combines topic-focused online research with academic structuring. " +
                    "Reference extracts related to \"$title\" were collected, cleaned, and arranged under " +
                    "standard report headings so the narrative stays on the assigned title."
        )
        sb.appendLine()

        // Pad toward page target with more chunk text if available
        var chars = sb.length
        val target = pages * 2200
        var i = 0
        while (chars < target && i < chunks.size) {
            val c = chunks[i++]
            val key = c.heading
            if (key in used) continue
            used += key
            sb.appendLine(c.heading.uppercase())
            sb.appendLine()
            sb.appendLine(c.body.take(3000))
            sb.appendLine()
            chars = sb.length
        }

        sb.appendLine("CONCLUSION")
        sb.appendLine()
        sb.appendLine(
            "This report stayed focused on \"$title\". Online reference material was used to build " +
                    "background and technical discussion, then arranged into a readable academic structure. " +
                    "Further laboratory or simulation work can extend the same title with implementation evidence."
        )
        sb.appendLine()

        if (research.sources.isNotEmpty()) {
            sb.appendLine("REFERENCES")
            sb.appendLine()
            research.sources.forEachIndexed { idx, s ->
                sb.appendLine("${idx + 1}. $s")
            }
        }

        return sb.toString().trim()
    }
}
