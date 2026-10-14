"""Replace the hollow girdle walls with one solid hip housing.
Run: uv run --with pillow python3 model/art/solid_girdle_art.py
Input .scratch/solid-girdle-source.json; output .scratch/solid-girdle-plan.json.
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'solid-girdle-source.json').read_text())
m = json.loads(s['model']) if isinstance(s['model'],str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
walls = [c for c in m['elements'] if c['name'].startswith('girdle_armour_')]
assert len(walls) == 4
remove = [c['uuid'] for c in walls]
start = [min(c['from'][axis] for c in walls) for axis in range(3)]
end = [max(c['to'][axis] for c in walls) for axis in range(3)]
# The tassets cover the wider silhouette; the structural core matches the chest.
start[0], end[0] = -7.5, 7.5
w,h,d = [round(b-a) for a,b in zip(start,end)]
used,freed = set(),set()
for c in m['elements']:
    if c.get('type') != 'cube':
        continue
    cw,ch,cd = [math.ceil(b-a) for a,b in zip(c['from'],c['to'])]
    u,v = map(int,c.get('uv_offset',[0,0]))
    pts = {(x,y) for y in range(v,min(v+cd+ch,tex.height))
           for x in range(u,min(u+2*(cw+cd),tex.width))}
    (freed if c['uuid'] in remove else used).update(pts)
for pt in freed-used:
    tex.putpixel(pt,(0,0,0,0))
used.update((x,y) for y in range(tex.height) for x in range(tex.width) if tex.getpixel((x,y))[3])
uv = None
for v in range(tex.height-d-h+1):
    for u in range(tex.width-2*(w+d)+1):
        pts = {(x,y) for y in range(v,v+d+h) for x in range(u,u+2*(w+d))}
        if not used & pts:
            uv = [u,v]
            break
    if uv is not None:
        break
assert uv is not None, 'No free UV net for solid girdle'
u,v = uv
front = next(c for c in walls if c['name']=='girdle_armour_front')
fu,fv = front['uv_offset']
# Preserve the blue front/back panels exactly; close the mounting faces too.
for x0,fw in [(0,d),(d,w),(d+w,d),(2*d+w,w)]:
    wall_width = round(front['to'][0]-front['from'][0])
    strip = original.crop((fu+1,fv+1,fu+1+wall_width,fv+1+h))
    tex.paste(strip.resize((fw,h),Image.Resampling.NEAREST),(u+x0,v+d))
for x0 in [d,d+w]:
    for y in range(d):
        for x in range(w):
            color = (39,79,129) if y not in (0,d-1) else (33,65,107)
            tex.putpixel((u+x0+x,v+y),(*color,255))
out = io.BytesIO()
tex.save(out,format='PNG')
(scratch/'solid-girdle-plan.json').write_text(json.dumps(dict(remove=remove,name='girdle_housing',from_=start,to=end,uv_offset=uv,texture_hex=out.getvalue().hex())))
print('Solid hip housing:',[w,h,d],'UV:',uv)
