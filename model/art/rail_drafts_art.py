"""Rail cannon drafts (2026-10-26, Phase 11). Hidden proto_rail_* groups under left_forearm, like the other tools: the
standard 7x1x7 socket base (x -15..-8, z -5..2, y 19.625..20.625) and the tool hanging from it (the muzzle points down at
rest; raised, the front (-z) faces up, the back (+z) down; -x is the suit's outer side). Simple boxes, detail painted.
User's pick of inspiration: DOOM (2016)'s gauss cannon (.scratch/rail/doom_gauss_ref.png): two long steel rails with
a glowing blue core between them, a receiver behind with copper coil bands. 12 px long (the chaingun is 9, the
flamethrower 10): the long gun of the set.

  A doom      the gauss cannon: rails stacked top/bottom (raised), the core seen from the sides, copper bands on the back
  B split     the same turned a quarter: rails left/right, the core seen from above (the cockpit) and from behind
  C shroud    one closed steel shroud, the core glowing through long windows painted on its sides, two short prongs out front
  D full      A plus DOOM's extras: a carry handle on the top rail and a capacitor tube on the outer side

The core is painted in new reserved glow colours (E/F/H: dark blue glass when idle; in game a glowmask lights it while
charging). The plan's "lit" texels give the charged look for review renders.

    node .agents/skills/blockbench-mcp/scripts/snapshot.mjs .scratch/rail/source.json
    uv run --with pillow python3 model/art/rail_drafts_art.py   (writes .scratch/rail/plan.json; apply: .scratch/flame/apply.js
    with {plan: ".scratch/rail/plan.json"}; review: .scratch/flame/look.js with {group, out})
"""
import json, sys
sys.path.insert(0, "model/art")
import backpack_kit
from backpack_kit import on, build, PAL, MATERIALS, grid
import flame_drafts_art as fl
backpack_kit.GLOW_LIT.clear()   # (the flamethrower's module reserves its pilot light's blues: none here)
from helmet_drafts2_art import face_point

SRC, OUT = ".scratch/rail/source.json", ".scratch/rail/plan.json"
fl.SRC = SRC
ORIGIN = [-11.5, 25.625, -1.5]
BASE = 19.625   # underside of the slate base
TIP = BASE - 12

# slate (the tool bases' ramp): S lit, I hi, U fill, M base, D shade, V gap; core glass (reserved): E edge, F body, H hot
PAL.update(I='5d666b', U='434b4f', V='1f2326', E='16304a', F='1d3f5e', H='254e73')
backpack_kit.GLOW_LIT.update(E='1a5cc8', F='2f9cff', H='a8f0ff')


def wf(fn):
    """Face overrides painted in model coordinates: fn(face, x, y, z) -> key or '.' (keeps the material). A down face's
    rows run front to back mirrored (AGENTS.md), hence the flip."""
    def at(face, f, t, c, r):
        x, y, z = face_point(face, f, t, c, r)
        return (x, y, f[2] + t[2] - z) if face == "down" else (x, y, z)

    def for_face(face):
        return lambda w, h, f, t: [''.join(fn(face, *at(face, f, t, c, r)) for c in range(w)) for r in range(h)]
    return {k: for_face(k) for k in ("north", "south", "east", "west", "up", "down")}


def first(*fns):
    def p(face, x, y, z):
        for fn in fns:
            k = fn(face, x, y, z)
            if k != '.': return k
        return '.'
    return p


def slate(face, w, h, f, t):
    if face == "down": return grid(w, h, 'M')
    if face == "up": return [['I' if x == 0 else 'D' if x == w - 1 else 'U' for x in range(w)] for _ in range(h)]
    g = [['I'] + ['U'] * (w - 2) + ['D'] if w > 1 else ['U'] for _ in range(h)]
    g[0] = ['S'] * w
    if h > 2: g[h - 1] = ['D'] * w
    return g


def core(face, w, h, f, t):
    """Blue core glass: darker edge columns, the middle in rings (every other row hot), like DOOM's banded core."""
    if face in ("up", "down"): return grid(w, h, 'F')
    g = []
    for y in range(h):
        mid = 'H' if y % 2 == 0 else 'F'
        g.append(['E' if (x == 0 or x == w - 1) and w > 2 else mid for x in range(w)])
    return g


def copper(face, w, h, f, t):
    """A copper coil band: lit top row, winding lines, shaded bottom."""
    if face in ("up", "down"): return grid(w, h, 'c')
    g = [['C' if (x + y) % 3 else 'c' for x in range(w)] for y in range(h)]
    g[0] = ['k'] * w
    if h > 1: g[h - 1] = ['c'] * w
    return g


MATERIALS.update(slate=slate, core=core, copper=copper)


def vents(axis, at, y0, y1, step=2):
    """Dark vent slots along a rail's side (DOOM's lower rail): every `step` rows between y0 and y1, on faces facing
    `axis` ('x': east/west, 'z': north/south) in their middle third."""
    def p(face, x, y, z):
        if (face in ("east", "west")) != (axis == 'x'): return '.'
        if face in ("up", "down") or not (y0 < y < y1): return '.'
        if int(y - .5 - TIP) % step: return '.'
        c = z if axis == 'x' else x
        return 'K' if abs(c - at) < 1.1 else '.'
    return p


def muzzle_end(cx, cz, r=1.0):
    """The rail's front end: dark, with a lit rim."""
    def p(face, x, y, z):
        if face != "down": return '.'
        return 'K' if abs(x - cx) < r and abs(z - cz) < r else 'd'
    return p


BASE_CUBE = lambda: ([-15, BASE, -5], [-8, BASE + 1, 2], fl.socket_base())
CX, CZ = -11.5, -1.5


def bands(pre, ys, x0=-14.25, x1=-8.75, z0=-5.25, z1=2.25):
    """Copper coil bands round the receiver (0.25 proud of everything they wrap: no shared planes)."""
    return {f"{pre}_band_{i}": ([x0, y, z0], [x1, y + 1, z1], on('copper')) for i, y in enumerate(ys)}


def doom(pre):
    """A: rails top (-z) and bottom (+z), 5 wide, 2 deep, a 3 px gap with the core in it from the receiver to 1 px short
    of the muzzle; the top rail 1 px longer (DOOM's); receiver 5 px filling the gap behind; two copper bands."""
    rail = on('steel', **wf(first(vents('x', -1, TIP + 1, BASE - 5), muzzle_end(CX, 0, 1.5))))
    return {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_rail_top": ([-14, TIP, -5], [-9, BASE, -3], on('steel', **wf(muzzle_end(CX, -4, 1.5)))),
        f"{pre}_rail_bot": ([-14, TIP + 1, 0], [-9, BASE, 2], rail),
        f"{pre}_receiver": ([-13.9, BASE - 5, -3.05], [-9.1, BASE, .05], on('slate')),
        f"{pre}_core": ([-12.5, TIP + 1.5, -2.5], [-10.5, BASE - 5, -.5], on('core')),
        **bands(pre, [BASE - 2.5, BASE - 4.5]),
    }


def split(pre):
    """B: rails left (-x) and right (+x), 2 wide, 5 deep, a 3 px gap, the core 2x4 in it, receiver behind, bands."""
    return {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_rail_out": ([-15, TIP, -4], [-13, BASE, 1], on('steel', **wf(first(vents('z', -14, TIP + 1, BASE - 5),
                                                                                       muzzle_end(-14, CZ, 0))))),
        f"{pre}_rail_in": ([-10, TIP, -4], [-8, BASE, 1], on('steel', **wf(first(vents('z', -9, TIP + 1, BASE - 5),
                                                                                    muzzle_end(-9, CZ, 0))))),
        f"{pre}_receiver": ([-13.05, BASE - 5, -3.9], [-9.95, BASE, .9], on('slate')),
        f"{pre}_core": ([-12.5, TIP + 1.5, -3.5], [-10.5, BASE - 5, .5], on('core')),
        **bands(pre, [BASE - 2.5, BASE - 4.5], -15.25, -7.75, -4.25, 1.25),
    }


def window(face, x, y, z):
    """C: the core showing through a long window on each side (east/west) and on top (north), y TIP+2 .. BASE-4."""
    if not (TIP + 2 < y < BASE - 4): return '.'
    if face in ("east", "west") and abs(z - CZ) < 1.6:
        return 'E' if abs(z - CZ) > 1 else 'H' if int(y - TIP) % 2 == 0 else 'F'
    if face == "north" and abs(x - CX) < 1.1:
        return 'E' if abs(x - CX) > .5 else 'H' if int(y - TIP) % 2 == 0 else 'F'
    return '.'


def shroud(pre):
    """C: a 5x6 steel shroud from the base to 2 px short of the tip, two 1 px prongs on top and bottom beyond it."""
    return {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_shroud": ([-14, TIP + 2, -4.5], [-9, BASE, 1.5], on('steel', **wf(first(window, muzzle_end(CX, CZ, 1.2))))),
        f"{pre}_prong_top": ([-13, TIP, -4.5], [-10, TIP + 2.05, -3.5], on('steel')),
        f"{pre}_prong_bot": ([-13, TIP, .5], [-10, TIP + 2.05, 1.5], on('steel')),
        **bands(pre, [BASE - 2.5], -14.25, -8.75, -4.75, 1.75),
    }


def capacitor(face, x, y, z):
    """D: the capacitor tube's side, copper rings at its ends, a glowing gauge in the middle (outer face)."""
    if face == "west" and abs(y - (BASE - 3.5)) < 1 and abs(z - CZ) < .6: return 'H'
    return '.'


def full(pre):
    """D: A + a carry handle on the top rail (two posts and a bar, DOOM's) and a capacitor tube on the outer side."""
    d = doom(pre)
    d.update({
        f"{pre}_handle_post_a": ([-12, BASE - 3, -6], [-11, BASE - 2, -4.95], on('slate')),
        f"{pre}_handle_post_b": ([-12, BASE - 8, -6], [-11, BASE - 7, -4.95], on('slate')),
        f"{pre}_handle_bar": ([-12.25, BASE - 8.25, -7], [-10.75, BASE - 1.75, -6], on('slate')),
        f"{pre}_cap_tube": ([-15.5, BASE - 7, -2.5], [-14, BASE - .1, -.5], on('steel', **wf(capacitor))),
        f"{pre}_cap_ring_a": ([-15.75, BASE - 1.5, -2.75], [-14.05, BASE - .5, -.25], on('copper')),
        f"{pre}_cap_ring_b": ([-15.75, BASE - 7.25, -2.75], [-14.05, BASE - 6.25, -.25], on('copper')),
    })
    return d


DRAFTS = {"proto_rail_A_doom": doom("pkRA"), "proto_rail_B_split": split("pkRB"),
          "proto_rail_C_shroud": shroud("pkRC"), "proto_rail_D_full": full("pkRD")}

if __name__ == "__main__":
    plan = build(SRC, DRAFTS, "left_forearm", ORIGIN, "before rail cannon drafts")
    json.dump(plan, open(OUT, "w"))
    lit = dict(plan, pixels=[p for p in plan["pixels"]])
    litmap = {(x, y): c for x, y, c in plan["lit"]}
    lit["pixels"] = [[x, y, litmap.get((x, y), c)] for x, y, c in plan["pixels"]]
    lit["checkpoint"] = "rail drafts: lit preview"
    json.dump(lit, open(OUT.replace(".json", "_lit.json"), "w"))
    print("cubes", sum(len(c) for c in DRAFTS.values()), "pixels", len(plan["pixels"]), "lit", len(plan["lit"]))
