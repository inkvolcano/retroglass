package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * Lanczos-2 resampling scaler — a windowed-sinc reconstruction that is sharper and less
 * blurry than hardware bilinear, without the ringing of higher-order Lanczos. A good
 * general-purpose upscaler for both 2D and 3D content, and a clean composable [FilterStack]
 * stage that can sit before a look pass (e.g. Lanczos → CRT scanlines).
 *
 * Single pass, 4×4 tap window (a = 2): each output pixel is the sinc-weighted average of its
 * 16 nearest source texels. Reads its input at `sourceSize*inScale`; renders into a 2×
 * framebuffer.
 */
object LanczosShaders {

    private const val OUT_SCALE = 2.0f

    private fun fragment(input: String, inScale: Float): String = """#version 300 es
precision highp float;
uniform highp sampler2D mainTexture;
uniform highp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;

const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};

// Lanczos-2 weight: L(x) = sinc(x)*sinc(x/2) = 2*sin(pi x)*sin(pi x/2)/(pi x)^2.
float lanczos(float x) {
    x = abs(x);
    if (x < 1e-4) return 1.0;
    if (x >= 2.0) return 0.0;
    float px = 3.14159265 * x;
    return 2.0 * sin(px) * sin(px * 0.5) / (px * px);
}

void main() {
    vec2 inSize = sourceSize * IN_SCALE;
    vec2 sp = coords * inSize - 0.5;
    vec2 fp = floor(sp);
    vec2 f = sp - fp;

    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int dy = -1; dy <= 2; dy++) {
        float wy = lanczos(f.y - float(dy));
        for (int dx = -1; dx <= 2; dx++) {
            float wgt = lanczos(f.x - float(dx)) * wy;
            vec3 c = texture($input, (fp + vec2(float(dx), float(dy)) + 0.5) / inSize).rgb;
            acc += c * wgt;
            wsum += wgt;
        }
    }
    fragColor = vec4(clamp(acc / wsum, 0.0, 1.0), 1.0);
}
"""

    /** Lanczos-2 as a composable stage (2× output). */
    fun stage(): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = fragment(ctx.inputSampler, ctx.inScale),
                    scale = ctx.inScale * OUT_SCALE,
                    linear = true,
                )
            ),
            outScale = OUT_SCALE,
        )
    }

    fun lanczos(): ShaderConfig = FilterStack.compose(listOf(stage()))
}
