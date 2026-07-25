package com.nvanloo.retroglass.controller

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nvanloo.retroglass.R
import com.nvanloo.retroglass.model.Console
import com.nvanloo.retroglass.model.ControllerDefs
import com.nvanloo.retroglass.ui.GameMenuView

/**
 * The controls designer, hosted by whichever screen wants it.
 *
 * It started inside the in-game menu, which meant the only way to lay out a console's pad was to
 * launch one of its games first — awkward for a console you are still setting up, and impossible
 * for one you own no games for yet. Nothing here actually needs a running emulator: the design is
 * derived from the console's shipped controls and saved against the console, not the game. So the
 * whole thing takes its dependencies as parameters and the library can host it too.
 *
 * [onApplied] lets a host react to an edit — in game that re-lays out the live pad; in the library
 * there is nothing to refresh. [onDone] is how the host closes it, since "back to the game" and
 * "back to the library" are not the same gesture.
 */
class ControllerDesigner(
    private val activity: Activity,
    private val menu: GameMenuView,
    private val console: Console,
    private val layoutStore: LayoutStore,
    private val onApplied: () -> Unit = {},
    private val onDone: () -> Unit = {},
) {

    private val startLandscape: Boolean
        get() = activity.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

    fun open() = openDesigner()


    /** The field being edited: zone code plus slot number for the LC/RC columns. */
    private var designerField: Pair<String, Int?>? = null

    /** While moving: the module being carried and the field it came from. */
    private var designerMove: Triple<String, Int?, String>? = null

    /** The design being edited. Every edit is written through immediately. */
    private var designerDraft: PadDesign? = null

    /** Which orientation the canvas is showing. The two layouts are stored and edited apart. */
    private var designerLandscape: Boolean = false

    fun padParts() = PadParts.from(ControllerDefs.controlsFor(console))

    fun padDesign(): PadDesign =
        layoutStore.padDesign(console) ?: PadDeriver.derive(padParts())

    fun draft(): PadDesign = designerDraft ?: padDesign().also { designerDraft = it }

    /**
     * Applies an edit straight through to the pad. Every tap commits, so the canvas and the
     * controls under it never disagree — and Done is an exit, not a save you can forget to press.
     */
    fun editDraft(layout: PadLayout) {
        designerDraft = draft().with(designerLandscape, layout)
        layoutStore.setPadDesign(console, designerDraft!!)
        onApplied()
        menu.refresh()
    }

    fun openDesigner() {
        designerDraft = padDesign()
        designerField = null
        designerMove = null
        // Open on whichever way the phone is actually being held: that is the layout you are
        // looking at, and almost always the one you opened the editor to change.
        designerLandscape = startLandscape
        menu.push(activity.getString(R.string.menu_edit_layout)) { menuDesignerScreen() }
    }


    /**
     * The controls designer (docs/controls-layout-handoff.md §6).
     *
     * The canvas is the screen. It stands at the phone's own aspect and fills the menu, so a
     * module's size and reach read the same here as they will under your thumbs — a thumbnail
     * cannot tell you whether something is within reach, which is most of what this screen is for.
     *
     * Field-first, and that ordering is the point: you tap the part of the pad you want to change
     * and are then shown only the modules that can go there, rather than picking a module and
     * hunting for somewhere it fits. Choosing takes over the whole screen rather than sharing it
     * with the canvas, because the module list carries a rendered preview per row and those are
     * useless at the size a split screen leaves them.
     */
    fun menuDesignerScreen(): View = with(menu) {
        val parts = padParts()
        val layout = draft().forOrientation(designerLandscape)
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val metrics = activity.resources.displayMetrics
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
        // The canvas is the phone, turned the way the layout being edited is held: tall and narrow
        // for portrait, short and wide for landscape. Fitted to whichever bound binds first.
        val ratio = if (designerLandscape) longSide / shortSide.toFloat()
        else shortSide / longSide.toFloat()
        val availH = (metrics.heightPixels - dp(230f)).coerceAtLeast(dp(220f))
        val availW = metrics.widthPixels - dp(24f)
        var canvasH = availH
        var canvasW = (canvasH * ratio).toInt()
        if (canvasW > availW) {
            canvasW = availW
            canvasH = (canvasW / ratio).toInt()
        }
        val canvas = DesignerView(activity).apply {
            bind(
                console, layout, parts,
                layoutStore.portraitScreenFraction(), designerLandscape,
            )
            onRotate = {
                designerLandscape = !designerLandscape
                designerField = null
                menu.refresh()
            }
            selected = designerField
            onLayoutChange = { editDraft(it) }
            onFieldTap = { zone, slot ->
                val moving = designerMove
                if (moving != null) {
                    moveModule(layout, moving, zone, slot)
                } else {
                    designerField = zone to slot
                    pushDesignerField()
                }
            }
        }

        body(padSides = 10f) {
            designerMove?.let { moving ->
                addView(
                    note(
                        activity.getString(
                            R.string.designer_moving,
                            PadModules.byId(moving.third)?.name.orEmpty(),
                        ),
                    ),
                )
            }
            addView(
                TextView(activity).apply {
                    text = activity.getString(
                        if (designerLandscape) R.string.designer_landscape
                        else R.string.designer_portrait,
                    )
                    textSize = 12f
                    setTextColor(Color.parseColor("#8A8A98"))
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            addView(
                canvas,
                LinearLayout.LayoutParams(canvasW, canvasH).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            addView(spacer())
            if (designerMove != null) {
                addView(navRow(null, activity.getString(R.string.designer_move_cancel)) {
                    designerMove = null
                    menu.refresh()
                })
            } else {
                addView(
                    pair(
                        actionTile(
                            activity.getString(R.string.designer_done),
                            activity.getString(R.string.designer_done_sub),
                        ) {
                            designerDraft = null
                            designerField = null
                            onDone()
                        },
                        actionTile(
                            activity.getString(R.string.designer_reset),
                            activity.getString(R.string.designer_reset_sub),
                        ) {
                            layoutStore.resetPadDesign(console)
                            designerDraft = padDesign()
                            designerField = null
                            onApplied()
                            menu.refresh()
                        },
                    ),
                )
            }
        }
    }

    fun pushDesignerField() {
        val (zone, slot) = designerField ?: return
        val layout = draft().forOrientation(designerLandscape)
        val start = slot?.let { layout.covering(zone, it, 1).firstOrNull()?.first }
        val module = PadModules.byId(layout.moduleAt(zone, start ?: slot))
        menu.push(fieldTitle(zone, slot, module)) { menuDesignerFieldScreen() }
    }

    /** What to do with the tapped field - its own screen, reached by tapping the canvas. */
    fun menuDesignerFieldScreen(): View = with(menu) {
        val layout = draft().forOrientation(designerLandscape)
        val field = designerField ?: return@with body { }
        val zone = field.first
        val slot = field.second
        val start = slot?.let { layout.covering(zone, it, 1).firstOrNull()?.first }
        val id = layout.moduleAt(zone, start ?: slot)
        val module = PadModules.byId(id)
        body {
            if (module == null) {
                addView(navRow(null, activity.getString(R.string.designer_add)) {
                    push(activity.getString(R.string.designer_add)) { menuDesignerListScreen() }
                })
            } else {
                addView(navRow(null, activity.getString(R.string.designer_edit), module.name) {
                    push(activity.getString(R.string.designer_edit)) { menuDesignerListScreen() }
                })
                if (module.cat == PadModules.Cat.BLOCK && slot != null) {
                    addView(designerAlignRow(layout, zone, start ?: slot))
                }
                addView(navRow(null, activity.getString(R.string.designer_move)) {
                    designerMove = Triple(zone, start ?: slot, id!!)
                    pop()
                })
                addView(navRow(null, activity.getString(R.string.designer_remove)) {
                    editDraft(removeAt(layout, zone, start ?: slot))
                    pop()
                })
            }
        }
    }

    /** The modules that fit this field, with the rules shown rather than enforced silently. */
    fun menuDesignerListScreen(): View = with(menu) {
        val parts = padParts()
        val layout = draft().forOrientation(designerLandscape)
        val field = designerField ?: return@with body { }
        val zone = field.first
        val slot = field.second
        val centre = slot == null
        val current = layout.moduleAt(zone, slot)
        val thumbH = (76 * activity.resources.displayMetrics.density).toInt()
        val thumbW = (thumbH / LayoutPreview.ASPECT).toInt()

        val options = (if (centre) {
            PadModules.optionsFor(PadModules.Cat.SYSTEM, parts, zone)
        } else {
            PadModules.optionsFor(PadModules.Cat.BLOCK, parts, zone) +
                PadModules.optionsFor(PadModules.Cat.SHOULDER, parts, zone)
        }).filter { hasRoomFor(layout, parts, zone, slot, it) }

        body {
            for (module in options) {
                val blocked = blockedReason(layout, zone, slot, module)
                val next = if (blocked == null) place(layout, zone, slot, module) else layout
                val replaces = if (blocked == null) replacedNames(layout, zone, slot, module) else null
                val shot = LayoutPreview.render(
                    console, PadRenderer.render(next, parts, landscape = false), thumbW, thumbH,
                )
                addView(
                    modulePickRow(
                        shot, module.name, blocked ?: replaces,
                        selected = module.id == current,
                        enabled = blocked == null,
                    ) {
                        editDraft(next)
                        // Straight back to the canvas: the point of picking was to see it in place.
                        pop()
                        pop()
                    },
                )
            }
        }
    }

    /**
     * Alignment, as the wireframe draws it: three boxes with a marker sitting where the module
     * will sit. Picking one shifts the module in the canvas immediately.
     *
     * A dropdown would have been less code, but "inner / centre / outer" only means anything once
     * you can see which edge of the block is which — and on a mirrored pair of columns, outer is a
     * different direction on each side. The picture says it; the words need translating.
     */
    fun designerAlignRow(layout: PadLayout, zone: String, slot: Int): View {
        val key = zone + slot
        val current = layout.align[key] ?: 'c'
        val d = activity.resources.displayMetrics.density
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.designer_align)
                    textSize = 15f
                    setTextColor(Color.parseColor("#E8E8F0"))
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            for (side in listOf('l', 'c', 'r')) {
                addView(
                    alignBox(side, current) {
                        editDraft(layout.copy(align = layout.align + (key to side)))
                    },
                    LinearLayout.LayoutParams((34 * d).toInt(), (28 * d).toInt()).apply {
                        marginStart = (8 * d).toInt()
                    },
                )
            }
        }
    }

    /** One alignment box: a marker parked at the edge the module would be pushed to. */
    fun alignBox(side: Char, current: Char, onPick: () -> Unit): View {
        val d = activity.resources.displayMetrics.density
        val on = side == current
        val edge = if (on) Color.parseColor("#C6FF4A") else Color.parseColor("#7A7A88")
        return FrameLayout(activity).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (on) Color.parseColor("#22C6FF4A") else Color.TRANSPARENT)
                cornerRadius = 6 * d
                setStroke((1.5f * d).toInt(), edge)
            }
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
            addView(
                View(activity).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(edge)
                        cornerRadius = 2 * d
                    }
                },
                FrameLayout.LayoutParams((9 * d).toInt(), (9 * d).toInt()).apply {
                    gravity = Gravity.CENTER_VERTICAL or when (side) {
                        'l' -> Gravity.START
                        'r' -> Gravity.END
                        else -> Gravity.CENTER_HORIZONTAL
                    }
                },
            )
            setOnClickListener { onPick() }
        }
    }

    // ---- designer rules ---------------------------------------------------------------------

    /**
     * Why a module cannot go in a field, in words, or null when it can.
     *
     * The one cross-zone rule the handoff defines: a combined shoulder pill and a two-button CT
     * module share the top row, so whichever is placed second would sit on top of the first.
     * Enforced in both directions, since either can be picked first.
     */
    fun blockedReason(
        layout: PadLayout,
        zone: String,
        slot: Int?,
        module: PadModules.Module,
    ): String? {
        if (slot == null) {
            if (zone == PadLayout.ZONE_CT && PadModules.isMultiSystem(module.id) &&
                (PadModules.isCombinedShoulder(layout.lc[1]) || PadModules.isCombinedShoulder(layout.rc[1]))
            ) return activity.getString(R.string.designer_blocked_shoulder)
            return null
        }
        if (module.cat == PadModules.Cat.SHOULDER) {
            if (slot > 2) return activity.getString(R.string.designer_blocked_slot)
            if (slot == 1 && PadModules.isCombinedShoulder(module.id) &&
                PadModules.isMultiSystem(layout.ct)
            ) return activity.getString(R.string.designer_blocked_ct)
        }
        return null
    }

    fun replacedNames(
        layout: PadLayout,
        zone: String,
        slot: Int?,
        module: PadModules.Module,
    ): String? {
        if (slot == null) {
            val current = layout.moduleAt(zone, null) ?: return null
            if (current == module.id) return null
            return activity.getString(R.string.designer_replaces, PadModules.byId(current)?.name.orEmpty())
        }
        val start = if (module.cat == PadModules.Cat.SHOULDER) slot
        else layout.clampStart(slot, module.slots)
        val hit = layout.covering(zone, start, module.slots).filter { it.second != module.id }
        if (hit.isEmpty()) return null
        return activity.getString(
            R.string.designer_replaces,
            hit.joinToString(", ") { PadModules.byId(it.second)?.name.orEmpty() },
        )
    }

    /**
     * Whether the console still has an unplaced copy of what this module draws — ignoring the
     * field being edited, since replacing a d-pad with another d-pad design is always fine.
     */
    fun hasRoomFor(
        layout: PadLayout,
        parts: PadParts,
        zone: String,
        slot: Int?,
        module: PadModules.Module,
    ): Boolean {
        val capacity = PadModules.capacity(module.family, parts)
        if (capacity == Int.MAX_VALUE) return true
        if (capacity == 0) return false
        var used = 0
        fun count(atZone: String, atSlot: Int?, id: String?) {
            if (atZone == zone && atSlot == slot) return
            // Shoulders are per-column: the left stack being placed says nothing about the right.
            val family = PadModules.byId(id)?.family ?: return
            if (family != module.family) return
            if (family == PadModules.Family.SHOULDER && atZone != zone) return
            used++
        }
        count(PadLayout.ZONE_CT, null, layout.ct)
        count(PadLayout.ZONE_CL, null, layout.cl)
        for (z in listOf(PadLayout.ZONE_LC, PadLayout.ZONE_RC)) {
            layout.column(z).forEach { (s, id) -> count(z, s, id) }
        }
        return used < capacity
    }

    /** An image plus a two-line label — [previewRow] ellipsizes, which hid every rule message. */
    fun modulePickRow(
        image: android.graphics.Bitmap,
        name: String,
        subtitle: String?,
        selected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val d = activity.resources.displayMetrics.density
        setPadding((10 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        if (enabled) setOnClickListener { onClick() }
        addView(android.widget.ImageView(activity).apply {
            setImageBitmap(image)
        }, LinearLayout.LayoutParams(image.width, image.height))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = name
                textSize = 15f
                setTextColor(
                    when {
                        !enabled -> Color.parseColor("#FF8A80")
                        selected -> Color.parseColor("#C6FF4A")
                        else -> Color.parseColor("#E8E8F0")
                    },
                )
            })
            if (subtitle != null) {
                addView(TextView(activity).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(
                        if (enabled) Color.parseColor("#FFC46B") else Color.parseColor("#FF8A80"),
                    )
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (14 * d).toInt()
        })
    }

    fun place(
        layout: PadLayout,
        zone: String,
        slot: Int?,
        module: PadModules.Module,
    ): PadLayout {
        if (slot == null) return layout.withZone(zone, module.id)
        // Shoulders keep the row you tapped; anything taller slides up so it fits in the column.
        val start = if (module.cat == PadModules.Cat.SHOULDER) slot
        else layout.clampStart(slot, module.slots)
        val column = layout.column(zone).toMutableMap()
        layout.covering(zone, start, module.slots).forEach { column.remove(it.first) }
        column[start] = module.id
        return layout.withColumn(zone, column)
    }

    fun removeAt(layout: PadLayout, zone: String, slot: Int?): PadLayout {
        if (slot == null) return layout.withZone(zone, null)
        return layout.withColumn(zone, layout.column(zone) - slot)
    }

    fun moveModule(
        layout: PadLayout,
        moving: Triple<String, Int?, String>,
        zone: String,
        slot: Int?,
    ) {
        val (fromZone, fromSlot, id) = moving
        val module = PadModules.byId(id) ?: return
        val centre = slot == null
        // A module can only land where its own kind is allowed, so a stick cannot be carried into
        // the shoulder row and a START pill cannot be dropped into a block column.
        val allowed = if (centre) module.cat == PadModules.Cat.SYSTEM &&
            (!PadModules.isClOnly(id) || zone == PadLayout.ZONE_CL)
        else module.cat != PadModules.Cat.SYSTEM
        if (!allowed || blockedReason(layout, zone, slot, module) != null) return
        val cleared = removeAt(layout, fromZone, fromSlot)
        designerMove = null
        designerField = zone to slot
        editDraft(place(cleared, zone, slot, module))
    }

    fun fieldTitle(zone: String, slot: Int?, module: PadModules.Module?): String {
        val where = if (slot == null) zone else activity.getString(R.string.designer_slot, zone, slot)
        return module?.let { "$where — ${it.name}" } ?: activity.getString(R.string.designer_empty, where)
    }
}
