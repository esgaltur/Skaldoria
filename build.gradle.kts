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

/** Makes the authoritative version available to local release scripts. */
tasks.register("printVersion") {
    group = "help"
    description = "Prints the application version, for the local release scripts to read."
    val versionToPrint = appVersion
    doLast { println(versionToPrint) }
}
