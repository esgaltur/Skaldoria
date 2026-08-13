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
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
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
