plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

group = "com.skaldoria.cv"
version = rootProject.version

kotlin {
    jvm("desktop")

    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").isPresent)
    }

    sourceSets {
        getByName("desktopMain") {
            dependencies {
                implementation(project(":skaldoria-cv-core"))
                implementation(project(":skaldoria-shared-ui"))
                // The shared line grammar and highlight tokenizer. Without this the editor's
                // highlighter reimplemented both, and drifted from CvMarkdownAdapter.
                implementation(project(":skaldoria-markdown"))
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.extended)
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                // Test-only, as in :skaldoria-cv-core — reads back what the hand-rolled writer
                // produced, here through the real Compose measurer rather than a fake.
                implementation(libs.pdfbox)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.skaldoria.cv.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg
            )
            packageName = "SkaldoriaCV"
            packageVersion = rootProject.version.toString()
            description = "Skaldoria CV - Markdown CV and resume authoring"
            vendor = "Skaldoria"

            val iconsDir = project.file("src/desktopMain/resources/icons")
            windows {
                iconFile.set(iconsDir.resolve("cv.ico"))
            }
            linux {
                iconFile.set(iconsDir.resolve("cv.png"))
            }
        }
    }
}
