package com.atlasai.reportgenerator

object StyleCues {

    data class Cues(
        val headingSamples: List<String>,
        val usesNumberedHeadings: Boolean,
        val usesAllCapsHeadings: Boolean,
        val hasAbstract: Boolean,
        val hasObjectivesBullets: Boolean,
        val avgParagraphWords: Int,
        val notes: String
    )

    fun fromSample(sample: String?): Cues {
        if (sample.isNullOrBlank()) {
            return Cues(
                headingSamples = listOf("Abstract", "Introduction", "Conclusion"),
                usesNumberedHeadings = true,
                usesAllCapsHeadings = false,
                hasAbstract = true,
                hasObjectivesBullets = true,
                avgParagraphWords = 90,
                notes = "No sample — use formal academic headings."
            )
        }
        val lines = sample.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val headings = lines.filter { line ->
            line.length in 3..80 && (
                    line == line.uppercase() && line.any { it.isLetter() } ||
                            line.matches(Regex("""^\d+[\.\)]\s+\S.+""")) ||
                            line.endsWith(":") ||
                            (line.split(" ").size <= 8 && line[0].isUpperCase())
                    )
        }.distinct().take(20)

        val numbered = headings.any { it.matches(Regex("""^\d+[\.\)]\s+.*""")) }
        val allCaps = headings.count { it == it.uppercase() && it.any { c -> c.isLetter() } } >= 2
        val lower = sample.lowercase()
        val paras = sample.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.length > 40 }
        val avgWords = if (paras.isEmpty()) 90 else paras.map { it.split(Regex("""\s+""")).size }.average().toInt()

        val notes = buildString {
            append("Detected headings (preserve this naming style): ")
            append(headings.take(12).joinToString(" | ").ifBlank { "standard academic" })
            append(". ")
            if (numbered) append("Prefer numbered section headings. ")
            if (allCaps) append("Some headings appear in ALL CAPS — mirror that for main titles. ")
            append("Average paragraph length ≈ $avgWords words; keep paragraphs informative, not padded.")
        }

        return Cues(
            headingSamples = headings,
            usesNumberedHeadings = numbered,
            usesAllCapsHeadings = allCaps,
            hasAbstract = listOf("abstract", "executive summary", "summary").any { lower.contains(it) },
            hasObjectivesBullets = sample.contains(Regex("""(?m)^\s*[-•*]\s+""")) ||
                    sample.contains(Regex("""(?m)^\s*[a-z0-9][\.\)]\s+""")),
            avgParagraphWords = avgWords.coerceIn(40, 160),
            notes = notes
        )
    }
}
