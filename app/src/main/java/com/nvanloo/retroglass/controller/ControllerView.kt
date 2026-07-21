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

    companion object {
        /** Bezel thickness as a fraction of the control's radius. */
        const val BEZEL_WIDTH = 0.15f
        /**
         * The D-pad wears a thinner rim. Its arms are only ~0.62r wide, so a rim scaled off the
         * full radius eats most of an arm and the cross reads as outlined rather than moulded.
         */
        const val BEZEL_WIDTH_CROSS = 0.075f
        /** How far the body is pushed off the glass, as a fraction of radius. */
        const val EXTRUDE_DEPTH = 0.10f
        /** Contact shadow on the screen underneath. */
        const val CONTACT_ALPHA = 80
        /** How far off "straight up" the lit edge sits in the resting pose. */
        const val BEZEL_BASE = 0.6f
        const val BEZEL_HIGHLIGHT = 120
        const val BEZEL_SHADE = 130
        const val LAYOUT_PORTRAIT = 0   // as authored (game above, pad below / handheld)
        const val LAYOUT_FRAME = 1      // landscape, game on the phone: pad frames a centred screen
        const val LAYOUT_FULLPAD = 2    // landscape, external display: full-screen pad
    }

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
        // Turbo/autofire: current toggled output state while held.
        var turboState = false
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

    /** How the layout is arranged: portrait (as authored) or a landscape rearrangement
     *  ([LayoutPreview]/gen_landscape_frame). Frame = game on the phone; full pad = external display. */
    var layoutMode: Int = LAYOUT_PORTRAIT
        set(value) {
            if (field == value) return
            field = value
            reloadControls()
        }

    var editMode: Boolean = false
        set(value) {
            field = value
            if (!value) selected = null
            releaseEverything()
            invalidate()
        }

    /** Read-only live-input display: ignores touch and drops the menu button. The pressed/stick
     *  state is driven from the physical gamepad via [monitorButton]/[monitorDpad]/[monitorStick]
     *  so the diagram lights up as the player presses their pad (companion-dashboard mode). */
    var monitorMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            reloadControls()
        }

    private var selected: ControlState? = null

    /** Control ids set to autofire (turbo) while held. Set by EmulationActivity. */
    var turboIds: Set<String> = emptySet()
    private val turboHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var turboRunning = false
    private val turboTick = object : Runnable {
        override fun run() {
            var any = false
            for (c in controls) {
                if (c.pressed && c.def.type == ControlType.BUTTON &&
                    c.def.id in turboIds && c.def.id != "_menu" && !isCButton(c.def.id) && !isKeypad(c.def.id)
                ) {
                    any = true
                    c.turboState = !c.turboState
                    listener?.onButton(c.def.keyCode, c.turboState)
                }
            }
            if (any) turboHandler.postDelayed(this, 50) else turboRunning = false
        }
    }

    private fun startTurbo() {
        if (!turboRunning) {
            turboRunning = true
            turboHandler.postDelayed(turboTick, 50)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        turboHandler.removeCallbacks(turboTick)
        turboRunning = false
    }

    // pointerId -> control being driven (play mode)
    // A pointer usually drives one control, but a co-centred D-pad + button (the N64
    // "Z in the D-pad" combo) lets a single finger hold a direction and the centre together.
    private val pointerTargets = mutableMapOf<Int, List<ControlState>>()
    // pointerId used for dragging in edit mode
    private var dragPointer = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val extrudePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- tilt-driven bezel ---------------------------------------------------------------
    // A virtual light fixed in the world. Each control carries a bevelled rim: bright on the
    // edge facing the light, dark on the far edge. Turning the phone rolls the lit edge around
    // the shape, which is what makes the controls read as moulded rather than printed on.
    // Driven by TiltSource (gravity, not the gyro — see that class).

    /** Light direction projected onto the screen, -1..1 per axis. 0,0 is the resting pose. */
    private var lightX = 0f
    private var lightY = 0f

    /** Off by default until the activity says the sensor is running. */
    var tiltBezel = false
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Called from the sensor; only repaints when the light actually moved. */
    fun setLight(x: Float, y: Float) {
        if (x == lightX && y == lightY) return
        lightX = x
        lightY = y
        if (tiltBezel) invalidate()
    }

    /**
     * Unit vector to the lit edge, in screen space. Resting pose points up, so a control looks
     * lit from above the way a physical button on a handheld does; rolling the phone right
     * swings the highlight left, leaning it back walks the highlight down across the face.
     */
    private fun lightDir(): Pair<Float, Float> {
        val hx = -lightX
        val hy = -BEZEL_BASE + lightY
        val len = hypot(hx.toDouble(), hy.toDouble()).toFloat()
        return if (len < 1e-4f) 0f to -1f else (hx / len) to (hy / len)
    }
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
        var base = ControllerDefs.presetOrDefault(console, presetId).controls
        val landscape = layoutMode != LAYOUT_PORTRAIT
        if (landscape && width > 0 && height > 0) {
            base = LandscapeLayout.transform(base, screen = layoutMode == LAYOUT_FRAME,
                w = width.toFloat(), h = height.toFloat())
        }
        val defs = if (monitorMode) base else base + menuControl()
        controls = defs.map { def ->
            // Landscape layouts are computed, not user-tweaked, so ignore the per-preset portrait overrides.
            val p = if (landscape) ControlPlacement(def.x, def.y, 1f)
            else overrides[def.id] ?: ControlPlacement(def.x, def.y, 1f)
            ControlState(def, p)
        }.toMutableList()
        pointerTargets.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The landscape transform depends on the view's real width/height (aspect-correction and the
        // size cap), so recompute it once the view is measured or resized.
        if (layoutMode != LAYOUT_PORTRAIT && (w != oldw || h != oldh)) reloadControls()
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

    /** Button-sizing reference: the shorter edge in portrait, but capped in landscape so buttons
     *  don't balloon on a tall/near-square screen (foldable, tablet). Matches LandscapeLayout. */
    private fun sizeBase(): Float =
        if (layoutMode != LAYOUT_PORTRAIT) min(minDim(), width * 0.46f) else minDim()

    private fun controlRadius(c: ControlState): Float =
        c.def.size * c.placement.scale * sizeBase() / 2f

    /** Half-width of a control's visible box, matching how [drawControl] draws each shape. */
    private fun halfExtentX(c: ControlState): Float = when (c.def.shape) {
        ControlShape.BAR -> barHalfLen(c)
        ControlShape.PILL -> controlRadius(c) * 1.85f
        ControlShape.CIRCLE -> controlRadius(c) * if (c.def.plateColor != Color.TRANSPARENT) 1.28f else 1f
        else -> controlRadius(c)
    }

    private fun halfExtentY(c: ControlState): Float = when (c.def.shape) {
        ControlShape.BAR -> barHalfThick(c)
        ControlShape.PILL -> controlRadius(c) * 0.8f
        ControlShape.CIRCLE -> controlRadius(c) * if (c.def.plateColor != Color.TRANSPARENT) 1.28f else 1f
        else -> controlRadius(c)
    }

    /** Keeps a control's whole box on-screen: centres it if it's wider than the view,
     *  otherwise pins it so neither edge is clipped. A safety net over the authored
     *  layouts (also guards the scaled presets and user edits). */
    private fun clampCenter(raw: Float, half: Float, extent: Int): Float {
        if (extent <= 0) return raw
        return if (half * 2f >= extent) extent / 2f else raw.coerceIn(half, extent - half)
    }

    private fun centerX(c: ControlState) = clampCenter(c.placement.cx * width, halfExtentX(c), width)
    private fun centerY(c: ControlState) = clampCenter(c.placement.cy * height, halfExtentY(c), height)

    // BAR controls use their size as a fraction of the view WIDTH (bar length);
    // thickness is a small fixed fraction of the shorter edge.
    private fun barHalfLen(c: ControlState) = c.def.size * c.placement.scale * width / 2f
    private fun barHalfThick(c: ControlState) = sizeBase() * 0.062f * c.placement.scale

    /** Effective half-extents for hit-testing (pills are wider than tall). */
    private fun hitTest(c: ControlState, x: Float, y: Float, slop: Float = 1.15f): Boolean {
        val dx = x - centerX(c)
        val dy = y - centerY(c)
        if (c.def.shape == ControlShape.BAR) {
            return abs(dx) <= barHalfLen(c) * slop && abs(dy) <= barHalfThick(c) * slop
        }
        val r = controlRadius(c) * slop
        return when (c.def.shape) {
            ControlShape.PILL -> abs(dx) <= r * 1.85f && abs(dy) <= r
            ControlShape.CROSS, ControlShape.PSX_CROSS -> abs(dx) <= r && abs(dy) <= r
            else -> hypot(dx, dy) <= r
        }
    }

    private fun findControl(x: Float, y: Float): ControlState? =
        controls.firstOrNull { hitTest(it, x, y) }

    /**
     * Controls under a touch. Normally the single topmost one, but if the touch lands on a
     * D-pad that has a button sitting at its centre (the N64 Z-in-D-pad layout), both are
     * returned so one finger can press a direction and the centre button at once.
     */
    private fun findControls(x: Float, y: Float): List<ControlState> {
        val hits = controls.filter { hitTest(it, x, y) }
        if (hits.size <= 1) return hits
        val dpad = hits.firstOrNull { it.def.type == ControlType.DPAD }
        val centre = dpad?.let { coCenteredButton(it) }?.takeIf { it in hits }
        return if (dpad != null && centre != null) listOf(centre, dpad) else listOf(hits.first())
    }

    /** A button sitting at a D-pad's centre (the N64 Z-in-D-pad combo), if any. */
    private fun coCenteredButton(dpad: ControlState): ControlState? =
        controls.firstOrNull {
            it.def.type == ControlType.BUTTON &&
                hypot(centerX(it) - centerX(dpad), centerY(it) - centerY(dpad)) < controlRadius(dpad)
        }

    // ---------------------------------------------------------------- input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (monitorMode) return false
        if (editMode) return handleEditTouch(event)
        return handlePlayTouch(event)
    }

    // ------------------------------------------------------- monitor (live display)

    /** Light every button bound to [retroKeyCode] (RetroPad = Android keycode, same space as the
     *  emulator receives). No-op unless it changes something, so it's cheap to call per key event. */
    fun monitorButton(retroKeyCode: Int, pressed: Boolean) {
        var changed = false
        for (c in controls) {
            if (c.def.type == ControlType.BUTTON && c.def.id != "_menu" && c.def.keyCode == retroKeyCode) {
                if (c.pressed != pressed) { c.pressed = pressed; changed = true }
            }
        }
        if (changed) invalidate()
    }

    /** Deflect the D-pad diagram to a direction (-1/0/1 on each axis). */
    fun monitorDpad(x: Float, y: Float) {
        val c = controls.firstOrNull { it.def.type == ControlType.DPAD } ?: return
        if (c.valueX == x && c.valueY == y) return
        c.valueX = x; c.valueY = y; c.pressed = x != 0f || y != 0f
        invalidate()
    }

    /** Deflect a named analog stick ("stick_l"/"stick_r") in the diagram. */
    fun monitorStick(id: String, x: Float, y: Float) {
        val c = controls.firstOrNull { it.def.type == ControlType.STICK && it.def.id == id } ?: return
        if (c.valueX == x && c.valueY == y) return
        c.valueX = x; c.valueY = y; c.pressed = x != 0f || y != 0f
        invalidate()
    }

    /** Reset every control to its resting state (e.g. when the driving pad disconnects). */
    fun monitorClear() {
        var changed = false
        for (c in controls) {
            if (c.pressed || c.valueX != 0f || c.valueY != 0f) {
                c.pressed = false; c.valueX = 0f; c.valueY = 0f; changed = true
            }
        }
        if (changed) invalidate()
    }

    private fun handlePlayTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val targets = findControls(event.getX(index), event.getY(index))
                if (targets.isEmpty()) return true
                pointerTargets[id] = targets
                targets.forEach { engage(it, event.getX(index), event.getY(index), pressDown = true) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val targets = pointerTargets[id] ?: continue
                    val px = event.getX(i)
                    val py = event.getY(i)
                    targets.forEach {
                        if (it.def.type != ControlType.BUTTON) engage(it, px, py, pressDown = false)
                    }
                    // If the finger slides onto the centre button of a D-pad it's holding (e.g.
                    // dragging from a direction into N64's Z), latch that button on too.
                    val dpad = targets.firstOrNull { it.def.type == ControlType.DPAD }
                    if (dpad != null) {
                        val centre = coCenteredButton(dpad)
                        if (centre != null && centre !in targets && hitTest(centre, px, py)) {
                            engage(centre, px, py, pressDown = true)
                            pointerTargets[id] = targets + centre
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val targets = pointerTargets.remove(id).orEmpty()
                // Only release a control if no other finger is still holding it.
                val stillHeld = pointerTargets.values.flatten().toSet()
                targets.forEach { if (it !in stillHeld) release(it) }
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
                    when {
                        c.def.id == "_menu" -> listener?.onMenu()
                        isCButton(c.def.id) -> sendCButtons()
                        isKeypadDir(c.def.id) -> sendKeypad()
                        c.def.id in turboIds -> { c.turboState = true; listener?.onButton(c.def.keyCode, true); startTurbo() }
                        else -> listener?.onButton(c.def.keyCode, true)
                    }
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
                    when {
                        c.def.id == "_menu" -> {}
                        isCButton(c.def.id) -> sendCButtons()
                        isKeypadDir(c.def.id) -> sendKeypad()
                        else -> listener?.onButton(c.def.keyCode, false)
                    }
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

    // The N64 has four discrete yellow C-buttons (not a second stick); the core reads them
    // as the right analog, so each held C-button adds its direction and we send the sum.
    /** Pressable face/shoulder buttons that can be set to turbo (id -> display label). */
    fun toggleableButtons(): List<Pair<String, String>> =
        controls.filter { it.def.type == ControlType.BUTTON && it.def.id != "_menu" && !isCButton(it.def.id) && !isKeypad(it.def.id) }
            .map { it.def.id to it.def.label.ifBlank { it.def.id.uppercase() } }

    private fun isCButton(id: String): Boolean = id.startsWith("c_")

    private fun cDir(id: String): Pair<Float, Float> = when (id) {
        "c_up" -> 0f to -1f
        "c_down" -> 0f to 1f
        "c_left" -> -1f to 0f
        "c_right" -> 1f to 0f
        else -> 0f to 0f
    }

    private fun sendCButtons() {
        var nx = 0f
        var ny = 0f
        for (s in controls) {
            if (s.pressed && isCButton(s.def.id)) {
                val (dx, dy) = cDir(s.def.id)
                nx += dx
                ny += dy
            }
        }
        listener?.onStick("cbuttons", nx.coerceIn(-1f, 1f), ny.coerceIn(-1f, 1f))
    }

    // The Intellivision keypad's numbers 1-4 and 6-9 map to the right analog (a 3x3 disc);
    // 5/0/Clear/Enter are ordinary buttons (R3/L3/L2/R2). See FreeIntv's RetroPad mapping.
    private val KEYPAD_DIRS: Map<String, Pair<Float, Float>> = mapOf(
        "kp_1" to (-0.7f to -0.7f), "kp_2" to (0f to -1f), "kp_3" to (0.7f to -0.7f),
        "kp_4" to (-1f to 0f), /* 5 = R3 thumb */          "kp_6" to (1f to 0f),
        "kp_7" to (-0.7f to 0.7f), "kp_8" to (0f to 1f), "kp_9" to (0.7f to 0.7f),
    )

    private fun isKeypad(id: String): Boolean = id.startsWith("kp_")

    private fun isKeypadDir(id: String): Boolean = id in KEYPAD_DIRS

    private fun sendKeypad() {
        var nx = 0f
        var ny = 0f
        for (s in controls) {
            if (s.pressed && isKeypadDir(s.def.id)) {
                val (dx, dy) = KEYPAD_DIRS.getValue(s.def.id)
                nx += dx
                ny += dy
            }
        }
        listener?.onStick("intvkp", nx.coerceIn(-1f, 1f), ny.coerceIn(-1f, 1f))
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
        // Three passes make a control read as sitting on the glass rather than printed on it:
        // its shadow and side wall underneath, then the face, then the lit rim on top.
        if (tiltBezel && !editMode) {
            for (c in controls) drawExtrusion(canvas, c, alpha)
        }
        for (c in controls) {
            drawControl(canvas, c, alpha)
        }
        if (tiltBezel && !editMode) {
            for (c in controls) drawBezel(canvas, c, alpha)
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

    /**
     * The part that sells "on top of the screen": a contact shadow on the glass, then the
     * body's side wall. Both sit on the side away from the light, so they swing with the phone
     * and the control looks like it is standing off the surface rather than drawn onto it.
     */
    private fun drawExtrusion(canvas: Canvas, c: ControlState, alpha: Int) {
        val r = controlRadius(c)
        val cx = centerX(c)
        val cy = centerY(c)
        val (hx, hy) = lightDir()
        val depth = r * EXTRUDE_DEPTH

        // Contact shadow, thrown a little further than the body it belongs to.
        extrudePaint.color = Color.argb(CONTACT_ALPHA * alpha / 255, 0, 0, 0)
        canvas.drawPath(controlPath(c, cx - hx * depth * 1.9f, cy - hy * depth * 1.9f, r), extrudePaint)

        // Side wall: the same silhouette in a darker tone, offset by the extrusion depth. Drawn
        // under the face, so only the sliver away from the light stays visible.
        extrudePaint.color = withAlpha(darken(darken(c.def.fillColor)), alpha)
        canvas.drawPath(controlPath(c, cx - hx * depth, cy - hy * depth, r), extrudePaint)
    }

    /**
     * The bevelled rim. A three-stop gradient (highlight → clear → shade) laid along the light
     * axis and stroked just inside the control's edge, so one side catches the light and the
     * opposite side falls away.
     */
    private fun drawBezel(canvas: Canvas, c: ControlState, alpha: Int) {
        val r = controlRadius(c)
        val cx = centerX(c)
        val cy = centerY(c)
        val w = r * bezelWidth(c)
        applyBezelShader(cx, cy, r, alpha)
        bezelPaint.strokeWidth = w
        bezelPaint.style = Paint.Style.STROKE
        canvas.drawPath(controlPath(c, cx, cy, r - w / 2f), bezelPaint)

        // The stick's knob is its own raised part and reads flat without a rim of its own.
        if (c.def.shape == ControlShape.STICK) {
            val knobR = r * 0.52f
            val kx = cx + c.valueX * r * 0.48f
            val ky = cy + c.valueY * r * 0.48f
            val kw = knobR * BEZEL_WIDTH * 1.4f
            applyBezelShader(kx, ky, knobR, alpha)
            bezelPaint.strokeWidth = kw
            canvas.drawCircle(kx, ky, knobR - kw / 2f, bezelPaint)
        }
    }

    private fun bezelWidth(c: ControlState): Float = when (c.def.shape) {
        ControlShape.CROSS, ControlShape.PSX_CROSS -> BEZEL_WIDTH_CROSS
        else -> BEZEL_WIDTH
    }

    private fun applyBezelShader(cx: Float, cy: Float, r: Float, alpha: Int) {
        val (hx, hy) = lightDir()
        bezelPaint.shader = android.graphics.LinearGradient(
            cx + hx * r, cy + hy * r,
            cx - hx * r, cy - hy * r,
            intArrayOf(
                Color.argb(BEZEL_HIGHLIGHT * alpha / 255, 255, 255, 255),
                Color.TRANSPARENT,
                Color.argb(BEZEL_SHADE * alpha / 255, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }

    /** A control's outer silhouette, for filling (extrusion) or stroking (rim). */
    private fun controlPath(
        c: ControlState,
        cx: Float,
        cy: Float,
        r: Float,
    ): android.graphics.Path = android.graphics.Path().apply {
        when (c.def.shape) {
            ControlShape.CIRCLE, ControlShape.STICK ->
                addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
            ControlShape.PILL -> {
                val w = r * 1.85f
                addRoundRect(
                    RectF(cx - w, cy - r * 0.8f, cx + w, cy + r * 0.8f),
                    r, r, android.graphics.Path.Direction.CW,
                )
            }
            ControlShape.BAR -> {
                val hl = barHalfLen(c)
                val ht = barHalfThick(c)
                addRoundRect(
                    RectF(cx - hl, cy - ht, cx + hl, cy + ht),
                    ht, ht, android.graphics.Path.Direction.CW,
                )
            }
            ControlShape.CROSS, ControlShape.PSX_CROSS -> {
                // Union of the two arms, so the outline follows the cross instead of cutting
                // two rectangles straight through the middle of it.
                val armW = r * 0.62f
                val half = armW / 2f
                val corner = armW * 0.28f
                addRoundRect(
                    RectF(cx - r, cy - half, cx + r, cy + half),
                    corner, corner, android.graphics.Path.Direction.CW,
                )
                val vertical = android.graphics.Path().apply {
                    addRoundRect(
                        RectF(cx - half, cy - r, cx + half, cy + r),
                        corner, corner, android.graphics.Path.Direction.CW,
                    )
                }
                op(vertical, android.graphics.Path.Op.UNION)
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
                // Uniform glyph size across all round buttons, shrinking to fit a tiny button and
                // scaling multi-character labels (e.g. "FIRE") down by length so they don't overflow.
                textPaint.textSize = min(
                    sizeBase() * 0.082f,
                    min(r * 1.25f, r * 3f / max(1, def.label.length)),
                )
                canvas.drawText(def.label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }

            ControlShape.PILL -> {
                val w = r * 1.85f
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
