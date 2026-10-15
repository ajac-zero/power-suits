"""Notches under the pelvis shell: a wide stepped one at the back, a narrow one at the front.

The shell's flat lower edge looked plain from behind and left the dark hip core showing between
the hip joints. Blue plates on the pelvis bone, hanging below the shell's edge (y 22.625):
  back   seat plates, stepped: 10x1x1 (x +-5, y 21.625..22.625) over 6x1x1 (x +-3, y 20.625..21.625),
         z 4..5 (flush with the shell's back face): shapes the seat, covers the core from behind
  front  stepped like the back, narrower: 4x1x1 (x +-2, y 21.625..22.625) over 2x1x1 (x +-1,
         y 20.625..21.625), z -5..-4 (flush with the shell's front): covers the core between the
         thighs. (A plain 4x2x1 cut 0.6 px into the forward thigh at rest and 1.6 px into the shins
         in run; a plain 2x2x1 left a slit beside it.)
Each net is painted as one armour plate (lit top/left, shaded right/bottom), like the shell.

Run: uv run --with pillow python3 model/art/hip_notch_art.py
Input .scratch/notch-source.json ({model, texture} of the live project);
output .scratch/notch-plan.json (new cubes, changed pixels as hex).
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'notch-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
rng = random.Random(21)


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


used = set()
for c in m['elements']:
    w, h, d = size(c)
    u, v = map(int, c.get('uv_offset', [0, 0]))
    used |= {(x, y) for y in range(v, v + d + h) for x in range(u, u + 2 * (w + d))}
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


LIGHT, HI, BASE, MID, DARK, EDGE = (98, 163, 239), (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)


def plate(w, h, shade=1.0):
    px = [[MID if rng.random() < .08 else DARK if rng.random() < .05 else BASE for _ in range(w)] for _ in range(h)]
    if h > 1:
        for x in range(w):
            px[0][x] = HI
            px[h - 1][x] = EDGE
    else:
        for x in range(w):
            px[0][x] = BASE
    for y in range(h):
        if w > 1:
            px[y][0] = HI if h > 1 else BASE
            px[y][w - 1] = DARK
    return [[tuple(int(c * shade) for c in p) for p in row] for row in px]


def paint(uv, w, h, d):
    faces = {'up': (d, 0, w, d, 1), 'down': (d + w, 0, w, d, .7), 'east': (0, d, d, h, .9),
             'north': (d, d, w, h, 1), 'west': (d + w, d, d, h, .9), 'south': (2 * d + w, d, w, h, 1)}
    for fx, fy, fw, fh, shade in faces.values():
        for y, row in enumerate(plate(fw, fh, shade)):
            for x, p in enumerate(row):
                tex.putpixel((uv[0] + fx + x, uv[1] + fy + y), (*p, 255))


new = []
for name, start, sz in [('seat_upper', [-5, 21.625, 4], [10, 1, 1]),
                        ('seat_lower', [-3, 20.625, 4], [6, 1, 1]),
                        ('codpiece_upper', [-2, 21.625, -5], [4, 1, 1]),
                        ('codpiece_lower', [-1, 20.625, -5], [2, 1, 1])]:
    uv = pack(*sz)
    paint(uv, *sz)
    new.append(dict(name=name, from_=start, to=[a + b for a, b in zip(start, sz)], uv_offset=uv, origin=[0, 22.625, 0]))

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
(scratch/'notch-plan.json').write_text(json.dumps(dict(
    new=new, pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print([(n['name'], n['uv_offset']) for n in new], len(changed), 'px')
