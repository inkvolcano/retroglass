package com.nvanloo.retroglass.controller

import com.nvanloo.retroglass.controller.ZoneGrid.HAnchor
import com.nvanloo.retroglass.controller.ZoneGrid.VAnchor
import com.nvanloo.retroglass.controller.ZoneGrid.Zone

/**
 * Applies a [ZoneSpec] to a console's authored controls — the step that makes the pads
 * *zone-driven* rather than coordinate-driven (docs/controls-layout-handoff.md §1).
 *
 * The authored layout still defines each cluster's internal geometry (the SNES diamond, a
 * keypad grid, Saturn's twin rows): that is the module's *content*. What this changes is the
 * module's *placement*: each cluster is translated as a rigid group so its centre lands where
 * [ZoneGrid.solve] puts it for the spec's anchors. Reach tuning without free dragging — the
 * editor the handoff explicitly rejected.
 *
 * Groups recognised per pad:
 *  - the directional (plus any button seated at its centre — N64's Z),
 *  - each analog stick,
 *  - the face cluster on each side of the seam (keypads included),
 *  - shoulder bars and pills are left where the zone system already puts them.
 */
object ZoneComposer {

    /** The user-editable knobs, persisted per console by LayoutStore. */
    data class ZoneSpec(
        val dpadDesign: Int = 0,
        val stickDesign: Int = 0,
        val dpadV: VAnchor? = null,
        val dpadH: HAnchor? = null,
        val faceV: VAnchor? = null,
        val faceH: HAnchor? = null,
        val stickV: VAnchor? = null,
        val stickH: HAnchor? = null,
        /** Centre pills + gear: 0 auto · 1 side-by-side row · 2 stacked · 3 combined pill. */
        val pillMode: Int = 0,
    ) {
        fun serialize(): String = listOf(
            "dd=$dpadDesign", "sd=$stickDesign",
            "dv=${dpadV?.name ?: "-"}", "dh=${dpadH?.name ?: "-"}",
            "fv=${faceV?.name ?: "-"}", "fh=${faceH?.name ?: "-"}",
            "sv=${stickV?.name ?: "-"}", "sh=${stickH?.name ?: "-"}",
            "pm=$pillMode",
        ).joinToString(";")

        companion object {
            fun parse(raw: String?): ZoneSpec {
                if (raw.isNullOrBlank()) return ZoneSpec()
                val map = raw.split(';').mapNotNull {
                    val kv = it.split('=', limit = 2)
                    if (kv.size == 2) kv[0] to kv[1] else null
                }.toMap()
                fun v(key: String) = map[key]?.takeIf { it != "-" }?.let {
                    runCatching { VAnchor.valueOf(it) }.getOrNull()
                }
                fun h(key: String) = map[key]?.takeIf { it != "-" }?.let {
                    runCatching { HAnchor.valueOf(it) }.getOrNull()
                }
                return ZoneSpec(
                    dpadDesign = map["dd"]?.toIntOrNull()?.coerceIn(0, 5) ?: 0,
                    stickDesign = map["sd"]?.toIntOrNull()?.coerceIn(0, 5) ?: 0,
                    dpadV = v("dv"), dpadH = h("dh"),
                    faceV = v("fv"), faceH = h("fh"),
                    stickV = v("sv"), stickH = h("sh"),
                    pillMode = map["pm"]?.toIntOrNull()?.coerceIn(0, 3) ?: 0,
                )
            }
        }
    }

    /**
     * Portrait application. Landscape still goes through LandscapeLayout — the auto-solve
     * between orientations is deliberately deferred until its open questions are settled.
     */
    fun apply(controls: List<ControlDef>, spec: ZoneSpec): List<ControlDef> {
        var out = controls

        // Designs are a straight stamp.
        out = out.map {
            when (it.type) {
                ControlType.DPAD -> it.copy(design = spec.dpadDesign)
                ControlType.STICK -> it.copy(design = spec.stickDesign)
                else -> it
            }
        }

        // Anchors: only re-place a group when the spec actually sets one, so an untouched
        // console keeps its authored placement exactly.
        val dpad = out.firstOrNull { it.type == ControlType.DPAD }
        if (dpad != null && (spec.dpadV != null || spec.dpadH != null)) {
            val centre = out.filter {
                it.type == ControlType.BUTTON && it.x == dpad.x && it.y == dpad.y
            }
            out = translateGroup(out, listOf(dpad) + centre, Zone.LC, spec.dpadV, spec.dpadH)
        }

        if (spec.stickV != null || spec.stickH != null) {
            val sticks = out.filter { it.type == ControlType.STICK }
            // Each stick anchors within its own side's block.
            for (stick in sticks) {
                val zone = if (stick.x <= 0.5f) Zone.LC else Zone.RC
                out = translateGroup(out, listOf(stick), zone, spec.stickV, spec.stickH)
            }
        }

        if (spec.faceV != null || spec.faceH != null) {
            val face = out.filter {
                it.type == ControlType.BUTTON &&
                    it.shape == ControlShape.CIRCLE &&
                    it.x > 0.5f &&
                    it.id != "_menu"
            }
            if (face.isNotEmpty()) {
                out = translateGroup(out, face, Zone.RC, spec.faceV, spec.faceH)
            }
        }

        return out
    }

    /** Translates [group] rigidly so its centroid sits at the solved anchor point. */
    private fun translateGroup(
        all: List<ControlDef>,
        group: List<ControlDef>,
        zone: Zone,
        v: VAnchor?,
        h: HAnchor?,
    ): List<ControlDef> {
        if (group.isEmpty()) return all
        val cx = group.map { it.x }.average().toFloat()
        val cy = group.map { it.y }.average().toFloat()
        val spanX = (group.maxOf { it.x + it.size / 2f } - group.minOf { it.x - it.size / 2f })
        val spanY = (group.maxOf { it.y + it.size / 2f } - group.minOf { it.y - it.size / 2f })

        val (tx, ty) = ZoneGrid.solve(
            zone, landscape = false,
            v = v ?: VAnchor.MID,
            h = h ?: HAnchor.CENTER,
            span = maxOf(spanX, spanY),
        )
        val dx = tx - cx
        val dy = ty - cy
        val ids = group.mapTo(HashSet()) { it.id }
        return all.map {
            if (it.id in ids) it.copy(
                x = (it.x + dx).coerceIn(0.02f, 0.98f),
                y = (it.y + dy).coerceIn(0.02f, 0.98f),
            ) else it
        }
    }
}
