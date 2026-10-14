"""Prepare a knee-style abdomen bearing and shorten the girdle from the top.
Run: uv run --with pillow python3 model/art/abdomen_joint_art.py
Input .scratch/abdomen-source.json; output .scratch/abdomen-plan.json.
"""
import base64
import io
import json
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'abdomen-source.json').read_text())
m = json.loads(s['model']) if isinstance(s['model'],str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
by_name = {c['name']:c for c in m['elements']}

def size(c):
    return [round(b-a) for a,b in zip(c['from'],c['to'])]

def faces(w,h,d):
    return [(d,0,w,d),(d+w,0,w,d),(0,d,d,h),
            (d,d,w,h),(d+w,d,d,h),(2*d+w,d,w,h)]

def remap(source,target,new_size):
    su,sv = source['uv_offset']
    u,v = target['uv_offset']
    w,h,d = size(target)
    # These are existing owned nets; resizing only makes them smaller.
    tex.paste((0,0,0,0),(u,v,u+2*(w+d),v+d+h))
    for old,new in zip(faces(*size(source)),faces(*new_size)):
        x,y,fw,fh = old
        nx,ny,nfw,nfh = new
        patch = original.crop((su+x,sv+y,su+x+fw,sv+y+fh))
        tex.paste(patch.resize((nfw,nfh),Image.Resampling.NEAREST),(u+nx,v+ny))

abdomen = by_name['abdomen']
remap(by_name['knee_joint_l'],abdomen,[11,3,8])
parts = [dict(uuid=abdomen['uuid'],name='abdomen',from_=[-5.5,25.625,-3.5],
              to=[5.5,28.625,4.5],parent='body')]
painted = set()
for name in ['girdle_armour_front','girdle_armour_back','girdle_armour_left','girdle_armour_right']:
    c = by_name[name]
    w,h,d = size(c)
    key = tuple(c['uv_offset'])
    if key not in painted:
        remap(c,c,[w,2,d])
        painted.add(key)
    end = c['to'].copy()
    end[1] = 26.625
    parts.append(dict(uuid=c['uuid'],name=name,from_=c['from'],to=end))
out = io.BytesIO()
tex.save(out,format='PNG')
(scratch/'abdomen-plan.json').write_text(json.dumps(dict(parts=parts,pivot=[0,27.125,0.5],texture_hex=out.getvalue().hex())))
print('Abdomen: 11x3x8; girdle: 2 pixels high; torso pivot: [0,27.125,0.5]')
