# Input audit: does every system's real controller fit on the pad?

Done 2026-07-22. The question was narrow: **did any supported system ship with input we cannot
reproduce?** A system whose games ask for a key we have no way to send is worse than a system we
never listed.

The hard limit behind all of it: **the fork has no keyboard device.** `RETRO_DEVICE_KEYBOARD`
and `SET_KEYBOARD_CALLBACK` appear only in `libretro.h`; nothing implements them. A core that
routes input through the host keyboard is unreachable from our pad — *unless the core draws its
own keyboard*, which turns out to be the whole story below.

## How the bindings were established

Not from memory and not from upstream source, which may not match the binary we ship. The
descriptors were read **out of the shipped `.so` files**: `retro_input_descriptor` is a struct
array, and its `const char *description` is filled at load time by an `R_AARCH64_RELATIVE`
relocation. Walking `.rela.dyn` for the relocation whose addend is a given string's vaddr gives
the slot it lands in; the four `unsigned`s in front of that slot are `port`, `device`, `index`,
`id`. That yields the exact port/button/label table the core will present at runtime.

Worth repeating for the next core that needs it — guessing a mapping produced a bug that
nothing else could have caught (below).

## Result

**35 systems. No system needs a key it cannot send.** Five keyboard computers stay dropped
(MSX, C64, Amiga, ZX Spectrum, Amstrad CPC) — their cores were removed and none of them has a
core-drawn keyboard we checked for.

Four systems ship input beyond a joystick and buttons:

| System | Real hardware | Core | How it is reached |
|---|---|---|---|
| Intellivision | 12-key keypad, 3 side buttons | freeintv | 1-4/6-9 as right-analog directions, rest as buttons |
| ColecoVision | 12-key keypad, 2 fire | gearcoleco | 1-8/\*/# on buttons, 0 and 9 on the left analog |
| Atari 5200 | 12-key keypad | atari800 | core's on-screen keyboard, raised by **R3** |
| Atari 8-bit | full keyboard | atari800 | core's on-screen keyboard, raised by **L3** |

Everything else is d-pad/stick plus face buttons and is fully covered. Arcade, NAOMI and
Atomiswave are JAMMA boards — buttons only. The Atari 2600/7800 and Vectrex keypad *accessories*
were peripherals, not what the console shipped with.

## The Atari 8-bit is supported after all

It was dropped with the other keyboard computers, on the reasoning that no keyboard path exists.
That reasoning was wrong for this core specifically. atari800's own option text:

> Show or hide the on-screen virtual keyboard overlay. It can also be toggled in-game with the
> mapped controller button (L3 for the Atari joystick, R3 for the 5200).

and its descriptor table agrees exactly — `id 14 (L3) Virtual Keyboard` in 8-bit mode,
`id 15 (R3) Virtual Keyboard` in 5200 mode. The keyboard is **drawn into the framebuffer by the
core** and driven by the pad, so it needs no keyboard device at all. The system came back with a
`KBD` button, which is the only reason it is playable rather than a joystick-only subset.

The same button is what makes the **5200** whole. Its keypad is on the host keyboard
(`5200 Keypad 0-9 "Keyboard 0-9"`), and only 0, 1, 2, 3, 7, `*` and `#` have RetroPad buttons —
4, 5, 6, 8 and 9 have none, and atari800 declares no analog descriptors to hide them on. The
overlay is the only route to a complete keypad, so the layout offers `KEYS` (R3) rather than a
keypad with five holes in it.

## The ColecoVision bug

The old layout bound `"1"` to `BUTTON_X` and `"2"` to `BUTTON_Y`. gearcoleco declares `Y` as
*Keypad 1* and `X` as *Keypad 2* — so the two keys nearly every Coleco game uses to start were
**swapped**, and nothing could tell: both are valid keypad presses, just the wrong ones. The
game simply started in the wrong mode.

It now carries all twelve keys, bound as declared: 1=Y, 2=X, 3=L, 4=R, 5=L2, 6=R2, 7=L3, 8=R3,
\*=START, #=SELECT. Keys 0 and 9 are the two the RetroPad has no button left for, so gearcoleco
reads them off the left analog axes (index 0, ids 0 and 1) and `ControllerView.COLECO_KEYPAD`
sends them there.

`ControlLayoutTest` now pins this whole table, and pins that both Atari layouts keep their
overlay button — losing one makes the system unplayable rather than merely degraded, which is
not a failure the app can report.

## Still to confirm on device

- **The overlay itself.** That it renders under this fork and takes d-pad input is the core's
  claim plus its descriptor table, not something seen running. Both Atari systems depend on it.
- **ColecoVision 0 and 9.** Sent as full positive deflection; the sign is a reasonable
  assumption, not a read one. If they do not register, flip the sign in `COLECO_KEYPAD`.
