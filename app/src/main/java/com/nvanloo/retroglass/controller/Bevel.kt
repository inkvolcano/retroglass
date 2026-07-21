package com.nvanloo.retroglass.controller

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs

/**
 * The shared bevel gradient, so everything on screen is lit by one light.
 *
 * Used by [ControllerView] for the buttons and by [ScreenBezelView] for the recess the game
 * sits in — if these drifted apart the shell would look lit from two directions at once.
 */
object Bevel {

    const val HIGHLIGHT = 120
    const val SHADE = 130

    /**
     * A highlight → clear → shade ramp across [bounds] along the light axis (hx, hy).
     *
     * The extent is the shape's reach in the light's direction rather than its radius, so a
     * long thin shape lit across its short side still gets the full ramp.
     *
     * [invert] flips it: a recess catches the light on the opposite side from a bump, which is
     * what separates the screen's sunken edge from a raised key.
     */
    fun gradient(
        bounds: RectF,
        hx: Float,
        hy: Float,
        strength: Float,
        alpha: Int = 255,
        invert: Boolean = false,
    ): LinearGradient {
        val lx = if (invert) -hx else hx
        val ly = if (invert) -hy else hy
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val reach = abs(lx) * bounds.width() / 2f + abs(ly) * bounds.height() / 2f
        return LinearGradient(
            cx + lx * reach, cy + ly * reach,
            cx - lx * reach, cy - ly * reach,
            intArrayOf(
                Color.argb((HIGHLIGHT * strength).toInt() * alpha / 255, 255, 255, 255),
                Color.TRANSPARENT,
                Color.argb((SHADE * strength).toInt() * alpha / 255, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
}
