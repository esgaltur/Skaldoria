package com.skaldoria.core.audience

import java.util.concurrent.ConcurrentHashMap

/** The three things a listener can tell the speaker without interrupting. */
enum class Reaction(val wireName: String, val label: String) {
    TooFast("fast", "Too fast"),
    Lost("lost", "Lost me"),
    Good("good", "Good");

    companion object {
        fun fromWire(name: String?): Reaction? = entries.firstOrNull { it.wireName == name }
    }
}

/** One tap, kept for the post-talk report. */
data class ReactionEvent(
    val atMillis: Long,
    val slideIndex: Int,
    val reaction: Reaction,
    val voterKey: String
)

/**
 * The room's live mood, and the record of it.
 *
 * **The pulse counts people, not taps.** Each device has one current reaction — its latest — and
 * it counts only while it is fresher than [WINDOW_MILLIS]. Tapping "too fast" ten times is one
 * person saying it once; switching to "good" replaces it. A meter that summed taps would let one
 * impatient phone look like the whole room.
 *
 * The [history] keeps every tap (bounded) so the report can say where in the talk people got lost.
 *
 * SEC-5: both stores are bounded — the input arrives from untrusted devices.
 */
class AudienceReactions(private val clock: () -> Long = System::currentTimeMillis) {

    private val latestByDevice = ConcurrentHashMap<String, ReactionEvent>()
    private val log = ArrayDeque<ReactionEvent>()

    fun react(voterKey: String, reaction: Reaction, slideIndex: Int) {
        val event = ReactionEvent(clock(), slideIndex, reaction, voterKey)
        latestByDevice[voterKey] = event
        if (latestByDevice.size > MAX_DEVICES) pruneExpired()
        synchronized(log) {
            log.addLast(event)
            while (log.size > MAX_EVENTS) log.removeFirst()
        }
    }

    /** How many devices currently hold each reaction. Every [Reaction] is present, zero or not. */
    fun pulse(): Map<Reaction, Int> {
        val since = clock() - WINDOW_MILLIS
        val counts = latestByDevice.values.filter { it.atMillis >= since }.groupingBy { it.reaction }.eachCount()
        return Reaction.entries.associateWith { counts[it] ?: 0 }
    }

    /** Every recorded tap, oldest first. Safe to call from any thread. */
    fun history(): List<ReactionEvent> = synchronized(log) { log.toList() }

    fun reset() {
        latestByDevice.clear()
        synchronized(log) { log.clear() }
    }

    private fun pruneExpired() {
        val since = clock() - WINDOW_MILLIS
        latestByDevice.entries.removeIf { it.value.atMillis < since }
    }

    companion object {
        /** A reaction older than this no longer describes the room. */
        const val WINDOW_MILLIS = 60_000L

        /** SEC-5 bounds. */
        const val MAX_DEVICES = 2_000
        const val MAX_EVENTS = 10_000
    }
}
