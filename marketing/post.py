"""Finishing pass for key-art renders: bloom around bright pixels (the square sun, highlights) and a soft vignette.

    uv run --with pillow --with numpy python3 marketing/post.py in.png out.png [bloom 0.55] [vignette 0.22] [--white]

--white: the render has a transparent background (film_transparent); lay it on pure white first (Man of Tomorrow's
comic-cover look). Use vignette 0 with it, or the corners go grey. --ink N: also an ink outline N pixels thick round
the figures' silhouettes (from the alpha, so see-through skin layers don't get a box drawn round them).
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

src, dst = sys.argv[1], sys.argv[2]

args = [x for i, x in enumerate(sys.argv[1:], 1) if not x.startswith('--') and sys.argv[i - 1] != '--ink']
bloom = float(args[2]) if len(args) > 2 else 0.55
vignette = float(args[3]) if len(args) > 3 else 0.22
im = Image.open(src)
if '--white' in sys.argv:
    im = im.convert('RGBA')
    white = Image.new('RGBA', im.size, (255, 255, 255, 255))
    if '--ink' in sys.argv:
        n = int(sys.argv[sys.argv.index('--ink') + 1])
        alpha = im.getchannel('A').point(lambda v: 255 if v > 128 else 0)
        grown = alpha.filter(ImageFilter.MaxFilter(2 * n + 1)).filter(ImageFilter.GaussianBlur(0.8))
        white.paste((20, 20, 28, 255), mask=grown)
    im = Image.alpha_composite(white, im)
im = im.convert('RGB')
a = np.asarray(im).astype(np.float32) / 255
w, h = im.size

# Bloom: keep what is brighter than the threshold, blur it wide and add it back (screen blend, no clipping).
lum = a @ np.array([0.2126, 0.7152, 0.0722], np.float32)
bright = a * np.clip((lum - 0.82) / 0.18, 0, 1)[..., None]
glow = np.zeros_like(a)
for radius, weight in ((w * 0.004, 0.5), (w * 0.015, 0.35), (w * 0.05, 0.25)):
    b = Image.fromarray((bright * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    glow += np.asarray(b).astype(np.float32) / 255 * weight
a = 1 - (1 - a) * (1 - np.clip(glow * bloom * 2, 0, 1))

# Vignette: darken the corners a little to pull the eye to the middle.
yy, xx = np.mgrid[0:h, 0:w]
r = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2) / np.sqrt(2)
a *= (1 - vignette * np.clip(r - 0.35, 0, 1) ** 1.5 / 0.65 ** 1.5)[..., None]

Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(dst)
print('wrote', dst)
