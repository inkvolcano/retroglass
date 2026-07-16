# RetroGlass

A multi-system retro console emulator for Android whose defining feature is an **extended display mode**: connect **any external display** — USB-C/DisplayPort display glasses (XREAL, Rokid, …), a USB-C or HDMI **monitor or TV**, or a wireless/Cast display — and the **game renders on that display** while your **phone becomes the touch controller** — the same technique VLC uses to put video on a second screen. Disconnect it and the game hops back onto the phone automatically.

It uses Android's `DisplayManager` + `Presentation`, which treats every external display the same way, so glasses and monitors are fully interchangeable. The in-game **screen rotation** (0/90/180/270°) is handy for a portrait monitor or vertical "TATE" arcade games, and **screen size/position** let you place the picture wherever it sits best on the external panel.

Built on [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) with official [libretro](https://www.libretro.com/) cores.

> ⚠️ **Personal-use / sideload project.** See [Licensing & distribution](#licensing--distribution) — because of GPL licensing this **cannot be published on the Google Play Store**; distribute via GitHub Releases or F-Droid.

## Features

- **Extended display mode** — game on the glasses, controller on the phone; seamless plug/unplug (the emulator is never restarted, the view is reparented), foldable cover-screen exclusion.
- **40 systems** — Arcade/Neo Geo, NAOMI, Atomiswave, PSP, PS2*, PS1, Dreamcast, Saturn, Sega CD, 32X, Master System, Game Gear, N64, 3DO, SNES, NES, Mega Drive, Nintendo DS, Game Boy/Color, GBA, PC Engine (+CD), Neo Geo Pocket, Neo Geo CD, Atari 2600/5200/7800/8-bit/Lynx, WonderSwan, Virtual Boy, ColecoVision, Intellivision, Vectrex, Pokémon Mini, MSX, C64, Amiga, ZX Spectrum, Amstrad CPC. *(PS2 via Play! is experimental.)*
- **Per-system touch controllers** drawn natively to match each **real pad** — NES's horizontal B–A, the SNES colour diamond, the Genesis A/B/C arc, the DualShock shape-diamond + twin sticks, the **N64 pad** (one centred analog + four yellow C-buttons + A/B), GBA's slanted B–A, etc. Every default layout stays fully on-screen with **no overlapping buttons**, and button glyphs are a uniform size. Multiple selectable presets per system + a drag/resize editor.
- **N64 "Z in D-pad" alt layout** — an optional preset with Z seated in the centre of an enlarged D-pad, so one thumb can hold a direction and Z at the same time (slide onto/off the centre to latch Z).
- **Intellivision keypad** — the full hand-controller: 16-way disc, three side action buttons, and the 12-key numeric keypad (1-9, Clear, 0, Enter) that games use with overlay cards, mapped to FreeIntv (keys 1-9 drive the right-analog disc).
- **Visual layout picker** — the "Choose controller layout" menu shows a live rendered thumbnail of every preset (Default, Large, Compact, Wide, Bottom-heavy, Left-handed, and any system-specific ones) so you can see the arrangement before you pick it. The HTML readme also has a **Controller layouts** gallery rendered from the app's real coordinates (31 distinct pads across the 40 systems).
- **Turbo / autofire** — mark any face/shoulder button to auto-repeat while held (per system); toggled from the in-game "Turbo / autofire buttons…" menu.
- **Portrait & landscape** — in portrait the game sits across the top with the controller below; rotate freely.
- **Screen size, rotation (0/90/180/270°) and position** controls.
- **Physical gamepad support** — when a gamepad plays, the game fills the phone like a handheld and the touch pad is hidden; a floating **≡ button** (top-left) plus the back gesture still open the in-game menu so you can exit or reconfigure by touch. Input is forwarded even when the game is on the glasses.
- **Phone-as-display / two-controller mode** — no external screen needed: pair **two Bluetooth controllers**, assign them P1/P2, and the phone itself is the TV (game fills the screen, no touch pad). Great for couch co-op on the phone alone.
- **Controllers / players manager** — assign phone/BT1/BT2 to any player port, **per-controller button remapping** (press-to-bind) and left-stick-as-D-pad. (Network netplay across phones is **not** supported — that's a RetroArch-frontend feature LibretroDroid doesn't implement.)
- **Controller type** — for systems that support it, switch a port between device types (e.g. PS1 **digital / analog / DualShock**, lightguns), applied per system.
- **Gyro aiming** — tilt the phone to drive the right analog stick for aiming (great with an external display, so the phone is free to move); adjustable sensitivity.
- **Stick tuning** — per-controller **dead zone** and **sensitivity**; **configurable menu hotkey** (L1+R1+Select, L1+R1+Start, Select+Start, or L3+R3).
- **FPS counter** overlay and a **screen background / bezel** (dark / gradient / your own image) framing a shrunk picture.
- **Cover art** — set a cover image per game (or drop images next to ROMs and a folder scan adopts them); shown in the library.
- **Save-data backup** — export all SRAM, save-states and settings to a `.zip` and restore them, so an uninstall or a new phone doesn't lose your progress.
- **Crash reports** — if the app crashes, the next launch offers to share a local crash log (nothing is sent automatically).
- **Core options** — expose each system's real libretro settings (e.g. **PS1 2× internal-resolution upscale**, PSP/N64 render scale, region), applied per system.
- **Cheats** — per-game cheat list (GameShark / Action Replay / PAR codes), toggle on/off.
- **Library** — sort **alphabetically**, **by maker** (Nintendo / Sega / Sony / …, systems newest-first), or **by release date**, plus search, favourites and recently-played.
- **Disc auto-detection** — shared `.cue`/`.bin`/`.iso` images are read on import and filed to the right system automatically (PlayStation vs PS2 vs Dreamcast vs Saturn vs Sega CD, via each disc's boot header). (`.chd` can't be inspected — reassign those with Change system.)
- **Video filters** (CRT/LCD/Sharp/**smooth-edge upscale**), **fast-forward**, **rumble → vibration**, **save-state slots** + auto-save/resume, **multi-disc** swapping, **screenshots** (→ Pictures/RetroGlass), **BIOS status** screen.

## Build

Requires JDK 17 and the Android SDK (platform 35). Cores are **not committed** (they are GPL binaries); fetch them first.

```bash
git clone https://github.com/<you>/retroglass.git
cd retroglass
bash scripts/fetch_cores.sh        # or: pwsh scripts/fetch_cores.ps1
./gradlew assembleDebug            # APK in app/build/outputs/apk/debug/
```

CI (`.github/workflows/build.yml`) fetches the cores and builds a debug APK on every push.

**arm64-v8a only.** Install `app/build/outputs/apk/debug/app-debug.apk` on any modern (arm64) Android phone with USB-C DisplayPort output.

## Using it

1. **Add ROMs** — *Add ROMs* offers two ways:
   - **Pick files** — multi-select individual games (and `.zip`; arcade `.zip` romsets are kept intact).
   - **Scan a folder** — point at a folder and RetroGlass walks it **recursively**, sorting every game into the right system for a **unified library**. Per-system **subfolders** (e.g. `PS2/`, `Dreamcast/`) are used as hints to place shared disc images correctly, and any **BIOS files it finds are routed to the system directory automatically**. Non-game files (art, readmes) are ignored.

   Shared disc formats (`.chd`/`.iso`) default to PlayStation; long-press → *Change system* to move one to PS2 or Dreamcast.
2. **Multi-disc games** — a set named `Game (Disc 1).chd`, `Game (Disc 2).chd`, … is **auto-detected on import** and stitched into an `.m3u` playlist, so it appears as **one** library entry; switch discs in-game via the menu → *Swap disc*. (You can also import your own `.m3u` — the discs it lists are hidden and play through the playlist.)
3. **BIOS** — the BIOS button shows which systems have their BIOS and which are missing. PS1/PS2/Dreamcast/Saturn/3DO/ColecoVision/Intellivision/Amiga/Neo Geo need one. Folder-scan imports them for you; you can also add one by hand.
4. Tap a game. Plug the glasses in any time.

ROMs and BIOS files are **not** included — dump them from hardware you own.

### Three ways to play

- **External display + phone touch pad** *(the headline mode)* — plug in glasses / a monitor / a TV; the game renders there and the phone is the touch controller.
- **External display + Bluetooth pad(s)** — the game is on the big screen and one or two Bluetooth controllers play; the phone is free.
- **Phone as the display (no external screen)** — pair one or two Bluetooth controllers, assign them as Player 1/2 (*Controllers / players* in the menu), and the game fills the phone like a handheld or a couch-co-op TV. The touch pad hides itself and a floating **≡** button (which fades out when idle, and returns on a tap) opens the menu.

### Reaching the menu without touching the screen

Every menu — in-game options **and** the game library — is fully operable from a Bluetooth gamepad, so you never have to put the controller down:

- **Open the in-game menu:** press **L1 + R1 + Select** together (or tap **≡**, or use the back gesture).
- **Navigate:** D-pad moves, **A** confirms, **B** goes back.
- **Change game:** choose **Save & exit** → the library is D-pad navigable and **A** launches the highlighted game. So "finish this game, pick another, tweak options" is a controller-only flow, on the phone alone or with an external screen.

### In-game menu

Save-state slots, load, fast-forward, **screen size / rotation / position**, **video filter** (CRT/LCD/Sharp), **core options** (per-system libretro settings — e.g. *PS1 2× internal-resolution upscale*, PSP/N64 render scale, region), **cheats** (GameShark / Action Replay codes), **screenshot** (→ `Pictures/RetroGlass`), controller layout picker & editor, rumble toggle, **Controllers / players**, disc swap, reset, and Save & exit.

## Known limitations

- **PS2** (Play! core) is experimental — low compatibility on phones. The good PS2 emulator (ARMSX2) is a standalone app, not a libretro core, so it can't be embedded.
- **Home computers** (MSX/C64/Amiga/ZX Spectrum/Amstrad) have a joystick+fire layout; LibretroDroid has no keyboard-device path, so full typing relies on each core's own on-screen keyboard (navigate with the gamepad).
- **Arcade** romsets must match FBNeo's expected versions; Neo Geo needs `neogeo.zip`.
- While the glasses occupy the USB-C port the phone can't charge — use a USB-C hub with power passthrough for long sessions.
- **Wireless displays add lag.** A wired external display (USB-C/DisplayPort/HDMI) is near-zero latency and is the intended experience. **Miracast / "Smart View" wireless displays work** (they register as a normal external display), but add ~100–300 ms of input latency, which is noticeable in fast games. **Chromecast via Google Cast is not supported** — Cast streams pre-encoded media, not a live game surface, so there is no display to render onto; only wired displays and Miracast-style wireless displays appear as real external screens.

## Licensing & distribution

RetroGlass is **GPL-3.0** (see [`LICENSE`](LICENSE)) because LibretroDroid and the bundled libretro cores are GPL. Distribute via **GitHub Releases**, **[F-Droid](https://f-droid.org/)**, or the **Google Play Store** — [Lemuroid](https://play.google.com/store/apps/details?id=com.swordfish.lemuroid) (GPL-3.0, libretro) is on all three, so a GPL libretro emulator on Play is fine as long as the source is public (it is).

### Play Store: the 16 KB requirement

The real gate for Play is not licensing but **[16 KB page-size support](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html)** (required for all Play submissions targeting Android 15+ since Nov 2025). Native `.so` files must have 16 KB-aligned LOAD segments.

- Prebuilt cores from the libretro nightly buildbot (via `fetch_cores.sh`) are **mostly 4 KB-aligned** — fine for sideloading/GitHub/F-Droid, but **not** Play-compliant.
- **`scripts/build_cores_ndk.sh`** rebuilds cores from source with **NDK r28+**, which produces 16 KB-aligned binaries. Verify with `python scripts/check_16k.py`.
- **All 32/32 bundled cores now build 16 KB-aligned** (`0x4000` LOAD alignment) — the app is Play-ready on the alignment requirement. The last holdout, Saturn (`mednafen_saturn`), needed `APP_CFLAGS=-O1 APP_CPPFLAGS=-O1` because NDK r28's default optimizer hangs on one of its compilation units. CMake/heavier cores (mgba, ppsspp, melonDS, mupen64plus_next, PCSX2/Play!, VICE, PUAE, Saturn) each need their own build invocation — see the comments in `build_cores_ndk.sh`.

## Tech notes

- **One emulator, moved between screens.** LibretroDroid's native core is a process-wide singleton and only one `GLRetroView` may exist. The single view is created once on the phone and **reparented** between the phone container and an `android.app.Presentation` on the external display — never torn down. Recreating it raced the native singleton (black external screen, mirror-on-reconnect); reparenting only rebuilds the EGL surface, so gameplay is seamless across plug/unplug.
- **External-display detection** uses `MediaRouter` live-video routes first, then `DisplayManager` presentation displays filtered by `Display.getType() != INTERNAL` (reflection) so a foldable's *cover* screen isn't mistaken for an external display.
- **Display modes** are decided in `arrangeLayout()`: extended (game on Presentation, pad fills phone) · portrait split (game top, pad bottom) · landscape overlay (game + translucent pad) · **phone-as-display** (`!extendedMode && phonePort() < 0` → game fills the phone, no pad, auto-fading ≡ button).
- **Input** is routed per-device by descriptor: `InputConfig` maps each controller (`phone`/BT1/BT2) to a player port with a per-controller button remap and optional left-stick-as-D-pad. The **L1+R1+Select** menu hotkey and the gamepad-navigable dialogs (A→confirm, B→back, applied via each dialog's `setOnKeyListener` because a `Dialog` owns its own focused window) make the whole UI controller-operable.
- **Core options** are read live from `GLRetroView.getVariables()` (real keys/values/descriptions). LibretroDroid does **not** expose each option's *allowed-value list*, so `CoreOptions.KNOWN_VALUES` ships curated pickers for the high-impact keys (resolution/region) and everything else is editable as raw text. Overrides apply at load via `GLRetroViewData.variables` and live via `updateVariables()` (some, like internal resolution, only take effect after *Reset game*).
- **Library-blocked features:** rewind, online netplay, and RetroAchievements have **no LibretroDroid API** (verified by decompiling the 0.14.0 AAR) — they'd require replacing the emulation engine, which would sacrifice the reparent-based extended-display mechanism. A **virtual keyboard** for home computers is likewise blocked: `sendKeyEvent` is gamepad-only, with no `RETRO_DEVICE_KEYBOARD` path.

## Credits

- [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) by Filippo Scognamiglio (GPL-3.0)
- The [libretro](https://www.libretro.com/) project and core authors (various GPL licenses)
- Cores fetched from the [libretro buildbot](https://buildbot.libretro.com/)
