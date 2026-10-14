"""Prepare six blue wraparound tassets over the solid girdle.
Run: uv run --with pillow python3 model/art/tassets_art.py
Input .scratch/tassets-source.json; output .scratch/tassets-plan.json.
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'tassets-source.json').read_text())
m = json.loads(s['model']) if isinstance(s['model'],str) else s['model']
tex = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
used = {(x,y) for y in range(tex.height) for x in range(tex.width) if tex.getpixel((x,y))[3]}
for c in m['elements']:
    if c.get('type')!='cube':
        continue
    w,h,d = [math.ceil(b-a) for a,b in zip(c['from'],c['to'])]
    u,v = map(int,c.get('uv_offset',[0,0]))
    used.update((x,y) for y in range(v,min(v+d+h,tex.height))
                for x in range(u,min(u+2*(w+d),tex.width)))

def allocate(w,h):
    for v in range(tex.height-h+1):
        for u in range(tex.width-w+1):
            pts = {(x,y) for y in range(v,v+h) for x in range(u,u+w)}
            if not used & pts:
                used.update(pts)
                return [u,v]
    raise RuntimeError(f'No free UV net for tasset {w}x{h}')

def paint(uv,size):
    u,v = uv
    w,h,d = size
    faces = [(d,0,w,d),(d+w,0,w,d),(0,d,d,h),
             (d,d,w,h),(d+w,d,d,h),(2*d+w,d,w,h)]
    for i,(x0,y0,fw,fh) in enumerate(faces):
        for y in range(fh):
            for x in range(fw):
                color = (39,79,129)
                if i == 1:
                    color = (73,81,86)
                elif i == 0 or y == 0:
                    color = (67,113,165)
                elif y == fh-1:
                    color = (25,51,88)
                elif i in (2,4) or (i in (3,5) and x == fw-1):
                    color = (33,65,107)
                tex.putpixel((u+x0+x,v+y0+y),(*color,255))

parts = []
uvs = {}
for facing,z,angle in [('front',-6.5,8),('back',5.5,-8)]:
    for side,x in [('left',-9),('right',0)]:
        size = [9,5,1]
        key = tuple(size)
        if key not in uvs:
            uvs[key] = allocate(20,6)
            paint(uvs[key],size)
        start = [x,21.625,z]
        parts.append(dict(name=f'tasset_{facing}_{side}',from_=start,
                          to=[a+b for a,b in zip(start,size)],
                          origin=[x+4.5,26.625,-5.5 if facing=='front' else 5.5],
                          rotation=[angle,0,0],uv_offset=uvs[key],mirror_uv=side=='right'))
for side,x,angle in [('left',-9.5,-8),('right',8.5,8)]:
    size = [1,5,13]
    key = tuple(size)
    if key not in uvs:
        uvs[key] = allocate(28,18)
        paint(uvs[key],size)
    start = [x,21.625,-6.5]
    parts.append(dict(name=f'tasset_side_{side}',from_=start,
                      to=[a+b for a,b in zip(start,size)],
                      origin=[-8.5 if side=='left' else 8.5,26.625,0],
                      rotation=[0,0,angle],uv_offset=uvs[key],mirror_uv=side=='right'))
out = io.BytesIO()
tex.save(out,format='PNG')
(scratch/'tassets-plan.json').write_text(json.dumps(dict(parts=parts,texture_hex=out.getvalue().hex())))
print('Prepared',len(parts),'tassets; UV nets:',list(uvs.values()))
