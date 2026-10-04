package com.atlasai.reportgenerator

/**
 * Learns outline + front-page fields from sample PDF/DOCX.
 * Filters OCR garbage so words like "BOHNI" are not treated as chapter headings.
 */
object SampleStructure {

    data class Learned(
        val coverLines: List<String>,
        val headings: List<String>,
        val hasNumberedHeadings: Boolean,
        val estimatedPages: Int,
        val rawPreview: String
    )

    private val KNOWN = listOf(
        "abstract", "acknowledgement", "acknowledgment", "certificate", "declaration",
        "introduction", "problem statement", "objectives", "objective", "scope",
        "literature survey", "literature review", "background", "methodology",
        "materials required", "hardware requirements", "software requirements",
        "system design", "architecture", "system architecture", "block diagram",
        "implementation", "working procedure", "coding", "source code", "algorithm",
        "testing", "results", "result", "observation", "observations", "discussion",
        "advantages", "limitations", "applications", "future work", "future enhancements",
        "conclusion", "references", "bibliography", "appendix"
    )

    fun learn(sample: String?): Learned {
        if (sample.isNullOrBlank()) {
            return Learned(emptyList(), defaultHeadings(), true, 10, "")
        }

        val lines = sample.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Cover: first lines that look like institution/title page, cleaned
        val cover = lines.take(30)
            .map { cleanOcr(it) }
            .filter { it.length in 4..100 }
            .filter { !isGarbage(it) }
            .filter { !looksLikeBodyParagraph(it) }
            .take(10)

        val headings = mutableListOf<String>()
        for (line in lines) {
            val cleaned = cleanOcr(line)
            if (cleaned.length !in 3..80) continue
            if (isGarbage(cleaned)) continue
            if (looksLikeBodyParagraph(cleaned)) continue

            val asHeading = extractHeading(cleaned) ?: continue
            if (headings.none { it.equals(asHeading, ignoreCase = true) }) {
                headings += asHeading
            }
        }

        // If OCR polluted the outline, prefer clean academic defaults
        val realHeadings = headings.filter { h ->
            val low = h.lowercase()
            KNOWN.any { low.contains(it) } || h.matches(Regex("""^\d+(\.\d+)*\s+\S.+"""))
        }

        val finalHeadings = when {
            realHeadings.size >= 5 -> realHeadings.take(22)
            headings.count { it.split(" ").size >= 2 } >= 5 ->
                headings.filter { it.split(" ").size >= 2 }.take(22)
            else -> defaultHeadings()
        }

        val numbered = finalHeadings.any { it.matches(Regex("""^\d+.*""")) } ||
                lines.any { it.matches(Regex("""^\d+(\.\d+)*[\.\)]\s+\S.+""")) }
        val pages = (sample.length / 3200).coerceIn(5, 40)

        return Learned(cover, finalHeadings, numbered, pages, sample.take(1500))
    }

    private fun extractHeading(line: String): String? {
        // Numbered section: 1. Introduction
        val numbered = Regex("""^(\d+(\.\d+)*)[\.\)]\s+(.+)$""").find(line)
        if (numbered != null) {
            val body = numbered.groupValues[3].trim()
            if (body.split(" ").size >= 1 && !isGarbage(body)) return body
        }

        val low = line.lowercase()
        if (KNOWN.any { low == it || low.startsWith("$it ") || low.startsWith("$it:") }) {
            return line.trim().trimEnd(':')
        }

        // Title Case multi-word academic heading (not single token)
        val words = line.split(Regex("""\s+"""))
        if (words.size in 2..10 &&
            words.all { w -> w.firstOrNull()?.isUpperCase() == true || w.length <= 3 } &&
            line.length in 8..70 &&
            !line.contains(Regex("""\d{2,}""")) // avoid phone/reg numbers
        ) {
            // Reject if it looks like a college banner
            if (listOf("college", "university", "approved", "affiliated", "naac", "aicte", "autonomous")
                    .any { low.contains(it) }
            ) return null
            return line
        }

        return null
    }

    private fun cleanOcr(s: String): String {
        return s
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[|{}]"""), "")
            .trim()
    }

    /** OCR / banner garbage */
    private fun isGarbage(s: String): Boolean {
        val letters = s.count { it.isLetter() }
        if (letters < 3) return true
        // High non-letter ratio
        val ratio = letters.toDouble() / s.length.coerceAtLeast(1)
        if (ratio < 0.55) return true
        // Single ALLCAPS token (BOHNI, NAAG, etc.) unless known heading
        if (s.matches(Regex("""^[A-Z]{3,12}$""")) && s.lowercase() !in KNOWN) return true
        // Common OCR junk patterns
        if (Regex("""[a-z][A-Z]{2,}[a-z]""").containsMatchIn(s) && s.length < 40) {
            // mixed junk like "Proiect" is ok; extreme junk:
        }
        val low = s.lowercase()
        if (listOf("bohni", "echmology", "cautono", "naag", "graets", "coniscaon").any { low.contains(it) }) {
            return true
        }
        return false
    }

    private fun looksLikeBodyParagraph(s: String): Boolean {
        return s.length > 90 || s.count { it == ',' } >= 2 || s.endsWith(".")
    }

    fun defaultHeadings() = listOf(
        "Abstract",
        "Introduction",
        "Problem Statement",
        "Objectives",
        "Scope",
        "Literature Survey",
        "Materials Required",
        "System Design",
        "Implementation",
        "Coding",
        "Testing",
        "Results",
        "Discussion",
        "Advantages",
        "Limitations",
        "Future Enhancements",
        "Conclusion",
        "References"
    )
}
