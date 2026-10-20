"""Knee, elbow and hip joints repainted (user, 2026-10-20: "the last thing I want to repaint are the knee and elbow
joints, and maybe the hip axle also").

The joints were flat slate (2f3437) with a few specks. They stay slate (the user's rule: parts under the armour are
slate, joints black / grey), but get a pattern. Concepts, each painted per face as seen from outside (patterns run
along the face's own rows, so they work on the shared box-UV nets: knee_joint / knee_joint_low share one, both legs
and both arms share theirs through mirror_uv):
  ribs    flexible bellows: alternating ridge / groove rows, like the waist core (abd_core)
  bands   the gauntlet band in slate: dashed row, plain row, dark seam, lit right column
  hinge   a hinge barrel: the faces round the axis shaded like a cylinder (light along the middle row, dark at the
          edges); the axis ends (east / west, all three joints hinge about X) carry a round pin cap
The user picked hinge (2026-10-20: "that really makes it the most distinct from the armor").
Elbow fix (same day, user: "the elbow arcs look a bit jumbled"): the elbow is five overlapping blocks (a front box, a
back box and three arc pieces making an octagon drum, one turned 45 deg), so per-face caps and stripes came out as a
heap of small caps at different places and angles. The elbow is painted in the forearm's frame instead: one pin cap
centred on the elbow's axis (the forearm pivot) across every side face. The faces round the axis keep the barrel
shading (painting them one flat tone per facet, by the way they face, left the elbow's front completely flat: rejected).
Cubes: knee_joint(_low), elbow_joint, elbow_joint_back, elbow_arc_0 / _45 / _90, hip_axle, hip_axle_top. The thigh
drums (cap_*) are armour and stay blue.

Run: uv run --with pillow python3 model/art/joints_art.py <source.json> ribs|bands|hinge
Prints the pixels as hex (xxyyrrggbb) for a tex.edit.
"""
import base64, io, json, math, sys
from PIL import Image

P = dict(I0='1a1d1f', I1='25292c', I2='2f3437', I3='3b4246', I4='4c5559', G2='6f797e')
CUBES = ["knee_joint_l", "knee_joint_low_l", "elbow_joint_l", "elbow_joint_back_l", "elbow_arc_0_l", "elbow_arc_45_l",
         "elbow_arc_90_l", "hip_axle", "hip_axle_top"]


def face_cells(uv):
    """[(i, j, col, row)] with i, j counted from the face's top-left as seen from outside."""
    u1, v1, u2, v2 = uv
    w, h = round(abs(u2 - u1)), round(abs(v2 - v1))
    out = []
    for i in range(w):
        for j in range(h):
            col = math.floor(u1) + i if u1 < u2 else math.ceil(u1) - 1 - i
            row = math.floor(v1) + j if v1 < v2 else math.ceil(v1) - 1 - j
            out.append((i, j, col, row))
    return out, w, h


def ribs(face, i, j, w, h):
    if face in ("up", "down"): return 'I2'
    return 'I3' if j % 2 == 0 else 'I1'


def bands(face, i, j, w, h):
    if face in ("up", "down"): return 'I2'
    r = j % 3
    if r == 2: return 'I1'
    if i == w - 1: return 'I4'
    return 'I3' if r == 0 and i % 4 in (1, 2) else 'I2'


def hinge(face, i, j, w, h):
    if face in ("up", "down"): return 'I2'
    if face in ("east", "west"):                  # the pin's end: a round cap in the middle of the face
        cx, cy = (w - 1) / 2, (h - 1) / 2
        d = math.hypot(i - cx, j - cy)
        if d < 0.8: return 'I1'
        if d < 1.9: return 'G2' if (i - cx) + (j - cy) < -1 else 'I4'
        return 'I2' if d < 2.6 else 'I1' if (i in (0, w - 1) or j in (0, h - 1)) else 'I2'
    t = abs(j + 0.5 - h / 2) / (h / 2)          # the barrel: 0 along the middle, 1 at the top / bottom edges
    return 'I4' if t < 0.25 else 'I3' if t < 0.55 else 'I2' if t < 0.85 else 'I1'


ELBOW_PIVOT = (28.625, -1.5)                    # left_forearm's origin (y, z): the elbow's axis
AX = {"x": 0, "y": 1, "z": 2}
E = {"north": ("x", 1, "y", 1, ("z", 0)), "south": ("x", 0, "y", 1, ("z", 1)), "east": ("z", 1, "y", 1, ("x", 1)),
     "west": ("z", 0, "y", 1, ("x", 0)), "up": ("x", 0, "z", 0, ("y", 1)), "down": ("x", 0, "z", 1, ("y", 0))}


def rot_x(p, o, deg):
    a = math.radians(deg); y, z = p[1] - o[1], p[2] - o[2]
    return [p[0], o[1] + y * math.cos(a) - z * math.sin(a), o[2] + y * math.sin(a) + z * math.cos(a)]


def elbow(e, face, col, row):
    """One cap on the axis across the side faces (None elsewhere: the barrel pattern)."""
    deg = (e.get("rotation") or [0])[0]
    if face in ("east", "west"):
        ua, uMax, va, vMax, (pa, pMax) = E[face]
        u1, v1, u2, v2 = e["faces"][face]["uv"]
        lo, hi = e["from"], e["to"]
        p = [0, 0, 0]
        for ax, atMax, frac in ((ua, uMax, (col + 0.5 - u1) / (u2 - u1)), (va, vMax, (row + 0.5 - v1) / (v2 - v1))):
            i = AX[ax]; t = 1 - frac if atMax else frac
            p[i] = lo[i] + t * (hi[i] - lo[i])
        p = rot_x(p, e["origin"], deg)
        dy, dz = p[1] - ELBOW_PIVOT[0], p[2] - ELBOW_PIVOT[1]
        d = math.hypot(dy, dz)
        if d < 0.8: return 'I1'
        if d < 1.9: return 'G2' if dy - dz > 1 else 'I4'          # lit on the upper front edge
        return 'I1' if d < 2.6 else 'I2'
    return None                                  # faces round the axis: the barrel, as on the knees


def main():
    src = json.load(open(sys.argv[1]))
    pat = {"ribs": ribs, "bands": bands, "hinge": hinge}[sys.argv[2]]
    tex = Image.open(io.BytesIO(base64.b64decode(src["texture"].split(",", 1)[1]))).convert("RGBA")
    els = {e["name"]: e for e in src["model"]["elements"]}
    paint = {}
    for n in CUBES:
        for face, fc in els[n]["faces"].items():
            cells, w, h = face_cells(fc["uv"])
            for i, j, col, row in cells:
                if not tex.getpixel((col, row))[3]: continue
                c = elbow(els[n], face, col, row) if pat is hinge and n.startswith("elbow_") else None
                paint[(col, row)] = P[c or pat(face, i, j, w, h)]
    sys.stderr.write(f"{len(paint)} px\n")
    print("".join(f"{x:02x}{y:02x}{c}" for (x, y), c in sorted(paint.items())))


if __name__ == "__main__":
    main()
