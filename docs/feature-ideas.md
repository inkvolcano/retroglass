# Feature ideas — for review

Running list of things RetroGlass could gain, with enough analysis to decide against them
cheaply. Nothing here is committed to. Ordered by (value ÷ effort) within each group.

Audited against the codebase on 2026-07-22: the fork already exposes serialize/unserialize
(save states), disk control, cheats and a microphone interface. It has **no** runahead, rewind
or netplay — those numbers are what most of the estimates below hinge on.

---

## 1. Latency: run-ahead / preemptive frames

**What it is.** Every game has built-in input lag — the code takes 1–6 frames to react to a
button. Run-ahead hides it: each frame the emulator saves state, runs N frames forward, shows
the future frame, and restores. You get a reaction *faster than original hardware*.

**Why it fits here.** This is the single most-felt quality difference in emulation, and it
matters more on a touchscreen than on a pad — touch already adds its own latency, so we start
further behind than a desktop setup.

**What it costs.** It runs the core N+1 times per displayed frame, so it is roughly (N+1)× the
CPU. We have measured headroom: SNES/PS1/N64 all hold 60fps with a five-stage shader chain at
4×, and that headroom is *GPU*, whereas run-ahead spends *CPU* — so the shader measurements do
not transfer and this needs its own test. Preemptive Frames is the newer, cheaper variant that
reaches the same result with less compute and would be the one to target.

**Feasibility.** The hard requirement is fast, correct save states, which the fork already has.
This is a loop change in `libretrodroid.cpp` around `retro_run`, not new plumbing. Second-order
problem: audio has to be muted on the discarded frames or it doubles up.

**Verdict.** Highest value on the list. Worth prototyping on a light core (NES) first, where a
mistake is obvious and cheap.

### Where it would go (scoped 2026-07-22)

`LibretroDroid::step()` in `libretrodroid.cpp:449` is the whole loop, and it is small:

```cpp
for (size_t i = 0; i < frames * frameSpeed; i++)
    core->retro_run();
if (video && !video->rendersInVideoCallback()) video->renderFrame();
```

Run-ahead slots in around that, using the state calls that already exist —
`serializeState()` (:585) and `unserializeState()` (:147):

1. `retro_run()` once with the current input — this is the frame the player will *see*.
2. `serializeState()`.
3. `retro_run()` N more times, discarding video and audio.
4. Render the last frame, then `unserializeState()` back to step 2's snapshot.

Three things to get right, in the order they will bite:

- **Audio must be muted on the discarded runs.** Everything funnels through one
  `audio->write` (:555), so a single "suppress" flag around the inner loop covers it. Skip this
  and every frame plays N+1 times.
- **Input has to be sampled once** and replayed for the extra runs, or the run-ahead frames see
  different input than the visible one and the picture jitters.
- **Cost is (N+1)× CPU**, and all our measured headroom is GPU — the 60fps figures for
  five-stage shader chains at 4× say nothing about this. Measure separately, on PS1/N64 first
  since those are the CPU-heaviest cores we ship.

Worth knowing before starting: `serializeState()` allocates and copies the whole state every
frame. For PS1 that is ~1–2 MB per call at 60Hz. A reusable buffer is likely a prerequisite
rather than an optimisation, and it is the same buffer rewind wants.

## 2. Rewind

Ring buffer of save states, held button walks time backwards. Same primitive as run-ahead, far
easier — no timing subtlety, just memory. Cost is a state every N frames; PS1 states are
~1–2 MB, so a 10-second buffer at 4 states/sec is ~60 MB. Cheap on modern phones, needs a cap
and a per-console default.

Worth doing **after** run-ahead, since building the state-ring for one gives the other nearly
free.

## 3. RetroAchievements

Well-defined REST + memory-watching integration, and a large existing community. Notable
constraint: **hardcore mode disables save states, rewind, slow-motion and cheats** — so it has
to be a mode the user opts into per session, not a global setting, and it interacts directly
with items 1 and 2. Needs an account login flow, which is the bulk of the work.

Real question to answer before starting: does this user want it? It is a social feature on a
personal app.

## 4. Netplay

Rollback netplay is a large project (input prediction, state sync, NAT traversal) and the fork
has none of it. Realistically out of scope unless it becomes the point of the app.

---

## Things specific to this app

### Texture-level upscaling (already half-discovered)

The N64 core exposes `Texture Enhancement` (2xBRZ…6xBRZ, HQ2X/HQ4X) and `Texture filter`,
which upscale textures *before* rasterisation — a different axis from our post-process chain and
complementary to it. Flycast and PPSSPP expose similar. Since core options are now a proper
pick-list, surfacing "texture enhancement" as a first-class item in the Video menu is mostly
presentation work over machinery that already exists.

### Per-game settings

Filters, upscale factor and core options are per-console today. Per-game would let a
CRT-heavy look sit on one title without imposing it on the rest of the system. `LayoutStore`
already keys by console; extending to `console+rom` is mechanical. The UI question is harder:
how does the user tell which level they are editing?

### Save-state slots with screenshots

Slots exist (0 auto, 1–4 manual). Showing each with a thumbnail and timestamp would make them
usable rather than a guess. Cheap: capture the framebuffer at save time.

### Shader presets from .slangp

Would open the whole RetroArch shader library instead of hand-ported filters. Blocked on a
`.slangp` parser plus GLSL translation, and most of the interesting presets exceed GLSL ES 3.00
anyway (the reason Super-xBR, ScaleFX and GSR1 are still unported). Large, low certainty.

### Duplicate-frame skip / temporal anti-flicker

Already noted as unbuilt in `docs/filters.md`. Skipping identical frames saves GPU on 2D games
that idle; temporal blending would fix the flicker some games use deliberately (Virtual Boy,
Game Boy dithering). Small, self-contained, verifiable.

### Bluetooth pad on-screen hints

The controller layout is drawn even in gamepad mode (companion dashboard). With a pad attached
the touch layout could dim to a live *diagram* instead — it already does exactly this in
`monitorMode`, so this is a mode-switching decision, not new code.

---

## Rejected, with reasons

- **Vulkan / ParaLLEl-RDP for N64.** The N64 core exposes ParaLLEl-RDP options, but the fork
  only ever supplies a GLES context and there is no Vulkan path. Adding one is a rewrite of the
  video layer for one console.
- **Snapdragon GSR1.** Needs ESSL ≥3.10; our vertex path is 3.00 and mixing versions fails to
  link. Documented in `docs/filters.md`.
- **.ecm decoding.** Needs exact Reed–Solomon P/Q and EDC reconstruction; a wrong implementation
  produces a disc that fails confusingly much later. Flagging the file is the honest behaviour.

---

## Sources

- [RetroArch run-ahead announcement](https://medium.com/@libretro/retroarch-1-7-2-achieving-better-latency-than-original-hardware-through-new-runahead-method-1b80d26bb5d1)
- [Run Ahead guide](https://docs.libretro.com/guides/runahead/)
- [Run-ahead vs Preemptive Frames](https://forums.libretro.com/t/runahead-vs-preemptive-frames/43130)
- [RetroAchievements guide](https://docs.libretro.com/guides/retroachievements/)
- [RetroAchievements libretro core support](https://docs.retroachievements.org/libretro-core-support/)
