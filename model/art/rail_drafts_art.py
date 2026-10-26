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


_SOCKET = []
def BASE_CUBE():
    """The standard socket base (one shared painter: each socket_base() call claims new palette keys)."""
    if not _SOCKET: _SOCKET.append(fl.socket_base())
    return ([-15, BASE, -5], [-8, BASE + 1, 2], _SOCKET[0])
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


ROUND1 = {"proto_rail_A_doom": doom("pkRA"), "proto_rail_B_split": split("pkRB"),
          "proto_rail_C_shroud": shroud("pkRC"), "proto_rail_D_full": full("pkRD")}

# round 2 (user: "I prefer B, but not 100%. Let's try more variations of that horizontal two-prong style"). All keep the
# prongs left and right with the core between them, seen from above (the cockpit) and below (the back view at rest).


def prong(x0, x1, z0, z1, y0, y1=BASE, inner=None):
    """A steel prong: vents on its outer side, a dark muzzle end; `inner` = 'x' of its face toward the core, painted
    with a lit copper contact strip along it (the rail the slug rides)."""
    out = x0 if inner == x1 else x1
    def strip(face, x, y, z):
        if inner is None or face not in ("east", "west") or abs(x - inner) > .01: return '.'
        return 'k' if abs(z - (z0 + z1) / 2) < .6 and y < BASE - 2.5 else '.'
    return on('steel', **wf(first(strip, vents('z', (x0 + x1) / 2, y0 + 1, BASE - 5), muzzle_end((x0 + x1) / 2, (z0 + z1) / 2, 0))))


def long_core(pre):
    """B1: B with a 2 px receiver, so the core runs 9 px (most of the gun, like DOOM's); one copper band."""
    return {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_rail_out": ([-15, TIP, -4], [-13, BASE, 1], prong(-15, -13, -4, 1, TIP, inner=-13)),
        f"{pre}_rail_in": ([-10, TIP, -4], [-8, BASE, 1], prong(-10, -8, -4, 1, TIP, inner=-10)),
        f"{pre}_receiver": ([-13.05, BASE - 2, -3.9], [-9.95, BASE, .9], on('slate')),
        f"{pre}_core": ([-12.5, TIP + 1, -3.5], [-10.5, BASE - 2, .5], on('core')),
        **bands(pre, [BASE - 1.5], -15.25, -7.75, -4.25, 1.25),
    }


def fork(pre):
    """B2: tapered prongs like a tuning fork: a thick 2.5 x 6 root (y BASE-5..BASE) stepping down to a 1.5 x 4 prong
    out to the tip (the step on the outer side and top/bottom; the inner faces stay one plane along the slot)."""
    return {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_root_out": ([-15.5, BASE - 5, -4.5], [-13, BASE, 1.5], on('steel')),
        f"{pre}_root_in": ([-10, BASE - 5, -4.5], [-7.5, BASE, 1.5], on('steel')),
        f"{pre}_prong_out": ([-14.5, TIP, -3.5], [-13.05, BASE - 4.95, .5], prong(-14.5, -13, -3.5, .5, TIP, inner=-13.05)),
        f"{pre}_prong_in": ([-9.95, TIP, -3.5], [-8.5, BASE - 4.95, .5], prong(-10, -8.5, -3.5, .5, TIP, inner=-9.95)),
        f"{pre}_receiver": ([-13.1, BASE - 5, -3.9], [-9.9, BASE, .9], on('slate')),
        f"{pre}_core": ([-12.5, TIP + 1, -2.5], [-10.5, BASE - 5, -.5], on('core')),
        **bands(pre, [BASE - 2.5], -15.75, -7.25, -4.75, 1.75),
    }


def coils(pre):
    """B3: coil-gun prongs: each prong wrapped in its own three copper coils (no band round the whole), a long core."""
    d = {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_rail_out": ([-15, TIP, -4], [-13, BASE, 1], prong(-15, -13, -4, 1, TIP)),
        f"{pre}_rail_in": ([-10, TIP, -4], [-8, BASE, 1], prong(-10, -8, -4, 1, TIP)),
        f"{pre}_receiver": ([-13.05, BASE - 2, -3.9], [-9.95, BASE, .9], on('slate')),
        f"{pre}_core": ([-12.5, TIP + 1, -3.5], [-10.5, BASE - 2, .5], on('core')),
    }
    for i, y in enumerate([BASE - 3, BASE - 6, BASE - 9]):
        d[f"{pre}_coil_out_{i}"] = ([-15.25, y - 1, -4.25], [-12.95, y, 1.25], on('copper'))
        d[f"{pre}_coil_in_{i}"] = ([-10.05, y - 1, -4.25], [-7.75, y, 1.25], on('copper'))
    return d


def bridged(pre):
    """B4: B's prongs tied by two slate bridges across the top (cockpit side) and bottom, at the middle and near the
    muzzle: the core glows through three windows between them; the bridges' ends sit inside the prongs."""
    d = long_core(pre)
    for i, y in enumerate([BASE - 6, TIP + 1.5]):
        d[f"{pre}_bridge_top_{i}"] = ([-13.5, y - 1, -4.25], [-9.5, y, -3.25], on('slate'))
        d[f"{pre}_bridge_bot_{i}"] = ([-13.5, y - 1, .25], [-9.5, y, 1.25], on('slate'))
    return d


def wide(pre):
    """B5: a wider, heavier fork: prongs 2.5 x 6 set 4 px apart (9 px across, wider than the socket), a 3 px core, the
    prongs' inner faces lit by copper contact strips; a short receiver and one band."""
    return {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_rail_out": ([-16, TIP, -4.5], [-13.5, BASE, 1.5], prong(-16, -13.5, -4.5, 1.5, TIP, inner=-13.5)),
        f"{pre}_rail_in": ([-9.5, TIP, -4.5], [-7, BASE, 1.5], prong(-9.5, -7, -4.5, 1.5, TIP, inner=-9.5)),
        f"{pre}_receiver": ([-13.55, BASE - 3, -4.4], [-9.45, BASE, 1.4], on('slate')),
        f"{pre}_core": ([-13, TIP + 1, -3], [-10, BASE - 3, 0], on('core')),
        **bands(pre, [BASE - 2], -16.25, -6.75, -4.75, 1.75),
    }


ROUND2 = {"proto_rail_B_split": split("pkRB"), "proto_rail_B1_long": long_core("pkRB1"),
          "proto_rail_B2_fork": fork("pkRB2"), "proto_rail_B3_coils": coils("pkRB3"),
          "proto_rail_B4_bridged": bridged("pkRB4"), "proto_rail_B5_wide": wide("pkRB5")}
# (user picked B5: the game bone rail_cannon since 2026-10-26)

# round 3 (user, 2026-10-26: "I like wide, but let's experiment with making it flat", and the core should flow with
# energy as it charges, DOOM's): B5's width (9 px across) with the depth (front to back of the suit at rest, top to bottom
# raised) cut from 6 to 4, 3 or 2. The core is painted in rings of three reserved blues by position along it (H, m, n:
# ring r is colour r mod 3): in game three glowmasks light the rings in turn, so the light runs toward the muzzle (flow).
PAL.update(m='245079', n='26507b')
backpack_kit.GLOW_LIT.update(m='2f9cff', n='a8f0ff', H='e6fbff')


def core_flow(face, w, h, f, t):
    """The flowing core: ring rows across the sides by distance from the base (three phases), dark glass edge columns,
    the ends (up/down) plain body glass."""
    if face in ("up", "down"):
        return grid(w, h, 'F')
    g = []
    for y in range(h):
        r = int(BASE - (t[1] - y - 0.5))          # rows from the base end
        k = 'Hmn'[r % 3]
        g.append(['E' if (x == 0 or x == w - 1) and w > 2 else k for x in range(w)])
    return g


MATERIALS.update(core_flow=core_flow)


def flat(pre, depth, prong_w=2.5, core_w=3.0, core_depth=None, receiver=3, band=True):
    """B5 made flat: prongs prong_w wide and depth deep at the outer edges of a 9 px span (x -16..-7), centred on the
    socket's axis front to back; the core core_w wide between them (core_depth deep, default flush with the prongs: 0.1
    less so its faces never share a plane with theirs), a slate receiver at the base filling the gap, a copper band."""
    cd = depth - 0.1 if core_depth is None else core_depth
    z0, z1 = CZ - depth / 2, CZ + depth / 2
    x_out, x_in = -16 + prong_w, -7 - prong_w
    d = {
        f"{pre}_base": BASE_CUBE(),
        f"{pre}_prong_out": ([-16, TIP, z0], [x_out, BASE, z1], prong(-16, x_out, z0, z1, TIP, inner=x_out)),
        f"{pre}_prong_in": ([x_in, TIP, z0], [-7, BASE, z1], prong(x_in, -7, z0, z1, TIP, inner=x_in)),
        f"{pre}_receiver": ([x_out - .05, BASE - receiver, z0 + .1], [x_in + .05, BASE - .05, z1 - .1], on('slate')),
        f"{pre}_core": ([CX - core_w / 2, TIP + 1, CZ - cd / 2], [CX + core_w / 2, BASE - receiver, CZ + cd / 2], on('core_flow')),
    }
    if band:
        d.update(bands(pre, [BASE - 2], -16.25, -6.75, z0 - .25, z1 + .25))
    return d


ROUND3 = {"proto_rail_F4_flat4": flat("pkRF4", 4, core_depth=3),
          "proto_rail_F3_flat3": flat("pkRF3", 3),
          "proto_rail_F2_slab": flat("pkRF2", 2, prong_w=3),
          "proto_rail_F3o_open": flat("pkRF3o", 3, core_w=2)}
# (user picked F3: the game bone rail_cannon since 2026-10-26, prongs in rail_fork_out / rail_fork_in)

# round 4 (user, 2026-10-26: "add like one step at the tips so it looks pointier, and also add width to the sides of the
# rails that reduces as it gets to the tip: that large to small feeling"): F3 with stepped, tapering prongs. The inner
# faces (along the core) stay straight; the outer sides step in toward the muzzle, and the last pixel is a tip one
# pixel wide and a pixel shallower. Segments from the base: (bottom y, width).
SEGMENTS = [15.625, 11.625, 8.625, TIP + 1]   # bottoms of root, mid, front, then the tip down to TIP


def steel_step(end_dark=False, vents_at=None):
    """A prong segment: steel; vents across its top/bottom (z faces) on the root, a dark end only on the tip."""
    fns = []
    if vents_at is not None:
        fns.append(vents('z', vents_at, TIP + 1, BASE - 5))
    if end_dark:
        fns.append(lambda face, x, y, z: 'K' if face == "down" else '.')
    return on('steel', **wf(first(*fns))) if fns else on('steel')


def tapered(pre, widths, depth=3, tip_w=1.0, tip_inset=.5, band_pad=.25):
    """Two prongs of 4 segments each (widths root, mid, front; the tip tip_w wide and tip_inset shallower on both faces);
    the core, receiver and band as F3 (the band wraps the root)."""
    z0, z1 = CZ - depth / 2, CZ + depth / 2
    gap_out, gap_in = -13.5, -9.5          # the prongs' inner faces (the core slot), as F3
    d = {f"{pre}_base": BASE_CUBE()}
    tops = [BASE] + SEGMENTS[:-1]
    for i, (top, bottom) in enumerate(zip(tops, SEGMENTS + [TIP])):
        w = widths[i] if i < 3 else tip_w
        zi = tip_inset if i == 3 else 0
        tip = i == 3
        vx = (gap_out - w / 2) if i == 0 else None
        d[f"{pre}_prong_out_{i}"] = ([gap_out - w, bottom, z0 + zi], [gap_out, top, z1 - zi], steel_step(tip, vx))
        d[f"{pre}_prong_in_{i}"] = ([gap_in, bottom, z0 + zi], [gap_in + w, top, z1 - zi],
                                    steel_step(tip, None if vx is None else gap_in + w / 2))
    root = widths[0]
    d[f"{pre}_receiver"] = ([gap_out - .05, BASE - 3, z0 + .1], [gap_in + .05, BASE - .05, z1 - .1], on('slate'))
    d[f"{pre}_core"] = ([CX - 1.5, TIP + 1, CZ - (depth - .1) / 2], [CX + 1.5, BASE - 3, CZ + (depth - .1) / 2], on('core_flow'))
    d.update(bands(pre, [BASE - 2], gap_out - root - band_pad, gap_in + root + band_pad, z0 - .25, z1 + .25))
    return d


ROUND4 = {"proto_rail_T1_wide_base": tapered("pkRT1", [3.5, 2.5, 1.5]),
          "proto_rail_T2_taper": tapered("pkRT2", [2.5, 2.0, 1.5])}
DRAFTS = ROUND4   # (user picked T2: its prongs are the game's since 2026-10-26, rail_prong_{out,in}_0..3)
REMOVED = []

if __name__ == "__main__":
    plan = build(SRC, DRAFTS, "left_forearm", ORIGIN, "before rail cannon round 4 (tapered prongs)", own=("pk", "rail_"))
    # free the texels of the drafts dropped (painted, but no cube uses them any more)
    import base64, io
    from PIL import Image
    from backpack_kit import used_mask
    src = json.load(open(SRC)); model = src["model"]
    gs = {g["uuid"]: g for g in model["groups"]}
    def uuids(node, inside):
        out = set()
        for c in node:
            if isinstance(c, str):
                if inside: out.add(c)
            else: out |= uuids(c.get("children", []), inside or gs[c["uuid"]]["name"] in REMOVED)
        return out
    gone = uuids(model["outliner"], False)
    size = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).size
    mask = lambda els: used_mask(dict(model, elements=els), Image.new("RGBA", size, (0, 0, 0, 0)))
    o = mask([e for e in model["elements"] if e["uuid"] in gone])
    k = mask([e for e in model["elements"] if e["uuid"] not in gone and not e["name"].startswith("pkR")])
    new = {(x, y) for x, y, _ in plan["pixels"]}
    plan["clear"] = [[x, y] for y, row in enumerate(o) for x, v in enumerate(row) if v and not k[y][x] and (x, y) not in new]
    plan["remove"] = REMOVED
    json.dump(plan, open(OUT, "w"))
    lit = dict(plan, pixels=[p for p in plan["pixels"]])
    litmap = {(x, y): c for x, y, c in plan["lit"]}
    lit["pixels"] = [[x, y, litmap.get((x, y), c)] for x, y, c in plan["pixels"]]
    lit["checkpoint"] = "rail drafts: lit preview"
    json.dump(lit, open(OUT.replace(".json", "_lit.json"), "w"))
    print("cubes", sum(len(c) for c in DRAFTS.values()), "pixels", len(plan["pixels"]), "lit", len(plan["lit"]))
