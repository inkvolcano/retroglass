package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale
import kotlin.math.pow

/**
 * AMD FidelityFX Super Resolution 1.0 (FSR1) — a two-pass, single-frame spatial upscaler,
 * the 3D-oriented counterpart to [Anime4KShaders]. Runs on our LibretroDroid fork's
 * [ShaderConfig.Custom] end-phase pipeline, so it upscales every emulator identically.
 *
 * Pass 1 — **EASU** (Edge-Adaptive Spatial Upsampling): a locally-adaptive, 12-tap
 * elliptical Lanczos-like filter that detects edge direction/strength and stretches its
 * kernel along the edge, min/max-clamped against the central 2×2 to suppress ringing.
 * Renders into a 2× framebuffer.
 * Pass 2 — **RCAS** (Robust Contrast-Adaptive Sharpening): a 5-tap sharpen tuned to pair
 * with EASU, sampling the EASU output.
 *
 * The algorithm is ported directly from AMD's reference `ffx_fsr1.h` (the fp32 `FsrEasuF`/
 * `FsrRcasF` path — no `A_HALF`, which needs ESSL ≥3.10; no `textureGather`, not core in
 * 3.00). Fast reciprocal/rsqrt bit-hacks are replaced with exact highp `1.0/x` /
 * `inversesqrt`, guarded against divide-by-zero. Dialect is GLSL ES 3.00.
 *
 * Both passes are written as composable [FilterStack] stages: EASU reads the stage input
 * ([FilterStack.Ctx.inputSampler]) at `sourceSize*inScale`, so FSR1 can be the first filter
 * in a chain (reading `mainTexture`) or stacked after another (reading `previousPass`).
 *
 * FidelityFX-FSR is MIT-licensed, (c) 2021 Advanced Micro Devices, Inc. See
 * THIRD_PARTY_LICENSES.
 */
object FsrShaders {

    /** EASU's fixed upscale factor (relative to its own input). */
    private const val OUT_SCALE = 2.0f

    private const val HEADER = """#version 300 es
precision highp float;
uniform highp sampler2D mainTexture;
uniform highp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;
"""

    // ----------------------------------------------------------------- pass 1: EASU
    // Reads `input` at inSize = sourceSize*inScale; the output-pixel centre arrives as
    // `coords`, so the source position is coords*inSize-0.5 regardless of the target size.
    private fun easu(input: String, inScale: Float): String = HEADER + """
const float IN_SCALE = ${"%.5f".format(Locale.US, inScale)};

// Per-tap luma×2 used for edge analysis: L = G + 0.5*(R + B).
float easuLuma(vec3 c) { return c.g + 0.5 * (c.r + c.b); }

// Colour of the input texel at integer offset `off` from the top-left `fp` texel.
vec3 easuTap(vec2 fp, vec2 off, vec2 inSize) {
    return texture($input, (fp + off + 0.5) / inSize).rgb;
}

// Accumulate edge direction/length for one bilinear quadrant. The 5 lumas form a '+':
//   lA=top  lB=left  lC=centre  lD=right  lE=bottom.
void easuSet(inout vec2 dir, inout float len, vec2 pp,
             bool biS, bool biT, bool biU, bool biV,
             float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0;
    if (biS) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (biT) w =        pp.x  * (1.0 - pp.y);
    if (biU) w = (1.0 - pp.x) *        pp.y;
    if (biV) w =        pp.x  *        pp.y;
    // Horizontal.
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    lenX = (lenX > 1e-6) ? (1.0 / lenX) : 0.0;
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;
    // Vertical.
    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    lenY = (lenY > 1e-6) ? (1.0 / lenY) : 0.0;
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

// One filter tap: rotate the offset into edge space, apply anisotropy, weight by the
// two-lobe Lanczos approximation, accumulate.
void filterTap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len2,
               float lob, float clp, vec3 c) {
    vec2 v;
    v.x = (off.x *  dir.x) + (off.y * dir.y);
    v.y = (off.x * -dir.y) + (off.y * dir.x);
    v *= len2;
    float d2 = min(v.x * v.x + v.y * v.y, clp);
    float wB = (2.0 / 5.0) * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = 1.5625 * wB - 0.5625;
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void main() {
    vec2 inSize = sourceSize * IN_SCALE;
    // Source pixel position of this output fragment.
    vec2 pp = coords * inSize - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;

    // 12-tap neighbourhood around the central 2x2 (f g / j k):
    //       b c
    //     e f g h
    //     i j k l
    //       n o
    vec3 tB = easuTap(fp, vec2( 0.0, -1.0), inSize);
    vec3 tC = easuTap(fp, vec2( 1.0, -1.0), inSize);
    vec3 tE = easuTap(fp, vec2(-1.0,  0.0), inSize);
    vec3 tF = easuTap(fp, vec2( 0.0,  0.0), inSize);
    vec3 tG = easuTap(fp, vec2( 1.0,  0.0), inSize);
    vec3 tH = easuTap(fp, vec2( 2.0,  0.0), inSize);
    vec3 tI = easuTap(fp, vec2(-1.0,  1.0), inSize);
    vec3 tJ = easuTap(fp, vec2( 0.0,  1.0), inSize);
    vec3 tK = easuTap(fp, vec2( 1.0,  1.0), inSize);
    vec3 tL = easuTap(fp, vec2( 2.0,  1.0), inSize);
    vec3 tN = easuTap(fp, vec2( 0.0,  2.0), inSize);
    vec3 tO = easuTap(fp, vec2( 1.0,  2.0), inSize);

    float lB = easuLuma(tB); float lC = easuLuma(tC);
    float lE = easuLuma(tE); float lF = easuLuma(tF);
    float lG = easuLuma(tG); float lH = easuLuma(tH);
    float lI = easuLuma(tI); float lJ = easuLuma(tJ);
    float lK = easuLuma(tK); float lL = easuLuma(tL);
    float lN = easuLuma(tN); float lO = easuLuma(tO);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    easuSet(dir, len, pp, true,  false, false, false, lB, lE, lF, lG, lJ); // centre f
    easuSet(dir, len, pp, false, true,  false, false, lC, lF, lG, lH, lK); // centre g
    easuSet(dir, len, pp, false, false, true,  false, lF, lI, lJ, lK, lN); // centre j
    easuSet(dir, len, pp, false, false, false, true,  lG, lJ, lK, lL, lO); // centre k

    // Normalise direction, guarding the near-zero (flat) case.
    vec2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = inversesqrt(max(dirR, 1e-8));
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= dirR;

    // Shape length {0..2} -> {0..1}, square, then build anisotropic kernel size.
    len = len * 0.5;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) / max(max(abs(dir.x), abs(dir.y)), 1e-6);
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = 1.0 / lob;

    // Deringing clamp against the nearest 2x2.
    vec3 min4 = min(min(tF, tG), min(tJ, tK));
    vec3 max4 = max(max(tF, tG), max(tJ, tK));

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    filterTap(aC, aW, vec2( 0.0, -1.0) - pp, dir, len2, lob, clp, tB);
    filterTap(aC, aW, vec2( 1.0, -1.0) - pp, dir, len2, lob, clp, tC);
    filterTap(aC, aW, vec2(-1.0,  1.0) - pp, dir, len2, lob, clp, tI);
    filterTap(aC, aW, vec2( 0.0,  1.0) - pp, dir, len2, lob, clp, tJ);
    filterTap(aC, aW, vec2( 0.0,  0.0) - pp, dir, len2, lob, clp, tF);
    filterTap(aC, aW, vec2(-1.0,  0.0) - pp, dir, len2, lob, clp, tE);
    filterTap(aC, aW, vec2( 1.0,  1.0) - pp, dir, len2, lob, clp, tK);
    filterTap(aC, aW, vec2( 2.0,  1.0) - pp, dir, len2, lob, clp, tL);
    filterTap(aC, aW, vec2( 2.0,  0.0) - pp, dir, len2, lob, clp, tH);
    filterTap(aC, aW, vec2( 1.0,  0.0) - pp, dir, len2, lob, clp, tG);
    filterTap(aC, aW, vec2( 1.0,  2.0) - pp, dir, len2, lob, clp, tO);
    filterTap(aC, aW, vec2( 0.0,  2.0) - pp, dir, len2, lob, clp, tN);

    vec3 col = min(max4, max(min4, aC / aW));
    fragColor = vec4(col, 1.0);
}
"""

    // ----------------------------------------------------------------- pass 2: RCAS
    // Sharpen-only, over the EASU output (which sits at sourceSize*inScale*OUT_SCALE).
    private fun rcas(inScale: Float, sharp: Float): String = HEADER + """
const float EASU_SCALE = ${"%.5f".format(Locale.US, inScale * OUT_SCALE)};
const float SHARP = ${"%.5f".format(Locale.US, sharp)};
const float RCAS_LIMIT = 0.25 - (1.0 / 16.0); // 0.1875

float rcasLuma(vec3 c) { return c.g + 0.5 * (c.r + c.b); }

void main() {
    // One texel of the EASU framebuffer.
    vec2 px = 1.0 / (sourceSize * EASU_SCALE);
    //   b
    // d e f
    //   h
    vec3 e = texture(previousPass, coords).rgb;
    vec3 b = texture(previousPass, coords + vec2(0.0, -px.y)).rgb;
    vec3 d = texture(previousPass, coords + vec2(-px.x, 0.0)).rgb;
    vec3 f = texture(previousPass, coords + vec2( px.x, 0.0)).rgb;
    vec3 h = texture(previousPass, coords + vec2(0.0,  px.y)).rgb;

    float bL = rcasLuma(b);
    float dL = rcasLuma(d);
    float eL = rcasLuma(e);
    float fL = rcasLuma(f);
    float hL = rcasLuma(h);

    // Noise detection -> soften the sharpening in noisy neighbourhoods.
    float nz = 0.25 * (bL + dL + fL + hL) - eL;
    float mxL = max(max(max(bL, dL), max(fL, hL)), eL);
    float mnL = min(min(min(bL, dL), min(fL, hL)), eL);
    nz = clamp(abs(nz) / max(mxL - mnL, 1e-6), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;

    // Per-channel min/max of the 4-neighbour ring set the sharpening lobe.
    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));
    vec3 hitMin = mn4 / max(4.0 * mx4, vec3(1e-6));
    vec3 hitMax = (1.0 - mx4) / min(4.0 * mn4 - 4.0, vec3(-1e-6));
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-RCAS_LIMIT, min(max(max(lobeRGB.r, lobeRGB.g), lobeRGB.b), 0.0)) * SHARP;
    lobe *= nz;

    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 col = (lobe * (b + d + f + h) + e) * rcpL;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

    /** Map the 0..1 UI sharpness to FSR's exp2(-stops) attenuation (0=sharpest). */
    private fun sharpFor(sharpness: Float): Float {
        val stops = (1f - sharpness.coerceIn(0f, 1f)) * 2f
        return 2.0.pow(-stops.toDouble()).toFloat()
    }

    /** FSR1 as a composable [FilterStack] stage (EASU→RCAS, 2× output). */
    fun stage(sharpness: Float = 0.45f): FilterStack.Builder = FilterStack.Builder { ctx ->
        val sharp = sharpFor(sharpness)
        val passScale = ctx.inScale * OUT_SCALE
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = easu(ctx.inputSampler, ctx.inScale),
                    scale = passScale,
                    linear = true,
                    float16 = false,
                ),
                ShaderConfig.CustomPass(
                    fragment = rcas(ctx.inScale, sharp),
                    scale = passScale,
                    linear = true,
                    float16 = false,
                ),
            ),
            outScale = OUT_SCALE,
        )
    }

    /**
     * FSR1 EASU + RCAS as a standalone filter.
     *
     * @param sharpness RCAS strength on the 0..1 UI scale (higher = sharper); default ~0.45
     *   is moderate, which the big scale factor and PS1 dithering favour.
     */
    fun fsr1(sharpness: Float = 0.45f): ShaderConfig =
        FilterStack.compose(listOf(stage(sharpness)))
}
