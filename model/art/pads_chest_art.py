"""Pads painted like the breastplate (2026-10-19, user: "look at the chestplate, why not do something like that?" The
noise they meant was the red stripes, the white chevron and the grey rim, not the texture).

The breastplate's recipe (sm_chest_art.py bp_front): flat armour blue 2d6bcc, light from the viewer's upper left on
every face: a lit column (4588e0) and a half-lit one (3a7bd6) on the left, a shaded one (2358b2) and a dark one
(1b4895) on the right, a highlight pixel in the lit corner and a few 1 px wear marks. Here the same recipe is laid
over each pad's visible sides as ONE panel: the back plate, the front plate and the outer side (the back / front
plates' 1 px edges, the wall and the fill blocks) take their column from the pixel's world position across the whole
pad (so the pieces of a side meet without seams), plus:
  top plate's 1 px sides   the highlight rim (62a3ef) that catches the light, lit on the left, shaded at the right end
  roof (up face)           the same panel recipe seen from above
  plates' lowest row       shaded (2358b2) like the breastplate's underside
  undersides / insides     flat shaded blue / flat dark iron, as before.
No red, white or grey. Each face is painted as seen from outside through its live uv rect (box-UV mirror_uv reverses
the rect; painting through it keeps "left" the viewer's left on both pads).

    uv run --with pillow python3 model/art/pads_chest_art.py
    (reads .scratch/pads-clean/source.json (snapshot.mjs) + faces.json (live face uv rects); writes plan_chest.json for
    pads_clean_apply.js)
"""
import base64, io, json, math, random
from pathlib import Path
from PIL import Image

D = Path('.scratch/pads-clean')
s = json.loads((D / 'source.json').read_text())
src = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')   # the original pads
cubes = json.loads((D / 'faces.json').read_text())
tex = src.copy()

hexc = lambda h: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
P = {k: hexc(v) for k, v in dict(H='62a3ef', L='4588e0', M='3a7bd6', B='2d6bcc', S='2358b2', D='1b4895',
                                 I='2f3437', I1='25292c', U='1f4a8e').items()}
rng = random.Random(19)


def is_iron(c):
    return max(c) < 0x55 and max(c) - min(c) < 0x20


def rect_pixels(uv):
    """Face pixels as rows (top to bottom) of columns (viewer's left to right), through the uv rect's direction."""
    u1, v1, u2, v2 = uv
    cols = [math.floor(min(u1, u2) + i) for i in range(round(abs(u2 - u1)))]
    rows = [math.floor(min(v1, v2) + j) for j in range(round(abs(v2 - v1)))]
    if u1 > u2: cols.reverse()
    if v1 > v2: rows.reverse()
    return [[(u, v) for u in cols] for v in rows]


# viewer's-left edge and direction along the face, per side face: (axis, sign): world coord = left + sign * s
ALONG = {'north': (0, -1), 'south': (0, +1), 'east': (2, -1), 'west': (2, +1)}
PAD_X, PAD_Z = (7.5, 16.0), (-4.75, 4.75)


def column(face, cube, i, fw, right_pad):
    """Distances (px) of pixel i's centre from the viewer's left and right edges of the whole pad side."""
    ax, sg = ALONG[face]
    lo, hi = cube['from'][ax], cube['to'][ax]
    w = (i + 0.5) / fw * (hi - lo)
    pos = (hi - w) if sg < 0 else (lo + w)
    plo, phi = PAD_Z if ax == 2 else (PAD_X if right_pad else (-PAD_X[1], -PAD_X[0]))
    return ((phi - pos, pos - plo) if sg < 0 else (pos - plo, phi - pos))


def tone(dl, dr):
    return 'L' if dl < 1 else 'M' if dl < 2 else 'D' if dr < 1 else 'S' if dr < 2 else 'B'


def tone_i(i, n):                                            # the same by pixel index, for a face that is the panel
    return tone(i + 0.5, n - i - 0.5)


mine = set()
for cb in cubes:
    name, right = cb['name'], cb['name'].endswith('_r')
    for face, uv in cb['faces'].items():
        grid = rect_pixels(uv)
        if not grid or not grid[0]: continue
        fh, fw = len(grid), len(grid[0])
        old = [src.getpixel(p)[:3] for row in grid for p in row]
        if sum(map(is_iron, old)) * 2 > len(old):
            paint = [['I1' if (j == fh - 1 and fh > 2) else 'I'] * fw for j in range(fh)]
        elif name.startswith('pad_ramp'):
            paint = [['U' if face == 'down' else 'S'] * fw for _ in range(fh)]
        elif face == 'down':
            paint = [['S'] * fw for _ in range(fh)]
        elif face == 'up':
            if name.startswith('pad_top'):                       # the roof: the panel seen from above
                paint = [[tone_i(i, fw) for i in range(fw)] for _ in range(fh)]
                paint[0] = ['H'] * (fw - 1) + ['L']
                paint[-1] = [('M' if t in 'LM' else 'S' if t == 'B' else 'D') for t in paint[-1]]
            else:
                paint = [['B'] * fw for _ in range(fh)]          # plate tops hidden under the roof
        else:
            cols = [column(face, cb, i, fw, right) for i in range(fw)]
            if name.startswith('pad_top'):                       # the rim above the plates
                paint = [['L' if dr < 1 else 'H' for dl, dr in cols]]
            else:
                paint = [[tone(dl, dr) for dl, dr in cols] for _ in range(fh)]
                if name.startswith(('pad_back', 'pad_front')) and fh > 2:
                    paint[-1] = ['D' if dr < 1 else 'S' for dl, dr in cols]   # the plate's lower edge
                    if cols[0][0] < 1 and fw > 2: paint[0][1] = 'H'             # the lit corner
        if fw > 2 and fh > 2 and not paint[0][0].startswith('I') and not name.startswith('pad_ramp'):
            for _ in range(max(1, fw * fh // 16)):              # a few wear marks, like the breastplate
                x, y = rng.randrange(1, fw - 1), rng.randrange(1, fh - 1)
                if paint[y][x] == 'B': paint[y][x] = 'M' if rng.random() < 0.75 else 'S'
        for row_p, row_c in zip(grid, paint):
            for p, k in zip(row_p, row_c):
                tex.putpixel(p, P[k] + (255,)); mine.add(p)

px = [(x, y, *tex.getpixel((x, y))) for (x, y) in sorted(mine)]
(D / 'plan_chest.json').write_text(json.dumps(dict(pixels=''.join('%02x' * 6 % p for p in px))))
tex.save(D / 'shade_chest.png')
print(len(px), 'pixels')
