"""The end of the fist (user, 2026-10-20: "it doesn't look very good"): fist_r's bottom face and the lowest rows of its
sides, repainted in the slate ramp as curled fingers that read the same from every side.

Before: the bottom was three grey bars (2, 1, 2 px) with light columns every third pixel, the knuckle band on the back
of the hand was a 2-row stripe pattern copied unchanged onto the little-finger side, so nothing lined up round the
corners. Now the fist ends in four fingers (1 px each, 1 px dark gaps: pinky at the back, index at the front under
the thumb) that run across the bottom from the knuckles (outer side, lit) into the palm (inner side, shaded), and the
sides continue them: knuckle caps on the back of the hand, fingertips curled into the palm, the pinky's side at the back.

Face layout (measured on the live mesh): bottom = columns x outer->palm, rows z back->front; east (outer) columns
z back->front; west (palm) columns z front->back; south (back) columns x palm->outer; north (front) x outer->palm.
  A  fingers only.
  B  (chosen) A + a knuckle plate: a solid lit bar over the knuckles (the bottom's outer two columns and the back of the hand's
     lowest two rows), the fingers come out from under it.

Run: uv run --with pillow python3 model/art/fist_end_art.py <texture.png> A|B
Prints the changed pixels as hex (xxyyrrggbb) for a tex.edit; writes .scratch/fist/end_net_<variant>.png.
"""
import sys
from pathlib import Path
from PIL import Image

src, variant = sys.argv[1], sys.argv[2].upper()
tex = Image.open(src).convert('RGBA')
orig = tex.copy()
U, V, W, H, D = 0, 52, 7, 5, 7
LIT, HI, MID, FILL, BASE, SHADE, GAP = '6f797e', '5d666b', '4c5559', '434b4f', '3b4246', '2f3437', '1f2326'
DEEP = '25292c'


def put(x, y, c):
    tex.putpixel((x, y), (*bytes.fromhex(c), 255))


finger = lambda r: r % 2 == 0                  # rows / columns 0, 2, 4, 6 are fingers, 1, 3, 5 gaps
plate = variant == 'B'

# Bottom face: (U+D+W, V), 7 x 7; column c = x from outer (0) to palm (6), row r = z from back (0) to front (6).
ALONG = [LIT, MID, FILL, BASE, BASE, SHADE, DEEP]   # a finger lit at its knuckle, shaded as it curls into the palm
for r in range(D):
    for c in range(W):
        if plate and c < 2:
            col = LIT if c == 0 else HI
        elif finger(r):
            col = ALONG[c]
        else:
            col = GAP if c > 0 else SHADE        # the gaps open behind the knuckle edge
        put(U + D + W + c, V + r, col)

SY = V + D                                     # side strip top row; the fist's sides are 5 rows (0..4)
# East (outer, back of the hand): rows 3-4 = knuckle caps; column k = z from back, so finger row r = k.
for k in range(D):
    if plate:
        put(U + k, SY + 3, LIT)
        put(U + k, SY + 4, HI if 0 < k < D - 1 else MID)
    else:
        put(U + k, SY + 3, MID if finger(k) else SHADE)
        put(U + k, SY + 4, LIT if finger(k) else GAP)
# West (palm): rows 2-4 = the fingertips curled in; column k = z from front (symmetric pattern, same gaps).
for k in range(D):
    for row, col in ((2, SHADE), (3, BASE), (4, MID)):
        put(U + D + W + k, SY + row, col if finger(k) else GAP)
# South (back, the pinky's side): rows 3-4 = one finger seen from the side, palm (column 0) -> knuckle (column 6).
for k in range(W):
    put(U + 2 * D + W + k, SY + 3, [SHADE, BASE, BASE, FILL, FILL, MID, LIT][k] if not (plate and k >= 5) else LIT)
    put(U + 2 * D + W + k, SY + 4, [DEEP, SHADE, BASE, BASE, FILL, MID, MID][k] if not (plate and k >= 5) else HI)
# North (front): the thumb (rows 2-4) stays; with the plate, its outer columns (0-1) carry the knuckle bar's end.
if plate:
    for row in (3, 4):
        for k in (0, 1):
            put(U + D + k, SY + row, LIT if row == 3 else HI)

out = ''.join(f'{x:02x}{y:02x}{"%02x%02x%02x" % tex.getpixel((x, y))[:3]}'
              for y in range(V, V + D + H) for x in range(U, U + 2 * (D + W)) if tex.getpixel((x, y)) != orig.getpixel((x, y)))
Path('.scratch/fist').mkdir(parents=True, exist_ok=True)
crop = tex.crop((U, V, U + 2 * (D + W), V + D + H))
crop.resize((crop.width * 20, crop.height * 20), Image.Resampling.NEAREST).save(f'.scratch/fist/end_net_{variant}.png')
print(out)
