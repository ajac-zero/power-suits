"""The floor inside the collar in slate (user, 2026-10-20: "color the part of the chest top layer inside of the collar
slate, so it looks like that is where the player pokes his head through and also where the helmet connects. Right now
it just looks like a blue metal wall").

The floor (y 38.625 / 38.675, inside the collar ring x -5.5..5.5, z -6.5..4.9) is three top faces, all per-face UV and
used by no other cube (checked against every face's UV rectangle on the live project):
  chest       up uv [157,106 -> 172,95]  x -7.5..7.5 = u 157..172, z -5 = v 106 .. z 6 = v 95: cols 159..169, rows 96..105
  bp_main     up uv [34,17 -> 47,19]     the groove behind the neck guard, z -6..-5 (row 18, row 17 mostly under the lip)
  gorget_lip  up uv [112,49 -> 123.2,50.4]  the 0.5 px of lip between the guard and the groove (row 50)
Painted in the slate of the parts under the armour (drill_base_apply.js / fist_slate_art.py ramp):
  * the neck opening: the helmet's footprint (x -4.5..4.5, z -4..4.9) dark (I1) with a 1 px I2 gasket at its edge; with
    the helmet off the pilot's head stands in it,
  * the helmet seat: the 1 px ring between the footprint and the collar walls (sides + front), lighter slate (I4): the
    flange the helmet locks onto, the only part of the floor still seen with the helmet on,
  * the groove in front: slate fill (I3), shadowed (I2) under the neck guard.
Outside the collar (chest_top_r/l, cols 171..172) nothing changes.

Run: uv run --with pillow python3 model/art/collar_floor_art.py <texture.png>
Prints the pixels as hex (xxyyrrggbb) for a tex.edit; writes .scratch/collar-slate/floor_preview.png.
"""
import sys
from pathlib import Path
from PIL import Image

I1, I2, I3, I4 = '25292c', '2f3437', '3b4246', '4c5559'
BLUES = {'2d6bcc', '3a7bd6', '1b4895', '4588e0', '8ccaff', '2358b2', '62a3ef'}   # what's there now (checked)

tex = Image.open(sys.argv[1]).convert('RGBA')
paint = {}
for i in range(11):                 # x = -5.5 + i
    for j in range(10):             # z = -5 + j  (chest row 105 - j)
        ring = i in (0, 10) or j == 0
        edge = i in (1, 9) or j in (1, 9)
        paint[(159 + i, 105 - j)] = I4 if ring else I2 if edge else I1
    paint[(35 + i, 18)] = I3        # groove, z -6..-5
    paint[(35 + i, 17)] = I3        # its back sliver (z -6.05..-6) under the lip's edge
for c in range(112, 124):
    paint[(c, 50)] = I2             # the lip under the guard

out = []
for (x, y), col in sorted(paint.items()):
    r, g, b, a = tex.getpixel((x, y))
    assert a and f'{r:02x}{g:02x}{b:02x}' in BLUES, f'pixel {x},{y} is not the old blue (repainted since?)'
    out.append(f'{x:02x}{y:02x}{col}')
    tex.putpixel((x, y), (*bytes.fromhex(col), 255))
Path('.scratch/collar-slate').mkdir(parents=True, exist_ok=True)
crop = tex.crop((155, 93, 176, 108))
crop.resize((crop.width * 20, crop.height * 20), Image.Resampling.NEAREST).save('.scratch/collar-slate/floor_preview.png')
print(''.join(out))
