package com.nvanloo.retroglass.video

import com.swordfish.libretrodroid.ShaderConfig
import java.util.Locale

/**
 * CRT-Lottes — Timothy Lottes' well-known CRT emulation (public domain), ported to our
 * LibretroDroid fork's [ShaderConfig.Custom] pipeline. A richer CRT than the simple
 * [RetroShaders] scanline pass: it works in linear light, gives each source pixel a
 * Gaussian electron-beam profile (sharp horizontally, soft vertically → scanlines), lays a
 * shadow-mask aperture grille over the panel pixels, and gently curves the screen.
 *
 * The multi-tap bloom of the original is omitted so the ~11-tap beam holds 60 fps on
 * mobile; the scanline beam, shadow mask and curvature are the defining look and are kept.
 *
 * Written as a composable [FilterStack] stage, but its beam/scanline geometry is locked to
 * the **source** resolution (`sourceSize`), so it belongs first in a chain (on the native
 * frame) — stacking it after an upscaler would resample away the upscaled detail.
 *
 * Timothy Lottes' CRT shader is released to the public domain. See THIRD_PARTY_LICENSES.
 */
object CrtLottesShaders {

    private fun fragment(input: String): String = """#version 300 es
precision highp float;
uniform highp sampler2D mainTexture;
uniform highp sampler2D previousPass;
uniform highp vec2 sourceSize;
in highp vec2 coords;
out vec4 fragColor;

const float hardScan = -8.0;   // scanline hardness (more negative = sharper lines)
const float hardPix  = -3.0;   // horizontal pixel hardness
const float warpX    = 0.031;  // screen curvature
const float warpY    = 0.041;
const float maskDark  = 0.5;    // shadow-mask dark / light subpixel levels
const float maskLight = 1.5;
const float brightBoost = 1.10;
const float shape = 2.0;        // beam profile (2 = Gaussian)

float toLinear1(float c) { return (c <= 0.04045) ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
vec3 toLinear(vec3 c) { return vec3(toLinear1(c.r), toLinear1(c.g), toLinear1(c.b)); }
float toSrgb1(float c) { return (c < 0.0031308) ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
vec3 toSrgb(vec3 c) { return vec3(toSrgb1(c.r), toSrgb1(c.g), toSrgb1(c.b)); }

// Point-fetch the source texel `off` texels from `pos`, in linear light.
vec3 fetch(vec2 pos, vec2 off) {
    pos = (floor(pos * sourceSize + off) + 0.5) / sourceSize;
    return toLinear(brightBoost * texture($input, pos).rgb);
}
// Signed distance (in texels) from pos to the nearest texel centre.
vec2 dist(vec2 pos) { pos = pos * sourceSize; return -((pos - floor(pos)) - vec2(0.5)); }
float gaus(float pos, float scale) { return exp2(scale * pow(abs(pos), shape)); }

vec3 horz3(vec2 pos, float off) {
    vec3 b = fetch(pos, vec2(-1.0, off));
    vec3 c = fetch(pos, vec2( 0.0, off));
    vec3 d = fetch(pos, vec2( 1.0, off));
    float dst = dist(pos).x;
    float wb = gaus(dst - 1.0, hardPix);
    float wc = gaus(dst + 0.0, hardPix);
    float wd = gaus(dst + 1.0, hardPix);
    return (b * wb + c * wc + d * wd) / (wb + wc + wd);
}
vec3 horz5(vec2 pos, float off) {
    vec3 a = fetch(pos, vec2(-2.0, off));
    vec3 b = fetch(pos, vec2(-1.0, off));
    vec3 c = fetch(pos, vec2( 0.0, off));
    vec3 d = fetch(pos, vec2( 1.0, off));
    vec3 e = fetch(pos, vec2( 2.0, off));
    float dst = dist(pos).x;
    float wa = gaus(dst - 2.0, hardPix);
    float wb = gaus(dst - 1.0, hardPix);
    float wc = gaus(dst + 0.0, hardPix);
    float wd = gaus(dst + 1.0, hardPix);
    float we = gaus(dst + 2.0, hardPix);
    return (a * wa + b * wb + c * wc + d * wd + e * we) / (wa + wb + wc + wd + we);
}
float scanW(vec2 pos, float off) { return gaus(dist(pos).y + off, hardScan); }

// Combine the three nearest scanlines, each a horizontal beam.
vec3 tri(vec2 pos) {
    vec3 a = horz3(pos, -1.0);
    vec3 b = horz5(pos,  0.0);
    vec3 c = horz3(pos,  1.0);
    return a * scanW(pos, -1.0) + b * scanW(pos, 0.0) + c * scanW(pos, 1.0);
}

// Aperture-grille shadow mask over the panel's own pixels.
vec3 mask(vec2 pos) {
    vec3 m = vec3(maskDark);
    float x = fract(pos.x * (1.0 / 3.0));
    if (x < 0.333) m.r = maskLight;
    else if (x < 0.666) m.g = maskLight;
    else m.b = maskLight;
    return m;
}

vec2 warp(vec2 pos) {
    pos = pos * 2.0 - 1.0;
    pos *= vec2(1.0 + (pos.y * pos.y) * warpX, 1.0 + (pos.x * pos.x) * warpY);
    return pos * 0.5 + 0.5;
}

void main() {
    vec2 pos = warp(coords);
    vec3 outColor = tri(pos) * mask(gl_FragCoord.xy);
    // Black border where the curvature bends past the frame (CRT tube edge).
    float inside = step(0.0, pos.x) * step(pos.x, 1.0) * step(0.0, pos.y) * step(pos.y, 1.0);
    fragColor = vec4(toSrgb(outColor) * inside, 1.0);
}
"""

    /** CRT-Lottes as a composable stage (belongs first; does not change resolution). */
    fun stage(): FilterStack.Builder = FilterStack.Builder { ctx ->
        FilterStack.Stage(
            passes = listOf(
                ShaderConfig.CustomPass(
                    fragment = fragment(ctx.inputSampler),
                    scale = ctx.inScale,
                    linear = true,
                )
            ),
            outScale = 1.0f,
        )
    }

    fun crtLottes(): ShaderConfig = FilterStack.compose(listOf(stage()))
}
