"""Space-marine chest (2026-10-18, user: replace the painted abs with a chestpiece like a Primaris marine's, reference
`space_marine_armor.jpg`): a deep breastplate, a thick gorget rim in front of the chin that turns up into the collar at
the sides, and a pinched waist (a narrow blue abdomen plate on a ribbed dark-iron core) above the hip hoods (the belt).

Shapes (torso frame, px; the model faces -Z):
  chest        x +-7.5, y 31.625..39.625, z -5..6    the old chest block, now ending 4 px higher (the waist shows below)
  bp_main      x +-6.5, y 34.625..38.625, z -7..-5   the breastplate, 2 px proud of the chest (1 px past the pauldrons)
  bp_lower     the chest's underside: a plate turned about bp_main's front bottom edge, down to y 31.6, 1.5 px back
  gorget_lip   x +-5.6, y 38.625..40.625, z -7.5..-6.1  the collar's front rim, in front of the chin
  gorget_r/l   x 5.6..6.6, y 38.625..42.625, z -7.5..-2.5  the rim turning back and up into the collar
(A winged emblem on the chest was tried and removed: the user prefers the chest bare. The first version, a 4 px rim over
a 5 px breastplate, read as two stacked slabs.)
  abd_core     x +-6, y 28.625..31.625, z -3.5..5.5  dark ribbed waist (undersuit)
  abd_plate    x +-3, y 28.625..31.625, z -4.5..-3   the abdomen plate, 1 px behind the hood (belt) front
Known clash, left for the helmet rework (user: the head will get player-like proportions): the helmet pivots at
(y 41, z 1) and pitches up to 20 deg down (the game's aim) plus the animations; its chin guard's front-bottom edge then
sweeps (39, -6) -> (36.7, -4.9) and the cheeks do the same at x 3.5..5.6, through the breastplate's top. The rim itself
stands in front of z -6.1, where the chin never goes.

Faces are per-face UV on newly packed texture rects (free = transparent and outside every cube's UV), each painted as
seen from outside the face (Blockbench: north u0 at +x, east u0 at +z, west u0 at -z, south u0 at -x, v0 at the top;
up: u0 at -x, v0 at -z; down: u0 at -x, v0 at +z). Light from the viewer's upper left, like the pads (base 2d6bcc, lit
edge 4588e0, shaded edge 2358b2, top 62a3ef).

    uv run --with pillow python3 model/art/sm_chest_art.py   (reads .scratch/sm-chest/source.json from
    .agents/skills/blockbench-mcp/scripts/snapshot.mjs; writes .scratch/sm-chest/plan.json + preview.png)
"""
import json, base64, io, math, random
from PIL import Image

SRC = ".scratch/sm-chest/source.json"
OUT = ".scratch/sm-chest/plan.json"
C = dict(W='c8d0d3', H2='8ccaff', H='62a3ef', L='4588e0', M='3a7bd6', B='2d6bcc', S='2358b2', D='1b4895',
         G='122f66', G2='6f797e', GR='a2acb1', I0='1a1d1f', I1='25292c', I2='2f3437', I3='3b4246', I4='4c5559')
TORSO = [0, 27.125, 0.5]
BA = math.degrees(math.atan2(1.5, 3))          # slope of the chest's underside (3 down, 1.5 back)
BL = math.hypot(1.5, 3)

CUBES = {
    "chest":      ([-7.5, 31.625, -5], [7.5, 39.625, 6]),
    "bp_main":    ([-6.5, 34.625, -7], [6.5, 38.625, -5]),
    # the chest's underside: a plate turned about the breastplate's front bottom edge, its bottom 1.5 px back
    "bp_lower":   ([-6.5, 34.625 - BL, -7], [6.5, 34.625, -5.4], [0, 34.625, -7], [-BA, 0, 0]),
    "gorget_lip": ([-5.6, 38.625, -7.5], [5.6, 40.625, -6.1]),
    "gorget_r":   ([5.6, 38.625, -7.5], [6.6, 42.625, -2.5]),
    "gorget_l":   ([-6.6, 38.625, -7.5], [-5.6, 42.625, -2.5]),
    # B (2026-10-18, user): the abdomen plate 0.75 px deeper (front -5.25 -> -4.5) so the run's lean and twist keep it
    # behind the belt (hood fronts z -5.5); the least that keeps walk / run clean (-4.75 pokes 0.3 px out in the run;
    # only death still does, 0.54). The core can stay where it was.
    "abd_core":   ([-6, 28.625, -3.5], [6, 31.625, 5.5]),
    "abd_plate":  ([-3, 28.625, -4.5], [3, 31.625, -3]),
}
# the chest keeps its old south / up art (cut to the new height), everything else is painted here
KEEP = {"chest": ["south", "up"]}


def face_dims(f, t, face):
    w, h, d = t[0] - f[0], t[1] - f[1], t[2] - f[2]
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[face]


rng = random.Random(7)


def grid(w, h, fill):
    return [[fill for _ in range(w)] for _ in range(h)]


def speckle(g, base, marks, n):
    """Sparse 1 px marks on `base` pixels, like the pads' wear (never on edges)."""
    h, w = len(g), len(g[0])
    for _ in range(n):
        x, y = rng.randrange(1, max(2, w - 1)), rng.randrange(1, max(2, h - 1))
        if x < w and y < h and g[y][x] == base:
            g[y][x] = rng.choice(marks)


def plate(w, h, top='H', left='L', right='S', bottom='S', base='B', marks=('M', 'S'), n=None):
    """The suit's standard armour face: lit top / left edges, shaded right / bottom, a few wear marks."""
    g = grid(w, h, base)
    speckle(g, base, marks, n if n is not None else max(1, w * h // 14))
    for y in range(h):
        g[y][0] = left; g[y][w - 1] = right
    for x in range(w):
        if bottom: g[h - 1][x] = bottom
        if top: g[0][x] = top
    return g


def bp_front_v1(w, h):
    """Breastplate front (13 x 5): a barrel, brightest left of centre and turning dark at the right edge; the top row
    in the gorget's shadow, a lit rolled rim along the bottom edge over the waist."""
    prof = ['L', 'M', 'M', 'L', 'L', 'M', 'M', 'B', 'B', 'B', 'B', 'S', 'D']
    g = [[prof[x] for x in range(w)] for _ in range(h)]
    shade = {'H': 'L', 'L': 'M', 'M': 'B', 'B': 'S', 'S': 'D', 'D': 'D'}
    for x in range(w):
        g[0][x] = shade[shade[prof[x]]] if 2 <= x <= 10 else shade[prof[x]]   # under the rim
        g[h - 2][x] = shade[prof[x]]                                          # rim's top crease
        g[h - 1][x] = 'H' if x <= 6 else ('L' if x <= 10 else 'B')            # rolled bottom rim
    g[h - 2][0] = 'M'
    for (x, y) in ((3, 2), (8, 1), (10, 2), (5, 1)):
        g[y][x] = shade[g[y][x]]                                              # a little wear
    g[1][2] = 'H'
    return g


def lip_front_v1(w, h):
    """The gorget rim (11 x 4): a thick rolled lip, highlight along the top, belly, then the underside turning away."""
    rows = [['H2'] * 5 + ['H'] * 5 + ['L'],
            ['L', 'H', 'H', 'L', 'L', 'L', 'M', 'M', 'M', 'B', 'S'],
            ['M', 'L', 'M', 'M', 'B', 'B', 'B', 'B', 'B', 'S', 'S'],
            ['S'] * 4 + ['D'] * 7]
    g = [r[:w] for r in rows]
    g[1][8] = 'B'; g[2][3] = 'L'
    return g


def bp_front(w, h):
    """Breastplate front (13 x 7), pad style: flat armour blue, the lit left edge and the barrel's lit side, a shaded
    right edge, a soft top edge under the rim and a rolled bottom rim over the waist; a few wear marks."""
    g = grid(w, h, 'B')
    for y in range(h):
        g[y][0] = 'L'; g[y][1] = 'M'; g[y][w - 2] = 'S'; g[y][w - 1] = 'D'
    for x in range(1, w - 1):
        g[0][x] = 'S'                                       # shadow under the collar rim
    for (x, y) in ((4, 2), (9, 1), (6, 2), (3, 1), (2, 2)):
        g[y][x] = 'M'
    g[1][1] = 'H'
    return g


def bp_lower_front(w, h):
    """The chest's underside slope (13 x 4 for 3.35): faces down and forward, so a shade darker than the front; the
    top row is the lit corner where it turns under."""
    g = grid(w, h, 'S')
    for y in range(h):
        g[y][0] = 'B'; g[y][1] = 'B'; g[y][w - 1] = 'D'
    for x in range(w):
        g[0][x] = 'L' if x < 3 else ('M' if x < w - 2 else 'B')
        g[h - 1][x] = 'D'
    for (x, y) in ((5, 1), (9, 2)):
        g[y][x] = 'B'
    return g


def lip_front(w, h):
    """The collar's front rim (12 x 2 for 11.2 px): a rolled lip, bright top, darker underside."""
    g = [['H2'] + ['H'] * (w - 4) + ['L', 'M', 'B'],
         ['L'] + ['M'] * 2 + ['B'] * (w - 5) + ['S', 'S']]
    return g


def gorget_front(w, h, lit):
    g = grid(w, h, 'M' if lit else 'B')
    g[0][0] = 'H2' if lit else 'H'
    g[1][0] = 'H' if lit else 'L'
    g[h - 1][0] = 'S'
    return g


def gorget_side(w, h, outer):
    """Outer face: armour (lit on the +x side). Inner face (toward the helmet): shaded."""
    if outer == 'lit':
        return plate(w, h, top='H', left='L', right='L', bottom='S', base='M', marks=('L', 'B'))
    if outer == 'dark':
        return plate(w, h, top='L', left='B', right='B', bottom='D', base='B', marks=('S',))
    return plate(w, h, top='B', left='S', right='S', bottom='D', base='S', marks=('D',))


def ribs(w, h, vert=False):
    """Ribbed dark-iron undersuit: alternating ridges (lit) and grooves, a little noise."""
    g = grid(w, h, 'I2')
    for y in range(h):
        for x in range(w):
            k = (x if vert else y) % 2
            g[y][x] = 'I3' if k == 0 else 'I1'
            if rng.random() < 0.12:
                g[y][x] = 'I4' if k == 0 else 'I0'
    return g


def abd_front(w, h):
    """Abdomen plate (6 x 3): a central ridge (lit left, shaded right), under the chest's shadow at the top."""
    g = [['L', 'M', 'H', 'B', 'B', 'S'],
         ['L', 'M', 'L', 'S', 'B', 'S'],
         ['M', 'B', 'M', 'S', 'S', 'D']]
    for x in range(w):
        g[0][x] = {'L': 'B', 'M': 'S', 'H': 'M', 'B': 'S', 'S': 'D'}[g[0][x]]
    return g


def painter(name, face, w, h):
    if name == "chest":
        if face == "north":                   # mostly hidden: edges beside the breastplate and the well behind the rim
            g = grid(w, h, 'S')
            for y in range(h):
                g[y][0] = 'M'; g[y][1] = 'B'; g[y][w - 2] = 'S'; g[y][w - 1] = 'D'
            for x in range(w): g[0][x] = 'L' if x < 2 else ('B' if x > w - 3 else 'S')
            return g
        if face == "east":                     # lit side (the viewer's left from the front)
            return plate(w, h, top='H', left='M', right='L', bottom='S', base='B', marks=('M', 'L'))
        if face == "west":
            return plate(w, h, top='L', left='S', right='B', bottom='D', base='B', marks=('S',))
        if face == "down":
            return grid(w, h, 'D')
    if name == "bp_main":
        if face == "north": return bp_front(w, h)
        if face == "east": return plate(w, h, top='M', left='M', right='H', bottom='H', base='L', marks=('M',), n=0)
        if face == "west": return plate(w, h, top='S', left='S', right='B', bottom='B', base='S', marks=('D',), n=0)
        if face == "down": return [['S' if y == 0 else 'D' for _ in range(w)] for y in range(h)]
        return grid(w, h, 'S')
    if name == "bp_lower":
        if face == "north": return bp_lower_front(w, h)
        if face == "east": return grid(w, h, 'M')
        if face == "west": return grid(w, h, 'S')
        if face == "down": return grid(w, h, 'D')
        return grid(w, h, 'S')
    if name == "gorget_lip":
        if face == "north": return lip_front(w, h)
        if face == "up": return [['H2' if x < 5 else 'H' for x in range(w)] for _ in range(h)]
        if face == "down": return grid(w, h, 'D')
        if face == "south": return grid(w, h, 'S')
        return grid(w, h, 'B')
    if name in ("gorget_r", "gorget_l"):
        lit = name == "gorget_r"                # +x is the lit side
        if face == "north": return gorget_front(w, h, lit)
        if face == "up": return grid(w, h, 'H' if lit else 'L')
        if face == ("east" if lit else "west"): return gorget_side(w, h, 'lit' if lit else 'dark')
        if face == ("west" if lit else "east"): return gorget_side(w, h, 'inner')
        return grid(w, h, 'S')
    if name == "abd_core":
        if face in ("north", "east", "west"): return ribs(w, h)
        return grid(w, h, 'I1')
    if name == "abd_plate":
        if face == "north": return abd_front(w, h)
        if face == "east": return [['M'] * w for _ in range(h)]
        if face == "west": return [['S'] * w for _ in range(h)]
        return grid(w, h, 'S')
    raise ValueError((name, face))


def used_mask(model, tex):
    W, H = tex.size
    used = [[tex.getpixel((x, y))[3] > 0 for x in range(W)] for y in range(H)]
    def mark(u0, v0, u1, v1):
        for y in range(max(0, math.floor(min(v0, v1))), min(H, math.ceil(max(v0, v1)))):
            for x in range(max(0, math.floor(min(u0, u1))), min(W, math.ceil(max(u0, u1)))):
                used[y][x] = True
    for e in model["elements"]:
        if e.get("box_uv", True) is False or e.get("box_uv") is None and "faces" in e and not e.get("uv_offset"):
            for f in e.get("faces", {}).values():
                if f.get("uv") and f.get("texture") is not None: mark(*f["uv"])
        else:
            u, v = e.get("uv_offset", [0, 0])
            w, h, d = (math.floor(e["to"][i] - e["from"][i] + 1e-6) for i in range(3))
            mark(u, v, u + 2 * (w + d), v + d + h)
            for f in e.get("faces", {}).values():       # box-UV cubes also list their faces' uv
                if f.get("uv") and f.get("texture") is not None: mark(*f["uv"])
    return used


def pack(used, rects):
    """First fit, top-left scan; rects = [(key, w, h)] largest first. Marks the packed areas used."""
    H, W = len(used), len(used[0])
    out = {}
    for key, w, h in sorted(rects, key=lambda r: -r[1] * r[2]):
        done = False
        for v in range(H - h + 1):
            for u in range(W - w + 1):
                if all(not used[v + j][u + i] for j in range(h) for i in range(w)):
                    for j in range(h):
                        for i in range(w): used[v + j][u + i] = True
                    out[key] = (u, v); done = True; break
            if done: break
        if not done: raise SystemExit(f"no room for {key} {w}x{h}")
    return out


def main():
    src = json.load(open(SRC))
    model = src["model"]
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    used = used_mask(model, tex)
    chest = next(e for e in model["elements"] if e["name"] == "chest")
    rects, faces = [], {}
    for name, (f, t, *_) in CUBES.items():
        for face in ("north", "east", "south", "west", "up", "down"):
            if face in KEEP.get(name, []): continue
            fw, fh = face_dims(f, t, face)
            pw, ph = math.ceil(fw - 1e-6), math.ceil(fh - 1e-6)
            g = painter(name, face, pw, ph)
            assert len(g) == ph and all(len(r) == pw for r in g), (name, face, pw, ph)
            faces[(name, face)] = (fw, fh, g)
            rects.append(((name, face), pw, ph))
    at = pack(used, rects)
    plan = {"cubes": {}, "pixels": []}
    for name, (f, t, *rest) in CUBES.items():
        plan["cubes"][name] = {"from": f, "to": t, "faces": {}}
        if rest: plan["cubes"][name].update(origin=rest[0], rotation=rest[1])
    for (name, face), (fw, fh, g) in faces.items():
        u, v = at[(name, face)]
        plan["cubes"][name]["faces"][face] = [u, v, u + fw, v + fh]
        for y, row in enumerate(g):
            for x, k in enumerate(row):
                plan["pixels"].append([u + x, v + y, C[k]])
    # the chest's kept faces: the old south / up uv cut to the new height (south: v0 at the top, y 39.625 stays)
    old = {k: f["uv"] for k, f in chest["faces"].items()}
    s = old["south"]; frac = (39.625 - 31.625) / (chest["to"][1] - chest["from"][1])
    plan["cubes"]["chest"]["faces"]["south"] = [s[0], s[1], s[2], s[1] + (s[3] - s[1]) * frac]
    plan["cubes"]["chest"]["faces"]["up"] = old["up"]
    plan["origin"] = TORSO
    json.dump(plan, open(OUT, "w"))
    prev = tex.copy()
    for x, y, c in plan["pixels"]:
        prev.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,))
    bg = Image.new("RGBA", prev.size, (255, 0, 255, 255)); bg.alpha_composite(prev)
    bg.resize((prev.width * 4, prev.height * 4), Image.NEAREST).save(".scratch/sm-chest/preview.png")
    print("faces", len(faces), "pixels", len(plan["pixels"]))
    for k, (u, v) in sorted(at.items(), key=lambda kv: kv[1][1] * 1000 + kv[1][0]): print(k, u, v)


if __name__ == "__main__":
    main()
