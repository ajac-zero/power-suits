"""Clean repaint of the shoulder pads (2026-10-19, user: "a lot of noise that does not fit with the rest of the armour").

Why: the shell pads (pads_shell_art.py) resampled the old box pads' faces, so they kept the old wear speckle (1 px light
and dark marks all over the blue) and the back plate even shows the old pattern twice (it was tiled to the new height).
The newer armour (the breastplate, sm_chest_art.py) is flat armour blue with only shaded edges. Same here:
  blue faces   flat base blue 2d6bcc with one even colour per edge (the old edge colour where it was blue: lit top /
               left, shaded right / bottom). Second pass (user: "still red lines, white artifacts and iron border"):
               the red stripe, the white chevron and the grey rim are gone too; 1 px plate edges are plain blue (the
               top plate's edges lit, undersides shaded), the outer side between the plates one plain field.
  dark faces   (the shell's inside, seen under the pad) flat dark iron 2f3437 with a darker bottom row; the old ones
               carried greenish / purplish tints from the very first texture.
  underside    the ramp's downward face: one flat shaded blue (no marks).
Only pixels of the pad cubes' own nets change (each net belongs to one pad cube; asserted).

    uv run --with pillow python3 model/art/pads_clean_art.py   (reads .scratch/pads-clean/source.json from the skill's
    scripts/snapshot.mjs; writes .scratch/pads-clean/plan.json for pads_clean_apply.js + before/after previews)
"""
import base64, io, json, math
from pathlib import Path
from PIL import Image

SRC, OUT = Path('.scratch/pads-clean/source.json'), Path('.scratch/pads-clean')
s = json.loads(SRC.read_text())
m = s['model'] if isinstance(s['model'], dict) else json.loads(s['model'])
live = Image.open(io.BytesIO(base64.b64decode(s['texture'].split(',')[1]))).convert('RGBA')
tex = live.copy()

hexc = lambda h: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
BASE, IRON, IRON_DARK = hexc('2d6bcc'), hexc('2f3437'), hexc('25292c')
BLUES = {hexc(h) for h in ('8ccaff', '62a3ef', '4588e0', '3a7bd6', '2d6bcc', '2358b2', '1b4895')}
RAMP_UNDER = hexc('1f4a8e')                       # the ramp's underside tone (pad blue x 0.7), kept, without its marks


def is_iron(c):                                   # dark greys of the undersuit / shell inside (any tint)
    return max(c) < 0x55 and max(c) - min(c) < 0x20


def faces(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h), 'north': (d, d, w, h),
            'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


pads = [e for e in m['elements'] if e['name'].startswith('pad_')]
owner = {}
for e in m['elements']:                           # which cubes use which net pixels: pad nets must be the pads' alone
    w, h, d = (math.floor(b - a + 1e-6) for a, b in zip(e['from'], e['to']))
    u0, v0 = map(int, e.get('uv_offset', [0, 0]))
    if e.get('box_uv') is False: continue
    for (x, y, fw, fh) in faces(w, h, d).values():
        for j in range(fh):
            for i in range(fw): owner.setdefault((u0 + x + i, v0 + y + j), set()).add(e['name'])


C = {k: hexc(v) for k, v in dict(H='62a3ef', L='4588e0', B='2d6bcc', S='2358b2').items()}


def line_blue(px, default):
    """The edge colour of one border line: its most common armour blue (grey rim / red / white -> none), else default."""
    blues = [c for c in px if c in BLUES]
    return max(set(blues), key=blues.count) if blues else default


def clean(g, kind, face, cube):
    """g: rows of RGB. Returns the cleaned rows."""
    fh, fw = len(g), len(g[0])
    if kind == 'iron':
        return [[IRON_DARK if (j == fh - 1 and fh > 2) else IRON for i in range(fw)] for j in range(fh)]
    if kind == 'ramp':
        return [[RAMP_UNDER] * fw for _ in range(fh)]
    if min(fw, fh) <= 2:                          # a plate's 1 px edge (was the grey rim): plain blue, lit on the top
        fill = C['S'] if face == 'down' else C['L'] if cube.startswith('pad_top') else BASE
        return [[fill] * fw for _ in range(fh)]
    # a plate face: flat base blue, one even colour per edge (as on the breastplate); the old edge colour is kept
    # where it was blue, the grey rim / red stripe / white chevron are gone
    top, bottom = line_blue(g[0], C['H']), line_blue(g[-1], C['S'])
    left, right = line_blue([r[0] for r in g[1:-1]], C['L']), line_blue([r[-1] for r in g[1:-1]], C['S'])
    out = [[BASE] * fw for _ in range(fh)]
    if cube.startswith(('pad_wall', 'pad_mid')):  # the outer side between the back and front plates: one plain field
        out[0] = [top] * fw if cube.startswith('pad_wall') else out[0]
        return out
    for j in range(fh):
        out[j][0], out[j][-1] = left, right
    out[0], out[-1] = [top] * fw, [bottom] * fw
    return out


changed = 0
mine = set()  # every pad-face pixel goes into the plan (the live texture may hold an earlier version)
for e in pads:
    w, h, d = (math.floor(b - a + 1e-6) for a, b in zip(e['from'], e['to']))
    u0, v0 = map(int, e['uv_offset'])
    for f, (x, y, fw, fh) in faces(w, h, d).items():
        if fw == 0 or fh == 0: continue
        pts = [[(u0 + x + i, v0 + y + j) for i in range(fw)] for j in range(fh)]
        for row in pts:
            for p in row: assert owner[p] == {e['name']}, (e['name'], f, p, owner[p])
        g = [[live.getpixel(p)[:3] for p in row] for row in pts]
        flat = [c for row in g for c in row]
        iron = sum(map(is_iron, flat))
        kind = ('ramp' if e['name'].startswith('pad_ramp') and f == 'down' else
                'iron' if iron * 2 > len(flat) else 'blue')
        new = clean(g, kind, f, e['name'])
        for row_p, row_c in zip(pts, new):
            for p, c in zip(row_p, row_c):
                mine.add(p)
                if live.getpixel(p)[:3] != c: tex.putpixel(p, c + (255,)); changed += 1

px = [(x, y, *tex.getpixel((x, y))) for y in range(tex.height) for x in range(tex.width)
      if (x, y) in mine]
(OUT / 'plan.json').write_text(json.dumps(dict(pixels=''.join('%02x' * 6 % p for p in px))))
for name, im in (('before', live), ('after', tex)):
    bg = Image.new('RGBA', im.size, (255, 0, 255, 255)); bg.alpha_composite(im)
    bg.resize((im.width * 4, im.height * 4), Image.NEAREST).save(OUT / f'{name}.png')
print(len(pads), 'pad cubes,', changed, 'pixels changed')
