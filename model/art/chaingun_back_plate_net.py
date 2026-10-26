"""Chaingun back plate's own texels (2026-10-26): chaingun_plate_back first shared chaingun_plate_front's net, whose greys
are the reserved heat colours (tools/make-glowmasks.py: the front collar glows when the barrels overheat), so the back
plate, next to the socket, glowed too. This copies the front plate's 6x1x6 box net (24x7) into free texture space with
each reserved grey swapped for the cold grey one step off (89939b -> 89939a, 7d888f -> 7d888e, 6f797f -> 6f797e: the
same colours to the eye) and writes the plan for chaingun_back_plate_net_apply.js.

    uv run --with pillow python3 model/art/chaingun_back_plate_net.py <live project .bbmodel> <plan.json>
"""
import base64
import io
import json
import math
import sys

from PIL import Image

model = json.load(open(sys.argv[1]))
tex = Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',', 1)[1]))).convert('RGBA')
W, H = tex.size
COLD = {(0x89, 0x93, 0x9b): (0x89, 0x93, 0x9a), (0x7d, 0x88, 0x8f): (0x7d, 0x88, 0x8e), (0x6f, 0x79, 0x7f): (0x6f, 0x79, 0x7e)}

# free texture space = transparent pixels outside every cube's net (a box-UV cube without uv_offset sits at [0, 0])
used = [[tex.getpixel((x, y))[3] > 0 for x in range(W)] for y in range(H)]


def mark(x0, y0, w, h):
    for y in range(max(0, y0), min(H, y0 + h)):
        for x in range(max(0, x0), min(W, x0 + w)):
            used[y][x] = True


front = None
for e in model['elements']:
    if e['name'] == 'chaingun_plate_front':
        front = e
    if e.get('box_uv', True):
        u, v = e.get('uv_offset', [0, 0])
        w, h, d = [math.floor(abs(e['to'][i] - e['from'][i]) + 1e-6) for i in range(3)]
        mark(u, v, 2 * (w + d), d + h)
    else:
        for f in e.get('faces', {}).values():
            uv = f.get('uv')
            if uv:
                x0, x1 = sorted([uv[0], uv[2]]); y0, y1 = sorted([uv[1], uv[3]])
                mark(math.floor(x0), math.floor(y0), math.ceil(x1) - math.floor(x0), math.ceil(y1) - math.floor(y0))
assert front and front.get('box_uv', True), 'chaingun_plate_front must be a box-UV cube'
fu, fv = front['uv_offset']
NW, NH = 24, 7  # 6x1x6 box net


def place():
    for v in range(H - NH + 1):
        for u in range(W - NW + 1):
            if all(not used[y][x] for y in range(v, v + NH) for x in range(u, u + NW)):
                return u, v
    raise SystemExit('no free 24x7 space')


u, v = place()
pixels, swapped = [], 0
for y in range(NH):
    for x in range(NW):
        p = tex.getpixel((fu + x, fv + y))
        if p[3] == 0:
            continue
        rgb = COLD.get(p[:3], p[:3])
        swapped += rgb != p[:3]
        pixels.append([u + x, v + y, '%02x%02x%02x' % rgb])
json.dump({'uv_offset': [u, v], 'pixels': pixels}, open(sys.argv[2], 'w'))
print(f'back plate net at {u},{v}: {len(pixels)} pixels, {swapped} reserved greys swapped for cold ones')
