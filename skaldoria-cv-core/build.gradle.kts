plugins {
    alias(libs.plugins.kotlin.jvm)
    // For `api`: CvTextMeasurer takes InlineRun in its signature, so anything implementing it
    // needs the grammar module on its compile classpath too.
    `java-library`
}

group = "com.skaldoria.cv"
version = rootProject.version

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").isPresent)
    }
}

dependencies {
    api(project(":skaldoria-markdown"))
    testImplementation(kotlin("test"))
    testImplementation(libs.pdfbox)
}

tasks.withType<Test>().configureEach {
    // The PDF conformance guards embed the bundled Roboto, which lives in the CV application's
    // resources. Pin the repository root rather than depending on Gradle's implicit choice — the
    // same convention `:skaldoria-presentation` uses for tests that read root-level examples.
    workingDir = rootProject.projectDir
}
