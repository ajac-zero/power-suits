"""Flamethrower drafts (2026-10-23, Phase 11: more power tools). Hidden proto_flame_* groups under left_forearm, like the
other tools: a 7x1x7 slate base (x -15..-8, z -5..2, y 19.625..20.625, the socket's; 5x1x5, x -14..-9, z -4..1, since 2026-10-26) and the tool hanging from it down to
y ~10 (the muzzle points down at rest; the arm raises it when used). Back of the suit = +z. Simple boxes, detail painted.

  A tanks + wand   two upright fuel tanks on the outer half, a slim nozzle on the inner half with a flame cup
  B napalm cannon  one fat short barrel, flared muzzle, heat fins, a fuel drum riding on the back
  C torch lance    a long thin lance with collars, a wide ignition cup, a hose to a side drum

    uv run --with pillow python3 model/art/flame_drafts_art.py   (reads .scratch/flame/source.json from
    .agents/skills/blockbench-mcp/scripts/snapshot.mjs; writes .scratch/flame/plan.json; apply: .scratch/flame/apply.js)
"""
import json, sys
sys.path.insert(0, "model/art")
import backpack_kit
backpack_kit.GLOW_LIT.clear()   # no glow colours in these drafts (the engine packs own them)
from backpack_kit import on, build, FACES

SRC, OUT = ".scratch/flame/source.json", ".scratch/flame/plan.json"
ORIGIN = [-11.5, 25.625, -1.5]
BASE = 19.625   # underside of the slate base

STRAP = ['SSSSSS', 'GGGGGG']
def bands(rows):
    """Iron with steel bands at the given rows from the top (side faces)."""
    def p(w, h, f, t):
        g = [list(r) for r in on('iron')("east", w, h, f, t)]
        for r in rows:
            if r < h: g[r] = list('G' * w) if r % 2 == 0 else list('S' * w)
        return [''.join(r) for r in g]
    return p

TANK = on('iron', east=bands([1, 2, 7, 8]), west=bands([1, 2, 7, 8]), south=bands([1, 2, 7, 8]), north=bands([1, 2, 7, 8]))
WARM = on('steel', south=lambda w, h, f, t: ['y' * w] * h, north=lambda w, h, f, t: ['y' * w] * h,
          east=lambda w, h, f, t: ['y' * w] * h, west=lambda w, h, f, t: ['y' * w] * h, down=lambda w, h, f, t: ['Y' * w] * h)
FIN = on('iron')
STEEL = on('steel')
NET = on('nether')

A = {
    "pkFA_tank_a": ([-14.5, 13.625, -4.5], [-11.5, BASE, -1.5], TANK),
    "pkFA_tank_b": ([-14.5, 13.625, -1.5], [-11.5, BASE, 1.5], TANK),
    "pkFA_valve": ([-14, 12.625, -4], [-12, 13.625, -2], STEEL),
    "pkFA_wand": ([-11, 11.625, -2.5], [-9, BASE, -0.5], on('iron')),
    "pkFA_nozzle": ([-11.5, 9.625, -3], [-8.5, 11.625, 0], STEEL),
    "pkFA_pilot": ([-10.5, 8.625, -2], [-9.5, 9.625, -1], WARM),
}
B = {
    "pkFB_barrel": ([-14, 12.625, -4], [-9, BASE, 1], on('nether')),
    "pkFB_flare": ([-14.5, 10.625, -4.5], [-8.5, 12.625, 1.5], STEEL),
    "pkFB_lip": ([-14, 9.625, -4], [-9, 10.625, 1], on('iron')),
    "pkFB_fin_1": ([-14.5, 14.625, -3.5], [-8.5, 15.375, 0.5], FIN),
    "pkFB_fin_2": ([-14.5, 16.625, -3.5], [-8.5, 17.375, 0.5], FIN),
    "pkFB_drum": ([-13, 13.625, 1], [-10, 18.625, 4], TANK),
    "pkFB_pilot": ([-12, 8.625, -2], [-11, 9.625, -1], WARM),
}
C = {
    "pkFC_lance": ([-12.5, 9.625, -2.5], [-10.5, BASE, -0.5], on('steel')),
    "pkFC_collar_1": ([-13, 16.625, -3], [-10, 17.625, 0], on('iron')),
    "pkFC_collar_2": ([-13, 13.625, -3], [-10, 14.625, 0], on('iron')),
    "pkFC_cup": ([-13.5, 8.625, -3.5], [-9.5, 9.625, 0.5], on('nether')),
    "pkFC_pilot": ([-12, 7.625, -2], [-11, 8.625, -1], WARM),
    "pkFC_drum": ([-9.5, 13.625, -4], [-8, 18.625, 1], TANK),
    "pkFC_hose": ([-10.5, 18.625, -1.5], [-9.5, BASE, -0.5], on('nether')),
}
# round 3 (user: "the barrel with holes from Fallout's flamer, and the tank underneath"): a perforated shroud round a
# steel nozzle along the tool's axis, a red fuel tank "underneath" it. Raised, the tool's back face (+z) turns downward,
# so underneath = the +z side. Pilot light on a short prong at the muzzle (TF2). Red is outside the steel ramp the dyes
# swap, so paint doesn't touch it.
backpack_kit.PAL.update(E='5e1a14', F='8e2a1c', H='b5412a', J='d9603c', N='d9962a')


def holes(w, h, f, t):
    """Steel shroud face: a lit rim, rows of dark holes staggered, a dark band at the muzzle end."""
    g = [['S'] * w for _ in range(h)]
    g[0] = ['G'] * w
    for r in range(1, h - 1):
        if r % 2 == 1:
            for c in range(w):
                if (c + r // 2) % 2 == 0: g[r][c] = 'K'
    g[h - 1] = ['5'] * w
    return [''.join(r) for r in g]


def red_tank(w, h, f, t):
    """Fallout-style tank side: red body, a lit upper edge, shaded lower edge, steel strap rows and a hazard band."""
    g = [['F'] * w for _ in range(h)]
    for x in range(w):
        g[0][x] = 'J'; g[1][x] = 'H'; g[h - 1][x] = 'E'
        if h > 3: g[h - 2][x] = 'E' if x % 2 else 'F'
    for r in (2, h - 4):
        if 1 < r < h - 1:
            for x in range(w): g[r][x] = 'G'; g[r + 1][x] = 'S' if r + 1 < h - 1 else g[r + 1][x]
    for x in range(w):                       # hazard stripe just under the top strap: orange / black diagonals
        if h > 8: g[4][x] = 'N' if (x + 0) % 2 == 0 else 'K'; g[5][x] = 'K' if x % 2 == 0 else 'N'
    return [''.join(r) for r in g]


RED = on('iron', east=red_tank, west=red_tank, south=red_tank, north=red_tank,
         up=lambda w, h, f, t: ['H' * w] * h, down=lambda w, h, f, t: ['E' * w] * h)
SHROUD = on('steel', north=holes, south=holes, east=holes, west=holes)

# round 5 (user, on round 4: "looks dogshit, it needs to be cool"): holes in a box read as windows. A flamethrower's
# silhouette should come from open structure, like Fallout's cradle frame: the barrel is a CAGE (four corner posts, three
# steel bands, open gaps between them showing a glowing copper heating coil), a wide flared muzzle ring and a pilot prong
# that reaches past the nozzle (gold tip); under it a fat red tank with rounded steel end caps, strap cradles, a valve
# wheel and a hazard band. 13 px long (the others 9-10). Barrel axis x -11.5, z -2.5; tank behind it (+z).
backpack_kit.PAL.update(E='5e1a14', F='8e2a1c', H='b5412a', J='d9603c', N='d9962a', R='e8541c')


def holes(w, h, f, t): return ['S' * w] * h   # (unused since round 4)


def red_tank(w, h, f, t):
    """Fallout-style tank side: red body, lit upper edge, shaded lower edge, a hazard band across the middle."""
    g = [['F'] * w for _ in range(h)]
    for x in range(w):
        g[0][x] = 'J'; g[1][x] = 'H'; g[h - 1][x] = 'E'
        if h > 3: g[h - 2][x] = 'E' if x % 2 else 'F'
    if h > 6:
        m = h // 2
        for x in range(w):
            g[m - 1][x] = 'N' if x % 2 == 0 else 'K'; g[m][x] = 'K' if x % 2 == 0 else 'N'
    return [''.join(r) for r in g]


def coil(w, h, f, t):
    """The heating coil seen between the bands: copper windings, hot orange glints."""
    g = [[('C' if (y % 2 == 0) else 'c') for x in range(w)] for y in range(h)]
    for y in range(0, h, 2):
        for x in range(w):
            if (x + y // 2) % 3 == 0: g[y][x] = 'R'
    return [''.join(r) for r in g]


RED = on('iron', east=red_tank, west=red_tank, south=red_tank, north=red_tank,
         up=lambda w, h, f, t: ['H' * w] * h, down=lambda w, h, f, t: ['E' * w] * h)
COIL = on('iron', east=coil, west=coil, south=coil, north=coil)
BAND = on('steel')
POST = on('iron')
WHEEL = on('iron', south=['FJJF', 'JFFJ', 'FJJF'][:1] and (lambda w, h, f, t: ['N' * w] * h))
# round 6: back to the AGREED barrel (user: "that is not the agreed upon barrel design"; agreed = Fallout's perforated
# barrel, thicker, holes as real 3D openings, tank underneath). The cage was my own idea: dropped. Round 4's single pixel
# holes looked like windows, so: tall 1x2 slots in staggered bands through a 5x5 steel shroud, a glowing copper coil
# inside so each slot shows light, bold steel strips between the bands, a wide lip and a pilot prong at the muzzle.
SX0, SZ0, TOP = -14, -5, 17.625                   # shroud x -14..-9, z -5..0, 10 px tall (y 7.625..17.625)
# bands of rows (top = 0): strips on 0, 3, 6, 9; slot rows 1-2, 4-5, 7-8
PLAN = [((0, 0), []), ((1, 2), [1, 3]), ((3, 3), []), ((4, 5), [2]), ((6, 6), []), ((7, 8), [1, 3]), ((9, 9), [])]
PLAN_SIDE = [((0, 0), []), ((1, 2), [1]), ((3, 3), []), ((4, 5), [1]), ((6, 6), []), ((7, 8), [1]), ((9, 9), [])]


def pieces(width, holes):
    cols, c = [], 0
    while c < width:
        if c in holes: c += 1; continue
        e = c
        while e + 1 < width and (e + 1) not in holes: e += 1
        cols.append((c, e)); c = e + 1
    return cols


def walls():
    out = {}
    for side in ("n", "s", "e", "w"):
        ns = side in "ns"
        for (r0, r1), holes in (PLAN if ns else PLAN_SIDE):
            y0, y1 = TOP - (r1 + 1), TOP - r0
            for a, b in pieces(5 if ns else 3, holes):
                if ns:
                    z0 = SZ0 if side == "n" else SZ0 + 4
                    f, t = [SX0 + a, y0, z0], [SX0 + b + 1, y1, z0 + 1]
                else:
                    x0 = SX0 if side == "w" else SX0 + 4
                    f, t = [x0, y0, SZ0 + 1 + a], [x0 + 1, y1, SZ0 + 1 + b + 1]
                out[f"pkFE_wall_{side}{r0}{r1}_{a}"] = (f, t, on('steel'))
    return out


E_ = {
    **walls(),
    "pkFE_coil": ([-13, 7.625, -4], [-10, TOP, -1], COIL),               # the glow seen through the slots
    "pkFE_neck": ([-14, TOP, -5], [-9, BASE, 0], on('iron')),            # the shroud's collar up into the base
    "pkFE_flare": ([-14.5, 6.625, -5.5], [-8.5, 7.625, 0.5], on('nether')),   # wide muzzle lip, 6x1x6
    "pkFE_nozzle": ([-13, 5.625, -4], [-10, 6.625, -1], on('steel')),
    "pkFE_prong": ([-8.5, 5.625, -5.5], [-7.5, 16.625, -4.5], on('steel')),   # pilot prong up the side of the lip
    "pkFE_pilot": ([-8.5, 4.625, -5.5], [-7.5, 5.625, -4.5], WARM),
    # the tank underneath (+z): body, rounded steel caps, a valve wheel
    "pkFE_tank": ([-14, 11.125, 0], [-9, BASE, 3], RED),
    "pkFE_cap": ([-13.5, 10.125, 0.5], [-9.5, 11.125, 2.5], on('steel')),
    "pkFE_wheel": ([-12.5, 14.625, 3], [-10.5, 15.625, 3.75], WHEEL),
}
# round 7 (user: "improve the flamethrower design"). Round 6 kept the agreed parts (thick perforated barrel, real 3D
# holes, red tank underneath) but read as a box with windows: one 5x5 prism from base to muzzle, three slots a side, the
# tank glued flush to it, a thin prong up the side. What makes a flamer read is its SILHOUETTE: a fat perforated heat
# shroud, a narrower nozzle, a flared bell at the muzzle (5 -> 3 -> 7 px), and a tank slung under it on brackets with
# daylight between. Holes: a dense staggered perforation (8-9 per face) over a hot coil, so the barrel reads as a
# jacket, not a wall with windows. F = square sections, G = the same with the corners notched (plus-shaped sections
# read as round pipes). Axis x -11.5, z -2.5 like round 6; the tank's side is +z (underneath when raised).
backpack_kit.PAL.update(X='ffb347')                  # hot core / orifice glint
# pilot light glow (round 8): reserved dark blues (dead when the tool is off), lit like the EM tips by a glowmask
backpack_kit.PAL.update(u='1b2638', v='22304a')
backpack_kit.GLOW_LIT.update(u='2a6cff', v='8ec2ff')


def barrel_skin(style, x0, z0, y1):
    """The perforated barrel's outside, by position on the barrel (row r from the socket end, column c across the face:
    every wall cube is one column wide), so all four faces match (2026-10-25, user: the flat grey "feels flat").
      stripe  a lit stripe down the middle between the holes, a shadow row under the socket, soot at the muzzle end
      worn    mixed steel greys with a few glints, lighter at the socket, sooty toward the muzzle"""
    def skin(face, w, h, f, t):
        c = int(f[0] - x0) if face in ("north", "south") else int(f[2] - z0)
        rows = []
        for y in range(h):
            r = int(y1 - (t[1] - y - 0.5))
            if style == "stripe":
                k = 'S' if r == 0 else '5' if r >= 8 else ('G' if c == 1 else 'T' if r == 7 else 't')
            else:
                h_ = (r * 7 + c * 13 + {"north": 0, "south": 5, "east": 11, "west": 17}[face]) % 23
                k = ('G' if r <= 1 else 't') if h_ % 3 else 'T'
                if h_ in (4, 15): k = 'W'
                if r >= 7: k = '5' if (r == 8 or h_ % 2) else 'S'
            rows.append(k * w)
        return rows
    return skin


def lattice_paint(out, box=None, skin=None):
    """Perforated shroud strip: flat steel outside, darker steel elsewhere. With box (the jacket's outer x0, x1, y0, y1,
    z0, z1 and the wall's own span a0, a1; round 8, user: "make the inside of the holes black") a face that lies inside
    the jacket's outline (a hole's wall, roof or floor) is black; the jacket's two end faces and the notched corners'
    faces stay steel."""
    def p(face, w, h, f, t):
        if face == out:
            if skin: return [list(r) for r in skin(face, w, h, f, t)]
            k = 't'
        elif box is None: k = 'S' if face in ("up", "down") else '5'
        else:
            y0, y1, lo, hi = box
            if face == "up": k = 'S' if t[1] >= y1 else 'K'
            elif face == "down": k = 'S' if f[1] <= y0 else 'K'
            else:
                ax = 0 if face in ("east", "west") else 2     # the axis this face looks along
                at = t[ax] if face in ("east", "south") else f[ax]
                if (at <= lo or at >= hi) and skin:          # a wall's end on the barrel's corner: part of the side
                    return [list(r) for r in skin(face, w, h, f, t)]
                k = '5' if at <= lo or at >= hi else 'K'      # a wall's end (corner) or a hole's side
        return [[k] * w for _ in range(h)]
    return p


def hot_coil(w, h, f, t):
    g = [[('X' if y % 2 == 0 else 'R') for x in range(w)] for y in range(h)]
    for y in range(0, h, 2):
        for x in range(w):
            if (x + y // 2) % 3 == 0: g[y][x] = 'Y'
    return [''.join(r) for r in g]


def tank_side(w, h, f, t):
    """Round 7 tank: red body (lit top rows, shaded bottom), two steel straps instead of the loud hazard band."""
    g = [['F'] * w for _ in range(h)]
    for x in range(w):
        g[0][x] = 'H'; g[h - 1][x] = 'E'
        for r in (1, h - 2):                         # one-row steel straps near each end
            if 0 < r < h - 1: g[r][x] = 'S'
    return [''.join(r) for r in g]


TANK7 = on('iron', east=tank_side, west=tank_side, south=tank_side, north=tank_side,
           up=lambda w, h, f, t: ['H' * w] * h, down=lambda w, h, f, t: ['E' * w] * h)
HOT = on('iron', east=hot_coil, west=hot_coil, south=hot_coil, north=hot_coil)


def pattern_holes(pattern, side, width, r, L):
    """Hole columns (0..width-1) of a 3-wide notched face on row r (0 = the base end). Round 9 options:
      mid    one hole in the middle column, front/back faces on odd rows, sides on even rows (3 per face; "too few")
      swap   the round 8 density (rows of 2 edge holes / 1 middle hole) with neighbouring faces out of step, so a face's
             edge holes never sit on the same row as the next face's (5 / 4 per face)
      board  a full checkerboard per face, shifted one row on neighbouring faces (9 per face)"""
    ns = side in "ns"
    if pattern == "shift" and not ns and r == 0:
        # (user: "add more holes on the other side of the shift that is empty"): the shift left the side faces blank rows
        # at the base end; their pattern goes on there with a middle hole, under the neck
        return {width // 2}
    if r == 0 or r == L - 1:
        return set()
    if pattern == "mid":
        return checker_holes(side, width, r, L)
    if pattern == "shift":
        # (user, after mid/swap/board: "go back to the committed model and shift one side one block so the holes don't
        # line up"): round 8's pattern, rows of 2 edge holes / 1 middle hole; the left and right faces one row further
        # along, so their holes never share a row with the top and bottom faces' across a cut corner. (Tried after this
        # and dropped: every hole row a pair of edge holes, with or without middle holes between: "we overdid it".)
        rr = r if ns else r - 1
        if rr <= 0 or rr % 2 == 0 or r == L - 1:
            return set()
        return {0, width - 1} if (rr // 2) % 2 == 0 else {width // 2}
    if pattern == "swap":
        if r % 2 == 0:
            return set()
        edge = (r // 2) % 2 == 0
        return {0, width - 1} if edge == ns else {width // 2}
    if pattern == "board":
        return {0, width - 1} if (r % 2 == 1) == ns else {width // 2}
    raise ValueError(pattern)


def checker_holes(side, width, r, L):
    """Round 9 (user: "a more checkered pattern so that the holes on two sides aren't adjacent and form a bigger hole"):
    a face's edge columns sit next to the notched corner, so a hole there joined the notch and the next face's hole into
    one gap. Holes only in each face's middle column, the front/back faces on odd rows and the sides on even rows: a
    checker round the barrel, every hole walled in on all four sides."""
    if r == 0 or r == L - 1:
        return set()
    ns = side in "ns"
    return {width // 2} if (r % 2 == 1) == ns else set()


def shroud(pre, x0, x1, z0, z1, y0, y1, notch, black=False, checker=False, pattern=None, skin=None):
    """A perforated jacket: four walls built column by column (a column is solid except at its holes), corners left
    out when notched. Rows from the top (base end): odd rows hold holes, staggered (1,3 / 2) on the 5-wide faces."""
    L = int(y1 - y0)
    def holes(width, r):
        if r % 2 == 0 or r == L - 1: return set()
        if width >= 5: return {1, 3} if (r // 2) % 2 == 0 else {2}
        return {1}
    out = {}
    walls = {"n": (x0 + notch, x1 - notch, "north"), "s": (x0 + notch, x1 - notch, "south"),
             "w": (z0 + 1, z1 - 1, "west"), "e": (z0 + 1, z1 - 1, "east")}
    for side, (a0, a1, face) in walls.items():
        width = int(a1 - a0)
        full = int(x1 - x0) if side in "ns" else int(z1 - z0)
        off = (full - width) // 2            # keep the stagger centred on the full face
        for c in range(width):
            runs, start = [], None
            for r in range(L + 1):
                solid = r < L and (c not in pattern_holes(pattern, side, width, r, L) if pattern
                                   else c not in checker_holes(side, width, r, L) if checker else (c + off) not in holes(full, r))
                if solid and start is None: start = r
                if not solid and start is not None: runs.append((start, r)); start = None
            for ra, rb in runs:
                ya, yb = y1 - rb, y1 - ra
                if side in "ns":
                    zz = (z0, z0 + 1) if side == "n" else (z1 - 1, z1)
                    f, t = [a0 + c, ya, zz[0]], [a0 + c + 1, yb, zz[1]]
                else:
                    xx = (x0, x0 + 1) if side == "w" else (x1 - 1, x1)
                    f, t = [xx[0], ya, a0 + c], [xx[1], yb, a0 + c + 1]
                out[f"{pre}_sh_{side}{c}_{ra}"] = (f, t, lattice_paint(face, (y0, y1, a0, a1) if black else None,
                                                                     skin and skin(x0, z0, y1)))
    return out


def ring(pre, x0, x1, z0, z1, y0, y1, notch, paint):
    """A square (or corner-notched) frame one px thick: the muzzle bell's steps."""
    return {f"{pre}_n": ([x0 + notch, y0, z0], [x1 - notch, y1, z0 + 1], paint),
            f"{pre}_s": ([x0 + notch, y0, z1 - 1], [x1 - notch, y1, z1], paint),
            f"{pre}_w": ([x0, y0, z0 + 1], [x0 + 1, y1, z1 - 1], paint),
            f"{pre}_e": ([x1 - 1, y0, z0 + 1], [x1, y1, z1 - 1], paint)}


def flamer(pre, notch):
    d = {}
    d[f"{pre}_base"] = ([-15, BASE, -5], [-8, BASE + 1, 2], on('iron'))           # UV swapped for the drill's on apply
    d[f"{pre}_neck"] = ([-14, 17.625, -5], [-9, BASE, 0], on('iron'))             # breech under the socket
    d.update(shroud(pre, -14, -9, -5, 0, 9.625, 17.625, notch))                   # 5x8x5 jacket
    d[f"{pre}_coil"] = ([-13, 9.625, -4], [-10, 17.625, -1], HOT)                 # glows through every hole
    d[f"{pre}_nozzle"] = ([-13, 5.625, -4], [-10, 9.625, -1],                      # 3x3 nozzle, orifice at its tip
                          on('steel', down=['KKK', 'KXK', 'KKK']))
    d.update(ring(f"{pre}_bell_in", -14, -9, -5, 0, 5.625, 6.625, notch, on('steel')))
    d.update(ring(f"{pre}_bell", -15, -8, -6, 1, 4.625, 5.625, notch, on('nether')))   # 7 px flared lip
    # tank slung underneath (+z) with a 1 px gap, held by two brackets
    if notch:
        d[f"{pre}_tank"] = ([-13.5, 11.125, 2], [-9.5, 17.625, 4], TANK7)
        d[f"{pre}_tank_core"] = ([-12.5, 10.625, 1], [-10.5, 18.125, 5], TANK7)    # longer: its ends are the caps
    else:
        d[f"{pre}_tank"] = ([-13.5, 11.125, 1], [-9.5, 17.625, 5], TANK7)
        d[f"{pre}_cap_f"] = ([-12.5, 10.125, 2], [-10.5, 11.125, 4], on('steel'))
        d[f"{pre}_cap_r"] = ([-12.5, 17.625, 2], [-10.5, 18.625, 4], on('steel'))
    d[f"{pre}_brk_r"] = ([-12.5, 15.625, 0], [-10.5, 16.625, 1], on('iron'))
    d[f"{pre}_brk_f"] = ([-12.5, 11.625, 0], [-10.5, 12.625, 1], on('iron'))
    d[f"{pre}_valve"] = ([-14.5, 13.625, 2.5], [-13.5, 15.625, 3.5], on('gold'))   # valve knob on the outer side
    # pilot light under the bell (TF2): a short steel rod from the tank to the lip, a pale blue flame tip
    d[f"{pre}_pilot_rod"] = ([-12, 5.625, 1], [-11, 10.625, 2], on('steel'))
    d[f"{pre}_pilot"] = ([-12, 4.625, 1], [-11, 5.625, 2], on('steel', **{k: ['h'] for k in ("north", "south", "east", "west")}, down=['h']))
    return d


# round 8 (user: "the tank from the first one [round 6] but the barrel from the last one [G]", and the tip "small and
# concentrated, not this wide disk"): G's notched perforated barrel, round 6's red tank flush underneath (hazard band,
# steel cap, valve wheel), and no bell: the 3x3 nozzle narrows through a cross-shaped step to a 1x1 tip with the hot
# orifice. The pilot light sits beside the tip, on the tank side.
def flamer_h(pre, pattern="shift"):
    """The flamethrower. Round 10 (user: "much bigger than the other power tools": 15.5 px long, volume 397 against the
    drill's 11 px / 226): neck 1, holed barrel 6 rows, cross step 1; the tank 2 px shorter. Then (user: "remove the flame
    nozzle and leave just the flame tapers") the 3x3 nozzle went: the cross step sits on the barrel's end, 9 px below the
    socket base, and the pilot light ends 1 px past it, 10 px from the socket: the chaingun's length. Last ("remove the
    taper altogether, instead painting it on"): the tip is painted on the barrel's end face, 8 px below the socket base;
    the pilot light ends 1 px past it. Then the barrel 2 rows longer again for one more set of holes at the end: the
    barrel ends 10 px below the socket base (the chaingun's muzzle), the pilot light 11 (the drill's length)."""
    d = {}
    d[f"{pre}_base"] = ([-15, BASE, -5], [-8, BASE + 1, 2], on('iron'))           # UV swapped for the drill's on apply
    d[f"{pre}_neck"] = ([-14, 18.625, -5], [-9, BASE, 0], on('iron'))
    # 8 rows (user: "one more set of holes at the end there"; notches in a 6-row barrel's last row instead: no, "go back
    # to the longer barrel")
    d.update(shroud(pre, -14, -9, -5, 0, 10.625, 18.625, 1, black=True, pattern=pattern))
    # the black core inside; its end face carries the painted tip (user: "remove the taper altogether, instead painting it
    # on"): a light steel plus with the black exit hole in its centre, dark corners
    d[f"{pre}_core"] = ([-13, 10.625, -4], [-10, 18.625, -1], on('iron', **{k: lambda w, h, f, t: ['K' * w] * h
                                                                          for k in ("north", "south", "east", "west")},
                                                                    down=['DGD', 'GKG', 'DGD']))
    # round 6's tank, 2 px shorter
    d[f"{pre}_tank"] = ([-14, 13.125, 0], [-9, BASE, 3], RED)
    d[f"{pre}_cap"] = ([-13.5, 12.125, 0.5], [-9.5, 13.125, 2.5], on('steel'))
    d[f"{pre}_wheel"] = ([-12.5, 15.625, 3], [-10.5, 16.625, 3.75], WHEEL)
    # pilot light: dark iron, down from the tank's cap to 1 px past the barrel's end, straight in under the exit hole;
    # its tip glows like the EM projector's prong tips (reserved dark blues, lit by a glowmask in game)
    d[f"{pre}_pilot_rod"] = ([-12, 9.625, 1], [-11, 12.125, 2], on('iron'))
    d[f"{pre}_pilot_arm"] = ([-12, 9.625, -1], [-11, 10.625, 1], on('iron'))
    d[f"{pre}_pilot_tip"] = ([-12, 9.625, -2], [-11, 10.625, -1],
                             on('iron', **{k: ['v'] for k in ("north", "east", "west")}, up=['u'], down=['v'], south=['u']))
    return d


def socket_base(donor="drill_base"):
    """The standard tool socket every power tool's base wears (7x1x7, 5x1x5 since 2026-10-26) (slate sides, a two-tone underside, a steel top with
    cyan contacts): a painter copying the donor base's faces pixel for pixel from the live texture (each tool owns a copy
    of it; user, 2026-10-25: the flamethrower's plain iron base broke the set)."""
    import base64, io
    from PIL import Image
    src = json.load(open(SRC))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    faces = next(e for e in src["model"]["elements"] if e["name"] == donor)["faces"]
    keys = {v: k for k, v in backpack_kit.PAL.items()}
    spare = iter("0123456789!#$%&*+=?@^~")

    def key(rgb):
        if rgb not in keys:
            k = next(c for c in spare if c not in backpack_kit.PAL)
            backpack_kit.PAL[k] = rgb; keys[rgb] = k
        return keys[rgb]

    def paint(face, w, h, f, t):
        u0, v0, u1, v1 = faces[face]["uv"]          # a reversed pair = a mirrored face: sample it the same way
        return [''.join(key('%02x%02x%02x' % tex.getpixel((int(u0 + (x + 0.5) * (u1 - u0) / w),
                                                             int(v0 + (y + 0.5) * (v1 - v0) / h)))[:3])
                        for x in range(w)) for y in range(h)]
    return paint


def flamer_slim(pre, glow=True, skin=None):
    """Round 11, the game's flamethrower since 2026-10-25 (user: "make the barrel 3x3 and the tank 3 wide, so both fit in
    the flame neck"; picked over the 5x5 one): a 3x3 holed barrel round a 1x1 black core, holes in each face's middle
    column, staggered round the barrel (front/back on odd rows, sides on even), and the tank 3 wide x 2 deep right behind
    it; cap and pilot light scaled to match. Its barrel's centre is 1 px in front of the socket's axis
    (FlamethrowerItem.barrelForwardPixels)."""
    # (2026-10-25, user after seeing it in Blockbench: no valve wheel; no neck: the barrel and tank take its pixel, up to
    # the base's underside, and the pair is centred under the 7x7 base, x -13..-10, z -4..1)
    d = {}
    d[f"{pre}_base"] = ([-14, BASE, -4], [-9, BASE + 1, 1], socket_base())   # the standard socket, like every tool (5x5 since 2026-10-26)
    d.update(shroud(pre, -13, -10, -4, -1, 10.625, BASE, 0, black=True, checker=True,
                    skin=skin and (lambda x0, z0, y1: barrel_skin(skin, x0, z0, y1))))
    d[f"{pre}_core"] = ([-12, 10.625, -3], [-11, BASE, -2], on('iron', **{k: lambda w, h, f, t: ['K' * w] * h
                                                                         for k in ("north", "south", "east", "west", "down")}))
    d[f"{pre}_tank"] = ([-13, 13.125, -1], [-10, BASE, 1], RED)
    d[f"{pre}_cap"] = ([-12.5, 12.125, -1], [-10.5, 13.125, 1], on('steel'))
    d[f"{pre}_pilot_rod"] = ([-12, 9.625, 0], [-11, 12.125, 1], on('iron'))
    d[f"{pre}_pilot_arm"] = ([-12, 9.625, -1], [-11, 10.625, 0], on('iron'))
    # the tip glows (reserved dark blues u / v, lit by power_suit_glow_pilot.png); a draft built next to a glowing
    # flamethrower can't use them (the kit refuses reserved colours already in the texture): glow=False paints a look-alike
    u, v = ('u', 'v') if glow else ('w', 'w')
    d[f"{pre}_pilot_tip"] = ([-12, 9.625, -2], [-11, 10.625, -1],
                             on('iron', **{k: [v] for k in ("north", "east", "west")}, up=[u], down=[v], south=[u]))
    return d


backpack_kit.PAL.update(w='3a6ad0')
# The game's flamethrower bone (cubes flame_*): rebuilding it in place keeps the bone (only its cubes are replaced; no
# animation keys them). Draft H (5x5 barrel, flamer_h) was the game's from 76b3cf6 to round 11; E and G are in git history.
# the barrel's outside: "worn" (user's pick, 2026-10-25); skin=stripe was the other option, no skin = flat grey
SKIN = next((a.split("=")[1] for a in sys.argv[1:] if a.startswith("skin=")), "worn")
DRAFTS = {"flamethrower": flamer_slim("flame", skin=SKIN)}

if __name__ == "__main__":
    plan = build(SRC, DRAFTS, "left_forearm", ORIGIN, "before flamethrower rebuild")
    # free the texels of the cubes it replaces (any cube now in the bone; none share texels with another bone) that the
    # new paint doesn't cover: left painted, reserved glow colours on them would count in make-glowmasks
    from backpack_kit import used_mask
    model = json.load(open(SRC))["model"]
    gs = {g["uuid"]: g for g in model["groups"]}
    def uuids(node, inside):
        out = set()
        for c in node:
            if isinstance(c, str):
                if inside: out.add(c)
            else:
                out |= uuids(c.get("children", []), inside or gs[c["uuid"]]["name"] in DRAFTS)
        return out
    old = uuids(model["outliner"], False)
    import base64, io
    from PIL import Image
    size = Image.open(io.BytesIO(base64.b64decode(json.load(open(SRC))["texture"].split(",", 1)[1]))).size
    mask = lambda els: used_mask(dict(model, elements=els), Image.new("RGBA", size, (0, 0, 0, 0)))
    o, k = mask([e for e in model["elements"] if e["uuid"] in old]), mask([e for e in model["elements"] if e["uuid"] not in old])
    new = {(x, y) for x, y, _ in plan["pixels"]}
    plan["clear"] = [[x, y] for y, row in enumerate(o) for x, v in enumerate(row) if v and not k[y][x] and (x, y) not in new]
    json.dump(plan, open(OUT, "w"))
    print("cubes", sum(len(c) for c in DRAFTS.values()), "pixels", len(plan["pixels"]))
