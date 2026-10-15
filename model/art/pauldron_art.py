"""Space-marine pauldrons: the side plates reach down over most of the upper arm.

After arms_refit_art.py the pauldron's outer wall and front/back plates were 5 px tall (y 36.625..41.625),
a cap over the shoulder only. Now 10 px (y 31.625..41.625): the upper arm (28.625..40.625) shows 3 px
above the elbow. Same fit as before (0.5 px around the 7x7 upper arm), top plate unchanged.

Art: each face keeps its top rows (light edge, red band, chevron) and its bottom row (grey rim); the
rows added in between keep the edge pixels of the row above the rim, filled with the face's most
common colour, lightly mottled (blue faces: the arm plates' base blue and specks).
New nets are packed into free texture space; the old ones are cleared.

Run: uv run --with pillow python3 model/art/pauldron_art.py
Input .scratch/pads-source.json ({model, texture} of the live project);
output .scratch/pads-plan.json (cube edits with new uv_offset, changed pixels as hex).
"""
import base64
import io
import json
import math
import random
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'pads-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']
rng = random.Random(3)
TOP, BOTTOM, NEW_H = 41.625, 31.625, 10


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
            'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


def net(uv, sz):
    w, h, d = sz
    return {(x, y) for y in range(uv[1], uv[1] + d + h) for x in range(uv[0], uv[0] + 2 * (w + d))}


def grow(img, h):
    """Taller face: top rows and the bottom rim kept. Added rows: the edge pixels of the row above the
    rim, filled with the face's most common colour, lightly mottled (repeating a real row repeated
    its chevron or highlight too)."""
    from collections import Counter
    out = Image.new('RGBA', (img.width, h))
    h0, w = img.height, img.width
    inner = [img.getpixel((x, y)) for y in range(h0) for x in range(1, w - 1)] or [img.getpixel((0, 0))]
    base = Counter(inner).most_common(1)[0][0]
    blue = base[2] > base[0] + 60
    for y in range(h):
        for x in range(w):
            if y < h0 - 1:
                p = img.getpixel((x, y))
            elif y == h - 1:
                p = img.getpixel((x, h0 - 1))
            elif x in (0, w - 1) and w > 2:
                p = img.getpixel((x, h0 - 2))
            elif blue:                              # the arm plates' palette (arms_refit / thighs)
                r = rng.random()
                p = ((35, 88, 178) if r < 0.06 else (58, 123, 214) if r < 0.11 else (45, 107, 204)) + (255,)
            else:
                p = base
                if rng.random() < 0.12:
                    p = tuple(max(0, min(255, int(v * rng.choice([0.88, 1.1])))) for v in p[:3]) + (p[3],)
            out.putpixel((x, y), p)
    return out


pads = [c for c in els if c['name'].startswith(('pad_wall_', 'pad_front_', 'pad_back_'))]
pad_ids = {c['uuid'] for c in pads}
used = set()
for c in els:
    if c['uuid'] not in pad_ids:
        used |= net(c.get('uv_offset', [0, 0]), size(c))
used |= {(x, y) for y in range(tex.height) for x in range(tex.width) if original.getpixel((x, y))[3]} - \
    set().union(*[net(c['uv_offset'], size(c)) for c in pads])
for c in pads:                                              # free the old nets
    for pt in net(c['uv_offset'], size(c)) - used:
        tex.putpixel(pt, (0, 0, 0, 0))


def pack(sz):
    w, h, d = sz
    nw, nh = 2 * (w + d), d + h
    for v in range(tex.height - nh + 1):
        for u in range(tex.width - nw + 1):
            pts = {(x, y) for y in range(v, v + nh) for x in range(u, u + nw)}
            if not used & pts:
                used.update(pts)
                return [u, v]
    raise SystemExit(f'no room for a {sz} net')


edits = []
for c in pads:
    old = size(c)
    new = [old[0], NEW_H, old[2]]
    uv = pack(new)
    u0, v0 = c['uv_offset']
    for f, (x, y, w, h) in faces(*new).items():
        ox, oy, ow, oh = faces(*old)[f]
        patch = original.crop((u0 + ox, v0 + oy, u0 + ox + ow, v0 + oy + oh))
        if f not in ('up', 'down'):
            patch = grow(patch, h)
        tex.paste(patch, (uv[0] + x, uv[1] + y))
    edits.append(dict(name=c['name'], from_=[c['from'][0], BOTTOM, c['from'][2]],
                      to=[c['to'][0], TOP, c['to'][2]], uv_offset=uv))

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
(scratch/'pads-plan.json').write_text(json.dumps(dict(
    edits=edits,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print([(e['name'], e['uv_offset']) for e in edits], len(changed), 'px')
