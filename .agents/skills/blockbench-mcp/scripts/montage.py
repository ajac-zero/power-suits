"""Tile images into one labelled sheet: montage.py out.jpg [--cols N] shot_*.jpg [--row shot_*.jpg ...]
Labels are the file names after 'shot_' (and a leading 'NN_' sort index). Default: one row;
--cols N wraps every N images, --row starts a new row."""
import re
import sys
from PIL import Image, ImageDraw

args = sys.argv[1:]
out = args.pop(0)
cols = None
if args and args[0] == '--cols':
    cols = int(args[1]); args = args[2:]
groups = [[]]
for a in args:
    if a == '--row':
        groups.append([])
    else:
        groups[-1].append(a)
files = [f for g in groups for f in g]
ims = [Image.open(f).convert('RGB') for f in files]
rows, k = [], 0
for g in groups:
    n = cols or len(g) or 1
    gi = ims[k:k + len(g)]; k += len(g)
    rows += [gi[i:i + n] for i in range(0, len(gi), n)]
names = [re.sub(r'^\d+_', '', f.split('shot_')[-1].rsplit('.', 1)[0]) for f in files]
W = max(sum(i.width for i in r) for r in rows)
H = sum(max(i.height for i in r) + 14 for r in rows)
M = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(M)
y, k = 0, 0
for r in rows:
    x = 0
    for i in r:
        M.paste(i, (x, y + 14)); d.text((x + 4, y + 1), names[k], fill='black'); x += i.width; k += 1
    y += max(i.height for i in r) + 14
M.save(out, quality=85)
