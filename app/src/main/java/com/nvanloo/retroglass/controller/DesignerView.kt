package com.nvanloo.retroglass.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.nvanloo.retroglass.model.Console

/**
 * The designer canvas from the wireframe (turn 4, "Designer canvas").
 *
 * The whole phone is drawn, not just the pad: the screen slit pinned to the top, the two big
 * columns with their six numbered slots each, and the CT/CL overlay boxes floating on the seam.
 * That framing is the point — a module's reach only means something relative to where the picture
 * is and where the other modules sit, so the editor shows the whole thing and you tap the part you
 * want to change.
 *
 * The width/shadow/scale controls live *inside* the screen box, as the wireframe draws them. They
 * belong to the pad as a whole rather than to whichever module happens to be selected, and putting
 * them there keeps the editor on one screen instead of scrolling between the preview and the
 * controls that change it.
 */
class DesignerView(context: Context) : View(context) {

    /** Called with the tapped field: zone code, and slot number for the LC/RC columns. */
    var onFieldTap: ((String, Int?) -> Unit)? = null

    /** Called with the new layout when one of the in-screen setting pills is tapped. */
    var onLayoutChange: ((PadLayout) -> Unit)? = null

    /** Called by the layout row's rotate pill: switch which orientation is being edited. */
    var onRotate: (() -> Unit)? = null

    var selected: Pair<String, Int?>? = null
        set(value) { field = value; invalidate() }

    private var layout: PadLayout = PadLayout()
    private var parts: PadParts = PadParts()
    private var console: Console? = null
    private var screenFraction: Float = 0.42f
    private var landscape: Boolean = false
    private var preview: Bitmap? = null

    /** Tappable rects built during draw: the setting pills, the overlay zones, then the slots. */
    private val hits = mutableListOf<Pair<RectF, () -> Unit>>()

    fun bind(
        console: Console,
        layout: PadLayout,
        parts: PadParts,
        screenFraction: Float,
        landscape: Boolean,
    ) {
        this.console = console
        this.layout = layout
        this.parts = parts
        this.screenFraction = screenFraction.coerceIn(0.22f, 0.62f)
        this.landscape = landscape
        preview = null
        invalidate()
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8A8A9A")
        pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val bg = Color.parseColor("#26262B")
    private val screenBg = Color.parseColor("#17171B")
    private val zoneLabel = Color.parseColor("#9A9AA8")
    private val accent = Color.parseColor("#C6FF4A")
    private val pillOff = Color.parseColor("#22FFFFFF")
    private val pillText = Color.parseColor("#CFCFDA")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        preview = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        hits.clear()

        // The wireframe's canvas is 470 tall, so every measurement it gives carries over directly.
        val unit = h / 470f
        val inset = 6f * unit

        fill.color = bg
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 10f * unit, 10f * unit, fill)

        if (landscape) {
            // Landscape puts the controls *over* the picture rather than under it, so the pad
            // band is the whole canvas and the screen is the box the columns leave between them.
            drawScreen(canvas, landscapeScreenRect(w, h, inset), unit)
            drawPad(canvas, w, inset, h - inset, unit)
        } else {
            val screenBottom = inset + (h - inset * 2f) * screenFraction
            drawScreen(canvas, RectF(inset, inset, w - inset, screenBottom), unit)
            drawPad(canvas, w, screenBottom + 4f * unit, h - inset, unit)
        }
    }

    /** What the picture is left after the columns and any occupied overlay zones take their band. */
    private fun landscapeScreenRect(w: Float, h: Float, inset: Float): RectF {
        val (_, lcR) = PadRenderer.columnBounds(layout, PadLayout.ZONE_LC, landscape = true)
        val (rcL, _) = PadRenderer.columnBounds(layout, PadLayout.ZONE_RC, landscape = true)
        val padH = h - inset * 2f
        // An empty overlay zone gives its band back to the picture, which is the wireframe's rule
        // and the reason the filler module exists at all.
        val top = if (layout.ct != null) inset + 0.19f * padH else inset
        val bottom = if (layout.cl != null) h - inset - 0.19f * padH else h - inset
        return RectF(lcR * w + 6f, top, rcL * w - 6f, bottom)
    }

    /** The picture, with the settings panel inside it exactly as the wireframe draws it. */
    private fun drawScreen(canvas: Canvas, r: RectF, unit: Float) {
        fill.color = screenBg
        canvas.drawRoundRect(r, 6f * unit, 6f * unit, fill)
        solid.color = Color.parseColor("#4A4A55")
        solid.strokeWidth = 1f * unit
        canvas.drawRoundRect(r, 6f * unit, 6f * unit, solid)

        text.color = zoneLabel
        text.textAlign = Paint.Align.CENTER
        text.textSize = 11f * unit
        canvas.drawText("◉ SCREEN 4:3", r.centerX(), r.top + 14f * unit, text)

        val rows = listOf(
            "layout" to listOf(
                Triple(
                    "\u27F3 " + (if (landscape) "Landscape" else "Portrait"),
                    false,
                ) { onRotate?.invoke(); layout },
            ),
            "width" to PadLayout.SPLITS.map { id ->
                Triple(PadLayout.splitLabel(id), layout.split == id) { layout.copy(split = id) }
            },
            "shadow" to listOf(
                Triple("buttons", layout.shadowButtons) { layout.copy(shadowButtons = !layout.shadowButtons) },
                Triple("d-pad", layout.shadowDpad) { layout.copy(shadowDpad = !layout.shadowDpad) },
                Triple("stick", layout.shadowStick) { layout.copy(shadowStick = !layout.shadowStick) },
            ),
            "scale" to listOf('s' to "small", 'n' to "normal", 'l' to "large").map { (key, label) ->
                Triple(label, layout.scale == key) { layout.copy(scale = key) }
            },
        )

        val gap = 4f * unit
        val pillH = 15f * unit
        val labelH = 11f * unit
        text.textSize = 9f * unit

        // Measure first: the width row carries four options and wraps on a narrow preview, so the
        // panel has to be as tall as the rows actually turn out to be, not as tall as one line each.
        val innerW = r.width() - 12f * unit - gap * 4f
        var lines = 0
        for ((_, pills) in rows) {
            var x = 0f
            lines++
            for ((caption, _, _) in pills) {
                val tw = text.measureText(caption) + 10f * unit
                if (x > 0f && x + tw > innerW) { lines++; x = 0f }
                x += tw + gap
            }
        }
        val panelH = rows.size * labelH + lines * (pillH + gap) + gap
        val panel = RectF(
            r.left + 6f * unit, r.bottom - panelH - 6f * unit,
            r.right - 6f * unit, r.bottom - 6f * unit,
        )
        fill.color = Color.parseColor("#8C000000")
        canvas.drawRoundRect(panel, 6f * unit, 6f * unit, fill)
        solid.color = Color.parseColor("#4A4A55")
        canvas.drawRoundRect(panel, 6f * unit, 6f * unit, solid)

        var y = panel.top + gap
        for ((label, pills) in rows) {
            text.color = zoneLabel
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(label, panel.left + gap * 1.5f, y + labelH * 0.8f, text)
            y += labelH
            text.textAlign = Paint.Align.CENTER
            var x = panel.left + gap * 1.5f
            for ((caption, on, apply) in pills) {
                val tw = text.measureText(caption) + 10f * unit
                if (x > panel.left + gap * 1.5f && x + tw > panel.right - gap) {
                    x = panel.left + gap * 1.5f
                    y += pillH + gap
                }
                val pill = RectF(x, y, x + tw, y + pillH)
                fill.color = if (on) accent else pillOff
                canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, fill)
                text.color = if (on) Color.parseColor("#1A1A1A") else pillText
                canvas.drawText(caption, pill.centerX(), pill.centerY() + text.textSize * 0.36f, text)
                hits += RectF(pill) to {
                    // The rotate pill returns the layout unchanged and switches orientation
                    // through its own callback; everything else is a real edit.
                    val next = apply()
                    if (next !== layout) onLayoutChange?.invoke(next)
                }
                x += tw + gap
            }
            y += pillH + gap
        }
    }

    /** Columns, slots, overlay zones and the live pad render, in the band below the screen. */
    private fun drawPad(canvas: Canvas, w: Float, top: Float, bottom: Float, unit: Float) {
        val padH = bottom - top
        if (padH <= 0) return
        val padHeight = padH
        val defs = PadRenderer.render(layout, parts, landscape, aspect = width.toFloat() / padHeight)

        console?.let { target ->
            val bmp = preview
                ?: LayoutPreview.render(target, defs, width, padH.toInt()).also { preview = it }
            canvas.drawBitmap(bmp, 0f, top, null)
        }

        dashed.strokeWidth = 1.4f * unit
        text.textSize = 9f * unit
        text.textAlign = Paint.Align.LEFT

        val slotHits = mutableListOf<Pair<RectF, () -> Unit>>()
        for (zone in listOf(PadLayout.ZONE_LC, PadLayout.ZONE_RC)) {
            val (l, _) = PadRenderer.columnBounds(layout, zone, landscape)
            for (slot in 1..PadLayout.SLOT_COUNT) {
                val rect = slotRect(zone, slot, 1, w, top, padH)
                canvas.drawRoundRect(rect, 5f * unit, 5f * unit, dashed)
                text.color = zoneLabel
                canvas.drawText("$slot", rect.left + 4f * unit, rect.top + 10f * unit, text)
                slotHits += RectF(rect) to {
                    val owner = layout.covering(zone, slot, 1).firstOrNull()?.first ?: slot
                    onFieldTap?.invoke(zone, owner)
                }
            }
            text.color = zoneLabel
            canvas.drawText(zone, l * w + 4f * unit, top + 9f * unit, text)
        }

        // The centre boxes overlap both columns by design, so they are drawn last and hit-tested
        // first — otherwise they could never be selected at all.
        for (zone in listOf(PadLayout.ZONE_CT, PadLayout.ZONE_CL)) {
            val rect = centreRect(zone, w, top, padH)
            // The wireframe tints these boxes dark because it draws them on a dark canvas. Over a
            // real pad, which is light, the same tint reads as a grey smear across the bottom of
            // the layout. A pale wash keeps the "this zone floats over both columns" cue without
            // hiding what is underneath — which is the whole reason the wireframe made it
            // translucent in the first place.
            fill.color = Color.parseColor("#26FFFFFF")
            canvas.drawRoundRect(rect, 5f * unit, 5f * unit, fill)
            canvas.drawRoundRect(rect, 5f * unit, 5f * unit, dashed)
            text.color = zoneLabel
            canvas.drawText(zone, rect.left + 5f * unit, rect.top + 10f * unit, text)
            hits += RectF(rect) to { onFieldTap?.invoke(zone, null) }
        }
        hits += slotHits

        // The gear is translucent by design and nearly invisible on a light pad; ring it so the
        // way back to the settings is visibly still there.
        defs.firstOrNull { it.id == PadModules.MENU_ID }?.let { gear ->
            solid.color = Color.parseColor("#99000000")
            solid.strokeWidth = 2f * unit
            canvas.drawCircle(
                gear.x * w, top + gear.y * padH, gear.size * minOf(w, padH) / 2f, solid,
            )
        }

        selected?.let { (zone, slot) ->
            val rect = if (slot == null) centreRect(zone, w, top, padH) else {
                slotRect(zone, slot, PadModules.byId(layout.column(zone)[slot])?.slots ?: 1, w, top, padH)
            }
            solid.color = Color.parseColor("#FFD54F")
            solid.strokeWidth = 2.5f * unit
            canvas.drawRoundRect(rect, 5f * unit, 5f * unit, solid)
        }
    }

    private fun slotRect(zone: String, slot: Int, span: Int, w: Float, top: Float, padH: Float): RectF {
        val (l, r) = PadRenderer.columnBounds(layout, zone, landscape)
        val y0 = top + PadRenderer.slotTop(slot) * padH
        val y1 = top + (PadRenderer.slotTop(slot + span - 1) + PadRenderer.slotHeight()) * padH
        return RectF(l * w + 2f, y0, r * w - 2f, y1)
    }

    private fun centreRect(zone: String, w: Float, top: Float, padH: Float): RectF {
        val (l, r) = PadRenderer.centreBounds(zone, landscape)
        val cy = top + PadRenderer.centreY(zone, landscape) * padH
        val half = PadRenderer.slotHeight() * padH / 2f
        return RectF(l * w, cy - half, r * w, cy + half)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        hits.firstOrNull { it.first.contains(event.x, event.y) }?.second?.invoke()
        return true
    }
}
