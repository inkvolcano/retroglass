package com.nvanloo.retroglass.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The moulded lip the game's picture sits in.
 *
 * Drawn as a transparent overlay *above* the game rather than behind it. The GL surface covers
 * its whole view and clears to its own colour before drawing, so anything painted underneath is
 * invisible — in landscape, where the game view fills the window, that is the entire screen.
 * The shell colour is handled at the source instead (GLRetroView.setLetterboxColor); this view
 * only draws the rim, hugging the picture's real edges.
 *
 * The bevel is [Bevel.gradient] inverted: the screen is sunk into the shell, so it catches the
 * light on the opposite edge from a raised button. Unlike the buttons it keeps an ambient lip
 * when the phone is level — a physical screen surround does not vanish when you hold the thing
 * still, and with the tilt effect fading to nothing at rest there would otherwise be no bezel
 * at all most of the time.
 */
class ScreenBezelView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimRect = RectF()
    private val picture = RectF()

    private var lightX = 0f
    private var lightY = -1f
    private var strength = 0f

    /** The picture's bounds in this view's coordinates. */
    fun setPicture(l: Float, t: Float, r: Float, b: Float) {
        if (picture.left == l && picture.top == t && picture.right == r && picture.bottom == b) return
        picture.set(l, t, r, b)
        invalidate()
    }

    /** Light direction and strength; see ControllerView.currentLight(). */
    fun setLight(x: Float, y: Float, s: Float) {
        if (x == lightX && y == lightY && s == strength) return
        lightX = x
        lightY = y
        strength = s
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (picture.isEmpty) return
        val w = dp(RIM_DP)
        rimRect.set(
            picture.left - w / 2f, picture.top - w / 2f,
            picture.right + w / 2f, picture.bottom + w / 2f,
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w

        // Ambient lip, lit from above and always present.
        paint.shader = Bevel.gradient(rimRect, 0f, -1f, AMBIENT, invert = true)
        canvas.drawRect(rimRect, paint)

        // Tilt on top of it, so turning the phone rolls the light around the surround.
        if (strength >= 0.02f) {
            paint.shader = Bevel.gradient(rimRect, lightX, lightY, strength, invert = true)
            canvas.drawRect(rimRect, paint)
        }
        paint.shader = null
    }

    private companion object {
        /** Depth of the cut the screen sits in. */
        const val RIM_DP = 9f
        /** The lip you see with the phone held level. */
        const val AMBIENT = 0.45f
    }
}
