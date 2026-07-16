#!/usr/bin/env python3
"""Illustrate the PORTRAIT-WITH-SCREEN layout: on a phone held upright the game shows as a band
across the top and the touch controller sits in the region below it. The controller uses the same
base layout as the portrait gallery; this just shows it in context. Renders a representative set of
consoles and inserts a section into README.html between <!--PORTRAIT_SPLIT_START/END-->.

The controller REGION aspect = controller_fraction × phone(H/W). The base pads are authored for
H/W≈1.42 and stay overlap-free down to ≈1.29; below that (squat regions on 18:9 phones with a big
game band) arcs/diagonals collide — so the app must keep the controller region ≥≈1.29 H/W by giving
the controller more height on squarer phones (a smaller game band).

Usage: python scripts/gen_portrait_split.py <layout_dump.json> <README.html> [standalone.html]
"""
import json, sys, html
import gen_layout_previews as P

# Card = an upright phone. Representative 19.5:9 phone, H/W = 2.17. Controller gets 62% of the
# height → region H/W = 0.62 × 2.17 ≈ 1.35 (clean). Game band is the top 38%.
CW = 300.0
PHONE_HW = 2.17
CH = CW * PHONE_HW               # phone height
GAME_FRAC = 0.38
GAME_H = CH * GAME_FRAC
CTRL_H = CH - GAME_H             # controller region height (region H/W = CTRL_H/CW ≈ 1.35)

REPRESENTATIVE = ["NES", "SNES", "Mega Drive", "PlayStation", "Nintendo 64",
                  "Game Boy", "Dreamcast", "Saturn"]

def esc(t): return html.escape(t, quote=True)

def render_card(entry):
    # draw the controller into the bottom region by pointing the shared renderer at CW×CTRL_H
    P.W, P.H, P.MIN = CW, CTRL_H, min(CW, CTRL_H)
    controls = "".join(P.draw_control(d) for d in entry["controls"] if d["id"] != "_menu")
    body = entry["body"]
    parts = [f'<svg viewBox="0 0 {CW:.0f} {CH:.0f}" xmlns="http://www.w3.org/2000/svg" class="ps-svg" role="img">']
    parts.append(f'<rect x="1" y="1" width="{CW-2:.0f}" height="{CH-2:.0f}" rx="26" fill="#0c0d10" stroke="#8884" stroke-width="2"/>')
    # game band
    parts.append(f'<rect x="14" y="14" width="{CW-28:.0f}" height="{GAME_H-22:.0f}" rx="8" fill="#000" '
                 f'stroke="{body}" stroke-opacity="0.6" stroke-width="2" stroke-dasharray="7 6"/>')
    parts.append(f'<text x="{CW/2:.0f}" y="{GAME_H/2:.0f}" font-size="15" fill="#5A6070" text-anchor="middle" '
                 f'dominant-baseline="central" font-family="Arial, sans-serif" font-weight="bold">GAME SCREEN</text>')
    # controller region (base pad) translated below the game band
    parts.append(f'<g transform="translate(0,{GAME_H:.0f})">{controls}</g>')
    parts.append("</svg>")
    return "".join(parts)

def main():
    dump, readme = sys.argv[1], sys.argv[2]
    data = json.load(open(dump, encoding="utf-8"))
    by_name = {e["display"]: e for e in data}
    cards = []
    for name in REPRESENTATIVE:
        e = by_name.get(name)
        if not e:
            continue
        cards.append(f'<figure class="ps-card">{render_card(e)}<figcaption class="ps-cap">{esc(name)}</figcaption></figure>')
    section = (
        '<!--PORTRAIT_SPLIT_START-->\n'
        '<h2 id="layouts-portrait-screen">Controller layouts — portrait with screen</h2>\n'
        '<p class="ps-note">Phone held upright, playing on the phone: the game is a band across the top and the '
        'touch pad fills the region below (the same per-console pad as the portrait gallery). '
        '<strong>Design rule:</strong> the pad region is kept at <strong>H/W ≈ 1.35–1.42</strong> — on squarer '
        '(18:9) phones the app shrinks the game band so the pad keeps that shape; letting the region get shorter '
        'than ≈1.29 squashes arcs/diagonals and buttons start to touch. Portrait <em>without</em> a screen (pad '
        'fills the whole upright phone) has even more room and is clean at every phone aspect. Representative set '
        'shown; all 31 pads are in the portrait gallery above.</p>\n'
        '<style>\n'
        '.ps-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:18px;margin:18px 0}\n'
        '.ps-card{margin:0;display:flex;flex-direction:column;align-items:center;background:#00000010;border:1px solid #8884;border-radius:12px;padding:10px}\n'
        '.ps-svg{width:100%;max-width:150px;height:auto;border-radius:12px;display:block}\n'
        '.ps-cap{margin-top:8px;font-size:13px;text-align:center;opacity:.85}\n'
        '.ps-note{opacity:.75;font-size:14px}\n'
        '</style>\n'
        '<div class="ps-grid">\n' + "\n".join(cards) + '\n</div>\n'
        '<!--PORTRAIT_SPLIT_END-->'
    )
    src = open(readme, encoding="utf-8").read()
    s, e = "<!--PORTRAIT_SPLIT_START-->", "<!--PORTRAIT_SPLIT_END-->"
    if s in src and e in src:
        out = src[:src.index(s)] + section + src[src.index(e)+len(e):]
    elif "<!--LAYOUTS_LS_END-->" in src:
        a = "<!--LAYOUTS_LS_END-->"; idx = src.index(a) + len(a); out = src[:idx] + "\n" + section + src[idx:]
    else:
        out = src.replace("</body>", section + "\n</body>")
    open(readme, "w", encoding="utf-8").write(out)
    if len(sys.argv) > 3:
        open(sys.argv[3], "w", encoding="utf-8").write(
            "<!doctype html><meta charset=utf-8><body style='background:#141419;color:#eee;font-family:Arial'>"
            + section.replace("<!--PORTRAIT_SPLIT_START-->", "").replace("<!--PORTRAIT_SPLIT_END-->", "") + "</body>")
    print(f"portrait-split section written ({len(cards)} cards)")

if __name__ == "__main__":
    main()
