"""Shoulders that join the torso: arms in against the chest, knee-sized shoulder joints, blue upper arms.

The arms hung apart from the torso: the arm pivots (x +-11) sat 3.5 px outside the chest (x +-7.5),
the rest pose splays them 15-18 degrees, the pauldrons swung on their own and the upper arm was
bare dark iron (a dark stick under a floating pad). A 9x3x3 joint was all that fit; it looked tiny.

Geometry (done live in Blockbench, see shoulders_apply.js; this script only plans cubes + paint):
- every arm part except the pauldron moved 2 px in (arm pivots x +-9), so the arm hangs off the
  chest's top corner; pauldrons stay where they were, 1 px higher;
- pauldrons no longer move on their own (their keys zeroed) and arm abduction is limited to 15
  degrees past the rest pose (heavy suit, user's call): shoulders_apply.js.
This script: one 5x4x5 dark iron joint per arm on the arm bone (the knee joints' size and UV nets),
x 6.5..11.5 (centred on the pivot, 1 px into the chest), y 36.375..40.375 (under the pauldron's
top plate, just below the chest top so the faces never coincide); the upper arms repainted as blue
armour like the thighs. Clean against the pauldrons in all 23 animations (livesweep.js).

Run: uv run --with pillow python3 model/art/shoulders_art.py
Input .scratch/shoulders-source.json ({model, texture} of the live project);
output .scratch/shoulders-plan.json (remove uuids, new cubes with their bone, changed pixels as hex).
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'shoulders-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']
byname = {e['name']: e for e in els}

REMOVE = ['shoulder_joint_l', 'shoulder_joint_r']
remove_ids = {byname[n]['uuid'] for n in REMOVE}

def net(c):
    w, h, d = [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]
    u, v = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v, min(v + d + h, tex.height))
            for x in range(u, min(u + 2 * (w + d), tex.width))}

used, freed = set(), set()
for c in els:
    (freed if c['uuid'] in remove_ids else used).update(net(c))
for pt in freed - used:
    tex.putpixel(pt, (0, 0, 0, 0))
used.update((x, y) for y in range(tex.height) for x in range(tex.width) if tex.getpixel((x, y))[3])

def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
            'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}

knee = byname['knee_joint_l']
ku, kv = knee['uv_offset']
LIGHT, HI, BASE, MID, DARK, EDGE = (98,163,239), (69,136,224), (45,107,204), (58,123,214), (35,88,178), (27,72,149)
rng = random.Random(5)

def plate(w, h):
    """Blue armour face like the thighs: lit top/left, shaded right/bottom, mottled."""
    px = [[BASE] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if x + y < 3 and rng.random() < 0.6:
                px[y][x] = MID
            elif rng.random() < 0.06:
                px[y][x] = DARK
            elif rng.random() < 0.05:
                px[y][x] = MID
    for x in range(w):
        px[0][x] = HI
        if h > 2:
            px[h - 1][x] = EDGE
    for y in range(1, h):
        px[y][0] = HI
        if w > 2:
            px[y][w - 1] = DARK
    px[0][0] = LIGHT
    if h > 2:
        px[h - 1][0] = DARK
    return px

for name in ['upper_arm_r', 'upper_arm_l']:
    c = byname[name]
    w, h, d = [round(b - a) for a, b in zip(c['from'], c['to'])]
    u, v = c['uv_offset']
    for face, (fx, fy, fw, fh) in faces(w, h, d).items():
        px = plate(fw, fh)
        if face == 'down':
            px = [[tuple(int(k * 0.7) for k in p) for p in row] for row in px]
        for y in range(fh):
            for x in range(fw):
                tex.putpixel((u + fx + x, v + fy + y), (*px[y][x], 255))

parts = []
kr = byname['knee_joint_r']
for side, bone, x0, pivot, kn in [('r', 'right_arm', 6.5, [9, 39.625, 0], kr),
                                  ('l', 'left_arm', -11.5, [-9, 39.625, 0], knee)]:
    start = [x0, 36.375, -2.5]
    parts.append(dict(bone=bone, name='shoulder_joint_' + side, from_=start,
                      to=[a + b for a, b in zip(start, [5, 4, 5])], uv_offset=kn['uv_offset'],
                      mirror_uv=kn.get('mirror_uv', False), origin=pivot))

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
hexpx = ''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed)
(scratch/'shoulders-plan.json').write_text(json.dumps(dict(
    remove=sorted(remove_ids), parts=parts, pixels=hexpx)))
print('parts', [(p['name'], p['from_'], p['to'], p['uv_offset']) for p in parts], 'changed px', len(changed))
