"""Thighs painted as plates, like the breastplate (2026-10-19, user on thighs_chest_art.py: "still way too flat, the
thighs read like tubes or something, make them more similar to the chestplate").

Why tubes: thighs_chest_art.py shaded every face by columns across the whole thigh (lit edge, flat middle, dark edge),
which is how a cylinder is shaded. The breastplate reads as armour because its shading runs in horizontal bands, plate by
plate (sm_chest_art.py, the chest's east face): a highlight rim along each plate's top edge (62a3ef), a shadow row
(2358b2) where a plate tucks under the one above, light 1 px marks (3a7bd6 / 4588e0, about 1 per 10 px, never touching) on the flat blue, only soft edge
columns (3a7bd6 on the lit side, 2358b2 on the shaded one) and a shaded bottom row. The same here, block by block:
  thigh_upper, drum caps,     the upper piece, painted as ONE plate (v2): marked body, lit edge column, no rims or
  thigh_top, thigh_shoulder   shaded columns between its blocks (v1 gave thigh_top a rim: it cut the piece in two)
  thigh_main                  the band under it: a shadow row on top (tucked under the upper piece), marked body
  thigh_front                 the lower front plate, continuing the band: marked body, shaded bottom row over the knee
  1 px faces, the ramp        edge / underside shades
The lit side of each face stays where it was (the old face's brighter edge column), so the legs stay mirror images.
Undersides, roofs and the dark iron insides keep their pixels; the black dashes (thighs_clean_art.py) stay gone.

    uv run --with pillow python3 model/art/thighs_plate_art.py [ridge|panel] [studs|bolts|ridge|slits|panel]   (reads .scratch/thighs/source.json + faces.json;
    writes .scratch/thighs/plan_plate.json for pads_clean_apply.js)
"""
import base64, io, json, math, random, sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import thigh_upper_world as upper_world   # noqa: E402

D = Path('.scratch/thighs')
s = json.loads((D / 'source.json').read_text())
src = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
cubes = json.loads((D / 'faces.json').read_text())
tex = src.copy()
hexc = lambda h: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
P = {k: hexc(v) for k, v in dict(H='62a3ef', L='4588e0', M='3a7bd6', B='2d6bcc', S='2358b2', D='1b4895').items()}
BLUES = set(P.values()) | {hexc('8ccaff')}
rng = random.Random(29)


def rect(uv):
    u1, v1, u2, v2 = uv
    cols = [math.floor(min(u1, u2) + i) for i in range(round(abs(u2 - u1)))]
    rows = [math.floor(min(v1, v2) + j) for j in range(round(abs(v2 - v1)))]
    if u1 > u2: cols.reverse()
    if v1 > v2: rows.reverse()
    return [[(u, v) for u in cols] for v in rows]


# v2 (user: "it segments the top of the legs, thigh_top should read as the same piece as thigh_upper"): the blocks of the
# upper thigh (thigh_upper, the drum caps, thigh_top, thigh_shoulder) are ONE piece: no rims or shaded rows between them,
# and on their front / back faces no shaded column where one block meets the next (UPPER); only the band under them
# (thigh_main) keeps its tuck row, so the leg reads as an upper piece over a lower one.
ROLE = {'thigh_top': (None, None), 'thigh_shoulder': (None, None), 'thigh_main': ('S', None), 'thigh_front': (None, 'S'),
        'thigh_upper': (None, None), 'cap_c': (None, None), 'cap_f': (None, None), 'cap_b': (None, None),
        'thigh_mid_f': (None, None)}
UPPER = ('thigh_top', 'thigh_shoulder', 'thigh_upper', 'cap_')
lum = lambda c: sum(c)
DETAIL = sys.argv[1] if len(sys.argv) > 1 else None     # v3 (user: "now add more detail"): 'ridge' or 'panel'
UPPER_DETAIL = sys.argv[2] if len(sys.argv) > 2 else None   # the upper piece: studs | ridge | slits | bolts | panel (on
# thigh_upper alone, rejected as lopsided) or inset | row | corners (across the whole piece, thigh_upper_world.py)


def recess(paint, x0, x1, y0, y1, lit_left):
    """A pressed-in panel: shadow along its top and lit-side walls, light along the bottom and the far wall."""
    for j in range(y0, y1 + 1):
        for i in range(x0, x1 + 1): paint[j][i] = 'B'
    lit_wall, sh_wall = (x0, x1) if lit_left else (x1, x0)
    for i in range(x0, x1 + 1): paint[y0][i], paint[y1][i] = 'S', 'L'
    for j in range(y0, y1 + 1): paint[j][lit_wall], paint[j][sh_wall] = 'S', 'L'
    paint[y1][lit_wall] = 'M'; paint[y0][sh_wall] = 'M'


def detail(name, face, paint, lit_left):
    """Detail in the armour's own blues (no new colours: the user calls red / white / grey trim noise):
      studs      a row of studs round the band (thigh_main): a 62a3ef head with a 2358b2 shadow under it, every 3 px
      ridge      the lower front plate gets a raised spine down its middle (lit side 4588e0, shaded side 2358b2),
                 like the abdomen plate's ridge
      panel      or a recessed panel in it instead: shadow on the inner top / lit-side walls, light on the others;
                 plus a smaller one on the upper piece (thigh_upper's front and back; the user picked 'panel')"""
    fh, fw = len(paint), len(paint[0])
    if name.startswith('thigh_main') and fh >= 3 and fw >= 5:
        xs = range(2, fw - 2, 3) if lit_left else range(fw - 3, 1, -3)
        for x in xs:
            for a in (x - 1, x, x + 1):                      # clear wear marks next to a stud
                for b in (1, 2):
                    if paint[b][a] in ('M', 'L'): paint[b][a] = 'B'
            paint[1][x] = 'H'
            if fh > 3 or face != 'south': paint[2][x] = 'S'
    if UPPER_DETAIL and name.startswith('thigh_upper') and face in ('north', 'south') and fh >= 5 and fw >= 5:
        # the upper piece's own detail (user: "you need to also change the top part"; the 3x3 panel was rejected, so
        # variations), front and back, on its big flat face under the belt, inside the face so the piece reads as one
        lit = 1 if lit_left else -1                      # +1: the lit side is the face's left
        c = fw // 2
        if UPPER_DETAIL == 'panel':
            recess(paint, 1, fw - 2, 1, fh - 2, lit_left)
        elif UPPER_DETAIL == 'studs':                    # two studs, like the band's
            for x in (1, fw - 2): paint[1][x], paint[2][x] = 'H', 'S'
        elif UPPER_DETAIL == 'bolts':                    # a bolted plate: four studs at its corners
            for x in (1, fw - 2):
                for y in (1, fh - 2): paint[y][x] = 'H'
                paint[2][x] = 'S'
        elif UPPER_DETAIL == 'ridge':                    # a raised spine down the middle, lit on the lit side
            for j in range(0, fh - 1): paint[j][c], paint[j][c + lit] = 'L', 'S'
            paint[0][c] = 'H'
        elif UPPER_DETAIL == 'slits':                    # two vent slits: a dark slot with a lit lower lip
            for x in (1, fw - 2):
                for j in (1, 2): paint[j][x] = 'D'
                paint[3][x] = 'L'
    if name.startswith('thigh_front') and face == 'north' and fh >= 5:
        body = range(0, fh - 1)
        if DETAIL == 'ridge':
            c = fw // 2
            for j in body:
                paint[j][c] = 'L'
                paint[j][c + 1 if lit_left else c - 1] = 'S'
                paint[j][c - 1 if lit_left else c + 1] = 'B'
        elif DETAIL == 'panel':
            recess(paint, 2, fw - 3, 0, fh - 2, lit_left)


done = set()
for cb in cubes:
    name = cb['name']
    if not name.startswith(('thigh_', 'cap_')): continue
    for face, uv in cb['faces'].items():
        if face in ('up', 'down'): continue
        g = rect(uv)
        if not g or not g[0] or g[0][0] in done: continue          # the drum caps share one net
        fh, fw = len(g), len(g[0])
        old = [[src.getpixel(p)[:3] for p in row] for row in g]
        if sum(c in BLUES for row in old for c in row) * 2 < fw * fh: continue
        lit_left = sum(lum(r[0]) for r in old) >= sum(lum(r[-1]) for r in old)
        role = next((v for k, v in ROLE.items() if name.startswith(k)), None)
        if name.startswith('thigh_ramp') or role is None:            # the slope under the knee: shaded
            paint = [['S'] * fw for _ in range(fh)]
        elif fw == 1:                                                  # a 1 px edge face
            paint = [['M' if lit_left else 'S'] for _ in range(fh)]
        else:
            top, bottom = role
            if name.startswith('thigh_main') and face == 'south': bottom = 'S'   # its back face is a lower edge too
            paint = [['B'] * fw for _ in range(fh)]
            for j in range(fh):
                paint[j][0 if lit_left else -1] = 'M'
                if not (name.startswith(UPPER) and face in ('north', 'south')): paint[j][-1 if lit_left else 0] = 'S'
            marked = set()
            if fh > 2 or bottom is None:
                for _ in range(max(1, fw * fh // 10)):                # light wear marks, like the breastplate's,
                    x, y = rng.randrange(1, max(2, fw - 1)), rng.randrange(0, fh)   # never touching (no streaks)
                    near = [(a, b) for a in (x - 1, x, x + 1) for b in (y - 1, y, y + 1)
                            if 1 <= a < fw - 1 and 0 <= b < fh and (a, b) != (x, y)]
                    if paint[y][x] == 'B' and all((a, b) not in marked for a, b in near):
                        marked.add((x, y))
                        paint[y][x] = 'M' if rng.random() < 0.7 else 'L'
            if top: paint[0] = [top] * fw
            if bottom and fh > 1: paint[-1] = [bottom] * fw
            if top == 'H' and fw > 2: paint[0][0 if lit_left else -1] = 'H'
            if DETAIL: detail(name, face, paint, lit_left)
            if UPPER_DETAIL in ('inset', 'row', 'corners'):          # drawn across the whole upper piece
                upper_world.paint_face(name, cb, face, paint, lit_left, UPPER_DETAIL)
        for row_p, row_c in zip(g, paint):
            for p, k in zip(row_p, row_c):
                tex.putpixel(p, P[k] + (255,)); done.add(p)
        print(f'{name:16} {face:5} {fw}x{fh} lit {"left" if lit_left else "right"}')

px = [(x, y, *tex.getpixel((x, y))) for (x, y) in sorted(done)]
(D / f'plan_plate{"_" + DETAIL if DETAIL else ""}{"_" + UPPER_DETAIL if UPPER_DETAIL else ""}.json').write_text(json.dumps(dict(pixels=''.join('%02x' * 6 % p for p in px))))
print(len(px), 'pixels')
