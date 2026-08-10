package com.skaldoria

import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * The measurement harness both probes use.
 *
 * Extracted rather than duplicated because *how to benchmark reliably here* is one piece of
 * knowledge, and it was learned the hard way — see PRF-6 in
 * `skaldoria-presentation/docs/PERFORMANCE_BASELINE.md`. Two
 * copies would drift, and a probe that measures differently from the one its numbers are compared
 * against is worse than no probe.
 *
 * Two properties matter, and both exist because their absence produced false conclusions:
 *
 *  - **Every result is consumed.** A JIT that can prove a result is unused may elide the work that
 *    produced it, which makes a discarding benchmark a lower bound rather than a cost.
 *  - **Fastest of N passes, not one pass.** A single timed pass was not reproducible: back-to-back
 *    runs of an unchanged binary varied by 2.2x. Minimum rather than mean, because every source of
 *    interference — JIT recompilation, GC, scheduling — makes a pass slower and none makes it
 *    faster.
 *
 * Still an estimate. This is a print-probe, not JMH: it cannot control compilation tiers or give
 * each subject a fresh JVM.
 */
object Bench {

    private var blackhole: Int = 0

    /**
     * Keeps a benchmarked result observable.
     *
     * `identityHashCode` is deliberate — constant-time, and it forces the object to have actually
     * been allocated. `hashCode()` on a `List<Slide>` would walk the whole structure and time the
     * sink instead of the subject.
     */
    fun consume(value: Any?) {
        blackhole += System.identityHashCode(value)
    }

    fun report(label: String, iterations: Int, elapsed: Duration, rounds: Int) {
        val perOp = elapsed.inWholeMicroseconds.toDouble() / iterations
        val perOpText = if (perOp >= 1000) "%.2f ms".format(perOp / 1000) else "%.0f us".format(perOp)
        println("  %-52s %10s / call   (best of %d x %d)".format(label, perOpText, rounds, iterations))
    }

    inline fun measure(label: String, iterations: Int, rounds: Int = 3, block: () -> Any?) {
        repeat(iterations / 2 + 1) { consume(block()) } // warm the JIT

        var best = Duration.INFINITE
        repeat(rounds) {
            val elapsed = measureTime { repeat(iterations) { consume(block()) } }
            if (elapsed < best) best = elapsed
        }
        report(label, iterations, best, rounds)
    }

    /** A deck the shape of a real conference talk: headings, prose, bullets, periodic code. */
    fun realisticDeck(slides: Int): String = buildString {
        for (slide in 1..slides) {
            appendLine("# Section $slide")
            appendLine()
            appendLine("Some introductory prose for section $slide that runs a little long.")
            appendLine()
            repeat(6) { appendLine("- A bullet point number $it explaining part of section $slide") }
            appendLine()
            if (slide % 4 == 0) {
                appendLine("```kotlin")
                appendLine("fun handler$slide(input: String): Int {")
                appendLine("    val parsed = input.trim().toIntOrNull() ?: return 0")
                appendLine("    return if (parsed > 0) parsed else -parsed")
                appendLine("}")
                appendLine("```")
                appendLine()
            }
            appendLine("---")
            appendLine()
        }
    }
}
