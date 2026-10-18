"""Drum hips (2026-10-18, user: thighs and hips designed as one piece, like the shin and the boot).

Why: the hip armour sat 1 px above the hip axis while the 8 px deep thighs swing 70 deg; a thigh's top corner rises
~4 sin(angle) px, so the thighs could only swing -15..+20 deg before hitting the V (walk needs 4..57, run -19..70).
Like the boot's heel (an arc about the ankle hinge), the thigh's top is now a drum centred on the hip axis under a hood
that stays outside the circle it sweeps: whatever the angle, nothing clips and no gap opens.

Side view (axis along x through the hip pivot y 22.625 z 0; angles from the front horizontal, up = 90):
  thigh   thigh_upper (on the kept thigh_main) up to 4 tan 22.5 above the axis, cap_c (flat top 4 above), cap_f / cap_b
          (the same box turned -45 / +45): the upper half of an octagon with faces 4 from the axis (corners 4.33).
          Only on the inner part of the thigh (x 1..XD): the hanging arms come to x ~7.2 between y 26 and 29 in the
          walk / run, so outside it the thigh keeps its old sloped top (thigh_top, 25 deg) and the drum shows its
          octagonal end. The old V's line is painted across the drum's front and back (V_LINE).
  hood    pieces outside the thigh's swept region (drum circle + its flat faces at the swing limits FWD / BACK, grown by
          ROOM for the gaits' 9 deg roll): a top block (under the chest, whose bottom is RH above the axis), front and
          back plates with fills behind them (no hollow from the side), slabs on the tangents at FWD and 135 deg; a
          steel trim row along the lower edges. On new bones under the root (hips > right_hood / left_hood, pivot = the
          hip pivot, turned like the leg's rest yaw -7 / +7): drum and hood share an axis (the body bone twists +-8 deg
          in the run, which would swing a hood on it into the drum)
  centre  a thin dark plate between the hoods (the legs roll toward each other); the V, band, fill, tip, the hip
          joints and hip_core are gone; the abdomen is shortened (it sits inside the hoods and the chest)
Checks printed: every hood piece clear of the swept thigh and inside its outline box, uncovered outline area.
Run: uv run --with pillow --with shapely python3 model/art/drum_hips_art.py
Input .scratch/hip/pre_drum.json (snapshot.mjs of 997aa9a, the geometry/texture base) and .scratch/hip/live.json (a
snapshot of the live project, the pixel diff base); output .scratch/drum-hips-plan.json for drum_hips_apply.js, which
replaces earlier drum pieces on re-runs.
"""
import base64, io, json, math, random
from pathlib import Path
from PIL import Image
from shapely.geometry import Polygon, Point
from shapely.ops import unary_union
from shapely import affinity

RD = 4.0                    # drum face distance (the thigh's half depth)
RF = 4.65                   # the front / back slabs: the thigh's flat faces at the swing limits are 4 + ROOM away
RH = 5.0                    # hood face distance (drum corners at 4.33; the gaits roll the thigh up to 9 deg)
FWD, BACK = 72, 28          # the swing the hood leaves room for: forward (front underside tangent), back (push-off)
PY, X = 22.625, 5.5         # hip pivot height, leg axis x (right)
ZF, ZB, TOP = -5.5, 5.75, 6.5   # hood outline (relative to the pivot): front face, back face, top
XD = 6.5                    # the drum (thigh_upper + caps) spans x 1..XD: further out the hanging arms come within ~7.2 of
                            # the centre between y 26 and 29 (walk/run swings), so the thigh keeps its old sloped top
                            # (thigh_top, 25 deg, outer corner 8.25 / 22.1) outside and the drum shows its octagonal end
XH0, XH1 = 0.25, XD + 0.1   # hood x span (right side); a thin dark centre plate between the two hoods
T8 = math.tan(math.radians(22.5))

def n(phi):                 # outward normal of the tangent at elevation phi (deg from the front horizontal): (y, z)
    a = math.radians(phi); return (math.sin(a), -math.cos(a))

def slab(phi, s0, s1, T, r):
    """Box with its inner face the tangent at elevation phi, distance r; s along the tangent (+ toward smaller phi
    side's neighbour... i.e. toward the front/top), thickness T outward. Returns (from_yz, to_yz, rot_x)."""
    # unrotated: inner face up (phi 90) at y = r; tangent coordinate s maps to -z
    return (r, -s1), (r + T, -s0), phi - 90

def rot_poly(y0, z0, y1, z1, rx):
    a = math.radians(rx); c, s = math.cos(a), math.sin(a)
    pts = [(y0, z0), (y1, z0), (y1, z1), (y0, z1)]
    return Polygon([(z * c + y * s, y * c - z * s) for y, z in pts])   # (z', y') after Blockbench's X rotation

# ---- thigh (leg bone): thigh_main up to the octagon's side corners, cap_c / cap_f / cap_b ---------------------------
BASE = Path('.scratch/hip/pre_drum.json')       # snapshot of 997aa9a (before any drum work): geometry and texture base
LIVE = Path('.scratch/hip/live.json')           # snapshot of the live project: the pixel diff is taken against it
s = json.loads(BASE.read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
by = {e['name']: e for e in m['elements']}
tm = by['thigh_main_r']
THIGH = [  # name, from, to (right side, model coords), inflate, rotation x
    ('thigh_upper', [1, 18.5, -4], [XD, PY + RD * T8, 4], 0, 0),   # on top of thigh_main (kept: 15.27..18.625)
    ('cap_c', [1, PY, -RD * T8], [XD, PY + RD, RD * T8], -0.1, 0),
    ('cap_f', [1, PY, -RD * T8], [XD, PY + RD, RD * T8], -0.15, -45),
    ('cap_b', [1, PY, -RD * T8], [XD, PY + RD, RD * T8], -0.2, 45),
]
# ---- hood (right_hood bone) ----------------------------------------------------------------------------------------
# Blockbench's X rotation by a: (y, z) -> (y cos a - z sin a, y sin a + z cos a). A box whose inner face is y = RH,
# turned by a = phi - 90, gets its inner face on the tangent at elevation phi (normal n(phi)), its z axis along
# t(phi) = (cos phi, sin phi) (y, z): so a slab is (RH, s0) .. (RH + T, s1) in (y, z) before turning.
def t(phi): a = math.radians(phi); return (math.cos(a), math.sin(a))
def rot_poly(y0, z0, y1, z1, a):
    a = math.radians(a); c, sn = math.cos(a), math.sin(a)
    return Polygon([(y * sn + z * c, y * c - z * sn) for y, z in [(y0, z0), (y1, z0), (y1, z1), (y0, z1)]])   # (z, y)
def on_tangent(phi, z_face):     # point of the tangent at phi with z = z_face, and its tangent coordinate
    y0, z0 = n(phi)[0] * RH, n(phi)[1] * RH; ty, tz = t(phi); k = (z_face - z0) / tz
    return y0 + ty * k, k
def isect(p1, p2):               # corner where the tangents at p1 and p2 meet: (y, z)
    a, b = n(p1), n(p2); det = a[0] * b[1] - a[1] * b[0]
    return (RH * (b[1] - a[1]) / det, RH * (a[0] - b[0]) / det)
def s_of(phi, y, z): ty, tz = t(phi); return y * ty + z * tz
TAN = [FWD, 90, 135, 180 - BACK]
V = [isect(TAN[i], TAN[i + 1]) for i in range(3)]
yF, _ = on_tangent(FWD, ZF); yB, _ = on_tangent(180 - BACK, ZB)
print(f'hood front edge rel {yF:.2f}, back edge rel {yB:.2f}; corners', [(round(y, 2), round(z, 2)) for y, z in V])
def sl(phi, pa, pb, T, ext=0.5):
    ss = sorted([s_of(phi, *pa), s_of(phi, *pb)]); return ((RH, ss[0] - ext), (RH + T, ss[1] + ext), phi - 90)
# what the hood must stay out of: the thigh (drum + straight sides) swept from -BACK to +FWD, grown by ROOM (the gaits
# also roll the thigh up to 9 deg and the leg is yawed 7: its drum ends move ~0.6 px)
ROOM = 0.6
thigh = Polygon([(z, y) for y, z in [(-14, -4), (RD * T8, -4), (RD, -RD * T8), (RD, RD * T8), (RD * T8, 4), (-14, 4)]])
swept = unary_union([affinity.rotate(thigh, -a, origin=(0, 0)) for a in range(-BACK, FWD + 1)]).buffer(ROOM)
box_ = Polygon([(ZF, -3), (ZF, TOP), (ZB, TOP), (ZB, -3)])
def clear(p): return p.intersection(swept).area < 1e-4
def inbox(p): return p.difference(box_).area < 1e-4
def lowest(z0, z1, top, inf):    # the lowest bottom for a plate z0..z1 that stays clear of the swept thigh
    lo, hi = -3.0, top
    for _ in range(30):
        mid = (lo + hi) / 2
        if clear(rot_poly(mid - inf, z0 - inf, top + inf, z1 + inf, 0)): hi = mid
        else: lo = mid
    return hi + 0.02
def fit_slab(phi, r, T, inf, pick=None):     # the longest slab on the tangent at phi (distance r, thickness T) inside the box
    a_ = math.radians(phi - 90); c, sn = math.cos(a_), math.sin(a_)       # and clear of the swept thigh
    def ok(sv):
        for y, z in ((r - inf, sv), (r + T + inf, sv)):
            yy, zz = y * c - z * sn, y * sn + z * c
            if not (ZF - 1e-6 <= zz <= ZB + 1e-6 and yy <= TOP + 1e-6): return False
        return clear(rot_poly(r - inf, sv - 0.02, r + T + inf, sv + 0.02, phi - 90))
    ss = [round(i * 0.04, 2) for i in range(-250, 251)]
    runs, cur = [], []
    for v in ss:
        if ok(v): cur.append(v)
        elif cur: runs.append(cur); cur = []
    if cur: runs.append(cur)
    best = max(runs, key=len) if pick is None else max(runs, key=lambda run: pick * run[-1] if pick > 0 else -run[0])
    return (r, best[0]), (r + T, best[-1]), phi - 90
FD, BD = 1.9, 2.0            # front / back fills: deep enough that no hollow between the plates and the slabs shows from the side
yFp = lowest(ZF, ZF + 1.0, TOP - 0.2, -0.04)   # the 1 px plates reach lowest
yBp = lowest(ZB - 1.0, ZB, TOP - 0.2, -0.04)
yFf = lowest(ZF + 0.5, ZF + FD, TOP - 0.2, -0.08)
yBf = lowest(ZB - BD, ZB - 0.5, TOP - 0.2, -0.08)
print(f'front plate down to rel {yFp:.2f} (world {PY + yFp:.2f}), back plate down to rel {yBp:.2f} (world {PY + yBp:.2f})')
HOOD = [  # name, (y0, z0), (y1, z1) unrotated, rotation x, inflate
    ('hood_top', (RH, ZF), (TOP, ZB), 0, 0),
    ('hood_front', (yFp, ZF), (TOP - 0.2, ZF + 1.0), 0, -0.04),
    ('hood_back', (yBp, ZB - 1.0), (TOP - 0.2, ZB), 0, -0.04),
    ('hood_ffill', (yFf, ZF + 0.5), (TOP - 0.3, ZF + FD), 0, -0.08),
    ('hood_bfill', (yBf, ZB - BD), (TOP - 0.3, ZB - 0.5), 0, -0.08),
    ('hood_fslab', *fit_slab(FWD, RF, 1.4, -0.12, pick=-1), -0.12),
    ('hood_mslab', *fit_slab(135, RH, 1.2, -0.16), -0.16),
]
polys = [rot_poly(a[0] - inf, a[1] - inf, b[0] + inf, b[1] + inf, rx) for _, a, b, rx, inf in HOOD]
hood = unary_union(polys)
for (name, *_), p in zip(HOOD, polys):
    print(f'{name:11s} clear of the thigh: {clear(p)}  inside the outline: {inbox(p)}')
gaps = box_.difference(swept).difference(hood)
print(f'outline not covered (outside the swept thigh): {gaps.area:.2f} px2')
Path('.scratch/drum-hips-geom.json').write_text(json.dumps(dict(swept=list(swept.exterior.coords),
    hood=[list(p.exterior.coords) for p in polys], outline=list(box_.exterior.coords), gaps=[list(g.exterior.coords) for g in getattr(gaps, 'geoms', [gaps]) if g.area > 0.01])))

# ---- texture: free the removed nets, pack and paint the new ones ---------------------------------------------------
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
rng = random.Random(41)
REMOVED = ['hip_v_r', 'hip_v_l', 'hip_band_r', 'hip_band_l', 'hip_fill', 'hip_tip', 'hip_joint_r', 'hip_joint_l',
           'cap_c_r', 'cap_c_l', 'cap_f_r', 'cap_f_l', 'cap_b_r', 'cap_b_l', 'thigh_upper_r', 'thigh_upper_l', 'hip_centre'] + \
          [f'hood_{p}_{sd}' for p in ('top', 'front', 'back', 'fslab', 'mslab', 'bslab', 'ffill', 'bfill') for sd in 'rl']   # (v1 pieces: re-runs replace them)
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def net_pts(c):
    w, h, d = size(c['from'], c['to']); u0, v0 = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v0, v0 + d + h) for x in range(u0, u0 + 2 * (w + d))}
used = set()
for c in m['elements']:
    if c['name'] in REMOVED and c['name'] not in ('hip_joint_r', 'hip_joint_l'):   # the hip joints share the knees' net
        for q in net_pts(c): tex.putpixel(q, (0, 0, 0, 0))
for c in m['elements']:
    if c['name'] not in REMOVED or c['name'] in ('hip_joint_r', 'hip_joint_l'):
        pts = net_pts(c); used |= pts
        for q in pts: tex.putpixel(q, live.getpixel(q))
used |= {(x, y) for y in range(H) for x in range(W) if tex.getpixel((x, y))[3]}
def pack(sz):
    w, h, d = sz; nw, nh = 2 * (w + d), d + h
    for v in range(H - nh + 1):
        for u_ in range(W - nw + 1):
            if not any((x, y) in used for y in range(v, v + nh) for x in range(u_, u_ + nw)):
                used.update((x, y) for y in range(v, v + nh) for x in range(u_, u_ + nw)); return [u_, v]
    raise SystemExit(f'no room for {sz}')
def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h), 'north': (d, d, w, h),
            'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}
HI, BASE, MID, DARK, EDGE = (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)
IRON = [(47, 52, 55), (37, 41, 44), (59, 66, 70), (47, 52, 55), (26, 29, 31)]
STEEL, STEEL_LO = (128, 134, 140), (95, 100, 106)
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
    return [[tuple(int(c * shade) for c in q) for q in row] for row in px]
def iron(w, h): return [[IRON[(x * 7 + y * 3 + rng.randrange(2)) % 5] for x in range(w)] for y in range(h)]
def P(kind):
    def thigh_main(f, w, h):                      # the thigh's sides up to the drum
        if f in ('up', 'down'): return iron(w, h)
        px = plate(w, h, top_edge=False, bottom_edge=False)
        if f in ('north', 'south'):               # front / back: the old V's line, the thigh top's 25 deg slope carried
            line = V_LINE(w, h)                   # across the drum (south: column 0 = the inner side; the north face's
            for c, r in line:                     # columns run the other way), dark like the V's gap
                px[r][c if f == 'south' else w - 1 - c] = IRON[1]
        return px
    def cap(f, w, h):                             # the drum's faces: its 'up' face is the outside
        if f == 'up': return plate(w, h, top_edge=False, bottom_edge=False)
        if f == 'down': return iron(w, h)
        return plate(w, h, top_edge=False, bottom_edge=False)
    def hood(f, w, h):                            # outside blue with a steel trim row along the lower edge (like the
        if f == 'down': return plate(w, h, False, False, 0.62)   # chest's grey frames), the underside in shadow
        px = plate(w, h, top_edge=f != 'up', bottom_edge=False)
        if f != 'up' and h > 1:
            for x in range(w): px[h - 1][x] = STEEL if x % 3 else STEEL_LO
        return px
    def slab(f, w, h):                            # inner face (down, before turning) shaded, the rest hidden or blue
        if f == 'down': return plate(w, h, False, False, 0.62)
        return plate(w, h, top_edge=False, bottom_edge=False)
    def centre(f, w, h): return iron(w, h)
    return dict(thigh_main=thigh_main, cap=cap, hood=hood, slab=slab, centre=centre)[kind]
def V_LINE(w, h):     # pixels (col, row) of the slope line on thigh_upper's front: y = 18.74 + 0.466 * (distance from x 1)
    top, ht, wd = PY + RD * T8, PY + RD * T8 - 18.5, XD - 1
    out = []
    for c in range(w):
        y = 18.74 + math.tan(math.radians(25)) * (c + 0.5) * wd / w
        r = int((top - y) / (ht / h))
        if 0 <= r < h: out.append((c, r))
    return out
LEG_PARTS = [(nm, f, t, inf, rx, 'thigh_main' if nm == 'thigh_upper' else 'cap') for nm, f, t, inf, rx in THIGH]
HOOD_PARTS = []
for name, a, b, rx, inf in HOOD:
    HOOD_PARTS.append((name, [XH0, PY + a[0], a[1]], [XH1, PY + b[0], b[1]], inf, rx, 'slab' if rx or 'fill' in name else 'hood'))
nets = {}
for name, f, t, inf, rx, kind in sorted(LEG_PARTS + HOOD_PARTS, key=lambda p: -2 * (lambda z: (z[0] + z[2]) * (z[2] + z[1]))(size(p[1], p[2]))):
    sz = size(f, t)
    if name in ('cap_f', 'cap_b') and 'cap_c' in nets: nets[name] = nets['cap_c']; continue
    uv = pack(sz); nets[name] = uv
    for fc, (x, y, w, h) in faces(*sz).items():
        for j, row in enumerate(P(kind)(fc, w, h)):
            for i, q in enumerate(row): tex.putpixel((uv[0] + x + i, uv[1] + y + j), (*q, 255))
    if name == 'cap_c': nets['cap_f'] = nets['cap_b'] = uv
CENTRE = ('hip_centre', [-0.25, 19.5, -4.5], [0.25, PY + RH, 4.5])
csz = size(CENTRE[1], CENTRE[2]); cuv = pack(csz)
for fc, (x, y, w, h) in faces(*csz).items():
    for j, row in enumerate(iron(w, h)):
        for i, q in enumerate(row): tex.putpixel((cuv[0] + x + i, cuv[1] + y + j), (*q, 255))
cubes = []
for side, sx in (('r', 1), ('l', -1)):
    for name, f, t, inf, rx, kind in LEG_PARTS + HOOD_PARTS:
        fr, to = list(f), list(t)
        if sx < 0: fr[0], to[0] = -t[0], -f[0]
        hood_part = name.startswith('hood')
        c = dict(name=f'{name}_{side}', group=('right_' if sx > 0 else 'left_') + ('hood' if hood_part else 'leg'),
                 from_=fr, to=to, origin=[sx * X, PY, 0], uv_offset=nets[name], mirror_uv=side == 'r')
        inf2 = inf - (0.05 if (hood_part and sx < 0) else 0)   # the two hoods overlap in the middle (leg yaw): offset faces
        if inf2: c['inflate'] = round(inf2, 3)
        if rx: c['rotation'] = [rx, 0, 0]
        cubes.append(c)
cubes.append(dict(name='hip_centre', group='hips', from_=CENTRE[1], to=CENTRE[2], origin=[0, PY, 0], uv_offset=cuv, mirror_uv=False))
groups = [dict(name='hips', parent='marine', origin=[0, PY, 0], rotation=[0, 0, 0]),
          dict(name='right_hood', parent='hips', origin=[X, PY, 0], rotation=[0, -7, 0]),
          dict(name='left_hood', parent='hips', origin=[-X, PY, 0], rotation=[0, 7, 0])]
ab = by['abdomen']
edits = [dict(name='abdomen', from_=[ab['from'][0], 27.65, ab['from'][2]], to=ab['to'])]
ls = json.loads(LIVE.read_text()); cur = Image.open(io.BytesIO(base64.b64decode(ls['texture'].split(',')[1]))).convert('RGBA')
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != cur.getpixel((x, y))]
restore = [dict(name=e['name'], group=('right_' if e['name'].endswith('_r') else 'left_') + 'leg', from_=e['from'], to=e['to'],
                origin=e['origin'], rotation=e.get('rotation'), inflate=e.get('inflate'), uv_offset=e['uv_offset'],
                mirror_uv=e.get('mirror_uv', False)) for e in m['elements'] if e['name'] in ('thigh_top_r', 'thigh_top_l')]
Path('.scratch/drum-hips-plan.json').write_text(json.dumps(dict(removed=REMOVED, groups=groups, cubes=cubes, edits=edits, restore=restore,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g_:02x}{b:02x}{a:02x}' for x, y, r, g_, b, a in changed))))
print(len(cubes), 'cubes', len(changed), 'px', nets)
