"""Shared pieces for the engine backpack drafts (model/art/backpack_drafts_art.py, 2026-10-21): palette, materials,
reserved glow colours and the plan builder (per-face UV cubes in hidden proto_* groups, packed into free texture space).

Palette keys (one character each, so faces can be drawn as strings):
  dark iron (the firebox pack's)   K 1a1d1f  d 25292c  D 2f3437  M 3b4246  L 4c5559
  steel trim (the exhausts')       5 565f63  S 6f797e  T 7d888e  t 89939a  G a2acb1  W c9d1d5
  prismarine (tidal C)             0 1d3833  1 28493f  2 33594c  3 41705f  4 568a75  P 6ba38b
  obsidian (reactor B)             o 120d1a  O 1c1428  Q 281c38
  copper windings (reactor C)      c 7a3f22  C a85a32  k c77b4b
  netherite (round 2)              6 241e20  7 2f2729  8 3d3436  9 4b4245  A 5d5558  B 716a6d
  gold trim                        z b8892a  y f2cf5c  Y fbe792     glass (not glowing)  g 8fbfc4  h cfe9ea
  GLOW, reserved (dark when off, lit by a new glowmask in game; see GLOW_LIT):
    tidal  a 173a40 (dim)  b 1d4a50  e 275c63 (core)
    reactor p 2b2238 (dim)  q 362a47  r 433556 (core)
'.' in a pattern = leave the material's own pixel.
"""
import json, base64, io, math, random
from PIL import Image
import sys
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, face_dims
from helmet_drafts2_art import face_point

PAL = dict(K='1a1d1f', d='25292c', D='2f3437', M='3b4246', L='4c5559',
           **{'5': '565f63'}, S='6f797e', T='7d888e', t='89939a', G='a2acb1', W='c9d1d5',
           **{'0': '1d3833', '1': '28493f', '2': '33594c', '3': '41705f', '4': '568a75'}, P='6ba38b',
           o='120d1a', O='1c1428', Q='281c38', c='7a3f22', C='a85a32', k='c77b4b',
           **{'6': '241e20', '7': '2f2729', '8': '3d3436', '9': '4b4245'}, A='5d5558', B='716a6d',
           y='f2cf5c', Y='fbe792', z='b8892a', g='8fbfc4', h='cfe9ea',
           a='173a40', b='1d4a50', e='275c63', p='2b2238', q='362a47', r='433556')
GLOW_LIT = dict(a='1f8fa8', b='5fd3e6', e='d8fcff', p='7a4fd0', q='c39bff', r='fff6ff')
FACES = ("north", "east", "south", "west", "up", "down")
rng = random.Random(21)


def grid(w, h, k):
    return [[k] * w for _ in range(h)]


def iron(face, w, h, f, t):
    """The firebox pack's dark iron: lit top row, dark edges and bottom, an occasional light mark."""
    if face in ("north", "down"): return grid(w, h, 'K')
    if face == "up":
        g = grid(w, h, 'D')
        for x in range(w): g[h - 1][x] = 'M'
        for y in range(h): g[y][0] = g[y][w - 1] = 'd'
        return g
    g = grid(w, h, 'D')
    for y in range(h): g[y][0] = g[y][w - 1] = 'd'
    for x in range(w): g[0][x] = 'M'; g[h - 1][x] = 'K'
    if w > 4 and h > 4: g[1][1] = 'L'; g[1][w - 2] = 'L'
    return g


def steel(face, w, h, f, t):
    """The exhausts' steel: lit left, mid, shaded right columns, a light top and a dark seam at the bottom."""
    if face == "north": return grid(w, h, 'S')
    if face in ("up", "down"):
        g = grid(w, h, 'G' if face == "up" else '5')
        if face == "up":
            for y in range(h): g[y][w - 1] = 'T'
        return g
    g = [['G'] + ['t'] * (w - 2) + ['S'] if w > 1 else ['t'] for _ in range(h)]
    g[0] = ['W'] * w
    return g


def prism(face, w, h, f, t):
    """Prismarine plating, mottled like the vanilla block, a lit top row."""
    if face == "north": return grid(w, h, '0')
    g = [[rng.choice('1222334') for _ in range(w)] for _ in range(h)]
    if face != "down":
        g[0] = ['4' if rng.random() < .6 else 'P' for _ in range(w)]
        for y in range(h): g[y][w - 1] = '1'
    return g


def obsid(face, w, h, f, t):
    """Obsidian (the end crystal's frame stone), violet specks that glow with the reactor."""
    if face == "north": return grid(w, h, 'o')
    g = [[rng.choice('oOOOQ') for _ in range(w)] for _ in range(h)]
    for _ in range(max(1, w * h // 9)):
        g[rng.randrange(h)][rng.randrange(w)] = 'p'
    return g


def nether(face, w, h, f, t):
    """Netherite (the endgame metal): warm dark grey, lit top row and left edge, darker right and bottom."""
    if face in ("north", "down"): return grid(w, h, '6')
    g = [[rng.choice('8889') for _ in range(w)] for _ in range(h)]
    for y in range(h): g[y][0] = 'A'; g[y][w - 1] = '7'
    for x in range(w): g[0][x] = 'B' if face != "up" else 'A'; g[h - 1][x] = '6' if face != "up" else '9'
    return g


def gold(face, w, h, f, t):
    """Gold trim: bright top, a darker right edge."""
    if face == "down": return grid(w, h, 'z')
    g = grid(w, h, 'y')
    g[0] = ['Y'] * w
    for y in range(h): g[y][w - 1] = 'z'
    return g


MATERIALS = dict(iron=iron, steel=steel, prism=prism, obsid=obsid, nether=nether, gold=gold)


def on(material, **faces):
    """A painter: the material everywhere, with face overrides. An override is a function (w, h, f, t) -> rows, or a
    list of strings (must match the face's size; '.' keeps the material's pixel)."""
    base = MATERIALS[material]

    def paint(face, w, h, f, t):
        g = base(face, w, h, f, t)
        o = faces.get(face)
        if o is None: return g
        rows = o(w, h, f, t) if callable(o) else o
        assert len(rows) == h and all(len(r) == w for r in rows), (face, w, h, rows)
        return [[g[y][x] if ch == '.' else ch for x, ch in enumerate(row)] for y, row in enumerate(rows)]
    return paint


def world(fn):
    """A face override painted in model coordinates: fn(x, y, z) -> key or '.', sampled at each texel's centre."""
    def o(face):
        def rows(w, h, f, t):
            return [''.join(fn(*face_point(face, f, t, c, r)) for c in range(w)) for r in range(h)]
        return rows
    return o


def build(src_path, drafts, parent, origin, checkpoint):
    """drafts: {group: {cube: (from, to, painter)}} -> plan for .scratch/helmets/apply.js (+ 'lit': the same texels in
    their lit colours, for review renders)."""
    src = json.load(open(src_path))
    model = src["model"]
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    # the reserved glow colours may only be on the drafts' own texels (cubes named pk*, or the cubes being rebuilt) so far
    names = {n for g in drafts.values() for n in g}
    mine = used_mask(dict(model, elements=[e for e in model["elements"] if e["name"].startswith("pk") or e["name"] in names]),
                     Image.new("RGBA", tex.size, (0, 0, 0, 0)))
    have = {'%02x%02x%02x' % tex.getpixel((x, y))[:3] for x in range(tex.width) for y in range(tex.height)
            if tex.getpixel((x, y))[3] and not mine[y][x]}
    for k in GLOW_LIT:
        assert PAL[k] not in have, f"reserved glow colour {k}={PAL[k]} already in the texture"
    names = {n for g in drafts.values() for n in g}
    rest = [e for e in model["elements"] if e["name"] not in names]
    used = used_mask(dict(model, elements=rest), Image.new("RGBA", tex.size, (0, 0, 0, 0)))
    plan = {"groups": {}, "pixels": [], "lit": [], "origin": origin, "parent": parent, "checkpoint": checkpoint}
    faces, rects = {}, []
    for group, cubes in drafts.items():
        plan["groups"][group] = {}
        for name, (f, t, paint) in cubes.items():
            plan["groups"][group][name] = {"from": f, "to": t, "faces": {}}
            for face in FACES:
                fw, fh = face_dims(f, t, face)
                pw, ph = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
                faces[(group, name, face)] = (fw, fh, paint(face, pw, ph, f, t))
                rects.append(((group, name, face), pw, ph))
    at = pack(used, rects)
    for (group, name, face), (fw, fh, g) in faces.items():
        u, v = at[(group, name, face)]
        plan["groups"][group][name]["faces"][face] = [u, v, u + fw, v + fh]
        for y, row in enumerate(g):
            for x, k in enumerate(row):
                plan["pixels"].append([u + x, v + y, PAL[k]])
                if k in GLOW_LIT:
                    base = [int(PAL[k][i:i + 2], 16) for i in (0, 2, 4)]
                    glow = [int(GLOW_LIT[k][i:i + 2], 16) for i in (0, 2, 4)]
                    plan["lit"].append([u + x, v + y, ''.join('%02x' % min(255, a + b) for a, b in zip(base, glow))])
    return plan
