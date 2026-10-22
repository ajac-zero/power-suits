"""Skin casting sheet: renders each candidate skin's head (3/4 from above, as a helmetless pilot shows it in the
collar) and tiles them, numbered, with the flat face texture under each, so the user can pick one by number.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/skin_lineup.py -- out.png skin1.png skin2.png ...

Body: --body renders the whole player instead of the head (for characters seen in full).
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy  # noqa: E402

import mclib as M  # noqa: E402

args = sys.argv[sys.argv.index('--') + 1:]
BODY = '--body' in args
args = [a for a in args if a != '--body']
out, skins = os.path.abspath(args[0]), [os.path.abspath(a) for a in args[1:]]
tmp = out + '.tiles'
os.makedirs(tmp, exist_ok=True)

M.reset()
M.sky_world(strength=0.35)
M.cycles(res=(500, 500 if not BODY else 800))
bpy.context.scene.cycles.samples = 32
target = (0, 0, 0.25) if not BODY else (0, 0, 1.0)
M.light('AREA', 'key', (-2, -4, 5), target, 500, size=4)
if BODY:
    M.camera((1.4, -4.6, 2.6), target, lens=60)
else:
    M.camera((0.9, -2.6, 2.0), target, lens=110)

for i, skin in enumerate(skins):
    for o in list(bpy.data.objects):
        if o.type != 'CAMERA' and o.type != 'LIGHT':
            bpy.data.objects.remove(o, do_unlink=True)
    if BODY:
        parts = M.player(f'p{i}', skin)
        parts['root'].rotation_euler = M.rad((0, 0, -145))
    else:
        e = M._empty(f'h{i}', None, (0, 0, 0))
        e.rotation_euler = M.rad((0, 0, -145))  # face 3/4 toward the camera
        M.player_head(f'h{i}', skin, e)
    M.render(os.path.join(tmp, f'{i}.png'))

# Tile with Pillow outside Blender (Blender's Python has no PIL).
script = f'''
from PIL import Image, ImageDraw, ImageFont
skins = {skins!r}; tmp = {tmp!r}; out = {out!r}
tiles = [Image.open(f"{{tmp}}/{{i}}.png").convert("RGB") for i in range(len(skins))]
W, H0 = tiles[0].size; H = H0 + 150; cols = min(5, len(tiles)); rows = (len(tiles) + cols - 1) // cols
sheet = Image.new("RGB", (W * cols, H * rows), (235, 235, 235)); d = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 60)
for i, (t, f) in enumerate(zip(tiles, skins)):
    x, y = (i % cols) * W, (i // cols) * H
    sheet.paste(t, (x, y))
    sk = Image.open(f).convert("RGBA"); face = Image.new("RGBA", (8, 8))
    face.alpha_composite(sk.crop((8, 8, 16, 16))); face.alpha_composite(sk.crop((40, 8, 48, 16)))
    d.text((x + 150, y + H0 + 35), str(i + 1), fill=(0, 0, 0), font=font)
    sheet.paste(face.resize((128, 128), Image.NEAREST).convert("RGB"), (x + 250, y + H0 + 10))
sheet.save(out)
'''
subprocess.run(['uv', 'run', '--with', 'pillow', 'python3', '-c', script], check=True)
print('lineup:', out)
for i, s in enumerate(skins):
    print(i + 1, s)
