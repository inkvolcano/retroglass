#!/usr/bin/env python3
"""Render each console's touch-controller layout to an SVG preview, straight from the
app's own dumped coordinates (ControllerDefs.dumpLayoutsJson). Geometry mirrors
controller/LayoutPreview.kt exactly. Emits an HTML section inserted into README.html
between the <!--LAYOUTS_START--> / <!--LAYOUTS_END--> markers.

Usage: python scripts/gen_layout_previews.py <layout_dump.json> <README.html>
"""
import json, sys, math, html

W, H = 300.0, 426.0          # portrait-split aspect H/W = 1.42
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

def render_svg(entry):
    body = entry["body"]
    parts = [f'<svg viewBox="0 0 {W:.0f} {H:.0f}" xmlns="http://www.w3.org/2000/svg" '
             f'class="lp-svg" role="img">']
    parts.append(f'<rect x="0" y="0" width="{W:.0f}" height="{H:.0f}" rx="14" fill="{body}"/>')
    parts.append(f'<rect x="0" y="0" width="{W:.0f}" height="{H*0.18:.0f}" fill="#FFFFFF" fill-opacity="0.07"/>')
    for d in entry["controls"]:
        if d["id"] == "_menu":
            continue
        parts.append(draw_control(d))
    parts.append("</svg>")
    return "".join(parts)

def main():
    dump_path, readme_path = sys.argv[1], sys.argv[2]
    data = json.load(open(dump_path, encoding="utf-8"))

    # Group consoles that share an identical control list (same geometry+colors).
    groups = []  # list of {sig, entry, names}
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
    for g in groups:
        svg = render_svg(g["entry"])
        names = " · ".join(g["names"])
        cards.append(
            f'<figure class="lp-card">{svg}'
            f'<figcaption class="lp-cap">{esc(names)}</figcaption></figure>'
        )

    section = (
        '<!--LAYOUTS_START-->\n'
        '<h2 id="layouts">Controller layouts</h2>\n'
        '<p class="lp-note">Rendered from the app’s actual layout coordinates '
        '(portrait, phone-as-controller). Systems that share a pad are grouped. '
        f'{len(groups)} distinct layouts across {len(data)} systems.</p>\n'
        '<style>\n'
        '.lp-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:18px;margin:18px 0}\n'
        '.lp-card{margin:0;display:flex;flex-direction:column;align-items:center;'
        'background:#00000010;border:1px solid #8884;border-radius:12px;padding:10px}\n'
        '.lp-svg{width:100%;max-width:170px;height:auto;border-radius:10px;display:block}\n'
        '.lp-cap{margin-top:8px;font-size:13px;line-height:1.35;text-align:center;opacity:.85}\n'
        '.lp-note{opacity:.75;font-size:14px}\n'
        '</style>\n'
        '<div class="lp-grid">\n' + "\n".join(cards) + '\n</div>\n'
        '<!--LAYOUTS_END-->'
    )

    src = open(readme_path, encoding="utf-8").read()
    start, end = "<!--LAYOUTS_START-->", "<!--LAYOUTS_END-->"
    if start in src and end in src:
        pre = src[:src.index(start)]
        post = src[src.index(end)+len(end):]
        out = pre + section + post
    else:
        # Insert before </body> the first time.
        out = src.replace("</body>", section + "\n</body>")
    open(readme_path, "w", encoding="utf-8").write(out)

    # Also write a standalone file for quick validation.
    standalone = ("<!doctype html><meta charset=utf-8><title>Layout previews</title>"
                  "<body style='background:#141419;color:#eee;font-family:Arial'>"
                  + section.replace("<!--LAYOUTS_START-->","").replace("<!--LAYOUTS_END-->","")
                  + "</body>")
    open(sys.argv[3] if len(sys.argv) > 3 else "layouts_preview.html", "w", encoding="utf-8").write(standalone)
    print(f"{len(groups)} distinct layouts, {len(data)} systems. Section written.")

if __name__ == "__main__":
    main()
