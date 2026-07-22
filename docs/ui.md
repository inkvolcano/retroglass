# UI: menus and the 3D pad

How the front end is put together, and why. Companion to `docs/filters.md` (shaders) and
`docs/menu-design-brief.md` (the brief the menu was designed from).

Everything here is hand-built Android Views in Kotlin — no Compose, no XML layouts. That is a
constraint, not a preference: adding Compose to this project is out of scope, so a design has to
be expressible as nested `LinearLayout`/`FrameLayout` with `GradientDrawable` backgrounds.

---

## The menu overlay

`ui/GameMenuView` is one full-screen overlay with a **screen stack**, replacing what used to be
44 separate `AlertDialog`s. `ui/MenuTheme` holds the palette and the row backgrounds.

Both activities own one: `EmulationActivity.gameMenu` and `MainActivity.libraryMenu`.

### Why a stack rather than dialogs

Back walks up a level instead of dropping out to the game. That is the whole reason: a pile of
dialogs cannot express "return to where I came from", so every sub-picker used to dismiss to
the game and make you start again.

```kotlin
gameMenu.open { rootScreen() }            // replaces the stack
gameMenu.push(menuTitle(R.string.x)) {…}  // one level down
gameMenu.pop()                            // back; false at the root
gameMenu.refresh()                        // rebuild the current screen in place
```

`push()` makes the view visible itself — do not assume `open()` ran first, because pickers
reached from the menu hotkey call `showMenu()`, and `showMenu()` declines while the layout
editor is up.

`refresh()` re-runs the current screen's builder, so a screen showing a value it just changed
picks the new one up. Rotation calls it too: the root is a list in portrait and four columns in
landscape, chosen when the screen is built.

### Row types

The point of the pattern library is that 44 surfaces are built from a dozen pieces:

| Row | For |
|---|---|
| `navRow(icon, text, value, chevron)` | drill-down, with the live value inline. `chevron = false` for a row that acts in place |
| `toggleRow` | a switch. Never a list row whose label rewrites itself |
| `selectRow` | single-select; the chosen one is ticked and tinted |
| `valueRow` | two-line: label above, current value below in monospace. Accent when overridden, dim when default |
| `previewRow` | thumbnail + label + tick, for choosing between pictures |
| `slider(text, value, format, onChange)` | 0..1 with a **formatter** — always print the real unit |
| `pipelineRow` | one stage of an ordered chain, with its ordinal |
| `addRow` | a dashed "off" stage waiting to be added |
| `infoRow` | non-interactive status; `ok = true/false` colours it |
| `searchField` | filter field, fires per keystroke |
| `actionTile`, `bigButton` | fire-and-close. `danger`/`tint` for destructive and console-tinted |
| `group`, `spacer`, `pair`, `columnOf`, `columns`, `body` | layout |

`pushSelect` and `pushActions` cover the common "list of options" screens in one call.

### Rules worth keeping

- **Green means focused or live. The console colour means identity.** From the design. Green is
  never decoration — `MenuTheme.ACCENT` on a value means it is the current one.
- **The palette answers to the artwork.** `ACCENT` is `#9AC40C`, sampled from the stroke core of
  `console_line.png`, so the accent and the console drawings are the same green rather than two
  greens that nearly agree. Surfaces are near-black with a faint green cast, replacing a
  blue-grey chrome that read as a second design sharing the screen. The library has no console
  to borrow identity from, so it uses `LIBRARY_TINT` (amber) — deliberately not another green,
  because identity and focus have to stay tellable apart. `MenuTheme` is the single source:
  `CompanionView` used to hold its own copies of these literals and silently kept the old
  palette, so it now aliases them.
- **Sliders print their unit.** A bare 0..1 position next to a label that quotes a percentage
  reads as two numbers for one control. Pass `format`.
- **Screen headings come from the row that opens them, via `menuTitle()`**, which drops the
  trailing ellipsis and the bracketed qualifier. A row says "Core options (system settings)"
  because it must announce what tapping it does; the heading is already the answer.
- **Focus is a functional state.** In glasses mode there is no touchscreen in front of the
  user's eyes, so every interactive row is focusable with an accent ring. `MenuTheme.rowBackground`
  is a `StateListDrawable` carrying pressed / focused / resting — declaratively, so touch gets
  feedback too.

### Still on AlertDialog, deliberately

Button remap (waits on a keypress), add cheat and save/delete look (text entry), scan results,
crash report, delete confirm, and the core-option free-text fallback. These are modal by nature
or need input the row types do not cover.

---

## The 3D pad

The controls are lit by a virtual light fixed in world space: turn the phone and the highlight
rolls around the shapes. Three cooperating pieces:

- `controller/TiltSource` — where the phone is, as a 2D light direction
- `controller/Bevel` — the shared gradient, so everything is lit by one lamp
- `ControllerView` (buttons) and `controller/ScreenBezelView` (the screen's surround)

### Gravity, not the gyroscope

Despite the shorthand. A gyro reports angular *velocity*, so integrating it into a pose drifts
within seconds with no absolute reference to correct against — the shadows would slide away on
their own while the phone sat still. `Sensor.TYPE_GRAVITY` gives tilt directly and never
drifts, with the raw accelerometer as fallback.

Sensor axes are welded to the device while the screen rotates underneath, so the tilt vector is
remapped per display rotation. Without that a landscape layout gets its light thrown 90° off.

### The effect fades at rest, on purpose

Strength is the tilt vector's **own length**, not a normalised direction at constant weight.
Near the resting pose the vector shrinks toward zero, so its angle stops meaning anything and
swings wildly on the smallest movement — normalising held the effect at full strength while it
span, which is what made the dead point snap round. Letting the magnitude carry through means
the whole effect fades out as it approaches the dead point, so there is nothing left to catch
mid-spin.

The consequence is intended: held at rest the controls are flat, and they lift as you tilt away
in whichever direction you tilt. The **screen surround** is the exception — it keeps an ambient
lip, because a physical screen bezel does not vanish when you hold the phone still.

### Rendering notes

- Each control is three passes: contact shadow and side wall underneath, the face, then the lit
  rim on top. A bevel alone says the edge is shaped, not that the thing is raised.
- Shadow layers are grown with a **stroke**, not `Matrix.setScale`. Scaling about a path's
  centre is wrong for anything built of separate pieces — a gapped cross is five subpaths in
  one path, so scaling pushed every arm radially outward and each got its own halo pointing
  away from the middle.
- Blur is faked with concentric silhouettes. A real `BlurMaskFilter` has version-dependent
  hardware-canvas support and would otherwise drag the whole overlay into software rendering,
  on top of a running emulator.
- Rims are stroked at double width **clipped to their own path**, not inset by hand. A cross
  derives arm thickness as a fraction of its radius, so shrinking the radius by half a stroke
  narrows the arm by 0.31 strokes, not 0.5 — and the remainder hangs over the edge as a glow.
- Each arm of a gapped cross gets its **own** gradient. One gradient across the whole cross put
  the bottom arm entirely in the highlight and the top entirely in the shade.

### Tuning

`ControllerView`'s companion object, measured on a 1080×2640 phone:

| Constant | Value | Effect |
|---|---|---|
| `BEZEL_WIDTH` | `0.15` | rim thickness, fraction of radius |
| `BEZEL_WIDTH_CROSS` | `0.0375` | thinner for the D-pad: arms are only ~0.62r wide, and a cross has five of them so it compounds |
| `EXTRUDE_DEPTH` | `0.10` | how far off the glass. **Raise this first if it does not look tall enough** |
| `CONTACT_ALPHA` | `105` | shadow weight |
| `SHADOW_LAYERS` / `SHADOW_SPREAD` | `5` / `1.15` | softness; spread is in extrusion depths |
| `BEZEL_HIGHLIGHT` / `BEZEL_SHADE` | `120` / `130` | rim contrast |

`TiltSource`: `NEUTRAL_FROM_FLAT_DEG = 80` — the resting pose, measured **from flat** (0° is
face-up on a table, 90° is upright). Stated from flat deliberately: the first cut called the
same pose "20° from vertical" and the ambiguity put neutral at the wrong end of the range.
`RANGE_DEG = 45` sets how far you tilt for full strength — lower it to make gentle tilts do
more.

---

## The console shell

Background is painted in `Console.bodyColor` with the screen cut into it (bezel mode
`BEZEL_BODY`, the default), so the front of the phone reads as one piece of plastic.

The letterbox bars are recoloured **in the fork**, not by a view behind the game:
`GLRetroView.setLetterboxColor` → `Video`'s clear colour. The GL surface covers its own view
and clears before drawing, so anything painted underneath is invisible — and in landscape that
view is the whole window. `LibretroDroid` holds the colour across video rebuilds, since the
video object is recreated on surface changes and would otherwise revert to black.

`ScreenBezelView` is an overlay **above** the game for the same reason, drawing only the rim.
It lays out from the core's own aspect ratio (`GLRetroView.aspectRatio()`) rather than the game
view's bounds, because the picture is letterboxed inside that view — in landscape the view is
the full screen and a rim on its edges is off-screen.

---

## Verified on device — 2026-07-22

- **Texture upscaling** — the row appears on N64 with the core's real choice list
  (None / As Is / X2 / X2SAI / HQ2X… / 2xBRZ…6xBRZ), so the option lookup found the right
  key. 2xBRZ set, persisted, and **held 60fps** — the cost I had flagged as unmeasured is
  not visible on this hardware at 2×. 6× on a large-texture game is still untested.
- **Layout picker** — thumbnails render each pad, current one ticked. The reason for the
  picture is immediately obvious: "Z in D-pad" shows Z inside the cross, "Default" above it.
- **Rotation refresh** — rotating with the menu open rebuilds the root from the portrait list
  into the landscape four-column layout.
- **Console identity line** — "Nintendo 64 · running" is back under the logo.
- **SAF-only import** — scan-all is gone from Add ROMs, the folder picker opens and cancels
  cleanly, and the existing library is untouched. Confirmed on the built APK that the only
  permissions requested are legacy READ (maxSdk 32) and VIBRATE.

**Android will not grant the storage root.** The picker answers "choose a different folder to
protect your privacy" if you select the top of internal storage — so users must pick a
subfolder (Download, or a ROMs folder). There is now a note under *Add ROMs* saying so.

## Still pending

- **Rumble** — the VIBRATE permission is granted (checked with `dumpsys package`), but proving
  it needs a game that actually sends rumble events.
- **The Add ROMs hint** — written and building, but the device dropped off wireless ADB before
  I could see it drawn.
- **push() visibility fix** — open the layout editor, then trigger a picker from the menu
  hotkey. The screen should be visible and B should return to the menu, not the game.
- **Core-options search** re-inflates ~83 rows per keystroke on N64. Watch for jank before
  deciding whether it needs fixing.
