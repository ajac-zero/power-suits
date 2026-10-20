"""Shaded pads (2026-10-19, user after pads_clean_art.py v2: "now they are too flat in their texture").

The plain pads lost the noise but also every cue of shape. Shading here comes from the shape, not from marks: a pauldron
catches the light on its top and darkens toward its lower edge, so every blue side face takes its tone from the pixel's
height on the pad (world y, the same ladder on the back plate, the front plate and the outer side, so the pieces of the
outer side meet without seams):
    the top plate's edges H (the rim that catches the light), then L, M, B, S and D toward the bottom of the back plate.
The top face: L with an H border. Both pads get the same ladder (it only depends on y), so they stay mirror images.
Variant B adds a raised rim: the plates' vertical edges (the back and front plate edge columns and their 1 px sides)
one step lighter than the ladder, the lowest row of each plate one step lighter (a rolled lower edge), like the
breastplate's lit edge and rolled rim.
Insides stay flat dark iron, the ramp's underside flat shaded blue (pads_clean_art.py).

    uv run --with pillow python3 model/art/pads_shade_art.py A|B   (reads .scratch/pads-clean/source.json; writes
    .scratch/pads-clean/plan_<v>.json for pads_clean_apply.js)
"""
import base64, io, json, math, sys
from pathlib import Path
from PIL import Image

VARIANT = sys.argv[1] if len(sys.argv) > 1 else 'A'
SRC, OUT = Path('.scratch/pads-clean/source.json'), Path('.scratch/pads-clean')
s = json.loads(SRC.read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy()

hexc = lambda h: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
LADDER = [hexc(h) for h in ('62a3ef', '4588e0', '3a7bd6', '2d6bcc', '2358b2', '1b4895')]   # H L M B S D
H, L, M, B, S, D = range(6)
IRON, IRON_DARK, RAMP_UNDER = hexc('2f3437'), hexc('25292c'), hexc('1f4a8e')
PAD_TOP = 42.625


def is_iron(c):
    return max(c) < 0x55 and max(c) - min(c) < 0x20


def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h), 'north': (d, d, w, h),
            'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


def tone(y):
    """Ladder step for a pixel whose centre is at height y (px below the pad's top)."""
    dy = PAD_TOP - y
    for limit, step in ((1, H), (3.5, L), (6, M), (9.5, B), (12.5, S)):
        if dy < limit: return step
    return D


pads = [e for e in m['elements'] if e['name'].startswith('pad_')]
mine = set()
for e in pads:
    n = e['name']
    w, h, d = (math.floor(b - a + 1e-6) for a, b in zip(e['from'], e['to']))
    u0, v0 = map(int, e['uv_offset'])
    y0, y1 = e['from'][1], e['to'][1]
    for f, (x, y, fw, fh) in faces(w, h, d).items():
        if fw == 0 or fh == 0: continue
        pts = [[(u0 + x + i, v0 + y + j) for i in range(fw)] for j in range(fh)]
        old = [live.getpixel(p)[:3] for row in pts for p in row]
        if sum(map(is_iron, old)) * 2 > len(old):                     # the shell's inside
            grid = [[IRON_DARK if (j == fh - 1 and fh > 2) else IRON] * fw for j in range(fh)]
        elif n.startswith('pad_ramp'):                                # underside + its 1 px sides
            grid = [[RAMP_UNDER if f == 'down' else LADDER[S]] * fw for _ in range(fh)]
        elif f == 'up':                                               # the pad's roof (or a plate top under it)
            grid = [[LADDER[H] if (j in (0, fh - 1) or i in (0, fw - 1)) else LADDER[L] for i in range(fw)]
                    for j in range(fh)]
        elif f == 'down':
            grid = [[LADDER[S]] * fw for _ in range(fh)]
        else:                                                         # a side face: tone by height
            grid = []
            for j in range(fh):
                yc = y1 - (j + 0.5) * (y1 - y0) / fh
                row = []
                for i in range(fw):
                    t = tone(yc)
                    if VARIANT == 'B' and n.startswith(('pad_back', 'pad_front')) and not n.startswith('pad_top'):
                        big = fw > 1 and f in ('north', 'south')
                        edge = (not big) or i in (0, fw - 1)               # 1 px plate sides / the plate's edge columns
                        if edge or j == fh - 1: t = max(H, t - 1)
                    row.append(LADDER[t])
                grid.append(row)
        for row_p, row_c in zip(pts, grid):
            for p, c in zip(row_p, row_c):
                tex.putpixel(p, c + (255,)); mine.add(p)

px = [(x, y, *tex.getpixel((x, y))) for (x, y) in sorted(mine)]
(OUT / f'plan_{VARIANT}.json').write_text(json.dumps(dict(pixels=''.join('%02x' * 6 % p for p in px))))
tex.save(OUT / f'shade_{VARIANT}.png')
print(VARIANT, len(px), 'pixels')
