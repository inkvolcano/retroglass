package com.nvanloo.retroglass.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.nvanloo.retroglass.model.Console
import com.nvanloo.retroglass.model.ControllerDefs
import com.nvanloo.retroglass.model.LayoutPreset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a console-styled touch controller and reports input.
 *
 * Two modes:
 *  - play mode: multi-touch input, haptics, pressed states
 *  - edit mode: drag controls to move them, pinch to resize the selected control
 *
 * In overlay mode (game on the same screen) the body is transparent and
 * controls are drawn semi-transparent.
 */
class ControllerView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onButton(keyCode: Int, pressed: Boolean)
        fun onDpad(x: Float, y: Float)
        fun onStick(id: String, x: Float, y: Float)
        fun onMenu()
    }

    var listener: Listener? = null

    /** Fired in edit mode whenever the user moves/resizes something. */
    var onLayoutEdited: (() -> Unit)? = null

    private class ControlState(val def: ControlDef, val placement: ControlPlacement) {
        var pressed = false
        // D-pad current direction / stick current deflection
        var valueX = 0f
        var valueY = 0f
    }

    private var console: Console = Console.NES
    private var presetId: String = "default"
    private var controls: MutableList<ControlState> = mutableListOf()
    private val layoutStore = LayoutStore(context)

    var overlayMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var editMode: Boolean = false
        set(value) {
            field = value
            if (!value) selected = null
            releaseEverything()
            invalidate()
        }

    private var selected: ControlState? = null

    // pointerId -> control being driven (play mode)
    private val pointerTargets = mutableMapOf<Int, ControlState>()
    // pointerId used for dragging in edit mode
    private var dragPointer = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#66FFFFFF")
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFD54F")
        strokeWidth = 5f
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = selected ?: return false
                target.placement.scale =
                    (target.placement.scale * detector.scaleFactor).coerceIn(0.45f, 2.6f)
                onLayoutEdited?.invoke()
                invalidate()
                return true
            }
        })

    fun setConsole(console: Console) {
        this.console = console
        presetId = layoutStore.selectedPreset(console) ?: ControllerDefs.defaultPresetId(console)
        reloadControls()
    }

    /** All layouts the user can pick from for the current console. */
    fun availablePresets(): List<LayoutPreset> = ControllerDefs.presetsFor(console)

    fun currentPresetId(): String = presetId

    /** Switch to a different layout preset (keeps that preset's own saved tweaks). */
    fun setPreset(id: String) {
        presetId = id
        layoutStore.setSelectedPreset(console, id)
        reloadControls()
    }

    private fun reloadControls() {
        val overrides = layoutStore.load(console, presetId)
        val defs = ControllerDefs.presetOrDefault(console, presetId).controls + menuControl()
        controls = defs.map { def ->
            val p = overrides[def.id] ?: ControlPlacement(def.x, def.y, 1f)
            ControlState(def, p)
        }.toMutableList()
        pointerTargets.clear()
        invalidate()
    }

    private fun menuControl() = ControlDef(
        id = "_menu", type = ControlType.BUTTON, label = "≡",
        keyCode = -1,
        x = 0.5f, y = 0.09f, size = 0.105f,
        shape = ControlShape.CIRCLE,
        fillColor = Color.parseColor("#33FFFFFF"),
        labelColor = Color.parseColor("#DDFFFFFF"),
    )

    fun saveLayout() =
        layoutStore.save(console, presetId, controls.associate { it.def.id to it.placement })

    fun resetLayout() {
        layoutStore.reset(console, presetId)
        reloadControls()
    }

    fun cancelEdits() = reloadControls()

    // ---------------------------------------------------------------- geometry

    private fun minDim() = min(width, height).toFloat()

    private fun controlRadius(c: ControlState): Float =
        c.def.size * c.placement.scale * minDim() / 2f

    private fun centerX(c: ControlState) = c.placement.cx * width
    private fun centerY(c: ControlState) = c.placement.cy * height

    // BAR controls use their size as a fraction of the view WIDTH (bar length);
    // thickness is a small fixed fraction of the shorter edge.
    private fun barHalfLen(c: ControlState) = c.def.size * c.placement.scale * width / 2f
    private fun barHalfThick(c: ControlState) = minDim() * 0.062f * c.placement.scale

    /** Effective half-extents for hit-testing (pills are wider than tall). */
    private fun hitTest(c: ControlState, x: Float, y: Float, slop: Float = 1.15f): Boolean {
        val dx = x - centerX(c)
        val dy = y - centerY(c)
        if (c.def.shape == ControlShape.BAR) {
            return abs(dx) <= barHalfLen(c) * slop && abs(dy) <= barHalfThick(c) * slop
        }
        val r = controlRadius(c) * slop
        return when (c.def.shape) {
            ControlShape.PILL -> abs(dx) <= r * 2.0f && abs(dy) <= r
            ControlShape.CROSS, ControlShape.PSX_CROSS -> abs(dx) <= r && abs(dy) <= r
            else -> hypot(dx, dy) <= r
        }
    }

    private fun findControl(x: Float, y: Float): ControlState? =
        controls.firstOrNull { hitTest(it, x, y) }

    // ---------------------------------------------------------------- input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) return handleEditTouch(event)
        return handlePlayTouch(event)
    }

    private fun handlePlayTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val target = findControl(event.getX(index), event.getY(index)) ?: return true
                pointerTargets[id] = target
                engage(target, event.getX(index), event.getY(index), pressDown = true)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val target = pointerTargets[id] ?: continue
                    if (target.def.type != ControlType.BUTTON) {
                        engage(target, event.getX(i), event.getY(i), pressDown = false)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val target = pointerTargets.remove(id)
                // Only release when no other finger is still holding the same control.
                if (target != null && target !in pointerTargets.values) release(target)
                if (event.actionMasked == MotionEvent.ACTION_UP) releaseEverything()
            }
            MotionEvent.ACTION_CANCEL -> releaseEverything()
        }
        return true
    }

    private fun engage(c: ControlState, x: Float, y: Float, pressDown: Boolean) {
        when (c.def.type) {
            ControlType.BUTTON -> {
                if (pressDown && !c.pressed) {
                    c.pressed = true
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (c.def.id == "_menu") listener?.onMenu()
                    else listener?.onButton(c.def.keyCode, true)
                }
            }
            ControlType.DPAD -> {
                val r = controlRadius(c)
                val dx = (x - centerX(c)) / r
                val dy = (y - centerY(c)) / r
                var nx = 0f
                var ny = 0f
                if (hypot(dx, dy) >= 0.28f) {
                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    // 8 sectors of 45°, centered on E, SE, S, SW, W, NW, N, NE
                    nx = when {
                        angle in -67.5f..67.5f -> 1f
                        angle > 112.5f || angle < -112.5f -> -1f
                        else -> 0f
                    }
                    ny = when {
                        angle in 22.5f..157.5f -> 1f
                        angle in -157.5f..-22.5f -> -1f
                        else -> 0f
                    }
                }
                if (nx != c.valueX || ny != c.valueY) {
                    if (!c.pressed && (nx != 0f || ny != 0f)) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    c.valueX = nx
                    c.valueY = ny
                    c.pressed = nx != 0f || ny != 0f
                    listener?.onDpad(nx, ny)
                    invalidate()
                }
                return
            }
            ControlType.STICK -> {
                val r = controlRadius(c) * 0.72f
                var dx = (x - centerX(c)) / r
                var dy = (y - centerY(c)) / r
                val len = hypot(dx, dy)
                if (len > 1f) {
                    dx /= len
                    dy /= len
                }
                c.valueX = dx
                c.valueY = dy
                c.pressed = true
                listener?.onStick(c.def.id, dx, dy)
                invalidate()
                return
            }
        }
        invalidate()
    }

    private fun release(c: ControlState) {
        when (c.def.type) {
            ControlType.BUTTON -> {
                if (c.pressed) {
                    c.pressed = false
                    if (c.def.id != "_menu") listener?.onButton(c.def.keyCode, false)
                }
            }
            ControlType.DPAD -> {
                c.pressed = false
                c.valueX = 0f
                c.valueY = 0f
                listener?.onDpad(0f, 0f)
            }
            ControlType.STICK -> {
                c.pressed = false
                c.valueX = 0f
                c.valueY = 0f
                listener?.onStick(c.def.id, 0f, 0f)
            }
        }
        invalidate()
    }

    private fun releaseEverything() {
        controls.forEach { if (it.pressed) release(it) }
        pointerTargets.clear()
    }

    // ---------------------------------------------------------------- edit mode

    private fun handleEditTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val target = findControl(event.x, event.y)
                if (target != null) {
                    selected = target
                    dragPointer = event.getPointerId(0)
                    dragOffsetX = event.x - centerX(target)
                    dragOffsetY = event.y - centerY(target)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragPointer != -1) {
                    val index = event.findPointerIndex(dragPointer)
                    val target = selected
                    if (index != -1 && target != null) {
                        target.placement.cx =
                            ((event.getX(index) - dragOffsetX) / width).coerceIn(0.03f, 0.97f)
                        target.placement.cy =
                            ((event.getY(index) - dragOffsetY) / height).coerceIn(0.04f, 0.96f)
                        onLayoutEdited?.invoke()
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragPointer = -1
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == dragPointer) dragPointer = -1
            }
        }
        return true
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        if (!overlayMode) {
            canvas.drawColor(console.bodyColor)
            // subtle top sheen
            fillPaint.color = Color.argb(18, 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height * 0.18f, fillPaint)
        }

        val alpha = if (overlayMode) 165 else 255
        for (c in controls) {
            drawControl(canvas, c, alpha)
        }

        if (editMode) {
            for (c in controls) drawEditOutline(canvas, c)
            selected?.let { sel ->
                textPaint.color = Color.parseColor("#FFD54F")
                textPaint.textSize = minDim() * 0.045f
                val name = if (sel.def.id == "_menu") "MENU" else sel.def.label.ifBlank { sel.def.id.uppercase() }
                canvas.drawText(
                    "$name  ·  ${(sel.placement.scale * 100).toInt()}%  (drag to move, pinch to resize)",
                    width / 2f, minDim() * 0.06f, textPaint,
                )
            }
        }
    }

    private fun drawEditOutline(canvas: Canvas, c: ControlState) {
        val cx = centerX(c)
        val cy = centerY(c)
        val paint = if (c === selected) selectPaint else editPaint
        val halfW: Float
        val halfH: Float
        if (c.def.shape == ControlShape.BAR) {
            halfW = barHalfLen(c)
            halfH = barHalfThick(c)
        } else {
            val r = controlRadius(c)
            halfW = if (c.def.shape == ControlShape.PILL) r * 2f else r
            halfH = r
        }
        canvas.drawRoundRect(
            RectF(cx - halfW - 8, cy - halfH - 8, cx + halfW + 8, cy + halfH + 8),
            16f, 16f, paint,
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            (Color.alpha(color) * alpha) / 255,
            Color.red(color), Color.green(color), Color.blue(color),
        )

    private fun drawControl(canvas: Canvas, c: ControlState, alpha: Int) {
        val cx = centerX(c)
        val cy = centerY(c)
        val r = controlRadius(c)
        val def = c.def

        when (def.shape) {
            ControlShape.CIRCLE -> {
                if (def.plateColor != Color.TRANSPARENT) {
                    fillPaint.color = withAlpha(def.plateColor, alpha)
                    val pr = r * 1.28f
                    canvas.drawRoundRect(
                        RectF(cx - pr, cy - pr, cx + pr, cy + pr), pr * 0.25f, pr * 0.25f, fillPaint,
                    )
                }
                fillPaint.color = withAlpha(if (c.pressed) lighten(def.fillColor) else def.fillColor, alpha)
                canvas.drawCircle(cx, cy, r, fillPaint)
                if (def.strokeColor != Color.TRANSPARENT) {
                    strokePaint.color = withAlpha(def.strokeColor, alpha)
                    strokePaint.strokeWidth = r * 0.12f
                    canvas.drawCircle(cx, cy, r * 0.94f, strokePaint)
                }
                textPaint.color = withAlpha(def.labelColor, alpha)
                textPaint.textSize = r * if (def.label.length > 1) 0.62f else 0.95f
                canvas.drawText(def.label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }

            ControlShape.PILL -> {
                val w = r * 2.1f
                fillPaint.color = withAlpha(if (c.pressed) lighten(def.fillColor) else def.fillColor, alpha)
                canvas.drawRoundRect(RectF(cx - w, cy - r * 0.8f, cx + w, cy + r * 0.8f), r, r, fillPaint)
                textPaint.color = withAlpha(def.labelColor, alpha)
                textPaint.textSize = r * (if (def.label.length > 2) 0.62f else 0.9f)
                canvas.drawText(def.label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }

            ControlShape.BAR -> {
                val hl = barHalfLen(c)
                val ht = barHalfThick(c)
                fillPaint.color = withAlpha(if (c.pressed) lighten(def.fillColor) else def.fillColor, alpha)
                canvas.drawRoundRect(RectF(cx - hl, cy - ht, cx + hl, cy + ht), ht, ht, fillPaint)
                textPaint.color = withAlpha(def.labelColor, alpha)
                textPaint.textSize = ht * 1.15f
                canvas.drawText(def.label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }

            ControlShape.CROSS, ControlShape.PSX_CROSS -> drawDpad(canvas, c, cx, cy, r, alpha)

            ControlShape.STICK -> {
                fillPaint.color = withAlpha(def.fillColor, alpha)
                canvas.drawCircle(cx, cy, r, fillPaint)
                strokePaint.color = withAlpha(Color.parseColor("#22FFFFFF"), alpha)
                strokePaint.strokeWidth = r * 0.05f
                canvas.drawCircle(cx, cy, r * 0.98f, strokePaint)
                val knobR = r * 0.52f
                val kx = cx + c.valueX * r * 0.48f
                val ky = cy + c.valueY * r * 0.48f
                fillPaint.color = withAlpha(if (c.pressed) lighten(darken(def.fillColor)) else darken(def.fillColor), alpha)
                canvas.drawCircle(kx, ky, knobR, fillPaint)
                fillPaint.color = withAlpha(Color.argb(30, 255, 255, 255), alpha)
                canvas.drawCircle(kx - knobR * 0.2f, ky - knobR * 0.25f, knobR * 0.55f, fillPaint)
                textPaint.color = withAlpha(def.labelColor, (alpha * 0.5f).toInt())
                textPaint.textSize = r * 0.3f
                canvas.drawText(def.label, cx, cy + r * 1.28f, textPaint)
            }
        }
    }

    private fun drawDpad(canvas: Canvas, c: ControlState, cx: Float, cy: Float, r: Float, alpha: Int) {
        val def = c.def
        val armW = r * 0.62f
        val half = armW / 2f
        val gap = if (def.shape == ControlShape.PSX_CROSS) r * 0.06f else 0f

        fun armColor(active: Boolean): Int =
            withAlpha(if (active) lighten(def.fillColor) else def.fillColor, alpha)

        val corner = armW * 0.28f
        // horizontal arms
        fillPaint.color = armColor(c.valueX < 0)
        canvas.drawRoundRect(RectF(cx - r, cy - half, cx - half - gap, cy + half), corner, corner, fillPaint)
        fillPaint.color = armColor(c.valueX > 0)
        canvas.drawRoundRect(RectF(cx + half + gap, cy - half, cx + r, cy + half), corner, corner, fillPaint)
        // vertical arms
        fillPaint.color = armColor(c.valueY < 0)
        canvas.drawRoundRect(RectF(cx - half, cy - r, cx + half, cy - half - gap), corner, corner, fillPaint)
        fillPaint.color = armColor(c.valueY > 0)
        canvas.drawRoundRect(RectF(cx - half, cy + half + gap, cx + half, cy + r), corner, corner, fillPaint)
        // center
        fillPaint.color = withAlpha(def.fillColor, alpha)
        canvas.drawRoundRect(RectF(cx - half, cy - half, cx + half, cy + half), corner * 0.6f, corner * 0.6f, fillPaint)
        if (def.shape == ControlShape.PSX_CROSS) {
            fillPaint.color = withAlpha(darken(def.fillColor), alpha)
            canvas.drawCircle(cx, cy, half * 0.55f, fillPaint)
        }

        // direction arrows
        fillPaint.color = withAlpha(def.labelColor, (alpha * 0.85f).toInt())
        val ar = armW * 0.30f
        fun arrow(px: Float, py: Float, dirX: Float, dirY: Float) {
            val path = android.graphics.Path()
            val perpX = -dirY
            val perpY = dirX
            path.moveTo(px + dirX * ar, py + dirY * ar)
            path.lineTo(px - dirX * ar * 0.6f + perpX * ar, py - dirY * ar * 0.6f + perpY * ar)
            path.lineTo(px - dirX * ar * 0.6f - perpX * ar, py - dirY * ar * 0.6f - perpY * ar)
            path.close()
            canvas.drawPath(path, fillPaint)
        }
        arrow(cx - r + armW * 0.55f, cy, -1f, 0f)
        arrow(cx + r - armW * 0.55f, cy, 1f, 0f)
        arrow(cx, cy - r + armW * 0.55f, 0f, -1f)
        arrow(cx, cy + r - armW * 0.55f, 0f, 1f)
    }

    private fun lighten(color: Int): Int = Color.argb(
        Color.alpha(color),
        min(255, Color.red(color) + 55),
        min(255, Color.green(color) + 55),
        min(255, Color.blue(color) + 55),
    )

    private fun darken(color: Int): Int = Color.argb(
        Color.alpha(color),
        max(0, Color.red(color) - 30),
        max(0, Color.green(color) - 30),
        max(0, Color.blue(color) - 30),
    )
}
