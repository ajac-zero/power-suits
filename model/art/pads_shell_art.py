"""Shoulder pads as shells: pad and upper arm one piece (2026-10-19, user: option A of .scratch/pads/sketch_v2.png).

Why: after the elbow rework (elbow_art.py) the upper arm was a dark core inside a 12 px deep box pad plus a separate
blue guard below it at the back. Now the pad hugs the arm (9.5 deep: 1 px plates round the 7.5 px arm) and runs down
to the elbow at the back and the outer side; its lower edge follows the elbow's ramp (rising 30 deg to the front from
the back plate), so from behind it is one plate from the shoulder to the elbow and from the front the dark joint shows
under it. Width and top kept (x 7.5..16, top 43.625): the inner edge stays at 7.5 so its top front corner keeps clear
of the gorget when the arm splays out.

Pieces (right side, on right_pad; y relative to the elbow pivot 28.625, front = -z; the left side mirrors):
  pad_top     x 7.5..16, 13..15, z +-4.75
  pad_back    1 px back plate, 0..13 (the old ua_back's place, now also the pad's back)
  pad_front   1 px front plate, from 5.0 (just above the slope's front end) up to 13
  pad_wall    1 px outer wall between them, from 4.5 up to 13 (the mids and the ramp below it)
  pad_ramp    the underside: a slab turned 30 deg about the back plate's front bottom edge (z 3.75)
  pad_mid_1/2 blocks filling above the slope up to 4.5 (into the dark core)
  elbow_joint top 2.5 -> 2.8: the slope starts 1 px further back, so the joint must reach 0.3 higher to meet it at
              the rest bend (no slot through the wedge from the side)
Faces are resampled from the old pad's faces of the same side (keep the rim rows / columns and the red band, tile the
middle), so each side keeps its own art and mirror flag.
Run: uv run --with pillow --with shapely python3 model/art/pads_shell_art.py
Input .scratch/pads/source.json (skill scripts/snapshot.mjs, after elbow_art.py); output .scratch/pads/plan.json for
elbow_apply.js (same plan format: removed, edits, cubes, pixels).
"""
import base64, io, json, math, random
from pathlib import Path
from PIL import Image
from shapely.geometry import Polygon
from shapely.ops import unary_union

A, T = 30.0, 1.5
PIV, ZB, ZF, ZP = 28.625, 3.75, -4.75, 4.75     # elbow pivot y; back plate front face; pad front / back faces
X0, X1, XW = 7.5, 16.0, 15.0                    # pad inner / outer x, outer wall's inner face
TOP, ROOF, MIDTOP, FRONT_BOT = 13.0, 15.0, 4.5, 5.0
JOINT_TOP = 2.8

s = json.loads(Path('.scratch/pads/source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
by = {e['name']: e for e in m['elements']}
rng = random.Random(7)

ta = math.tan(math.radians(A)); g = lambda z: (ZB - z) * ta
TV = T / math.cos(math.radians(A)); L = (ZB - ZF) / math.cos(math.radians(A))
m1 = ZB - (TV - 0.1) / ta + 0.02
m2 = m1 - (TV - 0.1) / ta + 0.02
assert g(m2) + TV >= MIDTOP and g(ZF) < FRONT_BOT and g(ZF + 1) + TV >= FRONT_BOT
print(f'slope front {g(ZF):.2f} (front plate from {FRONT_BOT}), mids {m1:.2f} {m2:.2f}')

PARTS = [   # name, from (x, yrel, z), to, rotation X, hinge (yrel, z), face recipe
    ('pad_top', [X0, TOP, ZF], [X1, ROOF, ZP], None, None, 'top'),
    ('pad_back', [X0, 0, ZB], [X1, TOP, ZP], None, None, 'back'),
    ('pad_front', [X0, FRONT_BOT, ZF], [X1, TOP, ZF + 1], None, None, 'front'),
    ('pad_wall', [XW, MIDTOP, ZF + 1], [X1, TOP, ZB], None, None, 'wall'),
    ('pad_mid_1', [X0, g(m1) + 0.1, m1], [X1, MIDTOP, ZB], None, None, 'mid'),
    ('pad_mid_2', [X0, g(m2) + 0.1, m2], [X1, MIDTOP, m1], None, None, 'mid'),
    ('pad_ramp', [X0 + 0.05, 0, ZB - L], [X1 - 0.05, T, ZB], A, (0, ZB), 'ramp'),
]

# ---- side-view check ----------------------------------------------------------------------------------------------
def poly(f, t, rot, hinge):
    pts = [(f[2], f[1]), (t[2], f[1]), (t[2], t[1]), (f[2], t[1])]
    if rot:
        a = math.radians(rot); c, sn = math.cos(a), math.sin(a); hy, hz = hinge
        pts = [(hz + (y - hy) * sn + (z - hz) * c, hy + (y - hy) * c - (z - hz) * sn) for z, y in pts]
    return Polygon(pts)
u = unary_union([poly(f, t, r, h) for _, f, t, r, h, _ in PARTS])
target = Polygon([(ZP, 0), (ZP, ROOF), (ZF, ROOF), (ZF, g(ZF)), (ZB, 0)])
poke, hole = u.difference(target), target.difference(u)
print(f'side: pokes out {poke.area:.3f} px2 {[tuple(round(v, 2) for v in p.bounds) for p in getattr(poke, "geoms", [poke]) if p.area > 0.01]}, '
      f'holes {hole.area:.3f} px2 {[tuple(round(v, 2) for v in p.bounds) for p in getattr(hole, "geoms", [hole]) if p.area > 0.01]}')

# ---- texture -------------------------------------------------------------------------------------------------------
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def faces(w, hh, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, hh), 'north': (d, d, w, hh),
            'west': (d + w, d, d, hh), 'south': (2 * d + w, d, w, hh)}
def net_pts(c):
    w, hh, d = size(c['from'], c['to']); u0, v0 = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v0, v0 + d + hh) for x in range(u0, u0 + 2 * (w + d))}
def old_face(name, f):
    c = by[name]; w, hh, d = size(c['from'], c['to']); u0, v0 = c['uv_offset']; x, y, fw, fh = faces(w, hh, d)[f]
    return [[live.getpixel((u0 + x + i, v0 + y + j)) for i in range(fw)] for j in range(fh)]
def resample(src, w, hh, top=1, bottom=1, left=1, right=1):
    """Keep `top`/`bottom` rows and `left`/`right` columns, tile the middle to the new size."""
    sh, sw = len(src), len(src[0])
    def idx(n_new, n_old, a, b):
        mid = list(range(a, n_old - b)) or [n_old // 2]
        if n_new <= a + b: return (list(range(a)) + list(range(n_old - b, n_old)))[:n_new] if n_new >= 1 else []
        return list(range(a)) + [mid[k % len(mid)] for k in range(n_new - a - b)] + list(range(n_old - b, n_old))
    rows, cols = idx(hh, sh, min(top, sh), min(bottom, sh - top)), idx(w, sw, min(left, sw), min(right, sw - left))
    return [[src[r][c] for c in cols] for r in rows]
def solid(src_px, w, hh): return [[src_px] * w for _ in range(hh)]

REMOVED = [f'{n}_{sd}' for sd in 'rl' for n in ('pad_top', 'pad_wall', 'pad_front', 'pad_back', 'ua_back', 'ua_mid_1',
                                                 'ua_mid_2', 'ua_ramp')]
KEEP = {tuple(c.get('uv_offset', [0, 0])) for c in m['elements'] if c['name'] not in REMOVED}
for c in m['elements']:
    if c['name'] in REMOVED and tuple(c.get('uv_offset', [0, 0])) not in KEEP:
        for p in net_pts(c): tex.putpixel(p, (0, 0, 0, 0))
used = set()
for c in m['elements']:
    if c['name'] not in REMOVED: used |= net_pts(c)
used |= {(x, y) for y in range(H) for x in range(W) if tex.getpixel((x, y))[3]}
def pack(sz):
    w, hh, d = sz; nw, nh = 2 * (w + d), d + hh
    for v in range(H - nh + 1):
        for u_ in range(W - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u_, u_ + nw)}
            if not used & pts:
                used.update(pts); return [u_, v]
    raise SystemExit(f'no room for {sz}')

def recipe(kind, sd, sz):
    """face -> pixel rows, from the old pad of side sd. Every pad cube is mirror_uv (like the old ones), so the box-UV
    region 'west' shows on the right pad's outer side (+x) but 'east' on the left pad's outer side (-x): OUT / IN below.
    (The first build painted 'west' as outer on both sides: the left pad's outer faces came out dark.)"""
    w, hh, d = sz
    OUT, IN = ('west', 'east') if sd == 'r' else ('east', 'west')
    o = lambda n, f: old_face(f'pad_{n}_{sd}', f)
    outer = o('wall', OUT)                          # 12x9: light row, red, dark red, dark blue, blue..., grey row; grey edge columns
    inner_dark = o('wall', IN)
    rim = outer[-1][len(outer[0]) // 2]             # the grey rim
    edge_col = [[r[0]] for r in outer]               # an edge column: light, red, dark red, then grey
    blue = outer[5][5]
    out = {}
    if kind == 'top':
        out['up'] = resample(o('top', 'up'), w, d, 1, 1, 2, 1)
        out['down'] = resample(o('top', 'down'), w, d)
        for f in (IN, OUT, 'north', 'south'):
            fw = faces(w, hh, d)[f][2]; out[f] = resample(o('top', f), fw, hh)
    elif kind == 'back':
        out['south'] = resample(o('back', 'south'), w, hh, 1, 1, 1, 1)
        out['north'] = resample(o('back', 'north'), w, hh)
        out[OUT] = resample(edge_col, d, hh, 3, 1, 1, 0)
        out[IN] = resample(o('back', IN), d, hh, 1, 1, 1, 0)
        out['down'] = solid(rim, w, d); out['up'] = resample(o('back', 'up'), w, d)
    elif kind == 'front':
        out['north'] = resample(o('front', 'north'), w, hh, 4, 1, 1, 1)
        out['south'] = resample(o('front', 'south'), w, hh)
        out[OUT] = resample(edge_col, d, hh, 3, 1, 1, 0)
        out[IN] = resample(o('front', IN), d, hh, 1, 1, 1, 0)
        out['down'] = solid(rim, w, d); out['up'] = resample(o('front', 'up'), w, d)
    elif kind == 'wall':
        mid_cols = [r[1:-1] for r in outer[:-1]]     # no edge columns (the plates are the edges), no rim row
        out[OUT] = resample(mid_cols, d, hh, 4, 0, 0, 0)
        out[IN] = resample(inner_dark, d, hh)
        for f in ('north', 'south', 'up', 'down'):
            fw, fh = faces(w, hh, d)[f][2:]; out[f] = resample(inner_dark, fw, fh)
    elif kind == 'mid':
        plain = [r[1:-1] for r in outer[4:-1]]
        for f in (IN, OUT): out[f] = resample(plain, d, hh, 0, 0, 0, 0)
        for f in ('north', 'south', 'up', 'down'):
            fw, fh = faces(w, hh, d)[f][2:]; out[f] = resample(inner_dark, fw, fh)
    elif kind == 'ramp':                             # its sides are the rim along the slope; its underside the plate, shaded
        for f in (IN, OUT): out[f] = solid(rim, d, hh)
        plain = [r[1:-1] for r in outer[4:-1]]
        out['down'] = [[tuple(int(ch * 0.7) for ch in p[:3]) + (255,) for p in row] for row in resample(plain, w, d, 0, 0, 0, 0)]
        for f in ('up', 'north', 'south'):
            fw, fh = faces(w, hh, d)[f][2:]; out[f] = resample(inner_dark, fw, fh)
    return out

ARM = {'r': ('right_pad', 1), 'l': ('left_pad', -1)}
cubes = []
for name, f, t, rot, hinge, kind in PARTS:
    f = [f[0], f[1] + PIV, f[2]]; t = [t[0], t[1] + PIV, t[2]]; sz = size(f, t)
    for sd, (bone, sx) in ARM.items():
        uv = pack(sz)
        for fc, rows in recipe(kind, sd, sz).items():
            x, y, fw, fh = faces(*sz)[fc]
            assert len(rows) == fh and all(len(r) == fw for r in rows), (name, fc, len(rows), fh)
            for j, row in enumerate(rows):
                for i, px in enumerate(row): tex.putpixel((uv[0] + x + i, uv[1] + y + j), tuple(px[:3]) + (255,))
        fr, to = list(f), list(t)
        if sx < 0: fr[0], to[0] = -t[0], -f[0]
        c = dict(name=f'{name}_{sd}', group=bone, from_=fr, to=to, uv_offset=uv, mirror_uv=True, origin=[sx * 10.5, PIV, 0])
        if rot: c['rotation'] = [rot, 0, 0]; c['origin'] = [sx * 10.5, hinge[0] + PIV, hinge[1]]
        cubes.append(c)
EDITS = []
for sd in 'rl':
    c = by[f'elbow_joint_{sd}']; EDITS.append(dict(name=c['name'], frm=c['from'], to=[c['to'][0], PIV + JOINT_TOP, c['to'][2]]))
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/pads/plan.json').write_text(json.dumps(dict(removed=REMOVED, edits=EDITS, cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g_:02x}{b:02x}{a:02x}' for x, y, r, g_, b, a in changed))))
print(len(cubes), 'cubes', len(changed), 'px', {c['name']: (c['uv_offset'], size(c['from_'], c['to'])) for c in cubes})
