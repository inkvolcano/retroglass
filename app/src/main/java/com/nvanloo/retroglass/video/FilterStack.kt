package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig

/**
 * Composes several composable filter [Stage]s into one [ShaderConfig.Custom] chain, so
 * multiple end-phase filters run together (e.g. FSR1 upscale + CRT scanlines).
 *
 * The fork binds the same three inputs to every pass: `mainTexture` (always the original
 * core frame), `previousPass` (the prior pass's output; unbound on the very first pass),
 * and `sourceSize` (always the original frame size). A pass's `scale` sizes its
 * framebuffer as source*scale (absolute, not cumulative), and the final pass of the whole
 * chain renders to the screen.
 *
 * Two rules follow, which each composable builder honours via its [Ctx]:
 *  - Only the first stage may read `mainTexture`; later stages read `previousPass`.
 *  - A stage placed after an upscaler works at an enlarged resolution, so it must (a) bake
 *    the cumulative input scale ([Ctx.inScale]) for any texel-size math and (b) set each
 *    pass's absolute `scale` to inScale*(its own relative scale) so it doesn't shrink the
 *    image back to native resolution.
 */
object FilterStack {

    /** Where a stage reads from, and the scale of that input relative to the source frame. */
    data class Ctx(val inputSampler: String, val inScale: Float)

    /** A composed filter's passes plus its output scale relative to its input. */
    data class Stage(val passes: List<ShaderConfig.CustomPass>, val outScale: Float)

    /** A composable filter: given its input context, produce its passes and output scale. */
    fun interface Builder {
        fun build(ctx: Ctx): Stage
    }

    /**
     * Concatenate [builders] into a single chain. The first stage reads `mainTexture`;
     * each subsequent stage reads `previousPass` at the accumulated scale.
     */
    fun compose(builders: List<Builder>): ShaderConfig {
        val passes = mutableListOf<ShaderConfig.CustomPass>()
        var sampler = "mainTexture"
        var scale = 1.0f
        for (builder in builders) {
            val stage = builder.build(Ctx(sampler, scale))
            passes += stage.passes
            scale *= stage.outScale
            sampler = "previousPass"
        }
        return ShaderConfig.Custom(passes = passes, linearTexture = true)
    }
}
