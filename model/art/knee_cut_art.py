"""Knee cutout (animation overhaul, 2026-10-14): the back of the knee is cut away so the knee can bend.

Before: the 8x9x8 shin (z -4..4) started 1 px below the knee pivot (y 13.625) and was deeper than the thigh
(7 px + inflate 0.25 = z -3.75..3.75). Bending the knee swings the shin's back top corner up and forward, into the
back of the thigh: it clipped from ~15 degrees on (0.8 px at 30, 1.7 at 60, 2.75 at 90), so every walk, run, sneak
and landing showed the shin sinking into the thigh. A point of the shin at (z, y) relative to the pivot lands at
(-y, z) after 90 degrees of bend, so the material behind the pivot near it has to go, on both sides of the joint.
Bone pivots are unchanged (no animation changes); the dark knee joint (5x4x5, unchanged) shows in the pit.

Step 1 (SHIN_CUT 4, THIGH_CUT 0): shin 8x6x8 + front knee guard 8x3x4. Clean to 70 deg, 0.1 px at 80, 0.25 at 90.
Step 2 (SHIN_CUT 5, THIGH_CUT 2; the energetic sprint needs ~105 deg in the swing):
  shin        8x5x8, y 3.625..8.625 (lower part, full depth, keeps the old net position)
  shin_knee   8x4x4, y 8.625..12.625, z -4..0 (knee guard: the front half of the shin's top 4 px)
  thigh       6x6x7, y 15.625..21.625 (inflate 0.25, keeps the old net position)
  thigh_knee  6x2x4, y 13.625..15.625, z -3.5..0.5 (the front of the thigh's lower 2 px; inflate 0.15 so its
              faces don't share planes with the thigh's)
  Measured both ways (shin into thigh and thigh into shin): 0 up to 105 deg, 0.4 px at 110, 0.9 at 115.
Every new face copies the ORIGINAL (pre-cut) cube's pixel at the same 3D place, so plates and seams continue; the
pit's faces (inside the cut) are shaded darker so the cut reads as a recess, and the cut edges keep a lit top row.

Run: uv run --with pillow python3 model/art/knee_cut_art.py
Inputs: .scratch/knee-source.json ({model, texture} BEFORE step 1: the art is sampled from it) and
.scratch/knee-live.json (the live project now: its texture is edited, its nets are kept or freed).
Output .scratch/knee-plan.json (edits to existing cubes, new cubes, changed pixels as hex), .scratch/knee-preview.png.
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
SHIN_CUT, THIGH_CUT = 6, 2      # px of the shin's / thigh's back cut away below / above the knee pivot
PIVOT_Y = 13.625


def load(name):
    s = json.loads((scratch/name).read_text())
    if isinstance(s, str):
        s = json.loads(s)
    m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
    return m, Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')


om, original = load('knee-source.json')          # before any cut: the art to sample
lm, live = load('knee-live.json')                 # now: the texture we edit
tex = live.copy()
W, H = tex.size
old = {e['name']: e for e in om['elements']}
cur = {e['name']: e for e in lm['elements']}
REBUILT = ('shin_', 'shin_knee_', 'thigh_', 'thigh_knee_')


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


def rects(u, v, sz):
    """Box-UV face rects of a mirror_uv cube as Blockbench writes them (checked against shin_r)."""
    w, h, d = sz
    return {'north': (u + d + w, v + d, u + d, v + d + h), 'east': (u + 2 * d + w, v + d, u + d + w, v + d + h),
            'south': (u + 2 * d + 2 * w, v + d, u + 2 * d + w, v + d + h), 'west': (u + d, v + d, u, v + d + h),
            'up': (u + d, v + d, u + d + w, v), 'down': (u + d + w, v, u + d + 2 * w, v + d)}


# (axis, cube end at the rect's first column, at its last) and the same for rows; f = from, t = to
AX = {'north': ((0, 't', 'f'), (1, 't', 'f')), 'south': ((0, 'f', 't'), (1, 't', 'f')),
      'east': ((2, 't', 'f'), (1, 't', 'f')), 'west': ((2, 'f', 't'), (1, 't', 'f')),
      'up': ((0, 'f', 't'), (2, 't', 'f')), 'down': ((0, 'f', 't'), (2, 'f', 't'))}


def ends(c, a, e1, e2):
    return (c['from'][a] if e1 == 'f' else c['to'][a]), (c['from'][a] if e2 == 'f' else c['to'][a])


def texel_at(c, face, pos):
    """Pixel of the OLD cube c showing the 3D point pos on its face."""
    (au, u1e, u2e), (av, v1e, v2e) = AX[face]
    x1, y1, x2, y2 = c['faces'][face]['uv']
    a0, a1 = ends(c, au, u1e, u2e)
    b0, b1 = ends(c, av, v1e, v2e)
    fu = min(max((pos[au] - a0) / (a1 - a0), 0), 0.999)
    fv = min(max((pos[av] - b0) / (b1 - b0), 0), 0.999)
    px = x1 + math.floor(fu * (x2 - x1)) if x2 > x1 else x1 - 1 - math.floor(fu * (x1 - x2))
    py = y1 + math.floor(fv * (y2 - y1)) if y2 > y1 else y1 - 1 - math.floor(fv * (y1 - y2))
    return px, py


def darker(p, k=0.62):
    return tuple(int(c * k) for c in p[:3]) + (255,)


def rebuilt(name):
    return any(name == p + side for p in REBUILT for side in 'rl')


# free texture space: transparent and outside every net we keep (a cube without uv_offset uses [0,0]); the nets of
# the cubes we rebuild are cleared first
used = set()
for c in lm['elements']:
    w, h, d = size(c)
    u, v = map(int, c.get('uv_offset', [0, 0]))
    pts = {(x, y) for y in range(v, v + d + h) for x in range(u, u + 2 * (w + d))}
    if rebuilt(c['name']):
        for pt in pts:
            tex.putpixel(pt, (0, 0, 0, 0))
    else:
        used |= pts
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
    raise SystemExit('no room')


def paint(old, cube, uv, sz, special):
    """Paint cube's net at uv: every pixel copies the old cube's pixel at the same 3D place (same face direction);
    special(face, pos, col) may change it."""
    for face, (x1, y1, x2, y2) in rects(uv[0], uv[1], sz).items():
        fw, fh = abs(x2 - x1), abs(y2 - y1)
        (au, u1e, u2e), (av, v1e, v2e) = AX[face]
        a0, a1 = ends(cube, au, u1e, u2e)
        b0, b1 = ends(cube, av, v1e, v2e)
        for j in range(fh):
            for i in range(fw):
                px = (x1 + i) if x2 > x1 else (x1 - 1 - i)
                py = (y1 + j) if y2 > y1 else (y1 - 1 - j)
                pos = [0, 0, 0]
                pos[au] = a0 + (i + 0.5) / fw * (a1 - a0)
                pos[av] = b0 + (j + 0.5) / fh * (b1 - b0)
                col = special(face, pos, j, original.getpixel(texel_at(old, face, pos)))
                tex.putpixel((px, py), col)


def keep_net(c, sz):
    u, v = map(int, c['uv_offset'])
    w, h, d = sz
    used.update((x, y) for y in range(v, v + d + h) for x in range(u, u + 2 * (w + d)))
    return [u, v]


# the lower shin and the upper thigh keep their old net positions: reserve both sides before packing anything
for side in 'rl':
    for n, sz in ((f'shin_{side}', [8, 13.625 - SHIN_CUT - 3.625, 8]), (f'thigh_{side}', [6, 21.625 - 13.625 - THIGH_CUT, 7])):
        keep_net(old[n], [int(x) for x in sz])

edits, cubes = [], []
for side in 'rl':
    shin, thigh = old[f'shin_{side}'], old[f'thigh_{side}']
    sx0, sx1 = shin['from'][0], shin['to'][0]
    tx0, tx1 = thigh['from'][0], thigh['to'][0]
    cut_y = PIVOT_Y - SHIN_CUT
    lower = dict(shin, **{'from': [sx0, 3.625, -4], 'to': [sx1, cut_y, 4]})
    guard = dict(shin, **{'from': [sx0, cut_y, -4], 'to': [sx1, shin['to'][1], 0]})
    upper = dict(thigh, **{'from': [tx0, PIVOT_Y + THIGH_CUT, -3.5], 'to': [tx1, thigh['to'][1], 3.5]})
    tknee = dict(thigh, **{'from': [tx0, PIVOT_Y, -3.5], 'to': [tx1, PIVOT_Y + THIGH_CUT, 0.5]})

    def lower_px(face, pos, j, col, shin=shin):
        if face == 'up':                                   # the floor of the pit: shin blue (old underside), darker
            return darker(original.getpixel(texel_at(shin, 'down', pos)), 0.75)
        if face in ('south', 'east', 'west') and j == 0 and (face == 'south' or pos[2] > 0):
            return original.getpixel(texel_at(shin, face, [pos[0], shin['to'][1] - 0.5, pos[2]]))   # lit cut edge
        return col

    def pit_back(face, pos, j, col):
        return darker(col) if face == 'south' else col

    def upper_px(face, pos, j, col, thigh=thigh):
        if face == 'down':                                 # the pit's ceiling
            return darker(col)
        return col

    sz = {'lower': [8, int(cut_y - 3.625), 8], 'guard': [8, int(shin['to'][1] - cut_y), 4],
          'upper': [6, int(thigh['to'][1] - PIVOT_Y - THIGH_CUT), 7], 'tknee': [6, THIGH_CUT, 4]}
    luv = keep_net(shin, sz['lower'])
    paint(shin, lower, luv, sz['lower'], lower_px)
    guv = pack(sz['guard'])
    paint(shin, guard, guv, sz['guard'], pit_back)
    uuv = keep_net(thigh, sz['upper'])
    paint(thigh, upper, uuv, sz['upper'], upper_px)
    kuv = pack(sz['tknee'])
    paint(thigh, tknee, kuv, sz['tknee'], pit_back)
    sname = 'right_' if side == 'r' else 'left_'
    edits.append(dict(name=f'shin_{side}', from_=lower['from'], to=lower['to'], uv_offset=luv))
    edits.append(dict(name=f'thigh_{side}', from_=upper['from'], to=upper['to'], uv_offset=uuv))
    for name, c, uv, group, inflate in ((f'shin_knee_{side}', guard, guv, sname + 'shin', 0),
                                        (f'thigh_knee_{side}', tknee, kuv, sname + 'leg', 0.15)):
        e = dict(name=name, group=group, from_=c['from'], to=c['to'], origin=c['origin'], uv_offset=uv,
                 mirror_uv=True, inflate=inflate)
        (edits if name in cur else cubes).append(e)

changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W)
           if tex.getpixel((x, y)) != live.getpixel((x, y))]
(scratch/'knee-plan.json').write_text(json.dumps(dict(
    edits=edits, cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
prev = tex.crop((0, 0, W, 48)).resize((W * 4, 48 * 4), Image.NEAREST)
bg = Image.new('RGBA', prev.size, (255, 0, 255, 255))
bg.alpha_composite(prev)
bg.save(scratch/'knee-preview.png')
print('edits', [(e['name'], e['uv_offset']) for e in edits], 'new', [(c['name'], c['uv_offset']) for c in cubes], len(changed), 'px')
