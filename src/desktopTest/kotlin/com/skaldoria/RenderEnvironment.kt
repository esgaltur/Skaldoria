package com.skaldoria

import org.junit.jupiter.api.Assumptions
import java.awt.GraphicsEnvironment

/**
 * PLT-08: whether this machine can draw, so render guards can stand down instead of failing.
 *
 * `ImageComposeScene` asks Skia for a real surface. On a box with no display — WSL without an
 * X server, a container, a headless VM — that fails, and it fails for every test that renders,
 * which is how `./gradlew createDistributable` on WSL turns into a red suite that says nothing
 * about the packaging being attempted.
 *
 * **The workaround this replaces was worse than the problem.** `FullscreenDeckKeyTest` carried a
 * blanket `@Ignore`, added inside an unrelated commit with no note. That silenced 8 passing
 * tests covering the presenter window's entire keyboard — the guard `FEATURE_INDEX` credits with
 * discovering that `H`, the arrows, blackout and the presenter clicker were all dead in the
 * window a speaker spends the talk looking at — on *every* machine, including the ones with a
 * display where they run fine. A platform problem had been solved by disabling the coverage
 * everywhere.
 *
 * Skipping is deliberately an **assumption, not a pass**: JUnit reports these as skipped, so the
 * suite total drops visibly on a headless box rather than quietly claiming the same coverage.
 */
object RenderEnvironment {

    /** Set `-Dskaldoria.skipRenderTests=true` to stand the render guards down explicitly. */
    const val SKIP_PROPERTY = "skaldoria.skipRenderTests"

    private val explicitlySkipped: Boolean
        get() = System.getProperty(SKIP_PROPERTY)?.toBooleanStrictOrNull() == true

    /**
     * True when a scene can be rendered here.
     *
     * `GraphicsEnvironment.isHeadless()` is the platform's own answer — on Linux it is what an
     * unset `DISPLAY` produces — so this needs no per-OS branching.
     */
    val canRender: Boolean
        get() = !explicitlySkipped && !GraphicsEnvironment.isHeadless()

    /**
     * Skips the calling test when there is no display.
     *
     * Call from `@BeforeTest`. A skipped test is reported as skipped; it never reports as passed.
     */
    fun requireDisplay() {
        Assumptions.assumeTrue(
            canRender,
            "no display available — render guards skipped (headless=${GraphicsEnvironment.isHeadless()}, " +
                "$SKIP_PROPERTY=${System.getProperty(SKIP_PROPERTY)})"
        )
    }
}
