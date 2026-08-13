plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.skaldoria.cv"
version = rootProject.version

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").isPresent)
    }
}

dependencies {
    implementation(project(":skaldoria-markdown"))
    testImplementation(kotlin("test"))
}
