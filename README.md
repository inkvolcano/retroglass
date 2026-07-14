# RetroGlass

A multi-system retro console emulator for Android whose defining feature is an **extended display mode**: plug in USB-C display glasses (XREAL, Rokid, …) or any USB-C/HDMI monitor, and the **game renders on the external display** while your **phone becomes the touch controller** — the same technique VLC uses to put video on a second screen. Unplug the glasses and the game hops back onto the phone automatically.

Built on [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) with official [libretro](https://www.libretro.com/) cores.

> ⚠️ **Personal-use / sideload project.** See [Licensing & distribution](#licensing--distribution) — because of GPL licensing this **cannot be published on the Google Play Store**; distribute via GitHub Releases or F-Droid.

## Features

- **Extended display mode** — game on the glasses, controller on the phone; seamless plug/unplug (the emulator is never restarted, the view is reparented), foldable cover-screen exclusion.
- **32 systems** — Arcade/Neo Geo, PSP, PS2*, PS1, Dreamcast, Saturn, N64, 3DO, SNES, NES, Mega Drive (+GG/SMS), 32X, Nintendo DS, Game Boy/Color, GBA, PC Engine, Neo Geo Pocket, Atari 2600/5200/7800/Lynx, WonderSwan, Virtual Boy, ColecoVision, Intellivision, Vectrex, Pokémon Mini, MSX, C64, Amiga, ZX Spectrum, Amstrad CPC. *(PS2 via Play! is experimental.)*
- **Per-system touch controllers** drawn natively, with 7 selectable layout presets and a drag/resize editor.
- **Portrait & landscape** — in portrait the game sits across the top with the controller below; rotate freely.
- **Screen size, rotation (0/90/180/270°) and position** controls.
- **Physical gamepad support** — auto-hides the touch pad; input forwarded even when the game is on the glasses.
- **Video filters** (CRT/LCD/Sharp), **fast-forward**, **rumble → vibration**, **save-state slots** + auto-save/resume, **multi-disc** swapping, **BIOS status** screen, **library grouping + search**.

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

1. **Add ROMs** — pick your game files (multi-select and `.zip` supported; arcade `.zip` romsets are kept intact). Games are sorted by type; shared disc formats (`.chd`/`.iso`) import as PlayStation and can be reassigned with long-press → *Change system*.
2. **BIOS** — the BIOS button shows which systems have their BIOS and which are missing. PS1/PS2/Dreamcast/Saturn/3DO/ColecoVision/Intellivision/Amiga/Neo Geo need one.
3. Tap a game. Plug the glasses in any time.

ROMs and BIOS files are **not** included — dump them from hardware you own.

## Known limitations

- **PS2** (Play! core) is experimental — low compatibility on phones. The good PS2 emulator (ARMSX2) is a standalone app, not a libretro core, so it can't be embedded.
- **Home computers** (MSX/C64/Amiga/ZX Spectrum/Amstrad) have a joystick+fire layout; LibretroDroid has no keyboard-device path, so full typing relies on each core's own on-screen keyboard (navigate with the gamepad).
- **Arcade** romsets must match FBNeo's expected versions; Neo Geo needs `neogeo.zip`.
- While the glasses occupy the USB-C port the phone can't charge — use a USB-C hub with power passthrough for long sessions.

## Licensing & distribution

RetroGlass is **GPL-3.0** (see [`LICENSE`](LICENSE)) because LibretroDroid and the bundled libretro cores are GPL.

**This is why it is not on Google Play:** the GPL is widely held to be incompatible with Google Play's Developer Distribution Agreement (the DDA's redistribution restrictions conflict with the freedoms the GPL guarantees). This is the same reason [RetroArch](https://www.retroarch.com/) is not on Google Play. Distribute via **GitHub Releases** or **[F-Droid](https://f-droid.org/)** instead.

## Credits

- [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) by Filippo Scognamiglio (GPL-3.0)
- The [libretro](https://www.libretro.com/) project and core authors (various GPL licenses)
- Cores fetched from the [libretro buildbot](https://buildbot.libretro.com/)
