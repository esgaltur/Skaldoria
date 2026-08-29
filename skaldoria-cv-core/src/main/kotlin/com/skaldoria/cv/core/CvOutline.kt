package com.skaldoria.cv.core

/** Whether an outline row is a level-two section or a level-three entry beneath one. */
enum class CvOutlineLevel { Section, Entry }

/**
 * One navigable row of the document outline — CV-FR-024.
 *
 * [source] is the whole point of the type: an outline that could only name its rows would be a
 * table of contents, not navigation. The line it carries is what moves the caret and, through
 * [com.skaldoria.cv.core.layout.CvResolvedLayout.pageContaining], the preview.
 */
data class CvOutlineItem(
    val title: String,
    val level: CvOutlineLevel,
    val source: SourceRange,
    /** Null for entries, which take their meaning from the section containing them. */
    val kind: CvSectionKind? = null
)

/**
 * Flattens the semantic document into the outline the navigator renders.
 *
 * Derived on demand rather than stored on [CvDocument], because it is a projection of structure
 * the adapter already produced — a second copy could only ever disagree with the first.
 */
object CvOutline {

    fun of(document: CvDocument): List<CvOutlineItem> = buildList {
        for (section in document.sections) {
            add(
                CvOutlineItem(
                    title = section.title,
                    level = CvOutlineLevel.Section,
                    source = section.source,
                    kind = section.kind
                )
            )
            for (entry in section.entries) {
                add(
                    CvOutlineItem(
                        title = entry.title,
                        level = CvOutlineLevel.Entry,
                        source = entry.source
                    )
                )
            }
        }
    }

    /**
     * The row the caret currently sits in: the last one that starts at or before [line].
     *
     * Null before the first section, where the caret is in the header and no row owns it —
     * highlighting section one there would claim a position the user is not at.
     */
    fun activeAt(items: List<CvOutlineItem>, line: Int): CvOutlineItem? =
        items.lastOrNull { it.source.startLine <= line }
}
