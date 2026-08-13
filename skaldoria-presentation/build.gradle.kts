plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

group = rootProject.group
version = rootProject.version

/** Generates runtime build metadata from the root project's single version authority. */
val generateBuildInfo = tasks.register("generateBuildInfo") {
    group = "build"
    description = "Generates the presentation application's BuildInfo Kotlin source."
    val outputDir = layout.buildDirectory.dir("generated/source/buildinfo")
    val appVersion = rootProject.version.toString()
    inputs.property("appVersion", appVersion)
    outputs.dir(outputDir)

    doLast {
        val packageDir = outputDir.get().asFile.resolve("com/skaldoria")
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.kt").writeText(
            """
            package com.skaldoria

            /** Generated build metadata. Change the version in the root build.gradle.kts. */
            object BuildInfo {
                const val VERSION: String = "$appVersion"
                const val DISPLAY_VERSION: String = "v$appVersion"
            }

            """.trimIndent()
        )
    }
}

kotlin {
    jvm("desktop")

    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").isPresent)
    }

    sourceSets {
        getByName("desktopMain") {
            kotlin.srcDir(generateBuildInfo)

            dependencies {
                implementation(project(":skaldoria-markdown"))
                implementation(project(":skaldoria-shared-ui"))

                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Several integration tests intentionally consume root-level examples and README content.
    // Pin the repository root instead of depending on Gradle's implicit working-directory
    // choice, which changed when the application moved out of the root project.
    workingDir = rootProject.projectDir

    systemProperty(
        "skaldoria.configDir",
        layout.buildDirectory.dir("test-config").get().asFile.absolutePath
    )

    if (providers.gradleProperty("skipRenderTests").isPresent) {
        systemProperty("skaldoria.skipRenderTests", "true")
    }
}

compose.desktop {
    application {
        mainClass = "com.skaldoria.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg
            )
            packageName = "Skaldoria"
            packageVersion = rootProject.version.toString()
            description = "Skaldoria - Native Markdown Presentation Studio"
            vendor = "Skaldoria"

            val iconsDir = project.file("src/desktopMain/resources/icons")
            windows { iconFile.set(iconsDir.resolve("app.ico")) }
            linux { iconFile.set(iconsDir.resolve("app.png")) }
        }
    }
}
