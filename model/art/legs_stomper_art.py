"""Heavier legs on a wider stance, the shin and foot one armoured boot (2026-10-14, animation overhaul).

Why: the top half (33 px of shoulders, a 20 px deep torso) stood on two thin columns close together (thighs 6 wide,
both legs 17 px across, the boots touching), which looked top-heavy and stiff.
v1 (Fallout-style stompers: hips +-6.5, thighs 8x6x9, a greave over an 11x16 boot round a hidden ankle) was rejected:
"the boots look like most of it is missing" (from the side the lower leg was only a greave in front of a low shaft)
and the legs looked comically large under the upper body. v2, between the old legs and v1 (right side; left mirrors):
  hips      leg pivots x +-4.5 -> +-5.5 (11 px apart); pelvis shell 16 -> 18 wide (old faces widened in the middle)
  thigh     7x6x8 (inflate 0.25) over the knee's 2 px back cut + thigh_knee 7x2x4 in front (inflate 0.15)
  joints    hip and knee joints 6x5x6 (were 5x4x5), the old joint art scaled up
  shin      a solid 8x5x8 box (shin) from the boot up to 5 px under the knee, and the knee guard 8x4x4 (shin_knee) in
            front above it: the back of the knee is open so it bends cleanly to ~100 deg
  boot      on the foot bone, in the shin's blue armour: an ankle cuff 9x2x10 (inflate 0.25) round the shin's foot,
            boot_upper 9x2x11 with a grey welt, a toe cap 9x2x3 turned down 20 deg, a dark 9x1x14 sole
The ankle bends freely inside the roomy cuff (as the boots before: model/art/boot_art.py); kit.json "ankle" keeps it
within -30..+40 deg, past which the boot rolls on its heel or toe.
Bone pivots keep their heights (hip 22.625, knee 13.625, ankle 3.625), so leg lengths and the animations' IK hold.
Re-running rebuilds every leg cube (old and v1 names) from a fresh snapshot of the live project.

Run: uv run --with pillow python3 model/art/legs_stomper_art.py
Input .scratch/stomper-source.json ({model, texture} of the live project); output .scratch/stomper-plan.json
(removed cubes, group origins, new cubes, pelvis edit, changed pixels as hex) and .scratch/stomper-preview.png.
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'stomper-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy()
W, H = tex.size
els = m['elements']
by = {e['name']: e for e in els}
rng = random.Random(29)

HI, BASE, MID, DARK, EDGE = (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)
STEEL, STEEL_LO = (128, 134, 140), (95, 100, 106)
SOLE = (40, 44, 48)
IRON = [(47, 52, 55), (37, 41, 44), (59, 66, 70), (47, 52, 55), (26, 29, 31)]
X = 5.5                                   # new leg axis (was 4.5; v1 6.5)
REMOVED = [p + side for side in ('_r', '_l') for p in
           ('thigh', 'thigh_knee', 'hip_joint', 'knee_joint', 'shin', 'shin_knee',
            'stomper_sole', 'stomper_foot', 'stomper_toe', 'stomper_ankle',
            'shin_guard', 'shin_core', 'boot_shaft', 'boot_upper', 'boot_toe', 'boot_sole', 'boot_cuff')]   # re-runs rebuild these


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


def faces(w, h, d):
    """Box-UV face rects (x, y, w, h) relative to the net's corner."""
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
            'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


def plate(w, h, top_edge=True, bottom_edge=True, shade=1.0):
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
    return [[tuple(int(c * shade) for c in p) for p in row] for row in px]


def iron(w, h):
    return [[IRON[(x * 7 + y * 3 + rng.randrange(2)) % 5] for x in range(w)] for y in range(h)]


def solid(c, w, h):
    return [[c] * w for _ in range(h)]


# ---- free texture space: transparent and outside every net we keep -------------------------------------------
used = set()
for c in els:
    w, h, d = size(c)
    u, v = map(int, c.get('uv_offset', [0, 0]))
    pts = {(x, y) for y in range(v, v + d + h) for x in range(u, u + 2 * (w + d))}
    if c['name'] in REMOVED or c['name'] == 'pelvis_shell':
        for pt in pts:
            tex.putpixel(pt, (0, 0, 0, 0))
    else:
        used |= pts
# nets shared with cubes we keep (the joints' nets are also the shoulders' and elbows') must survive: repaint them
for c in els:
    if c['name'] not in REMOVED and c['name'] != 'pelvis_shell':
        w, h, d = size(c)
        u, v = map(int, c.get('uv_offset', [0, 0]))
        for y in range(v, v + d + h):
            for x in range(u, u + 2 * (w + d)):
                tex.putpixel((x, y), live.getpixel((x, y)))
used |= {(x, y) for y in range(H) for x in range(W) if tex.getpixel((x, y))[3]}


def pack(sz):
    w, h, d = sz
    nw, nh = 2 * (w + d), d + h
    for v in range(H - nh + 1):
        for u in range(W - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u, u + nw)}
            if not used & pts:
                used.update(pts)
                return [u, v]
    raise SystemExit(f'no room for {sz}')


def paint(uv, sz, painter):
    for f, (x, y, w, h) in faces(*sz).items():
        for j, row in enumerate(painter(f, w, h)):
            for i, p in enumerate(row):
                tex.putpixel((uv[0] + x + i, uv[1] + y + j), (*p[:3], 255))


# ---- painters ---------------------------------------------------------------------------------------------------
def thigh(f, w, h):
    if f == 'up':
        return iron(w, h)                                  # inside the hip shell
    if f == 'down':
        return plate(w, h, False, False, 0.62)             # the knee pit's ceiling
    return plate(w, h, bottom_edge=False)


def thigh_knee(f, w, h):
    if f == 'south':
        return plate(w, h, False, False, 0.62)             # inside the knee pit
    if f in ('up', 'down'):
        return plate(w, h, False, False, 0.8)
    return plate(w, h, top_edge=False)


def guard(f, w, h):
    if f == 'south':
        return plate(w, h, False, False, 0.62)             # its back, inside the knee pit
    if f == 'down':
        return iron(w, h)
    px = plate(w, h)
    return px


def shin(f, w, h):
    if f == 'up':
        return plate(w, h, False, False, 0.62)             # the floor of the knee pit (its front half is covered)
    if f == 'down':
        return iron(w, h)                                  # inside the cuff
    return plate(w, h, bottom_edge=False)                  # lit top row = the cut edge at the back


def cuff(f, w, h):
    if f in ('up', 'down'):
        return iron(w, h)                                  # round the shin / on the foot
    return plate(w, h, top_edge=False, bottom_edge=False)  # no seam lines: the shin runs on into the boot


def upper(f, w, h):
    if f == 'down':
        return solid(SOLE, w, h)
    if f == 'up':
        return plate(w, h)
    px = plate(w, h, bottom_edge=False)
    for x in range(w):                                     # grey welt where the upper meets the sole
        px[h - 1][x] = STEEL if x % 3 else STEEL_LO
    return px


def toe(f, w, h):
    if f == 'down':
        return solid(SOLE, w, h)
    px = plate(w, h)
    if f == 'south':
        return plate(w, h, False, False, 0.8)
    return px


def sole(f, w, h):
    if f in ('down', 'up'):
        return solid(SOLE, w, h)
    px = solid(SOLE, w, h)                                 # dark iron sides with tread notches
    for x in range(w):
        if x % 3 == 1:
            px[h - 1][x] = (26, 29, 31)
        px[0][x] = (59, 66, 70)
    return px


old_joint = by['knee_joint_r']


def joint(f, w, h):
    """The old 5x4x5 joint art, scaled to the new size (same face, sampled by fraction)."""
    ow, oh, od = size(old_joint)
    ou, ov = map(int, old_joint['uv_offset'])
    fx, fy, fw, fh = faces(ow, oh, od)[f]
    return [[live.getpixel((ou + fx + min(fw - 1, i * fw // w), ov + fy + min(fh - 1, j * fh // h)))
             for i in range(w)] for j in range(h)]


# ---- pelvis shell 16x4x10 -> 20x4x10: widen its faces in the middle (edges and details kept) ---------------------
pel = by['pelvis_shell']
pw, ph, pd = size(pel)
NW = 18


def widen(f, w, h):
    ou, ov = map(int, pel['uv_offset'])
    fx, fy, fw, fh = faces(pw, ph, pd)[f]
    cols = list(range(fw)) if fw == w else list(range(fw // 2)) + [fw // 2 - 1 - (k % 2) for k in range(w - fw)] + list(range(fw // 2, fw))
    return [[live.getpixel((ou + fx + cols[i], ov + fy + j)) for i in range(w)] for j in range(h)]


# ---- plan --------------------------------------------------------------------------------------------------------
PARTS = [  # name, bone, from, to (right side), inflate, painter, rotation, rotation origin
    ('thigh', 'leg', [X - 3.5, 15.625, -4], [X + 3.5, 21.625, 4], 0.25, thigh, None, None),
    ('thigh_knee', 'leg', [X - 3.5, 13.625, -4], [X + 3.5, 15.625, 0], 0.15, thigh_knee, None, None),
    ('hip_joint', 'leg', [X - 3, 20.125, -3], [X + 3, 25.125, 3], 0, joint, None, None),
    ('knee_joint', 'shin', [X - 3, 11.125, -3], [X + 3, 16.125, 3], 0, joint, None, None),
    ('shin', 'shin', [X - 4, 3.625, -4], [X + 4, 8.625, 4], 0, shin, None, None),
    ('shin_knee', 'shin', [X - 4, 8.625, -4], [X + 4, 12.625, 0], 0, guard, None, None),
    ('boot_cuff', 'foot', [X - 4.5, 2.625, -5], [X + 4.5, 4.625, 5], 0.25, cuff, None, None),
    ('boot_upper', 'foot', [X - 4.5, 0.625, -6], [X + 4.5, 2.625, 5], 0, upper, None, None),
    ('boot_toe', 'foot', [X - 4.5, 0.625, -9], [X + 4.5, 2.625, -6], 0.05, toe, [-20, 0, 0], [X, 2.625, -6]),
    ('boot_sole', 'foot', [X - 4.5, -0.375, -9], [X + 4.5, 0.625, 5], 0, sole, None, None),
]
nets = {}
def net_area(p):
    w, h, d = (math.ceil(p[3][i] - p[2][i] - 1e-6) for i in range(3))
    return 2 * (w + d) * (d + h)


order = sorted(PARTS, key=lambda p: -net_area(p))   # biggest nets first
for name, bone, f, t, inf, painter, rot, ro in order:
    sz = [math.ceil(b - a - 1e-6) for a, b in zip(f, t)]
    if name == 'knee_joint' and 'hip_joint' in nets:       # same size and art: share the net
        nets[name] = nets['hip_joint']
        continue
    uv = pack(sz)
    paint(uv, sz, painter)
    nets[name] = uv
puv = pack([NW, ph, pd])
paint(puv, [NW, ph, pd], widen)

cubes = []
for side, sx in (('r', 1), ('l', -1)):
    prefix = 'right_' if side == 'r' else 'left_'
    for name, bone, f, t, inf, painter, rot, ro in PARTS:
        fr, to = list(f), list(t)
        if sx < 0:
            fr[0], to[0] = -t[0], -f[0]
        pivot = {'leg': 22.625, 'shin': 13.625, 'foot': 3.625}[bone]
        c = dict(name=f'{name}_{side}', group=prefix + bone, from_=fr, to=to, origin=[sx * X, pivot, 0],
                 uv_offset=nets[name], mirror_uv=(side == 'r'))
        if inf:
            c['inflate'] = inf
        if rot:
            c['rotation'] = rot
            c['origin'] = [sx * ro[0], ro[1], ro[2]]
        cubes.append(c)
groups = {f'{p}_{b}': [sx * X, y, 0] for p, sx in (('right', 1), ('left', -1))
          for b, y in (('leg', 22.625), ('shin', 13.625), ('foot', 3.625))}
pelvis = dict(name='pelvis_shell', from_=[-NW / 2, 22.625, -5], to=[NW / 2, 26.625, 5], uv_offset=puv)

changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W)
           if tex.getpixel((x, y)) != live.getpixel((x, y))]
(scratch/'stomper-plan.json').write_text(json.dumps(dict(
    removed=REMOVED, groups=groups, cubes=cubes, pelvis=pelvis,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
prev = tex.resize((W * 4, H * 4), Image.NEAREST)
bg = Image.new('RGBA', prev.size, (255, 0, 255, 255))
bg.alpha_composite(prev)
bg.save(scratch/'stomper-preview.png')
print('nets', nets, 'pelvis', puv, len(changed), 'px')
