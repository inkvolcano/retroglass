#!/usr/bin/env python3
"""Extract per-console {key,name,maker,year,accent,img} from Console.kt for the menu
HTML prototype. Usage: python scripts/gen_menu_data.py Console.kt out.json"""
import re, json, sys

src = open(sys.argv[1], encoding="utf-8").read()

enum = {}
for m in re.finditer(r'\n    ([A-Z0-9_]+)\(\s*\n\s*displayName = "((?:[^"\\]|\\.)*)",(.*?)\n    \)', src, re.S):
    name, disp, block = m.group(1), m.group(2), m.group(3)
    acc = re.search(r'accentColor = Color\.parseColor\("(#[0-9A-Fa-f]{6})"\)', block)
    enum[name] = {"disp": disp, "accent": acc.group(1) if acc else "#888888"}

def whenmap(header, strval):
    mm = re.search(header + r".*?when \(this\) \{(.*?)\n    \}", src, re.S)
    out = {}
    if not mm:
        return out
    for line in mm.group(1).splitlines():
        lm = re.match(r'\s*(.+?)\s*->\s*(.+)', line)
        if not lm:
            continue
        raw = lm.group(2).strip()
        val = raw.strip('"') if strval else raw
        for c in re.findall(r'[A-Z0-9_]+', lm.group(1)):
            if c in enum:
                out[c] = val
    return out

years = whenmap(r"val year: Int get\(\) =", False)
makers = whenmap(r"val maker: String get\(\) =", True)

IMG = {"NES":0,"GAMEBOY":1,"SNES":2,"VIRTUALBOY":3,"N64":4,"GBA":5,"POKEMONMINI":6,"NDS":7,
       "MASTERSYSTEM":8,"MEGADRIVE":9,"GAMEGEAR":10,"SEGACD":11,"SEGA32X":12,"SATURN":13,
       "DREAMCAST":14,"NAOMI":15,"ATOMISWAVE":16,"PSX":17,"PS2":18,"PSP":19}

out = []
for k, v in enum.items():
    y = years.get(k, "0")
    out.append({"k": k, "n": v["disp"], "accent": v["accent"],
                "year": int(y) if str(y).isdigit() else 0,
                "maker": makers.get(k, "?"), "img": IMG.get(k, -1)})
json.dump(out, open(sys.argv[2], "w", encoding="utf-8"))
print(f"{len(out)} systems ->", sys.argv[2])
print(json.dumps([o for o in out if o["k"] in ("PSX","SNES","NES","N64","DREAMCAST","GBA")]))
