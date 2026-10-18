"""Breastplate in the texture (2026-10-18, user: the block prototypes were "too coarse; do something at the texture
level"). Paints the chest's front face (15x13 px: row 0 is the rims beside the neck notch, rows 1-12 the body; the
notch takes row 0's cols 3-11) in variants, drawn as seen from the front: col 0 = the viewer's left (+X; the chest's
face is mirrored, so texture col = 14 - col). Light from the upper left, like the rest of the suit's art.

  A  ridge + plackart: a keel down the middle (lit left of it, shaded right), the lower plate overlapping with a
     pointed top (classic Gothic breastplate), rivets at its ends, a grey roll around the neck notch
  B  boiler plate: barrel shading (lighter middle, darker edges and bottom), two riveted horizontal seams
  C  ridge + chevron: A's upper plate with the pads' white V across the chest

    uv run --with pillow python3 model/art/breastplate_art.py      -> /private/tmp/ub/bp_<v>.json + bp_preview.png
"""
import json
from PIL import Image

C = dict(H2='8ccaff', H='62a3ef', L='4588e0', M='3a7bd6', B='2d6bcc', S='2358b2', D='1b4895',
         W='c8d0d3', G='a2acb1', G2='6f797e')
U0, V0 = 157, 106          # the chest's front face in the texture (15 x 13)


def base(profile):
    g = [[profile[c] for c in range(15)] for _ in range(13)]
    for c in range(15):
        g[0][c] = 'H2' if c == 0 else 'H'               # top edge (rims)
        g[12][c] = 'D'                                  # bottom edge
    for c in range(3, 12):
        g[1][c] = 'G'                                   # grey roll around the neck notch
    for r in range(1, 12):
        g[r][14] = 'D' if g[r][14] != 'G' else 'G'
    return g


RIDGE = ['B', 'M', 'M', 'L', 'L', 'L', 'L', 'H', 'S', 'B', 'B', 'B', 'S', 'S', 'D']
DARK = {'H': 'L', 'L': 'M', 'M': 'B', 'B': 'S', 'S': 'D', 'D': 'D'}


def variant_a():
    g = base(RIDGE)
    for c in range(15):
        top = min(10, 6 + (abs(c - 7) + 1) // 2)        # plackart's pointed top edge (1 row per 2 cols)
        g[top - 1][c] = 'D'                            # shadow line under the upper plate
        g[top][c] = 'H' if c <= 7 else 'L'             # lit lip of the plackart
        for r in range(top + 1, 12):
            g[r][c] = DARK[RIDGE[c]] if c != 7 else 'L'
    for c in (1, 13):
        g[11][c] = 'G'                                 # rivets at the plackart's ends
    for c in (1, 13):
        g[2][c] = 'G'                                  # rivets under the neck rim
    return g


def variant_b():
    prof = ['S', 'B', 'M', 'M', 'L', 'L', 'L', 'L', 'L', 'M', 'M', 'B', 'B', 'S', 'D']
    g = base(prof)
    for r in range(8, 12):                             # lower part turns away from the light
        for c in range(14):
            g[r][c] = DARK[prof[c]]
    for seam in (5, 9):
        for c in range(15):
            g[seam][c] = 'D'
        for c in range(1, 14, 3):
            g[seam - 1][c] = 'W'                       # rivet heads above each seam
            g[seam + 1][c] = 'G2' if seam == 9 else g[seam + 1][c]
    return g


def variant_c():
    g = base(RIDGE)
    for r in range(6, 12):
        for c in range(15):
            g[r][c] = DARK[RIDGE[c]] if c != 7 else 'L'
    for k in range(5):                                 # the pads' V: two 1 px diagonals, white on the lit side
        g[3 + k][2 + k] = 'W'
        g[3 + k][12 - k] = 'G'
    g[8][7] = 'W'
    return g


out = {}
prev = Image.new('RGB', (3 * 15 * 12 + 40, 13 * 12), 'white')
for i, (name, fn) in enumerate((('A', variant_a), ('B', variant_b), ('C', variant_c))):
    g = fn()
    out[name] = [[U0 + 14 - c, V0 + r, C[g[r][c]]] for r in range(13) for c in range(15)]
    im = Image.new('RGB', (15, 13))
    for r in range(13):
        for c in range(15):
            im.putpixel((c, r), tuple(int(C[g[r][c]][k:k + 2], 16) for k in (0, 2, 4)))
    prev.paste(im.resize((180, 156), Image.NEAREST), (i * 200, 0))
    json.dump(out[name], open(f'/private/tmp/ub/bp_{name}.json', 'w'))
prev.save('/private/tmp/ub/bp_preview.png')
print('ok')


# --- Roman / gladiator readings (2026-10-18, user: "an actual armour aesthetic, like old gladiator armour") ----------
# M  muscle cuirass (lorica musculata): sculpted pecs with a curved underline, a sternum groove, two rows of abs between
#    the iliac furrows, rolled grey rims at the neck notch and the bottom edge
# S  lorica segmentata: two breast plates meeting at a central opening with grey buckles, three overlapping girth
#    hoops below that wrap round the sides
# Both also paint the chest's sides (11 x 13, both side faces), so the rims / hoops run round the body.
SIDES = [(146, 106), (172, 106)]
# the cuirass needs a steeper ramp than the flat plates (every letter one tone darker, plus a deep groove colour)
C.update(X='122f66')
KM = dict(h='H2', H='H2', L='H', M='L', B='M', S='S', D='X', W='W', G='G', g='G2')
K = dict(h='H2', H='H', L='L', M='M', B='B', S='S', D='D', W='W', G='G', g='G2')


def grid_m():
    # hand-drawn (one letter per pixel, see K): pecs with a dark underline arc, sternum groove, 2 x 2 abs, navel
    rows = [
        "hHHHHHHHHHHHHHH",
        "LLLGGGGGGGGGLBS",
        "LhHHLLMSHLLLMBS",
        "LHHLLMMSLLLMMBD",
        "MDLLLMBSLLMMBDD",
        "MBDLMBDSDLMBDBD",
        "BBBDDDBDBDDDBBD",
        "BBSLhLMSLLMBSSD",
        "BBSMLMBSMMBSSSD",
        "BSSDDDDDDDDSSDD",
        "BBBSLLMSLMBSSSD",
        "BBBBSMBDMBSBSSD",
        "GGGGGGGGGGGGGGg",
    ]
    return [[KM[ch] for ch in row] for row in rows]


def side_m():
    return [['H'] * 11] + [['B'] * 11 for _ in range(5)] + [['S'] * 11 for _ in range(6)] + [['G'] * 11]


def grid_s():
    g = [['B'] * 15 for _ in range(13)]
    for r in range(1, 5):                              # breast plates
        for c in range(15):
            g[r][c] = 'L' if r == 1 else ('M' if c < 7 else 'B')
    g[5] = ['D'] * 15                                  # under the breast plates
    for i, r in enumerate((6, 8, 10)):                 # girth hoops: lit top row, shaded lower row
        for c in range(15):
            g[r][c] = 'H' if c < 7 else 'L'
            g[r + 1][c] = 'S'
    for r in range(1, 12):
        g[r][7] = 'D'                                  # the opening where the two halves meet
    for r in (2, 4, 6, 8, 10):
        g[r][6] = g[r][8] = 'G'                        # buckles across it
    for r in range(1, 12):
        g[r][14] = 'D' if g[r][14] not in ('H',) else 'S'
    g[0] = ['H2'] + ['H'] * 14
    for c in range(3, 12):
        g[1][c] = 'G' if c != 7 else 'D'
    g[12] = ['D'] * 15
    return g


def side_s():
    rows = [['H'] * 11] + [['L'] * 11] + [['B'] * 11 for _ in range(3)] + [['D'] * 11]
    for r in (6, 8, 10):
        rows += [['L'] * 11, ['S'] * 11]
    return rows + [['D'] * 11]


for i, (name, fn, side) in enumerate((('M', grid_m, side_m), ('S', grid_s, side_s))):
    g, sg = fn(), side()
    px = [[U0 + 14 - c, V0 + r, C[g[r][c]]] for r in range(13) for c in range(15)]
    px += [[u + c, v + r, C[sg[r][c]]] for (u, v) in SIDES for r in range(13) for c in range(11)]
    json.dump(px, open(f'/private/tmp/ub/bp_{name}.json', 'w'))
    im = Image.new('RGB', (15 + 1 + 11, 13), 'white')
    for r in range(13):
        for c in range(15):
            im.putpixel((c, r), tuple(int(C[g[r][c]][k:k + 2], 16) for k in (0, 2, 4)))
        for c in range(11):
            im.putpixel((16 + c, r), tuple(int(C[sg[r][c]][k:k + 2], 16) for k in (0, 2, 4)))
    im.resize((27 * 14, 13 * 14), Image.NEAREST).save(f'/private/tmp/ub/bp_preview_{name}.png')
print('roman ok')
