"""Belt buckle (2026-10-18, user: the gap between the hip hoods' fronts is the place for a buckle that closes it).
A 4 x 3 steel buckle on the `hips` bone (static, like the hoods), centred over the 0.5 px gap, 0.6 px proud of the
hood fronts (z -5.5) and inside their side profile (y 26.125..29.125, back at z -5), so the thigh drums swinging under
the hoods never reach it. Per-face UV on newly packed rects, painted in the hoods' grey band colours.

    uv run --with pillow python3 model/art/buckle_art.py   (reads .scratch/head/buckle_source.json from the skill's
    snapshot.mjs; writes .scratch/head/buckle_plan.json; apply with the chest's apply script logic: buckle_apply.js)
"""
import json, base64, io, math, sys
from PIL import Image
sys.path.insert(0, "model/art")
from sm_chest_art import used_mask, pack, grid, C, face_dims

SRC, OUT = ".scratch/head/buckle_source.json", ".scratch/head/buckle_plan.json"
HIPS = [0, 22.625, 0]
CUBES = {"belt_buckle": ([-2, 26.125, -6.1], [2, 29.125, -5])}


def painter(face, w, h):
    if face == "north":                     # steel frame round a dark plate, lit from the upper left
        return [['W', 'W', 'W', 'GR'],
                ['W', 'I1', 'I2', 'G2'],
                ['GR', 'G2', 'G2', 'G2']]
    if face == "up": return grid(w, h, 'W')
    if face == "east": return grid(w, h, 'GR')
    if face == "west": return grid(w, h, 'G2')
    return grid(w, h, 'I2')


def main():
    src = json.load(open(SRC))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    used = used_mask(src["model"], tex)
    faces, rects = {}, []
    for name, (f, t) in CUBES.items():
        for face in ("north", "east", "south", "west", "up", "down"):
            fw, fh = face_dims(f, t, face)
            pw, ph = math.ceil(fw - 1e-6), math.ceil(fh - 1e-6)
            faces[(name, face)] = (fw, fh, painter(face, pw, ph))
            rects.append(((name, face), pw, ph))
    at = pack(used, rects)
    plan = {"cubes": {n: {"from": f, "to": t, "faces": {}} for n, (f, t) in CUBES.items()}, "pixels": [], "origin": HIPS,
            "bone": "hips"}
    for (name, face), (fw, fh, g) in faces.items():
        u, v = at[(name, face)]
        plan["cubes"][name]["faces"][face] = [u, v, u + fw, v + fh]
        plan["pixels"] += [[u + x, v + y, C[k]] for y, row in enumerate(g) for x, k in enumerate(row)]
    json.dump(plan, open(OUT, "w"))
    print("pixels", len(plan["pixels"]), at)


if __name__ == "__main__":
    main()
