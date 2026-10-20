"""Shins and boots repainted in the gauntlets' pattern (user, 2026-10-20: "repaint the shins/boots, a pattern similar to
the gauntlets would match and look good").

The gauntlet's sides (gauntlet_full_art.py, STYLE double) are one band repeated: three rows
    dash   S with B dashes (columns 1-2 of every 4), lit L in the last column
    plain  S, lit L in the last column
    seam   D all along
so the forearm reads as stacked plates. Here the same band runs round each lower leg by WORLD height (the user's rule:
blocks that form one piece get no seams of their own, so the rows line up across shin_main_f / shin_main_b /
shin_front / shin_ramp, and the dashes count from the leg's edge, not each cube's):
  shin  rows counted down from y 9.625: seams at y ~7, ~4 and the bottom edge (0.625..1.625), the top row of the shin's
        front a dash row
  boot  the toe cap is one band of its own (dash 3.6..4.6, plain, seam right above the grey sole); the hidden heels
        continue the count upwards
The lit column follows the gauntlet: every face lights its right-hand column as seen from outside (north at the leg's
-x edge, east at the front, south at +x, west at the back), and only on the leg's outline (no lit stripe where two
cubes meet). Tops (the shin ramp, the toe cap) carry the band along their length, lit on the -x side.
Only blue pixels change: the slate tops, the dark knee and the grey sole stay. Both legs share box-UV nets (the right
one with mirror_uv), so painting the left nets paints the right leg's mirror.

Run: uv run --with pillow python3 model/art/shins_band_art.py <source.json>   (a snapshot.mjs of the live project)
Prints the pixels as hex (xxyyrrggbb, x and y 2 digits each) for a tex.edit; writes .scratch/shins/nets_after.png.
"""
import json, base64, io, math, sys
from pathlib import Path
from PIL import Image

C = dict(L='4588e0', B='2d6bcc', S='2358b2', D='1b4895')
E = {"north": ("x", 1, "y", 1, ("z", 0)), "south": ("x", 0, "y", 1, ("z", 1)), "east": ("z", 1, "y", 1, ("x", 1)),
     "west": ("z", 0, "y", 1, ("x", 0)), "up": ("x", 0, "z", 0, ("y", 1)), "down": ("x", 0, "z", 1, ("y", 0))}
AX = {"x": 0, "y": 1, "z": 2}
BONES = {
    "shin": (["shin_main_f_l", "shin_main_b_l", "shin_front_l", "shin_ramp_l"], 9.625),
    "boot": (["boot_foot_l", "boot_heel_a_l", "boot_heel_b_l", "boot_tip_l", "boot_toe_l"], 4.625),
}
XMIN, XMAX = -10, -1          # the left leg's sides
# Sloped cubes (turned about X) carry the band in their own frame, rows parallel to their edges (by world height the rows
# crossed their faces as stair steps); their lit column is the one end that is on the leg's outline.
SLOPED_END = {"shin_ramp_l": "back", "boot_toe_l": "front", "boot_heel_a_l": "back", "boot_heel_b_l": "back"}


def is_blue(p):
    r, g, b, a = p
    return a and b > r + 60 and b > g + 20


def rot_x(p, origin, deg):
    if not deg: return p
    a = math.radians(deg); y, z = p[1] - origin[1], p[2] - origin[2]
    return [p[0], origin[1] + y * math.cos(a) - z * math.sin(a), origin[2] + y * math.sin(a) + z * math.cos(a)]


def face_pixels(e, face):
    """(col, row, world point, local point) for every texture pixel of a face."""
    ua, uMax, va, vMax, (pa, pMax) = E[face]
    u1, v1, u2, v2 = e["faces"][face]["uv"]
    lo, hi = e["from"], e["to"]
    out = []
    for c in range(math.floor(min(u1, u2)), math.ceil(max(u1, u2))):
        for r in range(math.floor(min(v1, v2)), math.ceil(max(v1, v2))):
            p = [0, 0, 0]
            for ax, atMax, frac in ((ua, uMax, (c + 0.5 - u1) / (u2 - u1)), (va, vMax, (r + 0.5 - v1) / (v2 - v1))):
                i = AX[ax]; s = 1 - frac if atMax else frac
                p[i] = lo[i] + s * (hi[i] - lo[i])
            p[AX[pa]] = hi[AX[pa]] if pMax else lo[AX[pa]]
            out.append((c, r, rot_x(p, e["origin"], (e.get("rotation") or [0])[0]), p))
    return out


def main():
    src = json.load(open(sys.argv[1]))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    els = {e["name"]: e for e in src["model"]["elements"]}
    paint = {}
    for bone, (names, Y0) in BONES.items():
        px = {(n, f): face_pixels(els[n], f) for n in names for f in E}
        def outline(face, w):            # is a side pixel on the leg's front (east) / back (west) outline at its height?
            zs = [q[2] for (n, f), lst in px.items() if f == face for _, _, q, _ in lst if abs(q[1] - w[1]) < 0.5]
            return w[2] < min(zs) + 1 if face == "east" else w[2] > max(zs) - 1
        for (n, f), lst in px.items():
            if f == "down": continue
            zfront, zback = els[n]["from"][2], els[n]["to"][2]
            sloped = n in SLOPED_END
            for c, r, w, loc in lst:
                if not is_blue(tex.getpixel((c, r))): continue
                if f == "up":                      # along the top's length, from its front end
                    row, k = math.floor(loc[2] - zfront) % 3, math.floor(loc[0] - XMIN)
                    lit = loc[0] < XMIN + 1
                elif sloped:
                    row = math.floor(Y0 - loc[1]) % 3
                    k = math.floor(loc[0] - XMIN) if f in ("north", "south") else math.floor(loc[2] + 9)
                    lit = {"north": loc[0] < XMIN + 1, "south": loc[0] > XMAX - 1,
                           "east": SLOPED_END[n] == "front" and loc[2] < zfront + 1,
                           "west": SLOPED_END[n] == "back" and loc[2] > zback - 1}[f]
                else:
                    row = math.floor(Y0 - w[1]) % 3
                    k = math.floor(w[0] - XMIN) if f in ("north", "south") else math.floor(w[2] + 9)
                    lit = {"north": w[0] < XMIN + 1, "south": w[0] > XMAX - 1}.get(f) or \
                        (f in ("east", "west") and outline(f, w))
                col = 'D' if row == 2 else 'L' if lit else 'B' if row == 0 and k % 4 in (1, 2) else 'S'
                paint[(c, r)] = C[col]
    out = []
    for (x, y), col in sorted(paint.items()):
        out.append(f"{x:02x}{y:02x}{col}")
        tex.putpixel((x, y), (*bytes.fromhex(col), 255))
    Path(".scratch/shins").mkdir(parents=True, exist_ok=True)
    tex.resize((tex.width * 6, tex.height * 6), Image.NEAREST).save(".scratch/shins/tex_after.png")
    sys.stderr.write(f"{len(out)} px\n")
    print("".join(out))


if __name__ == "__main__":
    main()
