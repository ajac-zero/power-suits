"""Mod icon: lays the helmet render (transparent PNG) on a badge background and writes the finished square icon.

    uv run --with pillow --with numpy python3 marketing/icon_compose.py helmet_raw.png icon.png [size 1024] [--hue blue|steel]

Background: a radial gradient (deep blue-steel, lighter in the middle), a thin ring plate behind the helmet, a soft
contact shadow under it, and a warm bloom where the visor glows. Modrinth rounds the corners itself.
"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

src, dst = sys.argv[1], sys.argv[2]
size = int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3].isdigit() else 1024
helm = Image.open(src).convert('RGBA').resize((size, size), Image.LANCZOS)

yy, xx = np.mgrid[0:size, 0:size].astype(np.float32) / size
r = np.sqrt((xx - 0.5) ** 2 + (yy - 0.47) ** 2)
t = np.clip(r / 0.72, 0, 1)[..., None]
inner, outer = np.array([62, 105, 156], np.float32), np.array([16, 30, 54], np.float32)
bg = inner * (1 - t) + outer * t
im = Image.fromarray(bg.astype(np.uint8)).convert('RGBA')

# ring plate: a darker disc with a light rim
d = ImageDraw.Draw(im, 'RGBA')
c, R = size / 2, size * 0.43
d.ellipse((c - R, c - R, c + R, c + R), fill=(24, 44, 76, 110))
d.ellipse((c - R, c - R, c + R, c + R), outline=(150, 175, 205, 170), width=max(2, size // 120))
R2 = R * 0.93
d.ellipse((c - R2, c - R2, c + R2, c + R2), outline=(150, 175, 205, 60), width=max(1, size // 250))

# warm bloom behind the helmet (the visor's light)
glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((size * .22, size * .25, size * .78, size * .75), fill=(255, 150, 40, 150))
im = Image.alpha_composite(im, glow.filter(ImageFilter.GaussianBlur(size * 0.09)))

# contact shadow
a = helm.getchannel('A')
sh = Image.new('RGBA', (size, size), (0, 0, 0, 0))
sh.putalpha(a.point(lambda v: int(v * 0.55)))
sh = sh.filter(ImageFilter.GaussianBlur(size * 0.025))
im.alpha_composite(sh, (int(size * 0.012), int(size * 0.03)))
im.alpha_composite(helm)
im.convert('RGB').save(dst)
print('wrote', dst)
