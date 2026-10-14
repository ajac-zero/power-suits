"""Replace the girdle + tassets (a skirt hiding the missing hips) with real hips.

The pilot's pelvis needs a shell the legs hang from, like trunks: one blue pelvis block
whose bottom edge is the hip axis (the thighs turn about that edge, as a Minecraft
player's legs turn about the body's lower edge), a crotch piece filling the gap between
the thighs, and a dark bearing cap on each side at the hip pivot (knee-joint iron).

Run: uv run --with pillow python3 model/art/hips_art.py
Input .scratch/hips-source.json ({model, texture} snapshot of the live project);
output .scratch/hips-plan.json (remove uuids, new cubes, changed pixels as hex) and
.scratch/hips_preview.png.
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'hips-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']

REMOVE = ['girdle_housing', 'hip_joint_l', 'hip_joint_r'] + \
    [e['name'] for e in els if e['name'].startswith('tasset_')]
remove = [e for e in els if e['name'] in REMOVE]
assert len(remove) == 9, [e['name'] for e in remove]
remove_ids = {e['uuid'] for e in remove}

def net(c):
    w, h, d = [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]
    u, v = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v, min(v + d + h, tex.height))
            for x in range(u, min(u + 2 * (w + d), tex.width))}

used, freed = set(), set()
for c in els:
    (freed if c['uuid'] in remove_ids else used).update(net(c))
for pt in freed - used:          # nets only the removed cubes used (knee nets stay)
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

# Blue armour palette (sampled from the thigh plate).
LIGHT, HI, BASE, MID, DARK, EDGE = (98,163,239), (69,136,224), (45,107,204), (58,123,214), (35,88,178), (27,72,149)
rng = random.Random(7)

def plate(w, h):
    """One blue armour face: lit top/left edge, shaded right/bottom edge, mottled middle."""
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
    for y in range(h):
        px[y][0] = HI
        if w > 2 and y not in (0,):
            px[y][w - 1] = DARK
    px[0][0] = LIGHT
    if h > 2:
        px[h - 1][0] = DARK
    return px

def paint_box(uv, w, h, d, face_fn):
    u, v = uv
    # (x, y, width, height) of each face in the net: top, bottom, east, north, west, south
    faces = {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
             'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}
    for name, (fx, fy, fw, fh) in faces.items():
        px = face_fn(name, fw, fh)
        for y in range(fh):
            for x in range(fw):
                tex.putpixel((u + fx + x, v + fy + y), (*px[y][x], 255))

def blue(name, fw, fh):
    px = plate(fw, fh)
    if name == 'down':                               # underside: in shadow
        px = [[tuple(int(c * 0.7) for c in p) for p in row] for row in px]
    return px

# Knee-joint iron for the bearing caps (side face of knee_joint_l, 5 wide x 4 tall).
knee = next(e for e in els if e['name'] == 'knee_joint_l')
ku, kv = knee['uv_offset']
def iron(name, fw, fh):
    src = original.crop((ku + 5, kv + 5, ku + 10, kv + 9))
    src = src.resize((fw, fh), Image.Resampling.NEAREST)
    return [[src.getpixel((x, y))[:3] for x in range(fw)] for y in range(fh)]

parts = []
def cube(name, start, size, painter, mirror=False, uv=None):
    w, h, d = size
    if uv is None:
        uv = pack(w, h, d)
        paint_box(uv, w, h, d, painter)
    parts.append(dict(name=name, from_=start, to=[a + b for a, b in zip(start, size)],
                      uv_offset=uv, mirror_uv=mirror))
    return uv

# The hip axis is y 22.625 (thigh pivots at x +-4.5); the thighs (inflated) span x +-8.75, z +-3.75.
cube('pelvis_shell', [-9, 22.625, -5], [18, 4, 10], blue)
cube('crotch', [-1, 20.625, -4], [2, 2, 8], blue)
cap = cube('hip_cap_l', [-10, 20.625, -2], [1, 4, 4], iron)
cube('hip_cap_r', [9, 20.625, -2], [1, 4, 4], iron, mirror=True, uv=cap)

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
hexpx = ''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed)
tex.resize((tex.width * 4, tex.height * 4), Image.Resampling.NEAREST).save(scratch/'hips_preview.png')
(scratch/'hips-plan.json').write_text(json.dumps(dict(
    remove=[e['uuid'] for e in remove], parts=parts, pixels=hexpx)))
print('removed', sorted(REMOVE))
print('parts', [(p['name'], p['uv_offset']) for p in parts], 'changed px', len(changed))
