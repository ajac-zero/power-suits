# Tool socket bases (user, 2026-10-18): the wrist ring leaves the forearm (the gauntlet's flat underside is the
# interface) and every socket option brings its own 7x1x7 base where the ring was (y 19.625..20.625): the empty
# socket keeps the ring's art (socket_ring -> socket_base on the socket bone), each tool gets a base painted in its own
# colours. Plans + paints the four new nets in free texture space (transparent and outside every cube's net).
# Inputs: /private/tmp/fa/tex2.png + model.json (live project). Writes /private/tmp/fa/bases_plan.json + preview.
import json, math
from PIL import Image
D = '/private/tmp/fa/'
im = Image.open(D + 'tex2.png').convert('RGBA'); W, H = im.size
M = json.load(open(D + 'model.json'))
used = [[im.getpixel((x, y))[3] > 0 for x in range(W)] for y in range(H)]
def mark(x0, y0, w, h):
    for y in range(max(0, y0), min(H, y0 + h)):
        for x in range(max(0, x0), min(W, x0 + w)): used[y][x] = True
for e in M['elements']:
    if e.get('box_uv', True):
        u, v = e.get('uv_offset', [0, 0]); w, h, d = [math.floor(abs(e['to'][i] - e['from'][i]) + 1e-6) for i in range(3)]
        mark(u, v, 2 * (w + d), d + h)
    else:
        for f in e.get('faces', {}).values():
            uv = f.get('uv')
            if uv:
                x0, x1 = sorted([uv[0], uv[2]]); y0, y1 = sorted([uv[1], uv[3]])
                mark(math.floor(x0), math.floor(y0), math.ceil(x1) - math.floor(x0), math.ceil(y1) - math.floor(y0))
def place(w, h):
    for v in range(H - h + 1):
        for u in range(W - w + 1):
            if all(not used[y][x] for y in range(v, v + h) for x in range(u, u + w)):
                mark(u, v, w, h); return u, v
    raise SystemExit(f'no space for {w}x{h}')
C = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
# per tool: (top/underside fill, underside rim, side main, side ends (bolts), side centre accent)
STYLE = {
    'chaingun_base': ('25292c', '3b4246', '2f3437', 'a2acb1', '565f63'),   # gunmetal, steel bolts
    'em_base':       ('25292c', '8c482c', 'ba6840', '3b4246', 'de9262'),   # copper band, dark iron bolts
    'drill_base':    ('1b4895', '2358b2', '2358b2', 'a2acb1', '4588e0'),   # the drill hub's blue
    'saw_base':      ('3b4246', '6f797e', '6f797e', '2f3437', 'c98426'),   # grey, the saw's orange
}
plan, out = {}, im.copy(); O = out.load()
for name, (fill, rim, side, bolt, acc) in STYLE.items():
    u, v = place(28, 8)            # 7x1x7 box net: top (u+7, v), bottom (u+14, v), sides 7x1 at y v+7
    for f0 in (7, 14):
        for y in range(7):
            for x in range(7):
                edge = x in (0, 6) or y in (0, 6)
                O[u + f0 + x, v + y] = C(rim if (edge and f0 == 14) else fill)
    for s0 in (0, 7, 14, 21):
        for x in range(7):
            O[u + s0 + x, v + 7] = C(bolt if x in (0, 6) else acc if x == 3 else side)
    plan[name] = [u, v]
json.dump({'nets': plan, 'pixels': [[x, y, '%02x%02x%02x' % O[x, y][:3]] for name, (u, v) in plan.items()
           for y in range(v, v + 8) for x in range(u, u + 28) if (x - u >= 7 and x - u < 21 and y - v < 7) or y - v == 7]},
          open(D + 'bases_plan.json', 'w'))
out.crop((0, 0, W, H)).resize((W * 4, H * 4), Image.NEAREST).save(D + 'bases_preview.png')
print(plan)
