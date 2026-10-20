"""The suit's armour from blue paint to bare polished steel (user, 2026-10-20: variant B of .scratch/metal/variants.py).

The armour was painted in one blue ramp (flat 2d6bcc, plate rims, tuck shadows, edge columns, marks). Each pixel of it
(hue 205-225, saturation >= 0.44, brightness >= 0.3: not the grey-blue slate joints, the steel trim, the glow colours
or the pilot's head) snaps by luminance to the nearest step of that ramp and takes the same step of a steel ramp, so the
shading recipe stays as painted. What makes it read as metal, not grey plastic (the 10-19 vanilla-iron recolours):
  - a wide value range: shadows go down to 22262e, rims and marks up to near white;
  - hue shift: the shadows lean cool (blue-grey), the lit steps neutral and the glints warm (fbf8f0).
Usage: uv run --with pillow python3 model/art/steel_art.py in.png out.png [out.hex]
(the .hex is the whole texture as RGBA hex, for a tex.edit in Blockbench, e.g. .scratch/recolor/settex.mjs)
"""
import colorsys
import sys

from PIL import Image

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))
lum = lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
#         deep      D(seam)   S(shade)  B(main)   mid       L(lit)    rim       mark
BLUE  = ['122f66', '1b4895', '2358b2', '2d6bcc', '3a7bd6', '4588e0', '62a3ef', '8ccaff']
STEEL = ['22262e', '3c424c', '58606a', '757c85', '8a9097', '9fa4a9', 'cdcfcf', 'fbf8f0']
ANCH = [lum(H(c)) for c in BLUE]


def is_armour(p):
    h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in p[:3]))
    return p[3] > 0 and 205 <= h * 360 <= 225 and s >= 0.44 and v >= 0.3


def main():
    im = Image.open(sys.argv[1]).convert('RGBA')
    px, n = im.load(), 0
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if is_armour(p):
                i = min(range(len(ANCH)), key=lambda k: abs(ANCH[k] - lum(p)))
                px[x, y] = H(STEEL[i]) + (p[3],)
                n += 1
    im.save(sys.argv[2])
    if len(sys.argv) > 3:
        open(sys.argv[3], 'w').write(im.tobytes().hex())
    print(n, 'armour pixels recoloured')


if __name__ == '__main__':
    main()
