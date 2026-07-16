#!/usr/bin/env python3
"""Device-free: parse control GEOMETRY (id, shape, x, y, size, plate) for every console
straight from Console.kt, emitting the same JSON shape as dumpLayoutsJson (colors are
placeholders — only geometry matters for overlap checking). Lets check_overlaps.py run
without a phone attached."""
import re, json, sys

def main():
    src = open(sys.argv[1], encoding="utf-8").read()

    # 1) console -> layout function (from the baseControls when-branch)
    m = re.search(r"fun baseControls\(console: Console\): List<ControlDef> = when \(console\) \{(.*?)\n    \}", src, re.S)
    branch = m.group(1)
    console_fn = {}
    for line in branch.splitlines():
        lm = re.match(r"\s*(Console\.[A-Z0-9_,\s.]+?)\s*->\s*([a-zA-Z0-9_]+)\(", line)
        if not lm: continue
        fn = lm.group(2)
        for c in re.findall(r"Console\.([A-Z0-9_]+)", lm.group(1)):
            console_fn[c] = fn

    # 2) extract each layout function body
    def fn_body(name):
        m = re.search(r"private fun " + re.escape(name) + r"\([^)]*\): List<ControlDef>[^\n]*\{?", src)
        if not m: return None
        start = m.end()
        depth, i, body = 0, start, []
        # capture until the function's closing brace at depth 0 (best effort: to next 'private fun ')
        nxt = src.find("private fun ", start)
        return src[start: nxt if nxt != -1 else len(src)]

    # resolve simple float locals: val NAME = 0.NNf
    def parse_controls(body):
        floats = dict(re.findall(r"val\s+([a-zA-Z0-9_]+)\s*=\s*([0-9.]+)f\b", body))
        def num(tok):
            tok = tok.strip()
            if tok in floats: return float(floats[tok])
            return float(tok.rstrip("f"))
        controls = []
        # each ControlDef(...) block
        idx = 0
        while True:
            k = body.find("ControlDef(", idx)
            if k == -1: break
            # find matching close paren
            depth, j = 0, k + len("ControlDef(")
            depth = 1
            while j < len(body) and depth:
                if body[j] == "(": depth += 1
                elif body[j] == ")": depth -= 1
                j += 1
            block = body[k:j]
            idx = j
            strings = re.findall(r'"((?:[^"\\]|\\.)*)"', block)
            cid = strings[0] if strings else "?"
            label = strings[1] if len(strings) > 1 else ""
            shape = (re.search(r"ControlShape\.([A-Z_]+)", block) or [None, "CIRCLE"])[1] \
                    if re.search(r"ControlShape\.([A-Z_]+)", block) else "CIRCLE"
            shape = re.search(r"ControlShape\.([A-Z_]+)", block).group(1)
            def field(f):
                fm = re.search(re.escape(f) + r"\s*=\s*([a-zA-Z0-9_.]+)f?\b", block)
                return num(fm.group(1)) if fm else None
            x = field("x"); y = field("y"); size = field("size")
            plate = "transparent" if "plateColor" not in block else "#000000"
            controls.append({"id": cid, "label": label, "shape": shape,
                             "x": x, "y": y, "size": size,
                             "fill": "#888888", "labelColor": "#eeeeee",
                             "stroke": "transparent", "plate": plate})
        return controls

    bodies = {}
    out = []
    for console, fn in console_fn.items():
        if fn not in bodies:
            bodies[fn] = parse_controls(fn_body(fn) or "")
        out.append({"console": console, "display": console, "maker": "", "year": 0,
                    "body": "#222222", "controls": bodies[fn]})
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"))
    print(f"parsed {len(out)} consoles, {len(bodies)} distinct layouts")

if __name__ == "__main__":
    main()
