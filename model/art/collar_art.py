"""Narrow the collars (beside the helmet) from 3 to 2 px wide: x 4.5..6.5 instead of 4.5..7.5.

With the arms moved in (shoulders_art.py) the pauldrons' inner edge dipped into the collars in
walk/run/jump/fall (up to 1.5 px). One pixel less clears them at rest and in walk/run.
The cubes are resized live; this repaints their box-UV nets for the new size (each face copied
from the old one, 3 columns squeezed to 2 on the faces that lost width) and clears the 2 freed
columns at the end of each net.

Run: uv run --with pillow python3 model/art/collar_art.py
Input .scratch/collar-source.json ({model, texture} with the collars already 2 wide);
output .scratch/collar-plan.json (changed pixels as hex).
"""
import base64
import io
import json
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'collar-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()

def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
            'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}

old, new = faces(3, 5, 5), faces(2, 5, 5)
for c in m['elements']:
    if c['name'] not in ('collar_l', 'collar_r'):
        continue
    assert [round(b - a) for a, b in zip(c['from'], c['to'])] == [2, 5, 5], c['name']
    u, v = c['uv_offset']
    patches = {f: original.crop((u + x, v + y, u + x + w, v + y + h)) for f, (x, y, w, h) in old.items()}
    tex.paste((0, 0, 0, 0), (u, v, u + 16, v + 10))           # the old 16x10 net
    for f, (x, y, w, h) in new.items():
        tex.paste(patches[f].resize((w, h), Image.Resampling.NEAREST), (u + x, v + y))

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
(scratch/'collar-plan.json').write_text(json.dumps(dict(
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print('changed px', len(changed))
