package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * SABR v3.0 — Joshua Street's pattern-based 2D pixel-art scaler (a relative of xBR), ported
 * from the RetroArch `sabr-v3.0.slang` to our LibretroDroid fork's [ShaderConfig.Custom]
 * pipeline. It smooths the diagonals of low-res 2D art into clean HD edges while keeping
 * hard pixel boundaries — the crisp counterpart to the smoothing upscalers.
 *
 * The reference is a single fragment stage that reads a 5×5 luma neighbourhood, classifies
 * each corner's dominant edge direction (45°/30°/60°), and blends accordingly. The original
 * relies on **point** sampling of the source; because our framebuffers may be bilinear, the
 * taps here are snapped to source texel centres (`tex`) while the sub-texel blend position
 * (`fp`) still comes from the true output coordinate — reproducing point-sampled behaviour
 * in any chain position.
 *
 * Exposed as a composable [FilterStack] stage (reads `mainTexture` first, or `previousPass`
 * when stacked, at `sourceSize*inScale`) and rendered into an `outScale`× framebuffer.
 *
 * SABR is GPLv2+ (© Joshua Street; portions from Hyllian's 5xBR) — compatible with this
 * app's GPLv3. See THIRD_PARTY_LICENSES.
 */
object SabrShaders {

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

const vec4 Ai  = vec4( 1.0, -1.0, -1.0,  1.0);
const vec4 B45 = vec4( 1.0,  1.0, -1.0, -1.0);
const vec4 C45 = vec4( 1.5,  0.5, -0.5,  0.5);
const vec4 B30 = vec4( 0.5,  2.0, -0.5, -2.0);
const vec4 C30 = vec4( 1.0,  1.0, -0.5,  0.0);
const vec4 B60 = vec4( 2.0,  0.5, -2.0, -0.5);
const vec4 C60 = vec4( 2.0,  0.0, -1.0,  0.5);

const vec4 M45 = vec4(0.4, 0.4, 0.4, 0.4);
const vec4 M30 = vec4(0.2, 0.4, 0.2, 0.4);
const vec4 M60 = vec4(0.4, 0.2, 0.4, 0.2);
const vec4 Mshift = vec4(0.2);

const float coef = 2.0;
const vec4 threshold = vec4(0.32);
const vec3 lum = vec3(0.21, 0.72, 0.07);

bvec4 _and_(bvec4 A, bvec4 B) {
    return bvec4(A.x && B.x, A.y && B.y, A.z && B.z, A.w && B.w);
}
bvec4 _or_(bvec4 A, bvec4 B) {
    return bvec4(A.x || B.x, A.y || B.y, A.z || B.z, A.w || B.w);
}
vec4 lum_to(vec3 v0, vec3 v1, vec3 v2, vec3 v3) {
    return vec4(dot(lum, v0), dot(lum, v1), dot(lum, v2), dot(lum, v3));
}
vec4 lum_df(vec4 A, vec4 B) { return abs(A - B); }
bvec4 lum_eq(vec4 A, vec4 B) { return lessThan(lum_df(A, B), threshold); }
vec4 lum_wd(vec4 a, vec4 b, vec4 c, vec4 d, vec4 e, vec4 f, vec4 g, vec4 h) {
    return lum_df(a, b) + lum_df(a, c) + lum_df(d, e) + lum_df(d, f) + 4.0 * lum_df(g, h);
}
float c_df(vec3 c1, vec3 c2) {
    vec3 df = abs(c1 - c2);
    return df.r + df.g + df.b;
}

void main() {
    vec2 inSize = sourceSize * IN_SCALE;
    float x = 1.0 / inSize.x;
    float y = 1.0 / inSize.y;

    // Snap to the source texel centre so ±texel offsets sample the grid exactly (emulates
    // point filtering); keep the true sub-texel position for the blend weights.
    vec2 tc = (floor(coords * inSize) + 0.5) / inSize;
    vec2 fp = fract(coords * inSize);

    vec4 xyp_1_2_3    = tc.xxxy + vec4(      -x, 0.0,   x, -2.0 * y);
    vec4 xyp_6_7_8    = tc.xxxy + vec4(      -x, 0.0,   x,       -y);
    vec4 xyp_11_12_13 = tc.xxxy + vec4(      -x, 0.0,   x,      0.0);
    vec4 xyp_16_17_18 = tc.xxxy + vec4(      -x, 0.0,   x,        y);
    vec4 xyp_21_22_23 = tc.xxxy + vec4(      -x, 0.0,   x,  2.0 * y);
    vec4 xyp_5_10_15  = tc.xyyy + vec4(-2.0 * x,  -y, 0.0,        y);
    vec4 xyp_9_14_9   = tc.xyyy + vec4( 2.0 * x,  -y, 0.0,        y);

    vec3 P1  = texture($input, xyp_1_2_3.xw   ).rgb;
    vec3 P2  = texture($input, xyp_1_2_3.yw   ).rgb;
    vec3 P3  = texture($input, xyp_1_2_3.zw   ).rgb;
    vec3 P6  = texture($input, xyp_6_7_8.xw   ).rgb;
    vec3 P7  = texture($input, xyp_6_7_8.yw   ).rgb;
    vec3 P8  = texture($input, xyp_6_7_8.zw   ).rgb;
    vec3 P11 = texture($input, xyp_11_12_13.xw).rgb;
    vec3 P12 = texture($input, xyp_11_12_13.yw).rgb;
    vec3 P13 = texture($input, xyp_11_12_13.zw).rgb;
    vec3 P16 = texture($input, xyp_16_17_18.xw).rgb;
    vec3 P17 = texture($input, xyp_16_17_18.yw).rgb;
    vec3 P18 = texture($input, xyp_16_17_18.zw).rgb;
    vec3 P21 = texture($input, xyp_21_22_23.xw).rgb;
    vec3 P22 = texture($input, xyp_21_22_23.yw).rgb;
    vec3 P23 = texture($input, xyp_21_22_23.zw).rgb;
    vec3 P5  = texture($input, xyp_5_10_15.xy ).rgb;
    vec3 P10 = texture($input, xyp_5_10_15.xz ).rgb;
    vec3 P15 = texture($input, xyp_5_10_15.xw ).rgb;
    vec3 P9  = texture($input, xyp_9_14_9.xy  ).rgb;
    vec3 P14 = texture($input, xyp_9_14_9.xz  ).rgb;
    vec3 P19 = texture($input, xyp_9_14_9.xw  ).rgb;

    vec4 p7  = lum_to(P7,  P11, P17, P13);
    vec4 p8  = lum_to(P8,  P6,  P16, P18);
    vec4 p11 = p7.yzwx;
    vec4 p12 = lum_to(P12, P12, P12, P12);
    vec4 p13 = p7.wxyz;
    vec4 p14 = lum_to(P14, P2,  P10, P22);
    vec4 p16 = p8.zwxy;
    vec4 p17 = p7.zwxy;
    vec4 p18 = p8.wxyz;
    vec4 p19 = lum_to(P19, P3,  P5,  P21);
    vec4 p22 = p14.wxyz;
    vec4 p23 = lum_to(P23, P9,  P1,  P15);

    vec4 ma45 = smoothstep(C45 - M45, C45 + M45, Ai * fp.y + B45 * fp.x);
    vec4 ma30 = smoothstep(C30 - M30, C30 + M30, Ai * fp.y + B30 * fp.x);
    vec4 ma60 = smoothstep(C60 - M60, C60 + M60, Ai * fp.y + B60 * fp.x);
    vec4 marn = smoothstep(C45 - M45 + Mshift, C45 + M45 + Mshift, Ai * fp.y + B45 * fp.x);

    vec4 e45   = lum_wd(p12, p8, p16, p18, p22, p14, p17, p13);
    vec4 econt = lum_wd(p17, p11, p23, p13, p7, p19, p12, p18);
    vec4 e30   = lum_df(p13, p16);
    vec4 e60   = lum_df(p8, p17);

    bvec4 r45_1   = _and_(notEqual(p12, p13), notEqual(p12, p17));
    bvec4 r45_2   = _and_(not(lum_eq(p13, p7)), not(lum_eq(p13, p8)));
    bvec4 r45_3   = _and_(not(lum_eq(p17, p11)), not(lum_eq(p17, p16)));
    bvec4 r45_4_1 = _and_(not(lum_eq(p13, p14)), not(lum_eq(p13, p19)));
    bvec4 r45_4_2 = _and_(not(lum_eq(p17, p22)), not(lum_eq(p17, p23)));
    bvec4 r45_4   = _and_(lum_eq(p12, p18), _or_(r45_4_1, r45_4_2));
    bvec4 r45_5   = _or_(lum_eq(p12, p16), lum_eq(p12, p8));
    bvec4 r45     = _and_(r45_1, _or_(_or_(_or_(r45_2, r45_3), r45_4), r45_5));
    bvec4 r30 = _and_(notEqual(p12, p16), notEqual(p11, p16));
    bvec4 r60 = _and_(notEqual(p12, p8), notEqual(p7, p8));

    bvec4 edr45 = _and_(lessThan(e45, econt), r45);
    bvec4 edrrn = lessThanEqual(e45, econt);
    bvec4 edr30 = _and_(lessThanEqual(coef * e30, e60), r30);
    bvec4 edr60 = _and_(lessThanEqual(coef * e60, e30), r60);

    vec4 final45 = vec4(_and_(_and_(not(edr30), not(edr60)), edr45));
    vec4 final30 = vec4(_and_(_and_(edr45, not(edr60)), edr30));
    vec4 final60 = vec4(_and_(_and_(edr45, not(edr30)), edr60));
    vec4 final36 = vec4(_and_(_and_(edr60, edr30), edr45));
    vec4 finalrn = vec4(_and_(not(edr45), edrrn));

    vec4 px = step(lum_df(p12, p17), lum_df(p12, p13));

    vec4 mac = final36 * max(ma30, ma60) + final30 * ma30 + final60 * ma60 + final45 * ma45 + finalrn * marn;

    vec3 res1 = P12;
    res1 = mix(res1, mix(P13, P17, px.x), mac.x);
    res1 = mix(res1, mix(P7, P13, px.y), mac.y);
    res1 = mix(res1, mix(P11, P7, px.z), mac.z);
    res1 = mix(res1, mix(P17, P11, px.w), mac.w);

    vec3 res2 = P12;
    res2 = mix(res2, mix(P17, P11, px.w), mac.w);
    res2 = mix(res2, mix(P11, P7, px.z), mac.z);
    res2 = mix(res2, mix(P7, P13, px.y), mac.y);
    res2 = mix(res2, mix(P13, P17, px.x), mac.x);

    fragColor = vec4(mix(res1, res2, step(c_df(P12, res1), c_df(P12, res2))), 1.0);
}
"""

    /** SABR as a composable stage (2× output). */
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

    fun sabr(outScale: Float = DEFAULT_SCALE): ShaderConfig =
        FilterStack.compose(listOf(stage(outScale)))
}
