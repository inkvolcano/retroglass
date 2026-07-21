package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * NTSC composite-video simulation (colour bleed).
 *
 * A real composite signal carries luma at full bandwidth but chroma at roughly a quarter of
 * it, so colour smears horizontally while edges stay sharp. Artists relied on this: Genesis
 * and PS1 dithering blends into smooth gradients, and cross-hatch patterns read as solid
 * shades. This filter reproduces that by converting to YIQ, band-limiting only the I/Q
 * (chroma) channels over a horizontal window, and keeping luma crisp.
 *
 * Best applied **before** an upscaler (bleeding at source resolution, like the real signal),
 * which is where it sits in the chain order. Cheap: a 7-tap horizontal window.
 */
object NtscShaders {

    private fun fragment(input: String, inScale: Float, bleed: Float): String = """#version 300 es
precision highp float;
uniform highp sampler2D mainTexture;
uniform highp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;

const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};
const float BLEED = ${"%.4f".format(Locale.US, bleed)};

vec3 rgb2yiq(vec3 c) {
    return vec3(
        dot(c, vec3(0.299, 0.587, 0.114)),
        dot(c, vec3(0.5959, -0.2746, -0.3213)),
        dot(c, vec3(0.2115, -0.5227, 0.3112))
    );
}
vec3 yiq2rgb(vec3 v) {
    return vec3(
        v.x + 0.956 * v.y + 0.619 * v.z,
        v.x - 0.272 * v.y - 0.647 * v.z,
        v.x - 1.106 * v.y + 1.703 * v.z
    );
}

void main() {
    vec2 px = 1.0 / (sourceSize * IN_SCALE);
    float luma = rgb2yiq(texture($input, coords).rgb).x;

    // Band-limit chroma over a horizontal window (triangular weights).
    vec2 chroma = vec2(0.0);
    float wsum = 0.0;
    for (int i = -3; i <= 3; i++) {
        float fi = float(i);
        vec3 s = rgb2yiq(texture($input, coords + vec2(fi * px.x * BLEED, 0.0)).rgb);
        float w = 1.0 - abs(fi) / 4.0;
        chroma += s.yz * w;
        wsum += w;
    }
    chroma /= wsum;

    fragColor = vec4(clamp(yiq2rgb(vec3(luma, chroma)), 0.0, 1.0), 1.0);
}
"""

    /** NTSC colour bleed as a composable stage (does not change resolution). */
    fun stage(bleed: Float = 1.0f): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = fragment(ctx.inputSampler, ctx.inScale, bleed),
                    scale = ctx.inScale,
                    linear = true,
                )
            ),
            outScale = 1.0f,
        )
    }

    fun ntsc(bleed: Float = 1.0f): ShaderConfig = FilterStack.compose(listOf(stage(bleed)))
}
