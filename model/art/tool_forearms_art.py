# Tool forearms: the right arm ends in an elbow socket and each socket tool IS the forearm (16-19 px from the
# elbow pivot, like the left forearm + hand). Plans every cube under right_forearm (except elbow_r and the
# muzzle flash), packs nets into space freed by the removed cubes, paints them procedurally.
# Inputs: /private/tmp/sa/tex.png + model.json. Writes tf_plan.json / tf_paint.json / tf_preview.png.
import json, math
from PIL import Image
D = '/private/tmp/sa/'
im = Image.open(D + 'tex.png').convert('RGBA')
W, H = im.size
M = json.load(open(D + 'model.json'))
els = {e['uuid']: e for e in M['elements']}
grp = {g['uuid']: g for g in M['groups']}
KEEP_UNDER_FOREARM = {'elbow_r', 'flash'}
removed = set()
def walk(n, inside):
    if isinstance(n, str):
        if inside and els[n]['name'] not in KEEP_UNDER_FOREARM: removed.add(n)
        return
    inside = inside or grp[n['uuid']]['name'] == 'right_forearm'
    for c in n.get('children', []): walk(c, inside)
for n in M['outliner']: walk(n, False)
def size(e): return [math.ceil(abs(e['to'][i] - e['from'][i]) - 1e-6) for i in range(3)]
used = [[False] * W for _ in range(H)]
for u_, e in els.items():           # space = everything outside the nets of the cubes we keep
    if u_ in removed: continue
    u, v = e.get('uv_offset', [0, 0]); w, h, d = size(e)
    for y in range(v, min(H, v + d + h)):
        for x in range(u, min(W, u + 2 * (w + d))): used[y][x] = True
REMOVED_NAMES = sorted(els[u]['name'] for u in removed)

def H_(c): return tuple(int(c[i:i+2], 16) for i in (1, 3, 5)) + (255,)
P = {k: H_(v) for k, v in dict(
    k0='#1a1d1f', k1='#25292c', k2='#2f3437', k3='#3b4246', k4='#4c5559', k5='#565f63', k6='#6f797e',
    k7='#7d888e', k8='#89939a', k9='#a2acb1', kA='#c8d0d3', kW='#e6edf0',
    b0='#1b4895', b1='#2358b2', b2='#2d6bcc', b3='#4588e0', b4='#62a3ef',
    d0='#0e7b7a', d1='#12a1a0', d2='#2ce3d9', d3='#a1fbe8', d4='#e4fffa',
    c1='#1f86b8', c2='#2fb8f0', c3='#a8f0ff',
    p0='#6e34be', p1='#a85cff', p2='#d6a0ff', p3='#f4e4ff',
    r1='#8c482c', r2='#ba6840', r3='#de9262', o1='#c98426', o2='#e2a23e').items()}

def fits(u, v, w, h):
    return u + w <= W and v + h <= H and all(not used[y][x] for y in range(v, v + h) for x in range(u, u + w))
def place(w, h):
    for v in range(H):
        for u in range(W):
            if fits(u, v, w, h):
                for y in range(v, v + h):
                    for x in range(u, u + w): used[y][x] = True
                return u, v
    raise SystemExit(f'no space for {w}x{h}')
out = Image.new('RGBA', (W, H), (0, 0, 0, 0)); O = out.load()
def faces(u, v, s):
    w, h, d = s
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h),
            'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}
def paint(u, v, s, fn):
    for f, (x0, y0, fw, fh) in faces(u, v, s).items():
        for y in range(fh):
            for x in range(fw):
                O[x0 + x, y0 + y] = P[fn(f, x, y, fw, fh)]
exec(open(__file__.replace('_art.py', '_painters.py')).read())   # painter functions

PIV = [12.5, 25.625, 0]             # socket face under the elbow: pivot of every tool bone
cubes, nets = [], {}
def add(bone, name, s, frm, painter, rot=0, share=None, origin=None):
    w, h, d = s
    if share:
        u, v = nets[share]
    else:
        u, v = place(2 * (w + d), d + h); paint(u, v, s, painter); nets[name] = (u, v)
    to = [frm[0] + w, frm[1] + h, frm[2] + d]
    c = dict(bone=bone, name=name, from_=frm, to=to, uv_offset=[u, v], origin=origin or PIV)
    if rot:
        c['rotation'] = [0, rot, 0]; c['origin'] = [(frm[i] + to[i]) / 2 for i in range(3)]
    cubes.append(c)

# Elbow stump: a steel ring on the elbow (always there) and the coupling face shown when the socket is empty
add('right_forearm', 'socket_ring', (7, 1, 7), [9, 25.625, -3.5], ring, origin=[12.5, 28.625, 0])
add('socket', 'socket_plate', (5, 1, 5), [10, 24.625, -2.5], plate)
add('socket', 'socket_pins', (3, 1, 3), [11, 23.625, -1.5], pins)
# Plasma cannon (gauss-rifle arm): receiver with energy cell on top, magazine under, heat sink outside,
# shroud, exposed barrel, big muzzle brake that hides the muzzle flash until it fires.
add('cannon', 'cannon_body', (7, 6, 7), [9, 19.625, -3.5], receiver)
add('cannon', 'cannon_cell', (4, 5, 2), [10.5, 20.125, -5.5], cell)
add('cannon', 'cannon_mag', (4, 4, 3), [10.5, 20.625, 3.5], mag)
add('cannon', 'cannon_fin', (1, 5, 5), [16, 20.125, -2.5], fins)
add('cannon', 'cannon_shroud', (5, 3, 5), [10, 16.625, -2.5], shroud)
add('cannon', 'cannon_barrel', (3, 2, 3), [11, 14.625, -1.5], barrel)
add('cannon', 'cannon_muzzle', (6, 5, 6), [9.5, 9.625, -3], muzzle)
# EM projector: emitter housing, glowing core through two copper coils, four prongs around the core
add('em_projector', 'em_housing', (7, 4, 7), [9, 21.625, -3.5], em_housing)
add('em_projector', 'em_coil_a', (8, 1, 8), [8.5, 19.125, -4], coil)
add('em_projector', 'em_coil_b', (8, 1, 8), [8.5, 16.625, -4], None, share='em_coil_a')
add('em_projector', 'em_core', (3, 8, 3), [11, 13.625, -1.5], core)
for i, (x, z) in enumerate([(9, -3.5), (14, -3.5), (9, 1.5), (14, 1.5)]):
    add('em_projector', f'em_prong_{i + 1}', (2, 7, 2), [x, 12.125, z], prong, share='em_prong_1' if i else None)
# Drill: motor housing at the elbow, cooling fin, spinning hub + stepped twisted cone + diamond tip
add('drill', 'drill_motor', (7, 6, 7), [9, 19.625, -3.5], drill_motor)
add('drill', 'drill_fin', (1, 4, 5), [16, 20.625, -2.5], fins)
add('drill_bit', 'drill_hub', (6, 2, 6), [9.5, 17.625, -3], hub)
add('drill_bit', 'drill_cone_1', (5, 2, 5), [10, 15.625, -2.5], steel_cone(False), rot=22.5)
add('drill_bit', 'drill_cone_2', (4, 2, 4), [10.5, 13.625, -2], steel_cone(True), rot=45)
add('drill_bit', 'drill_bit_1', (3, 2, 3), [11, 11.625, -1.5], diamond, rot=-22.5)
add('drill_bit', 'drill_bit_2', (2, 1, 2), [11.5, 10.625, -1], diamond)
add('drill_bit', 'drill_tip', (1, 1, 1), [12, 9.625, -0.5], diamond, rot=45)
# Chainsaw: engine at the elbow, exhaust, steel guard, guide bar with the chain running round its nose
add('chainsaw', 'saw_motor', (7, 6, 8), [9, 19.625, -4], saw_motor)
add('chainsaw', 'saw_exhaust', (2, 2, 3), [16, 21.625, -1.5], exhaust)
add('chainsaw', 'saw_guard', (5, 1, 6), [10, 18.625, -3], guard)
add('chainsaw', 'saw_bar', (2, 8, 5), [11.5, 10.125, -2.5], bar)
add('chainsaw_chain', 'saw_chain', (1, 9, 6), [12, 9.625, -3], chain)

json.dump(dict(removed=REMOVED_NAMES, cubes=cubes), open(D + 'tf_plan.json', 'w'), indent=1)
painted = {n: uv for n, uv in nets.items()}
rects = []
for c in cubes:
    if c['name'] not in painted: continue
    w, h, d = [round(c['to'][i] - c['from_'][i]) for i in range(3)]
    u, v = c['uv_offset']; rw, rh = 2 * (w + d), d + h
    rects.append(dict(x=u, y=v, w=rw, h=rh, hex=''.join('%02x%02x%02x%02x' % O[u + x, v + y] for y in range(rh) for x in range(rw))))
# pixels to clear: painted but outside every kept net and every new net (old nets of removed cubes)
clear = [[x, y] for y in range(H) for x in range(W) if not used[y][x] and im.getpixel((x, y))[3]]
json.dump(dict(rects=rects, clear=clear), open(D + 'tf_paint.json', 'w'))
print('removed', len(REMOVED_NAMES), REMOVED_NAMES)
print('new cubes', len(cubes), 'nets', len(rects), 'clear px', len(clear))
base = im.copy()
for x, y in clear: base.putpixel((x, y), (0, 0, 0, 0))
base.alpha_composite(out)
bg = Image.new('RGBA', (W, H), (255, 0, 255, 255)); bg.alpha_composite(base)
bg.resize((W * 4, H * 4), Image.NEAREST).convert('RGB').save(D + 'tf_preview.png')
