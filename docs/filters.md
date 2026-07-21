# Video filters

Every filter runs on the **end-phase blit**, after the core has rendered, so it works
identically for every system (2D and 3D). They are built on our LibretroDroid fork's
`ShaderConfig.Custom` multi-pass pipeline and composed by
[`FilterStack`](../app/src/main/java/com/nvanloo/retroglass/video/FilterStack.kt).

## The pipeline contract

The fork binds the same three inputs to **every** pass:

| Uniform | What it is |
| --- | --- |
| `mainTexture` | Always the **original** core frame — never the previous stage's output |
| `previousPass` | The previous pass's output (**unbound on the very first pass**) |
| `sourceSize` | Always the **original** frame size in texels |

A pass's `scale` sizes its framebuffer as `sourceSize * scale` — **absolute, not
cumulative** — and the final pass of the chain renders to the screen.

Three rules follow, and every composable stage honours them via its `FilterStack.Ctx`:

1. Only the **first** stage may read `mainTexture`; later stages read `previousPass`.
   Stages take their sampler from `ctx.inputSampler`.
2. A stage after an upscaler works at an enlarged resolution, so any texel-size maths must
   use `sourceSize * ctx.inScale`, not `sourceSize`.
3. Each pass's absolute `scale` must be `ctx.inScale * (its own relative scale)`, or the
   image shrinks back to native part-way through the chain.

> **Anime4K is first-only.** Its depth-to-space pass adds a residual on top of a bilinear
> upscale of `mainTexture`, so it can only be stage 0 — the chain order enforces this.

## Chain order

Blocks always compose in this order (signal → scale → look), regardless of tick order:

```
dedither → ntsc → anime4k → fsr1 → sabr → lanczos → pixelaa → cas → crt → lcdgrid → bloom → curve → grade
```

Rationale: de-dither and NTSC act on the *signal*, so they run at source resolution before
anything upscales; scalers come next; looks (scanlines, grid, glow) sit on the scaled image;
curvature warps the composed picture; colour grade is last.

## The filters

| Filter | Kind | Notes |
| --- | --- | --- |
| **FSR 1** | upscaler 2× | AMD EASU + RCAS, ported from `ffx_fsr1.h` (MIT). Best for 3D |
| **SABR** | upscaler 2× | Joshua Street's SABR v3.0 (GPLv2+). Best for 2D pixel-art |
| **Anime4K CNN x2 (S)** | upscaler 2× | bloc97's 4-layer CNN (MIT). First-only |
| **Lanczos-2** | resampler 2× | 16-tap windowed sinc; sharper than bilinear, no ringing |
| **Pixel-AA** | resampler 2× | Sharp bilinear + one-output-pixel edge AA via derivatives |
| **CAS** | sharpen | Contrast-adaptive sharpen |
| **CRT-Lottes** | look | Timothy Lottes' CRT (public domain): beam, mask, curvature |
| **CRT scanlines** | look | Source-locked scanlines + aperture grille |
| **LCD dot-matrix** | look | Handheld pixel grid; fades out below ~3 output px/source px |
| **Phosphor glow** | look | Hue-preserving bright-pass ring |
| **CRT tube** | look | Barrel warp + vignette, standalone |
| **NTSC composite** | signal | YIQ chroma band-limiting — makes dithering blend |
| **De-dither** | signal | PS1 ordered-dither reducer; run *before* an upscaler |
| **Colour grade** | colour | Contrast / saturation / gamma |

## Presets, parameters and recipes

* **Per console.** Filter index and chain are stored per system
  (`shader/<system>`, `shader_combo/<system>`), so SNES can keep SABR while PS1 keeps
  de-dither + FSR1.
* **Sliders.** *Filter settings…* tunes sharpen, glow, scanline depth, NTSC bleed, grid
  depth and curvature. Each slider is 0..1 in the UI and mapped onto that filter's useful
  range; **Reset defaults** restores the tuned values.
* **Saved looks.** *Saved looks…* stores the whole state (single filter + chain + every
  slider) as a `key=value;` blob, so new tunables don't invalidate old saves.
* **★ Best for &lt;system&gt;** applies the recipe: PS1 → `dedither + fsr1`; other 3D →
  `fsr1`; handhelds → `pixelaa + lcdgrid`; everything else (2D) → `sabr`.
* **Cost warning.** Each block has a rough GPU weight; applying a heavy chain warns to watch
  the FPS counter.

## Adding a filter

1. Write the fragment as a `FilterStack.Builder` that reads `ctx.inputSampler` and bakes
   `ctx.inScale` (see `LanczosShaders` for the simplest complete example).
2. Register it in `EmulationActivity`: `shaderForIndex`, `filterName`, a `filterCategories`
   entry, and — if it composes — `comboOrder` / `comboLabel` / `comboBuilder`.
3. Add its string, and a `paramSlider` in `showFilterSettings` if it has a knob.

GLSL must be **GLSL ES 3.00** (the pipeline's passthrough vertex is 3.00 and mixing versions
fails to link). No `textureGather` (not core in 3.00) and no `A_HALF` (needs ESSL ≥ 3.10) —
this is why Snapdragon GSR1 was not ported.
