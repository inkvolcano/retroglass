package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composition contract from `docs/filters.md`, pinned down.
 *
 * These rules are not obvious from the fork's C++ and were the source of real confusion while
 * the chain was being built — particularly that a pass's `scale` is **absolute** (a multiple of
 * the original frame) rather than relative to the pass before it. A stage that reads
 * `ctx.inScale` and forgets to multiply it back in silently renders at the wrong size, which
 * looks like a blurry filter rather than a bug.
 */
class FilterStackTest {

    /** A stage that records what it was handed and scales by [factor]. */
    private class Spy(val factor: Float) : FilterStack.Builder {
        var seen: FilterStack.Ctx? = null
        override fun build(ctx: FilterStack.Ctx): FilterStack.Stage {
            seen = ctx
            return FilterStack.Stage(
                passes = listOf(ShaderConfig.CustomPass(fragment = "", scale = ctx.inScale * factor)),
                outScale = factor,
            )
        }
    }

    private fun passes(config: ShaderConfig): List<ShaderConfig.CustomPass> =
        (config as ShaderConfig.Custom).passes

    @Test
    fun `first stage reads the original frame`() {
        val first = Spy(2f)
        FilterStack.compose(listOf(first))
        assertEquals("mainTexture", first.seen!!.inputSampler)
        assertEquals(1.0f, first.seen!!.inScale, 0f)
    }

    @Test
    fun `later stages read the previous pass, never the original frame`() {
        val a = Spy(2f)
        val b = Spy(1f)
        val c = Spy(1f)
        FilterStack.compose(listOf(a, b, c))
        // Only the first stage may sample mainTexture — the fork rebinds previousPass for the
        // rest, and a later stage reading mainTexture would silently discard everything before
        // it (which is exactly how the Anime4K ordering bug behaved).
        assertEquals("previousPass", b.seen!!.inputSampler)
        assertEquals("previousPass", c.seen!!.inputSampler)
    }

    @Test
    fun `input scale accumulates across stages`() {
        val a = Spy(2f)
        val b = Spy(3f)
        val c = Spy(1f)
        FilterStack.compose(listOf(a, b, c))
        assertEquals(1.0f, a.seen!!.inScale, 0f)
        assertEquals(2.0f, b.seen!!.inScale, 0f)
        assertEquals(6.0f, c.seen!!.inScale, 0f)
    }

    @Test
    fun `pass scale is absolute, not relative to the pass before it`() {
        // Two 2x stages: the second renders at 4x the *source*, not 2x its input.
        val out = passes(FilterStack.compose(listOf(Spy(2f), Spy(2f))))
        assertEquals(2, out.size)
        assertEquals(2.0f, out[0].scale, 0f)
        assertEquals(4.0f, out[1].scale, 0f)
    }

    @Test
    fun `a stage may contribute several passes and they are kept in order`() {
        val two = FilterStack.Builder { ctx ->
            FilterStack.Stage(
                passes = listOf(
                    ShaderConfig.CustomPass(fragment = "first", scale = ctx.inScale),
                    ShaderConfig.CustomPass(fragment = "second", scale = ctx.inScale * 2f),
                ),
                outScale = 2f,
            )
        }
        val out = passes(FilterStack.compose(listOf(two)))
        assertEquals(listOf("first", "second"), out.map { it.fragment })
    }

    @Test
    fun `an empty chain still produces a usable config`() {
        val config = FilterStack.compose(emptyList())
        assertTrue(passes(config).isEmpty())
    }

    @Test
    fun `real scalers agree with the contract when stacked`() {
        // FSR1 then CRT: the CRT stage has to see the upscaled size, or its texel maths is
        // computed against the wrong resolution and the scanlines land at the wrong pitch.
        val config = FilterStack.compose(
            listOf(FsrShaders.stage(outScale = 2f), RetroShaders.crtStage()),
        )
        val out = passes(config)
        assertTrue("FSR contributes EASU + RCAS", out.size >= 3)
        // Every pass after the first upscale renders at the upscaled size.
        assertEquals(2.0f, out.last().scale, 0f)
    }

    @Test
    fun `scaler default factors are all 2x`() {
        // The menu's upscale-factor control assumes every resampler starts from the same
        // baseline; if one drifted, "3x" would mean different things per filter.
        assertEquals(2.0f, FsrShaders.DEFAULT_SCALE, 0f)
        assertEquals(2.0f, SabrShaders.DEFAULT_SCALE, 0f)
        assertEquals(2.0f, LanczosShaders.DEFAULT_SCALE, 0f)
        assertEquals(2.0f, PixelAaShaders.DEFAULT_SCALE, 0f)
    }
}
