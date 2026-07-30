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

    /** Which rows sit under the preview: the field's actions, or its module list. */
    private var designerMode = MODE_MENU

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
        designerMode = MODE_MENU
        designerLandscape = startLandscape
        menu.push(activity.getString(R.string.menu_edit_layout)) { menuDesignerScreen() }
    }

    /**
     * The designer: a preview you tap, and the controls for it underneath.
     *
     * The preview takes a fixed share of the screen rather than all of it. An earlier version
     * filled the height and put every setting inside the picture as the wireframe draws them — at
     * wireframe scale those are 7px labels, which is legible on a desktop mock-up and unusable
     * under a thumb. The settings are ordinary rows now, and the preview keeps enough of the screen
     * to judge reach by.
     *
     * Everything happens on this one screen. Picking a field swaps the rows beneath the preview
     * instead of pushing a new screen over it, so the preview stays visible while you choose — the
     * whole point of choosing is seeing where the thing lands.
     */
    fun menuDesignerScreen(): View = with(menu) {
        val parts = padParts()
        val layout = draft().forOrientation(designerLandscape)
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val metrics = activity.resources.displayMetrics
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
        val ratio = if (designerLandscape) longSide / shortSide.toFloat()
        else shortSide / longSide.toFloat()
        // 60% of the screen for the preview, then whatever width keeps the phone's shape.
        var canvasH = (metrics.heightPixels * 0.6f).toInt()
        var canvasW = (canvasH * ratio).toInt()
        val availW = metrics.widthPixels - dp(24f)
        if (canvasW > availW) {
            canvasW = availW
            canvasH = (canvasW / ratio).toInt()
        }
        val canvas = DesignerView(activity).apply {
            bind(
                console, layout, parts,
                layoutStore.portraitScreenFraction(), designerLandscape,
            )
            selected = designerField
            onFieldTap = { zone, slot ->
                val moving = designerMove
                if (moving != null) {
                    moveModule(layout, moving, zone, slot)
                } else {
                    designerField = zone to slot
                    designerMode = MODE_MENU
                    menu.refresh()
                }
            }
        }

        body(padSides = 10f) {
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
            when {
                designerMove != null -> movePanel(this)
                designerField != null && designerMode == MODE_LIST -> listPanel(this, layout, parts)
                designerField != null -> fieldPanel(this, layout)
                else -> {
                    addView(note(activity.getString(R.string.designer_hint)))
                    settingsPanel(this, layout)
                    donePanel(this, parts)
                }
            }
        }
    }

    /** Rows for the tapped field, kept above the settings so they need no scrolling. */
    private fun fieldPanel(root: LinearLayout, layout: PadLayout) = with(menu) {
        val field = designerField ?: return@with
        val zone = field.first
        val slot = field.second
        val start = slot?.let { layout.covering(zone, it, 1).firstOrNull()?.first }
        val id = layout.moduleAt(zone, start ?: slot)
        val module = PadModules.byId(id)
        root.addView(group(fieldTitle(zone, slot, module)))
        if (module == null) {
            root.addView(navRow(null, activity.getString(R.string.designer_add)) {
                designerMode = MODE_LIST
                menu.refresh()
            })
        } else {
            root.addView(navRow(null, activity.getString(R.string.designer_edit), module.name) {
                designerMode = MODE_LIST
                menu.refresh()
            })
            if (module.cat == PadModules.Cat.BLOCK && slot != null) {
                root.addView(designerAlignRow(layout, zone, start ?: slot))
            }
            root.addView(navRow(null, activity.getString(R.string.designer_move)) {
                designerMove = Triple(zone, start ?: slot, id!!)
                menu.refresh()
            })
            root.addView(navRow(null, activity.getString(R.string.designer_remove)) {
                editDraft(removeAt(layout, zone, start ?: slot))
            })
        }
        root.addView(navRow(null, activity.getString(R.string.designer_deselect)) {
            designerField = null
            menu.refresh()
        })
    }

    /** The modules that fit this field, listed under the preview so you can see each land. */
    private fun listPanel(
        root: LinearLayout,
        layout: PadLayout,
        parts: PadParts,
    ) = with(menu) {
        val field = designerField ?: return@with
        val zone = field.first
        val slot = field.second
        val centre = slot == null
        val current = layout.moduleAt(zone, slot)
        val thumbH = (54 * activity.resources.displayMetrics.density).toInt()
        val thumbW = (thumbH / LayoutPreview.ASPECT).toInt()

        val options = (if (centre) {
            PadModules.optionsFor(PadModules.Cat.SYSTEM, parts, zone)
        } else {
            PadModules.optionsFor(PadModules.Cat.BLOCK, parts, zone) +
                PadModules.optionsFor(PadModules.Cat.SHOULDER, parts, zone)
        }).filter { hasRoomFor(layout, parts, zone, slot, it) }

        root.addView(group(fieldTitle(zone, slot, PadModules.byId(current))))
        for (module in options) {
            val blocked = blockedReason(layout, zone, slot, module)
            val next = if (blocked == null) place(layout, zone, slot, module) else layout
            val replaces = if (blocked == null) replacedNames(layout, zone, slot, module) else null
            val shot = LayoutPreview.render(
                console,
                PadRenderer.render(next, parts, designerLandscape),
                thumbW,
                thumbH,
            )
            root.addView(
                modulePickRow(
                    shot, module.name, blocked ?: replaces,
                    selected = module.id == current,
                    enabled = blocked == null,
                ) {
                    designerMode = MODE_MENU
                    editDraft(next)
                },
            )
        }
        root.addView(navRow(null, activity.getString(R.string.designer_back)) {
            designerMode = MODE_MENU
            menu.refresh()
        })
    }

    private fun movePanel(root: LinearLayout) = with(menu) {
        val moving = designerMove ?: return@with
        root.addView(
            note(
                activity.getString(
                    R.string.designer_moving,
                    PadModules.byId(moving.third)?.name.orEmpty(),
                ),
            ),
        )
        root.addView(navRow(null, activity.getString(R.string.designer_move_cancel)) {
            designerMove = null
            menu.refresh()
        })
    }

    /**
     * The pad's own settings, as ordinary rows.
     *
     * These were pills inside the preview, which is where the wireframe puts them — but the
     * wireframe is a drawing, and at the size the picture actually needs to be useful the pills
     * came out too small to hit. Rows cost vertical space and win every time on a phone.
     */
    private fun settingsPanel(root: LinearLayout, layout: PadLayout) = with(menu) {
        root.addView(group(activity.getString(R.string.designer_group_layout)))
        root.addView(
            navRow(
                null, activity.getString(R.string.designer_orientation),
                activity.getString(
                    if (designerLandscape) R.string.designer_landscape_short
                    else R.string.designer_portrait_short,
                ),
            ) {
                designerLandscape = !designerLandscape
                designerField = null
                menu.refresh()
            },
        )
        root.addView(
            navRow(null, activity.getString(R.string.designer_zones), "${layout.zones}") {
                pushSelect(
                    activity.getString(R.string.designer_zones),
                    PadLayout.ZONE_COUNTS.map { "$it" },
                    PadLayout.ZONE_COUNTS.indexOf(layout.zones),
                ) { which ->
                    editDraft(layout.copy(zones = PadLayout.ZONE_COUNTS[which]).prunedToZones())
                }
            },
        )
        root.addView(
            navRow(null, activity.getString(R.string.designer_width), PadLayout.splitLabel(layout.split)) {
                pushSelect(
                    activity.getString(R.string.designer_width),
                    PadLayout.SPLITS.map { PadLayout.splitLabel(it) },
                    PadLayout.SPLITS.indexOf(layout.split),
                ) { which -> editDraft(layout.copy(split = PadLayout.SPLITS[which])) }
            },
        )
        // The two largest scales only make sense with the picture elsewhere.
        val scaleIds = PadLayout.SCALES +
            if (layout.noScr) PadLayout.EXTERNAL_SCALES else emptyList()
        root.addView(
            navRow(null, activity.getString(R.string.designer_scale), PadLayout.scaleLabel(layout.scale)) {
                pushSelect(
                    activity.getString(R.string.designer_scale),
                    scaleIds.map { PadLayout.scaleLabel(it) },
                    scaleIds.indexOf(layout.scale).coerceAtLeast(0),
                ) { which -> editDraft(layout.copy(scale = scaleIds[which])) }
            },
        )
        root.addView(
            toggleRow(activity.getString(R.string.designer_external), layout.noScr) {
                editDraft(layout.copy(noScr = it))
            },
        )
        if (layout.noScr) {
            root.addView(
                toggleRow(activity.getString(R.string.designer_reflow), layout.reflow) {
                    editDraft(layout.copy(reflow = it))
                },
            )
        }
        root.addView(group(activity.getString(R.string.designer_group_shadow)))
        root.addView(
            toggleRow(activity.getString(R.string.designer_shadow_screen), layout.shadowScreen) {
                editDraft(layout.copy(shadowScreen = it))
            },
        )
        root.addView(
            toggleRow(activity.getString(R.string.designer_shadow_buttons), layout.shadowButtons) {
                editDraft(layout.copy(shadowButtons = it))
            },
        )
        root.addView(
            toggleRow(activity.getString(R.string.designer_shadow_dpad), layout.shadowDpad) {
                editDraft(layout.copy(shadowDpad = it))
            },
        )
        root.addView(
            toggleRow(activity.getString(R.string.designer_shadow_stick), layout.shadowStick) {
                editDraft(layout.copy(shadowStick = it))
            },
        )
    }

    private fun donePanel(root: LinearLayout, parts: PadParts) = with(menu) {
        root.addView(
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

    /**
     * Alignment, as the wireframe draws it: three boxes with a marker sitting where the module
     * will sit. Picking one shifts the module in the preview immediately.
     *
     * A dropdown would be less code, but "inner / centre / outer" only means something once you can
     * see which edge of the block is which — and on a mirrored pair of columns, outer points the
     * other way on each side. The picture says it; the words need translating.
     */
    private fun designerAlignRow(layout: PadLayout, zone: String, slot: Int): View {
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

    /**
     * A module row: its rendered pad, its name, and any rule message right-aligned beside it —
     * the wireframe's three-part row. The shared preview row ellipsizes a single line, which hid
     * every "replaces" warning, and stacking the message under the name pushed the rows so tall
     * that fewer than four fitted on screen at once.
     */
    private fun modulePickRow(
        image: android.graphics.Bitmap,
        name: String,
        tag: String?,
        selected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val d = activity.resources.displayMetrics.density
        setPadding((10 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        if (enabled) setOnClickListener { onClick() }
        addView(
            android.widget.ImageView(activity).apply { setImageBitmap(image) },
            LinearLayout.LayoutParams(image.width, image.height),
        )
        addView(
            TextView(activity).apply {
                text = name
                textSize = 15f
                setTextColor(
                    when {
                        !enabled -> Color.parseColor("#FF8A80")
                        selected -> Color.parseColor("#C6FF4A")
                        else -> Color.parseColor("#E8E8F0")
                    },
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * d).toInt()
            },
        )
        if (tag != null) {
            addView(
                TextView(activity).apply {
                    text = tag
                    textSize = 11f
                    gravity = Gravity.END
                    setTextColor(
                        if (enabled) Color.parseColor("#FFC46B") else Color.parseColor("#FF8A80"),
                    )
                },
                LinearLayout.LayoutParams(
                    (112 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = (8 * d).toInt() },
            )
        }
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
        designerMode = MODE_MENU
        designerField = zone to slot
        editDraft(place(cleared, zone, slot, module))
    }

    private companion object {
        const val MODE_MENU = "menu"
        const val MODE_LIST = "list"
    }

    fun fieldTitle(zone: String, slot: Int?, module: PadModules.Module?): String {
        val where = if (slot == null) zone else activity.getString(R.string.designer_slot, zone, slot)
        return module?.let { "$where — ${it.name}" } ?: activity.getString(R.string.designer_empty, where)
    }
}
