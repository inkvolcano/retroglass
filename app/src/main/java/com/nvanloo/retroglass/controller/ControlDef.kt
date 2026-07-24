package com.nvanloo.retroglass.controller

import android.graphics.Color

enum class ControlType { BUTTON, DPAD, STICK }

enum class ControlShape { CIRCLE, PILL, BAR, CROSS, PSX_CROSS, STICK }

/**
 * Where a pill sits inside a *combined divided pill* — the handoff's fallback when separate
 * buttons will not fit (SELECT | START, or L2 | L1).
 *
 * Each segment stays its own [ControlDef], so hit-testing, remapping and turbo all keep working
 * per button; the segment only changes how it is drawn. Outer corners round, inner corners
 * square, and a divider is drawn on inner edges only — the handoff is explicit that the outer
 * sides carry no divider.
 */
enum class PillSegment { NONE, LEFT, MID, RIGHT }

/**
 * Static definition of a single on-screen control.
 *
 * [x], [y] are the normalized center (0..1 of the controller view's width/height).
 * [size] is the control's diameter/side as a fraction of the view's shorter edge.
 */
data class ControlDef(
    val id: String,
    val type: ControlType,
    val label: String,
    val keyCode: Int = 0,
    val x: Float,
    val y: Float,
    val size: Float,
    val shape: ControlShape,
    val fillColor: Int,
    val labelColor: Int,
    val strokeColor: Int = Color.TRANSPARENT,
    val plateColor: Int = Color.TRANSPARENT,
    /** Position within a combined divided pill; [PillSegment.NONE] for an ordinary control. */
    val segment: PillSegment = PillSegment.NONE,
    /** Multiplies a pill's width, so N segments can share one pill's footprint (1/N each). */
    val widthScale: Float = 1f,
    /**
     * Pills sharing a group may be merged into one combined divided pill *if they would
     * otherwise overlap on this device*. The decision is made at layout time by
     * `ControllerView.resolveCombineGroups`, because it needs real pixels: a control's size is a
     * fraction of the pad's shorter edge while zone widths are fractions of its width, so
     * whether two pills collide depends on the pad's aspect ratio, not on authored numbers.
     */
    val combineGroup: String? = null,
)

/** User-adjustable placement of a control: position plus a scale multiplier. */
data class ControlPlacement(
    var cx: Float,
    var cy: Float,
    var scale: Float = 1f,
)
