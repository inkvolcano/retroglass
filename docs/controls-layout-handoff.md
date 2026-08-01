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

## 4b. Amendments from on-device review (2026-07-24)

Agreed while iterating on the real pad; these refine §2d and are implemented:

- **Separate is the default, combined is the fallback.** Start/Select and the gear render as
  *distinct* pills in a row (as the 2d wireframe draws them). The combined divided pill only
  fires when the row genuinely cannot fit the centre zone — the degrade-rather-than-overlap
  rule, applied in that order: separate → row → combined.
- **The gear is a small round pill,** the same height as the pills beside it, never taller or
  shorter.
- **The row is centred on the screen** (the centre zone is screen-centred by definition), and
  when it lives in the top band it sits **on the shoulder bars' own line** — LT · CT · RT read
  as one row, exactly as §1's landscape sketch draws them.
- **New arrangement: stacked.** Start/Select and the gear may also stack *vertically* with
  small spacing, growing downward from the shoulder line so the stack never climbs into the
  screen. The arrangement is user-selectable per console in the Controller designer:
  **Auto · Side by side · Stacked · Combined pill** (Auto separates when it can and combines
  when it must).

## 5. Decisions / open questions carried forward
- Layout is **preset-driven per console**, re-solved automatically for portrait ↔ landscape.
- Center boxes (CT/CL) overlap the LC/RC seam by **40%** on one side.
- Confirm whether presets are strictly fixed or allow small per-module nudges (still open).

---

## 6. The layout DESIGNER (wireframe turn 4) — interaction spec

Replaces the preset-only picker and the zone picker. Field-first editing, one orientation at a time.

### 6.1 Structure
- **LC and RC are single 6-slot columns** (slots 1–6, top to bottom) — the old LT/RT boxes are
  merged in. **Shoulder modules only fit slots 1–2.** CT and CL remain overlay zones on the seam.
- Big modules occupy 1 or 2 slots: face layouts of ≤3 buttons = 1 slot; 4+ button layouts, d-pads
  and analog sticks = 2 slots.
- **Portrait**: screen slit pinned top (4:3); CT aligned with the slot-1 row; CL pinned bottom.
- **Landscape**: LC/RC full height at the outer edges; CT top-centre; **CL spans the full bottom
  band between LC and RC**. The screen box fills the area between LC/RC/CT/CL, with the true 4:3
  game area drawn as a centred inner rect.
- If **CT or CL is empty** in landscape the screen box expands over that row. A **□ filler module**
  occupies the zone to force the screen small — invisible in the actual game, a ghost outline in
  the editor.

### 6.2 Interaction flow
1. **Tap a field** (an LC/RC slot, or CT/CL) → an **action menu**: empty field → "Add module";
   occupied → "Change module" / "Move module" / "Remove module", with a "contains: <name>" subtitle.
2. **Add/Change** → contextual module list, filtered to what fits that field, each row showing the
   resulting pad. Row states: current = highlighted; **"replaces <name>"** = would overwrite;
   **blocked, with the reason** = not pickable.
3. **Move** → "moving <name> — tap where it should go"; all placement rules enforced at the
   destination; cancel available.
4. **Alignment** is visual: inner / centre / outer within the block, and the module shifts live.
5. **Rotate** switches Portrait ↔ Landscape.

### 6.3 Portrait and landscape are SEPARATE layouts
Each orientation stores its own module map, alignment map, split, shadows and scale. Editing one
never touches the other.

### 6.4 Rules engine
- Shoulder modules: slots 1–2 only.
- **If CT holds a multi-button module** (SELECT·START dual, or the combined pill), **combined
  shoulder pills are blocked in slot 1** — they share the top row. Exempt: single START, ⚙ only,
  filler, and START + ⚙ below. Enforced in both directions, since either can be picked first.
- Placing over occupied slots replaces the overlapped modules, after the warning.

### 6.5 In-screen settings panel (per orientation)
- **width** — LC/RC split: 40/60 · 50/50 (default) · 60/40 · 40/20/40 (empty middle band).
- **shadow** — separate toggles for screen, buttons, d-pad, stick.
- **scale** — global module scale: small (0.8×) · normal (1×) · large (1.25×).

### 6.6 Landscape shoulder widening
When CT is occupied, slot 1 of LC/RC widens inward up to the CT edge and a shoulder pill placed
there stretches across it, vertically aligned with the CT row — LT · CT · RT read as one row. When
CT is empty the picture covers that row, so the widening is off and nothing overlaps the screen.

Implemented in `PadRenderer.widenedBand`, which the canvas also asks so that the field it draws and
hit-tests is the width the module actually occupies. A stretched bar takes its size from the band
rather than from the console's authored width; everywhere else the authored width stands.

### 6.7 Module catalogue

A module is an **arrangement, not a set of buttons**. The buttons always come from the console —
ids, labels, keycodes and colours are fixed by the hardware being emulated — so picking a different
arrangement never changes what a button does. Remap, turbo and hit-testing are identical whichever
one is chosen. That split is what keeps the catalogue short across 35 very different pads: a
twelve-key Intellivision keypad and a four-key NeoGeo row are the same kind of thing, a cluster of
N buttons, differing only in N and in which arrangement reads best.

Because of that, the list is **filtered by button count**. A three-button cluster is never offered
the diamond; a console with no analog stick is never offered a stick.

**Blocks — LC/RC, any slot (2 slots unless noted)**

| Module | Buttons | Notes |
| --- | --- | --- |
| Console default | any | The shipped hand-tuned cluster, moved as a rigid group. The default for every console. |
| Row | any (1 slot) | One horizontal guide. |
| Rising diagonal | any (1 slot) | Low-left → high-right, the handheld placement. |
| Column | any | One vertical guide. |
| Arc | any (1 slot) | Shallow bow, middle button proud of the line through the ends (Genesis). |
| Two rows | any | Splits across two rows. |
| 2×2 square | 4 | Y/X top, B/A bottom. |
| Diamond | 4 | SNES: X top, A right, B bottom, Y left. |
| N64 A/B + C diamond | 6 | Four C keys as a diamond, A and B on a rising diagonal clear of it. |
| Keypad (3 per row) | any | Row-major grid, three columns (Intellivision, ColecoVision, Atari 5200). |
| One + row of three | 4 | Vectrex. |
| Two stacked + one | 3 (1 slot) | ColecoVision's pair with the third on the guide between them. |

**Directional variants** (2 slots, all functionally 8-way): ✛ cross · ⬤ disc · ⯃ octagon ·
✧ split arrows · ▣ square plate · ◎ dished round. A console whose directional carries a centre
button (N64's Z) keeps it concentric in every variant, because the co-centred hit test is what lets
one thumb hold a direction and press it at the same time.

**Stick variants** (2 slots): concentric · dished cap · ring + nub · square gate · dimpled cap ·
knurled cap.

**Shoulders — LC/RC slots 1–2 only**: single L1/R1 · double stack · triple stack · combined L1|L2 ·
combined L1|L2|L3 · L3/R3 separate. Each is offered only when the console has enough shoulder
buttons to fill it.

**System — CT/CL**: single START · START + ⚙ below · SELECT·START dual · combined SEL|⚙|START
(gear centred, no outer divider lines) · ⚙ only · □ filler · ◉ stick (CL only).

The gear is never allowed to go missing: if no placed system module carries one, the renderer adds
it to the top centre, tagged into the system pill group so it spreads or fuses with START/SELECT
rather than landing on top of them.

### 6.8 Persistence model
Per console: `{ portrait: Layout, landscape: Layout }` where
`Layout = { CT, CL, LC:{slot:moduleId}, RC:{slot:moduleId}, align:{zone+slot:'l'|'c'|'r'},
split, shadowScreen, shadowButtons, shadowDpad, shadowStick, scale }`.

Nothing is stored until the user edits: an unsaved console derives its design from its own authored
layout each time. Writing a derived design eagerly would freeze today's derivation into every
user's preferences and make later improvements to it invisible to anyone who had merely opened the
screen.

### 6.9 Where it lives

`ControllerDesigner` takes its host as parameters, so both the in-game menu and the library open
the same editor. A pad belongs to a console rather than to a game — the design is derived from the
console's shipped controls and saved against the console — so requiring a game to be running before
you could lay one out was an accident of where the code first sat, and impossible for a console you
own no games for yet. The library entry is scoped to whichever console the carousel is showing.

### 6.10 Implementation status (app)

Portrait is fully slot-driven: `PadDesign`/`PadLayout` (model), `PadModules` (catalogue),
`PadParts` (decomposition), `PadDeriver` (starting design per console), `PadRenderer` (geometry),
`DesignerView` (preview). The zone picker and the preset thumbnail picker are removed, as is the
drag editor's saved per-control offset map — reading it back would pull a freshly designed pad
toward wherever the old free editor last left each button.

**Landscape now runs through the slot renderer too**, and `LandscapeLayout` is deleted. What kept
them apart was aspect correction: `ControlDef.size` is a fraction of the pad's shorter edge while x
and y are fractions of its width and height, so a cluster authored to be square comes out stretched
the moment the pad is not the shape it was tuned for. Modules now express spacing as one physical
step and convert it per axis (`Box.mx`/`Box.my`), which makes portrait correct on any phone rather
than only on the one the numbers were tuned against, and makes landscape possible at all.

The ⟳ rotate pill sits in the settings panel's `layout` row and switches which of the two stored
layouts the canvas is editing. In landscape the canvas draws the controls *over* the picture, and
the picture is whatever the columns and occupied overlay zones leave — an empty CT or CL gives its
band back, which is what the filler module exists to prevent.

Alignment is applied by measuring what a module actually emitted and sliding the group, rather than
by asking each arrangement to declare its own footprint — a declaration can drift out of step with
the drawing, a measurement cannot.

### 6.11 Known gaps (acknowledged, not yet designed)
CL collision rule for bottom slots · bottom-slot widening toward CL · turbo/fast-forward/state
quick buttons · per-layout opacity · left-handed mirror button · per-console screen aspect
(GBA 3:2, PSP 16:9) · named save/reset UI.

---

## 7. Changes — 2026-07-26 (implemented)

Imported from the design project and built. Every setting is stored **per orientation** except the
panel's collapse state, which is editor chrome.

### 7.1 Configurable zone count (5–7)
`zones` per orientation, default 6. Row 1 keeps a fixed height (top 2%, height 15%) whatever the
count — it is the shoulder row and has to stay aligned with CT, and a shoulder that grew or shrank
with the row count would break the LT · CT · RT line the whole top of the pad is built around. The
remaining rows divide what is left evenly (1.4% gaps, down to 99%). The six-row case reproduces the
old hard-coded table exactly, so nothing moves for a layout that never touches the setting.

Shrinking the count prunes modules whose footprint would run past the new bottom row. Leaving them
in the map would make them invisible but still occupy their rows, blocking placements for a reason
nothing on screen explains.

### 7.2 External-screen mode + reflow
`noScr` puts the video on the glasses: the screen box becomes a faint dashed "⇱ EXTERNAL SCREEN"
outline that never takes a tap, and the landscape 4:3 guide hides. The band stays drawn because it
is still reserved — the layout has to know what it is leaving room for.

`reflow` (only meaningful with `noScr`) hands that band back to the pad. Portrait: the columns start
at the top of the phone instead of under the slit. Landscape: the columns meet in the middle, the
split dividing the whole width, and the §6.6 shoulder widening switches off — the columns already
meet, so growing into a band that is not there would drive the two shoulders through each other.

Scale gains **XXL (1.6×)** and **XXXL (2×)**, offered only in external mode and falling back to
normal otherwise, so a saved external layout opened on-device cannot bury the game under the pad.

### 7.3 Settings panel
Near-opaque, compact, with each category titled above its buttons: **layout** (⟳ orientation · ext)
· **zones** (5 · 6 · 7 · ⇅ reflow) · **width** · **shadow** (screen · buttons · d-pad · stick) ·
**scale**. A `⌄` handle on the panel's bottom-right corner collapses it to a `⌃ settings` pill, for
when it covers the part of the layout you are trying to judge.

The shadow toggles now drive the real pad: `ControllerView` skips the contact-shadow pass per module
family, so a pad can lift its buttons off the glass while leaving a flat d-pad.

### 7.5 Verified on device

Row counts 5/6/7 (row 1 keeps its height, the rest redivide), external-screen mode, reflow, and
the mode picker were all exercised on a Galaxy Z Flip. One bug came out of it: reflowed portrait
still drew the screen box over the band the columns had just taken, putting its label straight
through the LC/RC/CT labels. Reflowed layouts now draw no screen box at all, which is the honest
rendering — the band belongs to the pad, and the picture is on the glasses.

### 7.4 Persistence
`zones`, `noScr` and `reflow` join the serialised layout; `scale` widens from a single char to an id
so it can carry `xxl`/`xxxl`. Older saved strings parse unchanged — every new key falls back to its
default when absent.
