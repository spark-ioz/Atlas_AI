package com.atlasai.reportgenerator

/**
 * High-quality offline paragraphs for common CSE mini-project domains.
 * Used when web research is empty and/or live AI is unavailable.
 */
object DomainContent {

    fun paragraph(title: String, heading: String, domain: String): String {
        val t = title.trim()
        val h = heading.lowercase()
        val bus = domain.contains("bus") || t.lowercase().contains("bus")
        val mmio = t.lowercase().contains("memory-mapped") || t.lowercase().contains("mmio") ||
                t.lowercase().contains("memory mapped")
        val logisim = domain.contains("digital") || t.lowercase().contains("logisim") ||
                t.lowercase().contains("logism")

        return when {
            h.contains("abstract") || h.contains("summary") -> abstractOf(t, bus, mmio, logisim)
            h.contains("intro") -> introOf(t, bus, mmio, logisim)
            h.contains("problem") -> problemOf(t, bus, mmio)
            h.contains("objective") -> objectivesOf(t, bus, mmio, logisim)
            h.contains("scope") -> scopeOf(t, logisim)
            h.contains("literature") || h.contains("background") || h.contains("survey") ->
                backgroundOf(t, bus, mmio, logisim)
            h.contains("material") || h.contains("hardware") || h.contains("software") || h.contains("requirement") ->
                materialsOf(t, logisim)
            h.contains("design") || h.contains("architecture") || h.contains("block") ->
                designOf(t, bus, mmio, logisim)
            h.contains("method") -> methodOf(t, logisim)
            h.contains("implement") || h.contains("working") || h.contains("procedure") ->
                implementOf(t, bus, mmio, logisim)
            h.contains("coding") || h.contains("source code") || h.contains("algorithm") || h.contains("program") ->
                codingOf(t, bus, mmio)
            h.contains("test") -> testingOf(t, bus, mmio)
            h.contains("result") || h.contains("observation") -> resultsOf(t, bus, mmio)
            h.contains("discussion") -> discussionOf(t, bus, mmio)
            h.contains("advantage") -> advantagesOf(t, logisim)
            h.contains("limitation") -> limitationsOf(t, logisim)
            h.contains("application") -> applicationsOf(t, bus, mmio)
            h.contains("future") -> futureOf(t, bus, mmio)
            h.contains("conclusion") -> conclusionOf(t)
            h.contains("reference") -> "References are listed at the end of this report."
            else -> genericOf(t, heading)
        }
    }

    private fun abstractOf(t: String, bus: Boolean, mmio: Boolean, logisim: Boolean) =
        "This report presents a structured study of \"$t\". " +
                (if (logisim) "The work is demonstrated using digital-logic simulation concepts suitable for classroom tools such as Logisim. " else "") +
                (if (bus) "Emphasis is placed on how address, data, and control paths cooperate on a shared system bus, including device selection and transaction direction. " else "") +
                (if (mmio) "Memory-mapped I/O is treated as a method of placing peripheral registers in a common address space so that ordinary read/write style accesses reach devices. " else "") +
                "The report covers motivation, design organization, procedure, testing ideas, and conclusions for academic evaluation."

    private fun introOf(t: String, bus: Boolean, mmio: Boolean, logisim: Boolean) =
        "\"$t\" addresses a core idea in computer organization: the processor must communicate reliably with memory and peripheral devices. " +
                (if (bus) "A system bus provides shared lines for addresses, data, and control signals so multiple units can coordinate transfers without dedicated point-to-point wiring for every device. " else "") +
                (if (mmio) "In a memory-mapped approach, device registers appear as locations in the address map; decoding logic decides whether a transaction targets memory or a peripheral. " else "") +
                (if (logisim) "Simulation environments make these transactions visible: students can watch select lines, data values, and register updates step by step. " else "") +
                "This introduction defines the topic, states why it matters in coursework, and outlines how the remaining sections develop the design and evaluation story."

    private fun problemOf(t: String, bus: Boolean, mmio: Boolean) =
        "Students often memorize bus and I/O definitions without seeing how a concrete transaction unfolds. " +
                "The practical problem addressed by \"$t\" is to make selection, direction, and data movement explicit. " +
                (if (bus) "Without clear bus rules, multiple devices may drive the data path at once, or a device may respond to the wrong address. " else "") +
                (if (mmio) "Without a correct address map and decoder, peripheral reads and writes cannot be predicted or tested. " else "") +
                "The report therefore frames the problem as designing and explaining a coherent, testable model rather than only listing textbook terms."

    private fun objectivesOf(t: String, bus: Boolean, mmio: Boolean, logisim: Boolean) =
        """
1. To explain the purpose of "$t" in computer architecture terms.
2. To identify the main blocks required (processor interface, address decode, device registers, and control enables).
3. To describe read and write behaviour for the chosen organization.
4. To outline a practical construction or simulation procedure${if (logisim) " in a digital-logic tool" else ""}.
5. To propose test cases that confirm correct device isolation and data transfer.
6. To summarize advantages, limits, and possible extensions.
""".trimIndent()

    private fun scopeOf(t: String, logisim: Boolean) =
        "The scope of \"$t\" is educational. The focus is logical organization—addressing, selection, and observable data movement—rather than full industrial timing closure or multi-master arbitration at product scale. " +
                (if (logisim) "Gate-level or block-level simulation is used to clarify behaviour; analog electrical effects are omitted. " else "") +
                "Operating-system drivers and high-level programming stacks are outside the primary scope unless needed to explain why the hardware map matters."

    private fun backgroundOf(t: String, bus: Boolean, mmio: Boolean, logisim: Boolean) =
        "Computer architecture separates processing, storage, and input/output. Communication among these parts uses structured signal groups. " +
                (if (bus) "A typical system bus includes an address bus that identifies the target, a data bus that carries values, and control lines such as read, write, and clock or enable. " else "") +
                (if (mmio) "Memory-mapped I/O places peripheral registers in the same address space as memory so that load/store style operations can reach devices after decoding. " else "") +
                "Address decoding asserts a device-select only when the current address matches that device’s assigned range. " +
                (if (logisim) "Educational simulators reinforce these ideas by showing each signal transition during a transaction. " else "") +
                "This background supports the detailed design of \"$t\"."

    private fun materialsOf(t: String, logisim: Boolean) =
        "Materials and tools for \"$t\" typically include: " +
                (if (logisim) "a digital-logic simulator (e.g., Logisim or equivalent), " else "a simulation or HDL environment, ") +
                "documentation for the planned address map, basic combinational blocks for decoding (comparators or decoders), " +
                "register or flip-flop elements for output holding, input switches or input registers, and simple output indicators such as LEDs or seven-segment models. " +
                "A written test plan and labeling convention for buses improve reproducibility. No specialized industrial hardware is required for the logical demonstration itself."

    private fun designOf(t: String, bus: Boolean, mmio: Boolean, logisim: Boolean) =
        "The design of \"$t\" can be organized in layers. " +
                (if (bus) "At the bus interface, address, data, and control ports are defined with fixed widths. " else "") +
                "An address decoder maps ranges or discrete addresses to select lines. " +
                (if (mmio) "Each peripheral occupies one or more addresses; input devices drive the data path when selected for read, while output devices latch data when selected for write. " else "") +
                "Enable logic combines select with read/write so registers update only on intentional writes. " +
                (if (logisim) "In simulation, subcircuits isolate the decoder, register bank, and device models for independent verification before full integration. " else "") +
                "Clear naming of signals and a published address table are part of the design deliverable."

    private fun methodOf(t: String, logisim: Boolean) =
        "The methodology for \"$t\" proceeds as follows: define learning goals; specify the address map; design decoder and device interfaces; " +
                (if (logisim) "build the circuit in the simulator; " else "implement the model; ") +
                "execute controlled read/write tests; record observations; and compare results with expected behaviour. " +
                "Iteration is expected when select polarity or enable conditions are incorrect."

    private fun implementOf(t: String, bus: Boolean, mmio: Boolean, logisim: Boolean) =
        "Implementation of \"$t\" begins with fixed bus widths and a minimal address map (for example, distinct addresses for inputs and outputs). " +
                "Decoder outputs become device selects. " +
                (if (mmio) "Input paths are enabled on read-select; output registers capture data on write-select. " else "") +
                (if (bus) "Tri-state or multiplexed data steering prevents contention when more than one device could drive the data lines. " else "") +
                (if (logisim) "In Logisim-style tools, tunnels or labeled wires keep the schematic readable while clock or button-step simulation advances each transaction. " else "") +
                "Documentation of the final map and enable equations is required so another student can reproduce the build."

    private fun codingOf(t: String, bus: Boolean, mmio: Boolean) =
        """
Pseudocode outlining the control of transfers for "$t":

  define ADDRESS_MAP  // e.g., INPUT=0x00, OUTPUT=0x04
  on each bus cycle:
    addr ← address_bus
    if addr matches INPUT and READ:
      data_bus ← input_register
    else if addr matches OUTPUT and WRITE:
      output_register ← data_bus
    else:
      // no device selected — data_bus not driven by peripherals

  // Test vector example
  set inputs to pattern P
  perform READ at INPUT address
  assert data equals P
  perform WRITE pattern Q at OUTPUT address
  assert outputs show Q
""".trimIndent() +
                "\n\nThis logic is conceptual; the same control conditions are realized with combinational select logic and register enables in the hardware model."

    private fun testingOf(t: String, bus: Boolean, mmio: Boolean) =
        "Testing \"$t\" should be systematic. First verify decoder selects for each assigned address and confirm that unassigned addresses select nothing. " +
                "Next, perform reads from input addresses and check that presented values match the input model. " +
                "Then write multiple patterns to output addresses and confirm retention until the next write. " +
                "Cross-tests ensure that writing one device does not change another. " +
                "A short table of (address, operation, data, expected observation) makes the validation reproducible for evaluation."

    private fun resultsOf(t: String, bus: Boolean, mmio: Boolean) =
        "Expected results for a correct realization of \"$t\" include: unique device response per mapped address, stable output state after writes, faithful input reflection on reads, and no bus contention under the designed enables. " +
                "Screenshots or waveform-style notes from the simulator can support the written observations. Deviations usually trace to inverted select polarity, missing write enables, or overlapping address assignments."

    private fun discussionOf(t: String, bus: Boolean, mmio: Boolean) =
        "Discussion of \"$t\" highlights why decoding and enables are non-negotiable: shared buses only work when at most one device drives data at a time and when writes are intentional. " +
                "Educationally, the exercise converts abstract bus diagrams into inspectable behaviour. Design trade-offs include address sparsity versus decoder complexity and the amount of status reporting provided by each peripheral."

    private fun advantagesOf(t: String, logisim: Boolean) =
        "Advantages of studying \"$t\" include a unified view of processor–device communication, modular extension when new addresses are added, and " +
                (if (logisim) "immediate visual feedback in simulation. " else "clear separation of datapath and control concerns. ") +
                "The same mental model transfers to microcontroller memory maps and soft-CPU designs."

    private fun limitationsOf(t: String, logisim: Boolean) =
        "Limitations include simplified timing compared with real printed-circuit buses, limited multi-master arbitration, and omission of interrupts or DMA unless explicitly added. " +
                (if (logisim) "Simulator models also abstract electrical loading and noise. " else "") +
                "These limits are acceptable for coursework but must be acknowledged when relating the project to industrial practice."

    private fun applicationsOf(t: String, bus: Boolean, mmio: Boolean) =
        "Applications of the ideas in \"$t\" appear in embedded boards, laboratory teaching kits, FPGA soft cores, and any system that maps peripheral registers into a shared address space. " +
                "Understanding the model helps when reading datasheets that publish memory maps and control-bit definitions."

    private fun futureOf(t: String, bus: Boolean, mmio: Boolean) =
        "Future enhancements to \"$t\" may add interrupt request lines, status registers, burst transfers, hierarchical decoding for larger maps, or a small programmed sequence that exercises the hardware automatically. " +
                "Closer timing models and multi-master arbitration are further extensions for advanced courses."

    private fun conclusionOf(t: String) =
        "This report examined \"$t\" as a coherent organization problem: define the map, decode addresses, control direction, and verify behaviour. " +
                "When those elements work together, processor and device communication becomes predictable and teachable. " +
                "The same foundation supports richer peripherals and more realistic system models in later work."

    private fun genericOf(t: String, heading: String) =
        "This section develops \"$heading\" for the project titled \"$t\". " +
                "It explains the role of this part of the work, the decisions required, and how the outcomes support the overall objectives of the mini-project. " +
                "All points remain tied to \"$t\" rather than to an unrelated subject."
}
