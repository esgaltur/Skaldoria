package com.skaldoria.cv.core

import com.skaldoria.markdown.parser.FenceInfo
import com.skaldoria.markdown.parser.FenceRules
import com.skaldoria.markdown.parser.HeadingRules
import com.skaldoria.markdown.parser.ListRules
import com.skaldoria.markdown.parser.ThematicBreakRules

/** Converts standard Markdown structure into the renderer-independent CV domain model. */
class CvMarkdownAdapter {
    fun parse(source: String): CvDocument {
        val lines = source.split('\n').map { it.removeSuffix("\r") }
        val diagnostics = mutableListOf<CvDiagnostic>()
        val metadata = linkedMapOf<String, String>()
        val headerBlocks = mutableListOf<CvBlock>()
        val contacts = mutableListOf<CvContact>()
        val sections = mutableListOf<MutableSection>()
        var candidateName: String? = null
        var currentSection: MutableSection? = null
        var currentEntry: MutableEntry? = null
        var openFence: FenceInfo? = null
        var lineIndex = parseFrontMatter(lines, metadata, diagnostics)

        while (lineIndex < lines.size) {
            val lineNumber = lineIndex + 1
            val line = lines[lineIndex]
            val activeFence = openFence
            if (activeFence != null) {
                if (FenceRules.closes(line, activeFence)) openFence = null
                lineIndex++
                continue
            }

            FenceRules.openingFence(line)?.let { fence ->
                openFence = fence
                addBlock(
                    CvBlock(CvBlockKind.Unsupported, line, SourceRange(lineNumber)),
                    headerBlocks,
                    currentSection,
                    currentEntry
                )
                diagnostics += diagnostic(
                    code = "CV_UNSUPPORTED_FENCE",
                    severity = DiagnosticSeverity.Warning,
                    message = "Fenced code is preserved in the source but omitted from the ATS preview.",
                    action = "Replace the code block with concise prose or list items.",
                    line = lineNumber
                )
                lineIndex++
                continue
            }

            val heading = HeadingRules.heading(line)
            if (heading != null) {
                when (heading.level) {
                    1 -> {
                        if (candidateName == null) {
                            candidateName = heading.text
                        } else {
                            diagnostics += diagnostic(
                                code = "CV_MULTIPLE_IDENTITIES",
                                severity = DiagnosticSeverity.Error,
                                message = "A CV must have exactly one level-one candidate heading.",
                                action = "Change this heading to level two or remove it.",
                                line = lineNumber
                            )
                        }
                        currentSection = null
                        currentEntry = null
                    }

                    2 -> {
                        currentSection = MutableSection(
                            title = heading.text,
                            kind = sectionKind(heading.text),
                            source = SourceRange(lineNumber)
                        ).also(sections::add)
                        currentEntry = null
                    }

                    3 -> {
                        val section = currentSection ?: MutableSection(
                            title = "Unsectioned",
                            kind = CvSectionKind.Custom,
                            source = SourceRange(lineNumber)
                        ).also {
                            sections += it
                            currentSection = it
                            diagnostics += diagnostic(
                                code = "CV_ENTRY_WITHOUT_SECTION",
                                severity = DiagnosticSeverity.Error,
                                message = "Entry '${heading.text}' is not inside a level-two section.",
                                action = "Add a level-two section heading before this entry.",
                                line = lineNumber
                            )
                        }
                        currentEntry = MutableEntry(heading.text, SourceRange(lineNumber)).also(section.entries::add)
                    }

                    else -> {
                        addBlock(
                            CvBlock(CvBlockKind.Unsupported, heading.text, SourceRange(lineNumber)),
                            headerBlocks,
                            currentSection,
                            currentEntry
                        )
                        diagnostics += diagnostic(
                            code = "CV_AMBIGUOUS_HEADING",
                            severity = DiagnosticSeverity.Warning,
                            message = "Level-${heading.level} headings have no CV structural meaning.",
                            action = "Use level two for sections or level three for entries.",
                            line = lineNumber
                        )
                    }
                }
                lineIndex++
                continue
            }

            if (line.isBlank()) {
                lineIndex++
                continue
            }

            val listItem = ListRules.listItem(line)
            val block = when {
                listItem != null -> CvBlock(
                    kind = CvBlockKind.ListItem,
                    markdown = listItem.text,
                    source = SourceRange(lineNumber),
                    isOrdered = listItem.isOrdered
                )
                ThematicBreakRules.isThematicBreak(line) -> CvBlock(
                    CvBlockKind.Divider,
                    markdown = "",
                    source = SourceRange(lineNumber)
                )
                else -> CvBlock(CvBlockKind.Paragraph, line.trim(), SourceRange(lineNumber))
            }
            val lineContacts = contactItems(line, lineNumber)
            val isHeaderContactLine = candidateName != null && currentSection == null && currentEntry == null &&
                lineContacts.isNotEmpty() && isContactOnlyLine(line)
            if (!isHeaderContactLine) addBlock(block, headerBlocks, currentSection, currentEntry)
            contacts += lineContacts
            lineIndex++
        }

        if (openFence != null) {
            diagnostics += diagnostic(
                code = "CV_UNCLOSED_FENCE",
                severity = DiagnosticSeverity.Error,
                message = "The fenced code block is not closed.",
                action = "Add a matching closing fence.",
                line = lines.size.coerceAtLeast(1)
            )
        }
        if (candidateName == null) {
            diagnostics += diagnostic(
                code = "CV_MISSING_IDENTITY",
                severity = DiagnosticSeverity.Error,
                message = "The CV has no candidate identity.",
                action = "Add one level-one heading, for example '# Ada Lovelace'.",
                line = 1
            )
        }

        return CvDocument(
            sourceMarkdown = source,
            candidateName = candidateName,
            professionalHeadline = metadata["headline"]?.takeIf(String::isNotBlank),
            metadata = metadata.toMap(),
            headerContent = headerBlocks.toList(),
            contacts = contacts.distinctBy { it.kind to it.target },
            sections = sections.map(MutableSection::freeze),
            diagnostics = diagnostics.sortedWith(compareBy({ it.source.startLine }, { it.code }))
        )
    }

    private fun parseFrontMatter(
        lines: List<String>,
        metadata: MutableMap<String, String>,
        diagnostics: MutableList<CvDiagnostic>
    ): Int {
        if (lines.firstOrNull()?.trim() != "---") return 0
        val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
        if (closingIndex == null) {
            diagnostics += diagnostic(
                code = "CV_UNCLOSED_METADATA",
                severity = DiagnosticSeverity.Error,
                message = "YAML front matter has no closing delimiter.",
                action = "Add a closing '---' line before the CV content.",
                line = 1
            )
            return 0
        }

        for (index in 1 until closingIndex) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith('#')) continue
            val separator = line.indexOf(':')
            if (separator <= 0 || separator == line.lastIndex) {
                diagnostics += diagnostic(
                    code = "CV_MALFORMED_METADATA",
                    severity = DiagnosticSeverity.Error,
                    message = "Metadata must use 'key: value' syntax.",
                    action = "Correct or remove this metadata line.",
                    line = index + 1
                )
                continue
            }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().removeSurrounding("\"")
            metadata[key] = value
        }
        return closingIndex + 1
    }

    private fun addBlock(
        block: CvBlock,
        headerBlocks: MutableList<CvBlock>,
        section: MutableSection?,
        entry: MutableEntry?
    ) {
        when {
            entry != null -> entry.content += block
            section != null -> section.introduction += block
            else -> headerBlocks += block
        }
    }

    private fun contactItems(line: String, lineNumber: Int): List<CvContact> {
        val result = mutableListOf<CvContact>()
        LOCATION.matchEntire(line)?.let { match ->
            val value = match.groupValues[1].trim()
            result += CvContact(ContactKind.Location, null, value, null, SourceRange(lineNumber))
        }
        MARKDOWN_LINK.findAll(line).forEach { match ->
            val label = match.groupValues[1].ifBlank { null }
            val target = match.groupValues[2]
            contact(target, label, lineNumber)?.let(result::add)
        }
        EMAIL.findAll(line).forEach { match ->
            val value = match.value
            result += CvContact(ContactKind.Email, null, value, "mailto:$value", SourceRange(lineNumber))
        }
        URL.findAll(line).forEach { match ->
            result += CvContact(ContactKind.Web, null, match.value, match.value, SourceRange(lineNumber))
        }
        return result
    }

    private fun isContactOnlyLine(line: String): Boolean {
        if (LOCATION.matches(line)) return true
        val withoutLinks = MARKDOWN_LINK.replace(line, "")
        val withoutContacts = URL.replace(EMAIL.replace(withoutLinks, ""), "")
        return withoutContacts.all { it.isWhitespace() || it in "·|,;" }
    }

    private fun contact(target: String, label: String?, lineNumber: Int): CvContact? {
        val kind = when {
            target.startsWith("mailto:", ignoreCase = true) -> ContactKind.Email
            target.startsWith("tel:", ignoreCase = true) -> ContactKind.Telephone
            target.startsWith("https://", ignoreCase = true) || target.startsWith("http://", ignoreCase = true) -> ContactKind.Web
            else -> return null
        }
        return CvContact(kind, label, target.substringAfter(':'), target, SourceRange(lineNumber))
    }

    private fun sectionKind(title: String): CvSectionKind = when (title.trim().lowercase()) {
        "profile", "summary", "about" -> CvSectionKind.Profile
        "experience", "work experience", "employment" -> CvSectionKind.Experience
        "education" -> CvSectionKind.Education
        "skills", "technical skills" -> CvSectionKind.Skills
        "projects" -> CvSectionKind.Projects
        "certifications", "certificates" -> CvSectionKind.Certifications
        "publications" -> CvSectionKind.Publications
        "languages" -> CvSectionKind.Languages
        "volunteering", "volunteer experience" -> CvSectionKind.Volunteering
        else -> CvSectionKind.Custom
    }

    private fun diagnostic(
        code: String,
        severity: DiagnosticSeverity,
        message: String,
        action: String,
        line: Int
    ) = CvDiagnostic(code, severity, message, action, SourceRange(line))

    private data class MutableEntry(
        val title: String,
        val source: SourceRange,
        val content: MutableList<CvBlock> = mutableListOf()
    ) {
        fun freeze() = CvEntry(title, source, content.toList())
    }

    private data class MutableSection(
        val title: String,
        val kind: CvSectionKind,
        val source: SourceRange,
        val introduction: MutableList<CvBlock> = mutableListOf(),
        val entries: MutableList<MutableEntry> = mutableListOf()
    ) {
        fun freeze() = CvSection(title, kind, source, introduction.toList(), entries.map(MutableEntry::freeze))
    }

    private companion object {
        val MARKDOWN_LINK = Regex("""\[([^]]*)]\(([^)\s]+)\)""")
        val EMAIL = Regex("""(?<![:/\w.])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}(?![\w.])""", RegexOption.IGNORE_CASE)
        val URL = Regex("""https?://[^\s)>]+""", RegexOption.IGNORE_CASE)
        val LOCATION = Regex("""^\s*(?:location|based in)\s*:\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
    }
}
