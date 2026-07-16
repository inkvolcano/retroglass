#!/usr/bin/env python3
"""Detect overlapping controls in each console layout, using the exact LayoutPreview
geometry (300x426 portrait-split space). Reports button pairs that visually collide."""
import json, sys, math

W, H = 300.0, 426.0
MIN = min(W, H)

def radius(d): return d["size"] / 2.0 * MIN
def plate(d):  return d["plate"] != "transparent"

def half_x(d):
    s=d["shape"]; r=radius(d)
    if s=="BAR": return d["size"]*W/2.0
    if s=="PILL": return r*1.85
    if s=="CIRCLE": return r*(1.28 if plate(d) else 1.0)
    return r
def half_y(d):
    s=d["shape"]; r=radius(d)
    if s=="BAR": return MIN*0.062
    if s=="PILL": return r*0.8
    if s=="CIRCLE": return r*(1.28 if plate(d) else 1.0)
    return r
def clamp(raw, half, ext):
    if ext<=0: return raw
    if half*2>=ext: return ext/2.0
    return max(half, min(ext-half, raw))
def cx(d): return clamp(d["x"]*W, half_x(d), W)
def cy(d): return clamp(d["y"]*H, half_y(d), H)

CIRCLE_LIKE = {"CIRCLE","STICK"}

def pen(a, b):
    """Positive = overlap depth (px in 300-wide space); negative = gap."""
    ax, ay, bx, by = cx(a), cy(a), cx(b), cy(b)
    if a["shape"] in CIRCLE_LIKE and b["shape"] in CIRCLE_LIKE:
        dist = math.hypot(ax-bx, ay-by)
        return (radius(a)+radius(b)) - dist
    # AABB penetration (min overlap on either axis)
    ox = (half_x(a)+half_x(b)) - abs(ax-bx)
    oy = (half_y(a)+half_y(b)) - abs(ay-by)
    if ox <= 0 or oy <= 0: return -min(-ox, -oy) if False else -1.0
    return min(ox, oy)

def main():
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    THRESH = float(sys.argv[2]) if len(sys.argv) > 2 else 1.5  # px of overlap to flag
    seen_names = {}
    any_bad = False
    for e in data:
        controls = [c for c in e["controls"] if c["id"] != "_menu"]
        bad = []
        for i in range(len(controls)):
            for j in range(i+1, len(controls)):
                a, b = controls[i], controls[j]
                # dpad arms are a plus, not a box; skip dpad-vs-far-button false positives
                p = pen(a, b)
                if p > THRESH:
                    bad.append((a["id"], b["id"], round(p,1)))
        if bad:
            any_bad = True
            print(f"{e['console']:14s} ({e['display']}):")
            for aid, bid, p in sorted(bad, key=lambda t:-t[2]):
                print(f"     {aid:>8s} <-> {bid:<8s}  overlap {p}px")
    if not any_bad:
        print(f"No overlaps > {THRESH}px. All clear.")

if __name__ == "__main__":
    main()
