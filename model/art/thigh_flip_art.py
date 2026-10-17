"""Thigh bottom sloping the other way (2026-10-17, user): low in front, rising toward the back.

Why: the armour faces forward; the back of the leg may show more joint so the knee can bend. The thigh's underside used
to be parallel to the shin's top (both falling toward the back, v9). Mirrored, the thigh comes down over the front of
the knee (a thin dark band above the knee cap) and opens a wedge behind it, so the shin's back has room when the knee
folds. Same angle as the shin's top in the neutral pose (27.3 deg, mirrored), the shin untouched.

Built like the shin's top, upside down (side view, thigh-local, the leg's right side; the left mirrors):
  thigh_front  a 1 px plate at the front, flat bottom FRONT_GAP above the shin's knee cap
  thigh_ramp   the slope: a slab turned about the plate's back bottom edge, rising to the back face
  thigh_mid_f / thigh_mid_b  blocks filling the wedge above the slope up into thigh_main, their bottoms just above it
  thigh_main   the rest of the thigh, from the slope's back end up (replaces thigh_main, thigh_low, thigh_back)
Run: uv run --with pillow --with shapely python3 model/art/thigh_flip_art.py
Input .scratch/stomper-source.json (the skill's scripts/snapshot.mjs); output .scratch/thigh-flip-plan.json for
thigh_flip_apply.js and .scratch/thigh-flip-preview.png.
"""
import base64, io, json, math, random, sys
from pathlib import Path
from PIL import Image
from shapely.geometry import Polygon
from shapely.ops import unary_union

FRONT_GAP = 1.0            # dark band above the knee cap at the front (px)
B_WORLD = math.degrees(math.acos(8 / 9))   # the shin top's slope (27.3), mirrored
REL = 6.0                  # the thigh is turned 6 deg against the shin in the neutral pose
B = B_WORLD + REL          # the slope in thigh-local coordinates
T = 1.8                    # ramp thickness (its front end leans T sin B = 0.99 px, inside the 1 px plate)
X = 5.5
K = 12.625

s = json.loads(Path('.scratch/stomper-source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
by = {e['name']: e for e in m['elements']}
rng = random.Random(31)

# ---- where the shin's knee cap is (thigh-local) ------------------------------------------------------------------
def to_thigh(y, z, deg=-REL):
    a = math.radians(deg); c, sn = math.cos(a), math.sin(a); yy = y - K
    return K + yy * c - z * sn, yy * sn + z * c
cap = by['shin_front_r']
cap_top = max(to_thigh(cap['to'][1], z)[0] for z in (-5, -4))
YF = cap_top + FRONT_GAP
TB = math.tan(math.radians(B))
g = lambda z: YF + (z + 3) * TB          # the slope (the thigh's underside), z from -3 (plate) to 4 (back face)
YB = g(4)
TOP = 18.625
TV = T / math.cos(math.radians(B))       # the ramp's vertical thickness
assert g(0) + 0.1 <= YF + TV, 'mid_f would show under the slope'
Z_MB = 1.5
assert g(Z_MB) + 0.1 <= g(0) + TV, 'mid_b would show under the slope'
assert g(Z_MB) + TV >= YB, 'gap above the ramp behind mid_b'
print(f'cap top {cap_top:.2f}  front bottom {YF:.2f}  back bottom {YB:.2f}  slope {B:.1f} (thigh-local)')

L_RAMP = 7 / math.cos(math.radians(B))
PARTS = [  # name, from, to (right side, thigh-local), inflate, rotation, rotation origin, painter
    ('thigh_main',  [X - 4.5, YB, -4], [X + 4.5, TOP, 4], -0.05, None, None, 'main'),
    ('thigh_front', [X - 4.5, YF, -4], [X + 4.5, YB + 0.5, -3], 0, None, None, 'front'),
    ('thigh_mid_f', [X - 4.5, g(0) + 0.1, -3], [X + 4.5, YB + 0.5, 0], 0, None, None, 'mid'),
    ('thigh_mid_b', [X - 4.5, g(Z_MB) + 0.1, 0], [X + 4.5, YB + 0.5, Z_MB], 0, None, None, 'mid'),
    ('thigh_ramp',  [X - 4.5, YF, -3], [X + 4.5, YF + T, -3 + L_RAMP], -0.1, [-B, 0, 0], [X, YF, -3], 'ramp'),
]

# ---- side-view check: the pieces against the outline they should make ----------------------------------------------
def poly(f, t, inf, rot, ro):
    pts = [(f[1] - inf, f[2] - inf), (t[1] + inf, f[2] - inf), (t[1] + inf, t[2] + inf), (f[1] - inf, t[2] + inf)]
    if rot:
        a = math.radians(rot[0]); c, sn = math.cos(a), math.sin(a); o = (ro[1], ro[2])
        pts = [(o[0] + (y - o[0]) * c - (z - o[1]) * sn, o[1] + (y - o[0]) * sn + (z - o[1]) * c) for y, z in pts]
    return Polygon(pts)
u = unary_union([poly(f, t, i, r, o) for _, f, t, i, r, o, _ in PARTS])
target = Polygon([(YF, -4), (YF, -3), (YB, 4), (TOP, 4), (TOP, -4)])
poke, hole = u.difference(target), target.difference(u)
print(f'pokes out {poke.area:.3f} px2 (deepest {max([target.exterior.distance(__import__("shapely").geometry.Point(p)) for g_ in getattr(poke, "geoms", [poke]) if not g_.is_empty for p in g_.exterior.coords] or [0]):.2f}), holes {hole.area:.3f} px2')

# ---- texture: free the old thigh nets, pack the new ones -----------------------------------------------------------
REMOVED = [f'{n}_{sd}' for sd in 'rl' for n in ('thigh_main', 'thigh_low', 'thigh_back')]
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def net_pts(c):
    w, h, d = size(c['from'], c['to']); u0, v0 = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v0, v0 + d + h) for x in range(u0, u0 + 2 * (w + d))}
used = set()
for c in m['elements']:
    if c['name'] in REMOVED:
        for p in net_pts(c): tex.putpixel(p, (0, 0, 0, 0))
for c in m['elements']:
    if c['name'] not in REMOVED:
        pts = net_pts(c); used |= pts
        for p in pts: tex.putpixel(p, live.getpixel(p))
used |= {(x, y) for y in range(H) for x in range(W) if tex.getpixel((x, y))[3]}
def pack(sz):
    w, h, d = sz; nw, nh = 2 * (w + d), d + h
    for v in range(H - nh + 1):
        for u_ in range(W - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u_, u_ + nw)}
            if not used & pts:
                used.update(pts); return [u_, v]
    raise SystemExit(f'no room for {sz}')
def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h), 'north': (d, d, w, h),
            'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}
HI, BASE, MID, DARK, EDGE = (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)
IRON = [(47, 52, 55), (37, 41, 44), (59, 66, 70), (47, 52, 55), (26, 29, 31)]
def plate(w, h, top_edge=True, bottom_edge=True, shade=1.0):
    px = [[MID if (x + y < 3 and rng.random() < .6) else DARK if rng.random() < .06 else
           MID if rng.random() < .05 else BASE for x in range(w)] for y in range(h)]
    for y in range(h):
        px[y][0] = HI
        if w > 2: px[y][w - 1] = DARK
    if top_edge:
        for x in range(w): px[0][x] = HI
    if bottom_edge and h > 2:
        for x in range(w): px[h - 1][x] = EDGE
    return [[tuple(int(c * shade) for c in p) for p in row] for row in px]
def iron(w, h):
    return [[IRON[(x * 7 + y * 3 + rng.randrange(2)) % 5] for x in range(w)] for y in range(h)]
def painter(kind):
    def main(f, w, h):
        if f == 'up': return iron(w, h)                                 # inside the hips
        if f == 'down': return plate(w, h, False, False, 0.62)          # its underside behind the slope
        return plate(w, h, top_edge=False, bottom_edge=f == 'south')    # the back face ends in a dark edge row
    def front(f, w, h):
        if f == 'down': return plate(w, h, False, False, 0.62)          # the flat lip over the knee cap
        if f in ('up', 'south'): return iron(w, h)
        return plate(w, h, top_edge=False, bottom_edge=True)            # lit... dark edge along the bottom, front and sides
    def mid(f, w, h):
        if f in ('up', 'down', 'north', 'south'): return iron(w, h)    # inside
        return plate(w, h, top_edge=False, bottom_edge=False)           # the thigh's sides above the slope
    def ramp(f, w, h):
        if f == 'down': return plate(w, h, False, False, 0.62)          # the slope, seen from below and behind
        if f == 'up': return iron(w, h)
        return plate(w, h, top_edge=False, bottom_edge=True)            # its sides end in a dark edge along the slope
    return {'main': main, 'front': front, 'mid': mid, 'ramp': ramp}[kind]
cubes = []
for name, f, t, inf, rot, ro, kind in sorted(PARTS, key=lambda p: -2 * (lambda s_: (s_[0] + s_[2]) * (s_[2] + s_[1]))(size(p[1], p[2]))):
    sz = size(f, t); uv = pack(sz)
    for fc, (x, y, w, h) in faces(*sz).items():
        for j, row in enumerate(painter(kind)(fc, w, h)):
            for i, p in enumerate(row): tex.putpixel((uv[0] + x + i, uv[1] + y + j), (*p, 255))
    for side, sx in (('r', 1), ('l', -1)):
        fr, to = list(f), list(t)
        if sx < 0: fr[0], to[0] = -t[0], -f[0]
        c = dict(name=f'{name}_{side}', group=('right_' if sx > 0 else 'left_') + 'leg', from_=fr, to=to,
                 origin=[sx * X, 22.625, 0], uv_offset=uv, mirror_uv=(side == 'r'))
        if inf: c['inflate'] = inf
        if rot: c['rotation'] = rot; c['origin'] = [sx * ro[0], ro[1], ro[2]]
        cubes.append(c)
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/thigh-flip-plan.json').write_text(json.dumps(dict(removed=REMOVED, cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g_:02x}{b:02x}{a:02x}' for x, y, r, g_, b, a in changed))))
prev = tex.resize((W * 4, H * 4), Image.NEAREST); bg = Image.new('RGBA', prev.size, (255, 0, 255, 255)); bg.alpha_composite(prev)
bg.save('.scratch/thigh-flip-preview.png')
print(len(cubes), 'cubes', len(changed), 'px', {c['name']: c['uv_offset'] for c in cubes if c['name'].endswith('_r')})
inner = hole.difference(target.exterior.buffer(0.15))
print(f'holes away from the outline: {inner.area:.4f} px2', [tuple(round(v, 2) for v in g_.bounds) for g_ in getattr(inner, 'geoms', [inner]) if not g_.is_empty])
