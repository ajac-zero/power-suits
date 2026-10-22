"""Finishing pass for key-art renders: bloom around bright pixels (the square sun, highlights) and a soft vignette.

    uv run --with pillow --with numpy python3 marketing/post.py in.png out.png [bloom 0.55] [vignette 0.22]
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

src, dst = sys.argv[1], sys.argv[2]
bloom = float(sys.argv[3]) if len(sys.argv) > 3 else 0.55
vignette = float(sys.argv[4]) if len(sys.argv) > 4 else 0.22

im = Image.open(src).convert('RGB')
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
