"""Extend chest armour to the girdle while preserving the existing box-UV art.
Run: uv run --with pillow python3 model/art/chest_extension_art.py
Requires .scratch/chest-source.json; emits a live-edit plan, not a saved model.
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
source = json.loads((scratch/'chest-source.json').read_text())
model = source['model']
if isinstance(model,str):
    model = json.loads(model)
original = Image.open(io.BytesIO(base64.b64decode(source['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
names = {'chest','chest_lower'}
cubes = [c for c in model['elements'] if c['name'] in names]
assert len(cubes) == 2
used, freed = set(), set()
for c in model['elements']:
    if c.get('type') != 'cube':
        continue
    w,h,d = [math.ceil(b-a) for a,b in zip(c['from'],c['to'])]
    u,v = map(int,c.get('uv_offset',[0,0]))
    pts = {(x,y) for y in range(v,min(v+d+h,tex.height))
           for x in range(u,min(u+2*(w+d),tex.width))}
    (freed if c['name'] in names else used).update(pts)
for pt in freed-used:
    tex.putpixel(pt,(0,0,0,0))
used.update((x,y) for y in range(tex.height) for x in range(tex.width) if tex.getpixel((x,y))[3])

def allocate(w,h):
    for v in range(tex.height-h+1):
        for u in range(tex.width-w+1):
            pts = {(x,y) for y in range(v,v+h) for x in range(u,u+w)}
            if not used & pts:
                used.update(pts)
                return [u,v]
    raise RuntimeError(f'No free UV net for {w}x{h}')

parts = []
for c in cubes:
    w,h,d = [round(b-a) for a,b in zip(c['from'],c['to'])]
    new_from = c['from'].copy()
    new_from[1] = 27.625
    new_h = round(c['to'][1]-new_from[1])
    old_u,old_v = map(int,c['uv_offset'])
    u,v = allocate(2*(w+d),d+new_h)
    # The top/bottom faces are unchanged. Keep every original side row except
    # the bottom trim, extend the panel, then put the trim at the new bottom.
    for x0,fw in [(d,w),(d+w,w)]:
        patch = original.crop((old_u+x0,old_v,old_u+x0+fw,old_v+d))
        tex.paste(patch,(u+x0,v))
    for x0,fw in [(0,d),(d,w),(d+w,d),(2*d+w,w)]:
        for y in range(new_h):
            old_y = y if y<h-1 else h-2
            if y == new_h-1:
                old_y = h-1
            patch = original.crop((old_u+x0,old_v+d+old_y,old_u+x0+fw,old_v+d+old_y+1))
            tex.paste(patch,(u+x0,v+d+y))
    parts.append(dict(name=c['name'],uuid=c['uuid'],from_=new_from,uv_offset=[u,v]))
out = io.BytesIO()
tex.save(out,format='PNG')
(scratch/'chest-plan.json').write_text(json.dumps(dict(parts=parts,texture_hex=out.getvalue().hex())))
print('Extended armour:',[(p['name'],p['from_'][1],p['uv_offset']) for p in parts])
