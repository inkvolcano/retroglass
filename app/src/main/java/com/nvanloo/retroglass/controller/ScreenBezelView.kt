package com.nvanloo.retroglass.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The console shell the game sits in: the whole background painted in the console's body
 * colour, with the screen cut into it behind a bevelled edge.
 *
 * The bevel is the same one the buttons wear, but [Bevel.gradient] inverted — the screen is
 * sunk into the shell rather than standing on it, so it catches the light on the opposite side
 * from a raised key. Driven by the same [TiltSource] light, so the whole front panel reads as
 * one moulded piece under one lamp.
 */
class ScreenBezelView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimRect = RectF()

    /** The console's plastic. */
    var bodyColor: Int = Color.BLACK
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Where the game is, in this view's coordinates. Empty until the layout settles. */
    private val screenRect = RectF()

    private var lightX = 0f
    private var lightY = 0f
    private var strength = 0f

    fun setScreenRect(l: Float, t: Float, r: Float, b: Float) {
        if (screenRect.left == l && screenRect.top == t &&
            screenRect.right == r && screenRect.bottom == b
        ) {
            return
        }
        screenRect.set(l, t, r, b)
        invalidate()
    }

    /** Light direction and how strongly it applies; see ControllerView.lightVector(). */
    fun setLight(x: Float, y: Float, s: Float) {
        if (x == lightX && y == lightY && s == strength) return
        lightX = x
        lightY = y
        strength = s
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bodyColor)
        if (screenRect.isEmpty || strength < 0.02f) return

        // The rim sits just outside the picture, in the shell, so the game is never drawn over.
        val w = dp(RIM_DP)
        rimRect.set(
            screenRect.left - w / 2f, screenRect.top - w / 2f,
            screenRect.right + w / 2f, screenRect.bottom + w / 2f,
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.shader = Bevel.gradient(rimRect, lightX, lightY, strength, invert = true)
        // Square corners: the game's own edge is square, and a rounded rim around it would
        // leave the two disagreeing at every corner.
        canvas.drawRect(rimRect, paint)
        paint.shader = null
    }

    private companion object {
        /** Depth of the cut the screen sits in. */
        const val RIM_DP = 9f
    }
}
