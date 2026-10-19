"""Elbow ramp (2026-10-19, user): the front complement of the elbow arc, fixed on the upper arm.

As the elbow bends, the top of the forearm's joint box turns back into the pad, and a notch opens between the joint
and the pad's sloped underside, in front. The arc (on the forearm) closes the gap that opens behind; the ramp (on the
upper arm) fills this one: it is the joint box's front top part as it sits at the right arm's rest bend (14 deg),
copied onto the upper arm bone (cube turned 14 deg about the elbow axis, like the forearm at rest). At rest the joint
box covers it exactly; straight (0 deg) 0.2 px2 shows; bent 33..90 deg it fills 1.3..2.1 px2 of the notch (side section,
.scratch/ramp/ramp_up.py, ramp_upper_0.5.png). It never reaches the gauntlet and stays inside the arm's outline.
Its top goes up into the pad (hidden). Same on both arms (user: the right arm holding an item bends like the tool arm).
  forearm-at-rest frame (y up from the elbow pivot, z back): z -3.1..0 (first -2.70, 0.05 behind the box front, hidden at
  the right arm's rest; user: a gap stayed between ramp and joint when bent, so 0.4 px further forward: 0.6 px2 shows at rest), y 1.85..5.0, x the joint's width -0.05 a side (pivot x +-3.2)
Then (user: a gap stayed between ramp and joint when bent) the whole ramp DROP = 1 px lower on the upper arm: closes it
at the tool arm's rest; at the right arm's rest 0.3 px2 shows. Lower, the tool arm's tool_recoil (each shot slid the
forearm 2 px back and 0.45 up) pushed the gauntlet into it, so that slide was halved in the animation (1 px / 0.225).
Knee joint art (joints are black / grey). Run: uv run --with pillow python3 model/art/elbow_ramp_art.py
(input .scratch/ramp/source.json, snapshot of the live project); output .scratch/ramp/plan.json for elbow_apply.js.
"""
import base64, io, json, math
from pathlib import Path
from PIL import Image

REST, YB, YT, ZF, DROP = 14, 1.85, 5.0, -3.1, 1.0    # YB 0.5 (side section) went 0.87 px into the gauntlet in tool_recoil, whose keys push the forearm
s = json.loads(Path('.scratch/ramp/source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
by = {e['name']: e for e in m['elements']}
grp = {g['name']: g for g in m['groups']}
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def faces(w, hh, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, hh), 'north': (d, d, w, hh),
            'west': (d + w, d, d, hh), 'south': (2 * d + w, d, w, hh)}
def net_pts(c):
    w, hh, d = size(c['from'], c['to']); u0, v0 = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v0, v0 + d + hh) for x in range(u0, u0 + 2 * (w + d))}
used = set()
for c in m['elements']: used |= net_pts(c)
used |= {(x, y) for y in range(H) for x in range(W) if tex.getpixel((x, y))[3]}
def pack(sz):
    w, hh, d = sz; nw, nh = 2 * (w + d), d + hh
    for v in range(H - nh + 1):
        for u_ in range(W - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u_, u_ + nw)}
            if not used & pts:
                used.update(pts); return [u_, v]
    raise SystemExit(f'no room for {sz}')
cubes, uv = [], None
for sd, fore, arm in (('r', 'right_forearm', 'right_arm'), ('l', 'left_forearm', 'left_arm')):
    px, py, pz = grp[fore]['origin']
    f, t = [px - 3.2, py + YB - DROP, pz + ZF], [px + 3.2, py + YT - DROP, pz]
    sz = size(f, t)
    if uv is None:
        uv = pack(sz)
        kj = by['knee_joint_r']; ku, kv = kj['uv_offset']; kf = faces(*size(kj['from'], kj['to']))
        for fc, (x, y, fw, fh) in faces(*sz).items():
            sx, sy, kw, kh = kf[fc]
            for j in range(fh):
                for i in range(fw): tex.putpixel((uv[0] + x + i, uv[1] + y + j), live.getpixel((ku + sx + i % kw, kv + sy + j % kh)))
    cubes.append(dict(name=f'elbow_ramp_{sd}', group=arm, from_=f, to=t, uv_offset=uv, mirror_uv=(sd == 'r'),
                      rotation=[REST, 0, 0], origin=[px, py - DROP, pz]))
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/ramp/plan.json').write_text(json.dumps(dict(removed=[], edits=[], cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print(cubes, len(changed), 'px')
