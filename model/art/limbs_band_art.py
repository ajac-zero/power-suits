"""Thighs and pauldrons tied to the chest / gauntlets / boots (user, 2026-10-20: "I don't like that the thighs have sort
of indentations on them. I really like the chestpiece, the gauntlets and the boots ... something that makes that
transition more cohesive and really ties the armor together").

Two recipes, per part (pads, thighs):
  A  lames: the gauntlet's band (shins_band_art.py) over the whole part: rows of (dash, plain, seam) by world height,
     the lit column on the viewer's right of every side at the part's outline (no stripe where blocks meet)
  B  plate + cuff: the breastplate's recipe (flat armour blue, lit / half-lit columns on the viewer's left, shaded /
     dark on the right, a shadow row under the rim above, sparse 1 px marks) with one gauntlet band as a cuff along the
     part's bottom edge, the plate's lowest row shaded over it
More concepts (same day, the user wasn't sold on B pads + A thighs): C plate above / lames toward the joint, G = C in
the gauntlet's darker tone, F dark plate + cuff, D wide 4-row lames, L chest-style lames (lit rim, tuck), T plate + a
slate cuff, and
  R  rolled rims (the user's pick, 2026-10-20: "go with 6 RR"): the breastplate's plate with its bottom edge rolled out
     like the chest's rim (a bright H row with an S row under it); the thigh is two plates, the upper one rolling over
     the lower at y 16.68 (PARTS split), the lower ending in the same rim above the knee.
All of them drop the thighs' recessed panels and studs (the "indentations"). The pads' top plate (rim + roof) and
everything that isn't blue (iron insides, joints) stay. The thigh's sloped outer top (thigh_top / thigh_shoulder up
faces) is a lit plate in both. Every pixel is computed in the left side's frame (the right side is its mirror: x -> -x,
east <-> west), from the pixel's world position across the whole part, so blocks of one part meet without seams.

Run: uv run --with pillow python3 model/art/limbs_band_art.py <source.json> <pads A|B|-> <thighs A|B|->
Prints the pixels as hex (xxyyrrggbb) for a tex.edit.
"""
import base64, io, json, math, sys
from PIL import Image

C = dict(H='62a3ef', L='4588e0', M='3a7bd6', B='2d6bcc', S='2358b2', D='1b4895',
         I1='25292c', I2='2f3437', I3='3b4246', I4='4c5559')
SLATE = dict(D='I1', S='I2', B='I4', L='I4')        # the gauntlet band in the slate of the parts under the armour
E = {"north": ("x", 1, "y", 1, ("z", 0)), "south": ("x", 0, "y", 1, ("z", 1)), "east": ("z", 1, "y", 1, ("x", 1)),
     "west": ("z", 0, "y", 1, ("x", 0)), "up": ("x", 0, "z", 0, ("y", 1)), "down": ("x", 0, "z", 1, ("y", 0))}
AX = {"x": 0, "y": 1, "z": 2}
SIDES = ("north", "south", "east", "west")
# per side face (left frame): the horizontal axis and the sign of "toward the viewer's left" (as the box UV maps it)
LEFT = {"north": (0, +1), "south": (0, -1), "east": (2, +1), "west": (2, -1)}
PARTS = {
    "pads": dict(cubes=["pad_back", "pad_front", "pad_wall", "pad_mid_1", "pad_mid_2", "pad_ramp"], sides="rl",
                 ybot=29.625, split=35.625),
    "thighs": dict(cubes=["thigh_main", "thigh_ramp", "thigh_mid_f", "thigh_front", "thigh_mid_b", "thigh_top",
                          "thigh_upper", "thigh_shoulder"], sides="l", ybot=10.68, split=16.68),
}
TOPS = {"thigh_top", "thigh_shoulder"}          # their up faces: the thigh's sloped outer top


def is_blue(p):
    r, g, b, a = p
    return a and b > r + 60 and b > g + 20


def rot(p, o, r):
    p = [p[i] - o[i] for i in range(3)]
    for ax in (2, 1, 0):                        # Blockbench cubes: one axis each here
        a = math.radians(r[ax])
        if not a: continue
        i, j = [(1, 2), (2, 0), (0, 1)][ax]
        p[i], p[j] = p[i] * math.cos(a) - p[j] * math.sin(a), p[i] * math.sin(a) + p[j] * math.cos(a)
    return [p[i] + o[i] for i in range(3)]


def pixels(e, face):
    ua, uMax, va, vMax, (pa, pMax) = E[face]
    u1, v1, u2, v2 = e["faces"][face]["uv"]
    lo, hi = e["from"], e["to"]
    for c in range(math.floor(min(u1, u2)), math.ceil(max(u1, u2))):
        for r in range(math.floor(min(v1, v2)), math.ceil(max(v1, v2))):
            p = [0, 0, 0]
            for ax, atMax, frac in ((ua, uMax, (c + 0.5 - u1) / (u2 - u1)), (va, vMax, (r + 0.5 - v1) / (v2 - v1))):
                i = AX[ax]; s = 1 - frac if atMax else frac
                p[i] = lo[i] + s * (hi[i] - lo[i])
            p[AX[pa]] = hi[AX[pa]] if pMax else lo[AX[pa]]
            yield c, r, rot(p, e["origin"], e.get("rotation") or [0, 0, 0])


def mirror(face, w, side):
    if side == "l": return face, w
    return {"east": "west", "west": "east"}.get(face, face), [-w[0], w[1], w[2]]


def mark(k1, k2):                               # sparse, deterministic, never two side by side
    return (k1 * 7 + k2 * 13) % 17 == 0


def band(row, k, lit, period=3):
    """row counted from the band's bottom: 0 the seam, period-1 the dashed top row."""
    if row == 0: return 'D'
    if lit: return 'L'
    return 'B' if row == period - 1 and k % 4 in (1, 2) else 'S'


def plate(dl, dr, c, r, dark=False):
    """The breastplate's recipe; dark = in the gauntlet's tone (base S) instead of the chest's (base B)."""
    if dark:
        return 'L' if dl < 1 else 'M' if dl < 2 else 'D' if dr < 1 else 'B' if mark(c, r) else 'S'
    return 'L' if dl < 1 else 'M' if dl < 2 else 'D' if dr < 1 else 'S' if dr < 2 else 'M' if mark(c, r) else 'B'


def paint_part(els, tex, part, recipe):
    spec = PARTS[part]
    out = {}
    for side in spec["sides"]:
        pts = []                                  # (col, row, face, world point in left frame, cube)
        for base in spec["cubes"]:
            e = els[f"{base}_{side}"]
            for face in E:
                for c, r, w in pixels(e, face):
                    f, q = mirror(face, w, side)
                    pts.append((c, r, f, q, base))
        by_face = {f: [p for p in pts if p[2] == f] for f in SIDES}
        for c, r, f, q, base in pts:
            if not is_blue(tex.getpixel((c, r))) or f == "down": continue
            if f == "up":
                if base not in TOPS: continue
                out[(c, r)] = C['L' if mark(c, r) else 'M']
                continue
            ax, sg = LEFT[f]
            near = [p[3] for p in by_face[f] if abs(p[3][1] - q[1]) < 0.5]
            hs = [p[ax] * sg for p in near]
            dl, dr = max(hs) - q[ax] * sg, q[ax] * sg - min(hs)      # px from the viewer's left / right outline
            col = [p[3][1] for p in by_face[f] if abs(p[3][ax] - q[ax]) < 0.5]
            dt, db = max(col) - q[1], q[1] - min(col)
            k = math.floor(dl)
            dark = recipe in ("F", "G")
            yb = q[1] - spec["ybot"]
            if recipe == "A":
                out[(c, r)] = C[band(math.floor(yb) % 3, k, dr < 1)]
            elif recipe == "D":                      # wide lames: 4 rows (dash, plain, plain, seam)
                out[(c, r)] = C[band(math.floor(yb) % 4, k, dr < 1, 4)]
            elif recipe in ("C", "G"):               # plate above, gauntlet lames below toward the joint
                ys = q[1] - spec["split"]
                if ys < 0:
                    out[(c, r)] = C[band(math.floor(yb) % 3, k, dr < 1)]
                elif ys < 1 or dt < 1:
                    out[(c, r)] = C['D' if dark else 'S']
                else:
                    out[(c, r)] = C[plate(dl, dr, c, r, dark)]
            elif recipe == "R":                      # rolled rims: plates whose bottom edge rolls out (the chest's rim)
                ys = q[1] - spec["split"] if part == "thighs" else 99
                d = db if ys < 0 else ys             # thighs: an upper plate rolling over the lower one
                if d < 1: out[(c, r)] = C['S']
                elif d < 2: out[(c, r)] = C['L' if dl < 2 else 'D' if dr < 1 else 'H']
                elif dt < 1 or (0 <= ys and db < 1): out[(c, r)] = C['S']
                else: out[(c, r)] = C[plate(dl, dr, c, r)]
            elif recipe == "L":                      # chest-style lames: lit rim, base, base, tuck shadow
                row = 3 - math.floor(yb) % 4
                out[(c, r)] = C['H' if row == 0 and dr >= 1 else 'S' if row == 3 else plate(dl, dr, c, r)]
            elif recipe == "T" and db < 3:           # plate + a slate cuff
                out[(c, r)] = C[SLATE[band(math.floor(db) % 3, k, dr < 1)]]
            elif db < 3:                             # B / F: the cuff, one gauntlet band along the bottom edge
                out[(c, r)] = C[band(math.floor(db) % 3, k, dr < 1)]
            elif db < 4 or dt < 1:                   # plate's shaded bottom row / shadow under the rim above
                out[(c, r)] = C['D' if dark else 'S']
            else:
                out[(c, r)] = C[plate(dl, dr, c, r, dark)]
    return out


def main():
    src = json.load(open(sys.argv[1]))
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    els = {e["name"]: e for e in src["model"]["elements"]}
    paint = {}
    for part, recipe in (("pads", sys.argv[2]), ("thighs", sys.argv[3])):
        if recipe != "-": paint.update(paint_part(els, tex, part, recipe))
    sys.stderr.write(f"{len(paint)} px\n")
    print("".join(f"{x:02x}{y:02x}{col}" for (x, y), col in sorted(paint.items())))


if __name__ == "__main__":
    main()
