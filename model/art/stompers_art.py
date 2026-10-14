"""Build simple metal feet from a live Blockbench snapshot (no model file writes).
Run: uv run --with pillow python3 model/art/stompers_art.py
The output plan is applied in Blockbench as one undoable edit.
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
source = json.loads((scratch / 'sloped-boots-source.json').read_text())
model = source['model']
if isinstance(model, str):
    model = json.loads(model)
tex = Image.open(io.BytesIO(base64.b64decode(source['texture'].split(',')[1]))).convert('RGBA')
width, height = tex.size
foot_ids = set()
group_names = {g['uuid']: g['name'] for g in model['groups']}
def walk(node, foot=False):
    if isinstance(node, str):
        if foot:
            foot_ids.add(node)
        return
    foot = foot or group_names.get(node.get('uuid'), node.get('name')) in ('left_foot', 'right_foot')
    for child in node.get('children', []):
        walk(child, foot)
for node in model['outliner']:
    walk(node)

def net(cube):
    w, h, d = [math.ceil(b-a) for a, b in zip(cube['from'], cube['to'])]
    u, v = cube.get('uv_offset', [0, 0])
    return int(u), int(v), 2*(w+d), d+h

# Reserve whole UV nets, not just visible painted faces.
used = set()
freed = set()
for cube in model['elements']:
    if cube.get('type') != 'cube':
        continue
    u, v, w, h = net(cube)
    pixels = {(x, y) for y in range(v, min(v+h, height)) for x in range(u, min(u+w, width))}
    (freed if cube['uuid'] in foot_ids else used).update(pixels)
for point in freed-used:
    tex.putpixel(point, (0, 0, 0, 0))
used.update((x, y) for y in range(height) for x in range(width) if tex.getpixel((x, y))[3])

def allocate(w, h):
    for v in range(height-h+1):
        for u in range(width-w+1):
            points = {(x, y) for y in range(v, v+h) for x in range(u, u+w)}
            if not points & used:
                used.update(points)
                return [u, v]
    raise RuntimeError(f'No free UV net for {w}x{h}')

def paint(uv, size, palette):
    u, v = uv
    w, h, d = size
    faces = [(u+d,v,w,d), (u+d+w,v,w,d), (u,v+d,d,h),
             (u+d,v+d,w,h), (u+d+w,v+d,d,h), (u+2*d+w,v+d,w,h)]
    for index, (x0,y0,fw,fh) in enumerate(faces):
        for y in range(fh):
            for x in range(fw):
                # Flat industrial plate: restrained rim shading, no bolts or grille.
                color = palette[0]
                if y == 0:
                    color = palette[1]
                elif y == fh-1 or x == fw-1:
                    color = palette[2]
                elif index in (2,4,5):
                    color = palette[3]
                tex.putpixel((x0+x,y0+y), (*color,255))

iron = [(94,103,109),(127,138,145),(63,72,78),(80,90,97)]
parts = [
    ('sole', [8,1,13], [-4,-0.375,-8], [(47,52,55),(69,76,80),(32,36,39),(42,47,50)]),
    ('foot', [8,2,12], [-4,0.625,-8], iron),
    ('toe', [8,2,4], [-4,1.625,-8], [(39,79,129),(67,113,165),(25,51,88),(33,65,107)]),
    ('ankle', [8,2,8], [-4,2.625,-4], [(39,79,129),(67,113,165),(25,51,88),(33,65,107)]),
]
plan = []
for name, size, offset, palette in parts:
    w,h,d = size
    uv = allocate(2*(w+d),d+h)
    paint(uv,size,palette)
    for side, center in [('l',-4.5),('r',4.5)]:
        start = [offset[0]+center,offset[1],offset[2]]
        plan.append(dict(name=f'stomper_{name}_{side}',group='left_foot' if side=='l' else 'right_foot',
                         origin=[center,2.625,-6] if name=='toe' else [center,3.625,0],from_=start,
                         rotation=[-20,0,0] if name=='toe' else [0,0,0],
                         inflate=0.05 if name in ('toe','ankle') else 0,
                         to=[a+b for a,b in zip(start,size)],uv_offset=uv,mirror_uv=side=='r'))
output = io.BytesIO()
tex.save(output,format='PNG')
(scratch/'stompers-plan.json').write_text(json.dumps(dict(remove=list(foot_ids),parts=plan,texture_hex=output.getvalue().hex())))
tex.save(scratch/'stompers-texture.png')
print('Prepared',len(plan),'cubes; replacing',len(foot_ids),'old cubes. UVs:',[p['uv_offset'] for p in plan[::2]])
