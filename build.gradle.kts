plugins {
    kotlin("multiplatform") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.markdownpres"
version = "1.0.0"

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                // JetBrains Official Markdown AST parser
                implementation("org.jetbrains:markdown:0.7.3")

                // Coroutines & Desktop Swing dispatcher
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.markdownpres.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "Skaldoria"
            packageVersion = "1.0.0"
            description = "Skaldoria — Native 120 FPS Markdown Presentation Studio"
            vendor = "Skaldoria"

            val iconsDir = project.file("src/desktopMain/resources/icons")
            windows {
                iconFile.set(iconsDir.resolve("app.ico"))
            }
            linux {
                iconFile.set(iconsDir.resolve("app.png"))
            }
        }
    }
}
