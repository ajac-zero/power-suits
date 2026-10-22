"""Helmet drafts, round 2 (2026-10-20, user: "more drafts, but keep the dimensions the same this time").

(Round 1, five sketches of other sizes, was dropped by the user on 2026-10-21; the helpers it shared live here now.)
Every draft is the current helmet's envelope: the shell 9 x 9 x 9 (x +-4.5, y 38.95..47.5, z -4..5) and front pieces
inside the current band's reach (z -4.75, x +-4.75, top 47.75). The shell's sides, back and top reuse the current
helmet's UV (same paint) where a draft leaves them alone. Drafted as hidden groups under `head` named proto_helm_*; since
2026-10-22 the four kept ones are the helmet styles (design sec. 4c), bones helm_protector (F), helm_spartan (G),
helm_noble (H) and helm_robot (K), still hidden in Blockbench (GeckoLib draws them anyway: the renderer shows the one the
helmet's style asks for).

  F delta      (after Subject Delta, BioShock 2) a glowing visor painted flat in a riveted frame, three half-pixel
               bars raised in front of it, riveted seams, a side port and a hatch on top (v1: a raised porthole; v2:
               glowing eyes)
  G chief      (after Halo's Master Chief) a 1 px top band wrapping round the front and sides, the visor glass
               painted flat on the shell under it, round the sides too, its lower edge stepping up round the corners;
               a narrower mouthpiece with a ridge (v1 goggles: round lenses + gas-mask
               filter; v2: a raised gold visor, dropped: the visor glows yellow already)
  H eva        (after Emile's EVA helmet in Halo Reach) the visor painted flat over the whole front of the head (front,
               the front of the sides, the front half of the top); a steel band round the lower half with red ear
               marks, a mouthpiece (v1: a T-visor faceplate; v2-v3: a tiered dome, dropped: same shape as the others)
  K guy        (after Guy-Manuel's Daft Punk helmet) one big dark visor painted flat (it glows), set in a gold U-frame
               (chin bar + side posts), round ear discs painted on (v1 split: the current helmet with a centre bar)
The user reworked F, G, H and K one by one after a reference each (flat painted visors that glow, no grills, the
current helmet's shape); the unreviewed I (slit), J (wrap) and L (bars) were dropped before merging (2026-10-21; in
git history at 5b0a5fa).

    uv run --with pillow python3 model/art/helmet_drafts2_art.py   (reads .scratch/helmets/source2.json, a fresh
    snapshot of the live project; writes .scratch/helmets/plan2.json; apply: .scratch/helmets/apply.js {plan})
    ... helmet_drafts2_art.py spartan   rebuilds only the drafts whose group name contains spartan (the others stay as they are)
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, plate, C, face_dims

SRC, OUT = ".scratch/helmets/source2.json", ".scratch/helmets/plan2.json"
# the suit's steel ramp under the blue keys sm_chest_art paints with; reserved visor-glass colours (they glow)
STEEL = dict(G='22262e', D='3c424c', S='58606a', B='757c85', M='8a9097', L='9fa4a9', H='cdcfcf', H2='fbf8f0')
C = dict(C, **STEEL, GM='89939a', VG='262c34', VE='1e232a', VH='46505c', RD='c23b22')  # RD: H's red ear marks
FACES = ("north", "east", "south", "west", "up", "down")


def shell(face, w, h):
    """The suit's armour plate (lit top / left, shaded right / bottom); dark open-looking underside."""
    if face == "down": return grid(w, h, 'I1')
    if face == "up":
        g = plate(w, h, top='L', left='L', right='S', bottom='S', base='B', marks=('M', 'M', 'S'))
        g[0][0] = 'H2'
        return g
    lit = face in ("east", "north")
    return plate(w, h, top='H', left='L' if lit else 'B', right='B' if lit else 'S', bottom='S', base='B',
                 marks=('M', 'S'))


def trim(face, w, h):
    """Steel trim (bands, rims, ridges): lit top, shaded bottom."""
    if face == "up": return grid(w, h, 'W')
    if face == "down": return grid(w, h, 'G2')
    g = grid(w, h, 'GR' if face != "west" else 'GM')
    g[0] = ['W'] * w
    if h > 1: g[h - 1] = ['G2'] * w
    return g


_P = {'.': 'B', 'o': 'H', 'W': 'W', 'R': 'GR', 'r': 'G2', 'g': 'VG', 'h': 'VH', 'e': 'VE'}
SHELL = ([-4.5, 38.95, -4], [4.5, 47.5, 5])
REUSE = "reuse"            # painter result: take the named current cube's face UV


# name -> (from, to, painter); painter(face, w, h) returns a grid or (REUSE, cube name)
# ---------- F: Subject Delta (BioShock 2), on the standard shell ----------
# v2 (user: keep the diver theme but "more Subject Delta inspired"; v1 was a raised 7 x 7 porthole), v3 (user: the
# glowing eye portholes dropped, the main window is the visor and glows, with more detail; flat, its bars raised):
#   - the visor painted flat in the glow colours, a rounded window in a riveted frame: shadow under the frame's top,
#     a reflection streak
#   - two bars standing 0.75 px out in front of it (the reference's window bars), anchored in the frame
#   - a riveted seam down the chin, a riveted seam on each side near the front, a small dark side port, a round hatch
#     on the top, a riveted seam across the back (the in-game view)
# Frames, rivets and bars are in the armour's own shades: they take the paint (brown: Delta's brass).
def delta_pat(rows):
    """Pattern rows -> keys: '.' = leave the shell, H/M/S/B/L/D armour shades, h/g/e glass (glows), 0/1 dark iron."""
    k = {'h': 'VH', 'g': 'VG', 'e': 'VE', '0': 'I0', '1': 'I1', '2': 'H2'}
    return [[None if ch == '.' else k.get(ch, ch) for ch in r] for r in rows]


# 9 x 7 over the face's rows 0-6 (y 40.5..47.5): a solid frame (lit top and left, shaded right and bottom) with a
# rivet (H2) at each corner and halfway down each side, the glass rows 1-5 (y 41.5..46.5) its corners rounded off by
# the frame: shadow under the frame's top, a reflection streak, darker bottom row. The bars stand over columns 3 and
# 5 (x 0.5..1.5, -1.5..-0.5), since v4 three over columns 2, 4, 6. (v3a had rivets all along the frame and bar
# shadows on the glass: it read as a cage.)
DELTA_VISOR = delta_pat((".2HHHHH2.",
                         "LMeeeeeMS",
                         "LhhgggggS",
                         "2hgggggg2",
                         "LgggggggS",
                         "LMeeeeeMS",
                         ".2SSSSS2."))
DELTA_BAR_W = 0.5    # bar width (px): half-pixel bars leave ~1.25 px panes (user's pick; 1 px bars left 1 px slits)
DELTA_PORT = delta_pat((".HM.", "H01S", "M00S", ".SS."))
DELTA_HATCH = delta_pat((".HHM.", "HMMMS", "HMDMS", "MMMSS", ".SSS."))


def stamp(g, pat, r0, c0):
    for r, row in enumerate(pat):
        for c, k in enumerate(row):
            if k and 0 <= r0 + r < len(g) and 0 <= c0 + c < len(g[0]): g[r0 + r][c0 + c] = k


def delta(face, w, h):
    if face == "down": return (REUSE, "helmet_shell")
    # plain plates (no wear marks: the rivets and seams are the detail here)
    if face == "up": g = plate(w, h, top='L', left='L', right='S', bottom='S', base='B', n=0)
    else:
        lit = face in ("east", "north")
        g = plate(w, h, top='H', left='L' if lit else 'B', right='B' if lit else 'S', bottom='S', base='B', n=0)
    if face == "north":                                        # 9 x 9, row r = y 47.5-r
        stamp(g, DELTA_VISOR, 0, 0)
        for r in range(7, h): g[r][4] = 'H' if r % 2 else 'S'  # the riveted seam down the chin
    elif face in ("east", "west"):
        fc = w - 2 if face == "east" else 1                    # one column in from the front edge
        for r in range(1, h - 1): g[r][fc] = 'H' if r % 2 else 'S'
        stamp(g, DELTA_PORT, 1, 3 if face == "east" else 2)
    elif face == "up":
        stamp(g, DELTA_HATCH, 2, 2)
    elif face == "south":
        for c in range(w): g[4][c] = 'H' if c % 2 else 'S'
        g[5] = ['L'] * w
    return g


def delta_bar(f, w, h):
    """F's window bars: armour shades (they take the paint), lit top and left side."""
    if f == "up": return grid(w, h, 'H')
    if f == "down": return grid(w, h, 'S')
    if f == "north": return [['H']] + [['M']] * (h - 2) + [['S']]
    return grid(w, h, 'L' if f == "east" else 'S')


# ---------- K: Guy-Manuel's helmet (Daft Punk), on the standard shell ----------
# v2 (user: the yellow one after Guy-Manuel de Homem-Christo's gold helmet; v1 was the current helmet with a centre
# bar): a gold U-frame round one big visor painted flat, set back in the frame,
#   - the visor over the whole face and the front of the top in the dim glow colour (dark glass that lights up), its
#     top edge curving back over the head, a soft reflection (v2b: an LED display of stacked bars each side, the only
#     part lit: "they look like teeth"; v2c: the visor glowing dimly, picked over one LED line that read as a slit)
#   - the frame: a chin bar and two side posts standing out round the visor (0.7-0.75 px), wrapping the corners
#   - a round ear disc painted on each side over a panel (v2: 3D discs 0.25 px out; user: painted)
# Shell, frame and discs are in the armour's shades (they take the paint: yellow = gold); the glass doesn't.
def guy_plate(f, w, h):
    """K's frame pieces and ear discs: plain armour plate, lit top and left."""
    if f == "up": return grid(w, h, 'H')
    if f == "down": return grid(w, h, 'S')
    return plate(w, h, top='H', left='L', right='S', bottom='S', base='M', n=0)


# v2d (user): the ear discs painted, not 3D: a 4 x 4 disc outlined dark (shading alone read as a diagonal smear)
GUY_EAR = delta_pat((".DD.", "DHMD", "DMSD", ".DD."))


def guy_shell(f, w, h):
    if f == "down": return (REUSE, "helmet_shell")
    lit = f in ("east", "north")
    g = plate(w, h, top='H', left='L' if lit else 'B', right='B' if lit else 'S', bottom='S', base='B', n=0)
    if f == "up": g = plate(w, h, top='L', left='L', right='S', bottom='S', base='B', n=0)
    # the visor in the dim glow colour, the reflection in the bright one (v2b: black with LED bars, read as teeth;
    # v2c: black with one LED line, read as a visor slit)
    if f == "north":                                           # 9 x 9: the glass above the chin bar (rows 0-6)
        for r in range(7):
            for c in range(w): g[r][c] = 'VE'
        for r, c in ((0, 4), (1, 3), (2, 3), (0, 5)): g[r][c] = 'VH'
    elif f == "up":                                            # the visor over the front of the top, curving back
        reach = {1: 2, 2: 3, 3: 4, 4: 4, 5: 4, 6: 3, 7: 2}      # column -> last glass row (row 0 = front)
        for c, last in reach.items():
            for r in range(last + 1): g[r][c] = 'VE'
        g[0][3] = g[0][4] = g[1][4] = 'VH'
    elif f in ("east", "west"):                                # the ear disc (z -1.5..2.5) over a panel
        c0 = 2 if f == "east" else 3
        stamp(g, GUY_EAR if f == "east" else [r[::-1] for r in GUY_EAR], 2, c0)
        for c in range(c0, c0 + 4):
            g[6][c] = 'S'; g[7][c] = 'D'
    return g


# ---------- H: Emile's EVA helmet (Halo Reach), on the standard shell ----------
# v5 (user: "more dynamic and interesting", then "I will let you cook"): the right angles of v4 (a visor cut straight
# at the head's middle over a level ring) swapped for the reference's sweeps, keeping the shape:
#   - the visor a bubble: its back edge curves (furthest back at eye level, coming forward towards the top and the
#     band), its top edge rounds over the forehead; painted, so it steps in pixels like G's
#   - a dark rim round the glass where it meets the armour (a fitted window, not the head changing colour)
#   - a reflection streak across the glass (brighter again when lit: the mask's highlight colour)
#   - the band sweeps: low under the chin (a V where the mouthpiece sits), stepping up twice towards the back of each
#     side; three short red bars low on the cheeks (the reference's slashes)
#   - seams on the back half (a centre seam over the top, a plate tuck across the back) for the in-game view
def face_point(face, f, t, c, r):
    """World point (x, y, z) at the centre of texel (c, r) of a face whose UV maps 1:1 from its top left (as packed
    here): north: left = +x; south: left = -x; east: left = back; west: left = front; up: row 0 = front, left = +x."""
    if face == "north": return t[0] - c - .5, t[1] - r - .5, f[2]
    if face == "south": return f[0] + c + .5, t[1] - r - .5, t[2]
    if face == "east": return t[0], t[1] - r - .5, t[2] - c - .5
    if face == "west": return f[0], t[1] - r - .5, f[2] + c + .5
    if face == "up": return t[0] - c - .5, t[1], f[2] + r + .5
    return t[0] - c - .5, f[1], f[2] + r + .5


def eva_band_top(x, z):
    """Top of the band under a point of the shell (the visor starts above it)."""
    if z < -1: return 40.5 if abs(x) < 1.5 else 41.5
    return 42.5 if z < 1.5 else 43.5


def eva_glass(face, x, y, z):
    if face == "north": return y > eva_band_top(x, z)
    if face in ("east", "west"):
        return y > eva_band_top(x, z) and z < 1.0 - 1.5 * ((y - 44.5) / 3.0) ** 2
    if face == "up": return z < 1.0 - 1.5 * (x / 4.5) ** 2
    return False


def eva(f, t):
    """H's shell: the suit's plate (takes the paint) with the visor painted flat in the glow colours."""
    def p(face, w, h):
        g = shell(face, w, h)
        if face == "down": return g
        pts = [[face_point(face, f, t, c, r) for c in range(w)] for r in range(h)]
        glass = [[eva_glass(face, *pts[r][c]) for c in range(w)] for r in range(h)]
        near = lambda r, c: any(0 <= r + dr < h and 0 <= c + dc < w and glass[r + dr][c + dc]
                                for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for r in range(h):
            for c in range(w):
                if glass[r][c]: g[r][c] = 'VG'
                elif near(r, c) and pts[r][c][1] > eva_band_top(pts[r][c][0], pts[r][c][2]): g[r][c] = 'I2'   # rim
        if face == "north":                                    # the reflection: a diagonal streak, top left
            for r, c in ((0, 3), (1, 2), (2, 1), (3, 0), (0, 5), (1, 4)):     # a long and a short diagonal
                if glass[r][c]: g[r][c] = 'VH'
            for c in range(w):                                 # in the band's shadow: the glass's lowest row
                rows = [r for r in range(h) if glass[r][c]]
                if rows: g[rows[-1]][c] = 'VE'
        if face == "up":                                       # the streak carries over the top, lit edge up front
            for r, c in ((0, 2), (0, 3), (1, 2)):
                if glass[r][c]: g[r][c] = 'VH'
            m = w // 2
            for r in range(h):                                 # a centre seam over the back half
                if not glass[r][m] and g[r][m] != 'I2': g[r][m] = 'S'
        if face == "south":                                    # a plate tuck across the back
            g[3] = ['S'] * w; g[4] = ['L'] * w
            g[3][0] = g[4][0] = 'B'
        return g
    return p


SLASHES = [(0, 0), (1, 0), (0, 2), (1, 2), (0, 4), (1, 4)]     # three red bars, 2 px tall (as "///" read as a checkerboard)


def eva_band(f, w, h):
    """H's band pieces: bare steel trim; the low ring carries red slashes on the cheeks."""
    return trim(f, w, h)


def eva_ring(f, w, h):
    g = trim(f, w, h)
    if f in ("east", "west") and h >= 2 and w >= 8:
        start = 1 if f == "west" else w - 6                    # towards the front: west's first columns, east's last
        for r, c in SLASHES: g[r][start + c] = 'RD'
    return g


def eva_mouth(f, w, h):
    """H's mouthpiece: a steel block standing out from the band's V, a lit ridge down its middle."""
    g = trim(f, w, h)
    if f == "north":
        m = w // 2
        for y in range(1, h): g[y][m] = 'W'
    return g


def eva_cubes():
    """The standard 9 x 9 x 9 shell (back and underside painted here too: the seams) and a band in steps. Band pieces
    sink 0.1 into the one below and step 0.05 in at the sides / front / back, so no two faces share a plane."""
    f, t = SHELL
    out = {"hH_shell": (f, t, lambda face, w, h: (REUSE, "helmet_shell") if face == "down" else eva(f, t)(face, w, h))}
    out["hH_ring"] = ([-4.75, 38.9, -4.5], [4.75, 40.5, 3.5], eva_ring)            # low ring, front and sides
    for k, sx in (("r", 1), ("l", -1)):
        lo, hi = sorted((1.5 * sx, 4.7 * sx))
        out[f"hH_cheek_{k}"] = ([lo, 40.4, -4.45], [hi, 41.5, 3.45], eva_band)      # up to the visor, beside the V
        lo, hi = sorted((2 * sx, 4.65 * sx))
        out[f"hH_step1_{k}"] = ([lo, 41.4, -1], [hi, 42.5, 3.4], eva_band)          # rising behind the eyes
        lo, hi = sorted((2 * sx, 4.6 * sx))
        out[f"hH_step2_{k}"] = ([lo, 42.4, 1.5], [hi, 43.5, 3.35], eva_band)        # and again at the back
    out["hH_mouth"] = ([-1.5, 38.7, -4.75], [1.5, 40.75, -4.5], eva_mouth)
    return out


def chief_shell(f, w, h):
    """G's shell (v7, user: the visor flat, not a 3D piece): the visor glass painted straight onto the shell's front and
    sides, flush, under the 1 px wrap-around top. Faces map 1:1 from the top (row r = y 47.5-r .. 46.5-r): the top
    covers row 2, the glass rows 3-5 (y 41.5..44.5; the mouthpiece hides the bottom of the middle). One staircase round
    the lower edge: 3 rows across the middle of the front, 2 at its ends and at the front of the sides, 1 at the back of
    the sides (2 px round each corner). Glass in the glow colours (it lights up); the back, top and underside are the
    current helmet's."""
    if f not in ("north", "east", "west"): return (REUSE, "helmet_shell")
    g = shell(f, w, h)
    if f == "north":
        rows = {x: (3, 4) if x in (0, 1, w - 2, w - 1) else (3, 4, 5) for x in range(w)}
    else:                                                      # the side's front: east's last column, west's first
        f0, f1 = (w - 1, w - 2) if f == "east" else (0, 1)
        rows = {f0: (3, 4), f1: (3,)}
    for x, ys in rows.items():
        for y in ys:
            g[y][x] = 'VE' if y == ys[-1] else 'VG'
    if f == "north":
        g[3][0] = g[3][1] = g[3][2] = g[4][0] = 'VH'           # the highlight, top left
        for y in (3, 4, 5): g[y][w - 3] = 'VE' if y == 5 else g[y][w - 3]
        g[3][w - 1] = 'VE'
    else:
        g[3][f0] = 'VH' if f == "east" else 'VG'               # light from the viewer's left, like the front
    return g


def chief_brow(f, w, h):
    """G's forehead: the top of the shell wrapping round the front and both sides over the visor (the cap comes down
    round it, so the visor reads as an indent); painted like the shell, dark where it overhangs the glass."""
    if f == "down": return grid(w, h, 'D')
    if f == "north":
        if h == 1: return [['L'] + ['B'] * (w - 2) + ['S']]       # v6: 1 px tall, a plain band
        return plate(w, h, top='H', left='L', right='S', bottom='S', base='B', marks=('M',), n=0)
    return shell(f, w, h)


def chief_jaw(f, w, h):
    """G's mouthpiece: a narrower block under the visor, a lit ridge down its middle, shaded flanks (no vents: two
    dark dots read as a second pair of eyes)."""
    if f != "north": return shell(f, w, h)
    g = plate(w, h, top='L', left='L', right='S', bottom='S', base='B', marks=('M',), n=0)
    m = w // 2
    for y in range(1, h):
        g[y][m - 1] = 'H' if y < h - 1 else 'L'; g[y][m] = 'M'
        g[y][1] = 'M'; g[y][w - 2] = 'S'
    return g


# ---------- U: Samus's helmet as in Smash Bros. Ultimate, on the standard shell (2026-10-22) ----------
# (A first round after the Metroid Prime replicas, S, had the visor's bottom rising to a point over the snout and a
# red-orange option; user: steel like the others, redesigned after Ultimate's render, .scratch/helmets/ref/ssbu_helmet.jpg.)
# What makes Ultimate's helmet at 9 px:
#   - the visor a shield pointing down: wide at the top, its lower edge sloping down to a point in the middle, where a
#     narrow silver ribbed mouth grille hangs under it (a 2 px cube 0.75 out)
#   - a hooded brow: the dome's front edge comes down over the visor like a cap (A painted: a lit lip and the glass's
#     top row in shadow; B: a real lip standing 0.5 out over the visor), a seam down the middle of the dome
#   - cheek plates framing the visor and meeting in a V on the grille: a shadow rim along the visor's sloping edges
#     (v1: stepped dark seams, they zig-zagged like teeth)
#   - the visor wrapping round the sides (v3, like the spartan's), silver ribbed hoses from the grille's sides round the
#     jaw (v3: 3D; v2 painted them down the sides from a bulge, both gone)
# Plates in the steel shades (they take the paint: red dye gives Ultimate's red helmet); grille and hoses in the trim's
# silver (W / GR / G2: paint leaves them alone, as her silver grille and hoses).
# v4 (user: "the visor is broken, not continuous" - the front's outer columns stayed plate, cutting the glass at the
# corners - "and the details like the pipe are too basic: look at the EVA and Delta helmets"):
#   - one visor: the shield across the front and round both sides without a break (the corner columns carry the same
#     rows as the sides' first columns), a dark rim where it meets the armour, two reflection streaks, its top row in
#     the brow's shadow
#   - the brow a real lip wrapping the sides (0.5 out at the front, 0.2 at the sides) over the visor
#   - a half-pixel crest down the forehead and over the dome to the back, forehead panel lines either side
#   - corrugated hoses: a tube with raised rings leaving the grille, drooping along the jaw to a coupling at each
#     corner, back along the side and up into a round port behind the visor (v4 angled cubes, 12 and 25 deg;
#     v5 steps along the pixel axes; v6 straight runs at right angles, user)
#   - a plate tuck across the back (the in-game view), the crest over it
# Front, 9 x 9 (row r = y 47.5-r, column c = x 4.5-c). '.' = plate; g/h/e glass (glows), r = rim (darkest plate);
# row 1 is under the lip.
SAMUS_FRONT = (".........",
               "SSSSSSSSS",
               "eeeeeeeee",
               "ghhgghggg",
               "rghhggggr",
               "LrghggerL",
               ".LrrrrrL.",
               ".........",
               ".........")
# the sides' first columns from the front: glass rows (2 = in the brow's shadow), then the rim under them
SAMUS_WRAP = ((2, 3), (2, 3), (2,), (2,))   # v4b: one column shorter, clear of the port
SAMUS_PORT = (".HL.", "H0MD", "LMSD", ".DD.")         # the port the hose plugs into: a raised ring lit top left, a hole
#   (v4a: a dark iron ring round a 2 px hole, a black blot next to the rim)


def samus(lip):
    # the rim in the armour's darkest shade (it takes the paint: dark red on red; v4a's dark iron made black blots)
    k = {'h': 'VH', 'g': 'VG', 'e': 'VE', 'r': 'D', '0': 'I0'}

    def pat(rows):
        return [[None if ch == '.' else k.get(ch, ch) for ch in r] for r in rows]

    def shell_(f, w, h):
        if f == "down": return (REUSE, "helmet_shell")
        if f == "up": g = plate(w, h, top='H', left='L', right='S', bottom='S', base='B', n=0)
        else:
            lit = f in ("east", "north")
            g = plate(w, h, top='H', left='L' if lit else 'B', right='B' if lit else 'S', bottom='S', base='B', n=0)
        if f == "north":
            stamp(g, pat(SAMUS_FRONT), 0, 0)
            if not lip: g[1] = ['L'] + ['H'] * (w - 2) + ['L']  # A: the brow painted, a lit lip
        elif f == "up":                                        # row 0 = front, column 0 = +x
            # forehead panel lines: from the brow's corners towards the crest, then straight back beside it
            for r, c in ((0, 1), (1, 2), (2, 3), (3, 3), (4, 3), (5, 3), (6, 3), (7, 3), (8, 3)):
                g[r][c] = 'D'; g[r][w - 1 - c] = 'D'
            for r in range(0, 3):                              # the panel between them a shade lighter
                for c in range(r + 2, w - r - 2): g[r][c] = 'L'
        elif f == "south":                                     # a plate tuck across the back, the crest over it
            g[4] = ['S'] * w; g[5] = ['L'] * w
            g[4][0] = g[5][0] = 'B'
        elif f in ("east", "west"):
            front = (lambda i: w - 1 - i) if f == "east" else (lambda i: i)   # column i from the front edge
            for i, rows in enumerate(SAMUS_WRAP):
                g[1][front(i)] = 'S' if lip else 'H'           # under the lip (B) / the painted lip (A)
                for r in rows:
                    g[r][front(i)] = 'VE' if r == 2 else 'VG'
                g[rows[-1] + 1][front(i)] = 'D' if i == 0 else 'S'   # the rim, rising as the visor goes back (softer
            g[2][front(len(SAMUS_WRAP))] = 'S'                 # than the front's: v4a's ran into the port), closing it
            g[3][front(1)] = 'VH'                              # the streak carries round the corner
            port = pat(SAMUS_PORT)
            for r, row in enumerate(port):                     # rows 3-6, 5-8 from the front (z 1..5): where the
                for i, key in enumerate(row):                  # climbing hose arrives (z ~2.3, y ~43.4)
                    if key: g[3 + r][front(5 + i)] = key          # (by distance from the front: same spot both sides)
        return g

    def grille(f, w, h):
        """The silver mouth grille: ribs (lit rows between dark slots), shaded sides."""
        if f == "up": return grid(w, h, 'W')
        if f == "down": return grid(w, h, 'G2')
        if f != "north": return [['GR' if r % 2 == 0 else 'G2'] * w for r in range(h)]
        return [['W'] * w if r % 2 == 0 else ['I1'] * w for r in range(h)]

    def tube(f, w, h):
        """A hose's core: dark corrugation between the rings, shaded round."""
        if f == "up": return grid(w, h, 'G2')
        if f == "down": return grid(w, h, 'I1')
        return grid(w, h, 'I2')

    def ring(f, w, h):
        """A hose's raised ring: silver, lit on top."""
        if f == "up": return grid(w, h, 'W')
        if f == "down": return grid(w, h, 'G2')
        return [['W'] * w] + [['GR'] * w for _ in range(h - 1)]

    def coupling(f, w, h):
        """The silver joint at the jaw's corners: lit top and front edge."""
        if f == "up": return grid(w, h, 'W')
        if f == "down": return grid(w, h, 'G2')
        return [['W'] * w] + [['GR'] * (w - 1) + ['G2'] for _ in range(h - 1)]

    def brow(f, w, h):
        """The lip over the visor: plate, lit edge, its underside dark over the glass."""
        if f == "up": return grid(w, h, 'H')
        if f == "down": return grid(w, h, 'D')
        if f in ("north", "east", "west"): return [['L'] + ['H'] * (w - 2) + ['L']] + [['B'] * w for _ in range(h - 1)]
        return grid(w, h, 'S')

    def crest(f, w, h):
        """The half-pixel ridge: lit top, shaded flank."""
        if f == "up": return grid(w, h, 'H2')
        if f == "down": return grid(w, h, 'S')
        if f == "east": return grid(w, h, 'L')
        return grid(w, h, 'M' if f in ("north", "south") else 'S')

    tag = "B" if lip else "A"
    n = lambda part: f"hU{tag}_{part}"
    out = {n("shell"): (*SHELL, shell_),
           n("grille"): ([-1, 38.95, -4.75], [1, 41.5, -4], grille),
           # the crest: over the dome (0.25 up) and down the forehead to the lip (0.25 out; 0.05 narrower and lower
           # at the top than the dome's piece, which it runs into: no shared planes)
           n("crest_top"): ([-0.25, 47.5, -4.25], [0.25, 47.75, 4.5], crest),
           n("crest_front"): ([-0.2, 46.5, -4.25], [0.2, 47.7, -4], crest)}
    if lip:   # the lip wraps the sides back over the visor's wrap (0.2 proud of the sides, 0.5 of the front)
        out[n("brow")] = ([-4.7, 45.5, -4.5], [4.7, 46.5, 0.5], brow)
    # v6 (user: v4's angled cubes "went too far", v5's pixel steps were "too jagged": straight lines): each hose is
    # straight runs at right angles: along the jaw, the coupling at the corner, back along the side at the same
    # height, a silver elbow, straight up into the port. Raised silver rings spaced along the runs (the corrugation).
    # Rings overlap their run; runs end inside the couplings: no two faces share a plane.
    def run(name, lo, hi, axis, rings):
        out[n(name)] = (lo, hi, tube)
        for j, at in enumerate(rings):
            rlo = [a - 0.1 for a in lo]; rhi = [b + 0.1 for b in hi]
            rlo[axis], rhi[axis] = at - 0.15, at + 0.15
            rlo[0], rhi[0] = max(rlo[0], -4.75), min(rhi[0], 4.75)   # never past the helmet's reach
            rlo[2] = max(rlo[2], -4.75)
            out[n(f"{name}_ring{j}")] = (rlo, rhi, ring)

    for side, sx in (("r", 1), ("l", -1)):
        xs = lambda a, b: sorted((a * sx, b * sx))
        x0, x1 = xs(1, 4.1)                                    # along the jaw, from the grille's side
        run(f"hose_jaw_{side}", [x0, 40.65, -4.65], [x1, 41.35, -3.95], 0, [1.9 * sx, 3.1 * sx])
        x0, x1 = xs(3.95, 4.75)                                # the coupling at the corner
        out[n(f"hose_joint_{side}")] = ([x0, 40.4, -4.75], [x1, 41.6, -3.95], coupling)
        x0, x1 = xs(4.05, 4.7)                                 # back along the side
        run(f"hose_side_{side}", [x0, 40.65, -4.1], [x1, 41.35, 2.3], 2, [-2.6, -1.0, 0.6])
        x0, x1 = xs(3.95, 4.75)                                # the elbow under the port
        out[n(f"hose_elbow_{side}")] = ([x0, 40.4, 2.05], [x1, 41.6, 2.95], coupling)
        x0, x1 = xs(4.05, 4.7)                                 # up into the port's hole (z 2..3, y 42.5..43.5)
        run(f"hose_rise_{side}", [x0, 41.5, 2.15], [x1, 43.2, 2.85], 1, [42.35])
    return out

def reuse(name): return lambda f, w, h: (REUSE, name)


DRAFTS = {
    "helm_protector": {
        "hF_shell": (*SHELL, delta),
        # the window bars, 0.75 out from the flat visor; v4 (user): three, centred over columns 2, 4, 6
        **{f"hF_bar_{k}": ([x - 0.5 * DELTA_BAR_W, 41, -4.75], [x + 0.5 * DELTA_BAR_W, 47, -4], delta_bar)
           for k, x in (("r", 2), ("m", 0), ("l", -2))}},
    # v2 (user: rework the green one, "more of a master chief reference look"): a forehead plate coming forward over
    # one wide gold visor that wraps round the corners, a narrower mouthpiece under it with a ridge and two vents
    "helm_spartan": {
        "hG_shell": (*SHELL, chief_shell),
        # v3 (user): no raised gold visor; the top wraps round the front and sides, the glowing visor under it
        # v6: the top a 1 px band (y 44.5..45.5); v7: the visor flat, painted on the shell (chief_shell)
        "hG_brow": ([-4.75, 44.5, -4.75], [4.75, 45.5, -2], chief_brow),
        "hG_jaw": ([-2.5, 38.95, -4.75], [2.5, 41.75, -4], chief_jaw)},
    # v3 (user: Emile's helmet, the Reach EVA, without the skull and for now without the side module): the visor over
    # the whole front of the head, a steel band round the lower half, a mouthpiece; v4: on the standard shell (a
    # tiered dome, v2-v3, was dropped: the drafts keep the current helmet's shape)
    "helm_noble": eva_cubes(),
    # U: Samus as in Smash Bros. Ultimate, 2026-10-22: the user picked A (the brow painted) over B (samus(True): a real
    # lip wrapping the sides over the visor)
    "proto_helm_U_samus_A": samus(False),
    "helm_robot": {
        "hK_shell": (*SHELL, guy_shell),
        # the U-frame: a chin bar round the front corners, two side posts on it (sunk 0.1 into it, 0.05 in at the
        # sides and front: no shared planes; their tops just under the shell's)
        "hK_chin": ([-4.75, 38.9, -4.75], [4.75, 40.5, -2], guy_plate),
        "hK_post_r": ([3.75, 40.4, -4.7], [4.7, 47.45, -2], guy_plate),
        "hK_post_l": ([-4.7, 40.4, -4.7], [-3.75, 47.45, -2], guy_plate)},
}


def main():
    src = json.load(open(SRC))
    model = src["model"]
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    only = [g for g in DRAFTS if any(k in g for k in sys.argv[1:])] if len(sys.argv) > 1 else list(DRAFTS)
    drafts = {g: DRAFTS[g] for g in only}
    pre = tuple(n for g in drafts.values() for n in g)
    mine = [e for e in model["elements"] if e["name"] in pre]
    rest = [e for e in model["elements"] if e not in mine]
    # free = no other cube's UV uses it (transparent or old art nobody shows)
    used = used_mask(dict(model, elements=rest), Image.new("RGBA", tex.size, (0, 0, 0, 0)))
    cur = {e["name"]: e for e in model["elements"]}
    plan = {"groups": {}, "pixels": [], "origin": [0, 41, 1], "parent": "head", "checkpoint": "before helmet drafts: " + ", ".join(only)}
    faces, rects = {}, []
    for group, cubes in drafts.items():
        plan["groups"][group] = {}
        for name, (f, t, paint, *more) in cubes.items():
            spec = plan["groups"][group][name] = {"from": f, "to": t, "faces": {}}
            if more: spec.update(more[0])                     # a rotated cube: {"rotation": [x, y, z], "origin": [...]}
            for face in FACES:
                fw, fh = face_dims(f, t, face)
                pw, ph = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
                g = paint(face, pw, ph)
                if isinstance(g, tuple):                          # the current helmet's face, as it is
                    src_face = cur[g[1]]["faces"][face]
                    spec["faces"][face] = src_face["uv"] if src_face.get("texture") is not None else None
                    continue
                assert len(g) == ph and all(len(r) == pw for r in g), (name, face, pw, ph)
                faces[(group, name, face)] = (fw, fh, g)
                rects.append(((group, name, face), pw, ph))
    at = pack(used, rects)
    for (group, name, face), (fw, fh, g) in faces.items():
        u, v = at[(group, name, face)]
        plan["groups"][group][name]["faces"][face] = [u, v, u + fw, v + fh]
        plan["pixels"] += [[u + x, v + y, C[k]] for y, row in enumerate(g) for x, k in enumerate(row) if k != 'T']
        plan.setdefault("clear", []).extend([u + x, v + y] for y, row in enumerate(g) for x, k in enumerate(row) if k == 'T')
    json.dump(plan, open(OUT, "w"))
    print("drafts", list(drafts), "cubes", sum(len(c) for c in drafts.values()), "pixels", len(plan["pixels"]))


if __name__ == "__main__":
    main()
