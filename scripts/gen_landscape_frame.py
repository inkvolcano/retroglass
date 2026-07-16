#!/usr/bin/env python3
"""Render each console's LANDSCAPE-FRAME touch layout: the game screen kept CLEAR in a centred
window, every control pushed into the left/right side columns that frame it (no control over the
screen, no controls overlapping each other). This is the layout for the "On phone — landscape"
screen mode. Geometry mirrors controller/LayoutPreview.kt; the frame transform below is the piece
under review — tune the columns / sizes here and regenerate to steer the arrangement.

Usage: python scripts/gen_landscape_frame.py <layout_dump.json> <README.html|-> [standalone.html]
Pass '-' as the README to only write the standalone (for quick visual iteration).
"""
import json, sys, html, math

W, H = 880.0, 360.0
# Button sizing uses min(W, H) but CAPPED at W*SIZE_CAP, so on a tall/near-square/tablet landscape
# display (foldable unfolded, 16:10 tablet) the buttons stay phone-sized instead of ballooning with
# the taller short-side. On a normal wide phone the cap doesn't bind (H is already < W*SIZE_CAP).
SIZE_CAP = 0.46
def size_min():
    return min(W, H, W * SIZE_CAP)
MIN = size_min()

# The clear game window (a 4:3 picture letterboxed at full height ≈ 0.545 wide; we keep the
# control columns clear to ~0.24 / 0.76 so nothing ever touches the picture).
GAME_L, GAME_R = 0.255, 0.745
# Where control CENTRES may sit (leave a button-radius margin before the screen).
LCOL = (0.035, 0.205)
RCOL = (0.795, 0.965)
YSPAN = 1.16   # expand vertical placement to use the short landscape height
SIZE_K = 0.82  # trim sizes so the narrow columns don't collide

# Fixed slots so the primary directional controls match across every console (inset from the
# outer edge, well clear of the screen). D-pad and sticks anchor here; other controls fit around.
DPAD_POS = (0.118, 0.44)     # x, y for every D-pad
DPAD_SIZE = 0.36             # one D-pad size for all
LSTICK_POS = (0.118, 0.805)  # left analog, under the D-pad
RSTICK_POS = (0.882, 0.805)  # right analog, under the face buttons
STICK_SIZE = 0.24
# The right-hand face-button cluster (A/B, the colour diamond, II/I, FIRE, keypad…) anchors here,
# centred and narrow. ASPECT rebuilds the cluster in square pixels so up/down spacing equals
# left/right spacing (the base coords are authored for the taller portrait region).
FACE_POS = (0.868, 0.44)   # mirror of the D-pad, pulled in a touch so the cluster keeps a right-edge margin
FACE_COLHALF = 0.058   # max horizontal half-extent of the face cluster centres
FACE_VHALF = 0.20      # max vertical half-extent
FACE_KMAX = 380.0      # cap the spread of small (≤3-button) clusters so they stay compact
FACE_MIN = 0.115       # minimum face-button diameter (fraction of short side) so buttons stay tappable (~7mm)
ASPECT = 1.42          # portrait touch-region height/width the base layouts target

SHOULDER_IDS = {"l", "r", "l1", "l2", "r1", "r2", "lt", "rt", "zl", "zr"}
SYS_IDS = {"start", "select", "menu", "pause", "option", "reset", "mode", "run", "coin", "sel"}

def role(c):
    if c["shape"] == "STICK": return "stick"
    if c["shape"] in ("CROSS", "PSX_CROSS"): return "dpad"
    if c["shape"] == "BAR" or c["id"].lower() in SHOULDER_IDS: return "shoulder"
    if c["id"].lower() in SYS_IDS: return "system"
    return "face"

def landscape_frame(controls, screen=True):
    """Base (portrait) coords -> landscape layout. With screen=True the pad FRAMES a centred game
    window (on-phone landscape play). With screen=False it's a full-screen pad for external-display
    play: controls spread wider, D-pad/sticks are larger, and Start/Select move into the freed
    centre. Either way the D-pad and sticks snap to fixed slots so directional controls line up,
    the face cluster is centred + aspect-corrected, buttons keep a workable minimum size, and a
    bounding-box de-overlap pass guarantees nothing collides."""
    if screen:
        DP, DSZ = DPAD_POS, DPAD_SIZE
        FP, FCH, FVH = FACE_POS, FACE_COLHALF, FACE_VHALF
        LS, RS, SSZ = LSTICK_POS, RSTICK_POS, STICK_SIZE
        gl, gr = GAME_L, GAME_R
        lcol, rcol = LCOL, RCOL
    else:
        DP, DSZ = (0.185, 0.44), 0.42
        FP, FCH, FVH = (0.815, 0.44), 0.085, 0.20
        LS, RS, SSZ = (0.185, 0.83), (0.815, 0.83), 0.27
        gl, gr = 0.44, 0.56
        lcol, rcol = (0.05, 0.30), (0.70, 0.95)
    EDGE = 0.032   # keep every control ≥3.2% of the short/long side off each screen edge
    out = []
    anchored = set()
    lxs = [c["x"] for c in controls if c["x"] < 0.5] or [0.25]
    rxs = [c["x"] for c in controls if c["x"] >= 0.5] or [0.75]
    lmin, lmax = min(lxs), max(lxs)
    rmin, rmax = min(rxs), max(rxs)
    margin = 8.0 / W

    # Right-hand ACTION-button cluster (≤4: A/B, the colour diamond, II/I, FIRE): centre it on FP
    # with aspect-corrected, column-fitted spacing. Bigger sets (6-button, 12-key) keep the column
    # mapping, which already lays them out cleanly.
    rface = [i for i, c in enumerate(controls) if role(c) == "face" and c["x"] >= 0.5]
    anchor_face = 1 <= len(rface) <= 4
    if anchor_face:
        cx0 = sum(controls[i]["x"] for i in rface) / len(rface)
        cy0 = sum(controls[i]["y"] for i in rface) / len(rface)
        mdx = max((abs(controls[i]["x"] - cx0) for i in rface), default=0.0)
        mdy = max((abs(controls[i]["y"] - cy0) for i in rface), default=0.0)
        sx = (FCH * W) / mdx if mdx > 1e-6 else 1e9
        sy = (FVH * H) / (mdy * ASPECT) if mdy > 1e-6 else 1e9
        fscale = min(sx, sy) if len(rface) == 4 else min(sx, sy, FACE_KMAX)
        # …but always spread enough that the (floored) button sizes don't overlap each other.
        need = 0.0
        for a_ in range(len(rface)):
            for b_ in range(a_ + 1, len(rface)):
                i, j = rface[a_], rface[b_]
                dn = math.hypot(controls[i]["x"] - controls[j]["x"],
                                (controls[i]["y"] - controls[j]["y"]) * ASPECT)
                if dn < 1e-6:
                    continue
                ri = max(controls[i]["size"] * SIZE_K, FACE_MIN) / 2 * size_min()
                rj = max(controls[j]["size"] * SIZE_K, FACE_MIN) / 2 * size_min()
                need = max(need, (ri + rj) * 1.06 / dn)
        fscale = max(fscale, need)

    # Without a screen, Start/Select/menu drop into the freed centre-bottom.
    sysc = set(i for i, c in enumerate(controls) if role(c) == "system") if not screen else set()

    # N64 has too many face buttons (Z, A, B, four C's) to auto-cluster, and column-mapping loses
    # its shape. Hand-author the right side: the C-button DIAMOND up top, B/A slanted below it, Z
    # tucked top-left, and the analog stick in the bottom-right corner.
    ids = {c["id"] for c in controls}
    n64 = {"c_up", "c_down", "c_left", "c_right", "z"} <= ids
    n64pos = {}
    if n64:
        sm = size_min()
        AY = 0.44
        sx, sy = 0.098 * sm / W, 0.098 * sm / H   # step along each row — equal pixels ⇒ 45° slant
        ry = 0.19 * sm / H                         # vertical gap between the two rows
        # A/B are the leftmost of each row; the cluster runs A..(A+2·sx). Centre it horizontally in
        # the gap between the game window's right edge and the phone's right edge.
        gap_l = (GAME_R if screen else 0.56)
        AX = (gap_l + 1.0) / 2 - 1.5 * sx           # centre the (now 4-column-wide) cluster in the gap
        # Two 45°-slanted rows, the lower row staggered one column right + up so B lines up under the
        # top row's first C:  A · C · C (top)  and  ·B · C · C (bottom).
        n64pos = {
            "start":   (AX + 0.01,  0.10),
            "z":       (AX - 0.005, 0.24),
            "a":       (AX,          AY),
            "c_up":    (AX + sx,     AY - sy),
            "c_right": (AX + 2 * sx, AY - 2 * sy),
            "b":       (AX + sx,     AY + ry - sy),
            "c_left":  (AX + 2 * sx, AY + ry - 2 * sy),
            "c_down":  (AX + 3 * sx, AY + ry - 3 * sy),
        }
        n64stick = (AX + 1.5 * sx, 0.81)            # stick centred under the button cluster

    for i, c in enumerate(controls):
        d = dict(c)
        left = c["x"] < 0.5
        if n64 and c["id"] in n64pos:
            d["x"], d["y"] = n64pos[c["id"]]
            d["size"] = (c["size"] * SIZE_K if c["id"] == "start"
                         else 0.11 if c["id"] == "z"
                         else 0.092 if c["id"].startswith("c_")   # C-buttons 20% smaller than A/B
                         else 0.115)
            anchored.add(len(out)); out.append(d); continue
        if n64 and c["shape"] == "STICK":
            d["x"], d["y"] = n64stick; d["size"] = STICK_SIZE
            anchored.add(len(out)); out.append(d); continue
        if c["shape"] in ("CROSS", "PSX_CROSS"):
            d["x"], d["y"] = DP; d["size"] = DSZ; anchored.add(len(out))
        elif c["shape"] == "STICK":
            d["x"], d["y"] = (LS if left else RS); d["size"] = SSZ
            anchored.add(len(out))
        elif anchor_face and i in rface:
            dx = c["x"] - cx0; dy = c["y"] - cy0
            d["x"] = FP[0] + dx * fscale / W
            d["y"] = FP[1] + dy * ASPECT * fscale / H
            d["size"] = max(c["size"] * SIZE_K, FACE_MIN)
            anchored.add(len(out))
        elif i in sysc:
            d["x"] = 0.44 if left else 0.56
            d["y"] = 0.90
            d["size"] = c["size"] * SIZE_K
            anchored.add(len(out))
        else:
            if left:
                t = 0.5 if lmax == lmin else (c["x"] - lmin) / (lmax - lmin)
                d["x"] = lcol[0] + t * (lcol[1] - lcol[0])
            else:
                t = 0.5 if rmax == rmin else (c["x"] - rmin) / (rmax - rmin)
                d["x"] = rcol[0] + t * (rcol[1] - rcol[0])
            d["y"] = min(0.92, max(0.08, 0.5 + (c["y"] - 0.5) * YSPAN))
            d["size"] = c["size"] * SIZE_K
            if role(c) == "face":
                d["size"] = max(d["size"], FACE_MIN)
        out.append(d)

    # Keep boxes clear of the screen window (with screen), then off all four outer edges (both
    # modes) so nothing hugs the bezel where a thumb runs off or a system edge-swipe triggers.
    for idx, d in enumerate(out):
        left = d["x"] < 0.5
        hx = half_x(d) / W
        if screen:
            avail = (gl - margin) if left else (1.0 - gr - margin)
            if idx not in anchored and hx > avail:
                d["size"] *= max(0.40, avail / hx); hx = half_x(d) / W
            if left:
                d["x"] = max(hx + 0.004, min(d["x"], gl - margin - hx))
            else:
                d["x"] = min(1.0 - hx - 0.004, max(d["x"], gr + margin + hx))
        # Keep controls off the bezel. Shoulders (wide bars that reach the edge) are SHRUNK to fit
        # the margin, centred — never moved into neighbours (that broke crowded pads like the N64).
        # Start/Select are small and safe to nudge. Face/anchored keep their positions.
        if idx not in anchored and d["shape"] == "BAR":
            hx = half_x(d) / W
            fit = min(d["x"] - EDGE, 1.0 - EDGE - d["x"])
            if 0.02 < fit < hx:
                d["size"] = 2.0 * fit
        elif idx not in anchored and role(d) == "system":
            hx = half_x(d) / W; hy = half_y(d) / H
            d["x"] = min(1.0 - EDGE - hx, max(EDGE + hx, d["x"]))
            d["y"] = min(1.0 - EDGE - hy, max(EDGE + hy, d["y"]))

    # Separate residual collisions using BOUNDING BOXES (so plates/pills count) by nudging the
    # free control(s) vertically; anchored controls (D-pad, sticks, the face cluster) stay put.
    for _ in range(60):
        moved = False
        for i in range(len(out)):
            for j in range(i + 1, len(out)):
                a, b = out[i], out[j]
                if a["shape"] == "BAR" and b["shape"] == "BAR":
                    continue
                ai, bj = i in anchored, j in anchored
                if ai and bj:
                    continue
                hxa, hya = half_x(a) / W, half_y(a) / H
                hxb, hyb = half_x(b) / W, half_y(b) / H
                ox = (hxa + hxb) - abs(a["x"] - b["x"])
                oy = (hya + hyb) - abs(a["y"] - b["y"])
                if ox > 0 and oy > 0:
                    push = oy + 3.0 / H
                    sgn = 1.0 if (a["y"] - b["y"]) >= 0 else -1.0
                    if ai:
                        b["y"] = min(0.93, max(0.07, b["y"] - sgn * push))
                    elif bj:
                        a["y"] = min(0.93, max(0.07, a["y"] + sgn * push))
                    else:
                        a["y"] = min(0.93, max(0.07, a["y"] + sgn * push / 2))
                        b["y"] = min(0.93, max(0.07, b["y"] - sgn * push / 2))
                    moved = True
        if not moved:
            break
    return out

# ---- drawing (mirrors LayoutPreview.kt), identical to the other gallery generators ----

def darken(hexc):
    r = max(0, int(hexc[1:3], 16) - 30); g = max(0, int(hexc[3:5], 16) - 30); b = max(0, int(hexc[5:7], 16) - 30)
    return f"#{r:02X}{g:02X}{b:02X}"
def radius(d): return d["size"] / 2.0 * size_min()
def has_plate(d): return d["plate"] != "transparent"
def half_x(d):
    s=d["shape"]; r=radius(d)
    if s=="BAR": return d["size"]*W/2.0
    if s=="PILL": return r*1.85
    if s=="CIRCLE": return r*(1.28 if has_plate(d) else 1.0)
    return r
def half_y(d):
    s=d["shape"]; r=radius(d)
    if s=="BAR": return size_min()*0.062
    if s=="PILL": return r*0.8
    if s=="CIRCLE": return r*(1.28 if has_plate(d) else 1.0)
    return r
def clamp(raw, half, extent):
    if extent<=0: return raw
    if half*2.0>=extent: return extent/2.0
    return max(half, min(extent-half, raw))
def esc(t): return html.escape(t, quote=True)
def txt(cx,cy,s,size,color,weight="bold"):
    if not s: return ""
    return (f'<text x="{cx:.1f}" y="{cy:.1f}" font-size="{size:.1f}" fill="{color}" text-anchor="middle" '
            f'dominant-baseline="central" font-family="Arial, sans-serif" font-weight="{weight}">{esc(s)}</text>')
def draw_dpad(cx,cy,r,d):
    out=[]; fill=d["fill"]; lab=d["labelColor"]; armW=r*0.62; half=armW/2.0
    gap=r*0.06 if d["shape"]=="PSX_CROSS" else 0.0; corner=armW*0.28
    def rr(x0,y0,x1,y1,rad,col): return f'<rect x="{x0:.1f}" y="{y0:.1f}" width="{(x1-x0):.1f}" height="{(y1-y0):.1f}" rx="{rad:.1f}" ry="{rad:.1f}" fill="{col}"/>'
    out.append(rr(cx-r,cy-half,cx-half-gap,cy+half,corner,fill)); out.append(rr(cx+half+gap,cy-half,cx+r,cy+half,corner,fill))
    out.append(rr(cx-half,cy-r,cx+half,cy-half-gap,corner,fill)); out.append(rr(cx-half,cy+half+gap,cx+half,cy+r,corner,fill))
    out.append(rr(cx-half,cy-half,cx+half,cy+half,corner*0.6,fill))
    if d["shape"]=="PSX_CROSS": out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{half*0.55:.1f}" fill="{darken(fill)}"/>')
    ar=armW*0.30
    def arrow(px,py,dx,dy):
        perpx,perpy=-dy,dx; p1=(px+dx*ar,py+dy*ar); p2=(px-dx*ar*0.6+perpx*ar,py-dy*ar*0.6+perpy*ar); p3=(px-dx*ar*0.6-perpx*ar,py-dy*ar*0.6-perpy*ar)
        return f'<polygon points="{p1[0]:.1f},{p1[1]:.1f} {p2[0]:.1f},{p2[1]:.1f} {p3[0]:.1f},{p3[1]:.1f}" fill="{lab}"/>'
    out.append(arrow(cx-r+armW*0.55,cy,-1,0)); out.append(arrow(cx+r-armW*0.55,cy,1,0))
    out.append(arrow(cx,cy-r+armW*0.55,0,-1)); out.append(arrow(cx,cy+r-armW*0.55,0,1))
    return "".join(out)
def draw_control(d):
    cx=clamp(d["x"]*W,half_x(d),W); cy=clamp(d["y"]*H,half_y(d),H); r=radius(d); s=d["shape"]; out=[]
    if s=="CIRCLE":
        if has_plate(d):
            pr=r*1.28; out.append(f'<rect x="{cx-pr:.1f}" y="{cy-pr:.1f}" width="{2*pr:.1f}" height="{2*pr:.1f}" rx="{pr*0.25:.1f}" ry="{pr*0.25:.1f}" fill="{d["plate"]}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{d["fill"]}"/>')
        if d["stroke"]!="transparent": out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r*0.94:.1f}" fill="none" stroke="{d["stroke"]}" stroke-width="{r*0.12:.1f}"/>')
        out.append(txt(cx,cy,d["label"],min(size_min()*0.082,r*1.25,r*3.0/max(1,len(d["label"]))),d["labelColor"]))
    elif s=="PILL":
        pw=r*1.85; ph=r*0.8; out.append(f'<rect x="{cx-pw:.1f}" y="{cy-ph:.1f}" width="{2*pw:.1f}" height="{2*ph:.1f}" rx="{r:.1f}" ry="{r:.1f}" fill="{d["fill"]}"/>')
        out.append(txt(cx,cy,d["label"],r*(0.62 if len(d["label"])>2 else 0.9),d["labelColor"]))
    elif s=="BAR":
        hl=d["size"]*W/2.0; ht=size_min()*0.062; out.append(f'<rect x="{cx-hl:.1f}" y="{cy-ht:.1f}" width="{2*hl:.1f}" height="{2*ht:.1f}" rx="{ht:.1f}" ry="{ht:.1f}" fill="{d["fill"]}"/>')
        out.append(txt(cx,cy,d["label"],ht*1.15,d["labelColor"]))
    elif s in ("CROSS","PSX_CROSS"): out.append(draw_dpad(cx,cy,r,d))
    elif s=="STICK":
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{d["fill"]}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r*0.98:.1f}" fill="none" stroke="#FFFFFF" stroke-opacity="0.13" stroke-width="{r*0.05:.1f}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r*0.52:.1f}" fill="{darken(d["fill"])}"/>')
        if d["label"]: out.append(txt(cx,cy+r*1.28,d["label"],r*0.3,d["labelColor"],"normal"))
    return "".join(out)

def render_svg(entry, gid):
    frame = landscape_frame(entry["controls"])
    parts=[f'<svg viewBox="0 0 {W:.0f} {H:.0f}" xmlns="http://www.w3.org/2000/svg" class="lf-svg" role="img">']
    parts.append(f'<defs><linearGradient id="bg{gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#101216"/><stop offset="1" stop-color="#070709"/></linearGradient></defs>')
    parts.append(f'<rect x="0" y="0" width="{W:.0f}" height="{H:.0f}" rx="16" fill="url(#bg{gid})"/>')
    # the clear game window
    gx0, gx1 = GAME_L*W, GAME_R*W
    parts.append(f'<rect x="{gx0:.0f}" y="14" width="{gx1-gx0:.0f}" height="{H-28:.0f}" rx="8" fill="#000000" fill-opacity="0.55" stroke="{entry["body"]}" stroke-opacity="0.6" stroke-width="2" stroke-dasharray="7 6"/>')
    parts.append(txt((gx0+gx1)/2, H/2, "GAME SCREEN", 15, "#5A6070", "bold"))
    for d in frame:
        if d["id"]=="_menu": continue
        parts.append(draw_control(d))
    parts.append("</svg>")
    return "".join(parts)

def main():
    dump_path, readme_path = sys.argv[1], sys.argv[2]
    data = json.load(open(dump_path, encoding="utf-8"))
    groups=[]
    for e in data:
        sig=json.dumps(e["controls"], sort_keys=True)
        g=next((g for g in groups if g["sig"]==sig), None)
        if g is None: groups.append({"sig":sig,"entry":e,"names":[e["display"]],"min_year":e["year"]})
        else: g["names"].append(e["display"]); g["min_year"]=min(g["min_year"],e["year"])
    groups.sort(key=lambda g:(g["min_year"], g["entry"]["display"]))
    cards=[]
    for i,g in enumerate(groups):
        cards.append(f'<figure class="lf-card">{render_svg(g["entry"],i)}<figcaption class="lf-cap">{esc(" · ".join(g["names"]))}</figcaption></figure>')
    section=(
        '<!--LAYOUTS_LF_START-->\n'
        '<h2 id="layouts-landscape-screen">Controller layouts — landscape with screen</h2>\n'
        '<p class="lf-note">The <strong>On phone — landscape</strong> layout: the game plays in the centred '
        'window (dashed) and every control is pushed into the side columns that frame it — <strong>nothing over '
        'the screen, no overlapping controls</strong>. Rendered from the app’s layout coordinates. '
        f'{len(groups)} distinct layouts across {len(data)} systems.</p>\n'
        '<style>\n'
        '.lf-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:18px;margin:18px 0}\n'
        '.lf-card{margin:0;display:flex;flex-direction:column;align-items:center;background:#00000010;border:1px solid #8884;border-radius:12px;padding:10px}\n'
        '.lf-svg{width:100%;height:auto;border-radius:12px;display:block}\n'
        '.lf-cap{margin-top:8px;font-size:13px;line-height:1.35;text-align:center;opacity:.85}\n'
        '.lf-note{opacity:.75;font-size:14px}\n'
        '</style>\n'
        '<div class="lf-grid">\n' + "\n".join(cards) + '\n</div>\n'
        '<!--LAYOUTS_LF_END-->'
    )
    if readme_path != "-":
        src=open(readme_path, encoding="utf-8").read()
        s,e="<!--LAYOUTS_LF_START-->","<!--LAYOUTS_LF_END-->"
        if s in src and e in src:
            out=src[:src.index(s)]+section+src[src.index(e)+len(e):]
        elif "<!--LAYOUTS_LS_END-->" in src:
            a="<!--LAYOUTS_LS_END-->"; idx=src.index(a)+len(a); out=src[:idx]+"\n"+section+src[idx:]
        else:
            out=src.replace("</body>", section+"\n</body>")
        open(readme_path,"w",encoding="utf-8").write(out)
    if len(sys.argv)>3:
        open(sys.argv[3],"w",encoding="utf-8").write("<!doctype html><meta charset=utf-8><title>Landscape frame</title><body style='background:#141419;color:#eee;font-family:Arial'>"+section.replace("<!--LAYOUTS_LF_START-->","").replace("<!--LAYOUTS_LF_END-->","")+"</body>")
    print(f"{len(groups)} distinct layouts, {len(data)} systems.")

if __name__=="__main__": main()
