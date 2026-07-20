package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * Custom end-phase shader presets, built on our LibretroDroid fork's
 * [ShaderConfig.Custom] multi-pass pipeline. These run on the final blit, after the core
 * has rendered, so they work identically for every emulator (2D and 3D alike).
 *
 * Shader dialect is GLSL ES 3.00 to match the pipeline's default passthrough vertex
 * (mixing ESSL versions in one program fails to link). Each pass sees:
 *   uniform sampler2D mainTexture;   // the original core frame
 *   uniform sampler2D previousPass;  // previous pass output (from pass 1 on)
 *   uniform vec2 sourceSize;         // original frame size in texels
 *   in vec2 coords;                  // 0..1 UV
 */
object RetroShaders {

    private const val HEADER = """#version 300 es
precision highp float;
uniform lowp sampler2D mainTexture;
uniform lowp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;
"""

    /**
     * Contrast-Adaptive Sharpening (FidelityFX-CAS style, simplified). Single pass at
     * output resolution: sharpens edges without ringing, adapting strength to local
     * contrast. Good on 3D systems on top of their internal-resolution upscale.
     */
    fun casSharpen(sharpness: Float = 0.6f): ShaderConfig = ShaderConfig.Custom(
        passes = listOf(
            ShaderConfig.CustomPass(
                fragment = HEADER + """
void main() {
    vec2 px = 1.0 / sourceSize;
    vec3 a = texture(mainTexture, coords + vec2(-px.x, 0.0)).rgb;
    vec3 b = texture(mainTexture, coords + vec2(0.0, -px.y)).rgb;
    vec3 c = texture(mainTexture, coords).rgb;
    vec3 d = texture(mainTexture, coords + vec2(px.x, 0.0)).rgb;
    vec3 e = texture(mainTexture, coords + vec2(0.0, px.y)).rgb;

    vec3 minRGB = min(min(min(a, b), min(d, e)), c);
    vec3 maxRGB = max(max(max(a, b), max(d, e)), c);

    // Adaptive amount: strong on flat detail, restrained on hard edges.
    vec3 rcpM = 1.0 / (1.0 + maxRGB);
    vec3 amp = clamp(min(minRGB, 1.0 - maxRGB) * rcpM, 0.0, 1.0);
    amp = sqrt(amp);

    float peak = -(1.0 / mix(8.0, 5.0, ${"%.4f".format(Locale.US, sharpness)}));
    vec3 w = amp * peak;
    vec3 result = (c + (a + b + d + e) * w) / (1.0 + 4.0 * w);
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
""",
                scale = 1.0f,
                linear = true,
            )
        ),
        linearTexture = true,
    )
}
