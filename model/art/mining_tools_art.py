# Plans + paints the drill and chainsaw nets: finds free texture space, draws faces, writes
# plan.json (cubes with uv_offset) + paint.json (rects with hex rgba) + preview.png
import json
from PIL import Image
im = Image.open('/private/tmp/p5/tex.png').convert('RGBA')
W, H = im.size
A = im.load()
used = [[A[x, y][3] > 0 for x in range(W)] for y in range(H)]
import math
M = json.load(open('/private/tmp/p5/model.json'))
for e in M['elements']:
    e.setdefault('uv_offset', [0, 0])
    sz = [math.ceil(abs(e['to'][i] - e['from'][i]) - 1e-6) for i in range(3)]
    u, v = e['uv_offset']; w, h, d = sz
    for y in range(v, min(H, v + d + h)):
        for x in range(u, min(W, u + 2 * (w + d))): used[y][x] = True
def H_(c): return tuple(int(c[i:i+2], 16) for i in (1, 3, 5)) + (255,)
P = {k: H_(v) for k, v in dict(
    k0='#1a1d1f', k1='#25292c', k2='#2f3437', k3='#3b4246', k4='#4c5559', k5='#565f63', k6='#6f797e',
    k7='#7d888e', k8='#89939a', k9='#a2acb1', kA='#c8d0d3', kW='#e6edf0',
    b0='#1b4895', b1='#2358b2', b2='#2d6bcc', b3='#4588e0', b4='#62a3ef',
    d0='#0e7b7a', d1='#12a1a0', d2='#2ce3d9', d3='#a1fbe8', d4='#e4fffa',
    o1='#c98426', o2='#e2a23e', r1='#c43028').items()}

def fits(u, v, w, h):
    if u + w > W or v + h > H: return False
    return all(not used[y][x] for y in range(v, v + h) for x in range(u, u + w))
def place(w, h, prefer):
    for (x0, y0, x1, y1) in prefer:
        for v in range(y0, y1):
            for u in range(x0, x1):
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

# ---- face painters --------------------------------------------------------------------------
def hub(f, x, y, w, h):          # dark motor hub with bolts and a blue band
    if f in ('up', 'down'):
        c = (w - 1) / 2; r = max(abs(x - c), abs(y - c))
        return 'k1' if r < 1 else ('k4' if r < 2 else ('k2' if r < w / 2 - 1 else 'k3'))
    if y == 0: return 'b3' if x % 3 else 'k9'
    return 'b1' if x % 3 else 'k8'
def steel_cone(studs):
    def fn(f, x, y, w, h):
        if f == 'up': return 'k5'
        if f == 'down':            # visible ledge facing the tip: worn steel with diamond studs at corners
            if studs and (x in (0, w - 1)) and (y in (0, w - 1)): return 'd2'
            return 'k7' if (x + y) % 2 else 'k8'
        # sides: spiral flutes (diagonals) so the spin reads as an auger
        k = (x + y) % 3
        if y == 0: return 'kA' if k else 'k9'
        if studs and y == h - 1 and x % 2 == 0: return 'd3'
        return 'k4' if k == 0 else ('k9' if k == 1 else 'k7')
    return fn
def diamond(f, x, y, w, h):     # vanilla diamond palette, facet shading
    if w == 1 and h == 1: return 'd4' if f in ('down', 'north', 'east') else 'd2'
    if (x + y) == 0: return 'd4'
    if x == w - 1 or y == h - 1: return 'd1' if (x + y) % 2 else 'd0'
    return 'd3' if (x + y) % 3 == 0 else 'd2'
def motor(f, x, y, w, h):       # blue armour housing, vent grille on the sides, hazard strip front
    if f == 'up': return 'k3'
    if f == 'down': return 'k2' if 2 <= x < w - 2 else 'b0'
    if y == 0: return 'b4' if x else 'b3'
    if y == h - 1: return 'b0'
    if f in ('east', 'west') and 2 <= x < w - 2 and 1 <= y < h - 1:
        return 'k1' if y % 2 else 'k4'                      # grille
    if f in ('north', 'south') and y == 1 and 1 <= x < w - 1:
        return 'o2' if (x // 2) % 2 else 'k1'               # hazard stripe
    return 'b1' if x == w - 1 else 'b2'
def bar(f, x, y, w, h):         # guide bar: bright steel plates, darker rim + groove, warning label
    if f in ('up', 'down'): return 'k6'
    if f in ('north', 'south'): return 'k6' if y % 2 else 'k5'
    if x in (0, w - 1) or y == h - 1: return 'k6'
    if 1 <= y <= 2 and 1 <= x < w - 1: return 'o2' if y == 1 else 'o1'
    if y == h - 3 and x == w // 2: return 'k3'          # nose sprocket rivet
    return 'kA' if (x == 1 and y < h - 2) else 'k9'
def chain(f, x, y, w, h):       # cutting chain: dark links with bright teeth every other link
    if f in ('up',): return 'k1'
    if f == 'down': return 'kW' if x % 2 == 0 else 'k1'
    if f in ('north', 'south'): return 'kW' if y % 2 == 0 else 'k2'
    if x in (0, w - 1) or y == h - 1: return 'kA' if (y + x) % 2 == 0 else 'k1'
    return 'k1'
def exhaust(f, x, y, w, h):
    if f == 'east' or f == 'west': return 'k0' if (0 < x < w - 1 and 0 < y < h - 1) else 'k4'
    return 'k3' if y else 'k6'

BIG = [(0, 0, 256, 128)]
SMALL = BIG
O_TOOL = [12.5, 14.625, 0]
cubes = []
def add(bone, name, size, frm, painter, rot=0, prefer=BIG, origin=None):
    w, h, d = size
    u, v = place(2 * (w + d), d + h, prefer)
    paint(u, v, size, painter)
    to = [frm[0] + w, frm[1] + h, frm[2] + d]
    c = dict(bone=bone, name=name, from_=frm, to=to, uv_offset=[u, v])
    if rot:
        c['rotation'] = [0, rot, 0]
        c['origin'] = [(frm[0] + to[0]) / 2, (frm[1] + to[1]) / 2, (frm[2] + to[2]) / 2]
    else:
        c['origin'] = origin or O_TOOL
    cubes.append(c)

# Chainsaw first (biggest nets get the big free block)
add('chainsaw', 'saw_chain', (1, 13, 6), [12, -1.375, -3], chain)
add('chainsaw', 'saw_bar', (2, 12, 5), [11.5, -0.375, -2.5], bar)
add('chainsaw', 'saw_motor', (9, 4, 8), [8, 11.625, -4], motor)
add('chainsaw', 'saw_exhaust', (2, 2, 3), [17, 12.625, -1.5], exhaust, prefer=SMALL)
# Drill: a hub and a stepped, twisted cone ending in a diamond bit
add('drill', 'drill_hub', (7, 2, 7), [9, 13.625, -3.5], hub)
add('drill', 'drill_cone_1', (6, 2, 6), [9.5, 11.625, -3], steel_cone(False), rot=22.5)
add('drill', 'drill_cone_2', (5, 2, 5), [10, 9.625, -2.5], steel_cone(True), rot=45)
add('drill', 'drill_bit_1', (4, 2, 4), [10.5, 7.625, -2], diamond, rot=-22.5, prefer=SMALL)
add('drill', 'drill_bit_2', (2, 2, 2), [11.5, 5.625, -1], diamond, prefer=SMALL)
add('drill', 'drill_tip', (1, 1, 1), [12, 4.625, -0.5], diamond, rot=45, prefer=SMALL)

json.dump(cubes, open('/private/tmp/p5/plan.json', 'w'), indent=1)
# paint payload: bounding rects of new pixels (only non-transparent are written)
rects = []
for c in cubes:
    s = [c['to'][i] - c['from_'][i] for i in range(3)]
    s = [round(v) for v in s]
    u, v = c['uv_offset']; w, h, d = s
    rw, rh = 2 * (w + d), d + h
    hexs = ''.join('%02x%02x%02x%02x' % O[u + x, v + y] for y in range(rh) for x in range(rw))
    rects.append(dict(x=u, y=v, w=rw, h=rh, hex=hexs))
json.dump(rects, open('/private/tmp/p5/paint.json', 'w'))
for c in cubes: print(c['bone'], c['name'], c['uv_offset'])
comp = im.copy(); comp.alpha_composite(out)
bg = Image.new('RGBA', (W, H), (255, 0, 255, 255)); bg.alpha_composite(comp)
bg.crop((112, 88, 256, 128)).resize((144 * 6, 40 * 6), Image.NEAREST).convert('RGB').save('/private/tmp/p5/preview.png')
