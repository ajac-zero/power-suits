"""Prepare a hollow blue waist girdle, replacing the old belt and hanging guards.
Input: .scratch/girdle-source.json (live Blockbench snapshot).
Output: .scratch/girdle-plan.json; does not modify the saved model.
Run with: uv run --with pillow python3 model/art/girdle_art.py
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
source = json.loads((scratch/'girdle-source.json').read_text())
model = source['model']
if isinstance(model, str):
    model = json.loads(model)
tex = Image.open(io.BytesIO(base64.b64decode(source['texture'].split(',')[1]))).convert('RGBA')
remove_names = {'pelvis','codpiece','back_cover','seat','belt'}
remove = [c['uuid'] for c in model['elements'] if c['name'] in remove_names]
assert len(remove) == 5
used, freed = set(), set()
for cube in model['elements']:
    if cube.get('type') != 'cube':
        continue
    w,h,d = [math.ceil(b-a) for a,b in zip(cube['from'],cube['to'])]
    u,v = map(int,cube.get('uv_offset',[0,0]))
    points = {(x,y) for y in range(v,min(v+d+h,tex.height))
              for x in range(u,min(u+2*(w+d),tex.width))}
    (freed if cube['uuid'] in remove else used).update(points)
for point in freed-used:
    tex.putpixel(point,(0,0,0,0))
used.update((x,y) for y in range(tex.height) for x in range(tex.width) if tex.getpixel((x,y))[3])

def allocate(w,h):
    for v in range(tex.height-h+1):
        for u in range(tex.width-w+1):
            points = {(x,y) for y in range(v,v+h) for x in range(u,u+w)}
            if not points & used:
                used.update(points)
                return [u,v]
    raise RuntimeError(f'No free space for UV net {w}x{h}')

def paint(uv,size,palette):
    u,v = uv
    w,h,d = size
    faces = [(u+d,v,w,d),(u+d+w,v,w,d),(u,v+d,d,h),
             (u+d,v+d,w,h),(u+d+w,v+d,d,h),(u+2*d+w,v+d,w,h)]
    for i,(x0,y0,fw,fh) in enumerate(faces):
        for y in range(fh):
            for x in range(fw):
                color = palette[0]
                if i < 2 or y == 0:
                    color = palette[1]
                elif y == fh-1 or x == fw-1:
                    color = palette[2]
                elif i in (2,4,5):
                    color = palette[3]
                tex.putpixel((x0+x,y0+y),(*color,255))

blue = [(39,79,129),(67,113,165),(25,51,88),(33,65,107)]
# Hollow ring: the side walls meet the front/back walls without overlapping.
# The blue band stops one pixel above the thighs; no lower metal trim.
shapes = [
    ('front',[-8.5,24.625,-5.5],[17,3,1]),
    ('back',[-8.5,24.625,4.5],[17,3,1]),
    ('left',[-8.5,24.625,-4.5],[1,3,9]),
    ('right',[7.5,24.625,-4.5],[1,3,9]),
]
parts = []
uvs = {}
for material,palette in [('armour',blue)]:
    for side,start,size in shapes:
        start, size = start.copy(),size.copy()
        # Matching opposite walls share the same UV net.
        key = (material,tuple(size))
        if key not in uvs:
            w,h,d = size
            uvs[key] = allocate(2*(w+d),d+h)
            paint(uvs[key],size,palette)
        parts.append(dict(name=f'girdle_{material}_{side}',from_=start,
                          to=[a+b for a,b in zip(start,size)],origin=[0,24.625,0],
                          uv_offset=uvs[key],mirror_uv=side=='right'))
out = io.BytesIO()
tex.save(out,format='PNG')
(scratch/'girdle-plan.json').write_text(json.dumps(dict(remove=remove,parts=parts,texture_hex=out.getvalue().hex())))
print('Prepared hollow girdle:',len(parts),'cubes; replaces',len(remove),'old cubes. UVs:',list(uvs.values()))
