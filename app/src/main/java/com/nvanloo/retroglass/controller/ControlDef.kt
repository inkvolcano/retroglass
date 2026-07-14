package com.nvanloo.retroglass.controller

import android.graphics.Color

enum class ControlType { BUTTON, DPAD, STICK }

enum class ControlShape { CIRCLE, PILL, BAR, CROSS, PSX_CROSS, STICK }

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
)

/** User-adjustable placement of a control: position plus a scale multiplier. */
data class ControlPlacement(
    var cx: Float,
    var cy: Float,
    var scale: Float = 1f,
)
