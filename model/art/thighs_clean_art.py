"""Thighs without the black dashes (2026-10-19, user: "get rid of those black lines and make it all look more cohesive").

The dashes: dark iron pixels (25292c) painted in a step across the front and back faces of thigh_upper (the block under
the hip drum), left over from an older hip where they drew a seam. On the blue they read as black lines across both
thighs, from the front and from behind. Every dark-iron pixel on a mostly blue face of a thigh cube becomes the blue its
column has elsewhere (the lit / shaded edge columns stay edges), so the faces join the plates around them.
The thigh nets are shared by both legs (the left leg's cubes are not mirror_uv), so one paint fixes both sides.

    uv run --with pillow python3 model/art/thighs_clean_art.py   (reads .scratch/thighs/source.json from the skill's
    scripts/snapshot.mjs + faces.json (live face uv rects of the right leg); writes .scratch/thighs/plan.json for
    pads_clean_apply.js)
"""
import base64, io, json, math
from collections import Counter
from pathlib import Path
from PIL import Image

D = Path('.scratch/thighs')
s = json.loads((D / 'source.json').read_text())
src = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
cubes = json.loads((D / 'faces.json').read_text())
tex = src.copy()


def is_iron(c):
    return max(c) < 0x55 and max(c) - min(c) < 0x20


def rect(uv):
    u1, v1, u2, v2 = uv
    cols = [math.floor(min(u1, u2) + i) for i in range(round(abs(u2 - u1)))]
    rows = [math.floor(min(v1, v2) + j) for j in range(round(abs(v2 - v1)))]
    if u1 > u2: cols.reverse()
    if v1 > v2: rows.reverse()
    return [[(u, v) for u in cols] for v in rows]


fixed = {}
for cb in cubes:
    if not cb['name'].startswith(('thigh_', 'cap_')): continue
    for face, uv in cb['faces'].items():
        g = rect(uv)
        if not g or not g[0]: continue
        px = {p: src.getpixel(p)[:3] for row in g for p in row}
        iron = [p for p, c in px.items() if is_iron(c)]
        if not iron or len(iron) * 2 > len(px): continue          # all blue, or an iron face (undersides): leave
        for j, row in enumerate(g):
            for i, p in enumerate(row):
                if p not in iron: continue
                col = [px[r[i]] for r in g if not is_iron(px[r[i]])]
                line = col or [c for c in (px[q] for q in row) if not is_iron(c)]
                fixed[p] = Counter(line).most_common(1)[0][0]
        print(cb['name'], face, len(iron), 'iron px')

for p, c in fixed.items(): tex.putpixel(p, c + (255,))
(D / 'plan.json').write_text(json.dumps(dict(pixels=''.join('%02x' * 6 % (*p, *c, 255) for p, c in sorted(fixed.items())))))
print(len(fixed), 'pixels')
