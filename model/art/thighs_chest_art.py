"""Thighs painted with the breastplate / pad recipe (2026-10-19, user: "get rid of those black lines and make it all look
more cohesive"; first step thighs_clean_art.py removed the black dashes).

Each thigh is built from many blocks (thigh_upper + the drum caps under the hip, the sloped top, the shoulder slab, the
main block, the front plate, the fill blocks, the ramp under the knee). Each had its own lit / shaded edge columns and
its own wear marks, so the edges between blocks drew lines inside the thigh. Here every blue side face takes its tone
from the pixel's position across the WHOLE thigh (bone coordinates: x 1..10 front / back, z -4..4 sides), like the pads:
the lit edge 4588e0 then 3a7bd6, flat 2d6bcc, then 2358b2 and 1b4895 on the shaded edge, so stacked blocks share their
columns. Light keeps each face's old side (the brighter edge column of the old face stays the lit one), so the legs stay
mirror images and match the shins below.
  top rows       the shoulder slab: the highlight rim 62a3ef; the sloped top: 4588e0 (62a3ef read as a stripe
                 across the side, under the drum)
  lowest rows    the front plate's and the main block's bottom row 2358b2 (was 1b4895, a dark line)
  wear marks     sparse 1 px marks (about 1 per 16 px), like the breastplate
Undersides, roofs and the dark iron insides keep their pixels.
`keep` (v2, user: v1 "too flat"): inside each face the old wear pattern stays (its marks give the surface its grain);
only the edge columns (now by the whole thigh, the blocks' own edge columns removed), the top / bottom rows and the
black dashes change. The nets are shared by both legs (one paint, both sides).

    uv run --with pillow python3 model/art/thighs_chest_art.py [keep]   (reads .scratch/thighs/source.json + faces.json;
    writes .scratch/thighs/plan_chest.json for pads_clean_apply.js)
"""
import base64, io, json, math, random, sys
from pathlib import Path
from PIL import Image

D = Path('.scratch/thighs')
s = json.loads((D / 'source.json').read_text())
src = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
cubes = json.loads((D / 'faces.json').read_text())
tex = src.copy()
hexc = lambda h: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
P = {k: hexc(v) for k, v in dict(H='62a3ef', L='4588e0', M='3a7bd6', B='2d6bcc', S='2358b2', D='1b4895').items()}
BLUES = set(P.values()) | {hexc('8ccaff')}
rng = random.Random(23)
KEEP = len(sys.argv) > 1 and sys.argv[1] == 'keep'   # v2: keep the old texture inside the faces (user: v1 too flat)
ALONG = {'north': (0, -1), 'south': (0, +1), 'east': (2, -1), 'west': (2, +1)}   # viewer's left edge: axis, direction
RANGE = {0: (1.0, 10.0), 2: (-4.0, 4.0)}                                        # the thigh's extent (right leg)


def rect(uv):
    u1, v1, u2, v2 = uv
    cols = [math.floor(min(u1, u2) + i) for i in range(round(abs(u2 - u1)))]
    rows = [math.floor(min(v1, v2) + j) for j in range(round(abs(v2 - v1)))]
    if u1 > u2: cols.reverse()
    if v1 > v2: rows.reverse()
    return [[(u, v) for u in cols] for v in rows]


lum = lambda c: sum(c)
done = set()
for cb in cubes:
    name = cb['name']
    if not name.startswith(('thigh_', 'cap_')): continue
    for face, uv in cb['faces'].items():
        if face in ('up', 'down'): continue
        g = rect(uv)
        if not g or not g[0]: continue
        if g[0][0] in done: continue                     # the drum caps share one net
        fh, fw = len(g), len(g[0])
        old = [[src.getpixel(p)[:3] for p in row] for row in g]
        if sum(c in BLUES for row in old for c in row) * 2 < fw * fh: continue    # not an armour face
        lit_left = sum(lum(r[0]) for r in old) >= sum(lum(r[-1]) for r in old)
        ax, sg = ALONG[face]
        lo, hi = cb['from'][ax], cb['to'][ax]
        plo, phi = RANGE[ax]
        paint = []
        for j in range(fh):
            row = []
            for i in range(fw):
                w = (i + 0.5) / fw * (hi - lo)
                pos = (hi - w) if sg < 0 else (lo + w)
                dl, dr = ((phi - pos, pos - plo) if sg < 0 else (pos - plo, phi - pos))
                if fw == 1: dl, dr = (0.5, 9) if lit_left else (9, 0.5)          # a 1 px edge face: lit or shaded
                dlit, dsh = (dl, dr) if lit_left else (dr, dl)
                row.append('L' if dlit < 1 else 'M' if dlit < 2 else 'D' if dsh < 1 else 'S' if dsh < 2 else 'B')
            paint.append(row)
        if name.startswith('thigh_shoulder') and fh > 1: paint[0] = ['H'] * fw
        if name.startswith('thigh_top') and fh > 1: paint[0] = ['L'] * fw      # H read as a stripe across the side
        if (name.startswith('thigh_front') and face == 'north' or name.startswith('thigh_main') and face == 'south') \
                and fh > 2:
            paint[-1] = ['S'] * fw
        if KEEP:                                          # the old wear pattern inside, the old edge columns gone
            for j in range(fh):
                for i in range(1, fw - 1):
                    if paint[j][i] == 'B' and old[j][i] in BLUES: paint[j][i] = old[j][i]
        elif fw > 2 and fh > 2:
            for _ in range(max(1, fw * fh // 16)):
                x, y = rng.randrange(1, fw - 1), rng.randrange(1, fh - 1)
                if paint[y][x] == 'B': paint[y][x] = 'M' if rng.random() < 0.75 else 'S'
        for row_p, row_c in zip(g, paint):
            for p, k in zip(row_p, row_c):
                tex.putpixel(p, (P[k] if isinstance(k, str) else k) + (255,)); done.add(p)
        print(f'{name:16} {face:5} {fw}x{fh} lit {"left" if lit_left else "right"}')

px = [(x, y, *tex.getpixel((x, y))) for (x, y) in sorted(done)]
(D / ('plan_keep.json' if KEEP else 'plan_chest.json')).write_text(json.dumps(dict(pixels=''.join('%02x' * 6 % p for p in px))))
print(len(px), 'pixels')
