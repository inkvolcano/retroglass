"""Derive crop rectangles for the controller and screen-mode sprite sheets.

Same problem as ``derive_console_cells.py`` and the same answer, but these two sheets are not on
a regular grid at all: the controller sheet runs 7 across with a short final row, and the
screen-mode sheet has 3 icons on one row and 4 on the next, at whatever spacing the artwork
happened to land on. So rather than assume a grid, this finds each drawing by its own ink and
centres a shared box on it.

One box size per sheet, so nothing jitters as the library scrolls; per-drawing top-lefts, so
nothing is clipped.

Usage:
    python scripts/derive_sheet_cells.py app/src/main/res/drawable-nodpi/pad_line.png
    python scripts/derive_sheet_cells.py app/src/main/res/drawable-nodpi/screen_line.png
"""
import sys

import numpy as np
from PIL import Image

INK = 40          # a stroke, as opposed to the soft glow around one
MIN_RUN = 25      # ignore specks; the smallest real drawing is far wider than this
# A drawing can have detached parts - a cable, a stray button - so runs are merged before being
# called separate drawings. The two axes need different thresholds: rows of artwork sit closer
# together than the gap inside a single wide drawing, so one shared value either welds every row
# into one band or splits a cabled pad in two.
ROW_GAP = 6
COL_GAP = 26
PAD = 10          # breathing room around the tightest box that fits every drawing


def runs(values, threshold=0):
    out, start = [], None
    for i, v in enumerate(values):
        if v > threshold and start is None:
            start = i
        elif v <= threshold and start is not None:
            out.append((start, i - 1))
            start = None
    if start is not None:
        out.append((start, len(values) - 1))
    return out


def merge(spans, gap):
    merged = []
    for span in spans:
        if merged and span[0] - merged[-1][1] < gap:
            merged[-1] = (merged[-1][0], span[1])
        else:
            merged.append(span)
    return merged


def main(path):
    mask = np.array(Image.open(path).convert("L")) > INK

    bands = [b for b in runs(mask.sum(axis=1)) if b[1] - b[0] > MIN_RUN]
    bands = merge(bands, ROW_GAP)

    boxes = []
    for top, bottom in bands:
        strip = mask[top:bottom + 1]
        cols = [c for c in runs(strip.sum(axis=0)) if c[1] - c[0] > MIN_RUN]
        for left, right in merge(cols, COL_GAP):
            # Tighten vertically against this drawing alone: the band is as tall as the tallest
            # drawing in the row, and centring a shared box on a band rather than on the artwork
            # is what leaves short drawings sitting high in their tile.
            sub = mask[top:bottom + 1, left:right + 1]
            rows_with_ink = np.where(sub.any(axis=1))[0]
            y0 = top + int(rows_with_ink[0])
            y1 = top + int(rows_with_ink[-1])
            boxes.append((left, y0, right, y1))

    box_w = max(x1 - x0 for x0, _, x1, _ in boxes) + PAD * 2
    box_h = max(y1 - y0 for _, y0, _, y1 in boxes) + PAD * 2

    print(f"// {len(boxes)} drawings, box {box_w} x {box_h}")
    print(f"{box_w}, {box_h},")
    print("intArrayOf(")
    for i, (x0, y0, x1, y1) in enumerate(boxes):
        ox = (x0 + x1) // 2 - box_w // 2
        oy = (y0 + y1) // 2 - box_h // 2
        end = "\n" if i % 7 == 6 else " "
        print(f"    {ox}, {oy},", end=end)
    print("\n)")


if __name__ == "__main__":
    main(sys.argv[1])
