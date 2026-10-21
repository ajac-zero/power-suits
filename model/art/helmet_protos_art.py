"""Helmet sketches (2026-10-20, user: "explore helmet variations: different visor shapes and head shapes"; models only,
no gameplay yet). Each sketch is its own hidden group under `head` (pivot = the helmet's, [0, 41, 1]) so it turns
with every animation like the real helmet; cube names carry the sketch's letter (hA_shell ...). Painted in the
suit's steel (the chest's plate recipe), visor glass in the reserved glow colours (VG / VE / VH: it would light up).

Fit (torso frame, px): below the collar top (y 42.625) a helmet stays inside the collar (x +-5.5, z -6.5 .. 4.9);
the pilot's head is x +-4, y 39..47, z -3.5..4.5; the bottom sits at 38.95 (the current helmet's).

  A diver     10 wide, tall; a big round porthole (8 x 8 ring, 1 px proud), small round side ports
  B marine    the 9-cube; twin round lenses under a brow ridge, a grilled respirator snout
  C bucket    low and wide (10.5 x 7.5); a glass letterbox band wrapping round the front and both sides
  D knight    tall (10) with a top crest; a T-visor cut in a flat faceplate, breathing holes
  E canopy    a glass bubble over the front and top, steel chin with a grill

    uv run --with pillow python3 model/art/helmet_protos_art.py   (reads .scratch/helmets/source.json, a fresh
    snapshot.mjs; writes .scratch/helmets/plan.json; apply with .scratch/helmets/apply.js)
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, plate, C, face_dims

SRC, OUT = ".scratch/helmets/source.json", ".scratch/helmets/plan.json"
STEEL = dict(G='22262e', D='3c424c', S='58606a', B='757c85', M='8a9097', L='9fa4a9', H='cdcfcf', H2='fbf8f0')
C = dict(C, **STEEL, GM='89939a', VG='262c34', VE='1e232a', VH='46505c')
Y0 = 38.95
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
        if i < w - 1 and i < h - 1: g[i][0 if i == 0 else 0] = 'VH'
    if w > 2 and h > 1: g[0][1] = 'VH'
    if w > 3: g[0][2] = 'VH'
    return g


def disc(g, cx, cy, r_out, r_in, ring='GR', inner='VG', rivets=False):
    """A round window painted on g: a steel ring (lit top-left) round glass (highlight top-left)."""
    for y in range(len(g)):
        for x in range(len(g[0])):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r_in:
                g[y][x] = 'VH' if (dx + dy < -r_in * 0.9 and d > r_in * 0.45) else ('VE' if dx + dy > r_in * 0.9 else inner)
            elif d <= r_out:
                g[y][x] = 'W' if dx + dy < -0.5 else ('G2' if dx + dy > 1.5 else ring)
    if rivets:
        for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            rx, ry = cx + sx * (r_in + r_out) / 2 * 0.707, cy + sy * (r_in + r_out) / 2 * 0.707
            g[int(ry)][int(rx)] = 'I0'
    return g


# ---------- A diver: a big porthole, small side ports ----------
def a_paint(name, face, w, h):
    if name == "hA_shell":
        g = shell(face, w, h)
        if face in ("east", "west"):                          # a small round side port, upper middle
            x0, y0 = (w - 4) // 2, 2
            for j, row in enumerate(SIDE_PORT):
                for i, k in enumerate(row):
                    if k != 'B': g[y0 + j][x0 + i] = k
        if face == "south":                                   # air hose fitting: a dark round socket
            disc(g, w / 2, h * 0.55, 1.6, 0.8, inner='I0')
        return g
    if name == "hA_port":
        if face == "north": return [list(r) for r in PORT]
        return trim(face, w, h)


# the porthole, drawn by hand (8 x 8): a steel ring lit top-left, rounded glass with a highlight, bolts in the corners
_P = {'.': 'B', 'o': 'H', 'W': 'W', 'R': 'GR', 'r': 'G2', 'g': 'VG', 'h': 'VH', 'e': 'VE'}
PORT = [[_P[ch] for ch in row] for row in (
    "o.WWWR.o",
    ".Wggggr.",
    "Whhggggr",
    "Whgggggr",
    "Wggggger",
    "Rggggeer",
    ".Rgeeer.",
    "o.rrrr.o")]
SIDE_PORT = [[_P[ch] for ch in row] for row in (".WR.", "Whgr", "Rger", ".rr.")]


A = {"hA_shell": ([-5, Y0, -4.5], [5, 49, 4.85]),
     "hA_port": ([-4, 40, -5.5], [4, 48, -4.5])}


# ---------- B marine: twin lenses, brow ridge, respirator snout ----------
def b_paint(name, face, w, h):
    if name == "hB_shell": return shell(face, w, h)
    if name.startswith("hB_lens"):
        if face == "north": return [['I1', 'VH', 'I1'], ['VH', 'VG', 'VE'], ['I1', 'VE', 'I1']]
        return grid(w, h, 'I1')
    if name == "hB_brow":
        return trim(face, w, h)
    if name == "hB_snout":
        if face == "north":                                   # vertical grill: steel bars, dark slots
            g = [['GR' if x % 2 == 0 else 'I0' for x in range(w)] for _ in range(h)]
            g[0] = ['W'] * w; g[h - 1] = ['G2'] * w
            return g
        if face in ("east", "west"):                          # side vents: horizontal slots
            g = trim(face, w, h)
            if h > 2: g[1] = ['I0'] * w
            return g
        return trim(face, w, h)


B = {"hB_shell": ([-4.5, Y0, -4], [4.5, 47.5, 4.85]),
     "hB_brow": ([-4.5, 45.5, -4.75], [4.5, 46.5, -4]),
     "hB_lens_r": ([0.75, 42.5, -4.5], [3.75, 45.5, -4]),
     "hB_lens_l": ([-3.75, 42.5, -4.5], [-0.75, 45.5, -4]),
     "hB_snout": ([-2, 39.5, -5.5], [2, 42, -4])}


# ---------- C bucket: low and wide, wrap-around letterbox ----------
def c_paint(name, face, w, h):
    if name == "hC_shell":
        g = shell(face, w, h)
        if face == "north":                                   # breathing slots low on the front
            for y in (h - 3, h - 2):
                for x in range(2, w - 2):
                    g[y][x] = 'I0' if x % 2 == 0 else 'S'
        return g
    if name == "hC_band":
        if face in ("north", "east", "west"): return glass(face, w, h)
        return grid(w, h, 'I1')


C_ = {"hC_shell": ([-5.25, Y0, -4.5], [5.25, 46.5, 4.85]),
      "hC_band": ([-5.5, 43, -4.75], [5.5, 45, 1.5])}


# ---------- D knight: tall, crest, T-visor faceplate ----------
def d_paint(name, face, w, h):
    if name == "hD_shell": return shell(face, w, h)
    if name == "hD_face":
        if face != "north": return trim(face, w, h)
        g = plate(w, h, top='H', left='L', right='S', bottom='S', base='M', marks=('L',), n=0)
        for x in range(1, w - 1):                             # the eye slit
            g[2][x] = 'VH' if x <= 2 else 'VG'
            g[3][x] = 'VE'
        for y in range(4, h - 2):                             # the T's stem down the middle
            g[y][3] = 'VG'; g[y][4] = 'VE'
        for y in (6, 8):                                      # breathing holes either side
            g[y][1] = 'I0'; g[y][w - 2] = 'I0'
        return g
    if name == "hD_crest": return trim(face, w, h)


D = {"hD_shell": ([-4.5, Y0, -4], [4.5, 49, 4.85]),
     "hD_face": ([-4, 39.25, -4.75], [4, 48.75, -4]),
     "hD_crest": ([-0.5, 49, -3.5], [0.5, 50, 4.25])}


# ---------- E canopy: glass bubble over the front and top ----------
def e_paint(name, face, w, h):
    if name == "hE_shell": return shell(face, w, h)
    if name == "hE_chin":
        g = shell(face, w, h)
        if face == "north":                                   # a grill under the bubble
            for y in range(1, h - 1):
                for x in range(1, w - 1):
                    g[y][x] = 'I0' if x % 2 == 1 else 'GR'
        return g
    if name == "hE_canopy":
        if face == "down": return grid(w, h, 'I1')
        g = grid(w, h, 'VG')
        for y in range(h): g[y][w - 1] = 'VE'
        if face == "up":                                      # a steel bow frame across the middle of the top
            g[h // 2] = ['GR'] * w
            g[0][0] = g[0][1] = g[1][0] = 'VH'
        else:
            g[h - 1] = ['GR'] * w                             # the bubble's steel seat
            g[0][0] = g[0][1] = g[1][0] = 'VH'
            if face == "north": g[2][0] = 'VH'
            if face in ("east", "west"):                      # the bow frame comes down the sides
                m = w // 2
                for y in range(h): g[y][m] = 'GR'
        return g


# v2: the bubble is the front half of the head (glass from the sides too), steel behind it and under it
E = {"hE_shell": ([-4.5, Y0, -0.5], [4.5, 47.5, 4.85]),
     "hE_chin": ([-4.5, Y0, -4.5], [4.5, 41, -0.5]),
     "hE_canopy": ([-4.4, 41, -5], [4.4, 48, 0])}


SKETCHES = {"proto_helm_A_diver": (A, a_paint), "proto_helm_B_marine": (B, b_paint),
            "proto_helm_C_bucket": (C_, c_paint), "proto_helm_D_knight": (D, d_paint),
            "proto_helm_E_canopy": (E, e_paint)}


def main():
    src = json.load(open(SRC))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    mine = [e for e in src["model"]["elements"] if e["name"][:3] in ("hA_", "hB_", "hC_", "hD_", "hE_")]
    rest = [e for e in src["model"]["elements"] if e not in mine]
    # pack into pixels no other cube's UV uses: transparent ones and the ~8500 px of old art no cube shows any more
    # (the texture has no free block big enough otherwise); the sketches' own old faces (a re-run) count as free
    used = used_mask(dict(src["model"], elements=rest), Image.new("RGBA", tex.size, (0, 0, 0, 0)))
    plan = {"groups": {}, "pixels": [], "origin": [0, 41, 1], "parent": "head"}
    faces, rects = {}, []
    for group, (cubes, paint) in SKETCHES.items():
        plan["groups"][group] = {}
        for name, (f, t) in cubes.items():
            plan["groups"][group][name] = {"from": f, "to": t, "faces": {}}
            for face in FACES:
                fw, fh = face_dims(f, t, face)
                pw, ph = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
                g = paint(name, face, pw, ph)
                assert len(g) == ph and all(len(r) == pw for r in g), (name, face, pw, ph)
                faces[(group, name, face)] = (fw, fh, g)
                rects.append(((group, name, face), pw, ph))
    at = pack(used, rects)
    for (group, name, face), (fw, fh, g) in faces.items():
        u, v = at[(group, name, face)]
        plan["groups"][group][name]["faces"][face] = [u, v, u + fw, v + fh]
        plan["pixels"] += [[u + x, v + y, C[k]] for y, row in enumerate(g) for x, k in enumerate(row)]
    json.dump(plan, open(OUT, "w"))
    print("sketches", len(SKETCHES), "cubes", sum(len(c) for c, _ in SKETCHES.values()), "pixels", len(plan["pixels"]))


if __name__ == "__main__":
    main()
