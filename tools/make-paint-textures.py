"""Builds the painted suit textures, one per dye (run after node tools/export-model.mjs):

    uv run --with pillow python3 tools/make-paint-textures.py [base.png] [outdir]

The suit is bare polished steel (model/art/steel_art.py); right-clicking it with a dye paints its armour plates
(PowerArmorEntity.paintWith). Painting is a palette swap, the way Minecraft builds armour trims: the steel armour is
drawn in eight shades (STEEL, deep shadow .. glint), and on the plates each shade becomes the same step of the dye's
ramp. So every dye keeps the plates' shading (rims, tuck shadows, marks) and a repaint of the steel in Blockbench
repaints every dye with it.

Which parts are plates (user, 2026-10-20: "two-tone"): the shoulder pads, upper arms, chest + breastplate, hip hoods
and thighs (PLATES, cube names in the .bbmodel). The helmet, collar, gauntlets, shins + boots (they read as one
piece with the feet), backpack and waist stay steel. Their texels are found through each cube's faces, so the
layout follows the model.

The ramps: blue is the suit's old paint exactly (the blue dye gives back the pre-steel colours); every other dye is
built from its main colour like the blue: each step keeps the blue's lightness offset from its main shade (scaled
by RANGE: white and black need a narrower one) and chroma profile, and the hue leans warm in the lights and cool in
the shadows (yellows shade towards orange). The printed ramps can be pinned by hand in FIXED.
Output: textures/entity/paint/power_armor_<dye>.png (PowerArmorModel picks one by the suit's paint).
"""
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / 'src/main/resources/assets/powerarmor/textures/entity'
MODEL = ROOT / 'model/power_armor.bbmodel'
H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))
hexs = lambda c: '%02x%02x%02x' % c

#           deep      seam      shade     main      mid       lit       rim       mark
STEEL    = ['22262e', '3c424c', '58606a', '757c85', '8a9097', '9fa4a9', 'cdcfcf', 'fbf8f0']
OLD_BLUE = ['122f66', '1b4895', '2358b2', '2d6bcc', '3a7bd6', '4588e0', '62a3ef', '8ccaff']
PLATES = re.compile(r'^(pad_|upper_core|chest$|bp_|hood_|thigh_|cap_)')

# dye -> (main colour, lightness range): vanilla's dye colours, tuned to read as paint next to the steel
DYES = {
    'white': ('e9ebe7', 0.5), 'orange': ('dd6e1e', 1.0), 'magenta': ('b4439c', 1.0), 'light_blue': ('3c9fd2', 1.0),
    'yellow': ('d9a72a', 1.0), 'lime': ('6aa823', 1.0), 'pink': ('dc7d9b', 1.0), 'gray': ('5e5f5a', 0.9),
    'light_gray': ('a19f97', 0.8), 'cyan': ('18938f', 1.0), 'purple': ('7a3aae', 1.0), 'blue': (OLD_BLUE[3], 1.0),
    'brown': ('7a4e2e', 1.0), 'green': ('4f7a1c', 1.0), 'red': ('b02e26', 1.0), 'black': ('2c2d31', 0.95),
}
FIXED = {'blue': OLD_BLUE}


# ---- OKLab / OKLCH (a perceptual colour space: equal steps look equal) ----
def _lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _gam(c):
    return c * 12.92 if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def to_lch(rgb):
    r, g, b = (_lin(v) for v in rgb)
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return L, math.hypot(A, B), math.degrees(math.atan2(B, A)) % 360


def from_lch(L, C, h):
    def rgb(C):
        A, B = C * math.cos(math.radians(h)), C * math.sin(math.radians(h))
        l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3
        m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3
        s = (L - 0.0894841775 * A - 1.2914855480 * B) ** 3
        return (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
    while C > 0 and not all(-1e-4 <= v <= 1 + 1e-4 for v in rgb(C)):
        C -= 0.002  # pull the chroma in until the colour exists in sRGB
    return tuple(round(255 * _gam(min(1, max(0, v)))) for v in rgb(max(C, 0)))


def wrap(d):
    return (d + 180) % 360 - 180


def ramp(main, rng, warm=70):
    BL = [to_lch(H(c)) for c in OLD_BLUE]
    L0, C0, h0 = to_lch(H(main))
    out = []
    for L, C, _ in BL:
        dl = (L - BL[3][0]) * rng
        hh = h0
        if C0 > 0.02:  # coloured dyes only: greys stay grey
            target = 100 if dl > 0 else (40 if 70 <= h0 <= 120 else 265)
            hh = h0 + math.copysign(min(abs(dl) * warm, abs(wrap(target - h0))), wrap(target - h0))
        out.append(hexs(from_lch(min(0.98, max(0.06, L0 + dl)), C0 * C / BL[3][1], hh)))
    return out


def plate_texels(model):
    """Texels drawn on a plate cube's faces (box UV or per-face UV alike)."""
    out = set()
    for e in model['elements']:
        if not PLATES.match(e['name']):
            continue
        for f in e['faces'].values():
            u1, v1, u2, v2 = f['uv']
            for x in range(math.floor(min(u1, u2)), math.ceil(max(u1, u2))):
                for y in range(math.floor(min(v1, v2)), math.ceil(max(v1, v2))):
                    out.add((x, y))
    return out


def main():
    base = Path(sys.argv[1]) if len(sys.argv) > 1 else TEX / 'power_armor.png'
    outdir = Path(sys.argv[2]) if len(sys.argv) > 2 else TEX / 'paint'
    outdir.mkdir(parents=True, exist_ok=True)
    src = Image.open(base).convert('RGBA')
    sp = src.load()
    step = {H(c): i for i, c in enumerate(STEEL)}
    plates = [(x, y) for x, y in plate_texels(json.load(open(MODEL)))
              if 0 <= x < src.width and 0 <= y < src.height and sp[x, y][3] and sp[x, y][:3] in step]
    if len(plates) < 1000:
        sys.exit(f'only {len(plates)} paintable texels: is {base} the steel texture?')
    print(len(plates), 'paintable texels')
    for dye, (main_colour, rng) in DYES.items():
        r = FIXED.get(dye) or ramp(main_colour, rng)
        im = src.copy()
        px = im.load()
        for xy in plates:
            px[xy] = H(r[step[px[xy][:3]]]) + (255,)
        im.save(outdir / f'power_armor_{dye}.png')
        print(f'{dye:11s}', ' '.join(r))


if __name__ == '__main__':
    main()
