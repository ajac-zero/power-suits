"""Refit the wraparound tassets into visibly separate panels, not a closed skirt.
Run: uv run --with pillow python3 model/art/tassets_refit_art.py
Input .scratch/tassets-refit-source.json; output .scratch/tassets-refit-plan.json.
"""
import base64
import io
import json
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'tassets-refit-source.json').read_text())
m = json.loads(s['model']) if isinstance(s['model'],str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
cubes = {c['name']:c for c in m['elements'] if c['name'].startswith('tasset_')}
assert len(cubes)==6

def faces(w,h,d):
    return [(d,0,w,d),(d+w,0,w,d),(0,d,d,h),
            (d,d,w,h),(d+w,d,d,h),(2*d+w,d,w,h)]

painted = set()
parts = []
def panel(name,start,size,pivot,rotation):
    c = cubes[name]
    uv = c['uv_offset']
    key = tuple(uv)
    if key not in painted:
        old_size = [round(b-a) for a,b in zip(c['from'],c['to'])]
        w,h,d = old_size
        u,v = uv
        tex.paste((0,0,0,0),(u,v,u+2*(w+d),v+d+h))
        for old,new in zip(faces(*old_size),faces(*size)):
            x,y,fw,fh = old
            nx,ny,nfw,nfh = new
            patch = original.crop((u+x,v+y,u+x+fw,v+y+fh))
            tex.paste(patch.resize((nfw,nfh),Image.Resampling.NEAREST),(u+nx,v+ny))
        painted.add(key)
    parts.append(dict(uuid=c['uuid'],name=name,from_=start,
                      to=[a+b for a,b in zip(start,size)],origin=pivot,rotation=rotation))

# A one-pixel centre gap and open corners separate front/rear pairs.
# Shorter side panels break the continuous hemline of the previous skirt.
for facing,z,angle in [('front',-6.5,4),('back',5.5,-4)]:
    for side,x in [('left',-7.5),('right',0.5)]:
        panel(f'tasset_{facing}_{side}',[x,21.625,z],[7,5,1],
              [x+3.5,26.625,-5.5 if facing=='front' else 5.5],[angle,0,0])
for side,x,angle in [('left',-8.5,-4),('right',7.5,4)]:
    panel(f'tasset_side_{side}',[x,22.625,-4.5],[1,4,9],
          [-7.5 if side=='left' else 7.5,26.625,0],[0,0,angle])
out = io.BytesIO()
tex.save(out,format='PNG')
(scratch/'tassets-refit-plan.json').write_text(json.dumps(dict(parts=parts,texture_hex=out.getvalue().hex())))
print('Six separate panels: front/rear 7x5x1, sides 1x4x9, flare 4 degrees')
