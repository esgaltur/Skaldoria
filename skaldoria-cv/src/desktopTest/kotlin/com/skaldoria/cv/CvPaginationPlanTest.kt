package com.skaldoria.cv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Density
import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvThemeId
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class CvPaginationPlanTest {

    private fun plan(source: String): CvPagePlan {
        val document = CvMarkdownAdapter().parse(source)
        val font = CvFontResolver.resolve(com.skaldoria.cv.core.CvFontId.Roboto)
        val theme = CvPreviewThemes.resolve(CvThemeId.ModernBlue)
            .copy(bodyFont = font.family, headingFont = font.family)
        val layout = CvTemplateId.SoftwareEngineerAts.layout
        val paper = CvPaperSize.fromMetadata(document.metadata["paper"])

        val holder = arrayOfNulls<CvPagePlan>(1)
        val scene = ImageComposeScene(width = 900, height = 1400, density = Density(1f)) {
            SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
                holder[0] = planCvPages(document, theme, layout, paper)
                layout(constraints.minWidth, constraints.minHeight) {}
            }
        }
        try {
            scene.render(0L)
        } finally {
            scene.close()
        }
        return holder[0] ?: error("plan not captured")
    }

    @Test
    fun `dump example plan`() {
        val plan = plan(CvExamples.softwareEngineer())
        val g = plan.geometry
        println("=== contentHeight(pageExtent)=${g.contentHeight}  pageHeight=${g.pageHeight} ===")
        plan.pages.forEachIndexed { pageIndex, items ->
            val used = items.sumOf { plan.heights[it] }
            println("--- PAGE ${pageIndex + 1}  used=$used / ${g.contentHeight} ---")
            items.forEach { i ->
                println("   [%4d] %-28s h=%s".format(plan.heights[i], plan.groups[i] ?: "-", plan.descriptors[i]))
            }
        }
        println("=== SPLIT GROUPS: ${splitGroups(plan).keys} ===")
    }

    @Test
    fun `keep-together groups never span multiple pages`() {
        val plan = plan(CvExamples.softwareEngineer())
        val split = splitGroups(plan)
        assertTrue(split.isEmpty(), "keep-together groups split across pages: ${split.keys}")
    }

    @Test
    fun `a heading is never stranded as the last item of a non-final page`() {
        val plan = plan(CvExamples.softwareEngineer())
        val lastPage = plan.pages.lastIndex
        plan.pages.forEachIndexed { pageIndex, items ->
            if (pageIndex == lastPage) return@forEachIndexed
            val last = items.lastOrNull() ?: return@forEachIndexed
            val descriptor = plan.descriptors[last]
            assertTrue(
                !descriptor.startsWith("Section(") && !descriptor.startsWith("Entry("),
                "page ${pageIndex + 1} ends with an orphaned heading: $descriptor"
            )
        }
    }

    @Test
    fun `each page except the last is filled beyond half of the content height`() {
        val plan = plan(CvExamples.softwareEngineer())
        val lastPage = plan.pages.lastIndex
        val half = plan.geometry.contentHeight / 2
        plan.pages.forEachIndexed { pageIndex, items ->
            if (pageIndex == lastPage) return@forEachIndexed
            val used = items.sumOf { plan.heights[it] }
            assertTrue(
                used >= half,
                "page ${pageIndex + 1} only used $used / ${plan.geometry.contentHeight}; content is not flowing to fill it"
            )
        }
    }

    private fun splitGroups(plan: CvPagePlan): Map<String?, List<Int>> {
        val pageOf = HashMap<Int, Int>()
        plan.pages.forEachIndexed { pageIndex, items -> items.forEach { pageOf[it] = pageIndex } }
        return plan.groups.indices
            .filter { plan.groups[it] != null }
            .groupBy { plan.groups[it] }
            .filterValues { indices -> indices.map { pageOf[it] }.distinct().size > 1 }
    }
}
