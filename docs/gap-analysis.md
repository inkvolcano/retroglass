# Gap analysis — whole app, 2026-07-22

A structured audit of RetroGlass across seven tracks: emulation lifecycle, ROM import, external
display, input, UI, release readiness, and test/docs coverage. Findings are deduplicated and
ranked by what a user or the project actually loses.

Confidence is stated per finding. **Verified** means the logic was read and traced (in several
cases re-derived independently). **Inferred** means the code shape implies it but it was not
confirmed — usually because it needs a device.

---

## Closed on 2026-07-22

Worked through in four batches, after the audit above. Each entry keeps its original wording
below; this is the index.

| Batch | Closed | Commit |
|---|---|---|
| Saves | C1 collision, C2 atomic writes, H4 + H8 silent failures | `9d3dc0a` |
| Input/UI | H1 stuck input, H2 preset overlap, H13 back in editor, H14 focus, M21 target, M27 units | `91bacb4` |
| Import | C4 dead classifier, C5 `.exe`, H5 free space, H6 overwrite, M7 inconsistency | `9b60da3` |
| Release | C6 signing, H16 committed binaries, H18 traceability, H19 CI | this commit |
| Earlier | H17 artwork marks, and `GameCovers.load` from pattern 5 | `5a4a607`, `6e6a567` |

Three things came out differently than the audit assumed, and are worth keeping:

- **H2 had two root causes, not one.** Both were "spread past the edge, then clamp back onto
  what you spread away from" — `spreadToEdges` clamping each control separately (which pins
  distinct columns to the *same* x, unrecoverable by shrinking), and `scaled` not checking the
  edge at all. The size-trim pass alone would not have fixed Saturn.
- **The obvious test for H2 was wrong.** Written as "no preset overlaps", it failed on the SNES
  diamond, which overlaps on the x-axis by design. y cannot be compared against size at the
  model layer — one is a fraction of height, the other of width. The honest invariant is *a
  preset introduces no overlap the authored layout does not already have*.
- **C1 is fixed without migrating.** Old saves are still read when no new-key file exists, so
  nothing that already works stops working. Deleting the two `legacy*` helpers makes it a clean
  break whenever that is wanted.

Still open and unchanged: **C3** (the glasses GL trap) needs a device, and remains the single
highest-value thing to test. H3, H7, H9-H12, H15, H19's core-build reproducibility, H20, and
the Medium/Low list below are untouched.

---

## History rewrite — 2026-07-23

`git filter-repo` purged the trademarked console photos (`consoles.png`, `consoles2.png`, and
their `drawable-nodpi/console_photos*.png` copies) and the six committed GPL core binaries
(`libretrodroid/app/src/main/jniLibs/`) from all 108 commits, and `main` was force-pushed.
`.git` went 28 MB → 12 MB; every purged path now reports 0 reachable objects. The originals are
backed up outside the repo (`Desktop/retroglass-purged-backup/`, including a full pre-rewrite
bundle). This closes the "still in history" caveat on H17 and H16.

Two things worth knowing:

- **Every commit SHA changed.** The references in this file were remapped from filter-repo's
  commit-map; SHAs quoted inside older *commit messages* still point at nothing.
- **A force-push does not guarantee erasure from GitHub.** Orphaned commits can stay reachable
  by direct SHA until GitHub garbage-collects, which is not on a published schedule. If the
  photos genuinely must be unreachable, that needs GitHub Support (or a fresh repo), not just
  this rewrite.

---

## The five patterns underneath

Most individual findings are instances of five root causes. Fixing the pattern is worth more
than fixing the instances.

**1. Silent failure is the house style.** A save state that fails to load still toasts "State
loaded". An unrecognised archive imports "successfully" into Arcade. A half-present BIOS shows a
green tick. A write that fails while backgrounding says nothing at all. Today's ColecoVision
keypad bug is the archetype: keys 1 and 2 were swapped, both were valid presses, so games simply
started in the wrong mode and nothing anywhere could report it. **~15 findings below are this
one pattern.**

**2. The tests pin data, but the bugs are in behaviour.** 46 tests across 5 files, every one a
pure function; zero instrumentation tests. The suite's own stated philosophy is that layout and
table mistakes "fail quietly" — which is right, and yet the three worst defects found today
(save-file collision, the dead classifier, preset overlap) are all exactly that shape and none
were caught. Two reasons: 11 of the tests pin code that never runs in production, and nothing
tests the preset transforms or any persistence path.

**3. The headline feature is the least verified thing in the app.** External-display mode is
~200 lines inside a 2923-line activity, has no recorded on-device verification of any hotplug
scenario, and rests on a GL-lifecycle assumption that appears to contradict how `GLSurfaceView`
behaves on reparent.

**4. Nothing about a build is reproducible or traceable.** Cores are fetched from `latest`
nightly with no pin, release tags exist but are sparse (`v0.4.0`, `v0.5.0` only), `versionCode`
has never moved off 1, release is
signed with the debug key, and the 16 KB-aligned core set exists only as a state once produced
by hand. It is not currently possible to say what any given APK contained.

**5. Five subsystems are dead code that read as features.** `classifyFile`, `addReferences`,
`LayoutStore.localMultiplayer()`, `CompanionView.clearInput()`, and `GameCovers.load()` — all
fully written, documented, and never called. The covers one was the worst of them, because the
user does visible work for nothing: "Set cover" picks an image, stores it, and the library then
shows initials forever. **Fixed 2026-07-22** — list rows now show the cover, falling back to
cartridge/disc artwork.

---

## Critical

**C1. Save states and SRAM collide between different games.**
`stateFile()`/`sramFile()` key on `romFile.name` — the basename — into one flat directory, while
`romsDir` *is* per-console. `roms/nes/game.zip` and `roms/snes/game.zip` therefore both write
`saves/game.zip.srm`.
`EmulationActivity.kt:2753-2758`, `RomLibrary.kt:49-58`.
The correct key already exists in the same class — `gameKey = romFile.absolutePath`
(`EmulationActivity.kt:98`) — and is used for cheats but not for saves.
*Failure:* two same-named ROMs silently destroy each other's progress. **Verified.**

**C2. No save is written atomically.**
`writeBytes` truncates the destination before writing, with no temp-file-and-rename.
`EmulationActivity.kt:2776, 2816, 2823`; same shape in `SaveBackup.kt:55`.
*Failure:* a process kill or full disk during the `onPause` save leaves an empty file — and the
previous good save is already gone. Compounds with C1 and H8. **Verified.**

**C3. The GL objects may not be rebuilt when the game moves between screens.**
`initializeCore()` guards on `isGameLoaded`, which is set once and never reset, and
`LibretroDroid.onSurfaceCreated()` — which nulls and rebuilds the native `Video`, i.e. every
shader, texture and FBO — sits *inside* that guard.
`GLRetroView.kt:344-361` (only three references to `isGameLoaded` exist); `libretrodroid.cpp:189-210`.
Reparenting moves the view between two different Windows, which detaches the `GLSurfaceView` and
exits its `GLThread`.
*Failure if real:* plug or unplug the glasses mid-game and the core keeps running at full speed
against GL handles from a destroyed context — a black or garbled picture on the display it just
moved to.
**Mechanism verified; the consequence is inferred.** `README.md:129` asserts reparenting "only
rebuilds the EGL surface", and glasses mode is reported to work, so this is either a latent trap
or a live bug not yet triggered. **This is the single highest-value thing to test on device.**

**C4. The archive classifier that all the tests cover never runs.**
`classifyFile` has zero callers, so the whole subtree below it — `classifyZip`, `classify7z`,
`classifyArchiveEntries` — is production-dead. The live path, `importZip`, decides "console ROM
or arcade set" with one line, and on *any* failure to find a known extension (corrupt zip,
nested zip, docs bundle, unrelated file) copies the raw file into Arcade and reports success.
`RomLibrary.kt:493-511`, `:860`.
*Failure:* any junk `.zip` lands in the library as a successful import. The 11 tests in
`ArchiveClassificationTest` provide no protection. **Verified.**

**C5. Every stray `.exe` imports as a PlayStation game — and the app tells you to point it at
Downloads.**
PSX claims `exe` for homebrew (`Console.kt:49`), `isImportable` checks extension only
(`RomLibrary.kt:148`), and the folder-import hint reads "Pick the folder your games are in —
Download, or a ROMs folder" (`strings.xml:8`). The dead `classifyFile` explicitly guards against
exactly this and is unreachable.
*Failure:* following the app's own instructions fills the PS1 library with Windows installers.
**Verified.**

**C6. Release builds are signed with the debug keystore.**
`app/build.gradle.kts:28-32` — `release { signingConfig = signingConfigs.getByName("debug") }`,
and no release signing config exists anywhere.
*Failure:* the first genuine Play upload is rejected. **Verified.**

---

## High

**H1. A gamepad that disconnects mid-press leaves input stuck down forever.**
`onInputDeviceRemoved` only re-arranges the touch layout; nothing sends key-up or a neutral
motion event for that device's port. `EmulationActivity.kt:159-181`. (`CompanionView.clearInput()`
exists for this and is never called.)
*Failure:* a pad's battery dies while running — the character keeps walking until force-quit.
**Verified.**

**H2. Two layout presets stack the 12-key keypads on top of each other.**
The portrait preset transforms have no de-overlap pass (landscape has one). Re-derived
independently against the real transform and clamp code, for the Coleco/Intellivision 3×4 grid:

| Preset | Centre gap | Needed | Result |
|---|---|---|---|
| Large buttons `scaled(base, 1.28)` | 0.1009 | 0.1472 | overlap 0.046 |
| Full-screen `scaled(spreadToEdges, 1.35)` | 0.0189 | 0.1553 | **overlap 0.136 — near-total** |
| Compact `scaled(base, 0.82)` | 0.1173 | 0.0943 | ok |

`Console.kt:501-516, 570-579`; `ControllerView.kt:348-355`.
*Failure:* picking "Large buttons" or "Full-screen" on ColecoVision or Intellivision makes half
the keypad touch-ambiguous — the keys those games need to start. Directly undercuts the keypad
work committed in `4aa3c4a`. **Verified by recomputation.**

**H3. Core-mutating calls race `retro_run()` with no lock and no thread hop.**
`setControllerType`, `updateVariables`, `getVariables`, `getControllers` call straight into
native with neither `runOnEmulationThread` nor `coreLock`, unlike every other core-mutating
entry point. `GLRetroView.kt:215-227`; `libretrodroid.cpp:131-145`. `Environment::variables` is a
plain map read inside `retro_run()` and written from the UI thread.
*Failure:* changing controller type from the in-game menu during play is an unsynchronised call
into a running core. **Verified.**

**H4. A failed save-state load is reported as success.**
`unserializeState` returns a Boolean; the result is discarded and the toast is unconditional.
`EmulationActivity.kt:2803-2812`.
*Failure:* loading a state from a different core version silently does nothing and says "State
loaded." **Verified.**

**H5. No free-space check anywhere, and a partial copy survives as a valid-looking ROM.**
`StorageManager` is imported and never used (`RomLibrary.kt:7`). Copies are unverified and
uncleaned on throw (`:462-471`, `:577-583`).
*Failure:* storage fills during a disc import; the truncated file is picked up by the next scan
as a playable game. **Verified** (absence); partial-write behaviour inferred.

**H6. Duplicate ROM names are silently overwritten.**
`copyTo(dest, overwrite = true)` with no existence check, on both direct import and archive
extraction. `RomLibrary.kt:469-470, 578-582`. **Verified.**

**H7. BIOS status is wrong for multi-file requirements, and nothing checks BIOS at launch.**
Intellivision needs `exec.bin` *and* `grom.bin`, but `status()` uses `any` (`BiosCatalog.kt:27, 51`).
Separately, `bios` appears nowhere in `EmulationActivity.kt` — there is no runtime check or
message.
*Failure:* one of two files present shows a green tick; the game black-screens with no
diagnostic. **Verified.**

**H8. Save failures on the exit path are completely silent.**
`persistSram`/`autoSaveState` wrap writes in `runCatching {}` with no failure branch — no log, no
toast. `EmulationActivity.kt:2814-2825`. **Verified.**

**H9. Frame pacing uses the phone's refresh rate even after moving to the glasses.**
`getDefaultRefreshRate()` reads the phone display once at create time and feeds `FPSSync`;
nothing re-queries on display switch. `GLRetroView.kt:125-127`; `libretrodroid.cpp:620`;
`fpssync.cpp:41`.
*Failure:* a 60 Hz game on a 120 Hz phone keeps 120 Hz pacing on 60 Hz glasses — judder and
audio time-stretch drift, in the headline mode. **Verified** mechanism; severity inferred.

**H10. The menu hotkey is tracked across all pads at once.**
`pressedGamepadKeys` is one global set; the combo check doesn't know which device pressed what.
`EmulationActivity.kt:130, 247-248, 1008-1011`.
*Failure:* in local co-op, P1 holding L1 plus P2 holding R1+Select opens the pause menu for
everyone. **Verified.**

**H11. Analog triggers have no axis fallback.**
Nothing reads `AXIS_LTRIGGER`/`AXIS_RTRIGGER`/`AXIS_BRAKE`/`AXIS_GAS`; L2/R2 arrive only as
discrete key events. `EmulationActivity.kt:284-289`.
*Failure:* on pads that report triggers purely as axes, L2/R2 never fire — notably Dreamcast,
whose layout comment depends on them. **Verified** absent; hardware behaviour inferred.

**H12. Rumble is collapsed to a fixed 40 ms blip.**
The event payload is discarded; strength and start/stop are ignored.
`EmulationActivity.kt:821-822, 405-417`.
*Failure:* a 3-second rumble becomes one tick; a "stop" event becomes a spurious buzz.
**Verified.**

**H13. Back does nothing while the layout editor is open.**
The dispatcher callback is always enabled and consumes the event; `gameMenu.onBack()` returns
false, then `showMenu()` early-returns on `editMode`. `EmulationActivity.kt:369-375, 1381`.
*Failure:* back-gesture users have no way out of the editor except small text buttons.
**Verified.**

**H14. `refresh()` throws D-pad focus back to the top of the screen.**
Rebuilds re-focus the first focusable row. `GameMenuView.kt:133-146`, callers at
`EmulationActivity.kt:2719-2722, 2424-2425, 1538-1541, 1555-1558`.
*Failure:* worst precisely in glasses mode, where there is no touchscreen — cycling "Rotate
screen" four times means re-navigating the whole list four times. **Verified.**

**H15. No `contentDescription` anywhere in the app.**
Zero matches repo-wide. The menu's Close (✕) and Back (‹) and the library's settings gear (⚙)
are bare glyphs. `GameMenuView.kt:173, 218`; `MainActivity.kt:224-233`. **Verified.**

**H16. GPL core binaries are committed to the public repo.**
Six `.so` files (~10 MB) tracked under `libretrodroid/app/src/main/jniLibs/arm64-v8a/`, carried
in with the vendored fork's demo app, which is not even built. Directly contradicts the policy
stated in `scripts/fetch_cores.sh:6`. **Verified.**

**H17. Console artwork carries manufacturer trademarks with no licensing note.** — **FIXED
2026-07-22.** The photographic sheets carried legible Nintendo, SEGA, SONY, PlayStation, PS2,
PSP, NAOMI and Atomiswave marks. Replaced with original line art (`console_line.png`): 35
silhouettes, no logo, wordmark or lettering anywhere, verified cell by cell — and zero
non-green pixels in the sheet, so the red Atari fuji is gone too. Provenance recorded in
`THIRD_PARTY_LICENSES.md`. **The removed files remain in git history**; that is a separate
decision (rewrite vs accept) and has not been made.

**H18. Cores are fetched unpinned, so no build can be reproduced or GPL-corresponded.**
`scripts/fetch_cores.sh:7` points at `nightly/android/latest`; the only release tags are
`v0.4.0` and `v0.5.0`, neither recent. A core
update has already changed GL behaviour between runs once (`README.md:56-64`).
*Failure:* "the source is public" cannot be honoured for a specific shipped binary, because
nothing records which commit produced it. **Verified.**

**H19. The 16 KB-aligned core set cannot be rebuilt from the repo, and CI never checks it.**
CI runs only `fetch_cores.sh` (which the README says is not Play-compliant) and
`assembleDebug` — never `build_cores_ndk.sh`, `check_16k.py`, or the unit tests.
`.github/workflows/build.yml:25-26`. Only 12 of 32 cores build from the scripted loop; the rest
need hand-typed commands and a source patch that exists only as a code comment
(`scripts/build_cores_ndk.sh:58-78`).
*Failure:* README's "32/32 aligned" describes a one-time hand-made state nobody can reproduce,
and nothing would catch a regression. **Verified.**

**H20. Three of the five dropped keyboard computers can actually be supported.**
The five were removed on the reasoning that no keyboard device exists — but the keyboard does
not have to come from our side. Scanning the removed cores:

| System | Core | Core-drawn keyboard |
|---|---|---|
| C64 | vice_x64 | **yes** — `vice_mapper_vkbd`, themed, pad-bindable |
| Amiga | puae | **yes** — `puae_mapper_vkbd` |
| ZX Spectrum | fuse | **yes** — "Transparent Keyboard Overlay"; pad toggle unconfirmed |
| MSX | bluemsx | none found |
| Amstrad CPC | cap32 | none found |

Same mistake as the Atari 8-bit, three more times. It also makes `README.md:134` wrong, which
lists a virtual keyboard under **Library-blocked features**. **Verified** for VICE and PUAE
(explicit mapper options); Fuse's pad toggle inferred.

---

## Medium

- **M1.** `singleTask` with no `onNewIntent`: launching a second ROM while one is running
  silently reopens the first. `AndroidManifest.xml:45-49`. **Verified.**
- **M2.** `configChanges` omits `smallestScreenSize`, so entering split-screen recreates the
  activity the class doc says must never be recreated. `AndroidManifest.xml:48`. **Verified**
  gap; downstream impact partly inferred.
- **M3.** All save I/O runs on the UI thread inside `onPause`, blocking on a latch to the GL
  thread — ANR risk on large PSP/PS1 states. `EmulationActivity.kt:2850-2862`. **Verified.**
- **M4.** "Add files" imports synchronously on the UI thread; "Add folder" correctly uses a
  thread. `MainActivity.kt:61-77` vs `:172-188`. **Verified.**
- **M5.** Reset has no confirmation. `EmulationActivity.kt:1506`. **Verified.**
- **M6.** A misfiled `.zip` cannot be corrected — "Change system" only offers zip-claiming
  consoles. `RomLibrary.kt:703-707`. **Verified.**
- **M7.** Corrupt `.7z` is correctly rejected; corrupt `.zip` is silently accepted. Same input,
  opposite outcome. `RomLibrary.kt:521-532`. **Verified.**
- **M8.** No extraction size cap (zip-bomb exposure). `RomLibrary.kt:600-646`. **Verified**
  absence.
- **M9.** BIOS import never validates name or content against the catalog; status matches on
  filename alone. `RomLibrary.kt:648-658`. **Verified.**
- **M10.** `.m3u` multi-disc sets are never checked for missing referenced discs.
  `RomLibrary.kt:71-79`. **Verified** import side.
- **M11.** Returning to glasses always needs a modal tap; leaving them is silent and automatic —
  so a cable wobble drops you to the phone and waits on a dialog you are not looking at.
  `EmulationActivity.kt:863-946`. **Verified.**
- **M12.** `Presentation.show()` failure is logged, never surfaced. `EmulationActivity.kt:1274-1289`.
- **M13.** Phone input overrides an explicit port "Off": `phonePort().coerceAtLeast(0)`, and gyro
  registration never checks the port at all. `EmulationActivity.kt:530, 542-552, 1344-1367`.
- **M14.** Default port trusts `InputDevice.controllerNumber` with no disambiguation; pads that
  report 0 all stack onto Player 1. `EmulationActivity.kt:199-204`. **Inferred** platform quirk.
- **M15.** The player-port picker allows two devices on the same port with no warning.
  `EmulationActivity.kt:1697-1710`.
- **M16.** "Z in D-pad" latches: sliding onto the centre button presses it, sliding off never
  releases it. `ControllerView.kt:472-481`. **Verified.**
- **M17.** Dragging one control onto another makes the covered one permanently unreachable —
  no overlap check in the editor, and hit-testing always resolves ties the same way. Only
  recovery is a full layout reset. `ControllerView.kt:380-394, 687-699`. **Verified.**
- **M18.** Rotation wipes in-progress core-options search text and scroll position.
  `EmulationActivity.kt:2206-2259`. **Verified.**
- **M19.** Core-options search still re-inflates ~83 rows per keystroke, undebounced — the
  docs' own flagged item, unresolved. `EmulationActivity.kt:2215-2251`.
- **M20.** Empty search results render a blank area with no "no matches" message, in both the
  library and core options. `MainActivity.kt:610-626`; `EmulationActivity.kt:2242-2246`.
- **M21.** Library gear button is ~28 dp — below the 48 dp floor that the sibling component
  `glyphButton` already fixes explicitly. `MainActivity.kt:224-233` vs `GameMenuView.kt:235-237`.
- **M22.** No privacy policy exists, and Play requires one plus a Data Safety form regardless of
  what is collected. **Verified** absence.
- **M23.** No AAB has ever been built; the first `bundleRelease` would be run at submission time
  against an untested `useLegacyPackaging` native setup. **Inferred.**
- **M24.** R8 disabled and no ProGuard rules, with a ~134 MB base APK against Play's 150 MB cap.
  `app/build.gradle.kts:30`.
- **M25.** Crash reporting is local-only and opt-in per incident — a deliberate privacy
  trade-off, but it means zero production visibility. `RetroGlassApp.kt:9-11`.
- **M26.** `fetch_cores.ps1` is missing `fbneo`, so Windows contributors following the README
  get a build with no Arcade/Neo Geo support and no error.
- **M27.** Position sliders print a bare signed integer with no unit, breaking the docs' own
  "sliders print their unit" rule that every other slider follows.
  `EmulationActivity.kt:2724-2735`.

## Low

- **L1.** Native exceptions from serialize/variable/controller calls bypass `catchExceptions`
  and crash rather than reaching `showEmulatorError()`. `GLRetroView.kt:165-231`.
- **L2.** `LayoutStore.load()` does not validate persisted floats, though every write path
  clamps them — a corrupted pref can yield `scale=0` (invisible control). `LayoutStore.kt:33-46`.
- **L3.** `KEYCODE_BACK` is excluded from all gamepad handling *including* remap capture, so a
  BACK-mapped face button can neither be used nor rebound. `EmulationActivity.kt:239-241`.
- **L4.** Disconnect auto-pause calls `retroView.onPause()` directly, bypassing the lifecycle
  path that stops audio. `EmulationActivity.kt:1291-1306`.
- **L5.** `MainActivity` is exported with no intent filter. `AndroidManifest.xml:40-42`.
- **L6.** `versionCode` is still 1, and the only tags are `v0.4.0`/`v0.5.0` (both well behind
  `main`) — no recent build can be traced to a commit. **Correction:** earlier passes of this
  audit said "no git tags exist"; that came from a local `git tag -l` before the remote had been
  fetched. The tags are on the remote.
- **L7.** Storage cost of copy-based import is never surfaced, and there is no cleanup tool.
- **L8.** Temp import paths are name-derived with no uniqueness; concurrent imports can collide.
  `RomLibrary.kt:462, 491, 527, 610, 630`.
- **L9.** The `gamepad_connected` toast hardcodes "L1+R1+Select" though the hotkey is remappable.
  `strings.xml:49`.
- **L10.** `aspectRatio()` now reads a float written by the emulation thread with no
  synchronisation — a real regression from the previously-synchronised design, introduced in
  `15e98e8` to fix UI-thread blocking. Practically a one-frame-stale value, not corruption.
- **L11.** Dead code that reads as features: `classifyFile`, `addReferences`,
  `LayoutStore.localMultiplayer()` (documented, no callers), `CompanionView.clearInput()`.
  `GameCovers.load()` was a fifth and is now wired up (see pattern 5).
- **L12.** The vendored fork is 1661 files copied in with no `.gitmodules` and no record of the
  upstream commit — upstream fixes cannot be rebased and local changes cannot be told from
  inherited ones.
- **L13.** Foldable cover-screen detection falls back to `displayId <= 1` if reflection is
  blocked; failure mode is silent. `EmulationActivity.kt:1331-1339`.
- **L14.** `targetSdk 35` and AGP 8.7.3 will need moving before Play's next annual bump.

---

## Suggested order

Not by severity — by cost-to-value.

1. **Test the glasses hotplug on device (C3).** Two minutes, and it either clears the biggest
   unknown in the app or finds its worst bug. Everything else can wait for the answer.
2. **Fix the save key and make writes atomic (C1, C2).** Silent, permanent user data loss, and
   the correct key already exists three lines away.
3. **Close the import hole (C4, C5).** Either wire `classifyFile` into the live path or delete
   it and harden `importZip`; drop `exe` from PSX or content-check it.
4. **Release blockers as one batch (C6, H16, H18, H19, M22).** Signing, committed binaries,
   pinned cores, CI that runs tests and the alignment check.
5. **The keypad preset overlap (H2)**, since it undercuts work just shipped, plus an
   overlap assertion in `ControlLayoutTest` that runs the preset transforms.
6. **Stuck-input on disconnect (H1)** — small fix, bad symptom.
7. **Decide on H20** (bring back C64/Amiga/Spectrum) and **H17** (artwork licensing).

The pattern-level fix worth doing alongside: **make failures speak.** A shared result type for
save/load/import that carries a reason, surfaced in the UI, would collapse C4, H4, H5, H7, H8
and M20 into one change.
