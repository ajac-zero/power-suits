"""TNT launcher drafts (2026-10-25, Phase 11: the "grenade launcher"). Hidden proto_tnt_* groups under left_forearm, like
the other tools: the standard 7x1x7 socket base (x -15..-8, z -5..2, y 19.625..20.625) and the tool hanging from it to
y ~9.6 (the muzzle points down at rest; the arm raises it when used, then the front (-z) faces up and the back (+z) down).
User's pick: a revolver drum, Team Fortress 2's grenade launcher as the inspiration (all metal here, no wooden stock).
Like a revolver, the barrel lines up with the top chamber (-z), so the drum's axis sits behind it (the socket's centre).
The rounds are primed TNT: red with a white label band. Simple boxes, detail painted.

  A demo      TF2's: a solid octagonal drum, fluted sides, red chamber ends round the fat barrel, a TNT round showing in
              a loading slot on the back, a front sight on the muzzle
  B cage      an open cylinder: four mini TNT rounds between a rear and a front plate round an axle (see the TNT)
  C loaded    A's drum with a TNT round sitting in each of its four corner notches (red from every side)
  D wheel     the wildcard: a drum magazine on the outer side (a wheel, six TNT ends on its face) feeding a long barrel

    node .agents/skills/blockbench-mcp/scripts/snapshot.mjs .scratch/tnt/source.json
    uv run --with pillow python3 model/art/tnt_drafts_art.py   (writes .scratch/tnt/plan.json; apply: .scratch/flame/apply.js
    with {plan: ".scratch/tnt/plan.json"}; review: .scratch/flame/look.js with {group, out})
"""
import json, sys
sys.path.insert(0, "model/art")
import backpack_kit
from backpack_kit import on, build, PAL, MATERIALS, grid
import flame_drafts_art as fl
backpack_kit.GLOW_LIT.clear()   # (the flamethrower's module reserves its pilot light's blues: none here)
from helmet_drafts2_art import face_point

SRC, OUT = ".scratch/tnt/source.json", ".scratch/tnt/plan.json"
fl.SRC = SRC
ORIGIN = [-11.5, 25.625, -1.5]
BASE = 19.625   # underside of the slate base

# TNT: i shade, j red, l lit red, m label white, n label letters, s end (tan grey), x fuse hole
# slate (the suit fist's ramp, fist_slate_art.py): S lit, I hi, L mid, U fill, M base, D shade, V gap
PAL.update(i='6e1e14', j='a3301e', l='d2442a', m='e6e2d6', n='3a2f2a', s='a69c8c', x='5a5248',
           I='5d666b', U='434b4f', V='1f2326')


def wf(fn):
    """Face overrides painted in model coordinates: fn(face, x, y, z) -> key or '.' (keeps the material)."""
    def for_face(face):
        return lambda w, h, f, t: [''.join(fn(face, *face_point(face, f, t, c, r)) for c in range(w)) for r in range(h)]
    return {k: for_face(k) for k in ("north", "south", "east", "west", "up", "down")}


def slate(face, w, h, f, t):
    """Gunmetal slate like the tool bases: lit top row and left edge, shaded right edge and bottom row."""
    if face == "down": return grid(w, h, 'M')
    if face == "up": return [['I' if x == 0 else 'D' if x == w - 1 else 'U' for x in range(w)] for _ in range(h)]
    g = [['I'] + ['U'] * (w - 2) + ['D'] if w > 1 else ['U'] for _ in range(h)]
    g[0] = ['S'] * w
    if h > 2: g[h - 1] = ['D'] * w
    return g


def tnt(face, w, h, f, t):
    """A TNT round: red with a white label band round its middle (dark letter marks), tan ends with a fuse hole."""
    if face in ("up", "down"):
        g = grid(w, h, 's')
        g[h // 2][w // 2] = 'x'
        return g
    g = grid(w, h, 'j')
    for x in range(w): g[0][x] = 'l'; g[h - 1][x] = 'i'
    m = h // 2
    for x in range(w):                       # (letters only on faces wide enough: on 2 px two dots read as eyes)
        g[m - 1][x] = 'm'
        g[m][x] = 'n' if w > 2 and x % 2 == 1 and 0 < x < w - 1 else 'm'
    return g


MATERIALS.update(slate=slate, tnt=tnt)


def octagon(pre, y0, y1, painter, x0=-15, x1=-8, z0=-5, z1=2):
    """A blocky round drum: two crossed boxes (the octagon of the drum hips). The narrow one is 0.1 shorter at both ends
    so their end faces aren't coplanar (both faces are drawn: they would flicker)."""
    return {f"{pre}_drum_a": ([x0, y0, z0 + 1], [x1, y1, z1 - 1], painter),
            f"{pre}_drum_b": ([x0 + 1, y0 + .1, z0], [x1 - 1, y1 - .1, z1], painter)}


def chambers(cs, r=1.0):
    """Paints the red TNT ends of the rounds in their chambers on a drum's muzzle end (down face): cs = [(x, z)]."""
    def p(face, x, y, z):
        if face != "down": return '.'
        for cx, cz in cs:
            if abs(x - cx) < r and abs(z - cz) < r:
                return 'l' if x < cx and z < cz else 'i' if x > cx and z > cz else 'j'
        return '.'
    return p


def flutes(face, x, y, z):
    """Dark flutes along the drum between the chambers (sides), on the slate."""
    if face in ("east", "west") and abs(z + 1.5) < .5: return 'V'
    if face in ("north", "south") and abs(x + 11.5) < .5: return 'V'
    return '.'


def barrel(pre, cx, cz, size, y0, y1, ring=True):
    """A fat barrel down to a muzzle ring (0.5 px wider) with the bore painted dark on its end."""
    h = size / 2
    d = {f"{pre}_barrel": ([cx - h, y0 + .5, cz - h], [cx + h, y1, cz + h], on('steel'))}
    if ring:
        def bore(face, x, y, z):
            if face != "down": return '.'
            r = max(abs(x - cx), abs(z - cz))
            return 'K' if r < h - .8 else 'd' if r < h - .3 else '.'
        d[f"{pre}_muzzle"] = ([cx - h - .25, y0, cz - h - .25], [cx + h + .25, y0 + 1, cz + h + .25], on('slate', **wf(bore)))
    return d


BASE_CUBE = lambda: ([-15, BASE, -5], [-8, BASE + 1, 2], fl.socket_base())
CX, CZ = -11.5, -1.5                    # the drum's axis = the socket's
TOP = (CX, CZ - 2)                      # the chamber in line with the barrel (front)
CH = [(CX - 2, CZ), (CX + 2, CZ), (CX, CZ + 2)]   # the other three


def slot(face, x, y, z):
    """A loading slot on the drum's back showing the back chamber's TNT round (white band across its middle)."""
    if face != "south" or abs(x - CX) > 1 or not (15.625 < y < 18.625): return '.'
    return 'i' if y < 16.625 else 'm' if y < 17.625 else 'l'


def first(*fns):
    """The first painter that paints a texel wins."""
    def p(face, x, y, z):
        for fn in fns:
            k = fn(face, x, y, z)
            if k != '.': return k
        return '.'
    return p


A = {   # TF2: drum 14.625-19.625, barrel 4x4 on the top chamber down to 9.625, sight on the muzzle
    "pkTA_base": BASE_CUBE(),
    **octagon("pkTA", 14.625, BASE, on('slate', **wf(first(chambers(CH), slot, flutes)))),
    **barrel("pkTA", TOP[0], TOP[1], 4, 9.625, 15.125),
    "pkTA_sight": ([-12, 10.625, -6.25], [-11, 12.625, -5.5], on('slate')),
}

B = {   # cage: rear plate 18.625-19.625, front plate 13.625-14.625, axle, four rounds, barrel 3x3 + ring
    "pkTB_base": BASE_CUBE(),
    **{k.replace("drum", "rear"): v for k, v in octagon("pkTB", 18.625, BASE, on('slate')).items()},
    **{k.replace("drum", "front"): v for k, v in octagon("pkTB", 13.625, 14.625, on('slate', **wf(chambers(CH)))).items()},
    "pkTB_axle": ([CX - .5, 14.625, CZ - .5], [CX + .5, 18.625, CZ + .5], on('steel')),
    **{f"pkTB_round_{i}": ([cx - 1, 14.575, cz - 1], [cx + 1, 18.675, cz + 1], on('tnt'))
       for i, (cx, cz) in enumerate([TOP] + CH)},
    **barrel("pkTB", TOP[0], TOP[1], 3, 9.625, 13.675),
    "pkTB_rod": ([CX - .5, 10.625, CZ - .5], [CX + .5, 13.675, CZ + .5], on('steel')),
}

C = {   # loaded: A's drum, a round in each corner notch (x, z at the octagon's corners), barrel + sight
    "pkTC_base": BASE_CUBE(),
    **octagon("pkTC", 14.625, BASE, on('slate', **wf(chambers(CH)))),
    **{f"pkTC_round_{i}": ([x - .8, 15.125, z - .8], [x + .8, 19.125, z + .8], on('tnt'))   # 0.05 proud of the drum's faces (no flicker)
       for i, (x, z) in enumerate([(-14.25, -4.25), (-8.75, -4.25), (-14.25, 1.25), (-8.75, 1.25)])},
    **barrel("pkTC", TOP[0], TOP[1], 4, 9.625, 15.125),
    "pkTC_sight": ([-12, 10.625, -6.25], [-11, 12.625, -5.5], on('slate')),
}


def wheel_face(face, x, y, z):
    """The drum magazine's outer face: six TNT ends round a steel hub, a lit rim."""
    if face != "west": return '.'
    cy, cz = 16.125, -1.5
    if abs(y - cy) < 1 and abs(z - cz) < 1: return 'G'
    for k, (dy, dz) in enumerate([(2.2, 0), (1.1, 2), (-1.1, 2), (-2.2, 0), (-1.1, -2), (1.1, -2)]):
        if abs(y - cy - dy) < .8 and abs(z - cz - dz) < .8: return 'l' if y > cy + dy else 'j'
    return '.'


D = {   # wheel: a 2.5 px drum wheel on the outer side (axis across the arm), barrel 3x3 on the inner side + ring
    "pkTD_base": BASE_CUBE(),
    "pkTD_wheel_a": ([-15, 13.625, -5], [-12.5, 18.625, 2], on('slate', **wf(wheel_face))),
    "pkTD_wheel_b": ([-14.9, 12.625, -4], [-12.6, 19.625, 1], on('slate', **wf(wheel_face))),
    "pkTD_hub": ([-12.5, 15.125, -2.5], [-11.5, 17.125, -.5], on('steel')),
    "pkTD_feed": ([-11.4, 16.625, -3], [-9, BASE, 0], on('slate')),
    **barrel("pkTD", -10, -3.5, 3, 9.625, 16.675),
    "pkTD_sight": ([-10.5, 10.625, -5.75], [-9.5, 12.125, -5], on('slate')),
}

DRAFTS = {"proto_tnt_A_demo": A, "proto_tnt_B_cage": B, "proto_tnt_C_loaded": C, "proto_tnt_D_wheel": D}

if __name__ == "__main__":
    plan = build(SRC, DRAFTS, "left_forearm", ORIGIN, "before TNT launcher drafts")
    json.dump(plan, open(OUT, "w"))
    print("cubes", sum(len(c) for c in DRAFTS.values()), "pixels", len(plan["pixels"]))
