"""Heavier legs on a wider stance, the shin and foot one armoured boot (2026-10-14, animation overhaul).

Why: the top half (33 px of shoulders, a 20 px deep torso) stood on two thin columns close together (thighs 6 wide,
both legs 17 px across, the boots touching), which looked top-heavy and stiff.
v1 (Fallout-style stompers: hips +-6.5, thighs 8x6x9, a greave over an 11x16 boot round a hidden ankle) was rejected:
"the boots look like most of it is missing" (from the side the lower leg was only a greave in front of a low shaft)
and the legs looked comically large under the upper body. v2, between the old legs and v1 (right side; left mirrors):
  hips      leg pivots x +-4.5 -> +-5.5 (11 px apart); pelvis shell 16 -> 18 wide (old faces widened in the middle)
  thigh     7x6x8 (inflate 0.25) over the knee's 2 px back cut + thigh_knee 7x2x4 in front (inflate 0.15)
  joints    hip and knee joints 6x5x6 (were 5x4x5), the old joint art scaled up
  shin      (v2: a solid 8x5x8 box under an 8x4x4 knee guard, the boot an ankle cuff round it)
  v3 (a first reading of the user's sketch) put the whole lower leg above a thin flat foot with an empty sloped slot
  over it, and the lower leg in front of the knee so the knee joint hung out behind it: rejected.
  v4 (the sketch done right; real slopes = rotated cubes): the lower leg is one 9x9 block centred on the leg's axis
  (the knee joint sits inside it) that comes down to 1.5 px above the ground:
    knee    shin_cap 9x5x3: the front rises 3 px above the knee pivot (a knee cap hiding the joint from the front);
            shin_ramp turned 35 deg: the top slopes from the cap down toward the back, where the thigh comes in
    ankle   the bottom is cut on a 20 deg diagonal (shin_heel, turned): 1.6 px above the ground at the back, rising to
            4.4 px at the front, with a heel step at the back (shin_back, down to 1 px above the ground). The boot fills
            that slot, as wide as the shin (a hair inside it): boot_foot 9x5x9 from the heel to the shin's front (its
            lowest pixel row is the dark sole), boot_tip 9x3x5 the toe in front, boot_toe 9x2x4 turned down 30 deg on
            top of it (the slope from the slot down to the toe). v4 had a 9-wide sole under a 7-wide foot (it stuck out)
            and non-integer cube sizes (box UV faces then read unpainted, transparent pixels); every size is whole now,
            and every cube that sits inside another is inflated a little less, so no two faces share a plane.
    pivots  the ankle (foot bone) moves to the back-bottom corner of the slot (y 1.125, z 4): the foot hinges there
  Neutral pose: the lower leg vertical (thigh 6, shin -6), the foot flat, toes out 7 deg from the hips, no splay.
  The animations will be redone on the final model.
  v6 (one wide hip housing over both thighs) was dropped by the user for v7, his sketch: separate thighs, each going
  up to the hip on its own side, and a V-shaped piece in the middle as wide as the chest above it:
    hips    hip_band 16x2x10 (two mirrored halves) under the waist; the V under it from two halves (hip_v, 9x2x10,
            turned 25 deg about the tip 2.6 px under the hip axis) and a centre fill; a dark hip_core behind, seen in
            the gaps between the V and the thighs and under the V's tip
    thigh   thigh_main 9x4x8 (as wide as the shin, its front tucked behind the knee cap) and thigh_top 8x4x8 turned
            25 deg about its outer top corner (at the hip axis): its top runs down toward the crotch parallel to the
            V's side (a 1.2 px dark gap) and its outer end leans out going down, like the sketch's outer edge. The
            knee slope under the thigh is gone (the thigh's bottom is flat; the shin's slope leaves a V behind the knee)
  v8: the knee 2 px lower (pivot y 11.6; the shin's top pieces with it) for longer thighs; the thighs end in a slope
  opposite to the shin's again (thigh_low, turned -7 deg: low at the front over the knee cap, rising toward the back),
  so the two open a V behind the knee instead of the thigh running into the shin; the hips' V ends in a 2 px flat base
  (hip_tip) instead of a point; the dark hip core is 5 deep (it showed through the backs of the turned-out thighs).
  v9 (the user's side-view sketch of the knee): thigh bottom and shin top PARALLEL (both slope down toward the back,
  27 deg in the neutral pose) with a dark band of knee joint between them, 1.8 px tall, joining them edge to edge.
  The shin's top is one slope from its front top corner to its back (shin_front, a 1 px front block, then shin_ramp);
  the thigh's bottom is thigh_low (turned to match) with thigh_back closing its back end; the knee joint is one box
  (inset 1 px from the leg's sides) that fills the band and hides its excess inside thigh and shin. Knee pivot 12.6.
  Cube sizes may be fractional now: the texture nets are painted at Blockbench's floored box-UV sizes. Overlapping
  cubes of one bone are inflated by different amounts (0 / -0.05 / -0.1) so their side faces never share a plane
  (thigh_low and shin_ramp flickered against their neighbours).
  v10 (user, 2026-10-15): the shin's sloped bottom is gone: the shin comes straight down to 0.625 (where the heel step
  ended), over the boot's sole strip, with the toe in front; the slot no longer opens when the ankle bends.
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
REMOVED = ['hip_core', 'seat_upper', 'seat_lower', 'codpiece_upper', 'codpiece_lower', 'hip_skirt',
           'hip_shell_r', 'hip_shell_l', 'hip_skirt_r', 'hip_skirt_l', 'hip_band_r', 'hip_band_l', 'hip_v_r', 'hip_v_l',
           'hip_fill'] + [p + side for side in ('_r', '_l') for p in
           ('thigh', 'thigh_knee', 'hip_joint', 'knee_joint', 'shin', 'shin_knee',
            'stomper_sole', 'stomper_foot', 'stomper_toe', 'stomper_ankle',
            'shin_guard', 'shin_core', 'boot_shaft', 'boot_upper', 'boot_toe', 'boot_sole', 'boot_cuff',
            'shin_ramp', 'shin_low', 'shin_back', 'shin_skirt1', 'shin_skirt2',
            'shin_main', 'shin_cap', 'shin_heel', 'boot_foot', 'boot_fill', 'boot_tip', 'thigh_main', 'thigh_low', 'thigh_top',
            'shin_front', 'shin_main_f', 'shin_main_b', 'thigh_back', 'thigh_outer')] + ['hip_tip']   # re-runs rebuild these


def size(c):
    """Box-UV net size: Blockbench floors fractional cube sizes (checked: 2.5x3.3x4.7 -> a 2x3x4 net)."""
    return [math.floor(b - a + 1e-6) for a, b in zip(c['from'], c['to'])]


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
    if f in ('up', 'down'):
        return iron(w, h)                                  # inside (ramp above, heel below)
    return plate(w, h, top_edge=False, bottom_edge=False)


def shin_low(f, w, h):
    if f == 'up':
        return iron(w, h)                                  # inside (the ramp above)
    if f == 'down':
        return solid(EDGE, w, h)                           # the shin's underside over the boot
    return plate(w, h, top_edge=False, bottom_edge=True)   # dark edge row along the bottom, like the old heel step


def cap(f, w, h):
    if f in ('south', 'down'):
        return iron(w, h)
    return plate(w, h, bottom_edge=False)                  # lit top edge: the knee cap's rim


def ramp(f, w, h):
    if f == 'up':
        return plate(w, h, top_edge=True, bottom_edge=False)   # the slope from the knee cap down to the back
    if f in ('east', 'west', 'south'):
        return plate(w, h, top_edge=False, bottom_edge=False)  # the shin's sides under the slope, its sliver at the back
    return iron(w, h)


def heel(f, w, h):
    if f == 'down':
        return solid(EDGE, w, h)                           # the diagonal over the foot
    if f == 'north':
        return plate(w, h, False, True)                    # its end at the front, just above the foot
    if f in ('east', 'west'):
        return plate(w, h, top_edge=False, bottom_edge=False)  # the shin's sides above the diagonal
    return iron(w, h)


def back(f, w, h):
    if f in ('up', 'down'):
        return solid(EDGE, w, h)
    return plate(w, h, top_edge=False, bottom_edge=True)


def housing(f, w, h):
    if f == 'down':
        return iron(w, h)
    return plate(w, h, top_edge=True, bottom_edge=False)


def skirt(f, w, h):
    if f in ('up', 'down'):
        return iron(w, h)
    return plate(w, h, top_edge=False, bottom_edge=True)


def thigh_main(f, w, h):
    if f == 'up':
        return iron(w, h)                                  # inside the hip housing
    if f == 'down':
        return plate(w, h, False, False, 0.62)
    return plate(w, h, top_edge=False, bottom_edge=False)


def thigh_top(f, w, h):
    if f == 'up':
        return plate(w, h, top_edge=True, bottom_edge=False)   # the diagonal top, down toward the crotch
    if f == 'down':
        return iron(w, h)
    return plate(w, h, top_edge=True, bottom_edge=False)


def band(f, w, h):
    if f == 'down':
        return plate(w, h, False, False, 0.8)              # its underside shows a little above the V's sloped tops
    return plate(w, h, top_edge=True, bottom_edge=True)


def vee(f, w, h):
    if f == 'up':
        return plate(w, h, False, False)                   # meets the centre fill: dark iron showed as a line
    if f == 'down':
        return plate(w, h, False, False, 0.8)              # the V's slanted underside
    return plate(w, h, top_edge=False, bottom_edge=True)


def core(f, w, h):
    return iron(w, h)


def fill_plate(f, w, h):
    return plate(w, h, top_edge=False, bottom_edge=False)


def thigh_low(f, w, h):
    if f == 'up':
        return iron(w, h)
    if f == 'down':
        return plate(w, h, False, False, 0.62)             # the thigh's underside over the knee's V
    return plate(w, h, top_edge=False, bottom_edge=True)


def soled(w, h, shade=1.0, top_edge=False):
    """Plate whose lowest row is the dark sole with a grey welt row above it."""
    px = plate(w, h, top_edge=top_edge, bottom_edge=False, shade=shade)
    for x in range(w):
        px[h - 1][x] = SOLE
        if h > 2:
            px[h - 2][x] = STEEL if x % 3 else STEEL_LO
    return px


def foot_box(f, w, h):
    if f == 'down':
        return solid(SOLE, w, h)
    if f == 'up':
        return iron(w, h)                                  # inside the shin
    return soled(w, h, shade=0.9)                          # under the shin's diagonal (a hair inside its sides)


def tip(f, w, h):
    if f == 'down':
        return solid(SOLE, w, h)
    if f == 'up':
        return plate(w, h, top_edge=True, bottom_edge=False)   # the toe's lip in front of the slope
    return soled(w, h, top_edge=True)


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
    if f == 'up':
        return plate(w, h, top_edge=True, bottom_edge=False)   # the slope from the slot down to the toe
    if f in ('east', 'west'):
        return plate(w, h, top_edge=True, bottom_edge=False)
    return plate(w, h, False, False, 0.8)                  # inside the toe


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
pel = by.get('pelvis_shell')                   # gone once the hip housing (v6) replaced it
pw, ph, pd = size(pel) if pel else (20, 4, 11)
NW = 20
ND = 11


def widen(f, w, h):
    ou, ov = map(int, pel['uv_offset'])
    fx, fy, fw, fh = faces(pw, ph, pd)[f]
    cols = list(range(fw)) if fw == w else list(range(fw // 2)) + [fw // 2 - 1 - (k % 2) for k in range(w - fw)] + list(range(fw // 2, fw))
    return [[live.getpixel((ou + fx + cols[i], ov + fy + j)) for i in range(w)] for j in range(h)]


# ---- plan --------------------------------------------------------------------------------------------------------
# ---- knee geometry (v9), computed: shin-local = model coordinates of the shin bone, thigh-local likewise ----------
KNEE = 12.625                                         # knee pivot y (z 0)
TS = math.degrees(math.atan2(8, 9 * 0) if False else math.acos(8 / 9))   # shin slope: a 9-long ramp spans 8 px
TAN = math.tan(math.radians(TS))
def slope(z):                                         # the shin's top surface (shin-local)
    return 13.6 - (z + 5) * TAN
BAND = 1.8                                            # dark knee band, vertical (3.4 showed too much joint)
def to_thigh(y, z, deg=-6.0):                         # a shin-local point in thigh-local coordinates (neutral pose:
    a = math.radians(deg); c, sn = math.cos(a), math.sin(a)   # the shin is turned -6 deg against the thigh)
    yy, zz = y - KNEE, z
    return KNEE + yy * c - zz * sn, yy * sn + zz * c
_ya, _za = to_thigh(slope(-4) + BAND, -4)
_yb, _zb = to_thigh(slope(4) + BAND, 4)
TT = math.degrees(math.atan2(_ya - _yb, _zb - _za))   # thigh bottom slope in thigh-local (deg, descending to the back)
def tline(z):                                         # the thigh's bottom line (thigh-local)
    return _ya - (z - _za) * math.tan(math.radians(TT))
_T_LOW = 8 * math.sin(math.radians(TT)) + 0.25         # thick enough to reach the main block's flat bottom at the back
_ZBB = 3.95 - _T_LOW * math.sin(math.radians(TT))      # its back end leans; the thigh_back strip closes behind it
_L_LOW = (_ZBB + 3.95) / math.cos(math.radians(TT))
SHIN_LOW = 0.625                                      # the shin's flat bottom, 0.85 px above the sole's underside
_RAMP_T = 2.1                                         # ramp: its front end leans 0.96 px, hidden in the 1 px front block

PARTS = [  # name, bone, from, to (right side), inflate, painter, rotation, rotation origin
    ('thigh_main', 'leg', [X - 4.5, tline(-3.95), -4], [X + 4.5, 18.625, 4], -0.05, thigh_main, None, None),
    # the top: turned 25 deg about its outer top corner (x X+2.75, 0.5 under the hip axis), so it slopes down to the
    # crotch side parallel to the hips' V
    ('thigh_top', 'leg', [X - 5.25, 18.125, -4], [X + 2.75, 22.125, 4], -0.1, thigh_top, [0, 0, 25], [X + 2.75, 22.125, 0]),
    # the bottom: parallel to the shin's top slope (in the neutral pose), turned about its front bottom corner
    ('thigh_low', 'leg', [X - 4.5, tline(-3.95), -3.95], [X + 4.5, tline(-3.95) + _T_LOW, -3.95 + _L_LOW], -0.1,
     thigh_low, [TT, 0, 0], [X, tline(-3.95), -3.95]),
    ('thigh_back', 'leg', [X - 4.5, tline(_ZBB), _ZBB], [X + 4.5, tline(-3.95), 4], -0.05, thigh_main, None, None),
    # the knee joint fills the band between thigh and shin; inset 1 px from the leg's sides, its excess hides inside them
    # its front stays 0.1 behind the thigh's front face where it runs up inside the thigh (the thigh leans 6 deg
    # forward in the neutral pose; a front at -3.9 showed through the thigh's front as a dark patch), so the band's
    # front edge sits a little inside the thigh and shin fronts, like the sketch
    # back at 2.9 (user, 2026-10-15: 6.2 deep keeps all its edges inside the thigh and shin)
    ('knee_joint', 'shin', [X - 3.5, slope(4) - 0.5, -3.3], [X + 3.5, slope(-3.9) + BAND + 0.6, 2.9], 0, joint, None, None),
    ('shin_front', 'shin', [X - 4.5, 5.0, -5], [X + 4.5, slope(-4), -4], -0.05, cap, None, None),
    ('shin_ramp', 'shin', [X - 4.5, slope(-4) - _RAMP_T, -4], [X + 4.5, slope(-4), 5], -0.1, ramp, [TS, 0, 0], [X, slope(-4), -4]),
    # v10 (user, 2026-10-15): no sloped bottom any more, the shin comes straight down to SHIN_LOW (where the old heel
    # step ended): the boot shows only as its sole strip under it and the toe in front, so a bending ankle never opens
    # the slot (toes down) and the toe tucks under the shin's front (toes up) instead of the diagonal crossing the boot
    ('shin_main_f', 'shin', [X - 4.5, SHIN_LOW, -5], [X + 4.5, slope(0) - 0.15, 0], 0, shin_low, None, None),
    ('shin_main_b', 'shin', [X - 4.5, SHIN_LOW, 0], [X + 4.5, slope(4) - 0.1, 4], 0, shin_low, None, None),
    # -0.15, not -0.1: the shin's heel piece (-0.1) runs down into it and their sides flickered
    ('boot_foot', 'foot', [X - 4.5, -0.375, -5], [X + 4.5, 4.625, 4], -0.15, foot_box, None, None),
    ('boot_tip', 'foot', [X - 4.5, -0.375, -9], [X + 4.5, 2.625, -4], -0.2, tip, None, None),
    # the slope: turned down 30 deg about the foot's front top edge, ending on the tip's top 3.5 px ahead
    ('boot_toe', 'foot', [X - 4.5, 2.625, -9], [X + 4.5, 4.625, -5], -0.05, toe, [-30, 0, 0], [X, 4.625, -5]),   # -0.05: its back corner sits inside the shin (v10: side planes flickered)
]
nets = {}
def net_area(p):
    w, h, d = (math.floor(p[3][i] - p[2][i] + 1e-6) for i in range(3))
    return 2 * (w + d) * (d + h)


order = sorted(PARTS, key=lambda p: -net_area(p))   # biggest nets first
for name, bone, f, t, inf, painter, rot, ro in order:
    sz = [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
    if name == 'knee_joint' and 'hip_joint' in nets:       # same size and art: share the net
        nets[name] = nets['hip_joint']
        continue
    uv = pack(sz)
    paint(uv, sz, painter)
    nets[name] = uv
# the housing and its skirt are each two mirrored halves sharing one net (a whole 20-wide net found no room in the
# texture); the halves meet at x = 0 like two plates
# hips: a band as wide as the chest, a V under it (two mirrored halves turned about the tip, a centre fill where their
# tops don't reach the band) and a dark core behind, which shows in the gaps (the sketch's black undersuit)
BUV = pack([8, 2, 10]); paint(BUV, [8, 2, 10], band)
VUV = pack([8, 2, 10]); paint(VUV, [8, 2, 10], vee)
FUV = pack([8, 2, 10]); paint(FUV, [8, 2, 10], fill_plate)
CUV = pack([10, 6, 5]); paint(CUV, [10, 6, 5], core)
TUV = pack([2, 2, 10]); paint(TUV, [2, 2, 10], fill_plate)
TIP = 20.0
# the fill is flush with the band (front/back at z -+5, under the band, so no shared face); the V halves (-0.1) sit
# a hair behind it, the tip (-0.05) too: a recessed fill read as a hole under the band (user)
SINGLES = [dict(name='hip_fill', group='pelvis', from_=[-4, 21.625, -5], to=[4, 23.625, 5], inflate=0,
                origin=[0, 22.625, 0], uv_offset=FUV, mirror_uv=False),
           dict(name='hip_tip', group='pelvis', from_=[-1, TIP, -5], to=[1, TIP + 2, 5], inflate=-0.05,
                origin=[0, 22.625, 0], uv_offset=TUV, mirror_uv=False),
           dict(name='hip_core', group='pelvis', from_=[-5, 17.625, -2.5], to=[5, 23.5, 2.5],
                origin=[0, 22.625, 0], uv_offset=CUV, mirror_uv=False)]
for sx, nm in ((1, 'r'), (-1, 'l')):
    x0, x1 = (0, 8) if sx > 0 else (-8, 0)
    SINGLES.append(dict(name=f'hip_band_{nm}', group='pelvis', from_=[x0, 23.625, -5], to=[x1, 25.625, 5],
                        origin=[0, 22.625, 0], uv_offset=BUV, mirror_uv=sx > 0))
    v0, v1 = (1, 9) if sx > 0 else (-9, -1)                 # each half starts 1 px out: a 2 px base between them
    c = dict(name=f'hip_v_{nm}', group='pelvis', from_=[v0, TIP, -5], to=[v1, TIP + 2, 5], rotation=[0, 0, 25 * sx],
             origin=[sx, TIP, 0], uv_offset=VUV, mirror_uv=sx > 0, inflate=-0.1)   # inside the band (0): no shared faces
    SINGLES.append(c)


PIVOTS = {'leg': (22.625, 0), 'shin': (KNEE, 0), 'foot': (SHIN_LOW, -5)}   # bone pivots (y, z); v10: the ankle hinges at the shin's front bottom edge (the shin rocks on it; its back lifts off the boot's heel)
cubes = []
for side, sx in (('r', 1), ('l', -1)):
    prefix = 'right_' if side == 'r' else 'left_'
    for name, bone, f, t, inf, painter, rot, ro in PARTS:
        fr, to = list(f), list(t)
        if sx < 0:
            fr[0], to[0] = -t[0], -f[0]
        piv = PIVOTS[bone]
        c = dict(name=f'{name}_{side}', group=prefix + bone, from_=fr, to=to, origin=[sx * X, piv[0], piv[1]],
                 uv_offset=nets[name], mirror_uv=(side == 'r'))
        if inf:
            c['inflate'] = inf
        if rot:
            c['rotation'] = rot if sx > 0 else [rot[0], -rot[1], -rot[2]]
            c['origin'] = [sx * ro[0], ro[1], ro[2]]
        cubes.append(c)
groups = {f'{p}_{b}': [sx * X, PIVOTS[b][0], PIVOTS[b][1]] for p, sx in (('right', 1), ('left', -1)) for b in PIVOTS}
cubes = SINGLES + cubes

changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W)
           if tex.getpixel((x, y)) != live.getpixel((x, y))]
(scratch/'stomper-plan.json').write_text(json.dumps(dict(
    removed=REMOVED + ['pelvis_shell'], groups=groups, cubes=cubes,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
prev = tex.resize((W * 4, H * 4), Image.NEAREST)
bg = Image.new('RGBA', prev.size, (255, 0, 255, 255))
bg.alpha_composite(prev)
bg.save(scratch/'stomper-preview.png')
print('nets', nets, 'hips', BUV, VUV, FUV, CUV, len(changed), 'px')
