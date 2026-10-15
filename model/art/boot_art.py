"""Shin and foot as one boot: the foot's pieces in the shin's blue armour, the ankle a cuff over the shin.

Before: the blue 8x9x8 shin stood on a separate stack (navy ankle band, grey foot slab, navy toe wedge,
dark sole), tilted against it by the rest pose's ankle angle, so it read as a box on a pallet.
Now, with the ankle still bending (foot bone unchanged):
  ankle   8x3x8 (y 2.625..5.625, was 8x2x8) with inflate 0.25: a cuff over the shin's lower 2 px,
          blue like the shin, so the boot's shaft runs down into the foot without a step
  foot    8x2x12 blue armour sides and top, a grey welt along the bottom of the sides
  toe     the sloped 8x2x4 toe cap in the same blue
  sole    dark, unchanged
Blue faces use the arm/thigh plate palette (lit top/left, shaded right/bottom, specks).

Run: uv run --with pillow python3 model/art/boot_art.py
Input .scratch/boot-source.json ({model, texture} of the live project);
output .scratch/boot-plan.json (cube edits, changed pixels as hex).
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'boot-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']
by = {e['name']: e for e in els}
rng = random.Random(13)

HI, BASE, MID, DARK, EDGE = (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)
STEEL, STEEL_LO = (128, 134, 140), (95, 100, 106)
SOLE = (40, 44, 48)


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
            'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


def plate(w, h, top_edge=True, bottom_edge=True):
    px = [[MID if (x + y < 3 and rng.random() < .6) else DARK if rng.random() < .06 else
           MID if rng.random() < .05 else BASE for x in range(w)] for y in range(h)]
    for y in range(h):
        px[y][0] = HI
        if w > 2:
            px[y][w - 1] = DARK
    if top_edge:
        for x in range(w):
            px[0][x] = HI
    if bottom_edge and h > 2:
        for x in range(w):
            px[h - 1][x] = EDGE
    return px


def paint(uv, sz, painter):
    for f, (x, y, w, h) in faces(*sz).items():
        for j, row in enumerate(painter(f, w, h)):
            for i, p in enumerate(row):
                tex.putpixel((uv[0] + x + i, uv[1] + y + j), (*p, 255))


def foot(f, w, h):
    if f == 'down':
        return [[SOLE] * w for _ in range(h)]
    if f == 'up':
        return plate(w, h)
    px = plate(w, h, bottom_edge=False)
    for x in range(w):                                     # grey welt where the upper meets the sole
        px[h - 1][x] = STEEL if x % 3 else STEEL_LO
    return px


def toe(f, w, h):
    return [[SOLE] * w for _ in range(h)] if f == 'down' else plate(w, h)


def ankle(f, w, h):
    if f in ('up', 'down'):                               # inside the shin / on the foot
        return [[SOLE] * w for _ in range(h)]
    return plate(w, h, top_edge=False, bottom_edge=False)  # no seam lines: the shaft runs on


# Free the ankle's old net (it grows) and pack the new one.
used = set()
for c in els:
    w, h, d = size(c)
    u, v = map(int, c.get('uv_offset', [0, 0]))
    pts = {(x, y) for y in range(v, v + d + h) for x in range(u, u + 2 * (w + d))}
    if c['name'].startswith('stomper_ankle_'):
        for pt in pts:
            tex.putpixel(pt, (0, 0, 0, 0))
    else:
        used |= pts
used |= {(x, y) for y in range(tex.height) for x in range(tex.width) if tex.getpixel((x, y))[3]}


def pack(w, h, d):
    nw, nh = 2 * (w + d), d + h
    for v in range(tex.height - nh + 1):
        for u in range(tex.width - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u, u + nw)}
            if not used & pts:
                used.update(pts)
                return [u, v]
    raise SystemExit('no room')


paint(by['stomper_foot_l']['uv_offset'], [8, 2, 12], foot)      # nets shared by both feet (right mirrored)
paint(by['stomper_toe_l']['uv_offset'], [8, 2, 4], toe)
ankle_uv = pack(8, 3, 8)
paint(ankle_uv, [8, 3, 8], ankle)
edits = []
for side in 'lr':
    a = by[f'stomper_ankle_{side}']
    edits.append(dict(name=a['name'], from_=[a['from'][0], 2.625, a['from'][2]], to=[a['to'][0], 5.625, a['to'][2]],
                      uv_offset=ankle_uv, inflate=0.25))

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
(scratch/'boot-plan.json').write_text(json.dumps(dict(
    edits=edits, pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print('ankle net', ankle_uv, len(changed), 'px')
