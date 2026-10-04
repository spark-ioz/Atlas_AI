package com.atlasai.reportgenerator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Long-form report engine.
 * - Infers or accepts a target page count
 * - Expands sections until approximate page budget is met (~2800 chars/page)
 * - Uses topic keywords to drive deeper, domain-aware content
 */
object ReportEngine {

    private const val CHARS_PER_PAGE = 2800

    fun generate(
        sampleReport: String?,
        question: String,
        mode: ReportMode,
        targetPages: Int? = null
    ): String {
        val style = resolveStyle(sampleReport, mode)
        val topic = extractTopic(question)
        val keywords = extractKeywords(question)
        val pages = resolvePageTarget(sampleReport, targetPages)
        val sections = buildSections(style, topic, question, keywords, mode, sampleReport, pages)
        return assembleReport(style, topic, sections, mode, pages)
    }

    private data class StyleProfile(
        val hasTitle: Boolean,
        val hasDate: Boolean,
        val hasExecutiveSummary: Boolean,
        val hasNumberedSections: Boolean,
        val hasBulletPoints: Boolean,
        val hasConclusion: Boolean,
        val hasSignature: Boolean,
        val formalTone: Boolean,
        val useSubheadings: Boolean
    )

    private fun resolvePageTarget(sample: String?, requested: Int?): Int {
        requested?.takeIf { it in 3..40 }?.let { return it }
        if (!sample.isNullOrBlank()) {
            // ~2800 chars ≈ 1 page of formal report text
            val estimate = (sample.length / CHARS_PER_PAGE).coerceIn(5, 25)
            return estimate
        }
        return 10 // default solid report length
    }

    private fun resolveStyle(sample: String?, mode: ReportMode): StyleProfile {
        return when {
            !sample.isNullOrBlank() && mode == ReportMode.STYLE_CLONE -> analyzeStyle(sample)
            mode == ReportMode.EXECUTIVE -> presetExecutive()
            mode == ReportMode.RESEARCH -> presetResearch()
            mode == ReportMode.CREATIVE -> presetCreative()
            !sample.isNullOrBlank() -> analyzeStyle(sample)
            else -> presetResearch()
        }
    }

    private fun presetExecutive() = StyleProfile(
        true, true, true, false, true, true, true, true, true
    )
    private fun presetResearch() = StyleProfile(
        true, true, true, true, false, true, false, true, true
    )
    private fun presetCreative() = StyleProfile(
        true, false, false, false, true, true, false, false, false
    )

    private fun analyzeStyle(sample: String): StyleProfile {
        val lines = sample.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lower = sample.lowercase(Locale.getDefault())
        return StyleProfile(
            hasTitle = lines.isNotEmpty() && lines.first().length < 140,
            hasDate = lower.contains(Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""")) ||
                    lower.contains(Regex("""(january|february|march|april|may|june|july|august|september|october|november|december)""")),
            hasExecutiveSummary = listOf("executive summary", "summary", "overview", "abstract")
                .any { lower.contains(it) },
            hasNumberedSections = sample.contains(Regex("""(?m)^\s*\d+[\.\)]\s+\S""")),
            hasBulletPoints = sample.contains(Regex("""(?m)^\s*[-•*▪]\s+""")),
            hasConclusion = listOf("conclusion", "recommendation", "closing", "discussion")
                .any { lower.contains(it) },
            hasSignature = listOf("prepared by", "submitted by", "sincerely", "regards")
                .any { lower.contains(it) },
            formalTone = true,
            useSubheadings = true
        )
    }

    private fun extractTopic(question: String): String {
        var topic = question
            .replace(Regex("""(?i)^(write|create|generate|prepare|make|draft|produce)\s+(a|an|the)?\s*"""), "")
            .replace(Regex("""(?i)\s*(report|analysis|document|summary|brief|paper)\s*(about|on|for|regarding|of)?\s*"""), " ")
            .trim()
        if (topic.isBlank()) topic = question
        return topic.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }.take(140)
    }

    private fun extractKeywords(question: String): List<String> {
        val stop = setOf(
            "a", "an", "the", "and", "or", "for", "of", "on", "in", "to", "with", "about",
            "write", "create", "generate", "prepare", "make", "draft", "report", "analysis",
            "document", "summary", "brief", "please", "focusing", "focus", "regarding", "using"
        )
        return question.lowercase(Locale.getDefault())
            .replace(Regex("""[^\p{L}\p{N}\s-]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 2 && it !in stop }
            .distinct()
            .take(12)
    }

    private fun buildSections(
        style: StyleProfile,
        topic: String,
        question: String,
        keywords: List<String>,
        mode: ReportMode,
        sample: String?,
        targetPages: Int
    ): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        val kw = keywords.take(5).joinToString(", ").ifBlank { topic }
        val sampleCue = sample?.let { firstSentences(it, 3) }.orEmpty()
        val domain = DomainKnowledge.forTopic(topic, keywords)

        // Core sections always present
        if (style.hasExecutiveSummary) {
            val label = when (mode) {
                ReportMode.RESEARCH -> "Abstract"
                ReportMode.CREATIVE -> "Overview"
                else -> "Executive Summary"
            }
            sections.add(label to expand(execSummary(topic, question, kw, mode, sampleCue), domain, 1))
        }

        sections.add("Introduction" to expand(introduction(topic, question, mode), domain, 1))
        sections.add("Problem Statement" to expand(problemStatement(topic, question), domain, 1))
        sections.add("Objectives" to objectives(topic, keywords, style))
        sections.add("Scope of the Study" to expand(scope(topic, kw), domain, 1))

        // Domain / literature style expansion
        sections.add("Background and Literature Context" to expand(background(topic, kw), domain, 2))
        sections.add("Core Concepts" to expand(coreConcepts(topic, keywords, domain), domain, 2))

        when (mode) {
            ReportMode.RESEARCH, ReportMode.STYLE_CLONE -> {
                sections.add("System Architecture / Organization" to expand(architecture(topic, domain), domain, 2))
                sections.add("Methodology" to expand(methodology(topic), domain, 1))
                sections.add("Design and Implementation" to expand(designImpl(topic, domain), domain, 2))
                sections.add("Working Procedure" to expand(procedure(topic, domain), domain, 1))
                sections.add("Testing and Validation" to expand(testing(topic, domain), domain, 1))
                sections.add("Results and Observation" to expand(results(topic, domain), domain, 1))
                sections.add("Discussion" to expand(discussion(topic, kw, domain), domain, 1))
                sections.add("Advantages" to listBlock(advantages(topic, domain), style))
                sections.add("Limitations" to listBlock(limitationsList(topic), style))
                sections.add("Applications" to expand(applications(topic, domain), domain, 1))
                sections.add("Future Enhancements" to listBlock(futureWork(topic, domain), style, numbered = true))
            }
            ReportMode.EXECUTIVE -> {
                sections.add("Key Findings" to listBlock(findingsList(topic, domain), style))
                sections.add("Detailed Analysis" to expand(discussion(topic, kw, domain), domain, 2))
                sections.add("Strategic Context" to expand(background(topic, kw), domain, 1))
                sections.add("Opportunities and Risks" to listBlock(opportunitiesList(topic, domain), style))
                sections.add("Implementation Considerations" to expand(designImpl(topic, domain), domain, 1))
            }
            ReportMode.CREATIVE -> {
                sections.add("The Narrative" to expand(introduction(topic, question, mode), domain, 1))
                sections.add("What Stands Out" to listBlock(findingsList(topic, domain), style))
                sections.add("Creative Levers" to listBlock(opportunitiesList(topic, domain), style))
                sections.add("Audience Impact" to expand(applications(topic, domain), domain, 1))
            }
        }

        sections.add("Recommendations" to listBlock(recommendationsList(topic, domain), style, numbered = true))
        sections.add("Conclusion" to expand(conclusion(topic, mode), domain, 1))

        // Pad to page budget with extra deep-dive sections if still short
        var currentChars = sections.sumOf { it.first.length + it.second.length }
        val budget = targetPages * CHARS_PER_PAGE
        var extra = 0
        while (currentChars < budget * 0.85 && extra < 8) {
            extra++
            val title = when (extra) {
                1 -> "Extended Technical Notes"
                2 -> "Comparative Perspective"
                3 -> "Educational Value and Learning Outcomes"
                4 -> "Practical Considerations and Best Practices"
                5 -> "Case-Oriented Walkthrough"
                6 -> "Integration with Related Topics"
                7 -> "Summary of Critical Design Choices"
                else -> "Additional Observations ($extra)"
            }
            val body = expand(extraSection(topic, domain, extra), domain, 2)
            sections.add(title to body)
            currentChars += title.length + body.length
        }

        return sections
    }

    // ── Content builders ─────────────────────────────────────────────

    private fun execSummary(topic: String, q: String, kw: String, mode: ReportMode, cue: String): String {
        return "This report presents a structured study of $topic, with particular attention to $kw. " +
                "It responds to the guiding requirement: \"$q\". " +
                "The work connects theoretical foundations with practical demonstration, " +
                "covering motivation, design decisions, procedure, validation, and outcomes. " +
                (if (cue.isNotBlank()) "The presentation is aligned with patterns observed in the reference sample material. " else "") +
                "Readers are provided with clear objectives, a defined scope, and actionable conclusions " +
                "suitable for academic evaluation and further development."
    }

    private fun introduction(topic: String, q: String, mode: ReportMode): String {
        return "This report is written specifically on the title: \"$q\". " +
                "All sections that follow develop that title only — definitions, motivation, design ideas, " +
                "working steps, testing, and conclusions are tied to $topic. " +
                "The aim is to explain what the title means, why it matters in computer organization, " +
                "and how the ideas can be demonstrated in a practical or simulated environment. " +
                "Readers should be able to relate every major paragraph back to the stated title rather than " +
                "to a generic project template."
    }

    private fun problemStatement(topic: String, q: String): String {
        return "Learners and practitioners often understand high-level descriptions of $topic but struggle to visualize " +
                "how addresses, data paths, and control signals cooperate in a working system. " +
                "Without a concrete model, the relationship between theory and circuit-level behavior remains opaque. " +
                "The problem addressed here is therefore to explain, organize, and demonstrate $topic in a form that " +
                "answers \"$q\" while remaining suitable for laboratory or simulation-based verification. " +
                "The solution must show device selection, data transfer direction, and the role of decoding logic " +
                "in a clear and reproducible manner."
    }

    private fun objectives(topic: String, keywords: List<String>, style: StyleProfile): String {
        val items = mutableListOf(
            "To understand the fundamental concepts underlying $topic.",
            "To identify the architectural components required for a working demonstration.",
            "To describe address assignment, decoding, and device register behavior.",
            "To illustrate read and write data paths with concrete examples.",
            "To validate the design through systematic test cases.",
            "To document advantages, limitations, and realistic extensions.",
            "To connect classroom theory with digital simulation practice."
        )
        keywords.take(3).forEach { k ->
            items.add("To examine the role of $k within the overall system organization.")
        }
        return formatPoints(items.distinct(), style, numberedPreferred = true)
    }

    private fun scope(topic: String, kw: String): String {
        return "The scope of this work is educational and demonstrative. It focuses on the logical organization of $topic, " +
                "including $kw, rather than on commercial product engineering. " +
                "Physical electrical timing, full operating-system stacks, and industrial bus protocols are intentionally simplified. " +
                "The emphasis remains on clarity of addressing, register interfaces, control signaling, and observable input/output behavior. " +
                "Within these boundaries, the report still aims for sufficient depth to support a multi-section academic submission."
    }

    private fun background(topic: String, kw: String): String {
        return "Computer architecture literature consistently treats input/output organization as a first-class concern. " +
                "A processor is useful only when it can exchange information with memory and external devices. " +
                "Address buses identify targets, data buses carry values, and control lines establish operation type and timing. " +
                "In this context, $topic provides a coherent model for mapping device registers into a shared address space. " +
                "Themes such as $kw appear repeatedly because they capture the practical mechanisms—decoding, selection, " +
                "and register update—that make peripheral access predictable. " +
                "Educational simulations reinforce these ideas by making each transaction visible, which reduces the gap " +
                "between textbook diagrams and working digital logic."
    }

    private fun coreConcepts(topic: String, keywords: List<String>, domain: DomainKnowledge.Pack): String {
        val base = "Several interlocking concepts define $topic. " +
                "An address space assigns unique locations to resources. " +
                "Device registers hold input state or latched output values. " +
                "Decoding logic asserts a select line only when the current address matches a device mapping. " +
                "Read and write controls determine data direction. " +
                domain.concepts
        val extra = keywords.take(4).joinToString(" ") {
            "The term \"$it\" is relevant because it identifies a concrete element of the interface that must be designed, tested, and documented. "
        }
        return base + " " + extra
    }

    private fun architecture(topic: String, domain: DomainKnowledge.Pack): String {
        return "A practical organization for $topic can be described in layers. " +
                "At the top, a processor-side interface presents address, data, and control signals. " +
                "Below that, an address decoder partitions the space into device-select regions. " +
                "Each peripheral attaches through a register or interface block that either drives the data path (read) " +
                "or captures values (write). " +
                domain.architecture + " " +
                "This separation keeps the design modular: new devices can be added by allocating an address, " +
                "generating a select signal, and connecting the appropriate data and enable logic."
    }

    private fun methodology(topic: String): String {
        return "The methodology combines conceptual analysis with constructive demonstration. " +
                "First, the requirements of $topic are decomposed into addressability, directionality, and observability. " +
                "Second, a representative address map is defined for the peripherals under study. " +
                "Third, decoding and enable equations are specified so that only the intended device responds. " +
                "Fourth, a simulation or digital model is used to exercise read and write transactions. " +
                "Finally, results are compared against expected behavior for valid addresses, invalid addresses, and varied data patterns. " +
                "This sequence mirrors standard engineering practice while remaining accessible for academic evaluation."
    }

    private fun designImpl(topic: String, domain: DomainKnowledge.Pack): String {
        return "Design begins with an explicit address map and bit-width choices for the data path. " +
                "Implementation then realizes decoder comparisons, register enables, and peripheral connections. " +
                "For $topic, clarity is preferred over premature optimization: unique addresses, labeled signals, " +
                "and isolated enable conditions reduce debugging time. " +
                domain.implementation + " " +
                "Where a simulation environment is used, subcircuits help isolate decoder, register, and device blocks " +
                "so that each can be verified independently before full integration."
    }

    private fun procedure(topic: String, domain: DomainKnowledge.Pack): String {
        return "A recommended working procedure is as follows. " +
                "Establish the processor-side signals and bus widths. " +
                "Construct the address decoder and confirm select lines for each mapped device. " +
                "Attach input peripherals to the read path and output peripherals to write-enabled registers. " +
                "Apply controlled test vectors: change inputs, perform reads, write known patterns, and observe outputs. " +
                domain.procedure + " " +
                "Document each successful and unsuccessful case so that the relationship between address, control, and data is explicit."
    }

    private fun testing(topic: String, domain: DomainKnowledge.Pack): String {
        return "Testing should be systematic. Individual devices are verified first, then cross-effects are checked to ensure isolation. " +
                "Valid addresses must activate only the intended peripheral. Unassigned addresses should leave outputs unchanged. " +
                "Repeated writes with different patterns confirm that registers latch new values correctly. " +
                domain.testing + " " +
                "A short test matrix—address, operation, data, expected observation—keeps validation reproducible and easy to grade or review."
    }

    private fun results(topic: String, domain: DomainKnowledge.Pack): String {
        return "Expected results for a correct realization of $topic include: faithful reflection of input state on read transactions, " +
                "stable output patterns after write transactions, and strict dependence on address selection. " +
                domain.results + " " +
                "When these conditions are met, the demonstration successfully shows that peripheral behavior is governed by " +
                "the shared address and data interface rather than by ad-hoc point-to-point wiring alone."
    }

    private fun discussion(topic: String, kw: String, domain: DomainKnowledge.Pack): String {
        return "Discussion of $topic highlights why address decoding is non-negotiable: without unique selection, multiple devices " +
                "may respond and corrupt the data path. " +
                "Control signals are equally important; a register must update only when both select and write are asserted. " +
                "Keywords such as $kw point to the practical details that separate a working interface from a fragile prototype. " +
                domain.discussion + " " +
                "From an educational standpoint, making these mechanisms visible turns I/O organization from memorized definitions " +
                "into an inspectable system."
    }

    private fun applications(topic: String, domain: DomainKnowledge.Pack): String {
        return "Principles illustrated by $topic transfer directly to embedded systems, microcontroller GPIO and peripheral blocks, " +
                "FPGA soft-core designs, and laboratory teaching platforms. " +
                domain.applications + " " +
                "Once students can reason about address maps and register interfaces, they are better prepared for datasheets, " +
                "memory maps, and real peripheral programming models."
    }

    private fun conclusion(topic: String, mode: ReportMode): String {
        return "This report has examined $topic as both an architectural idea and a constructible interface. " +
                "By organizing motivation, design, procedure, testing, and reflection into a coherent document, " +
                "it supports academic assessment and practical understanding. " +
                "The essential lesson is that orderly communication between processor and devices depends on addressing, " +
                "decoding, control, and register semantics working together. " +
                "Future work can extend the same foundation toward interrupts, timers, richer peripherals, and closer timing models " +
                "without abandoning the clarity established here."
    }

    private fun findingsList(topic: String, domain: DomainKnowledge.Pack) = listOf(
        "Address selection is the primary mechanism that binds a transaction to a specific device in $topic.",
        "Read and write paths must be isolated so that input state and output latches do not contend on the data bus.",
        "Explicit enable conditions (select AND control) prevent unintended register updates.",
        domain.findingsExtra,
        "Systematic testing of valid and invalid addresses is necessary to prove decoder correctness."
    )

    private fun advantages(topic: String, domain: DomainKnowledge.Pack) = listOf(
        "Provides a unified addressing model for multiple peripherals related to $topic.",
        "Makes decoder behavior and data direction observable in simulation or lab settings.",
        "Supports modular extension when new devices are required.",
        domain.advantageExtra,
        "Strengthens the link between computer architecture theory and digital design practice."
    )

    private fun limitationsList(topic: String) = listOf(
        "Educational models of $topic simplify real bus timing and electrical constraints.",
        "Commercial protocols, privilege levels, and concurrent masters are outside the basic scope.",
        "Scanned or incomplete reference materials may limit style-matching fidelity.",
        "Very large address spaces need hierarchical decoding beyond a minimal classroom circuit."
    )

    private fun opportunitiesList(topic: String, domain: DomainKnowledge.Pack) = listOf(
        "Extend the $topic demonstration with additional peripherals and status registers.",
        "Introduce interrupt signaling to contrast polling with event-driven I/O.",
        domain.opportunityExtra,
        "Add lightweight software or instruction sequences to exercise the hardware map end-to-end."
    )

    private fun recommendationsList(topic: String, domain: DomainKnowledge.Pack) = listOf(
        "Keep the address map documented and conflict-free before wiring devices for $topic.",
        "Test each peripheral in isolation, then verify that other devices remain unaffected.",
        "Label buses and control lines clearly to reduce integration errors.",
        domain.recommendationExtra,
        "Reserve spare addresses early so future enhancements do not force a redesign."
    )

    private fun futureWork(topic: String, domain: DomainKnowledge.Pack) = listOf(
        "Add timers, serial interfaces, or sensor blocks to the $topic address map.",
        "Model wait states or simple handshaking for more realistic timing.",
        "Incorporate interrupt request and acknowledge paths.",
        domain.futureExtra,
        "Scale the decoder toward hierarchical or partial decoding for larger spaces."
    )

    private fun extraSection(topic: String, domain: DomainKnowledge.Pack, index: Int): String {
        return when (index) {
            1 -> "Additional technical notes on $topic emphasize signal discipline: only one driver should assert the shared data path at a time, " +
                    "and unused inputs should be tied to defined levels in simulation. ${domain.extra1}"
            2 -> "A comparative perspective positions $topic against isolated I/O models. Memory-mapped approaches reuse load/store style access, " +
                    "while port-mapped models may require dedicated instructions. ${domain.extra2}"
            3 -> "Learning outcomes include the ability to draw an address map, write decoder conditions, and predict device responses under read and write cycles for $topic."
            4 -> "Best practices include versioning of the address map, consistent bit ordering, and a written test plan executed after every structural change."
            5 -> "A case walkthrough might select an input address, present a known pattern, read it back, then write a complementary pattern to an output address while recording each observation."
            6 -> "Related topics include bus arbitration, memory hierarchy, and exception handling; each becomes easier once basic mapped I/O is understood for $topic."
            else -> "Critical design choices—width of the data path, granularity of decoding, and number of registers per device—should be justified against the educational goals of the $topic project."
        }
    }

    // ── Formatting helpers ───────────────────────────────────────────

    private fun expand(base: String, domain: DomainKnowledge.Pack, weight: Int): String {
        val extras = buildString {
            repeat(weight) { i ->
                append(" ")
                append(domain.paddingParagraphs.getOrElse(i) { domain.paddingParagraphs.last() })
            }
        }
        return (base.trim() + extras).trim()
    }

    private fun listBlock(items: List<String>, style: StyleProfile, numbered: Boolean = false): String {
        return formatPoints(items.filter { it.isNotBlank() }, style, numberedPreferred = numbered)
    }

    private fun formatPoints(
        points: List<String>,
        style: StyleProfile,
        numberedPreferred: Boolean = false
    ): String {
        return when {
            numberedPreferred || style.hasNumberedSections ->
                points.mapIndexed { i, p -> "${i + 1}. $p" }.joinToString("\n")
            style.hasBulletPoints ->
                points.joinToString("\n") { "• $it" }
            else -> points.joinToString(" ")
        }
    }

    private fun firstSentences(text: String, n: Int): String {
        return text.replace(Regex("""\s+"""), " ")
            .split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { it.length in 30..300 }
            .take(n)
            .joinToString(" ")
    }

    private fun assembleReport(
        style: StyleProfile,
        topic: String,
        sections: List<Pair<String, String>>,
        mode: ReportMode,
        pages: Int
    ): String {
        val sb = StringBuilder()
        val today = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())
        val ref = "ATL-" + UUID.randomUUID().toString().take(8).uppercase(Locale.getDefault())

        sb.appendLine(topic.uppercase(Locale.getDefault()))
        if (mode != ReportMode.CREATIVE) sb.appendLine("REPORT")
        sb.appendLine()
        if (style.hasDate) {
            sb.appendLine("Date: $today")
            sb.appendLine("Reference: $ref")
            sb.appendLine("Target length: approximately $pages pages")
            sb.appendLine()
        }

        sections.forEachIndexed { index, (header, body) ->
            if (style.hasNumberedSections) sb.appendLine("${index + 1}. $header")
            else sb.appendLine(header.uppercase(Locale.getDefault()))
            sb.appendLine()
            sb.appendLine(body.trim())
            sb.appendLine()
        }

        if (style.hasSignature) {
            sb.appendLine("Prepared by: ATLAS AI Report Studio")
            sb.appendLine("Date: $today")
            sb.appendLine("Classification: Academic / Internal use")
            sb.appendLine()
        }
        sb.appendLine("— Generated by ATLAS AI —")
        return sb.toString().trim()
    }
}

/**
 * Lightweight domain pack so technical topics (e.g. memory-mapped I/O, Logisim)
 * get denser, more relevant paragraphs instead of pure generic filler.
 */
object DomainKnowledge {

    data class Pack(
        val concepts: String,
        val architecture: String,
        val implementation: String,
        val procedure: String,
        val testing: String,
        val results: String,
        val discussion: String,
        val applications: String,
        val findingsExtra: String,
        val advantageExtra: String,
        val opportunityExtra: String,
        val recommendationExtra: String,
        val futureExtra: String,
        val extra1: String,
        val extra2: String,
        val paddingParagraphs: List<String>
    )

    fun forTopic(topic: String, keywords: List<String>): Pack {
        val blob = (topic + " " + keywords.joinToString(" ")).lowercase(Locale.getDefault())
        return when {
            listOf("memory-mapped", "memory mapped", "mmio", "i/o", "io", "logisim", "led", "switch", "seven-segment", "decoder", "address bus")
                .any { blob.contains(it) } -> mmioPack()
            listOf("cpu", "processor", "pipeline", "cache", "architecture").any { blob.contains(it) } -> archPack()
            else -> generalPack(topic)
        }
    }

    private fun mmioPack() = Pack(
        concepts = "Memory-mapped I/O places peripheral registers in the same address space used for ordinary memory. " +
                "The processor issues familiar read/write style operations; decoding decides whether RAM or a device responds.",
        architecture = "A minimal mapped-I/O stack includes an address bus, data bus, read/write controls, an address decoder, " +
                "and device registers for switches, LEDs, and displays. Device-select outputs from the decoder gate the correct interface.",
        implementation = "In Logisim-style implementations, equality comparators or decoder blocks generate selects for addresses such as " +
                "0x00 (switches), 0x04 (LEDs), and 0x08 (seven-segment). Write enables combine select with the write control line.",
        procedure = "After wiring, set a switch pattern and perform a read at the switch address; then write patterns to LED and display addresses " +
                "and confirm isolation between devices.",
        testing = "Useful vectors include alternating LED patterns (e.g., 10101010 and 11110000), multiple display codes, and intentional accesses " +
                "to unmapped addresses that must not alter outputs.",
        results = "A correct circuit shows switch values on read, latched LED/display state on write, and no cross-device updates when addresses differ.",
        discussion = "The exercise shows that peripherals share a bus structure while remaining independent through decoding—an essential idea in computer organization courses.",
        applications = "The same pattern appears in microcontroller memory maps, soft-CPU designs on FPGAs, and laboratory kits that expose GPIO and display registers.",
        findingsExtra = "Seven-segment outputs demonstrate that a register value may pass through an encoding layer before becoming human-readable symbols.",
        advantageExtra = "Logisim visibility of every select and data line accelerates debugging compared with opaque hardware alone.",
        opportunityExtra = "Interrupts and timers can be added as additional mapped registers to broaden the I/O story.",
        recommendationExtra = "Publish the address map in the report appendix so evaluators can reproduce every transaction.",
        futureExtra = "A small instruction engine or scripted test harness can automate multi-step bus sequences.",
        extra1 = "Tri-state or multiplexed data steering avoids bus contention when several devices can source read data.",
        extra2 = "Isolated I/O uses a separate port space; memory-mapped I/O reuses the memory space, which simplifies the conceptual model for many teaching CPUs.",
        paddingParagraphs = listOf(
            "Address decoding must guarantee mutual exclusion: at most one device select should be active for a given legal address.",
            "Register enables derived from select and write protect outputs from changing during unrelated transactions on the bus.",
            "Educational address maps should remain sparse and well documented so students can extend them without collisions.",
            "Human-readable displays add an interpretation stage that pure LED arrays do not require, enriching the learning outcome."
        )
    )

    private fun archPack() = Pack(
        concepts = "Processor architecture topics revolve around instruction flow, datapaths, control, and the memory hierarchy.",
        architecture = "Blocks such as ALU, registers, control unit, and memory interface cooperate under a defined timing discipline.",
        implementation = "Design choices include pipeline depth, hazard handling, and the width of internal buses.",
        procedure = "Progress from single-cycle explanations to multi-cycle or pipelined reasoning only after the basic datapath is clear.",
        testing = "Validation uses program traces, waveform inspection, and targeted micro-benchmarks for hazards and forwarding.",
        results = "Correct designs retire instructions with expected register and memory state under the chosen hazard policy.",
        discussion = "Trade-offs between simplicity and performance dominate architectural evaluation.",
        applications = "Concepts transfer to CPU design courses, embedded SoCs, and performance analysis work.",
        findingsExtra = "Control complexity often dominates datapath complexity once hazards appear.",
        advantageExtra = "A clean modular boundary between datapath and control simplifies verification.",
        opportunityExtra = "Caches and branch prediction are natural extensions after a baseline core is stable.",
        recommendationExtra = "Document pipeline diagrams and hazard tables alongside textual explanation.",
        futureExtra = "Superscalar or out-of-order extensions can be introduced as stretch goals.",
        extra1 = "Hazard classification (structural, data, control) structures both design and testing.",
        extra2 = "Memory hierarchy behavior frequently limits realized performance more than peak ALU throughput.",
        paddingParagraphs = listOf(
            "Clear specification of reset behavior and initial register state prevents ambiguous simulations.",
            "Performance claims should be paired with the workload used to measure them.",
            "Educational models benefit from fewer exceptional cases until the baseline is mastered."
        )
    )

    private fun generalPack(topic: String) = Pack(
        concepts = "The study of $topic requires precise definitions, a bounded scope, and evaluation criteria that can be tested.",
        architecture = "A coherent structure for $topic separates inputs, processing or control logic, and observable outputs.",
        implementation = "Implementation should follow the documented interface contracts so that modules remain replaceable.",
        procedure = "Work proceeds from requirements to design, construction, test, and reflection for $topic.",
        testing = "Tests must include both typical and edge cases relevant to $topic.",
        results = "Results are credible only when linked to the procedure and instruments used.",
        discussion = "Discussion interprets results against objectives rather than merely restating them.",
        applications = "Applications section should map $topic to at least one realistic setting outside the lab.",
        findingsExtra = "The strongest findings are those that change a design decision or teaching approach for $topic.",
        advantageExtra = "A modular treatment of $topic reduces the cost of later enhancements.",
        opportunityExtra = "Open questions around $topic can seed future mini-projects.",
        recommendationExtra = "Recommendations should be actionable within one academic term.",
        futureExtra = "Future work should list dependencies and skills required to extend $topic.",
        extra1 = "Consistency of terminology across sections improves readability of long reports on $topic.",
        extra2 = "Figures and tables, when available, should be referenced explicitly in the narrative about $topic.",
        paddingParagraphs = listOf(
            "Each major claim about $topic should be traceable to a section on method or observation.",
            "Where assumptions are introduced, they must be stated so that later readers can revisit them.",
            "Length is used to develop argument quality, not to repeat the same sentence with minor wording changes."
        )
    )
}
