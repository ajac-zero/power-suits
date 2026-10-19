"""Elbows like the knees, shoulders without the double armour (2026-10-19, user).

Why: the elbow bends only forwards (forearm up toward the front), so it gets the knee's trick mirrored front to back:
armour at the back (where it can't bend), ramps in the crook at the front. Before, the 7x12x7 blue upper arm sat inside
the pad (a second armour layer showing below it) and the 8 px gauntlet's top front corner went into it from ~16 deg
of bend (left arm 1.8 px at rest, 3.6 in tool_recoil; right 2.75 in the run).

Side view, arm-local (front = -z), y relative to the elbow pivot (y 28.625); the left arm mirrors in x:
  upper_core   dark iron core inside the pad (replaces upper_arm + shoulder_joint): the joint seen through the pad's
               open inner side and in the crook
  ua_back      1 px blue plate at the back, flat bottom at the pivot (0)
  ua_ramp      the upper arm's underside: a slab turned about ua_back's front bottom edge, rising A deg to the front
  ua_mid_1/2   blocks filling above the slope up into the core
  forearm      the old gauntlet block, its top lowered to the slope's front end (side rows kept from its lower part)
  fa_back      1 px plate at the gauntlet's back, top at -1 (a 1 px dark seam under ua_back, like the knee's)
  fa_ramp      the gauntlet's top: a slab turned about fa_back's front top edge, falling B deg to the front
  fa_mid       fills under the slope's back half
  elbow_joint  the knee's joint (2026-10-19, user: a resting arm must look straight, joints are black/grey): a dark
               box on the forearm bone set back 1 px from the upper arm's front and back (z +-2.75), 7 wide, from inside
               the gauntlet up into the guard; the knee joint's art cropped to its net. Its top reaches the ramp's
               underside at the rest bend (14 deg), so nothing shows through the wedge from the side. Replaced the
               octagonal drum (it filled the wedge to the outline and read as a bulge at rest; its blue back was armour on
               a joint). As the elbow bends, the gauntlet's back corner swings down and the joint shows behind (2 px at
               the right arm's rest, 4 at the tool arm's), like the knee's front band.
  pads         walls 1 px shorter from the bottom (31.625 -> 32.625): at 90 deg of bend the gauntlet's front face
               points up and reaches its half depth (4) above the pivot
Superseded in part by pads_shell_art.py (the pad replaces ua_*; the joint is 0.3 taller).
Run: uv run --with pillow --with shapely python3 model/art/elbow_art.py
Input .scratch/elbow/source.json (skill scripts/snapshot.mjs); output .scratch/elbow/plan.json for elbow_apply.js and
.scratch/elbow/preview.png.
"""
import base64, io, json, math, random
from pathlib import Path
from PIL import Image
from shapely.geometry import Polygon
from shapely.ops import unary_union

A = 30.0                   # upper arm underside, rising to the front
B = 27.0                   # gauntlet top, falling to the front (the knee's shin top is 27)
TA, TB = 1.5, 1.8          # ramp thicknesses
PIV = 28.625               # elbow pivot y
X0, X1 = 6.75, 14.25       # upper arm sides (the old 7..14 cube inflated 0.25)
F0, F1 = 6.5, 14.5         # gauntlet sides
ZU = 3.75                  # upper arm half depth
PAD_BOTTOM = 32.625

s = json.loads(Path('.scratch/elbow/source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
by = {e['name']: e for e in m['elements']}
rng = random.Random(19)

# ---- geometry (relative y; converted to model y at the end) ------------------------------------------------------
ta, tb = math.tan(math.radians(A)), math.tan(math.radians(B))
ZB = ZU - 1                                # ua_back's front face
g = lambda z: (ZB - z) * ta                # upper arm underside
TOPU = PAD_BOTTOM - PIV + 0.5              # guard pieces reach 0.5 into the core
TVA = TA / math.cos(math.radians(A))
m1 = ZB - (TVA - 0.1) / ta + 0.02          # widest mid block that still meets the ramp's top at its back end
m2 = m1 - (TVA - 0.1) / ta + 0.02
assert g(m2) + TVA >= PAD_BOTTOM - PIV, 'gap above the ramp in front of mid_2'
FB = 3.0                                   # fa_back's front face
h = lambda z: -1 - (FB - z) * tb           # gauntlet top
FTOP = h(-4)                               # the old block's new top (the slope's front end)
TVB = TB / math.cos(math.radians(B))
fm = FB - (TVB - 0.1) / tb + 0.02
assert h(fm) - 0.1 - (h(FB) - TVB) >= 0, 'fa_mid would not reach the ramp'
assert h(fm) - TVB <= FTOP, 'gap under the ramp in front of fa_mid'
LA, LB = (ZB + ZU) / math.cos(math.radians(A)), (FB + 4) / math.cos(math.radians(B))
print(f'upper front {g(-ZU):.2f} (pad bottom {PAD_BOTTOM - PIV}), gauntlet front top {FTOP:.2f}; mids {m1:.2f} {m2:.2f} / {fm:.2f}')

# name, bone, from (x, yrel, z), to, rotation X, hinge (yrel, z), painter   (right side)
UP = [
    ('upper_core', 'arm', [6.85, PAD_BOTTOM - PIV, -3.7], [14.15, 12.25, 3.7], None, None, 'core'),
    ('ua_back', 'arm', [X0, 0, ZB], [X1, TOPU, ZU], None, None, 'ua_back'),
    ('ua_mid_1', 'arm', [X0, g(m1) + 0.1, m1], [X1, TOPU, ZB], None, None, 'mid'),
    ('ua_mid_2', 'arm', [X0, g(m2) + 0.1, m2], [X1, TOPU, m1], None, None, 'mid'),
    ('ua_ramp', 'arm', [X0 + 0.05, 0, ZB - LA], [X1 - 0.05, TA, ZB], A, (0, ZB), 'ua_ramp'),
]
FA = [
    ('fa_back', 'forearm', [F0 + 0.05, FTOP, FB], [F1 - 0.05, -1, 4], None, None, 'fa_back'),
    ('fa_mid', 'forearm', [F0 + 0.1, FTOP - 0.1, fm], [F1 - 0.1, h(fm) - 0.1, FB], None, None, 'mid'),
    ('fa_ramp', 'forearm', [F0 + 0.15, -1 - TB, FB - LB], [F1 - 0.15, -1, FB], -B, (-1, FB), 'fa_ramp'),
]
FA.append(('elbow_joint', 'forearm', [7, -4, -2.75], [14, 2.5, 2.75], None, None, 'joint'))

# ---- side-view check (z, y): the pieces against the outline they should make -------------------------------------
def poly(f, t, rot, hinge):
    pts = [(f[2], f[1]), (t[2], f[1]), (t[2], t[1]), (f[2], t[1])]
    if rot:
        a = math.radians(rot); c, sn = math.cos(a), math.sin(a); hy, hz = hinge
        pts = [(hz + (y - hy) * sn + (z - hz) * c, hy + (y - hy) * c - (z - hz) * sn) for z, y in pts]
    return Polygon(pts)
up = unary_union([poly(f, t, r, hg) for n, _, f, t, r, hg, _ in UP if n != 'upper_core'])
up_target = Polygon([(ZU, 0), (ZU, TOPU), (-ZU, TOPU), (-ZU, g(-ZU)), (ZB, 0)]).intersection(
    Polygon([(-9, -9), (9, -9), (9, TOPU), (-9, TOPU)]))
old_fa = by['forearm_r']
fa = unary_union([poly(f, t, r, hg) for n, _, f, t, r, hg, _ in FA if not n.startswith('elbow')] +
                 [poly([0, old_fa['from'][1] - PIV, -4], [0, FTOP, 4], None, None)])
fa_target = Polygon([(-4, old_fa['from'][1] - PIV), (4, old_fa['from'][1] - PIV), (4, -1), (FB, -1), (-4, h(-4))])
for name, u, t in (('upper', up, up_target), ('gauntlet', fa, fa_target)):
    poke, hole = u.difference(t), t.difference(u)
    inner = hole.difference(t.exterior.buffer(0.2))
    print(f'{name}: pokes out {poke.area:.3f} px2, holes {hole.area:.3f} px2 ({inner.area:.4f} away from the outline)')

# ---- texture -----------------------------------------------------------------------------------------------------
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def net_pts(c):
    w, hh, d = size(c['from'], c['to']); u0, v0 = map(int, c.get('uv_offset', [0, 0]))
    return {(x, y) for y in range(v0, v0 + d + hh) for x in range(u0, u0 + 2 * (w + d))}
REMOVED = ['upper_arm_r', 'upper_arm_l', 'shoulder_joint_r', 'shoulder_joint_l', 'elbow_joint_r', 'elbow_joint_l']
KEEP_NETS = {tuple(c.get('uv_offset', [0, 0])) for c in m['elements'] if c['name'] not in REMOVED}
for c in m['elements']:
    if c['name'] in REMOVED and tuple(c.get('uv_offset', [0, 0])) not in KEEP_NETS:   # shared nets stay (hip axle)
        for p in net_pts(c): tex.putpixel(p, (0, 0, 0, 0))

def strip_edit(name, old_h, new_h, keep):
    """Box-UV side strip of a cube losing rows: new rows = old rows `keep` (list), the rest cleared."""
    c = by[name]; w, _, d = size(c['from'], c['to']); u0, v0 = c['uv_offset']
    rows = [[live.getpixel((u0 + x, v0 + d + r)) for x in range(2 * (w + d))] for r in range(old_h)]
    for r in range(old_h):
        for x in range(2 * (w + d)):
            tex.putpixel((u0 + x, v0 + d + r), rows[keep[r]][x] if r < new_h else (0, 0, 0, 0))
EDITS = []
for sd in 'rl':        # pads: drop the row above the bottom rim
    for n in ('pad_wall', 'pad_front', 'pad_back'):
        c = by[f'{n}_{sd}']; strip_edit(c['name'], 10, 9, list(range(8)) + [9])
        EDITS.append(dict(name=c['name'], frm=[c['from'][0], PAD_BOTTOM, c['from'][2]], to=c['to']))
    c = by[f'forearm_{sd}']; nh = math.floor(FTOP - (c['from'][1] - PIV) + 1e-6)   # gauntlet: keep its lower rows
    strip_edit(c['name'], 7, nh, list(range(7 - nh, 7)))
    EDITS.append(dict(name=c['name'], frm=c['from'], to=[c['to'][0], PIV + FTOP, c['to'][2]]))

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
def faces(w, hh, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, hh), 'north': (d, d, w, hh),
            'west': (d + w, d, d, hh), 'south': (2 * d + w, d, w, hh)}
HI, BASE, MID, DARK, EDGE = (69, 136, 224), (45, 107, 204), (58, 123, 214), (35, 88, 178), (27, 72, 149)
IRON = [(47, 52, 55), (37, 41, 44), (59, 66, 70), (47, 52, 55), (26, 29, 31)]
def plate(w, hh, top=None, bottom=None, shade=1.0):
    px = [[MID if (x + y < 3 and rng.random() < .6) else DARK if rng.random() < .06 else
           MID if rng.random() < .05 else BASE for x in range(w)] for y in range(hh)]
    if top:
        for x in range(w): px[0][x] = top
    if bottom and hh > 1:
        for x in range(w): px[hh - 1][x] = bottom
    return [[tuple(int(c * shade) for c in p) for p in row] for row in px]
def iron(w, hh):
    return [[IRON[(x * 7 + y * 3 + rng.randrange(2)) % 5] for x in range(w)] for y in range(hh)]
def painter(kind):
    def core(f, w, hh): return iron(w, hh)
    def ua_back(f, w, hh):           # the guard's back: dark edge along its bottom; its underside an edge strip
        if f == 'down': return [[EDGE] * w for _ in range(hh)]
        if f in ('up', 'north'): return iron(w, hh)
        return plate(w, hh, bottom=EDGE)
    def mid(f, w, hh):
        if f in ('east', 'west'): return plate(w, hh)
        return iron(w, hh)
    def ua_ramp(f, w, hh):           # the slope seen from the front, below: shaded like the thigh's underside
        if f == 'down': return plate(w, hh, shade=0.62)
        if f == 'up': return iron(w, hh)
        return plate(w, hh, bottom=EDGE)
    def fa_back(f, w, hh):           # the gauntlet's back: the light top edge it always had
        if f == 'up': return [[HI] * w for _ in range(hh)]
        if f in ('down', 'north'): return iron(w, hh)
        return plate(w, hh, top=HI)
    def fa_ramp(f, w, hh):           # the slope's top, lit
        if f == 'up': return plate(w, hh, top=None)
        if f == 'down': return iron(w, hh)
        return plate(w, hh, top=HI)
    if kind == 'joint':                 # crop the knee joint's faces (7x6x6 net) to this net
        kj = by['knee_joint_r']; kw, kh, kd = size(kj['from'], kj['to']); ku, kv = kj['uv_offset']
        kf = faces(kw, kh, kd)
        def joint(f, w, hh):
            x, y, _, _ = kf[f]
            return [[live.getpixel((ku + x + i, kv + y + j)) [:3] for i in range(w)] for j in range(hh)]
        return joint
    return dict(core=core, ua_back=ua_back, mid=mid, ua_ramp=ua_ramp, fa_back=fa_back, fa_ramp=fa_ramp)[kind]

ARM = {'r': ('right_arm', 'right_forearm', 9, 1), 'l': ('left_arm', 'left_forearm', -9, -1)}
cubes = []
core_net = by['upper_arm_r']['uv_offset']           # the core reuses the right upper arm's net place (28x15 of 28x19)
for p in sorted(UP + FA, key=lambda p: -sum(size(p[2], p[3]))):
    name, bone, f, t, rot, hinge, kind = p
    f = [f[0], f[1] + PIV, f[2]]; t = [t[0], t[1] + PIV, t[2]]
    sz = size(f, t)
    if name == 'upper_core':
        uv = core_net; used.update({(x, y) for y in range(uv[1], uv[1] + sz[2] + sz[1]) for x in range(uv[0], uv[0] + 2 * (sz[0] + sz[2]))})
    else:
        uv = pack(sz)
    for fc, (x, y, w, hh) in faces(*sz).items():
        for j, row in enumerate(painter(kind)(fc, w, hh)):
            for i, px in enumerate(row): tex.putpixel((uv[0] + x + i, uv[1] + y + j), (*px, 255))
    for sd, (arm, fore, px_, sx) in ARM.items():
        fr, to = list(f), list(t)
        if sx < 0: fr[0], to[0] = -t[0], -f[0]
        c = dict(name=f'{name}_{sd}', group=arm if bone == 'arm' else fore, from_=fr, to=to, uv_offset=uv,
                 mirror_uv=(sd == 'r'), origin=[sx * 10.5, PIV, 0])
        if rot: c['rotation'] = [rot, 0, 0]; c['origin'] = [sx * 10.5, hinge[0] + PIV, hinge[1]]
        cubes.append(c)
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/elbow/plan.json').write_text(json.dumps(dict(removed=REMOVED, edits=EDITS, cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g_:02x}{b:02x}{a:02x}' for x, y, r, g_, b, a in changed))))
prev = tex.resize((W * 4, H * 4), Image.NEAREST); bg = Image.new('RGBA', prev.size, (255, 0, 255, 255)); bg.alpha_composite(prev)
bg.save('.scratch/elbow/preview.png')
print(len(cubes), 'cubes', len(changed), 'px', {c['name']: (c['uv_offset'], size(c['from_'], c['to'])) for c in cubes if c['name'].endswith('_r')})
