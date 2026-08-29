package com.skaldoria.cv

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CvFileControllerTest {
    @Test
    fun `atomic write preserves UTF-8 source exactly`() {
        val directory = createTempDirectory("skaldoria-cv-test")
        try {
            val destination = directory.resolve("résumé.md")
            val source = "# Jiří Novák\r\n\r\n## Dovednosti\r\n\r\n- Kotlin 🟣"

            writeAtomically(destination.toFile(), source)

            assertEquals(source, Files.readString(destination))
            assertFalse(directory.toFile().listFiles().orEmpty().any { it.extension == "tmp" })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
