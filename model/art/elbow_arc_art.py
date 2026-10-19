"""Elbow joint with a rounded back (2026-10-19, user: on the tool arm, bent 62 deg at rest, "the joint top is exposed").

As the elbow bends, the gauntlet's back corner swings down and the gap behind the elbow shows what is round the pivot.
The joint was one box on the forearm bone (elbow_joint, z +-2.75, top 2.8 above the pivot): at 62 deg its flat top face
turned backwards into that gap. Now (forearm bone, right side, y relative to the pivot; left mirrors):
  elbow_joint        front box z -2.75..0, -4..2.8: the straight front the user approved (flush look at rest)
  elbow_joint_back   z 0..2.75, -4..0: fills under the arc down into the gauntlet (no slot above the gauntlet's ramp)
  elbow_arc_0/45/90  the back half of an octagon round the pivot (faces RD = 3.45 from it, corners 3.73 < the pad's
                     back plate 3.75 and the gauntlet's back 4: never sticks out at rest or bent): bars pointing back,
                     back-up (turned -45 deg) and up, so the gap behind shows a rounded dark joint at any bend
All dark (joints are black / grey, user), the knee joint's art; x ends stepped 0.05 so no two end faces share a plane.
Run: uv run --with pillow python3 model/art/elbow_arc_art.py  (input .scratch/pads/arc_source.json, snapshot after
pads_fix_art.py); output .scratch/pads/arc_plan.json for elbow_apply.js.
"""
import base64, io, json, math
from pathlib import Path
from PIL import Image

PIV, RD = 28.625, 3.45
T8 = RD * math.tan(math.radians(22.5))
s = json.loads(Path('.scratch/pads/arc_source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
by = {e['name']: e for e in m['elements']}
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def faces(w, hh, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, hh), 'north': (d, d, w, hh),
            'west': (d + w, d, d, hh), 'south': (2 * d + w, d, w, hh)}
def net_pts(c):
    w, hh, d = size(c['from'], c['to']); u0, v0 = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v0, v0 + d + hh) for x in range(u0, u0 + 2 * (w + d))}

PARTS = [  # name, x inset, (y0, y1), (z0, z1), rotation X
    ('elbow_joint', 0.0, (-4, 2.8), (-2.75, 0), None),
    ('elbow_joint_back', 0.05, (-3.9, 0), (-0.1, 2.75), None),      # back pieces start inside the front box: their front
    ('elbow_arc_0', 0.1, (-T8, T8), (-0.2, RD), None),            # faces would otherwise share the plane z = 0
    ('elbow_arc_45', 0.15, (-T8, T8), (0, RD), -45),
    ('elbow_arc_90', 0.2, (0, RD), (-0.3, T8), None),
]
REMOVED = ['elbow_joint_r', 'elbow_joint_l']
KEEP = {tuple(c.get('uv_offset', [0, 0])) for c in m['elements'] if c['name'] not in REMOVED}
for c in m['elements']:
    if c['name'] in REMOVED and tuple(c['uv_offset']) not in KEEP:
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
kj = by['knee_joint_r']; kw, kh, kd = size(kj['from'], kj['to']); ku, kv = kj['uv_offset']; kf = faces(kw, kh, kd)
cubes = []
for name, inset, (y0, y1), (z0, z1), rot in PARTS:
    f, t = [7.75 + inset, PIV + y0, z0], [14.25 - inset, PIV + y1, z1]; sz = size(f, t); uv = pack(sz)
    for fc, (x, y, fw, fh) in faces(*sz).items():
        sx, sy = kf[fc][:2]
        for j in range(fh):
            for i in range(fw): tex.putpixel((uv[0] + x + i, uv[1] + y + j), live.getpixel((ku + sx + i % kf[fc][2], kv + sy + j % kf[fc][3])))
    for sd, (bone, sx) in {'r': ('right_forearm', 1), 'l': ('left_forearm', -1)}.items():
        fr, to = list(f), list(t)
        if sx < 0: fr[0], to[0] = -t[0], -f[0]
        c = dict(name=f'{name}_{sd}', group=bone, from_=fr, to=to, uv_offset=uv, mirror_uv=(sd == 'r'), origin=[sx * 10.5, PIV, 0])
        if rot: c['rotation'] = [rot, 0, 0]
        cubes.append(c)
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/pads/arc_plan.json').write_text(json.dumps(dict(removed=REMOVED, edits=[], cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print(len(cubes), 'cubes', len(changed), 'px', {c['name']: (c['uv_offset'], size(c['from_'], c['to'])) for c in cubes if c['name'].endswith('_r')})
