package com.skaldoria.cv.core

data class MeasuredPageItem<T>(
    val content: T,
    val extent: Double,
    val keepWithNext: Boolean = false,
    val keepTogetherGroup: String? = null
) {
    init {
        require(extent >= 0.0) { "Page item extent must not be negative" }
    }
}

/** Packs already measured content without changing its order or stranding marked headings. */
object PagePacker {
    fun <T> pack(items: List<MeasuredPageItem<T>>, pageExtent: Double): List<List<T>> {
        require(pageExtent > 0.0) { "Page extent must be positive" }
        if (items.isEmpty()) return listOf(emptyList())

        val pages = mutableListOf<MutableList<T>>()
        var page = mutableListOf<T>()
        var used = 0.0

        items.forEachIndexed { index, item ->
            val groupExtent = item.keepTogetherGroup?.let { group ->
                if (index > 0 && items[index - 1].keepTogetherGroup == group) {
                    null
                } else {
                    items.drop(index).takeWhile { it.keepTogetherGroup == group }.sumOf { it.extent }
                }
            }
            if (groupExtent != null && groupExtent <= pageExtent && page.isNotEmpty() && used + groupExtent > pageExtent) {
                pages += page
                page = mutableListOf()
                used = 0.0
            }
            val nextExtent = if (item.keepWithNext) items.getOrNull(index + 1)?.extent ?: 0.0 else 0.0
            val cohesiveExtent = item.extent + nextExtent
            if (page.isNotEmpty() && used + cohesiveExtent > pageExtent) {
                pages += page
                page = mutableListOf()
                used = 0.0
            }
            if (page.isNotEmpty() && used + item.extent > pageExtent) {
                pages += page
                page = mutableListOf()
                used = 0.0
            }
            page += item.content
            used += item.extent
        }
        if (page.isNotEmpty()) pages += page
        return pages
    }
}
