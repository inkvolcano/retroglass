package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * Custom end-phase shader presets, built on our LibretroDroid fork's
 * [ShaderConfig.Custom] multi-pass pipeline. These run on the final blit, after the core
 * has rendered, so they work identically for every emulator (2D and 3D alike).
 *
 * Each filter is exposed both as a standalone [ShaderConfig] and as a composable
 * [FilterStack] stage, so it can be layered after an upscaler (e.g. FSR1 + CRT scanlines).
 * A stage reads its input from [FilterStack.Ctx.inputSampler] at `sourceSize*inScale`,
 * which is `mainTexture` at native res when first in the chain, or the (possibly upscaled)
 * `previousPass` when stacked.
 *
 * Shader dialect is GLSL ES 3.00 to match the pipeline's default passthrough vertex.
 */
object RetroShaders {

    private const val HEADER = """#version 300 es
precision highp float;
uniform highp sampler2D mainTexture;
uniform highp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;
"""

    // ------------------------------------------------------------------------ CAS
    // Contrast-Adaptive Sharpening (FidelityFX-CAS style, simplified): sharpens edges
    // without ringing, adapting strength to local contrast. Reads `input` at inSize.

    private fun casFragment(input: String, inScale: Float, sharpness: Float): String = HEADER + """
const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};
void main() {
    vec2 px = 1.0 / (sourceSize * IN_SCALE);
    vec3 a = texture($input, coords + vec2(-px.x, 0.0)).rgb;
    vec3 b = texture($input, coords + vec2(0.0, -px.y)).rgb;
    vec3 c = texture($input, coords).rgb;
    vec3 d = texture($input, coords + vec2(px.x, 0.0)).rgb;
    vec3 e = texture($input, coords + vec2(0.0, px.y)).rgb;

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
"""

    /** CAS as a composable stage (does not change resolution). */
    fun casStage(sharpness: Float = 0.6f): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = casFragment(ctx.inputSampler, ctx.inScale, sharpness),
                    scale = ctx.inScale,
                    linear = true,
                )
            ),
            outScale = 1.0f,
        )
    }

    fun casSharpen(sharpness: Float = 0.6f): ShaderConfig =
        FilterStack.compose(listOf(casStage(sharpness)))

    // ------------------------------------------------------------------------ CRT
    // A composable CRT look: horizontal scanlines locked to the *source* line count (so a
    // 240p game gets 240 scanlines regardless of upscale), plus a gentle RGB aperture
    // grille at the panel's own pixels. Designed to layer on top of a scaler.

    private fun crtFragment(input: String, scanDepth: Float, maskLow: Float): String {
        // Preserve overall brightness after the triad mask darkens two of three subpixels.
        val maskBoost = 3.0f / (1.0f + 2.0f * maskLow)
        return HEADER + """
const float SCAN_DEPTH = ${"%.4f".format(Locale.US, scanDepth)};
const float MASK_LOW = ${"%.4f".format(Locale.US, maskLow)};
const float MASK_BOOST = ${"%.4f".format(Locale.US, maskBoost)};
void main() {
    vec3 c = texture($input, coords).rgb;

    // Scanlines at source-line resolution.
    float pos = fract(coords.y * sourceSize.y);
    float beam = sin(pos * 3.14159265);            // 1 at line centre, 0 at the gap
    float scan = 1.0 - SCAN_DEPTH * (1.0 - beam);
    c *= scan;

    // Aperture grille over device pixels (one lit subpixel per triad).
    vec3 mask = vec3(MASK_LOW);
    int mi = int(mod(gl_FragCoord.x, 3.0));
    if (mi == 0) mask.r = 1.0; else if (mi == 1) mask.g = 1.0; else mask.b = 1.0;
    c *= mask * MASK_BOOST;

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }

    /** CRT scanlines + aperture grille as a composable stage. */
    fun crtStage(scanDepth: Float = 0.28f, maskLow: Float = 0.92f): FilterStack.Builder =
        FilterStack.Builder { ctx ->
            FilterStack.Stage(
                passes = listOf(
                    ShaderConfig.CustomPass(
                        fragment = crtFragment(ctx.inputSampler, scanDepth, maskLow),
                        scale = ctx.inScale,
                        linear = true,
                    )
                ),
                outScale = 1.0f,
            )
        }

    fun crtScanlines(scanDepth: Float = 0.35f, maskLow: Float = 0.88f): ShaderConfig =
        FilterStack.compose(listOf(crtStage(scanDepth, maskLow)))

    // --------------------------------------------------------------------- De-dither
    // A PS1 ordered-dither reducer: where a low-amplitude, high-frequency (dither) pattern
    // is detected, blend toward the 3×3 local average; keep real edges. Meant as a pre-pass
    // *before* an upscaler so it doesn't sharpen the dither cross-hatch into speckle.

    private fun deditherFragment(input: String, inScale: Float, strength: Float): String = HEADER + """
const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};
const float STRENGTH = ${"%.4f".format(Locale.US, strength)};
void main() {
    vec2 px = 1.0 / (sourceSize * IN_SCALE);
    vec3 c = texture($input, coords).rgb;
    vec3 sum = c;
    sum += texture($input, coords + vec2(-px.x, -px.y)).rgb;
    sum += texture($input, coords + vec2( 0.0,  -px.y)).rgb;
    sum += texture($input, coords + vec2( px.x, -px.y)).rgb;
    sum += texture($input, coords + vec2(-px.x,  0.0)).rgb;
    sum += texture($input, coords + vec2( px.x,  0.0)).rgb;
    sum += texture($input, coords + vec2(-px.x,  px.y)).rgb;
    sum += texture($input, coords + vec2( 0.0,   px.y)).rgb;
    sum += texture($input, coords + vec2( px.x,  px.y)).rgb;
    vec3 avg = sum / 9.0;
    // Dither pixels deviate from the local average by a small amount; edges deviate a lot.
    float dev = distance(c, avg);
    float t = (1.0 - smoothstep(0.02, 0.12, dev)) * STRENGTH;
    fragColor = vec4(mix(c, avg, t), 1.0);
}
"""

    /** PS1 de-dither as a composable stage (a pre-pass; does not change resolution). */
    fun deditherStage(strength: Float = 0.85f): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = deditherFragment(ctx.inputSampler, ctx.inScale, strength),
                    scale = ctx.inScale,
                    linear = true,
                )
            ),
            outScale = 1.0f,
        )
    }

    fun dedither(strength: Float = 0.85f): ShaderConfig =
        FilterStack.compose(listOf(deditherStage(strength)))

    // ----------------------------------------------------------------------- Bloom
    // Phosphor glow: a CRT's bright areas bleed light into their surroundings. Takes a
    // bright-pass of a ring of neighbours and adds it back, so highlights (explosions, neon,
    // white text) glow without washing out midtones. Cheap: a 12-tap ring, single pass.

    private fun bloomFragment(
        input: String,
        inScale: Float,
        threshold: Float,
        intensity: Float,
        radius: Float,
    ): String = HEADER + """
const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};
const float THRESH = ${"%.4f".format(Locale.US, threshold)};
const float INTENSITY = ${"%.4f".format(Locale.US, intensity)};
const float RADIUS = ${"%.4f".format(Locale.US, radius)};

// Keep only the part of a colour above the threshold, preserving its hue.
vec3 brightPass(vec3 c) {
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    return c * (max(0.0, l - THRESH) / max(l, 1e-4));
}

void main() {
    vec2 px = RADIUS / (sourceSize * IN_SCALE);
    vec3 c = texture($input, coords).rgb;

    vec3 b = vec3(0.0);
    // Inner ring (8 neighbours) plus an outer cross (4) for a softer falloff.
    b += brightPass(texture($input, coords + vec2(-1.0, -1.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 0.0, -1.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 1.0, -1.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2(-1.0,  0.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 1.0,  0.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2(-1.0,  1.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 0.0,  1.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 1.0,  1.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 0.0, -2.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 0.0,  2.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2(-2.0,  0.0) * px).rgb);
    b += brightPass(texture($input, coords + vec2( 2.0,  0.0) * px).rgb);
    b /= 12.0;

    fragColor = vec4(clamp(c + b * INTENSITY, 0.0, 1.0), 1.0);
}
"""

    /** Phosphor glow as a composable stage (does not change resolution). */
    fun bloomStage(
        threshold: Float = 0.6f,
        intensity: Float = 0.55f,
        radius: Float = 2.0f,
    ): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = bloomFragment(ctx.inputSampler, ctx.inScale, threshold, intensity, radius),
                    scale = ctx.inScale,
                    linear = true,
                )
            ),
            outScale = 1.0f,
        )
    }

    fun bloom(
        threshold: Float = 0.6f,
        intensity: Float = 0.55f,
        radius: Float = 2.0f,
    ): ShaderConfig = FilterStack.compose(listOf(bloomStage(threshold, intensity, radius)))

    // ----------------------------------------------------------------------- Grade
    // A mild colour grade: contrast, saturation and gamma. Cheap, resolution-agnostic;
    // handy as a final layer to make the picture pop.

    private fun gradeFragment(input: String, contrast: Float, saturation: Float, gamma: Float): String =
        HEADER + """
const float CONTRAST = ${"%.4f".format(Locale.US, contrast)};
const float SATURATION = ${"%.4f".format(Locale.US, saturation)};
const float INV_GAMMA = ${"%.4f".format(Locale.US, 1.0f / gamma)};
void main() {
    vec3 c = texture($input, coords).rgb;
    c = (c - 0.5) * CONTRAST + 0.5;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, SATURATION);
    c = pow(max(c, 0.0), vec3(INV_GAMMA));
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

    /** Colour grade as a composable stage (does not change resolution). */
    fun gradeStage(
        contrast: Float = 1.08f,
        saturation: Float = 1.12f,
        gamma: Float = 1.05f,
    ): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = gradeFragment(ctx.inputSampler, contrast, saturation, gamma),
                    scale = ctx.inScale,
                    linear = true,
                )
            ),
            outScale = 1.0f,
        )
    }

    fun colorGrade(
        contrast: Float = 1.08f,
        saturation: Float = 1.12f,
        gamma: Float = 1.05f,
    ): ShaderConfig = FilterStack.compose(listOf(gradeStage(contrast, saturation, gamma)))
}
