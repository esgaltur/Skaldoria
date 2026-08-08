plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

group = "com.skaldoria.canvas"
version = rootProject.version

kotlin {
    jvm("desktop")

    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").isPresent)
    }

    sourceSets {
        getByName("desktopMain") {
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
                implementation(kotlin("test"))
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.skaldoria.canvas.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg
            )
            packageName = "SkaldoriaCanvas"
            packageVersion = rootProject.version.toString()
            description = "Skaldoria Canvas - Spatial Markdown Whiteboard & Presentation Compiler"
            vendor = "Skaldoria"

            val iconsDir = project.file("src/desktopMain/resources/icons")
            windows {
                iconFile.set(iconsDir.resolve("canvas.ico"))
            }
            linux {
                iconFile.set(iconsDir.resolve("canvas.png"))
            }
        }
    }
}
