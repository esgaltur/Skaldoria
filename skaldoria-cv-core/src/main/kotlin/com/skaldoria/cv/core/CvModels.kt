package com.skaldoria.cv.core

data class SourceRange(val startLine: Int, val endLine: Int = startLine) {
    init {
        require(startLine > 0) { "Source lines are one-based" }
        require(endLine >= startLine) { "End line must not precede start line" }
    }
}

enum class DiagnosticSeverity { Warning, Error }

data class CvDiagnostic(
    val code: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val action: String,
    val source: SourceRange
)

enum class CvSectionKind {
    Profile,
    Experience,
    Education,
    Skills,
    Projects,
    Certifications,
    Publications,
    Languages,
    Volunteering,
    Custom
}

enum class CvBlockKind { Paragraph, ListItem, Divider, Unsupported }

data class CvBlock(
    val kind: CvBlockKind,
    val markdown: String,
    val source: SourceRange,
    val isOrdered: Boolean = false
)

enum class ContactKind { Email, Telephone, Location, Web }

data class CvContact(
    val kind: ContactKind,
    val label: String?,
    val value: String,
    val target: String?,
    val source: SourceRange
)

data class CvEntry(
    val title: String,
    val source: SourceRange,
    val content: List<CvBlock>
)

data class CvSection(
    val title: String,
    val kind: CvSectionKind,
    val source: SourceRange,
    val introduction: List<CvBlock>,
    val entries: List<CvEntry>
)

data class CvDocument(
    val sourceMarkdown: String,
    val candidateName: String?,
    val professionalHeadline: String?,
    val metadata: Map<String, String>,
    val headerContent: List<CvBlock>,
    val contacts: List<CvContact>,
    val sections: List<CvSection>,
    val diagnostics: List<CvDiagnostic>
) {
    val hasErrors: Boolean get() = diagnostics.any { it.severity == DiagnosticSeverity.Error }
}
