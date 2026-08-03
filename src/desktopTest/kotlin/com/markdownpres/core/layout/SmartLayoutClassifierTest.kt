package com.markdownpres.core.layout

import com.markdownpres.core.models.SlideElement
import com.markdownpres.core.models.SlideLayoutType
import kotlin.test.Test
import kotlin.test.assertEquals

class SmartLayoutClassifierTest {

    @Test
    fun testClassifyHeroTitleOnFirstSlide() {
        val elements = emptyList<SlideElement>()
        val layout = SmartLayoutClassifier.classify(
            title = "Keynote 2026",
            elements = elements,
            isFirstSlide = true
        )
        assertEquals(SlideLayoutType.HERO_TITLE, layout)
    }

    @Test
    fun testClassifySectionHeaderWhenEmptyOnLaterSlide() {
        val elements = emptyList<SlideElement>()
        val layout = SmartLayoutClassifier.classify(
            title = "Architecture Deep Dive",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.SECTION_HEADER, layout)
    }

    @Test
    fun testClassifyFullCode() {
        val elements = listOf(
            SlideElement.CodeBlock(
                code = "fun main() = println(\"Hello World\")",
                language = "kotlin"
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Main Entry Point",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.FULL_CODE, layout)
    }

    @Test
    fun testClassifySplitTextCode() {
        val elements = listOf(
            SlideElement.Text("Here is how the pipeline operates:"),
            SlideElement.CodeBlock(
                code = "val pipeline = Pipeline()",
                language = "kotlin"
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Pipeline Architecture",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.SPLIT_TEXT_CODE, layout)
    }

    @Test
    fun testClassifySplitTextMedia() {
        val elements = listOf(
            SlideElement.Text("System diagram of edge clusters:"),
            SlideElement.Image(
                url = "https://example.com/diag.png",
                altText = "Cluster Diagram"
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Edge Clusters",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.SPLIT_TEXT_MEDIA, layout)
    }

    @Test
    fun testClassifyBigQuote() {
        val elements = listOf(
            SlideElement.Quote(
                quote = "Premature optimization is the root of all evil.",
                author = "Donald Knuth"
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Guiding Principle",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.BIG_QUOTE, layout)
    }

    @Test
    fun testClassifyBigMetric() {
        val elements = listOf(
            SlideElement.Metric(
                value = "10x",
                label = "Faster Compilation Times"
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Speed Benchmark",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.BIG_METRIC, layout)
    }

    @Test
    fun testClassifyDataTable() {
        val elements = listOf(
            SlideElement.Table(
                headers = listOf("Metric", "v1.0", "v2.0"),
                rows = listOf(
                    listOf("Throughput", "10k req/s", "150k req/s"),
                    listOf("P99 Latency", "45ms", "2ms")
                )
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Comparative Analysis",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.DATA_TABLE, layout)
    }

    @Test
    fun testClassifyBulletList() {
        val elements = listOf(
            SlideElement.BulletList(
                items = listOf(
                    "Zero memory overhead",
                    "Native 120 FPS rendering",
                    "Pure Markdown input"
                )
            )
        )
        val layout = SmartLayoutClassifier.classify(
            title = "Key Takeaways",
            elements = elements,
            isFirstSlide = false
        )
        assertEquals(SlideLayoutType.BULLET_LIST, layout)
    }
}
