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
 * The designer's live preview: the pad as it will actually look, drawn over the grid of fields
 * you can tap.
 *
 * The controls layer is not a mock-up — it is [PadRenderer] output run through the same preview
 * renderer the layout thumbnails use, so what is on screen here is what the pad will be. The grid
 * underneath it (six slot bands per column, the two centre overlays) is the part you interact
 * with: every tap resolves to a field, and the field is what the module list is about.
 */
class DesignerView(context: Context) : View(context) {

    /** Called with the tapped field: zone code, and slot number for the LC/RC columns. */
    var onFieldTap: ((String, Int?) -> Unit)? = null

    var selected: Pair<String, Int?>? = null
        set(value) { field = value; invalidate() }

    private var layout: PadLayout = PadLayout()
    private var parts: PadParts = PadParts()
    private var console: Console? = null
    private var preview: Bitmap? = null

    fun bind(console: Console, layout: PadLayout, parts: PadParts) {
        this.console = console
        this.layout = layout
        this.parts = parts
        preview = null
        invalidate()
    }

    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#55FFFFFF")
        pathEffect = DashPathEffect(floatArrayOf(9f, 7f), 0f)
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FFD54F")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        preview = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        fillPaint.color = Color.parseColor("#26262B")
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 18f, 18f, fillPaint)
        labelPaint.textSize = h * 0.026f

        for (zone in listOf(PadLayout.ZONE_LC, PadLayout.ZONE_RC)) {
            val (l, r) = PadRenderer.columnBounds(layout, zone, landscape = false)
            for (slot in 1..PadLayout.SLOT_COUNT) {
                val top = PadRenderer.slotTop(slot) * h
                val rect = RectF(l * w + 3f, top + 3f, r * w - 3f, top + PadRenderer.slotHeight() * h - 3f)
                canvas.drawRoundRect(rect, 8f, 8f, zonePaint)
                canvas.drawText("$slot", rect.left + 14f, rect.top + labelPaint.textSize + 6f, labelPaint)
            }
        }
        for (zone in listOf(PadLayout.ZONE_CT, PadLayout.ZONE_CL)) {
            canvas.drawRoundRect(centreRect(zone, w, h), 8f, 8f, zonePaint)
            val rect = centreRect(zone, w, h)
            canvas.drawText(zone, rect.left + 16f, rect.top + labelPaint.textSize + 6f, labelPaint)
        }

        // The controls layer, rendered exactly as the pad will build it.
        val target = console
        if (target != null) {
            val bmp = preview ?: LayoutPreview.render(
                target,
                PadRenderer.render(layout, parts, landscape = false),
                width,
                height,
            ).also { preview = it }
            canvas.drawBitmap(bmp, 0f, 0f, null)
        }

        selected?.let { (zone, slot) ->
            canvas.drawRoundRect(fieldRect(zone, slot, w, h), 8f, 8f, selPaint)
        }
    }

    private fun centreRect(zone: String, w: Float, h: Float): RectF {
        val (l, r) = PadRenderer.centreBounds(zone, landscape = false)
        val cy = PadRenderer.centreY(zone, landscape = false) * h
        val half = PadRenderer.slotHeight() * h / 2f
        return RectF(l * w, cy - half, r * w, cy + half)
    }

    private fun fieldRect(zone: String, slot: Int?, w: Float, h: Float): RectF {
        if (slot == null) return centreRect(zone, w, h)
        val (l, r) = PadRenderer.columnBounds(layout, zone, landscape = false)
        val module = PadModules.byId(layout.column(zone)[slot])
        val span = module?.slots ?: 1
        val top = PadRenderer.slotTop(slot) * h
        val bottom = (PadRenderer.slotTop(slot + span - 1) + PadRenderer.slotHeight()) * h
        return RectF(l * w + 3f, top + 3f, r * w - 3f, bottom - 3f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val w = width.toFloat()
        val h = height.toFloat()
        val x = event.x / w
        val y = event.y / h

        // Overlay zones sit on top of the columns and win the tap, which is what makes the 40%
        // centre boxes usable at all — they overlap both columns by design.
        for (zone in listOf(PadLayout.ZONE_CT, PadLayout.ZONE_CL)) {
            if (centreRect(zone, w, h).contains(event.x, event.y)) {
                onFieldTap?.invoke(zone, null)
                return true
            }
        }
        val zone = if (x <= 0.5f) PadLayout.ZONE_LC else PadLayout.ZONE_RC
        val (l, r) = PadRenderer.columnBounds(layout, zone, landscape = false)
        if (x < l || x > r) return true
        val slot = (1..PadLayout.SLOT_COUNT).firstOrNull {
            y >= PadRenderer.slotTop(it) && y <= PadRenderer.slotTop(it) + PadRenderer.slotHeight()
        } ?: return true
        // Tapping anywhere on a two-slot module selects the module, not its lower half.
        val owner = layout.covering(zone, slot, 1).firstOrNull()?.first ?: slot
        onFieldTap?.invoke(zone, owner)
        return true
    }
}
