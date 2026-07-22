"""Derive the console-artwork crop rectangles from the sprite sheet itself.

The artwork is 35 line drawings on black, laid out 5 across and 7 down in the same order as
Console.SHEET_ORDER. It is generated art, so the drawings are *nearly* but not exactly on a
212 px grid - a straight width/5 crop clips 33 of the 35. Rather than hand-maintain 35
rectangles that silently rot the next time the art is regenerated, this reads the gutters back
out of the image and prints the Kotlin table.

Usage:
    python scripts/derive_console_cells.py app/src/main/res/drawable-nodpi/console_line.png

Then paste the printed block into ConsoleImages.CELLS.
"""
import sys

from PIL import Image
import numpy as np

COLS, ROWS = 5, 7
INK = 80          # a stroke, as opposed to the soft glow around one
MIN_GUTTER = 3    # px of blank needed to count as a separator


def spans(profile, min_gutter, min_span):
    """Content runs in a 1-D ink profile, i.e. the inverse of its blank gutters."""
    gaps, start = [], None
    for i, v in enumerate(profile):
        if v == 0 and start is None:
            start = i
        elif v != 0 and start is not None:
            if i - start >= min_gutter:
                gaps.append((start, i))
            start = None
    if start is not None:
        gaps.append((start, len(profile)))

    out, prev = [], 0
    for a, b in gaps:
        if a - prev > min_span:
            out.append((prev, a))
        prev = b
    if len(profile) - prev > min_span:
        out.append((prev, len(profile)))
    return out


def main(path):
    im = Image.open(path).convert('RGB')
    ink = np.asarray(im).astype(int).max(axis=2) > INK

    rows = spans(ink.sum(axis=1), MIN_GUTTER, 20)
    if len(rows) != ROWS:
        sys.exit('expected %d rows, found %d: %s' % (ROWS, len(rows), rows))

    cells = []
    for y0, y1 in rows:
        cols = spans(ink[y0:y1].sum(axis=0), MIN_GUTTER, 20)
        if len(cols) != COLS:
            sys.exit('row %d-%d: expected %d columns, found %d' % (y0, y1, COLS, len(cols)))
        for x0, x1 in cols:
            sub = ink[y0:y1, x0:x1]
            ys, xs = np.where(sub)
            cells.append((x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1))

    # One box size for every console, so the library grid does not jitter as you scroll.
    # Centred on each drawing and clamped to the sheet, so nothing is ever clipped.
    bw = max(x1 - x0 for x0, _, x1, _ in cells)
    bh = max(y1 - y0 for _, y0, _, y1 in cells)
    W, H = im.size
    print('// %d cells, uniform %dx%d box, derived from %s by scripts/derive_console_cells.py'
          % (len(cells), bw, bh, path.split('/')[-1]))
    for i, (x0, y0, x1, y1) in enumerate(cells):
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        lx = max(0, min(cx - bw // 2, W - bw))
        ly = max(0, min(cy - bh // 2, H - bh))
        print('        %d, %d,%s' % (lx, ly, '' if i % COLS != COLS - 1 else '   // row %d' % (i // COLS)))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else
         'app/src/main/res/drawable-nodpi/console_line.png')
