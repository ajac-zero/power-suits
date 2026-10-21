"""Helmet drafts, round 2 (2026-10-20, user: "more drafts, but keep the dimensions the same this time").

(Round 1, five sketches of other sizes, was dropped by the user on 2026-10-21; the helpers it shared live here now.)
Every draft is the current helmet's envelope: the shell 9 x 9 x 9 (x +-4.5, y 38.95..47.5, z -4..5) and front pieces
inside the current band's reach (z -4.75, x +-4.75, top 47.75). The shell's sides, back and top reuse the current
helmet's UV (same paint), so only the face changes. Hidden groups under `head` named proto_helm_* (export-model.mjs leaves proto_* out).

  F porthole   a 7 x 7 round porthole, 0.75 proud
  G chief      (after Halo's Master Chief) a 1 px top band wrapping round the front and sides, the visor glass
               painted flat on the shell under it, round the sides too, its lower edge stepping up round the corners;
               a narrower mouthpiece with a ridge (v1 goggles: round lenses + gas-mask
               filter; v2: a raised gold visor, dropped: the visor glows yellow already)
  H T-visor    a flat faceplate with a T cut in it
  I slit       two face plates with a real 1 px gap between them, glass set back in it; a plain jaw (seam, rivets)
  J wrap       a glass band round the front and both sides, half way back; plain below
  K split      the current helmet with a centre bar: two panes; a plain jaw instead of the grill
  L bars       the current visor with three steel bars in front of the glass; a plain jaw
v2 (user: "too many of them share the mouth grill"): only the current helmet keeps the grill

    uv run --with pillow python3 model/art/helmet_drafts2_art.py   (reads .scratch/helmets/source2.json, a fresh
    snapshot of the live project; writes .scratch/helmets/plan2.json; apply: .scratch/helmets/apply.js {plan})
    ... helmet_drafts2_art.py _G_   rebuilds only the drafts whose group name contains _G_ (the others stay as they are)
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, plate, C, face_dims

SRC, OUT = ".scratch/helmets/source2.json", ".scratch/helmets/plan2.json"
# the suit's steel ramp under the blue keys sm_chest_art paints with; reserved visor-glass colours (they glow)
STEEL = dict(G='22262e', D='3c424c', S='58606a', B='757c85', M='8a9097', L='9fa4a9', H='cdcfcf', H2='fbf8f0')
C = dict(C, **STEEL, GM='89939a', VG='262c34', VE='1e232a', VH='46505c')
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


def glass(face, w, h):
    """Visor glass: dark, a highlight streak in the top-left, a darker bottom-right edge."""
    g = grid(w, h, 'VG')
    for y in range(h): g[y][w - 1] = 'VE'
    g[h - 1] = ['VE'] * w
    for i in range(min(w, h, 3)):
        if i < w - 1 and i < h - 1: g[i][0] = 'VH'
    if w > 2 and h > 1: g[0][1] = 'VH'
    if w > 3: g[0][2] = 'VH'
    return g


_P = {'.': 'B', 'o': 'H', 'W': 'W', 'R': 'GR', 'r': 'G2', 'g': 'VG', 'h': 'VH', 'e': 'VE'}
SHELL = ([-4.5, 38.95, -4], [4.5, 47.5, 5])
REUSE = "reuse"            # painter result: take the named current cube's face UV


def pattern(rows):
    return [[dict(_P, I='I0')[ch] for ch in r] for r in rows]


PORT7 = pattern(("o.WWR.o", ".Whggr.", "Whhgggr", "Whggggr", "Wgggger", ".Rgeer.", "o.rrr.o"))


def face_plate(w, h):
    """A flat steel faceplate (lighter than the shell, like the band)."""
    return plate(w, h, top='H', left='L', right='S', bottom='S', base='M', marks=('L',), n=0)


# name -> (from, to, painter); painter(face, w, h) returns a grid or (REUSE, cube name)
def shell_with(front):
    return (*SHELL, lambda f, w, h: front(w, h) if f == "north" else (REUSE, "helmet_shell"))


def plain_front(w, h): return shell("north", w, h)


def h_face(f, w, h):
    if f != "north": return trim(f, w, h)
    g = face_plate(w, h)
    for x in range(1, w - 1):
        g[2][x] = 'VH' if x <= 2 else 'VG'; g[3][x] = 'VE'
    for y in range(4, h - 2):
        g[y][3] = 'VG'; g[y][4] = 'VE'
    for y in (6, 7):
        g[y][1] = 'I0'; g[y][w - 2] = 'I0'
    return g


def i_plate(lower):
    def p(f, w, h):
        if f != "north": return trim(f, w, h)
        g = face_plate(w, h)
        if lower:                                         # a plain jaw: a centre seam, a rivet each side
            m = w // 2
            for y in range(1, h - 1): g[y][m] = 'S'
            g[h // 2][1] = g[h // 2][w - 2] = 'H'
        return g
    return p


def k_bar(f, w, h):
    g = trim(f, w, h)
    if f == "north": g = [['W' if y == 0 else 'GR'] for y in range(h)]
    return g


def bar(f, w, h):
    """A steel bar standing in front of the visor."""
    if f == "north": return [['W']] + [['GR']] * (h - 2) + [['G2']]
    return trim(f, w, h)


def jaw(f, w, h):
    """A plain steel jaw block (no grill): lit top row, a rivet at each end."""
    g = trim(f, w, h)
    if f == "north" and w > 4: g[h - 1][1] = g[h - 1][w - 2] = 'H'
    return g


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


def reuse(name): return lambda f, w, h: (REUSE, name)


DRAFTS = {
    "proto_helm_F_porthole": {
        "hF_shell": shell_with(plain_front),
        "hF_port": ([-3.5, 40, -4.75], [3.5, 47, -4], lambda f, w, h: [r[:] for r in PORT7] if f == "north" else trim(f, w, h))},
    # v2 (user: rework the green one, "more of a master chief reference look"): a forehead plate coming forward over
    # one wide gold visor that wraps round the corners, a narrower mouthpiece under it with a ridge and two vents
    "proto_helm_G_chief": {
        "hG_shell": (*SHELL, chief_shell),
        # v3 (user): no raised gold visor; the top wraps round the front and sides, the glowing visor under it
        # v6: the top a 1 px band (y 44.5..45.5); v7: the visor flat, painted on the shell (chief_shell)
        "hG_brow": ([-4.75, 44.5, -4.75], [4.75, 45.5, -2], chief_brow),
        "hG_jaw": ([-2.5, 38.95, -4.75], [2.5, 41.75, -4], chief_jaw)},
    "proto_helm_H_tvisor": {
        "hH_shell": shell_with(plain_front),
        "hH_face": ([-4, 38.95, -4.75], [4, 47.5, -4], h_face)},
    "proto_helm_I_slit": {
        "hI_shell": shell_with(plain_front),
        "hI_top": ([-4.5, 44.5, -4.75], [4.5, 47.75, -4], i_plate(False)),
        "hI_bottom": ([-4.5, 38.95, -4.75], [4.5, 43.5, -4], i_plate(True)),
        "hI_glass": ([-3.5, 43.5, -4.25], [3.5, 44.5, -4], lambda f, w, h: glass(f, w, h) if f == "north" else grid(w, h, 'I1'))},
    "proto_helm_J_wrap": {
        "hJ_shell": shell_with(plain_front),                             # plain below the glass
        "hJ_band": ([-4.75, 42, -4.75], [4.75, 45, 0.5],
                    lambda f, w, h: glass(f, w, h) if f in ("north", "east", "west") else grid(w, h, 'I1'))},
    "proto_helm_K_split": {
        "hK_shell": (*SHELL, reuse("helmet_shell")),
        "hK_brow": ([-3.5, 46, -4.75], [3.5, 47.75, -2.75], reuse("helmet_brow")),
        "hK_cheek_r": ([3.5, 41, -4.75], [4.75, 46, -2.75], reuse("cheek_r")),
        "hK_cheek_l": ([-4.75, 41, -4.75], [-3.5, 46, -2.75], reuse("cheek_l")),
        "hK_visor": ([-3.5, 41, -4.5], [3.5, 46, -4], reuse("visor")),
        "hK_chin": ([-3.5, 39, -4.75], [3.5, 41, -4], jaw),               # a plain jaw, not the grill
        "hK_bar": ([-0.5, 41, -4.75], [0.5, 46, -4.5], k_bar)},
    "proto_helm_L_cage": {
        "hL_shell": shell_with(plain_front),
        "hL_brow": ([-3.5, 46, -4.75], [3.5, 47.75, -2.75], reuse("helmet_brow")),
        "hL_cheek_r": ([3.5, 41, -4.75], [4.75, 46, -2.75], reuse("cheek_r")),
        "hL_cheek_l": ([-4.75, 41, -4.75], [-3.5, 46, -2.75], reuse("cheek_l")),
        "hL_visor": ([-3.5, 41, -4.5], [3.5, 46, -4], reuse("visor")),
        "hL_bar_r": ([1.5, 41, -4.75], [2.5, 46, -4.5], bar),             # three bars in front of the glass
        "hL_bar_m": ([-0.5, 41, -4.75], [0.5, 46, -4.5], bar),
        "hL_bar_l": ([-2.5, 41, -4.75], [-1.5, 46, -4.5], bar),
        "hL_jaw": ([-3.5, 38.95, -4.75], [3.5, 41, -4], jaw)},
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
        for name, (f, t, paint) in cubes.items():
            spec = plan["groups"][group][name] = {"from": f, "to": t, "faces": {}}
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
