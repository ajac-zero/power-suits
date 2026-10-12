# Tiles run/screenshots/ap_*.png into labelled contact sheets: python tools/contact-sheet.py <prefix> <out.png> [cols] [width]
import sys, glob, os
from PIL import Image, ImageDraw
prefix, out = sys.argv[1], sys.argv[2]
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 3
W = int(sys.argv[4]) if len(sys.argv) > 4 else 480
files = sorted(glob.glob(f'run/screenshots/ap_{prefix}*.png'), key=lambda f: [int(x) if x.isdigit() else x for x in os.path.basename(f).replace('.', '_').split('_')])
ims = []
for f in files:
    im = Image.open(f).convert('RGB'); h = int(im.height * W / im.width); im = im.resize((W, h))
    ImageDraw.Draw(im).rectangle([0, 0, W, 16], fill=(0, 0, 0)); ImageDraw.Draw(im).text((4, 2), os.path.basename(f)[3:-4], fill=(255, 255, 0))
    ims.append(im)
rows = (len(ims) + cols - 1) // cols; H = ims[0].height
sheet = Image.new('RGB', (cols * W, rows * H), (40, 40, 40))
for i, im in enumerate(ims): sheet.paste(im, ((i % cols) * W, (i // cols) * H))
sheet.save(out); print(out, len(ims), sheet.size)
