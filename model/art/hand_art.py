"""The right hand as a closed fist, one piece with the gauntlet (like the socket tools on the other arm).

Before: a dark 5x2x5 wrist, a 6x3x6 hand and three open 2x4x2 fingers plus a thumb hanging below the
8x7x8 forearm. Now the forearm runs straight into a 7x5x7 fist (y 15.625..20.625, on the arm axis x 10.5),
no wrist joint between them: the forearm is a gauntlet that ends in a fist, like a player's arm ends
in its hand. The right arm hangs with the palm toward the body (-X):
  outer side (+X)   back of the hand: blue plate, grey knuckle band along the bottom
  inner side (-X)   palm: dark, the curled fingers' grey segments in the lower rows
  front (-Z)        blue plating on top, the thumb wrapped over the index finger below it (grey)
  back (+Z)         little-finger side: blue, grey knuckle at the bottom
  bottom            the curled fingers: grey segments with dark gaps
The held-item grip bone (right_hand) moves to the fist's lower end, where vanilla holds items.

Run: uv run --with pillow python3 model/art/hand_art.py
Input .scratch/hand-source.json ({model, texture} of the live project);
output .scratch/hand-plan.json (remove uuids, new cube, group edit, changed pixels as hex).
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'hand-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']
rng = random.Random(9)

REMOVE = ['wrist_r', 'hand_r', 'finger_1', 'finger_2', 'finger_3', 'thumb']
remove = [e for e in els if e['name'] in REMOVE]
assert len(remove) == len(REMOVE), [e['name'] for e in remove]
ids = {e['uuid'] for e in remove}


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


def net(c):
    w, h, d = size(c)
    u, v = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v, v + d + h) for x in range(u, u + 2 * (w + d))}


used, freed = set(), set()
for c in els:
    (freed if c['uuid'] in ids else used).update(net(c))
for pt in freed - used:
    tex.putpixel(pt, (0, 0, 0, 0))
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


# Palette: the arm plates' blues, the trim grey of the pauldron rims, knee iron.
HI, BASE, MID, DARK, EDGE = (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)
STEEL, STEEL_HI, STEEL_LO = (128, 134, 140), (165, 170, 175), (95, 100, 106)
IRON, IRON_LO = (48, 53, 58), (33, 37, 41)


def blue(w, h, knuckles=False):
    px = [[MID if (x + y < 2 and rng.random() < .6) else DARK if rng.random() < .06 else BASE
           for x in range(w)] for y in range(h)]
    for x in range(w):
        px[0][x] = HI
    for y in range(h):
        px[y][w - 1] = DARK
    if knuckles:                               # grey knuckle band, a dark gap between the knuckles
        for x in range(w):
            px[h - 1][x] = STEEL_LO if x % 2 else STEEL
            px[h - 2][x] = STEEL_HI if x % 2 == 0 else STEEL
    return px


def palm(w, h):
    px = [[IRON if rng.random() > .15 else IRON_LO for _ in range(w)] for _ in range(h)]
    for y in range(h - 3, h):                  # curled fingers: steel segments, dark gaps between fingers
        for x in range(w):
            px[y][x] = IRON_LO if x in (2, 4) else (STEEL_HI if y == h - 3 else STEEL if y == h - 2 else STEEL_LO)
    return px


def thumb_side(w, h):
    px = blue(w, h)                            # the gauntlet's plating wraps the front of the hand
    for y in range(2, h):                      # the thumb across the index finger, below the plating
        for x in range(w):
            px[y][x] = IRON_LO if x in (0, w - 1) else STEEL_HI if y == 2 else STEEL if y < h - 1 else STEEL_LO
    return px


def fingers_bottom(w, d):
    return [[IRON_LO if z in (2, 4) else (STEEL if x % 3 else STEEL_HI) for x in range(w)] for z in range(d)]


def top(w, d):
    return [[IRON_LO if rng.random() < .2 else IRON for _ in range(w)] for _ in range(d)]


W, H, D = 7, 5, 7
uv = pack(W, H, D)
faces = {'up': (D, 0, top(W, D)), 'down': (D + W, 0, fingers_bottom(W, D)),
         'east': (0, D, blue(D, H, knuckles=True)), 'north': (D, D, thumb_side(W, H)),
         'west': (D + W, D, palm(D, H)), 'south': (2 * D + W, D, blue(W, H, knuckles=True))}
for f, (fx, fy, px) in faces.items():
    for y, row in enumerate(px):
        for x, p in enumerate(row):
            tex.putpixel((uv[0] + fx + x, uv[1] + fy + y), (*p, 255))

hand = next(g for g in m['groups'] if g['name'] == 'right_hand')
changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
(scratch/'hand-plan.json').write_text(json.dumps(dict(
    remove=[e['uuid'] for e in remove],
    new=[dict(name='fist_r', from_=[7, 15.625, -3.5], to=[14, 20.625, 3.5], uv_offset=uv, origin=[10.5, 28.625, 0])],
    groups=[dict(name='right_hand', origin=[hand['origin'][0], 15.625, hand['origin'][2]])],
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
crop = tex.crop((uv[0], uv[1], uv[0] + 2 * (W + D), uv[1] + D + H))
crop.resize((crop.width * 16, crop.height * 16), Image.Resampling.NEAREST).save(scratch/'fist_preview.png')
print('fist net', uv, len(changed), 'px')
