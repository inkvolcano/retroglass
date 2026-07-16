#!/usr/bin/env python3
"""Device-free mirror of ControllerDefs.dumpLayoutsJson: parse every console's default
layout (geometry + resolved colors) straight from Console.kt, emitting the same JSON the
app dumps. Lets scripts/gen_layout_previews.py regenerate the doc previews without a phone.

Usage: python scripts/extract_layouts.py Console.kt out.json
"""
import re, json, sys

def main():
    src = open(sys.argv[1], encoding="utf-8").read()

    # class colour constants
    consts = dict(re.findall(r'private val ([A-Z_]+)\s*=\s*Color\.parseColor\("(#[0-9A-Fa-f]{6,8})"\)', src))

    # enum: console -> (displayName, bodyColor, accentColor)
    enum = {}
    for m in re.finditer(r'\n    ([A-Z0-9_]+)\(\s*\n\s*displayName = "((?:[^"\\]|\\.)*)",(.*?)\n    \)', src, re.S):
        name, disp, block = m.group(1), m.group(2), m.group(3)
        body = re.search(r'bodyColor = Color\.parseColor\("(#[0-9A-Fa-f]{6,8})"\)', block)
        accent = re.search(r'accentColor = Color\.parseColor\("(#[0-9A-Fa-f]{6,8})"\)', block)
        enum[name] = {"display": disp,
                      "body": body.group(1) if body else "#222222",
                      "accent": accent.group(1) if accent else "#888888"}

    # year getter: CONSOLE(, CONSOLE)* -> NNNN
    year = {}
    ym = re.search(r"val year: Int get\(\) = when \(this\) \{(.*?)\n    \}", src, re.S)
    if ym:
        for line in ym.group(1).splitlines():
            lm = re.match(r"\s*([A-Z0-9_,\s]+?)\s*->\s*(\d+)", line)
            if lm:
                for c in re.findall(r"[A-Z0-9_]+", lm.group(1)):
                    year[c] = int(lm.group(2))

    # baseControls: console -> layout function (entries may wrap across lines)
    m = re.search(r"fun baseControls\(console: Console\): List<ControlDef> = when \(console\) \{(.*?)\n    \}", src, re.S)
    console_fn = {}
    for lm in re.finditer(r"((?:Console\.[A-Z0-9_]+\s*,?\s*)+)->\s*([a-zA-Z0-9_]+)\(", m.group(1)):
        for c in re.findall(r"Console\.([A-Z0-9_]+)", lm.group(1)):
            console_fn[c] = lm.group(2)

    def fn_body(name):
        mm = re.search(r"private fun " + re.escape(name) + r"\([^)]*\): List<ControlDef>", src)
        start = mm.end()
        nxt = src.find("private fun ", start)
        return src[start: nxt if nxt != -1 else len(src)]

    def hexnorm(h):
        h = h.lstrip("#")
        if len(h) == 8: h = h[2:]      # drop alpha
        return "#" + h.upper()

    def parse_fn(name):
        body = fn_body(name)
        floats = dict(re.findall(r"val\s+([a-zA-Z0-9_]+)\s*=\s*([0-9.]+)f\b", body))
        colvals = {}
        for cm in re.finditer(r'val\s+([a-zA-Z0-9_]+)\s*=\s*Color\.parseColor\("(#[0-9A-Fa-f]{6,8})"\)', body):
            colvals[cm.group(1)] = hexnorm(cm.group(2))
        for cm in re.finditer(r'val\s+([a-zA-Z0-9_]+)\s*=\s*([A-Z_]+)\b', body):
            if cm.group(2) in consts: colvals[cm.group(1)] = hexnorm(consts[cm.group(2)])

        def num(tok):
            tok = tok.strip()
            return float(floats[tok]) if tok in floats else float(tok.rstrip("f"))

        def color(tok):
            tok = tok.strip()
            if tok == "Color.TRANSPARENT": return "transparent"
            if tok == "accent": return "@accent"        # resolved per-console later
            if tok in colvals: return colvals[tok]
            if tok in consts: return hexnorm(consts[tok])
            pm = re.match(r'Color\.parseColor\("(#[0-9A-Fa-f]{6,8})"\)', tok)
            if pm: return hexnorm(pm.group(1))
            return "transparent"

        controls = []
        idx = 0
        while True:
            k = body.find("ControlDef(", idx)
            if k == -1: break
            depth, j = 1, k + len("ControlDef(")
            while j < len(body) and depth:
                depth += (body[j] == "(") - (body[j] == ")")
                j += 1
            block, idx = body[k:j], j
            strings = re.findall(r'"((?:[^"\\]|\\.)*)"', block)
            cid = strings[0] if strings else "?"
            label = strings[1] if len(strings) > 1 else ""
            typ = re.search(r"ControlType\.([A-Z_]+)", block).group(1)
            shape = re.search(r"ControlShape\.([A-Z_]+)", block).group(1)
            def field(f):
                fm = re.search(re.escape(f) + r"\s*=\s*([a-zA-Z0-9_.]+)f?\b", block)
                return num(fm.group(1)) if fm else 0.0
            def colorfield(f):
                fm = re.search(f + r"\s*=\s*(Color\.parseColor\(\"[^\"]*\"\)|Color\.[A-Z]+|[a-zA-Z_][a-zA-Z0-9_]*)", block)
                return color(fm.group(1)) if fm else "transparent"
            controls.append({"id": cid, "type": typ, "label": label,
                             "x": field("x"), "y": field("y"), "size": field("size"),
                             "shape": shape,
                             "fill": colorfield("fillColor"), "labelColor": colorfield("labelColor"),
                             "stroke": colorfield("strokeColor"), "plate": colorfield("plateColor")})
        return controls

    cache, out = {}, []
    for console, fn in console_fn.items():
        if fn not in cache: cache[fn] = parse_fn(fn)
        acc = enum.get(console, {}).get("accent", "#888888")
        controls = []
        for c in cache[fn]:
            d = dict(c)
            for k in ("fill", "labelColor", "stroke", "plate"):
                if d[k] == "@accent": d[k] = acc
            controls.append(d)
        out.append({"console": console,
                    "display": enum.get(console, {}).get("display", console),
                    "maker": "", "year": year.get(console, 0),
                    "body": enum.get(console, {}).get("body", "#222222"),
                    "controls": controls})
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"))
    print(f"{len(out)} consoles, {len(cache)} distinct layouts -> {sys.argv[2]}")

if __name__ == "__main__":
    main()
