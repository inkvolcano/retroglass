#!/usr/bin/env python3
"""Rebuild consoles.png as a clean, centered 5x4 sprite: detect each console's actual
position (rows from a full-width projection, columns per-row where the gaps are clear),
tight-crop it, and paste it centered + size-normalized into a uniform cell. Keeps the
single-sheet layout so the doc CSS sprite and the app's grid-crop work unchanged.

Usage: python scripts/center_console_sprite.py IN.png OUT.png [cellW cellH fill]
"""
import sys
from PIL import Image
import numpy as np

def runs(prof, thr, gapmin):
    on = prof > thr
    out, s = [], None
    for i, v in enumerate(on):
        if v and s is None: s = i
        if (not v) and s is not None: out.append([s, i - 1]); s = None
    if s is not None: out.append([s, len(on) - 1])
    m = [out[0]]
    for r in out[1:]:
        if r[0] - m[-1][1] < gapmin: m[-1][1] = r[1]
        else: m.append(r)
    return m

def main():
    src = sys.argv[1]; out = sys.argv[2]
    CW = int(sys.argv[3]) if len(sys.argv) > 3 else 300
    CH = int(sys.argv[4]) if len(sys.argv) > 4 else 250
    FILL = float(sys.argv[5]) if len(sys.argv) > 5 else 0.88
    COLS, ROWS = 5, 4

    im = Image.open(src).convert('RGB')
    a = np.asarray(im).astype(int)
    bright = a.max(axis=2)
    mask = (bright > 22).astype(int)

    rowb = [(a0, a1) for a0, a1 in runs(mask.sum(1), mask.sum(1).max() * 0.02, 40)]
    assert len(rowb) == ROWS, f"expected {ROWS} rows, got {len(rowb)}"

    sheet = Image.new('RGB', (CW * COLS, CH * ROWS), (0, 0, 0))
    cells = 0
    for ri, (y0, y1) in enumerate(rowb):
        prof = mask[y0:y1].sum(0)
        colb = runs(prof, prof.max() * 0.04, 22)
        assert len(colb) == COLS, f"row {ri}: expected {COLS} cols, got {len(colb)}"
        for ci, (x0, x1) in enumerate(colb):
            # tighten to the console's own content within its (col x row) box
            sub = bright[y0:y1, x0:x1]
            m = sub > 16
            ys, xs = np.where(m)
            tx0, tx1, ty0, ty1 = x0 + xs.min(), x0 + xs.max(), y0 + ys.min(), y0 + ys.max()
            crop = im.crop((tx0, ty0, tx1 + 1, ty1 + 1))
            bw, bh = crop.size
            scale = min(FILL * CW / bw, FILL * CH / bh)
            nw, nh = max(1, round(bw * scale)), max(1, round(bh * scale))
            crop = crop.resize((nw, nh), Image.LANCZOS)
            px = ci * CW + (CW - nw) // 2
            py = ri * CH + (CH - nh) // 2
            sheet.paste(crop, (px, py))
            cells += 1
    sheet.save(out)
    print(f"wrote {out} {sheet.size}, {cells} cells")

if __name__ == "__main__":
    main()
