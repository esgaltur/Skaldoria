package com.skaldoria.core.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for spacing and sizing across all slides and diagrams.
 *
 * Spacing follows an 8pt scale: 4/8/12/16/20/24/28/32/36/40/44/48... This ensures
 * consistent rhythm and makes layout math predictable across all components.
 *
 * Named card tokens (header height, icon size, border width, radii) centralize the
 * shared frame styling that was previously duplicated in MermaidDiagramCanvas,
 * MathFormulaRenderer, and slide layouts.
 */
object DesignTokens {

    // Spacing scale (8pt grid)
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val xxxl = 28.dp
        val huge = 32.dp
        val massive = 36.dp
        val giant = 40.dp
        val colossal = 44.dp
        val titanic = 48.dp
    }

    // Card frame tokens (shared by MermaidDiagramCanvas, MathFormulaRenderer)
    object Card {
        val cornerRadius = 16.dp
        val borderWidth = 1.dp
        val headerHeight = 44.dp
        val headerPaddingHorizontal = 16.dp
        val headerPaddingVertical = 12.dp
        val contentPaddingHorizontal = 20.dp
        val contentPaddingVertical = 14.dp
    }

    // Diagram card header styling
    object CardHeader {
        val iconSize = 18.dp
        val actionButtonSize = 28.dp
        val labelFontSize = 11.sp
        val labelLetterSpacing = 1.sp
        val titleFontSize = 14.sp
        val titleLineHeight = 22.sp
    }

    // Slide layout padding
    object Slide {
        val horizontalPaddingWide = 48.dp
        val horizontalPaddingStandard = 44.dp
        val verticalPadding = 36.dp
    }

    // Diagram-specific spacing
    object Diagram {
        // Flowchart
        val flowchartSiblingGap = 26.dp
        val flowchartMinLaneGap = 68.dp
        val flowchartNodeRadiusRounded = 12.dp
        val flowchartNodeRadiusDiamond = 8.dp

        // Sequence
        val sequenceHeaderHeight = 44f
        val sequenceRowHeight = 54f
        val sequenceTopPadding = 8f
        val sequenceSidePadding = 16f
        val sequenceSelfCallWidth = 46f
        val sequenceActivationWidth = 10f
    }

    // Typography for diagrams
    object DiagramText {
        val edgeLabelFontSize = 10.sp
        val edgeLabelWeight = 600 // semibold
        val nodeMainFontSize = 15.sp
        val nodeIdFontSize = 11.sp
    }

    // Drawing primitives
    object DrawPrimitive {
        val arrowheadLength = 10f
        val arrowheadSpread = 5.5f
        val strokeWidth = 2f
        val edgeLabelBoxPadding = 8f
        val edgeLabelBoxVerticalPadding = 4f

        // Dash patterns
        val dashSequenceLifeline = floatArrayOf(6f, 8f)
        val dashSequenceMessage = floatArrayOf(7f, 5f)
        val dashFlowchartEdge = floatArrayOf(8f, 6f)
    }
}
