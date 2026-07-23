# Zone-based controller layout system

The spec the loop is implementing, taken from the Claude Design doc **"Controls Layout
Wireframes"** (project *Retroglass Emulator Menu Design*, file `Controls Layout Wireframes.dc.html`,
turn t2). This file is the stable reference; the design doc itself is not in the repo.

## The idea

Today every control carries an absolute `x, y, size` in `Console.baseControls()`, hand-tuned per
console. The design replaces that with **fixed zones** a control drops into, so one authored
layout re-flows between portrait and landscape instead of being placed twice.

The zone system is a **generator, not a new renderer**: it computes the same
`ControlDef(x, y, size, shape, …)` list the existing `ControllerView` already draws and
hit-tests. Nothing downstream of the coordinates changes. "All existing layouts created and
working" means every console re-expressed as zone assignments that produce a working pad, with
the existing invariants (no unauthored overlap, everything on-screen, non-zero keycodes) still
green.

## Zones

Seven boxes, re-flowed by orientation:

| Zone | Portrait | Landscape | Holds |
|---|---|---|---|
| `SCREEN` | full-width strip, top | centre column, middle | the game window |
| `LT` / `RT` | left/right, under screen | top-left / top-right | shoulder buttons only |
| `CT` | centre, over the seam (top) | centre, top | Start / Select only |
| `CL` | centre, over the seam (low) | centre, below screen | Start / Select **+** an analog stick |
| `LC` / `RC` | the two big half-width blocks, bottom | left / right columns, full height | 1–2 modules each |

`LC`/`RC` are the big blocks — the D-pad, face buttons and sticks live there. `LT/RT/CT/CL` take
small pills only.

## Modules (drop into a zone)

- **Shoulder** (LT/RT): single pill, double stacked (`L1` over `L2`), or a combined divided pill.
- **System pills** (CT/CL): Start / Select — single, dual, or combined divided pill. Never L/R.
- **Directional** — six shapes: `cross`, `disc`, `octagon` (8-way), `split` (four separate
  arrows), `plate` (square), `dished` (round, recessed). Maps onto existing `ControlShape`.
- **Analog stick** — six looks: `concentric`, `dished cap`, `ring + nub`, `square gate`,
  `dimpled cap`, `knurled cap`. (Rendering polish; all are one `ControlType.STICK`.)
- **Face buttons** — resolves to the console's own cluster, same block, different arrangement:
  `1` (Atari fire), `2-row` (NES), `2-diag` (GB/SMS), `3-arc` (Genesis), `4-diamond` (SNES),
  `6` 2×3 (arcade), `N64` (A/B + C-cluster), `ColecoVision`, `Vectrex` (1+3),
  `Neo Geo CD` (4 diagonal), `keypad` (Intellivision/Coleco numeric).

## Center-block rules

- A block holds **one** module, or **two stacked** (e.g. D-pad + analog stick for PS1).
- **Vertical anchor** per block: `top` / `middle` / `low` — reach tuning without free dragging.
- **Horizontal anchor**: `30 / 50 / 70%` across the block. **Mirrored per side** — on a right
  block, 30/70 pushes toward the *outer* edge.
- Module is vertically centred within its slot, ~half the block height.

## Guide axes (how a cluster lays its buttons out)

Every multi-button cluster sits on a guide, and buttons snap to it:

- **horizontal row** (NES 2-row)
- **vertical stack**
- **30° diagonal**, single (GB) or **doubled** (N64 C-cluster + A/B)
- **crossed axes** of a diamond (SNES)

## Implementation plan (loop iterations)

1. `controller/ZoneLayout.kt` — `Zone` enum, `Module` sealed types, `Anchor(vert, horiz)`, and a
   builder that lays zones out on a normalized 0..1 grid and emits `ControlDef`s (portrait first).
2. Convert a pilot console (NES) and prove it renders and passes `ControlLayoutTest`.
3. Convert the rest in batches — cartridge consoles, then disc/twin-stick, then the odd clusters
   (N64, keypads, Vectrex, arcade). Keep the suite green each batch.
4. Landscape: the same zone assignments re-flowed via the landscape grid, replacing the separate
   `LandscapeLayout` hand-math where the zone reflow covers it.

## Superseded work

This reflows layouts hand-tuned earlier today — the ColecoVision keypad, the Atari 5200/8-bit
pads, and the preset de-overlap. Those stay as the reference for *what each console needs*; the
zone system changes *how the coordinates are produced*. The overlap invariant test is the guard
that the reflow didn't reintroduce collisions.
