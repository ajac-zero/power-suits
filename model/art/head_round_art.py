"""Rounder helmet with 3D details (2026-10-18, user: "a little less square all around"; 9 px instead of the pumpkin's
10 "to give room for some more 3D details"; chose a + b + c + d of the proposal).

The shell is 9 x 9 x 9 (x +-4.5, y 38.5..47.5, z -4..5), 0.5 px clear of the pilot's 8 px head all round; raised
0.5 px plates use the room up to the old pumpkin's envelope:
  a  rounded silhouette: a cap on top (8 x 8, inset 0.5 px), side plates and a back plate inset 1 px from every edge,
     so the outline steps in at every corner from any side
  b  brow ridge: a 0.5 px ledge across the top of the visor (hangs 0.25 px over the glass)
  c  respirator snout: the grill stands 1 px proud of the face frame (z -5.75, where the mouthpiece was)
  d  ear bolts: a stepped 3 x 3 steel disc on each side plate, above the collars (y 43.75..46.75; the collars end at
     43.7) and out to x +-5.5
The face keeps its layout: frame (brow y 46..47.5, cheeks) 0.75 px proud, the visor glass 0.25 behind it
(y 40.625..46, reserved glow colours), the grill from the helmet's bottom up to 40.625.

    uv run --with pillow python3 model/art/head_round_art.py  (reads .scratch/head/round_source.json, a fresh
    snapshot from the skill's snapshot.mjs; writes .scratch/head/round_plan.json; apply: head_round_apply.js)
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, plate, C, face_dims
from head_pumpkin_art import visor as visor_face

SRC, OUT = ".scratch/head/round_source.json", ".scratch/head/round_plan.json"
C = dict(C, GM='89939a', VG='262c34', VE='1e232a', VH='46505c')
HEAD = [0, 41, 1]
ZF = -4.75                       # face frame front (shell front -4)
Y0, Y1 = 38.5, 47.5              # shell bottom / top
CUBES = {
    # v4 (user): no ears, no steps: the shell a plain 9 x 9 x 9 box painted like the rest of the armour (the 0.5 px
    # chamfers, the 7 x 7 cap and the back step of v3 are gone)
    # the shell ends at the snout's bottom (user: no strip under it); 0.05 below the pilot's head (y 39) so the two
    # bottoms don't share a plane
    "helmet_shell": ([-4.5, 38.95, -4], [4.5, Y1, 5]),
    # v2 (user: "terrible": the frame a thin square, framed mouthpiece; wanted a wider frame or one that wraps round
    # the sides, hugging the visor with its corners cut, no frame round the mouthpiece): a steel band over the visor
    # and down its sides; the side bands are blocks that turn the helmet's corner (1.25 px of front, 2 px along the
    # side); the band's corners stay open (no square frame)
    # the top band wraps onto the top like the side bands onto the sides (user): 2 px back, 0.25 proud of the top
    "helmet_brow": ([-3.5, 46, ZF], [3.5, Y1 + 0.25, -2.75]),
    "cheek_r": ([3.5, 41, ZF], [4.75, 46, -2.75]),                        # side bands round the corners
    "cheek_l": ([-4.75, 41, ZF], [-3.5, 46, -2.75]),
    "visor": ([-3.5, 41, -4.5], [3.5, 46, -4]),
    # the snout flush with the band's front (user: no further out) and 2 px tall (y 39..41) under the visor, the old
    # 7-wide grill
    "chin_guard": ([-3.5, 39, ZF], [3.5, 41, -4]),
}
# faces left open (no texture: Blockbench / the Bedrock export drop them): the shell's underside, so the wearer's head
# shows from below (user)
OPEN = {("helmet_shell", "down")}
PAINT_W = {}                                    # (cube, face) -> painted width, for a pattern stretched over a face
REMOVE = ["brow_ridge", "helmet_side_r", "helmet_side_l", "band_wrap_r", "band_wrap_l", "ear_nub_r", "ear_nub_l",
          "ear_bolt_r", "ear_bolt_l", "helmet_shell_fb", "helmet_cap", "helmet_back"]


def blue(face, w, h, lit_side=None):
    """Armour blue in the chest's style: lit top / front edges, shaded bottom."""
    if face == "down": return grid(w, h, 'S')
    if face == "up":
        g = plate(w, h, top='L', left='L', right='B', bottom='B', base='M', marks=('L', 'B'))
        return g
    lit = face == "east" or face == "north" or face == lit_side
    return plate(w, h, top='H', left='L' if lit else 'B', right='B' if lit else 'S', bottom='S',
                 base='B', marks=('M', 'S'))


def frame(name, face, w, h):
    """The steel band: lit top edge, the +x side lit (the viewer's left from the front)."""
    if face == "north":
        if name == "helmet_brow": return [['W'] * w] + [['GR'] * w for _ in range(h - 1)]
        if name == "cheek_r": return [['W', 'GR'] for _ in range(h)]
        if name == "cheek_l": return [['GR', 'GM'] for _ in range(h)]
    if name == "helmet_brow" and face == "up":                 # along the top: lit front row, shaded back row
        return [['W'] * w] + [['GR'] * w for _ in range(h - 2)] + [['GM'] * w]
    if name.startswith("cheek") and face == ("east" if name.endswith("_r") else "west"):
        g = [['GR'] * w for _ in range(h)]                     # along the side: lit top row, shaded bottom row
        g[0] = ['W'] * w; g[h - 1] = ['G2'] * w
        return g
    if face == "up": return grid(w, h, 'W')
    if face == "down": return grid(w, h, 'G2')
    if face == "east": return grid(w, h, 'GR')
    if face == "west": return grid(w, h, 'GM')
    return grid(w, h, 'I2')


def snout(face, w, h):
    if face == "north":                                        # 7 x 2: the old grill
        return [['W', 'I0', 'GR', 'I0', 'GR', 'I0', 'G2'], ['GR', 'I1', 'G2', 'I1', 'G2', 'I1', 'G2']][:h]
    if face in ("east", "west"):                               # along the sides, like the band
        g = [['GR'] * w for _ in range(h)]
        g[0] = ['W'] * w; g[h - 1] = ['G2'] * w
        return g
    if face == "up": return [['W'] * w] + [['GR'] * w for _ in range(h - 1)]
    if face == "up": return grid(w, h, 'W')
    if face == "east": return [['GR' if y == 0 else 'G2' for _ in range(w)] for y in range(h)]
    if face == "west": return [['GM' if y == 0 else 'G2' for _ in range(w)] for y in range(h)]
    return grid(w, h, 'G2')


def bolt(name, face, w, h):
    out = "east" if name.endswith("_r") else "west"
    if face == out:                                            # 3 x 2 grille: steel bars, dark slots
        return [['W', 'I0', 'GR'], ['GR', 'I1', 'G2']]
    if face == "up": return grid(w, h, 'W')
    if face == "down": return grid(w, h, 'G2')
    return grid(w, h, 'GR')


def shell(face, w, h):
    if face == "north":                                    # shows round the band and beside the snout now
        return plate(w, h, top='H', left='L', right='S', bottom='S', base='B', marks=('M',), n=0)
    if face == "down": return grid(w, h, 'I1')
    if face == "up":                                       # the dome: lit front-left corner, like the pads' tops
        g = plate(w, h, top='L', left='L', right='S', bottom='S', base='B', marks=('M', 'M', 'S'))
        g[0][0] = 'H2'
        return g
    return blue(face, w, h)


def painter(name, face, w, h):
    if name == "helmet_shell": return shell(face, w, h)
    if name == "visor": return visor_face(face, w, h)
    if name == "chin_guard": return snout(face, w, h)
    if name.startswith("ear_"): return bolt(name, face, w, h)
    if name == "helmet_shell_fb": return shell(face, w, h)
    if name in ("helmet_cap", "helmet_side_r", "helmet_side_l", "helmet_back"):
        if name == "helmet_cap" and face == "up":
            g = plate(w, h, top='L', left='L', right='S', bottom='S', base='B', marks=('M', 'M', 'S'))
            g[0][0] = 'H2'
            return g
        return blue(face, w, h)
    return frame(name, face, w, h)


def main():
    src = json.load(open(SRC))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    rest = [e for e in src["model"]["elements"] if e["name"] not in CUBES and e["name"] not in REMOVE]
    used = used_mask(dict(src["model"], elements=rest), tex)
    # the rebuilt cubes' own current faces are free to repaint (pixels no other cube's UV touches)
    blank = Image.new("RGBA", tex.size, (0, 0, 0, 0))
    others = used_mask(dict(src["model"], elements=rest), blank)
    own = used_mask(dict(src["model"], elements=[e for e in src["model"]["elements"]
                                                 if e["name"] in CUBES or e["name"] in REMOVE]), blank)
    for y in range(tex.height):
        for x in range(tex.width):
            if own[y][x] and not others[y][x]: used[y][x] = False
    faces, rects = {}, []
    for name, (f, t) in CUBES.items():
        for face in ("north", "east", "south", "west", "up", "down"):
            if (name, face) in OPEN: continue
            fw, fh = face_dims(f, t, face)
            pw, ph = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
            uw = PAINT_W.get((name, face))                     # a pattern stretched over a wider face
            if uw: pw = uw
            g = painter(name, face, pw, ph)
            assert len(g) == ph and all(len(r) == pw for r in g), (name, face, pw, ph, len(g), len(g[0]))
            faces[(name, face)] = (fw, fh, g)
            rects.append(((name, face), pw, ph))
    at = pack(used, rects)
    plan = {"cubes": {n: {"from": f, "to": t, "faces": {}} for n, (f, t) in CUBES.items()}, "pixels": [], "origin": HEAD,
            "bone": "helmet", "overwrite": True, "remove": REMOVE}
    for name, face in OPEN: plan["cubes"][name]["faces"][face] = None
    for (name, face), (fw, fh, g) in faces.items():
        u, v = at[(name, face)]
        uw = PAINT_W.get((name, face))
        plan["cubes"][name]["faces"][face] = [u, v, u + (uw or fw), v + fh]
        plan["pixels"] += [[u + x, v + y, C[k]] for y, row in enumerate(g) for x, k in enumerate(row)]
    json.dump(plan, open(OUT, "w"))
    print("cubes", len(CUBES), "pixels", len(plan["pixels"]))


if __name__ == "__main__":
    main()
