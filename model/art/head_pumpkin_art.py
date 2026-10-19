"""Pumpkin-sized helmet (2026-10-18, user: the slim 9 px helmet looked a little small and its top flickered against
the pilot's head; "the size of a pumpkin helmet": vanilla draws a worn carved pumpkin 10 px across, 1 px clear of the
8 px head all round).

The helmet becomes a 10 x 10 x 10 shell (x +-5, y 38..48, z -4.5..5.5) around the pilot's head, which moves 1 px down
(pilot_head pivot y 40 -> 39, head 39..47) so the shell clears it by 1 px everywhere and the model still tops out at
48 (3 blocks). The face is a 0.75 px relief on the shell's front: a grey frame (brow y 45..47, cheeks, chin) round
the dark visor glass (y 42.625..45.625, 0.25 behind the frame) over the old helmet's slotted mouthpiece (0.5 px
proud, its grill just above the chest's collar rim, which covers the helmet up to y 40.6). Everything is repainted (per-face UV, new rects); the visor glass
uses the glow mask's reserved colours (tools/make-glowmasks.py: 262c34 / 1e232a / 46505c).

    uv run --with pillow python3 model/art/head_pumpkin_art.py  (reads .scratch/head/pumpkin_source.json from the
    skill's snapshot.mjs; writes .scratch/head/pumpkin_plan.json; apply with model/art/head_pumpkin_apply.js)
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, plate, C, face_dims

SRC, OUT = ".scratch/head/pumpkin_source.json", ".scratch/head/pumpkin_plan.json"
C = dict(C, GM='89939a', VG='262c34', VE='1e232a', VH='46505c')
HEAD = [0, 41, 1]
ZF = -5.25                                    # the frame's front (shell front -4.5)
FB = 38.625                                   # the frame's bottom
CUBES = {
    "helmet_shell": ([-5, 38, -4.5], [5, 48, 5.5]),
    "helmet_brow": ([-5, 45.625, ZF], [5, 47.625, -4.5]),
    # the frame stops at the breastplate's top (38.625): it stands 0.25 in front of the chest's front plane (z -5)
    "cheek_r": ([3.5, FB, ZF], [5, 45.625, -4.5]),
    "cheek_l": ([-5, FB, ZF], [-3.5, 45.625, -4.5]),
    # the mouthpiece (user: keep the old helmet's grill): 0.5 px proud of the frame, its slotted top 2 rows just above
    # the collar rim (40.625), the rest behind it; still behind the rim's back (z -6.1) when the head pitches
    "chin_guard": ([-3.5, FB, -5.75], [3.5, 42.625, -4.5]),
    "visor": ([-3.5, 42.625, -5], [3.5, 45.625, -4.5]),
}


def shell(face, w, h):
    if face == "up":                          # the dome: lit front-left corner, like the old top
        g = plate(w, h, top='L', left='L', right='S', bottom='D', base='B', marks=('M', 'M', 'S'))
        g[0][0] = 'H2'
        return g
    if face == "north":                       # behind the face pieces; only the top row shows above the brow
        g = grid(w, h, 'I2')
        g[0] = ['H2'] + ['H'] * (w - 2) + ['L']
        return g
    if face == "down": return grid(w, h, 'I1')
    lit = face == "east"                      # +x is the lit side (the viewer's left from the front)
    g = plate(w, h, top='H', left='L' if lit else 'B', right='B' if lit else 'S', bottom='D',
              base='B' if lit else 'B', marks=('M', 'S') if lit else ('S',))
    if face == "south":
        g = plate(w, h, top='H', left='L', right='S', bottom='D', base='B', marks=('M', 'S'))
    return g


def frame(name, face, w, h):
    if face == "north":
        if name == "helmet_brow": return [['W'] * w, ['GM'] * w]
        if name == "cheek_r": return [['W', 'GR'] for _ in range(h - 1)] + [['GM', 'G2']]
        if name == "cheek_l": return [['GR', 'GM'] for _ in range(h - 1)] + [['G2', 'G2']]
        if name == "chin_guard":            # the old chin guard's grill: grey bars and dark slots, then plain
            return [['GR', 'I0', 'GR', 'I0', 'GR', 'I0', 'G2'], ['G2', 'I1', 'G2', 'I1', 'G2', 'I1', 'G2'],
                    ['G2'] * w, ['G2'] * w][:h]
    if face == "up": return grid(w, h, 'W')
    if face == "down": return grid(w, h, 'G2')
    if face == "east": return grid(w, h, 'GR')
    if face == "west": return grid(w, h, 'GM')
    return grid(w, h, 'I2')


def visor(face, w, h):
    if face != "north": return grid(w, h, 'VE')
    g = grid(w, h, 'VG')
    for y in range(h): g[y][w - 1] = 'VE'
    g[h - 1] = ['VE'] * w
    g[0][0] = 'VH'; g[1][0] = 'VH'; g[0][1] = 'VH'
    return g


def painter(name, face, w, h):
    if name == "helmet_shell": return shell(face, w, h)
    if name == "visor": return visor(face, w, h)
    return frame(name, face, w, h)


def main():
    src = json.load(open(SRC))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    # the cubes being rebuilt give up their old UV (the old art stays in the texture, unused)
    model = dict(src["model"], elements=[e for e in src["model"]["elements"] if e["name"] not in CUBES])
    used = used_mask(model, tex)
    faces, rects = {}, []
    for name, (f, t) in CUBES.items():
        for face in ("north", "east", "south", "west", "up", "down"):
            fw, fh = face_dims(f, t, face)
            pw, ph = math.ceil(fw - 1e-6), math.ceil(fh - 1e-6)
            g = painter(name, face, pw, ph)
            assert len(g) == ph and all(len(r) == pw for r in g), (name, face, pw, ph, len(g), len(g[0]))
            faces[(name, face)] = (fw, fh, g)
            rects.append(((name, face), pw, ph))
    # don't reuse the old helmet's texture area either: its pixels are still painted (alpha), so used_mask keeps them
    at = pack(used, rects)
    plan = {"cubes": {n: {"from": f, "to": t, "faces": {}} for n, (f, t) in CUBES.items()}, "pixels": [], "origin": HEAD,
            "bone": "helmet", "pilot_head_dy": -1}
    for (name, face), (fw, fh, g) in faces.items():
        u, v = at[(name, face)]
        plan["cubes"][name]["faces"][face] = [u, v, u + fw, v + fh]
        plan["pixels"] += [[u + x, v + y, C[k]] for y, row in enumerate(g) for x, k in enumerate(row)]
    json.dump(plan, open(OUT, "w"))
    print("pixels", len(plan["pixels"]))


if __name__ == "__main__":
    main()
