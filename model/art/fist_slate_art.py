"""The fist (fist_r, 7x5x7 box net at [0, 52]) from steel/iron to slate, like the parts under the armour (the tool
bases, the plug, the joints): user, 2026-10-20 ("the glove is iron, make it slate").

The fist's grey steel (knuckle band, curled fingers, thumb) read as iron next to the slate tool bases on the other
arm. This remaps colours inside the fist's net only, keeping every pixel's place (the knuckle stripes, finger gaps,
thumb) so the shapes read the same:
  A  steel -> slate ramp (drill_base_apply.js's slate: 6f797e lit, 4c5559, 3b4246, 2f3437 fill, 25292c shade);
     the blue plating band on the sides stays (the gauntlet wrapping over the back of the hand).
  B  A + the blue band slate too: the whole fist is slate, like a tool base hanging under the gauntlet. (chosen)

Run: uv run --with pillow python3 model/art/fist_slate_art.py <texture.png> A|B
Prints the changed pixels as hex (xxyyrrggbb) for a tex.edit; writes .scratch/fist/fist_net_<variant>.png.
"""
import sys
from collections import Counter
from pathlib import Path
from PIL import Image

src, variant = sys.argv[1], sys.argv[2].upper()
tex = Image.open(src).convert('RGBA')
U, V, W, H, D = 0, 52, 7, 5, 7
box = (U, V, U + 2 * (W + D), V + D + H)

STEEL = {                     # steel / iron (hand_art.py) -> slate
    'a5aaaf': '6f797e',       # STEEL_HI: finger / knuckle highlights
    '80868c': '4c5559',       # STEEL
    '5f646a': '3b4246',       # STEEL_LO
    '30353a': '2f3437',       # IRON: palm, top
    '212529': '1f2326',       # IRON_LO: gaps between fingers (stay the darkest)
}
BLUE = {                      # the plating band (variant B only)
    '4588e0': '4c5559',       # HI: the band's lit top row
    '3a7bd6': '434b4f',       # MID: the lit corner flecks
    '2d6bcc': '3b4246',       # BASE
    '2358b2': '2f3437',       # DARK: the band's shaded end column
}
remap = dict(STEEL, **(BLUE if variant == 'B' else {}))

out, seen = [], Counter()
for y in range(box[1], box[3]):
    for x in range(box[0], box[2]):
        r, g, b, a = tex.getpixel((x, y))
        if not a:
            continue
        c = f'{r:02x}{g:02x}{b:02x}'
        seen[c] += 1
        if c in remap:
            out.append(f'{x:02x}{y:02x}{remap[c]}')
            tex.putpixel((x, y), (*bytes.fromhex(remap[c]), 255))
unknown = set(seen) - set(STEEL) - set(BLUE)
assert not unknown, f'colours not in the palette (fist repainted since?): {unknown}'
Path('.scratch/fist').mkdir(parents=True, exist_ok=True)
crop = tex.crop(box)
crop.resize((crop.width * 20, crop.height * 20), Image.Resampling.NEAREST).save(f'.scratch/fist/fist_net_{variant}.png')
print(''.join(out))
