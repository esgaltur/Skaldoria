plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

group = "com.skaldoria.writer"
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
        mainClass = "com.skaldoria.writer.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg
            )
            packageName = "SkaldoriaWriter"
            packageVersion = rootProject.version.toString()
            description = "Skaldoria Writer - Distraction Free Markdown Editor"
            vendor = "Skaldoria"

            val iconsDir = project.file("src/desktopMain/resources/icons")
            windows {
                iconFile.set(iconsDir.resolve("writer.ico"))
            }
            linux {
                iconFile.set(iconsDir.resolve("writer.png"))
            }
        }
    }
}
