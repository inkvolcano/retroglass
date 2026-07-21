package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * Pixel-AA — "sharp bilinear with anti-aliasing". Plain nearest-neighbour gives crisp pixels
 * but uneven pixel sizes (and shimmer) at non-integer scales; plain bilinear is uniformly
 * blurry. This keeps every source pixel flat and square, and antialiases only the *edge*
 * between pixels, spread over exactly one output pixel — so the picture stays crisp at any
 * scale without jaggies or wobble.
 *
 * It works by compressing the sub-texel offset by the current scale factor (derived from the
 * screen-space derivative, so no output-size uniform is needed) and then letting hardware
 * bilinear do the one-pixel blend. Cheap: a single tap.
 *
 * Composable [FilterStack] stage; looks best as the final pass (rendering at panel
 * resolution), which is what a single-filter selection does.
 */
object PixelAaShaders {

    /** Default upscale factor; callers may raise it to better match the panel. */
    const val DEFAULT_SCALE = 2.0f

    private fun fragment(input: String, inScale: Float): String = """#version 300 es
precision highp float;
uniform highp sampler2D mainTexture;
uniform highp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;

const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};

void main() {
    vec2 inSize = sourceSize * IN_SCALE;
    vec2 sp = coords * inSize;          // position in source texels
    vec2 fp = floor(sp) + 0.5;          // texel centre
    vec2 d = sp - fp;                   // -0.5..0.5 within the texel

    // Source texels covered by one output pixel; d / w == d * scale.
    vec2 w = max(fwidth(sp), vec2(1e-5));

    // Snap to the texel centre, letting the transition span just one output pixel — the
    // hardware bilinear tap then produces a clean antialiased edge.
    vec2 uv = (fp + clamp(d / w, -0.5, 0.5)) / inSize;
    fragColor = vec4(texture($input, uv).rgb, 1.0);
}
"""

    /** Pixel-AA as a composable stage. */
    fun stage(outScale: Float = DEFAULT_SCALE): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = fragment(ctx.inputSampler, ctx.inScale),
                    scale = ctx.inScale * outScale,
                    linear = true,
                )
            ),
            outScale = outScale,
        )
    }

    fun pixelAa(outScale: Float = DEFAULT_SCALE): ShaderConfig =
        FilterStack.compose(listOf(stage(outScale)))
}
