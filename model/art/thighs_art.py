"""Slimmer thighs with a visible hip joint, so the hips can match the chest.

After hips_art.py the thighs (7 wide, half a pixel off their pivot) forced an 18-wide pelvis
and their tops vanished into it. Now each leg reads like the knee: pelvis, dark joint, thigh.
- thighs 6x8x7, centred on the hip pivot (x +-4.5), armour ends 1 below the hip axis
- hip joints the size of the knee joints (5x4x5, knee texture), on the leg bones
- a dark hip core between the joints (fills the crotch, joins both sides)
- pelvis shell 16x4x10, its lower edge on the hip axis (0.75 of joint shows, like the knee);
  side caps dropped

Run: uv run --with pillow python3 model/art/thighs_art.py
Input .scratch/thighs-source.json ({model, texture} of the live project);
output .scratch/thighs-plan.json (remove uuids, new cubes with their bone, changed pixels as hex).
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'thighs-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']
byname = {e['name']: e for e in els}

REMOVE = ['pelvis_shell', 'crotch', 'hip_cap_l', 'hip_cap_r', 'thigh_l', 'thigh_r']
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

def pack(w, h, d):
    nw, nh = 2 * (w + d), d + h
    for v in range(tex.height - nh + 1):
        for u in range(tex.width - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u, u + nw)}
            if not used & pts:
                used.update(pts)
                return [u, v]
    raise SystemExit(f'no room for a {w}x{h}x{d} net')

LIGHT, HI, BASE, MID, DARK, EDGE = (98,163,239), (69,136,224), (45,107,204), (58,123,214), (35,88,178), (27,72,149)
rng = random.Random(11)

def plate(w, h):
    """Blue armour face like the thigh's old one: lit top/left, shaded right/bottom, mottled."""
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

def blue(name, fw, fh):
    px = plate(fw, fh)
    if name == 'down':
        px = [[tuple(int(c * 0.7) for c in p) for p in row] for row in px]
    return px

knee = byname['knee_joint_l']
ku, kv = knee['uv_offset']
def iron(name, fw, fh):
    src = original.crop((ku + 5, kv + 5, ku + 10, kv + 9)).resize((fw, fh), Image.Resampling.NEAREST)
    return [[src.getpixel((x, y))[:3] for x in range(fw)] for y in range(fh)]

def paint_box(uv, w, h, d, face_fn):
    u, v = uv
    faces = {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
             'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}
    for name, (fx, fy, fw, fh) in faces.items():
        px = face_fn(name, fw, fh)
        for y in range(fh):
            for x in range(fw):
                tex.putpixel((u + fx + x, v + fy + y), (*px[y][x], 255))

parts = []
def cube(bone, name, start, size, painter=None, uv=None, mirror=False, inflate=0, origin=None):
    if uv is None:
        uv = pack(*size)
        paint_box(uv, *size, painter)
    parts.append(dict(bone=bone, name=name, from_=start, to=[a + b for a, b in zip(start, size)],
                      uv_offset=uv, mirror_uv=mirror, inflate=inflate, origin=origin))
    return uv

AXIS = 22.625                                          # hip axis (left_leg / right_leg pivot y)
cube('pelvis', 'pelvis_shell', [-8, AXIS, -5], [16, 4, 10], blue, origin=[0, AXIS, 0])
cube('pelvis', 'hip_core', [-2, AXIS - 2, -3], [4, 3, 6], iron, origin=[0, AXIS, 0])
t = cube('left_leg', 'thigh_l', [-7.5, 13.625, -3.5], [6, 8, 7], blue, inflate=0.25, origin=[-4.5, AXIS, 0])
cube('right_leg', 'thigh_r', [1.5, 13.625, -3.5], [6, 8, 7], uv=t, mirror=True, inflate=0.25, origin=[4.5, AXIS, 0])
# Same box + nets as the knee joints, so the hips get the knees' iron.
kr = byname['knee_joint_r']
cube('left_leg', 'hip_joint_l', [-7, AXIS - 2, -2.5], [5, 4, 5], uv=knee['uv_offset'],
     mirror=knee.get('mirror_uv', False), origin=[-4.5, AXIS, 0])
cube('right_leg', 'hip_joint_r', [2, AXIS - 2, -2.5], [5, 4, 5], uv=kr['uv_offset'],
     mirror=kr.get('mirror_uv', False), origin=[4.5, AXIS, 0])

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
hexpx = ''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed)
(scratch/'thighs-plan.json').write_text(json.dumps(dict(
    remove=sorted(remove_ids), parts=parts, pixels=hexpx)))
print('parts', [(p['name'], p['uv_offset']) for p in parts], 'changed px', len(changed))
