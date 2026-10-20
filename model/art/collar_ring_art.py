"""Collar ring (2026-10-19, user: "the inside of the collar is very stepped overall, could we flatten it? And complete
the collar on the front, but at a lower height, so it acts as a sort of neck guard").

The collar had grown out of six overlapping blocks: collar_r/l (inner face x 5.5, top 43.725), gorget_r/l (5.6, 42.625),
the nape strips (4.55, 43.575) and nape_back, over a floor at three heights (the breastplate top 38.625 in front, the
neck notch 39.625, the chest rims 40.625). It becomes one ring (torso frame, px; the model faces -Z):

  collar_post_r/l   x 5.5..6.6, y 38.675..43.625, z -7.5..-5    the front corners, down onto the breastplate
  collar_r/l        x 5.5..6.6, y 40.625..43.625, z -5..6       the side walls, on the chest rims, to the chest's back
  collar_back       x -5.5..5.5, y 40.625..43.625, z 4.9..6     between the walls, flush with the chest's back
  neck_guard        x -5.5..5.5, y 38.675..41.625, z -7.5..-6.5 the low front: a 1 px wall over the gorget lip
Inside, the walls are flat: x +-5.5 at the sides (1 px off the helmet's sides; the helmet's sway stays clear of it in
every animation but idle / death / power-down), z 4.9 at the back, z -6.5 at the front (the guard's inner face, 1 px
above the floor; the helmet's chin reaches z -5.9 at most). The floor is the chest rims' top (40.625) except at the
front: the old neck notch (x +-4.5, z -5..-3, at the chest top 39.625) stays open and between the guard and the chest a
1.5 px groove goes down to the breastplate's top (38.625). Both were filled at first (chest_rim_f, neck_floor) and the
user had them removed (FILL_NOTCH, NECK_FLOOR, 2026-10-19): the helmet's grill (chin_guard, y 39..41, z -4.75..-4) sits
in that notch, and with it open the grill shows from above, over the guard (straight from the front the guard hides it,
which the user accepted).
The user tried a 1 px band at 39.625 (the grill shows straight from the front too) and chose the guard (2026-10-19),
1 px deep instead of the first 2.5 px shelf.
WELL (2026-10-19, user: "the chest is still covering part of the helmet"): the helmet's bottom (38.95) and grill (39..41)
sat below that floor, so collar_well_apply.js cut the chest's top 1 px away inside the collar (floor 38.625, the
breastplate's top, level with the groove behind the guard): the posts and walls became one block per side
(collar_r/l, y 38.625..43.625, z -7.5..6) and collar_back reaches down to the floor too.
Pieces meet without sharing a plane in the same direction (GeckoLib draws both faces: flicker); bottoms at
38.675 keep the front faces off the gorget lip's (inflated to z -7.55, top 38.675).

Post + wall read as one piece: their faces are cut from one painted virtual box (x 5.5..6.6, y 38.675..43.625,
z -7.5..6), so the paint runs across the joint. Painting follows the chest recipe (sm_chest_art.py palette and plate()):
flat armour blue, lit top rim, light from the viewer's upper left (+x lit), inner faces shaded. The notch filler's top
reuses the chest's own top-face pixels (the notch region of the old chest top, still on the texture).

    node .agents/skills/blockbench-mcp/scripts/snapshot.mjs .scratch/collar-ring/source.json
    uv run --with pillow python3 model/art/collar_ring_art.py      (writes .scratch/collar-ring/plan.json + preview.png)
then model/art/collar_ring_apply.js.
"""
import json, base64, io, math, os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sm_chest_art import C, grid, plate, used_mask, pack, face_dims, gorget_front  # noqa: E402

SRC = ".scratch/collar-ring/source.json"
OUT = ".scratch/collar-ring/plan.json"
TORSO = [0, 24.625, 0]

FLOOR, TOP, GUARD_TOP, BASE = 40.625, 43.625, 41.625, 38.675
GUARD_DEPTH = 1         # the guard wall; neck_floor fills the rest up to the chest front (z -5)
WELL = True             # collar_well_apply.js cut the chest top inside the collar: walls + back reach the floor at 38.625
NECK_FLOOR = False      # True: neck_floor (x +-5.5, y 38.675..40.625) fills the groove between guard and chest
FILL_NOTCH = False      # True: chest_rim_f fills the neck notch (x +-4.5, y 39.625..40.625, z -5..-3)
WIN, WOUT, FRONT, FIN, BIN, BOUT = 5.5, 6.6, -7.5, -5, 4.9, 6
WALL_BOTTOM = 38.625 if WELL else FLOOR      # the side walls' and the back's bottom
SIDE_BOTTOM = 38.625 if WELL else BASE       # the posts + walls' painted box

# Painted (virtual) boxes: name -> (from, to). Real cubes cut their faces out of one of these.
VIRTUAL = {
    "side_r": ([WIN, SIDE_BOTTOM, FRONT], [WOUT, TOP, BOUT]),
    "side_l": ([-WOUT, SIDE_BOTTOM, FRONT], [-WIN, TOP, BOUT]),
    "collar_back": ([-WIN, WALL_BOTTOM, BIN], [WIN, TOP, BOUT]),
    "neck_guard": ([-WIN, BASE, FRONT], [WIN, GUARD_TOP, FRONT + GUARD_DEPTH]),
}
if NECK_FLOOR:
    VIRTUAL["neck_floor"] = ([-WIN, BASE, FRONT + GUARD_DEPTH], [WIN, FLOOR, FIN])
# Real cubes: name -> (from, to, virtual box, parent group)
CUBES = {
    "collar_back": ([-WIN, WALL_BOTTOM, BIN], [WIN, TOP, BOUT], "collar_back", "collar_parts"),
    "neck_guard": ([-WIN, BASE, FRONT], [WIN, GUARD_TOP, FRONT + GUARD_DEPTH], "neck_guard", "collar_parts"),
}
if WELL:     # one block per side, front corner to back, down to the floor
    CUBES["collar_r"] = ([WIN, SIDE_BOTTOM, FRONT], [WOUT, TOP, BOUT], "side_r", "collar_parts")
    CUBES["collar_l"] = ([-WOUT, SIDE_BOTTOM, FRONT], [-WIN, TOP, BOUT], "side_l", "collar_parts")
else:        # the walls stand on the chest rims, the posts reach down onto the breastplate in front
    CUBES["collar_post_r"] = ([WIN, BASE, FRONT], [WOUT, TOP, FIN], "side_r", "collar_parts")
    CUBES["collar_r"] = ([WIN, FLOOR, FIN], [WOUT, TOP, BOUT], "side_r", "collar_parts")
    CUBES["collar_post_l"] = ([-WOUT, BASE, FRONT], [-WIN, TOP, FIN], "side_l", "collar_parts")
    CUBES["collar_l"] = ([-WOUT, FLOOR, FIN], [-WIN, TOP, BOUT], "side_l", "collar_parts")
if NECK_FLOOR:
    CUBES["neck_floor"] = ([-WIN, BASE, FRONT + GUARD_DEPTH], [WIN, FLOOR, FIN], "neck_floor", "collar_parts")
REMOVE = ["collar_r", "collar_l", "gorget_r", "gorget_l", "nape_r", "nape_l", "nape_back"]

E = {"north": ("x", 1, "y", 1), "south": ("x", 0, "y", 1), "east": ("z", 1, "y", 1), "west": ("z", 0, "y", 1),
     "up": ("x", 0, "z", 0), "down": ("x", 0, "z", 1)}   # face: u axis, u0 at max?, v axis, v0 at max?
AX = {"x": 0, "y": 1, "z": 2}


def sub(vf, vt, uv, face, pf, pt):
    """The piece's face as the linear sub-range of the virtual box's face (Blockbench face uv: u1/v1 on a fixed edge)."""
    ua, umax, va, vmax = E[face]
    def rng(ax, atmax):
        i = AX[ax]; lo, hi = vf[i], vt[i]
        s0, s1 = (pf[i] - lo) / (hi - lo), (pt[i] - lo) / (hi - lo)
        return (1 - s1, 1 - s0) if atmax else (s0, s1)
    u0, u1 = rng(ua, umax); v0, v1 = rng(va, vmax)
    return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]


def side_outer(w, h, lit):
    """Outer side of a wall + post (14 x 5): the chest recipe; the post's lower 2 rows reach down onto the breastplate."""
    if lit:
        return plate(w, h, top='H', left='L', right='L', bottom='S', base='M', marks=('L', 'B'))
    return plate(w, h, top='L', left='B', right='B', bottom='D', base='B', marks=('S',))


def side_inner(w, h):
    """Inner side (toward the head): shaded, a lit rim along the top."""
    g = plate(w, h, top='B', left='S', right='S', bottom='D', base='S', marks=('D',))
    return g


def guard_front(w, h):
    """The neck guard's front (11 x 3), above the gorget lip: a lit top rim, flat blue, the shaded right end."""
    if h == 1:      # the 1 px band: a plain blue strip above the lip's bright top (its own top face carries the rim)
        return [['L'] + ['M'] * 2 + ['B'] * (w - 4) + ['S']]
    g = [['H2'] * 5 + ['H'] * (w - 6) + ['L'],
         ['L'] + ['M'] * 2 + ['B'] * (w - 4) + ['S'],
         ['M'] + ['B'] * (w - 3) + ['S', 'S']]
    g[1][7] = 'M'
    return g


def painter(vname, face, w, h):
    if vname in ("side_r", "side_l"):
        lit = vname == "side_r"                      # +x is the lit side
        outer = "east" if lit else "west"
        if face == outer: return side_outer(w, h, lit)
        if face in ("east", "west"): return side_inner(w, h)
        if face == "up":                             # u along x, v from the front (-z) to the back
            return [['H2' if lit and x == 0 and y < 3 else ('H' if lit else 'L') for x in range(w)] for y in range(h)]
        if face == "north": return gorget_front(w, h, lit)
        if face == "south": return grid(w, h, 'S')
        return grid(w, h, 'D')
    if vname == "collar_back":
        if face == "south": return plate(w, h, top='H', left='L', right='S', bottom='B', base='B', marks=('M', 'S'))
        if face == "north": return side_inner(w, h)
        if face == "up": return [['L' if y == 0 else 'H' for _ in range(w)] for y in range(h)]   # v0 at the inner edge
        return grid(w, h, 'S')
    if vname == "neck_guard":
        if face == "north": return guard_front(w, h)
        if face == "up": return [[('H2' if x < 5 else 'H') if y == 0 else ('H' if x < 6 else 'L') for x in range(w)] for y in range(h)]
        if face == "south": return [['B' if y == 0 else 'S' for _ in range(w)] for y in range(h)]
        if face in ("east", "west") and h == 1: return grid(w, h, 'M' if face == "east" else 'S')
        if face == "east": return grid(w, h, 'M')
        if face == "west": return grid(w, h, 'S')
        return grid(w, h, 'D')
    if vname == "neck_floor":                         # only its top shows: the floor, the chest top's blue
        return grid(w, h, 'B' if face == "up" else 'S')
    raise ValueError((vname, face))


def main():
    src = json.load(open(SRC))
    model = src["model"]
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    used = used_mask(model, tex)
    rects, grids = [], {}
    for vname, (f, t) in VIRTUAL.items():
        for face in E:
            fw, fh = face_dims(f, t, face)
            pw, ph = math.ceil(fw - 1e-6), math.ceil(fh - 1e-6)
            g = painter(vname, face, pw, ph)
            assert len(g) == ph and all(len(r) == pw for r in g), (vname, face, pw, ph, len(g), len(g[0]))
            grids[(vname, face)] = (fw, fh, g)
            rects.append(((vname, face), pw, ph))
    at = pack(used, rects)
    plan = {"cubes": {}, "pixels": [], "remove": REMOVE, "origin": TORSO}
    vuv = {}
    for (vname, face), (fw, fh, g) in grids.items():
        u, v = at[(vname, face)]
        vuv[(vname, face)] = [u, v, u + fw, v + fh]
        for y, row in enumerate(g):
            for x, k in enumerate(row):
                plan["pixels"].append([u + x, v + y, C[k]])
    for name, (f, t, vname, parent) in CUBES.items():
        vf, vt = VIRTUAL[vname]
        plan["cubes"][name] = {"from": f, "to": t, "parent": parent,
                               "faces": {face: sub(vf, vt, vuv[(vname, face)], face, f, t) for face in E}}
    # the notch filler: the chest top's notch region (rim_b's top extended from z -3 to -5), other faces hidden (rim_b's)
    faces = {}
    if FILL_NOTCH:
        rimb = next(e for e in model["elements"] if e["name"] == "chest_rim_b")
        rf, rt = rimb["from"], rimb["to"]
        faces = {k: fc["uv"] for k, fc in rimb["faces"].items()}
        faces["up"] = sub(rf, rt, rimb["faces"]["up"]["uv"], "up", [-4.5, 39.625, -5], [4.5, 40.625, -3])
        plan["cubes"]["chest_rim_f"] = {"from": [-4.5, 39.625, -5], "to": [4.5, 40.625, -3], "parent": "chest_parts", "faces": faces}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(plan, open(OUT, "w"))
    prev = tex.copy()
    for x, y, c in plan["pixels"]:
        prev.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,))
    bg = Image.new("RGBA", prev.size, (255, 0, 255, 255)); bg.alpha_composite(prev)
    bg.resize((prev.width * 4, prev.height * 4), Image.NEAREST).save(".scratch/collar-ring/preview.png")
    print("pixels", len(plan["pixels"]), "cubes", list(plan["cubes"]))
    for k, (u, v) in sorted(at.items(), key=lambda kv: kv[1][1] * 1000 + kv[1][0]): print(k, u, v)


if __name__ == "__main__":
    main()
