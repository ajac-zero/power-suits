"""Full-length gauntlets (2026-10-19, user: "make the gauntlets full length all around").

Each forearm block (forearm_r / forearm_l, x 8 wide, z -5.5..2.5) reached only from the wrist (y 20.625) to y 24.058;
above it the gauntlet stepped in: a back strip to the elbow line (fa_back, y 27.625), a middle step (fa_mid) and a
27 deg ramp at the front (fa_ramp), so the dark elbow joint showed in front, and a small block under the upper arm
(elbow_ramp_r/l) filled the notch that ramp left when the elbow bent. Now the forearm block is one box from the wrist to
the elbow line on every side (y 20.625..27.625, 1 px below the elbow pivot); fa_ramp / fa_mid / fa_back and elbow_ramp
are removed (the full front does elbow_ramp's job, and kept it clipped up to 1.3 px).
Measured over every animation (both arms): the full box only touches the pad's lower ramp, 0.54 px in the run (hand arm
at 90 deg) and 0.41 px in idle_fidget (tool arm at 86 deg); everything else is clean.

Paint (STYLE): the old side faces were one band of 3 rows (a lit top rim, base blue, a darker bottom row) stretched
over 3.43 px. "double" (the user's pick, 2026-10-19: the look of the shape prototype, which reused that band on its
extra block): the band twice, stacked, as 6 texture rows over the 7 px side (1.17 px per row, like the prototype).
"cuff" (the first build, f5c44ea): the band as the bottom 3 rows under 4 new rows of an upper cuff plate in the chest
recipe (sm_chest_art.py). The top (the grey-framed opening) and the wrist end (the fist mount / tool socket) keep
their old faces. The left arm is the exact mirror of the right (the user's rule: both arms identical, mirrored): each of
its sides is the right's opposite side flipped left to right.

    node .agents/skills/blockbench-mcp/scripts/snapshot.mjs .scratch/gauntlet/source.json
    uv run --with pillow python3 model/art/gauntlet_full_art.py      (writes .scratch/gauntlet/plan.json + preview.png)
then model/art/gauntlet_full_apply.js.
"""
import json, base64, io, math, os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sm_chest_art import C, used_mask, pack  # noqa: E402

SRC = ".scratch/gauntlet/source.json"
OUT = ".scratch/gauntlet/plan.json"
TOP = 27.625
STYLE = "double"      # or "cuff"
SIDES = ("north", "east", "south", "west")
REMOVE = ["fa_ramp_r", "fa_ramp_l", "fa_mid_r", "fa_mid_l", "fa_back_r", "fa_back_l", "elbow_ramp_r", "elbow_ramp_l"]
MIRROR_FACE = {"north": "north", "south": "south", "east": "west", "west": "east"}


def face_image(tex, uv):
    """A face's pixels as seen from outside (Blockbench: u1/v1 sit on a fixed edge, a swapped pair flips the image)."""
    u1, v1, u2, v2 = uv
    im = tex.crop((math.floor(min(u1, u2)), math.floor(min(v1, v2)), math.ceil(max(u1, u2)), math.ceil(max(v1, v2))))
    if u1 > u2: im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if v1 > v2: im = im.transpose(Image.FLIP_TOP_BOTTOM)
    return im


def hexc(k):
    return tuple(int(C[k][i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def cuff_rows(w):
    """The upper cuff plate (4 rows over the wrist band): lit rim, two base rows with edge columns and a mark, shadow."""
    rows = [['H2'] + ['H'] * (w - 2) + ['L'],
            ['L'] + ['B'] * (w - 2) + ['S'],
            ['L'] + ['B'] * (w - 2) + ['S'],
            ['M'] + ['S'] * (w - 2) + ['D']]
    rows[1][2] = 'M'; rows[2][w - 3] = 'M'          # sparse wear marks, not touching
    return rows


def main():
    src = json.load(open(SRC))
    model = src["model"]
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    els = {e["name"]: e for e in model["elements"]}
    fr = els["forearm_r"]
    w = 8
    rows = 6 if STYLE == "double" else 7
    right, clear = {}, []
    for face in SIDES:
        cur = face_image(tex, fr["faces"][face]["uv"])            # 8 x 3 (the old box-UV face) or a rebuilt side
        band = cur.crop((0, cur.height - 3, 8, cur.height))       # the band is always its bottom 3 rows
        if cur.height != 3:                                       # a rebuilt side: free its pixels for the new paint
            clear += [e["faces"][face]["uv"] for e in (fr, els["forearm_l"])]
        im = Image.new("RGBA", (w, rows))
        if STYLE == "double":
            im.paste(band, (0, 0)); im.paste(band, (0, 3))
        else:
            for y, row in enumerate(cuff_rows(w)):
                for x, k in enumerate(row):
                    im.putpixel((x, y), hexc(k))
            im.paste(band, (0, 4))
        right[face] = im
    left = {face: right[MIRROR_FACE[face]].transpose(Image.FLIP_LEFT_RIGHT) for face in SIDES}
    used = used_mask(model, tex)
    rects = [((arm, face), w, rows) for arm in ("r", "l") for face in SIDES]
    at = pack(used, rects)
    plan = {"remove": REMOVE, "pixels": [], "cubes": {}, "clear": clear}
    for arm, faces in (("r", right), ("l", left)):
        name = "forearm_" + arm
        e = els[name]
        uv = {face: [at[(arm, face)][0], at[(arm, face)][1], at[(arm, face)][0] + w, at[(arm, face)][1] + rows] for face in SIDES}
        uv["up"] = e["faces"]["up"]["uv"]
        uv["down"] = e["faces"]["down"]["uv"]
        plan["cubes"][name] = {"from": e["from"], "to": [e["to"][0], TOP, e["to"][2]], "faces": uv}
        for face, im in faces.items():
            u, v = at[(arm, face)]
            for y in range(rows):
                for x in range(w):
                    r, g, b, a = im.getpixel((x, y))
                    plan["pixels"].append([u + x, v + y, f"{r:02x}{g:02x}{b:02x}"])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(plan, open(OUT, "w"))
    # preview: the four sides of each arm, 16x
    prev = Image.new("RGBA", (4 * (w + 2) * 16, 2 * 9 * 16), (255, 0, 255, 255))
    for j, faces in enumerate((right, left)):
        for i, face in enumerate(SIDES):          # as drawn: the texture rows stretched over the 7 px side
            prev.paste(faces[face].resize((w * 16, 7 * 16), Image.NEAREST), (i * (w + 2) * 16, j * 9 * 16))
    prev.save(".scratch/gauntlet/preview.png")
    print("pixels", len(plan["pixels"]), {k: v for k, v in at.items()})


if __name__ == "__main__":
    main()
