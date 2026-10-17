"""Shorter shins (2026-10-17, user): the shin keeps its exact shape (knee cap, 27 deg top slope, solid block down to
the boot) but its top comes down DROP px, so it covers less of the leg and the dark knee joint shows that much more.

Why: with the knee pivot in the band between thigh and shin, the shin's sloped top behind the pivot swings up into the
thigh's underside once the knee bends (clean only to ~38 deg; the walk bends to 90, the run to 122). A side-view sweep
(.scratch/kneearc/shindrop.py, the skill's livesweep metric) gives the clean knee range per drop:
    0 px: 38   1 px: 52   2 px: 72   3 px: 120   4 px: everything (130+)
The knee and ankle pivots, the thigh and the boot are untouched, so every animation stays valid (nothing re-solved).
The leg rebuild script (legs_stomper_art.py) can't re-pack the whole texture any more, so this edits the live cubes:
  shin_ramp                moved down DROP (same size, same texture)
  shin_front               the knee cap's top DROP lower, its bottom kept at 5 (moved whole, its sides z-fought the
                           boot's toe); a shorter box-UV net keeps its top rows, the lit rim
  shin_main_f / shin_main_b  DROP shorter from the top; their side faces keep their bottom rows (the dark edge along the
                             shin's bottom), repainted in place inside their old nets
  knee_joint_low           a second dark joint box DROP lower (same net, inflate -0.05) so the taller band stays filled
Run: uv run --with pillow python3 model/art/shin_drop_art.py [DROP]  (input .scratch/stomper-source.json from the
skill's scripts/snapshot.mjs, output .scratch/shin-drop-plan.json for shin_drop_apply.js)
"""
import base64, io, json, math, sys
from pathlib import Path
from PIL import Image

DROP = float(sys.argv[1]) if len(sys.argv) > 1 else 3.0
s = json.loads(Path('.scratch/stomper-source.json').read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy()
by = {e['name']: e for e in m['elements']}
size = lambda e: [math.floor(b - a + 1e-6) for a, b in zip(e['from'], e['to'])]
edits, new = [], []
for side in 'rl':
    for n in ('shin_ramp',):
        e = by[f'{n}_{side}']
        edits.append(dict(name=e['name'], from_=[e['from'][0], e['from'][1] - DROP, e['from'][2]],
                          to=[e['to'][0], e['to'][1] - DROP, e['to'][2]],
                          origin=[e['origin'][0], e['origin'][1] - DROP, e['origin'][2]]))
    for n in ('shin_front', 'shin_main_f', 'shin_main_b'):
        e = by[f'{n}_{side}']
        edits.append(dict(name=e['name'], from_=e['from'], to=[e['to'][0], e['to'][1] - DROP, e['to'][2]], origin=e['origin']))
    k = by[f'knee_joint_{side}']
    new.append(dict(name=f'knee_joint_low_{side}', group=f"{'right' if side == 'r' else 'left'}_shin",
                    from_=[k['from'][0], k['from'][1] - DROP, k['from'][2]], to=[k['to'][0], k['to'][1] - DROP, k['to'][2]],
                    origin=k['origin'], uv_offset=k['uv_offset'], mirror_uv=k.get('mirror_uv', False), inflate=-0.05))
# repaint the two shortened blocks' nets in place (both sides share one net each)
for n in ('shin_main_f_r', 'shin_main_b_r'):
    e = by[n]; w, h, d = size(e); u, v = map(int, e['uv_offset'])
    h2 = math.floor(e['to'][1] - DROP - e['from'][1] + 1e-6); cut = h - h2
    for y in range(h2):                   # side strip: keep the bottom h2 rows
        for x in range(2 * (w + d)):
            tex.putpixel((u + x, v + d + y), live.getpixel((u + x, v + d + y + cut)))
    for y in range(h2, h):                # rows the shorter net no longer uses
        for x in range(2 * (w + d)):
            tex.putpixel((u + x, v + d + y), (0, 0, 0, 0))
changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != live.getpixel((x, y))]
Path('.scratch/shin-drop-plan.json').write_text(json.dumps(dict(drop=DROP, edits=edits, cubes=new,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
print(len(edits), 'edits', len(new), 'new cubes', len(changed), 'px')
