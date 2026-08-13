package com.skaldoria.cv

import java.nio.charset.StandardCharsets

object CvExamples {
    private const val SOFTWARE_ENGINEER_RESOURCE = "/examples/software-engineer-cv.md"

    fun softwareEngineer(): String {
        val stream = checkNotNull(CvExamples::class.java.getResourceAsStream(SOFTWARE_ENGINEER_RESOURCE)) {
            "Missing bundled software engineer CV example: $SOFTWARE_ENGINEER_RESOURCE"
        }
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
