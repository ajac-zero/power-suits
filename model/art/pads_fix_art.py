"""Shell pads, fixes (2026-10-19, user: "upper_core clipping through the shoulders", "pad_top a little less tall").

  upper_core   x 6.85..14.15, y 32.625..40.875 stuck 0.65 px out of the pad's inner side (7.5) and its bottom out under
               the slope at the front corner: now x 7.6..14.9 (inside the inner plates' ends and the outer wall at 15),
               y 33.025 (0.1 into the fill blocks, above the slope everywhere) .. 41.6 (under the roof); net size unchanged
  elbow_joint  x 7..14 rose 0.5 px out of the pad's inner side: now x 7.75..14.25 (6.5 wide, inside the pad and the
               gauntlet with the forearm's 4 deg roll / yaw), net 6x6x5 repainted from the knee joint's art
  pad_top      2 -> 1 px (top 43.625 -> 42.625); its sides keep their top row
Run: uv run --with pillow python3 model/art/pads_fix_art.py   (input .scratch/pads/fix_source.json, a snapshot of the live
project after pads_shell_art.py); output .scratch/pads/fix_plan.json for elbow_apply.js.
"""
import base64, io, json, math
from pathlib import Path
from PIL import Image

s = json.loads(Path('.scratch/pads/fix_source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy(); W, H = tex.size
by = {e['name']: e for e in m['elements']}
size = lambda f, t: [math.floor(b - a + 1e-6) for a, b in zip(f, t)]
def faces(w, hh, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, hh), 'north': (d, d, w, hh),
            'west': (d + w, d, d, hh), 'south': (2 * d + w, d, w, hh)}
def mx(sd, f, t):            # right-side x range -> this side's from/to x
    return (f, t) if sd == 'r' else (-t, -f)

EDITS = []
for sd in 'rl':
    c = by[f'upper_core_{sd}']; x0, x1 = mx(sd, 7.6, 14.9)
    EDITS.append(dict(name=c['name'], frm=[x0, 33.025, c['from'][2]], to=[x1, 41.6, c['to'][2]]))
    assert size([x0, 33.025, 0], [x1, 41.6, 1])[:2] == size(c['from'], c['to'])[:2], 'core net size changed'
    c = by[f'pad_top_{sd}']; EDITS.append(dict(name=c['name'], frm=c['from'], to=[c['to'][0], 42.625, c['to'][2]]))
    c = by[f'elbow_joint_{sd}']; x0, x1 = mx(sd, 7.75, 14.25)
    EDITS.append(dict(name=c['name'], frm=[x0, c['from'][1], c['from'][2]], to=[x1, c['to'][1], c['to'][2]]))

# the joint's net (shared by both sides): clear the old 7x6x5 net, paint the 6x6x5 one at the same place
j = by['elbow_joint_r']; u0, v0 = j['uv_offset']; ow, oh, od = size(j['from'], j['to'])
for y in range(v0, v0 + od + oh):
    for x in range(u0, u0 + 2 * (ow + od)): tex.putpixel((x, y), (0, 0, 0, 0))
kj = by['knee_joint_r']; kw, kh, kd = size(kj['from'], kj['to']); ku, kv = kj['uv_offset']; kf = faces(kw, kh, kd)
nw, nh, nd = 6, oh, od
for f, (x, y, fw, fh) in faces(nw, nh, nd).items():
    sx, sy = kf[f][:2]
    for jj in range(fh):
        for ii in range(fw): tex.putpixel((u0 + x + ii, v0 + y + jj), live.getpixel((ku + sx + ii, kv + sy + jj)))
changed = [(x, y, *tex.getpixel((x, y))) for y in range(H) for x in range(W) if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/pads/fix_plan.json').write_text(json.dumps(dict(removed=[], edits=EDITS, cubes=[],
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print(len(EDITS), 'edits', len(changed), 'px', [(e['name'], e['frm'], e['to']) for e in EDITS])
