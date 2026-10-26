plugins {
    kotlin("jvm")
}

group = "com.skaldoria"

/**
 * The markdown engine: slide model, parser, block rules, layout classification.
 *
 * **The absence of a Compose dependency is the point of this module, not an accident.** Keeping
 * it Compose-free is what makes the parser measurable in isolation (Phase D's experiments all
 * depend on that), and what would let an IntelliJ plugin consume the engine without dragging a
 * desktop UI toolkit behind it.
 *
 * If something here starts needing `androidx.compose.*`, that is a signal the type belongs in
 * the app module instead — `AnnotationStroke` was moved back out for exactly this reason.
 *
 * **`com.skaldoria.core.*` are the tenants here that are not markdown**, and both are here for the
 * same reason: all four applications already depend on this module, and it is the only Compose-free
 * one they share, so nothing about the dependency graph changed to admit them.
 *
 *  - `core.json` arrived from `:skaldoria-presentation` because `:skaldoria-canvas` had grown a
 *    *second* hand-rolled JSON reader — a heuristic tokenizer that silently accepted malformed
 *    documents and handed back an empty canvas, which the next save then wrote to disk (COR-15).
 *  - `core.io` holds `AtomicFileWriter`. Three modules had written their own copy of it and the
 *    writer had none, saving with `writeText` and truncating the user's document on any
 *    interruption (COR-17).
 *
 * The `com.skaldoria.core.*` namespace is deliberate rather than `com.skaldoria.markdown.*`: these
 * are general-purpose utilities squatting in a domain module. If a `:skaldoria-core` is ever worth
 * its own Gradle wiring — settings, four build files, CI and three release scripts — moving them
 * there is a `git mv` that touches no import.
 */
kotlin {
    compilerOptions {
        // Matches the root project: CI turns warnings into errors with -PwarningsAsErrors.
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").isPresent)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
