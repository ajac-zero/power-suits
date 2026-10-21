"""Helmet drafts, round 2 (2026-10-20, user: "more drafts, but keep the dimensions the same this time").

Every draft is the current helmet's envelope: the shell 9 x 9 x 9 (x +-4.5, y 38.95..47.5, z -4..5) and front pieces
inside the current band's reach (z -4.75, x +-4.75, top 47.75). The shell's sides, back and top reuse the current
helmet's UV (same paint), so only the face changes. Hidden groups under `head` like round 1 (helmet_protos_art.py).

  F porthole   a 7 x 7 round porthole, 0.75 proud
  G goggles    a steel band round the eyes (wrapping the corners) with two round lenses; a gas-mask filter below
  H T-visor    a flat faceplate with a T cut in it
  I slit       two face plates with a real 1 px gap between them, glass set back in it; a plain jaw (seam, rivets)
  J wrap       a glass band round the front and both sides, half way back; plain below
  K split      the current helmet with a centre bar: two panes; a plain jaw instead of the grill
  L bars       the current visor with three steel bars in front of the glass; a plain jaw
v2 (user: "too many of them share the mouth grill"): only the current helmet keeps the grill

    uv run --with pillow python3 model/art/helmet_drafts2_art.py   (reads .scratch/helmets/source2.json, a fresh
    snapshot with round 1 in it; writes .scratch/helmets/plan2.json; apply: .scratch/helmets/apply.js {plan})
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, plate, face_dims
from helmet_protos_art import C, shell, trim, glass, _P, FACES

SRC, OUT = ".scratch/helmets/source2.json", ".scratch/helmets/plan2.json"
SHELL = ([-4.5, 38.95, -4], [4.5, 47.5, 5])
REUSE = "reuse"            # painter result: take the named current cube's face UV


def pattern(rows):
    return [[dict(_P, I='I0')[ch] for ch in r] for r in rows]


PORT7 = pattern(("o.WWR.o", ".Whggr.", "Whhgggr", "Whggggr", "Wgggger", ".Rgeer.", "o.rrr.o"))
LENS = pattern((".WR.", "Whgr", "Rger", ".rr."))


def face_plate(w, h):
    """A flat steel faceplate (lighter than the shell, like the band)."""
    return plate(w, h, top='H', left='L', right='S', bottom='S', base='M', marks=('L',), n=0)


# name -> (from, to, painter); painter(face, w, h) returns a grid or (REUSE, cube name)
def shell_with(front):
    return (*SHELL, lambda f, w, h: front(w, h) if f == "north" else (REUSE, "helmet_shell"))


def plain_front(w, h): return shell("north", w, h)


def g_band(f, w, h):
    if f == "north":
        g = trim(f, w, h)
        for x0 in (1, 5):
            for j, row in enumerate(LENS):
                for i, k in enumerate(row):
                    if k != 'B': g[j][x0 + i] = k
        return g
    return trim(f, w, h)


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


FILTER = pattern((".WR.", "WIRr", "RrIr", ".rr."))   # a round filter canister: steel rim, dark mesh


def g_filter(f, w, h):
    return [r[:] for r in FILTER] if f == "north" else trim(f, w, h)


def reuse(name): return lambda f, w, h: (REUSE, name)


DRAFTS = {
    "proto_helm_F_porthole": {
        "hF_shell": shell_with(plain_front),
        "hF_port": ([-3.5, 40, -4.75], [3.5, 47, -4], lambda f, w, h: [r[:] for r in PORT7] if f == "north" else trim(f, w, h))},
    "proto_helm_G_goggles": {
        "hG_shell": shell_with(plain_front),
        "hG_filter": ([-2, 38.95, -4.75], [2, 42, -4], g_filter),        # gas-mask filter instead of a grill
        "hG_band": ([-4.75, 42, -4.75], [4.75, 46, -2.75], g_band)},
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
    pre = tuple(n for g in DRAFTS.values() for n in g)
    mine = [e for e in model["elements"] if e["name"] in pre]
    rest = [e for e in model["elements"] if e not in mine]
    # free = no other cube's UV uses it (transparent or old art nobody shows); round 1's sketches are kept
    used = used_mask(dict(model, elements=rest), Image.new("RGBA", tex.size, (0, 0, 0, 0)))
    cur = {e["name"]: e for e in model["elements"]}
    plan = {"groups": {}, "pixels": [], "origin": [0, 41, 1], "parent": "head", "checkpoint": "before helmet drafts 2"}
    faces, rects = {}, []
    for group, cubes in DRAFTS.items():
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
        plan["pixels"] += [[u + x, v + y, C[k]] for y, row in enumerate(g) for x, k in enumerate(row)]
    json.dump(plan, open(OUT, "w"))
    print("drafts", len(DRAFTS), "cubes", sum(len(c) for c in DRAFTS.values()), "pixels", len(plan["pixels"]))


if __name__ == "__main__":
    main()
