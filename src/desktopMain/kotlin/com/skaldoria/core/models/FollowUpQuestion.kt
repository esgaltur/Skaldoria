package com.skaldoria.core.models

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Domain model representing an unanswered question, parking lot item, or follow-up action item
 * tracked alongside the presentation.
 */
data class FollowUpQuestion(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val isAnswered: Boolean = false,
    val answerText: String = "",
    val slideIndex: Int? = null,
    val timestamp: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val author: String? = null
)
