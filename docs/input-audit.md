# Input audit: does every system's real controller fit on the pad?

Done 2026-07-22, after dropping the keyboard computers. The question was narrow: **did any
supported system ship with input we cannot reproduce?** A system whose games ask for a key we
have no way to send is worse than a system we never listed.

The hard limit behind all of this: **the fork has no keyboard device.** `RETRO_DEVICE_KEYBOARD`
and `SET_KEYBOARD_CALLBACK` appear only in `libretro.h` — nothing implements them. A core that
routes input through the host keyboard is unreachable from our pad.

## Result

No remaining system shipped with a keyboard. All 34 shipped with a joystick, joypad or a
keypad-on-the-controller. Dropped for keyboards: MSX, C64, Amiga, ZX Spectrum, Amstrad CPC,
Atari 8-bit.

Three systems shipped a **12-key numeric keypad on the controller** — which is not a keyboard,
but is still input the pad has to carry:

| System | Real hardware | Core | Reachable? | We ship |
|---|---|---|---|---|
| Intellivision | 12-key keypad + 3 side buttons + disc | freeintv | yes, on the RetroPad | **all 12** |
| ColecoVision | 12-key keypad + 2 fire | gearcoleco | yes — descriptors read `Keypad 0`…`Keypad #` | **2 of 12** |
| Atari 5200 | 12-key keypad + START/PAUSE/RESET | atari800 | keypad is on the **host keyboard** | **0 of 12** |

Everything else is d-pad/stick plus face buttons and is fully covered. Arcade, NAOMI and
Atomiswave are JAMMA boards — buttons only. The Atari 2600/7800 and Vectrex keypad *accessories*
were peripherals, not what the console shipped with.

## The two gaps

**ColecoVision** is a plain miss on our side. gearcoleco exposes the whole keypad as ordinary
RetroPad input descriptors; the layout binds `1` and `2` and stops. Coleco games use the keypad
at the title screen to choose game mode and skill level, and several use it in play (*Fathom*'s
map, *WarGames*' targeting). Fixable exactly the way Intellivision already is.

**Atari 5200** is not reachable the same way. atari800's own text says the 5200 keypad is
`"Keyboard 0-9"`, `*` is `"Keyboard Numpad *"` and `#` is `"Keyboard ="` — the host keyboard,
which we do not have. But the core carries its own escape hatch:

> Show or hide the on-screen virtual keyboard overlay. It can also be toggled in-game with the
> mapped controller button (L3 for the Atari joystick, R3 for the 5200).

So R3 raises a keyboard the **core draws into the framebuffer**, navigated with the pad. One
button in the layout buys the whole keypad. Untested — the string is the core's documentation,
not a verified behaviour, and whether the overlay renders and takes d-pad input under this fork
has to be confirmed on device.

That same option is why the Atari 8-bit removal deserves a footnote: `L3` would have given it a
usable keyboard too. It was removed anyway, on instruction and consistently with the other five
computers — but "no keyboard path exists" was too strong for that one core. It has a
core-drawn one.

## Not fixed here

Both gaps are layout work, deliberately left out of the removal commit so it stays one change.
`atari5200()` needs an R3 button; `coleco()` needs the other ten keys. `intellivision()` is the
worked example for both.
