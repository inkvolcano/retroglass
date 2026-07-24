# RetroGlass — Touch Controls Layout System (handoff)

Source of truth for the wireframe: `Controls Layout Wireframes.dc.html` (turn 2 / ids 2a–2l).
This documents the segmented zone model and every decision made in this session so it can be
implemented in the Android app. Imported from the Claude Design project 2026-07-23.

---

## 1. Core model — segmented drop-in zones (NOT free positioning)

We explicitly rejected a free-form anchor/offset/clamp editor. Controls drop into a **fixed grid
of named zones**. The user only ever picks a **module** per zone (and a variant of it) — they
never drag things to arbitrary coordinates.

### Zone names (use these exact codes everywhere)

```
SCREEN   the emulator output, 4:3, always present
LT  RT   left-top / right-top       small buttons only
CT       center-top                 Start/Select pills only (+ gear)
CL       center-low                 Start/Select pills (+ gear) + analog stick
LC  RC   left-center / right-center BIG blocks: directional / buttons / stick
```

### Portrait geometry (2a)
- SCREEN is a **full-width slit pinned to the very top** of the screen, **4:3** aspect.
- **No center column** in portrait. Only **LC** and **RC** exist as the two big blocks, each
  **half the screen width**, running **down to the bottom** of the screen.
- **LT / RT** sit as a thin row directly under the screen, above LC/RC.
- **CT** and **CL** are floating center boxes that **overlap the LC/RC seam**:
  - width = **40%** of screen width, centered.
  - overlap = **40% on one side** (i.e. each center box overlaps into LC and RC by a set amount —
    we settled on 40%, after trying 50% and 25%).
  - **CT** floats over the upper area; **CL** is pinned near the bottom.

### Landscape geometry (2b)
Same boxes, re-flowed wide (the layout auto-solves between orientations — one saved layout,
two solves):
- Top row: **LT / CT / RT**.
- Middle row: **LC · SCREEN · RC** (SCREEN center column, controls hug the outer edges).
- Below screen: **CL**.

### Inner anchoring
The big blocks (LC/RC) have **3 inner divisions** — `top`, `mid`, `low` — a module anchors to one
of them. In portrait these divisions run the full height of the block; in landscape they stack
inside the side block.

---

## 2. Modules per zone

### LT / RT — shoulder buttons only (2c)
Variants:
- **single**: one pill (L1 / R1).
- **double (stacked)**: two pills stacked — L1 over L2, R1 over R2.
- **triple (stacked)**: three pills stacked — L1/L2/L3, R1/R2/R3.
- **combined L1/L2**: one divided pill, 2 segments (L2 | L1, and R1 | R2).
- **combined L1/L2/L3**: one divided pill, 3 segments (L3 | L2 | L1, and R1 | R2 | R3).
- **L3 / R3 separate**: stick-click buttons as their own single pills.
- (There is **no** L3/R3 *combined* pill — removed.)

### CT / CL — Start/Select (2d)
- **single**: one pill (START).
- **dual**: two pills (SELECT, START).
- **combined**: one divided pill (SELECT | START), vertically centered, **no divider lines on the
  outer sides**.
- Each of single / dual / combined can carry a **settings gear** button in the center.
- **CL only**: may additionally hold an **analog stick**.
- CT/CL **never** hold L/R shoulder buttons.

### Settings gear
- Use the app's existing glyph: the Unicode cog **`⚙` (U+2699)** — the same character used in
  `MainActivity` and `EmulationActivity` for the settings/top-menu button. Do **not** use a custom
  SVG gear.
- Rendered as a wireframe pill (circular) matching the Start/Select button styling.

### LC / RC — directional / buttons / stick (2e, 2f)
A big block holds **1 or 2 modules**. The module is one of:

**Directional pad — 7 designs (2e):**
1. cross
2. **center button** — cross with a distinct button in the middle; the button **can carry a letter**
   (e.g. `Z` for N64).
3. disc (8-way)
4. octagon (8-way)
5. split arrows (4 separated keys)
6. square plate (8-way)
7. dished round (8-way)

> disc, octagon, square plate, dished round are all **8-way functionally** — we removed the extra
> diagonal arrow glyphs from disc/octagon/dished round; they read as 8-way without them.

**Analog stick — 6 designs (2f):**
concentric · dished cap · ring + nub · square gate · dimpled cap (single centered finger dimple) ·
knurled cap (dished ring + radial knurl ticks).

---

## 3. Console-specific button arrangements (2g–2l)

These are the face-button clusters that go in LC/RC, extrapolated from the app's
`layouts_preview.html`. Notable ones:

- **N64**: A/B (two same-size buttons, spaced apart so they don't touch) placed on a **bottom-left
  → top-right diagonal**, parallel to the C-button diamond edge; four **C buttons** (each labelled
  `C`) arranged as a **diamond**; angle guide is **30°** (also explored 60°). Layout is centered in
  its box.
- **ColecoVision**: 2 buttons vertical + `L`, with a **horizontal alignment line between button 2
  and L**; plus a diagonal GB-style 2-button pair beside it.
- **Vectrex**: 1 + 3 buttons, with small spacing so buttons don't touch.
- **NeoGeo-CD**: 4-button diagonal row.
- Keypad-style consoles (e.g. Intellivision/Coleco keypad): **4 rows**.
- **2l** holds the alignment-guide reference (30°/60° diagonals, horizontal lines) used to place
  these clusters; guides pass through button centers.

---

## 4. Palette (wireframe uses grey; app should use its own theme)
Wireframe module fills: dark `#2a2a30` bodies, `#7fa6c9` stick accents, `#EDEDF2` arrows/glyphs.
These are schematic only — the app applies its real theming.

---

## 5. Decisions / open questions carried forward
- Layout is **preset-driven per console**, re-solved automatically for portrait ↔ landscape.
- Center boxes (CT/CL) overlap the LC/RC seam by **40%** on one side.
- Confirm whether presets are strictly fixed or allow small per-module nudges (still open).
