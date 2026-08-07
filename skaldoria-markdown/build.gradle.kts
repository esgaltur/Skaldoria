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
