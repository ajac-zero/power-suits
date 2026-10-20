"""Detail for the thigh's upper piece drawn in world space (2026-10-19, user on the per-block variants: "all of these are
too lopsided instead of incorporating the available space from thigh_top").

From the front (and the back) the upper piece is three faces: thigh_upper's flat block on the inner side (x 1..6.5, the
frontmost), the part of thigh_top that shows outside it (a wedge under its 25 deg slope, x 6.5..9.9) and the shoulder
slab above that (-58.7 deg). A design painted on thigh_upper alone sat on the inner 60 % of the piece. Here a design is
a shape in the leg's coordinates (x, y; the right leg, the left one is its mirror through the shared nets) and every
pixel of those faces is painted from where its centre lands on the piece, so the shape runs across the blocks.
  inset    a recessed panel following the piece's outline 1.1 px in (top under the belt, the slanted outer edge, the band
           at the bottom): shadow on its top and lit-side walls, light on the bottom and far walls
  row      a row of three studs across the full width, above the band's studs
  corners  four studs at the corners of that inset outline (a bolted plate)
Used by thighs_plate_art.py (third argument).
"""
import math
from shapely.geometry import Point, Polygon

TOP_BACK = 23.74                  # the hood's back plate hangs to here: keep the panel's top under it on both sides
OUTLINE = Polygon([(1, 18.63), (1, 24.28), (6.5, 24.28), (6.5, 25.0), (8.25, 22.125), (9.94, 18.5), (9.94, 18.63)])
INSET = OUTLINE.buffer(-1.1, join_style=2).intersection(Polygon([(0, 0), (0, TOP_BACK - 0.6), (12, TOP_BACK - 0.6),
                                                                 (12, 0)]))
FACES = ('thigh_upper', 'thigh_top', 'thigh_shoulder')


def world(cb, face, i, j, fw, fh):
    """Centre of pixel (i, j) (viewer's left to right, top to bottom) of a north / south face, in leg coordinates."""
    (x0, y0, _), (x1, y1, _) = cb['from'], cb['to']
    x = x1 - (i + 0.5) * (x1 - x0) / fw if face == 'north' else x0 + (i + 0.5) * (x1 - x0) / fw
    y = y1 - (j + 0.5) * (y1 - y0) / fh
    rz = math.radians(cb['rotation'][2]); ox, oy = cb['origin'][0], cb['origin'][1]
    c, s = math.cos(rz), math.sin(rz)
    return ox + (x - ox) * c - (y - oy) * s, oy + (x - ox) * s + (y - oy) * c


def owner(x, y):
    """Which block shows at (x, y) on the front / back (thigh_upper is frontmost, then thigh_top, then the shoulder)."""
    if 1 <= x <= 6.5 and 18.5 <= y <= 24.28: return 'thigh_upper'
    if Polygon([(1.0, 18.74), (8.25, 22.125), (9.94, 18.5), (2.69, 15.1)]).contains(Point(x, y)):
        return 'thigh_top'
    return 'thigh_shoulder'


def studs(kind):
    if kind == 'row':
        return [(2.4, 20.3), (5.1, 20.3), (7.8, 20.3)]
    if kind == 'corners':
        pts = list(INSET.exterior.coords)[:-1]          # its top and bottom corners (not the slope's kink)
        ys = [p[1] for p in pts]
        keep = [p for p in pts if abs(p[1] - max(ys)) < 1e-6 or abs(p[1] - min(ys)) < 1e-6]
        return [(x + (0.45 if x < 5 else -0.45), y + (0.45 if y < 21 else -0.45)) for x, y in keep]
    return []


def paint_face(name, cb, face, paint, lit_left, kind):
    """Paint the design into one face's grid (in place)."""
    if face not in ('north', 'south') or not name.startswith(FACES): return
    fh, fw = len(paint), len(paint[0])
    W = [[world(cb, face, i, j, fw, fh) for i in range(fw)] for j in range(fh)]
    me = next(k for k in FACES if name.startswith(k))
    if kind == 'inset':
        lit_dx = (1 if face == 'north' else -1) * (1 if lit_left else -1)   # world x direction of the lit side
        inside = lambda p: INSET.contains(Point(p))
        for j in range(fh):
            for i in range(fw):
                p = W[j][i]
                if not inside(p): continue
                paint[j][i] = 'B'
                for di, dj in ((0, -1), (0, 1), (-1, 0), (1, 0)):   # a wall where the next pixel is outside
                    a, b = i + di, j + dj
                    q = W[b][a] if 0 <= a < fw and 0 <= b < fh else world(cb, face, a, b, fw, fh)
                    if inside(q): continue
                    dx, dy = q[0] - p[0], q[1] - p[1]
                    if abs(dy) >= abs(dx): paint[j][i] = 'S' if dy > 0 else 'L'
                    else: paint[j][i] = 'S' if dx * lit_dx > 0 else 'L'
                    if paint[j][i] == 'S': break
    else:
        for sx, sy in studs(kind):
            if owner(sx, sy) != me: continue
            j, i = min(((j, i) for j in range(fh) for i in range(fw)), key=lambda t: math.dist(W[t[0]][t[1]], (sx, sy)))
            paint[j][i] = 'H'
            if j + 1 < fh: paint[j + 1][i] = 'S'
