plugins {
    // The root is an aggregator and the single version authority. Product plugins belong to
    // product modules, so adding another application never turns the root back into one.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "com.skaldoria"

/** Single source of truth for every Skaldoria application and release artifact. */
val appVersion = "1.2.0"
version = appVersion

/**
 * Local releases build Windows and Linux from the same checkout. Give those builds isolated
 * outputs so an IDE build—or the other operating system—cannot replace classes and jpackage
 * inputs while a release gate is running.
 */
providers.gradleProperty("releaseBuildRoot").orNull?.let { relativeRoot ->
    val releaseRoot = layout.projectDirectory.dir(relativeRoot)
    subprojects {
        layout.buildDirectory.set(releaseRoot.dir(name))
    }
}

/** Makes the authoritative version available to local release scripts. */
tasks.register("printVersion") {
    group = "help"
    description = "Prints the application version, for the local release scripts to read."
    val versionToPrint = appVersion
    doLast { println(versionToPrint) }
}
