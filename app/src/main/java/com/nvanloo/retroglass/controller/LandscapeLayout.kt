package com.nvanloo.retroglass.controller

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Rearranges a console's portrait-authored control layout into a LANDSCAPE arrangement, so the
 * on-phone touch pad in landscape matches the doc galleries (scripts/gen_landscape_frame.py — this
 * is the faithful Kotlin port of `landscape_frame`). The key fix over drawing the portrait coords
 * directly: the face-button cluster is rebuilt aspect-corrected, so up/down spacing equals
 * left/right spacing in real pixels (otherwise the wide screen stretches a diamond flat).
 *
 * With [screen] = true the pad frames a centred game window (game on the phone); with false it's a
 * full-screen pad for external-display play (controls spread wider, Start/Select in the centre).
 * The D-pad and analog sticks snap to fixed slots, buttons keep a workable minimum size, and a
 * bounding-box de-overlap pass guarantees nothing collides. N64's seven-button right side is
 * hand-authored (two 45°-slanted rows + stick bottom-right).
 */
object LandscapeLayout {

    private const val SIZE_CAP = 0.46f
    private const val GAME_L = 0.255f
    private const val GAME_R = 0.745f
    private const val YSPAN = 1.16f
    private const val SIZE_K = 0.82f
    private const val DPAD_SIZE = 0.36f
    private const val STICK_SIZE = 0.24f
    private const val FACE_COLHALF = 0.058f
    private const val FACE_VHALF = 0.20f
    private const val FACE_KMAX = 380f
    private const val FACE_MIN = 0.115f
    private const val ASPECT = 1.42f
    private const val EDGE = 0.032f

    private val SHOULDER_IDS = setOf("l", "r", "l1", "l2", "r1", "r2", "lt", "rt", "zl", "zr")
    private val SYS_IDS = setOf("start", "select", "menu", "pause", "option", "reset", "mode", "run", "coin", "sel")

    /** The landscape sizing reference, capped so buttons stay phone-sized on tall/near-square screens. */
    fun sizeMin(w: Float, h: Float) = min(w, min(h, w * SIZE_CAP))

    private fun roleOf(shape: ControlShape, id: String): String = when {
        shape == ControlShape.STICK -> "stick"
        shape == ControlShape.CROSS || shape == ControlShape.PSX_CROSS -> "dpad"
        shape == ControlShape.BAR || id.lowercase() in SHOULDER_IDS -> "shoulder"
        id.lowercase() in SYS_IDS -> "system"
        else -> "face"
    }

    private class W(val def: ControlDef) {
        var x = def.x
        var y = def.y
        var size = def.size
        val shape = def.shape
        val id = def.id
        val plate = def.plateColor != Color.TRANSPARENT
    }

    fun transform(base: List<ControlDef>, screen: Boolean, w: Float, h: Float): List<ControlDef> {
        if (w <= 0f || h <= 0f) return base
        val controls = base.filter { it.id != "_menu" }.map { W(it) }
        if (controls.isEmpty()) return base
        val sm = sizeMin(w, h)

        fun radius(c: W) = c.size / 2f * sm
        fun halfX(c: W) = when (c.shape) {
            ControlShape.BAR -> c.size * w / 2f
            ControlShape.PILL -> radius(c) * 1.85f
            ControlShape.CIRCLE -> radius(c) * if (c.plate) 1.28f else 1f
            else -> radius(c)
        }
        fun halfY(c: W) = when (c.shape) {
            ControlShape.BAR -> sm * 0.062f
            ControlShape.PILL -> radius(c) * 0.8f
            ControlShape.CIRCLE -> radius(c) * if (c.plate) 1.28f else 1f
            else -> radius(c)
        }

        // ---- mode config
        val dp: Pair<Float, Float>; val dsz: Float
        val fp: Pair<Float, Float>; val fch: Float; val fvh: Float
        val ls: Pair<Float, Float>; val rs: Pair<Float, Float>; val ssz: Float
        val gl: Float; val gr: Float
        val lcol: Pair<Float, Float>; val rcol: Pair<Float, Float>
        if (screen) {
            dp = 0.118f to 0.44f; dsz = DPAD_SIZE
            fp = 0.868f to 0.44f; fch = FACE_COLHALF; fvh = FACE_VHALF
            ls = 0.118f to 0.805f; rs = 0.882f to 0.805f; ssz = STICK_SIZE
            gl = GAME_L; gr = GAME_R
            lcol = 0.035f to 0.205f; rcol = 0.795f to 0.965f
        } else {
            dp = 0.185f to 0.44f; dsz = 0.42f
            fp = 0.815f to 0.44f; fch = 0.085f; fvh = 0.20f
            ls = 0.185f to 0.83f; rs = 0.815f to 0.83f; ssz = 0.27f
            gl = 0.44f; gr = 0.56f
            lcol = 0.05f to 0.30f; rcol = 0.70f to 0.95f
        }
        val margin = 8f / w

        val lxs = controls.filter { it.x < 0.5f }.map { it.x }.ifEmpty { listOf(0.25f) }
        val rxs = controls.filter { it.x >= 0.5f }.map { it.x }.ifEmpty { listOf(0.75f) }
        val lmin = lxs.min(); val lmax = lxs.max(); val rmin = rxs.min(); val rmax = rxs.max()

        // ---- right-hand face cluster (≤4): centre + aspect-correct
        val rface = controls.indices.filter { roleOf(controls[it].shape, controls[it].id) == "face" && controls[it].x >= 0.5f }
        val anchorFace = rface.size in 1..4
        var cx0 = 0f; var cy0 = 0f; var fscale = 0f
        if (anchorFace) {
            cx0 = rface.map { controls[it].x }.average().toFloat()
            cy0 = rface.map { controls[it].y }.average().toFloat()
            val mdx = rface.maxOf { abs(controls[it].x - cx0) }
            val mdy = rface.maxOf { abs(controls[it].y - cy0) }
            val sx = if (mdx > 1e-6f) fch * w / mdx else 1e9f
            val sy = if (mdy > 1e-6f) fvh * h / (mdy * ASPECT) else 1e9f
            fscale = if (rface.size == 4) min(sx, sy) else min(min(sx, sy), FACE_KMAX)
            var need = 0f
            for (a in rface.indices) for (b in a + 1 until rface.size) {
                val i = rface[a]; val j = rface[b]
                val dn = hypot(controls[i].x - controls[j].x, (controls[i].y - controls[j].y) * ASPECT)
                if (dn < 1e-6f) continue
                val ri = max(controls[i].size * SIZE_K, FACE_MIN) / 2f * sm
                val rj = max(controls[j].size * SIZE_K, FACE_MIN) / 2f * sm
                need = max(need, (ri + rj) * 1.06f / dn)
            }
            fscale = max(fscale, need)
        }

        val sysc = if (!screen)
            controls.indices.filter { roleOf(controls[it].shape, controls[it].id) == "system" }.toSet()
        else emptySet()

        // ---- N64 special case
        val ids = controls.map { it.id }.toSet()
        val n64 = setOf("c_up", "c_down", "c_left", "c_right", "z").all { it in ids }
        val n64pos = HashMap<String, Pair<Float, Float>>()
        var n64stick = 0f to 0f
        if (n64) {
            val ay = 0.44f
            val sx = 0.098f * sm / w; val sy = 0.098f * sm / h
            val ry = 0.19f * sm / h
            val gapL = if (screen) GAME_R else 0.56f
            val ax = (gapL + 1f) / 2f - 1.5f * sx
            n64pos["start"] = (ax + 0.01f) to 0.10f
            n64pos["z"] = (ax - 0.005f) to 0.24f
            n64pos["a"] = ax to ay
            n64pos["c_up"] = (ax + sx) to (ay - sy)
            n64pos["c_right"] = (ax + 2 * sx) to (ay - 2 * sy)
            n64pos["b"] = (ax + sx) to (ay + ry - sy)
            n64pos["c_left"] = (ax + 2 * sx) to (ay + ry - 2 * sy)
            n64pos["c_down"] = (ax + 3 * sx) to (ay + ry - 3 * sy)
            n64stick = (ax + 1.5f * sx) to 0.81f
        }

        val anchored = HashSet<Int>()
        controls.forEachIndexed { i, c ->
            val left = c.x < 0.5f
            when {
                n64 && c.id in n64pos -> {
                    val p = n64pos[c.id]!!; c.x = p.first; c.y = p.second
                    c.size = when {
                        c.id == "start" -> c.def.size * SIZE_K
                        c.id == "z" -> 0.11f
                        c.id.startsWith("c_") -> 0.092f
                        else -> 0.115f
                    }
                    anchored.add(i)
                }
                n64 && c.shape == ControlShape.STICK -> {
                    c.x = n64stick.first; c.y = n64stick.second; c.size = STICK_SIZE; anchored.add(i)
                }
                c.shape == ControlShape.CROSS || c.shape == ControlShape.PSX_CROSS -> {
                    c.x = dp.first; c.y = dp.second; c.size = dsz; anchored.add(i)
                }
                c.shape == ControlShape.STICK -> {
                    val p = if (left) ls else rs; c.x = p.first; c.y = p.second; c.size = ssz; anchored.add(i)
                }
                anchorFace && i in rface -> {
                    val dx = c.x - cx0; val dy = c.y - cy0
                    c.x = fp.first + dx * fscale / w
                    c.y = fp.second + dy * ASPECT * fscale / h
                    c.size = max(c.size * SIZE_K, FACE_MIN)
                    anchored.add(i)
                }
                i in sysc -> {
                    c.x = if (left) 0.44f else 0.56f; c.y = 0.90f; c.size *= SIZE_K; anchored.add(i)
                }
                else -> {
                    c.x = if (left) {
                        val t = if (lmax == lmin) 0.5f else (c.x - lmin) / (lmax - lmin)
                        lcol.first + t * (lcol.second - lcol.first)
                    } else {
                        val t = if (rmax == rmin) 0.5f else (c.x - rmin) / (rmax - rmin)
                        rcol.first + t * (rcol.second - rcol.first)
                    }
                    c.y = min(0.92f, max(0.08f, 0.5f + (c.y - 0.5f) * YSPAN))
                    c.size *= SIZE_K
                    if (roleOf(c.shape, c.id) == "face") c.size = max(c.size, FACE_MIN)
                }
            }
        }

        // ---- keep boxes clear of the screen window, then off the outer edges
        controls.forEachIndexed { idx, c ->
            val left = c.x < 0.5f
            var hx = halfX(c) / w
            if (screen) {
                val avail = if (left) gl - margin else 1f - gr - margin
                if (idx !in anchored && hx > avail) { c.size *= max(0.40f, avail / hx); hx = halfX(c) / w }
                c.x = if (left) max(hx + 0.004f, min(c.x, gl - margin - hx))
                else min(1f - hx - 0.004f, max(c.x, gr + margin + hx))
            }
            if (idx !in anchored && c.shape == ControlShape.BAR) {
                val hxb = halfX(c) / w
                val fit = min(c.x - EDGE, 1f - EDGE - c.x)
                if (fit > 0.02f && fit < hxb) c.size = 2f * fit
            } else if (idx !in anchored && roleOf(c.shape, c.id) == "system") {
                val hxs = halfX(c) / w; val hys = halfY(c) / h
                c.x = min(1f - EDGE - hxs, max(EDGE + hxs, c.x))
                c.y = min(1f - EDGE - hys, max(EDGE + hys, c.y))
            }
        }

        // ---- bounding-box de-overlap (free controls move; anchored stay put)
        var iter = 0
        while (iter < 60) {
            var moved = false
            for (i in controls.indices) for (j in i + 1 until controls.size) {
                val a = controls[i]; val b = controls[j]
                if (a.shape == ControlShape.BAR && b.shape == ControlShape.BAR) continue
                val ai = i in anchored; val bj = j in anchored
                if (ai && bj) continue
                val ox = (halfX(a) / w + halfX(b) / w) - abs(a.x - b.x)
                val oy = (halfY(a) / h + halfY(b) / h) - abs(a.y - b.y)
                if (ox > 0 && oy > 0) {
                    val push = oy + 3f / h
                    val sgn = if (a.y - b.y >= 0) 1f else -1f
                    when {
                        ai -> b.y = min(0.93f, max(0.07f, b.y - sgn * push))
                        bj -> a.y = min(0.93f, max(0.07f, a.y + sgn * push))
                        else -> {
                            a.y = min(0.93f, max(0.07f, a.y + sgn * push / 2f))
                            b.y = min(0.93f, max(0.07f, b.y - sgn * push / 2f))
                        }
                    }
                    moved = true
                }
            }
            if (!moved) break
            iter++
        }

        return controls.map { it.def.copy(x = it.x, y = it.y, size = it.size) }
    }
}
