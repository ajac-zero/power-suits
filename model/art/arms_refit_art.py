"""Refit the arms: every piece sized and centred by one rule, like the legs.

Before: the upper arms (7x7) were centred on x +-10.5 but the right forearm pieces on +-10, the
pauldrons' cavity on +-12 (8 wide, so the pauldron stood 2 px off the upper arm's outer face) and
tilted 8 / 12 degrees against the arm; the elbow was an 8x8x8 cuff on one arm and a 5x4x6 block on
the other, the wrist a 7x4x7 band. Now, per arm (axis x +-10.5, z 0):

  shoulder joint   5x4x5 dark iron on the shoulder pivot (shoulders_art.py, unchanged)
  pauldron         fits the upper arm with 0.5 px clearance: top 9x2x12, outer wall 2x5x12,
                   front/back 7x5x2; no tilt of its own (pivot on the arm axis)
  upper arm        7x12x7 blue, down to the elbow pivot (inflate 0.25, like the thighs)
  elbow joint      5x4x5 dark iron on the elbow pivot (the knee joints' size and UV nets)
  forearm (right)  8x7x8 blue gauntlet from 1 below the elbow pivot (like the shin under the knee)
  wrist (right)    5x2x5 dark iron
  hand (right)     6x3x6 + fingers, unchanged, re-centred on the axis (+0.5 px)
  forearm (left)   the socket tools (7 wide, already on the axis), unchanged

Resized cubes keep their own art: each face is cut down from the old one (middle rows/columns
dropped, edges kept). The wrist is repainted in knee iron.

Run: uv run --with pillow python3 model/art/arms_refit_art.py
Input .scratch/arms-source.json ({model, texture} of the live project);
output .scratch/arms-plan.json (cube edits, new cubes, group edits, changed pixels as hex).
"""
import base64
import io
import json
import math
from pathlib import Path
from PIL import Image

scratch = Path('.scratch')
s = json.loads((scratch/'arms-source.json').read_text())
if isinstance(s, str):
    s = json.loads(s)
m = json.loads(s['model']) if isinstance(s['model'], str) else s['model']
original = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = original.copy()
els = m['elements']
byname = {e['name']: e for e in els}


def size(c):
    return [math.ceil(b - a - 1e-6) for a, b in zip(c['from'], c['to'])]


def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h),
            'north': (d, d, w, h), 'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


def cut(img, w, h):
    """Shrink by dropping middle columns / rows (edges and trim stay as they were)."""
    def keep(n, k):
        return list(range((k + 1) // 2)) + list(range(n - k // 2, n))
    cols, rows = keep(img.width, w), keep(img.height, h)
    out = Image.new('RGBA', (w, h))
    for y, sy in enumerate(rows):
        for x, sx in enumerate(cols):
            out.putpixel((x, y), img.getpixel((sx, sy)))
    return out


def net_box(uv, sz):
    w, h, d = sz
    return (uv[0], uv[1], uv[0] + 2 * (w + d), uv[1] + d + h)


done = set()


def refit(c, new_size):
    """Repaint c's net in place for new_size (smaller or equal in every axis)."""
    uv, old = tuple(c['uv_offset']), size(c)
    if uv in done:
        return
    done.add(uv)
    assert all(n <= o for n, o in zip(new_size, old)), (c['name'], old, new_size)
    patches = {f: original.crop((uv[0] + x, uv[1] + y, uv[0] + x + w, uv[1] + y + h))
               for f, (x, y, w, h) in faces(*old).items()}
    tex.paste((0, 0, 0, 0), net_box(uv, old))
    for f, (x, y, w, h) in faces(*new_size).items():
        tex.paste(cut(patches[f], w, h), (uv[0] + x, uv[1] + y))


knee_l, knee_r = byname['knee_joint_l'], byname['knee_joint_r']
ku, kv = knee_l['uv_offset']


def iron_box(uv, sz, clear_old=None):
    if clear_old:
        tex.paste((0, 0, 0, 0), net_box(uv, clear_old))
    for f, (x, y, w, h) in faces(*sz).items():
        sx, sy, sw, sh = faces(5, 4, 5)[f]
        patch = original.crop((ku + sx, kv + sy, ku + sx + sw, kv + sy + sh))
        tex.paste(patch.resize((w, h), Image.Resampling.NEAREST), (uv[0] + x, uv[1] + y))


edits, new, groups = [], [], []


def edit(name, start, sz, **kw):
    c = byname[name]
    if sz != size(c) and 'uv_offset' not in kw and not kw.pop('repaint', None):
        refit(c, sz)
    kw.pop('repaint', None)
    edits.append(dict(name=name, from_=start, to=[a + b for a, b in zip(start, sz)], **kw))


for side, sx in [('r', 1), ('l', -1)]:
    X = lambda x0, w: x0 if sx > 0 else -(x0 + w)          # mirror a span [x0, x0 + w] to the left arm
    ax = 10.5 * sx
    pad_pivot = [ax, 42.625, 0]
    # Pauldron: cavity x 7.5..14.5 / z +-4 around the 7x7 upper arm (x 7..14, z +-3.5).
    edit(f'pad_top_{side}', [X(7.5, 9), 41.625, -6], [9, 2, 12], origin=pad_pivot)
    edit(f'pad_wall_{side}', [X(14.5, 2), 36.625, -6], [2, 5, 12], origin=pad_pivot)
    edit(f'pad_front_{side}', [X(7.5, 7), 36.625, -6], [7, 5, 2], origin=pad_pivot)
    edit(f'pad_back_{side}', [X(7.5, 7), 36.625, 4], [7, 5, 2], origin=pad_pivot)
    groups.append(dict(name=('right' if sx > 0 else 'left') + '_pad', origin=pad_pivot, rotation=[0, 0, 0]))
    edit(f'upper_arm_{side}', [X(7, 7), 28.625, -3.5], [7, 12, 7], inflate=0.25)
    knee = knee_r if sx > 0 else knee_l
    joint = dict(from_=[X(8, 5), 26.625, -2.5], to=None, uv_offset=knee['uv_offset'],
                 mirror_uv=knee.get('mirror_uv', False), origin=[ax, 28.625, 0])
    joint['to'] = [joint['from_'][0] + 5, 30.625, 2.5]
    if sx > 0:
        new.append(dict(bone='right_forearm', name='elbow_joint_r', **joint))
    else:   # the tool arm's elbow block becomes the same joint
        c = byname['elbow_l']
        tex.paste((0, 0, 0, 0), net_box(c['uv_offset'], size(c)))
        edits.append(dict(name='elbow_l', rename='elbow_joint_l', **joint))

# Right forearm: gauntlet, wrist, hand on the axis x 10.5.
edit('elbow_cuff_r', [6.5, 20.625, -4], [8, 7, 8], rename='forearm_r')
w = byname['wrist_r']
iron_box(w['uv_offset'], [5, 2, 5], clear_old=size(w))
edit('wrist_r', [8, 18.625, -2.5], [5, 2, 5], repaint=True)
for n in ['hand_r', 'finger_1', 'finger_2', 'finger_3', 'thumb']:
    c = byname[n]
    edit(n, [c['from'][0] + 0.5, c['from'][1], c['from'][2]], size(c),
         origin=[c['origin'][0] + 0.5, c['origin'][1], c['origin'][2]])
hand = next(g for g in m['groups'] if g['name'] == 'right_hand')
groups.append(dict(name='right_hand', origin=[hand['origin'][0] + 0.5, hand['origin'][1], hand['origin'][2]]))

changed = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
           if tex.getpixel((x, y)) != original.getpixel((x, y))]
(scratch/'arms-plan.json').write_text(json.dumps(dict(
    edits=edits, new=new, groups=groups,
    pixels=''.join(f'{x:02x}{y:02x}{r:02x}{g:02x}{b:02x}{a:02x}' for x, y, r, g, b, a in changed))))
tex.resize((tex.width * 4, tex.height * 4), Image.Resampling.NEAREST).save(scratch/'arms_preview.png')
print(len(edits), 'edits,', len(new), 'new,', len(groups), 'groups,', len(changed), 'px')
