# EM projector -> redstone electromagnet (Phase 7 step 3). Reworks the projector under left_forearm:
#   housing   the forearm: a 7x6x7 block like the other tools' motors (blue armour, then a dark iron collar with
#             redstone vent slits), a steel bezel round the lens on its face. It was a 4 px dark iron box: next to the
#             other tools the projector looked like a head without an arm (user, 2026-10-13).
#   lens      the redstone core, now a flat light set into the housing's face (it used to stick out between the prongs)
#   prongs    four short steel pole pieces straight out of the housing, redstone tips (they spark while the beam runs)
#   windings  copper wire wound round each prong from the housing down to the tip, like the iron cores of a real
#             electromagnet (replaces the two big rings round all four prongs, which made the tool thick). Copper
#             is in the recipe, so it shows.
# Halved after the first in-game look (2026-10-13): prongs 9 -> 5 px, windings 4 -> 2 px (a pixel of steel shows between
# the copper and the tip); the tool is 10 px long, the
# shortest socket tool (it was 14, the biggest); then the forearm block made it 11 and the prongs got 2 px back (7 px,
# 3 of them bare steel): 13 px, the others are 15-16.
# Rerunnable: every projector cube is rebuilt.
# Lens/vents and prong tips are painted in RESERVED colours (tools/make-glowmasks.py turns them into glowmasks).
# Inputs: /private/tmp/em/tex.png + model.json (live project). Writes em_plan.json (cubes), em_paint.json (rects for
# tex.edit; state "off" = the real texture, "on" = with the glow added, to preview the lit look in Blockbench).
import json, math
from PIL import Image
D = '/private/tmp/em/'
im = Image.open(D + 'tex.png').convert('RGBA')
W, H = im.size
M = json.load(open(D + 'model.json'))
els = {e['uuid']: e for e in M['elements']}
byname = {e['name']: e for e in els.values()}
REMOVED = ['em_housing', 'em_coil_a', 'em_coil_b', 'em_core', 'em_lens'] + [f'em_{p}_{i}' for p in ('prong', 'winding') for i in range(1, 5)]

def H_(c): return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
P = {k: H_(v) for k, v in dict(
    k1='25292c', k2='2f3437', k3='3b4246', k4='4c5559', k5='565f63', k6='6f797e', k8='89939a',
    b0='1b4895', b1='2358b2', b2='2d6bcc', b3='4588e0', b4='62a3ef',      # the suit's blue armour
    c0='6a3420', c1='8c482c', c2='ba6840', c3='de9262',                    # copper (the old coil colours)
    R0='3a0e0c', R1='4b1310', R2='5c1813', R3='701f17',                    # RESERVED: dead redstone (lens, vents)
    T1='4e1411', T2='621a14').items()}                                       # RESERVED: redstone prong tips
# reserved colour -> (mask, glow added on top when lit)
GLOW = {'R0': ('em_core', '7a0600'), 'R1': ('em_core', 'b81206'), 'R2': ('em_core', 'f0200c'),
        'R3': ('em_core', 'ff6a4a'), 'T1': ('em_arc', 'c01206'), 'T2': ('em_arc', 'ff2a14')}

# Faces: north = -Z, south = +Z, east/west = the sides, up = towards the elbow, down = towards the tool's tip.
def housing(f, x, y, w, h):       # forearm: blue armour (as the other tools), dark iron collar with redstone vents
    if f == 'down':
        if 2 <= x <= 4 and 2 <= y <= 4: return 'k1'                    # under the lens
        if 1 <= x <= 5 and 1 <= y <= 5: return 'k6'                    # bezel
        return 'k4' if x in (0, w - 1) or y in (0, w - 1) else 'k2'
    if f == 'up': return 'k3'
    if y == 0: return 'b4' if x else 'b3'                               # armour, like tool_forearms_painters.armor
    if y <= 2: return 'b1' if x == w - 1 else 'b2'
    if y == 3: return 'b0'
    if y == h - 2 and 1 <= x < w - 1: return 'R2' if x % 2 else 'k1'   # vent slits in the collar
    if y == h - 1: return 'k1'
    return 'k5' if x % 3 else 'k4'
def lens(f, x, y, w, h):          # redstone lens: speckled, bright centre
    if f == 'down': return 'R3' if (x, y) == (1, 1) else ('R2' if (x + y) % 2 else 'R1')
    return 'R0'
def prong(f, x, y, w, h):         # steel pole piece: dark root at the housing, redstone tip
    if f == 'down': return 'T2'
    if f == 'up': return 'k3'
    if y >= h - 2: return 'T2' if y == h - 1 else 'T1'
    if y == 0: return 'k3'
    return 'k8' if x == 0 else 'k5'
def winding(f, x, y, w, h):       # copper wire: bright turns with dark gaps between them
    if f in ('up', 'down'): return 'c1'
    return 'c0' if y % 2 else ('c3' if x == 0 else 'c2')

def size(e): return [math.ceil(abs(e['to'][i] - e['from'][i]) - 1e-6) for i in range(3)]
def netsize(s): w, h, d = s; return 2 * (w + d), d + h
used = [[False] * W for _ in range(H)]
for e in els.values():               # free space = outside every net of the cubes we keep
    if e['name'] in REMOVED: continue
    u, v = e.get('uv_offset', [0, 0]); nw, nh = netsize(size(e))
    for y in range(v, min(H, v + nh)):
        for x in range(u, min(W, u + nw)): used[y][x] = True
old_used = [row[:] for row in used]
def place(nw, nh):
    for v in range(H):
        for u in range(W - nw + 1):
            if v + nh <= H and all(not used[y][x] for y in range(v, v + nh) for x in range(u, u + nw)):
                for y in range(v, v + nh):
                    for x in range(u, u + nw): used[y][x] = True
                return [u, v]
    raise SystemExit(f'no space for {nw}x{nh}')
def faces(u, v, s):
    w, h, d = s
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h),
            'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}

PIV = [-12.5, 25.625, 0]             # the projector bone's pivot (socket face under the elbow)
cubes, nets = [], []                 # nets: (u, v, size, painter)
def add(name, frm, s, painter, share=None, inflate=0):
    if share: uv = next(c['uv_offset'] for c in cubes if c['name'] == share)
    else:
        uv = place(*netsize(s)); nets.append((uv[0], uv[1], s, painter))
    to = [frm[i] + s[i] for i in range(3)]
    c = dict(name=name, from_=frm, to=to, origin=PIV, uv_offset=uv, mirror_uv=True)
    if inflate: c['inflate'] = inflate
    cubes.append(c)
add('em_housing', [-16, 19.625, -3.5], (7, 6, 7), housing)       # socket face (y 25.625) down to y 19.625
add('em_lens', [-14, 19.0, -1.5], (3, 1, 3), lens)             # mostly sunk into the housing's face (y 19.625)
for i, (x, z) in enumerate([(-16, -3.5), (-11, -3.5), (-16, 1.5), (-11, 1.5)]):
    add(f'em_prong_{i + 1}', [x, 12.625, z], (2, 7, 2), prong, share='em_prong_1' if i else None)
for i, (x, z) in enumerate([(-16, -3.5), (-11, -3.5), (-16, 1.5), (-11, 1.5)]):
    # top tucked 0.5 into the housing; with the inflate the copper runs from the housing's face down to y 17.625,
    # leaving three pixels of the steel prong showing above the redstone tip (one, then two more: user's requests)
    add(f'em_winding_{i + 1}', [x, 17.875, z], (2, 2, 2), winding, share='em_winding_1' if i else None, inflate=0.25)

reserved = {P[k] for k in GLOW}
paint_px = set()
for u, v, s, _ in nets:
    nw, nh = netsize(s); paint_px |= {(x, y) for y in range(v, v + nh) for x in range(u, u + nw)}
# old nets of the removed cubes that nothing uses any more: cleared
clear = [[x, y] for y in range(H) for x in range(W) if not old_used[y][x] and not used[y][x] and im.getpixel((x, y))[3]]
cleared = {tuple(c) for c in clear}
for y in range(H):                   # reserved colours must not exist anywhere we don't repaint (or clear)
    for x in range(W):
        px = im.getpixel((x, y))
        if px[3] and px[:3] in reserved and (x, y) not in paint_px and (x, y) not in cleared:
            raise SystemExit(f'reserved colour {px[:3]} already used at {x},{y}')

out = {'off': im.copy(), 'on': im.copy()}
for x, y in clear:
    for k in out: out[k].putpixel((x, y), (0, 0, 0, 0))
rects = []
for u, v, s, fn in nets:
    for f, (x0, y0, fw, fh) in faces(u, v, s).items():
        for y in range(fh):
            for x in range(fw):
                k = fn(f, x, y, fw, fh)
                g = H_(GLOW[k][1]) if k in GLOW else (0, 0, 0)
                out['off'].putpixel((x0 + x, y0 + y), P[k] + (255,))
                out['on'].putpixel((x0 + x, y0 + y), tuple(min(255, a + b) for a, b in zip(P[k], g)) + (255,))
    nw, nh = netsize(s)
    for key, img in out.items():
        L = img.load()
        rects.append(dict(state=key, x=u, y=v, w=nw, h=nh,
                          hex=''.join('%02x%02x%02x%02x' % L[u + x, v + y] for y in range(nh) for x in range(nw))))
json.dump(dict(removed=REMOVED, cubes=cubes), open(D + 'em_plan.json', 'w'), indent=1)
json.dump(dict(rects=rects, clear=clear), open(D + 'em_paint.json', 'w'))
print('nets', [(u, v, s) for u, v, s, _ in nets], 'clear px', len(clear))
