rootProject.name = "Skaldoria"

/**
 * The markdown engine, extracted so it can be compiled, tested and benchmarked without Compose
 * on the classpath. See `docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase A.
 */
include(":skaldoria-markdown")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
