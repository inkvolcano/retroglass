package com.nvanloo.retroglass.controller

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nvanloo.retroglass.model.Console
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a static thumbnail of a controller layout ([ControlDef]s) to a Bitmap.
 *
 * Geometry mirrors [ControllerView] exactly (radius = size/2 × min(w,h), the same
 * per-shape half-extents and on-screen clamping) so the preview matches what the
 * user actually sees. Controls are drawn in their resting (unpressed) state.
 *
 * The bitmap is authored at the portrait-split aspect (H/W ≈ 1.42) that the base
 * layouts target, so a caller should request height ≈ 1.42 × width.
 */
object LayoutPreview {

    /** Design aspect of the touch-controller region in portrait split (height / width). */
    const val ASPECT = 1.42f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun render(console: Console, controls: List<ControlDef>, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()

        canvas.drawColor(console.bodyColor)
        // subtle top sheen, same as ControllerView
        fill.color = Color.argb(18, 255, 255, 255)
        canvas.drawRect(0f, 0f, w, h * 0.18f, fill)

        for (def in controls) {
            if (def.id == "_menu") continue // menu affordance isn't part of the layout identity
            drawControl(canvas, def, w, h)
        }
        return bmp
    }

    private fun minDim(w: Float, h: Float) = min(w, h)

    private fun radius(def: ControlDef, w: Float, h: Float) = def.size * minDim(w, h) / 2f

    private fun halfExtentX(def: ControlDef, w: Float, h: Float): Float = when (def.shape) {
        ControlShape.BAR -> def.size * w / 2f
        ControlShape.PILL -> radius(def, w, h) * 1.85f
        ControlShape.CIRCLE -> radius(def, w, h) * if (def.plateColor != Color.TRANSPARENT) 1.28f else 1f
        else -> radius(def, w, h)
    }

    private fun halfExtentY(def: ControlDef, w: Float, h: Float): Float = when (def.shape) {
        ControlShape.BAR -> minDim(w, h) * 0.062f
        ControlShape.PILL -> radius(def, w, h) * 0.8f
        ControlShape.CIRCLE -> radius(def, w, h) * if (def.plateColor != Color.TRANSPARENT) 1.28f else 1f
        else -> radius(def, w, h)
    }

    private fun clamp(raw: Float, half: Float, extent: Float): Float {
        if (extent <= 0) return raw
        return if (half * 2f >= extent) extent / 2f else raw.coerceIn(half, extent - half)
    }

    private fun drawControl(canvas: Canvas, def: ControlDef, w: Float, h: Float) {
        val cx = clamp(def.x * w, halfExtentX(def, w, h), w)
        val cy = clamp(def.y * h, halfExtentY(def, w, h), h)
        val r = radius(def, w, h)

        when (def.shape) {
            ControlShape.CIRCLE -> {
                if (def.plateColor != Color.TRANSPARENT) {
                    fill.color = def.plateColor
                    val pr = r * 1.28f
                    canvas.drawRoundRect(RectF(cx - pr, cy - pr, cx + pr, cy + pr), pr * 0.25f, pr * 0.25f, fill)
                }
                fill.color = def.fillColor
                canvas.drawCircle(cx, cy, r, fill)
                if (def.strokeColor != Color.TRANSPARENT) {
                    stroke.color = def.strokeColor
                    stroke.strokeWidth = r * 0.12f
                    canvas.drawCircle(cx, cy, r * 0.94f, stroke)
                }
                text.color = def.labelColor
                // Scale multi-character labels (e.g. "FIRE") down so they fit the button.
                text.textSize = minOf(minDim(w, h) * 0.082f, r * 1.25f, r * 3.0f / maxOf(1, def.label.length))
                canvas.drawText(def.label, cx, cy - (text.ascent() + text.descent()) / 2f, text)
            }

            ControlShape.PILL -> {
                val pw = r * 1.85f
                fill.color = def.fillColor
                canvas.drawRoundRect(RectF(cx - pw, cy - r * 0.8f, cx + pw, cy + r * 0.8f), r, r, fill)
                text.color = def.labelColor
                text.textSize = r * (if (def.label.length > 2) 0.62f else 0.9f)
                canvas.drawText(def.label, cx, cy - (text.ascent() + text.descent()) / 2f, text)
            }

            ControlShape.BAR -> {
                val hl = def.size * w / 2f
                val ht = minDim(w, h) * 0.062f
                fill.color = def.fillColor
                canvas.drawRoundRect(RectF(cx - hl, cy - ht, cx + hl, cy + ht), ht, ht, fill)
                text.color = def.labelColor
                text.textSize = ht * 1.15f
                canvas.drawText(def.label, cx, cy - (text.ascent() + text.descent()) / 2f, text)
            }

            ControlShape.CROSS, ControlShape.PSX_CROSS -> drawDpad(canvas, def, cx, cy, r)

            ControlShape.STICK -> {
                fill.color = def.fillColor
                canvas.drawCircle(cx, cy, r, fill)
                stroke.color = Color.parseColor("#22FFFFFF")
                stroke.strokeWidth = r * 0.05f
                canvas.drawCircle(cx, cy, r * 0.98f, stroke)
                val knobR = r * 0.52f
                fill.color = darken(def.fillColor)
                canvas.drawCircle(cx, cy, knobR, fill)
                fill.color = Color.argb(30, 255, 255, 255)
                canvas.drawCircle(cx - knobR * 0.2f, cy - knobR * 0.25f, knobR * 0.55f, fill)
            }
        }
    }

    private fun drawDpad(canvas: Canvas, def: ControlDef, cx: Float, cy: Float, r: Float) {
        val armW = r * 0.62f
        val half = armW / 2f
        val gap = if (def.shape == ControlShape.PSX_CROSS) r * 0.06f else 0f
        val corner = armW * 0.28f

        fill.color = def.fillColor
        canvas.drawRoundRect(RectF(cx - r, cy - half, cx - half - gap, cy + half), corner, corner, fill)
        canvas.drawRoundRect(RectF(cx + half + gap, cy - half, cx + r, cy + half), corner, corner, fill)
        canvas.drawRoundRect(RectF(cx - half, cy - r, cx + half, cy - half - gap), corner, corner, fill)
        canvas.drawRoundRect(RectF(cx - half, cy + half + gap, cx + half, cy + r), corner, corner, fill)
        canvas.drawRoundRect(RectF(cx - half, cy - half, cx + half, cy + half), corner * 0.6f, corner * 0.6f, fill)
        if (def.shape == ControlShape.PSX_CROSS) {
            fill.color = darken(def.fillColor)
            canvas.drawCircle(cx, cy, half * 0.55f, fill)
        }

        fill.color = def.labelColor
        val ar = armW * 0.30f
        fun arrow(px: Float, py: Float, dirX: Float, dirY: Float) {
            val perpX = -dirY
            val perpY = dirX
            val path = Path()
            path.moveTo(px + dirX * ar, py + dirY * ar)
            path.lineTo(px - dirX * ar * 0.6f + perpX * ar, py - dirY * ar * 0.6f + perpY * ar)
            path.lineTo(px - dirX * ar * 0.6f - perpX * ar, py - dirY * ar * 0.6f - perpY * ar)
            path.close()
            canvas.drawPath(path, fill)
        }
        arrow(cx - r + armW * 0.55f, cy, -1f, 0f)
        arrow(cx + r - armW * 0.55f, cy, 1f, 0f)
        arrow(cx, cy - r + armW * 0.55f, 0f, -1f)
        arrow(cx, cy + r - armW * 0.55f, 0f, 1f)
    }

    private fun darken(color: Int): Int = Color.argb(
        Color.alpha(color),
        max(0, Color.red(color) - 30),
        max(0, Color.green(color) - 30),
        max(0, Color.blue(color) - 30),
    )
}
