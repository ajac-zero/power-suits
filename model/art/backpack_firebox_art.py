"""Backpack v3 (2026-10-20, user): the pack fits the chest's back and the detail is painted, not modelled.

  backpack    one 14x7x5 box, x -7..7, y 31.625..38.625 (the chest's back face is x -7.5..7.5, y 31.625..38.625; the old
              pack hung 4 px below it), z 6..11 (1 px deeper than the old one, so the exhaust pipes, which lean back
              12 deg, leave through its top instead of poking out of its back). Back: the firebox, flat (steel frame,
              coals in three heat rows behind two grate bars, corner rivets). Top: a dark hole and a steel collar round
              each pipe. Sides: a steel strap.
  fuel_hatch  4x2x3 on the pack's top between the pipes, against the collar's back (where spine_top was): a steel lid
              hinged at the collar, its handle at the back edge, on a dark iron base. It's where the burnables go.

The old backpack, spine_top and firebox cubes go (their texture pixels are cleared). The coals use the reserved fire
colours, so they glow in game (SuitGlowLayer); the old ember slits under the firebox are gone: fire = 12 pixels.
Per-face UV (box_uv off). Pixel rows: row 0 = top; on `up` faces row 0 is the chest side; on `side` faces col 0 is the
back (the west face gets the rect mirrored).

usage: uv run --with pillow python3 model/art/backpack_firebox_art.py <live elements.json> <saved texture.png> <live texture.png> <plan.json>
       (elements.json: the live project's elements; see backpack_firebox_apply.js)
"""
import json, math, sys
from PIL import Image

P = {'K': '1a1d1f', 'd': '25292c', 'D': '2f3437', 'M': '3b4246', 'L': '4c5559',      # dark iron (the old pack's)
     'S': '6f797e', 'T': '7d888e', 'G': 'a2acb1', 'W': 'c9d1d5',                      # steel (the old firebox frame's)
     'y': '3f211b', 'o': '371d19', 'r': '2f1a17'}                                     # RESERVED fire glow: bottom/mid/top
FACES = {
    'backpack': {
        'south': ['MMMMMMMMMMMMMM',
                  'dLDWGGGGGGWDLd',
                  'dDDGrKrrKrSDDd',
                  'dDDGoKooKoSDDd',
                  'dDDGyKyyKySDDd',
                  'dLDWSSSSSSWDLd',
                  'KKKKKKKKKKKKKK'],
        # pipes leave the top at x +-3.5..6.5 (cols 0.5..3.5 / 10.5..13.5), z ~7.6..10.6 (rows 1..4)
        'up': ['dDDDDDDDDDDDDd',
               'SKKKSDDDDSKKKS',
               'SKKKSDLDDSKKKS',
               'SKKKSDDDDSKKKS',
               'MSSSMMMMMMSSSM'],
        'side': ['MMMMM', 'dDTDd', 'dDGDd', 'dDSDd', 'dDTDd', 'dDSDd', 'KKKKK'],
        'down': ['K' * 14] + ['d' * 14] * 3 + ['K' * 14],
        'north': ['K' * 14] * 7,
    },
    'fuel_hatch': {
        'up': ['KTTK', 'TGGT', 'SWWS'],
        'south': ['GWWG', 'MDDM'],
        'side': ['GTS', 'MDD'],
        'down': ['KKKK'] * 3,
        'north': ['KKKK'] * 2,
    },
}
CUBES = {'backpack': ([-7, 31.625, 6], [7, 38.625, 11]), 'fuel_hatch': ([-2, 38.625, 6], [2, 40.625, 9])}
OLD = ('backpack', 'spine_top', 'firebox')          # removed; their pixels are freed unless another cube shares them


def net_rects(e):
    if e.get('box_uv') is False:
        for uv in e['faces'].values():
            if uv: yield min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3])
    else:
        u, v = e.get('uv_offset') or [0, 0]
        w, h, d = [math.floor(e['to'][i] - e['from'][i] + 1e-6) for i in range(3)]
        yield u, v, u + 2 * (w + d), v + d + h


def main(elements, saved, live, out):
    els = json.load(open(elements))['elements']
    keep = [e for e in els if e['name'] not in OLD and not e['name'].startswith('bp3_')]
    old = [e for e in els if e['name'] in OLD]
    base = Image.open(saved).convert('RGBA'); W, H = base.size
    cover = lambda es: {(x, y) for e in es for (x0, y0, x1, y1) in net_rects(e)
                        for y in range(int(y0), int(math.ceil(y1))) for x in range(int(x0), int(math.ceil(x1)))}
    kept, freed = cover(keep), cover(old)
    freed = {p for p in freed if p not in kept and 0 <= p[0] < W and 0 <= p[1] < H}
    final = base.copy()
    for p in freed: final.putpixel(p, (0, 0, 0, 0))
    used = [[(x, y) in kept or final.getpixel((x, y))[3] > 0 for x in range(W)] for y in range(H)]

    def alloc(w, h):   # first fit, scanning from the old pack's net so the new faces land where it was
        order = [(x, y) for y in range(H - h + 1) for x in range(W - w + 1)]
        order.sort(key=lambda p: (abs(p[1] - 51) + abs(p[0] - 196)))
        for x, y in order:
            if all(not used[yy][xx] for yy in range(y, y + h) for xx in range(x, x + w)):
                for yy in range(y, y + h):
                    for xx in range(x, x + w): used[yy][xx] = True
                return [x, y, x + w, y + h]
        raise SystemExit(f'no room for {w}x{h}')

    cubes = []
    for name, faces in FACES.items():
        rects = {}
        for f, rows in faces.items():
            r = rects[f] = alloc(len(rows[0]), len(rows))
            for j, row in enumerate(rows):
                for i, ch in enumerate(row):
                    final.putpixel((r[0] + i, r[1] + j), tuple(int(P[ch][k:k + 2], 16) for k in (0, 2, 4)) + (255,))
        s = rects['side']
        uv = {'south': rects['south'], 'north': rects['north'], 'up': rects['up'], 'down': rects['down'],
              'east': s, 'west': [s[2], s[1], s[0], s[3]]}
        cubes.append({'name': name, 'from': CUBES[name][0], 'to': CUBES[name][1], 'uv': uv})
    fire = sum(1 for y in range(H) for x in range(W) if final.getpixel((x, y))[:3] in
               {tuple(int(P[c][k:k + 2], 16) for k in (0, 2, 4)) for c in 'yor'} and final.getpixel((x, y))[3])
    cur = Image.open(live).convert('RGBA')
    ops = [[x, y, '%02x%02x%02x%02x' % final.getpixel((x, y))] for y in range(H) for x in range(W)
           if final.getpixel((x, y)) != cur.getpixel((x, y))]
    json.dump({'cubes': cubes, 'ops': ops, 'remove': list(OLD)}, open(out, 'w'))
    print(f'freed {len(freed)} px, {len(ops)} pixel ops vs live, fire-glow pixels in the texture: {fire}')
    print(json.dumps(cubes))


if __name__ == '__main__':
    main(*sys.argv[1:5])
