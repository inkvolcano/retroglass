#!/usr/bin/env python3
"""Render each console's touch-controller layout in LANDSCAPE (the overlay you see when the
game is on an external display or the phone is held sideways), straight from the app's own
dumped coordinates (ControllerDefs.dumpLayoutsJson). Geometry mirrors controller/LayoutPreview.kt
and ControllerView exactly — the same normalized (x,y), radius = size/2 x min(w,h), per-shape
half-extents and on-screen clamping — only the container aspect changes (full landscape screen,
2.44:1, matching the reference Galaxy Z Flip6 at 2640x1080). Controls sit on a dark backdrop
because in landscape the pad body is a transparent overlay over the game.

Emits an HTML section inserted into README.html between the
<!--LAYOUTS_LS_START--> / <!--LAYOUTS_LS_END--> markers (added after the portrait gallery on
first run).

Usage: python scripts/gen_layout_previews_landscape.py <layout_dump.json> <README.html> [standalone.html]
"""
import json, sys, html
import gen_landscape_frame as _glf   # reuse the full-controller (screen=False) layout transform

# Full landscape screen aspect (Flip6 2640x1080 = 2.444:1), scaled down 3x.
W, H = 880.0, 360.0
MIN = min(W, H)

def darken(hexc):
    r = max(0, int(hexc[1:3], 16) - 30)
    g = max(0, int(hexc[3:5], 16) - 30)
    b = max(0, int(hexc[5:7], 16) - 30)
    return f"#{r:02X}{g:02X}{b:02X}"

def radius(d):      return d["size"] / 2.0 * MIN
def has_plate(d):   return d["plate"] != "transparent"

def half_x(d):
    s = d["shape"]; r = radius(d)
    if s == "BAR":  return d["size"] * W / 2.0
    if s == "PILL": return r * 1.85
    if s == "CIRCLE": return r * (1.28 if has_plate(d) else 1.0)
    return r

def half_y(d):
    s = d["shape"]; r = radius(d)
    if s == "BAR":  return MIN * 0.062
    if s == "PILL": return r * 0.8
    if s == "CIRCLE": return r * (1.28 if has_plate(d) else 1.0)
    return r

def clamp(raw, half, extent):
    if extent <= 0: return raw
    if half * 2.0 >= extent: return extent / 2.0
    return max(half, min(extent - half, raw))

def esc(t): return html.escape(t, quote=True)

def txt(cx, cy, s, size, color, weight="bold"):
    if not s: return ""
    return (f'<text x="{cx:.1f}" y="{cy:.1f}" font-size="{size:.1f}" fill="{color}" '
            f'text-anchor="middle" dominant-baseline="central" '
            f'font-family="Arial, sans-serif" font-weight="{weight}">{esc(s)}</text>')

def draw_dpad(cx, cy, r, d):
    out = []
    fill = d["fill"]; lab = d["labelColor"]
    armW = r * 0.62; half = armW / 2.0
    gap = r * 0.06 if d["shape"] == "PSX_CROSS" else 0.0
    corner = armW * 0.28
    def rrect(x0, y0, x1, y1, rad, col):
        return (f'<rect x="{x0:.1f}" y="{y0:.1f}" width="{(x1-x0):.1f}" height="{(y1-y0):.1f}" '
                f'rx="{rad:.1f}" ry="{rad:.1f}" fill="{col}"/>')
    out.append(rrect(cx - r, cy - half, cx - half - gap, cy + half, corner, fill))
    out.append(rrect(cx + half + gap, cy - half, cx + r, cy + half, corner, fill))
    out.append(rrect(cx - half, cy - r, cx + half, cy - half - gap, corner, fill))
    out.append(rrect(cx - half, cy + half + gap, cx + half, cy + r, corner, fill))
    out.append(rrect(cx - half, cy - half, cx + half, cy + half, corner * 0.6, fill))
    if d["shape"] == "PSX_CROSS":
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{half*0.55:.1f}" fill="{darken(fill)}"/>')
    ar = armW * 0.30
    def arrow(px, py, dx, dy):
        perpx, perpy = -dy, dx
        p1 = (px + dx*ar, py + dy*ar)
        p2 = (px - dx*ar*0.6 + perpx*ar, py - dy*ar*0.6 + perpy*ar)
        p3 = (px - dx*ar*0.6 - perpx*ar, py - dy*ar*0.6 - perpy*ar)
        return (f'<polygon points="{p1[0]:.1f},{p1[1]:.1f} {p2[0]:.1f},{p2[1]:.1f} '
                f'{p3[0]:.1f},{p3[1]:.1f}" fill="{lab}"/>')
    out.append(arrow(cx - r + armW*0.55, cy, -1, 0))
    out.append(arrow(cx + r - armW*0.55, cy, 1, 0))
    out.append(arrow(cx, cy - r + armW*0.55, 0, -1))
    out.append(arrow(cx, cy + r - armW*0.55, 0, 1))
    return "".join(out)

def draw_control(d):
    cx = clamp(d["x"] * W, half_x(d), W)
    cy = clamp(d["y"] * H, half_y(d), H)
    r = radius(d); s = d["shape"]; out = []
    if s == "CIRCLE":
        if has_plate(d):
            pr = r * 1.28
            out.append(f'<rect x="{cx-pr:.1f}" y="{cy-pr:.1f}" width="{2*pr:.1f}" height="{2*pr:.1f}" '
                       f'rx="{pr*0.25:.1f}" ry="{pr*0.25:.1f}" fill="{d["plate"]}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{d["fill"]}"/>')
        if d["stroke"] != "transparent":
            out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r*0.94:.1f}" fill="none" '
                       f'stroke="{d["stroke"]}" stroke-width="{r*0.12:.1f}"/>')
        out.append(txt(cx, cy, d["label"], min(MIN*0.082, r*1.25, r*3.0/max(1,len(d["label"]))), d["labelColor"]))
    elif s == "PILL":
        pw = r * 1.85; ph = r * 0.8
        out.append(f'<rect x="{cx-pw:.1f}" y="{cy-ph:.1f}" width="{2*pw:.1f}" height="{2*ph:.1f}" '
                   f'rx="{r:.1f}" ry="{r:.1f}" fill="{d["fill"]}"/>')
        out.append(txt(cx, cy, d["label"], r*(0.62 if len(d["label"])>2 else 0.9), d["labelColor"]))
    elif s == "BAR":
        hl = d["size"] * W / 2.0; ht = MIN * 0.062
        out.append(f'<rect x="{cx-hl:.1f}" y="{cy-ht:.1f}" width="{2*hl:.1f}" height="{2*ht:.1f}" '
                   f'rx="{ht:.1f}" ry="{ht:.1f}" fill="{d["fill"]}"/>')
        out.append(txt(cx, cy, d["label"], ht*1.15, d["labelColor"]))
    elif s in ("CROSS", "PSX_CROSS"):
        out.append(draw_dpad(cx, cy, r, d))
    elif s == "STICK":
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{d["fill"]}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r*0.98:.1f}" fill="none" '
                   f'stroke="#FFFFFF" stroke-opacity="0.13" stroke-width="{r*0.05:.1f}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r*0.52:.1f}" fill="{darken(d["fill"])}"/>')
        if d["label"]:
            out.append(txt(cx, cy + r*1.28, d["label"], r*0.3, d["labelColor"], "normal"))
    return "".join(out)

# Keep-fixed priority when resolving overlaps (higher = don't move it): D-pad, then sticks, then bars.
_PRIO = {"CROSS": 4, "PSX_CROSS": 4, "STICK": 3, "BAR": 2, "PILL": 1, "CIRCLE": 1}

def deoverlap(controls):
    """Nudge overlapping controls apart vertically (bounding-box based). The higher-priority
    control of a colliding pair stays; the lower-priority one moves. Base coords stretched into
    landscape occasionally collide (e.g. D-pad vs a shoulder), and the app never had these clash."""
    out = [dict(c) for c in controls if c["id"] != "_menu"]
    for _ in range(40):
        moved = False
        for i in range(len(out)):
            for j in range(i + 1, len(out)):
                a, b = out[i], out[j]
                hxa, hya = half_x(a) / W, half_y(a) / H
                hxb, hyb = half_x(b) / W, half_y(b) / H
                ox = (hxa + hxb) - abs(a["x"] - b["x"])
                oy = (hya + hyb) - abs(a["y"] - b["y"])
                if ox > 0 and oy > 0:
                    sgn = 1.0 if (a["y"] - b["y"]) >= 0 else -1.0
                    pa, pb = _PRIO.get(a["shape"], 1), _PRIO.get(b["shape"], 1)
                    if pa > pb:      # keep a, move b
                        b["y"] = min(0.95, max(0.05, b["y"] - sgn * (oy + 3.0 / H)))
                    elif pb > pa:    # keep b, move a
                        a["y"] = min(0.95, max(0.05, a["y"] + sgn * (oy + 3.0 / H)))
                    else:
                        h = (oy + 3.0 / H) / 2
                        a["y"] = min(0.95, max(0.05, a["y"] + sgn * h))
                        b["y"] = min(0.95, max(0.05, b["y"] - sgn * h))
                    moved = True
        if not moved:
            break
    return out

def render_svg(entry, gid):
    parts = [f'<svg viewBox="0 0 {W:.0f} {H:.0f}" xmlns="http://www.w3.org/2000/svg" '
             f'class="lpl-svg" role="img">']
    # dark "screen" backdrop the pad overlays in landscape, with a faint tint of the body colour
    parts.append(f'<defs><linearGradient id="lg{gid}" x1="0" y1="0" x2="0" y2="1">'
                 f'<stop offset="0" stop-color="#16171C"/><stop offset="1" stop-color="#0A0A0D"/>'
                 f'</linearGradient></defs>')
    parts.append(f'<rect x="0" y="0" width="{W:.0f}" height="{H:.0f}" rx="16" fill="url(#lg{gid})"/>')
    parts.append(f'<rect x="0.5" y="0.5" width="{W-1:.0f}" height="{H-1:.0f}" rx="16" fill="none" '
                 f'stroke="{entry["body"]}" stroke-opacity="0.5" stroke-width="2"/>')
    for d in _glf.landscape_frame(entry["controls"], screen=False):
        if d["id"] == "_menu":
            continue
        parts.append(draw_control(d))
    parts.append("</svg>")
    return "".join(parts)

def main():
    dump_path, readme_path = sys.argv[1], sys.argv[2]
    data = json.load(open(dump_path, encoding="utf-8"))

    # Group consoles that share an identical control list (same geometry + colours).
    groups = []
    for e in data:
        sig = json.dumps(e["controls"], sort_keys=True)
        g = next((g for g in groups if g["sig"] == sig), None)
        if g is None:
            groups.append({"sig": sig, "entry": e, "names": [e["display"]], "min_year": e["year"]})
        else:
            g["names"].append(e["display"])
            g["min_year"] = min(g["min_year"], e["year"])
    groups.sort(key=lambda g: (g["min_year"], g["entry"]["display"]))

    cards = []
    for i, g in enumerate(groups):
        svg = render_svg(g["entry"], i)
        names = " · ".join(g["names"])
        cards.append(
            f'<figure class="lpl-card">{svg}'
            f'<figcaption class="lpl-cap">{esc(names)}</figcaption></figure>'
        )

    section = (
        '<!--LAYOUTS_LS_START-->\n'
        '<h2 id="layouts-landscape">Controller layouts — landscape (full pad, no screen)</h2>\n'
        '<p class="lpl-note">The <strong>external-display</strong> layout: the game is on the glasses/monitor '
        'so the whole phone is the pad — D-pad and sticks on the left, the console’s buttons on the right '
        '(spread wider and larger than the with-screen frame), Start/Select in the centre. D-pad/stick slots, '
        'the symmetric face cluster, the workable minimum sizes and the zero-overlap guarantee all match the '
        'with-screen frame. '
        f'{len(groups)} distinct layouts across {len(data)} systems.</p>\n'
        '<style>\n'
        '.lpl-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:18px;margin:18px 0}\n'
        '.lpl-card{margin:0;display:flex;flex-direction:column;align-items:center;'
        'background:#00000010;border:1px solid #8884;border-radius:12px;padding:10px}\n'
        '.lpl-svg{width:100%;height:auto;border-radius:12px;display:block}\n'
        '.lpl-cap{margin-top:8px;font-size:13px;line-height:1.35;text-align:center;opacity:.85}\n'
        '.lpl-note{opacity:.75;font-size:14px}\n'
        '</style>\n'
        '<div class="lpl-grid">\n' + "\n".join(cards) + '\n</div>\n'
        '<!--LAYOUTS_LS_END-->'
    )

    src = open(readme_path, encoding="utf-8").read()
    start, end = "<!--LAYOUTS_LS_START-->", "<!--LAYOUTS_LS_END-->"
    if start in src and end in src:
        pre = src[:src.index(start)]
        post = src[src.index(end)+len(end):]
        out = pre + section + post
    elif "<!--LAYOUTS_END-->" in src:
        # First run: drop the landscape gallery right after the portrait one.
        anchor = "<!--LAYOUTS_END-->"
        idx = src.index(anchor) + len(anchor)
        out = src[:idx] + "\n" + section + src[idx:]
    else:
        out = src.replace("</body>", section + "\n</body>")
    open(readme_path, "w", encoding="utf-8").write(out)

    if len(sys.argv) > 3:
        standalone = ("<!doctype html><meta charset=utf-8><title>Landscape layout previews</title>"
                      "<body style='background:#141419;color:#eee;font-family:Arial'>"
                      + section.replace("<!--LAYOUTS_LS_START-->","").replace("<!--LAYOUTS_LS_END-->","")
                      + "</body>")
        open(sys.argv[3], "w", encoding="utf-8").write(standalone)
    print(f"{len(groups)} distinct layouts, {len(data)} systems. Landscape section written.")

if __name__ == "__main__":
    main()
