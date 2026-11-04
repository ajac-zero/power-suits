#!/usr/bin/env python3
"""Builds model/power_frame.bbmodel: the Fallout-style exosuit FRAME (no armour) around a real-scale Steve.

Run: python3 model/art/frame_model.py        (writes ../power_frame.bbmodel; no dependencies)

Scale: 1 px = 1/16 block, Steve exactly as vanilla (4x12x4 limbs, 8x12x4 torso, 8^3 head, 32 px tall), standing on 3 px
boots (his soles at y 3; 6x6x6, the gauntlet cuff's footprint), and wearing the big suit's gauntlets, sized down (a cuff over his
forearm, a fist over his hand: 2 px past it). The frame is a hollow cage around him: every limb is two thin posts on the OUTER
side joined by front and back bands 1 px outside the limb; the inner side stays open (the neighbouring
limb / torso is there). The arm frames are 1 px wider than his limb, the leg frames 0.5 px. The rest pose is open (ARM_SPLAY, LEG_SPLAY).
The torso is an exoskeleton instead: plates on his chest, a spine on his back.
Clearance to Steve is 0, never an overlap: see check() at the bottom (it also keeps the limbs' frames apart).

Rig: the names and parent chain of power_suit.bbmodel (marine > body > torso > head / *_arm;
marine > *_leg), so the animation kit and the later armour can reuse them. Steve's limbs are rigid (vanilla), so
there is no elbow, knee, hand or ankle bone: each limb is one bone. Frame cubes are named fr_*, the pilot's st_*; each bone has a
`pilot_*` child group holding Steve's part (hide those to see the bare frame). The head has no frame at all: the helmet is armour, added later.

Drafts: give the part an env knob (os.environ.get), then build all its options into one review file with
frame_drafts.py (hidden proto_* groups, one Blockbench tab); once one is picked, the knob goes and its choice stays.
"""
import base64, glob, json, os, struct, uuid, zipfile, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get('FRAME_OUT') or os.path.join(HERE, '..', 'power_frame.bbmodel')
# The rest pose is OPEN, not Steve's idle: a bulky suit can't close like a body, so the arms hang splayed out and the
# legs stand apart (degrees, outward; env overrides are for trying values). Steve's limbs turn with their frame bones.
ARM_SPLAY = float(os.environ.get('ARM_SPLAY', 9))   # 9, not 8: room for the chest's side bars at the cuff
LEG_SPLAY = float(os.environ.get('LEG_SPLAY', 3))
STEVE_PNG = 'assets/minecraft/textures/entity/player/wide/steve.png'

def steve_skin():
    """Vanilla's default Steve skin, read from the Minecraft jar the build already unpacked (run ./gradlew build once).
    Its hat / jacket / pants layers are empty, so only the base boxes are modelled (an overlay would clip the frame)."""
    jars = sorted(glob.glob(os.path.join(HERE, '..', '..', 'build', 'moddev', 'artifacts', 'minecraft-patched-*.jar')))
    assert jars, 'no Minecraft jar in build/moddev/artifacts: run ./gradlew build first'
    with zipfile.ZipFile(jars[-1]) as z:
        return unpng(z.read(STEVE_PNG))

def unpng(data):
    """Decodes an 8-bit PNG (palette, grey, RGB or RGBA; not interlaced) to rows of RGBA tuples."""
    pos, chunks, idat = 8, {}, b''
    while pos < len(data):
        n, t = struct.unpack('>I4s', data[pos:pos + 8])
        d = data[pos + 8:pos + 8 + n]
        if t == b'IDAT': idat += d
        else: chunks[t] = d
        pos += 12 + n
    w, h, depth, ctype, _, _, inter = struct.unpack('>IIBBBBB', chunks[b'IHDR'])
    assert depth == 8 and inter == 0, (depth, inter)
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw, rows, prev = zlib.decompress(idat), [], bytearray(w * bpp)
    for y in range(h):
        i = y * (w * bpp + 1)
        ft, line = raw[i], bytearray(raw[i + 1:i + 1 + w * bpp])
        for x in range(len(line)):
            a = line[x - bpp] if x >= bpp else 0
            b, c = prev[x], (prev[x - bpp] if x >= bpp else 0)
            if ft == 1: line[x] = (line[x] + a) & 255
            elif ft == 2: line[x] = (line[x] + b) & 255
            elif ft == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif ft == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    pal = [tuple(chunks[b'PLTE'][i:i + 3]) for i in range(0, len(chunks.get(b'PLTE', b'')), 3)]
    trns = chunks.get(b'tRNS', b'')
    def px(line, x):
        v = line[x * bpp:(x + 1) * bpp]
        if ctype == 3: return pal[v[0]] + (trns[v[0]] if v[0] < len(trns) else 255,)
        if ctype == 0: return (v[0],) * 3 + (255,)
        if ctype == 4: return (v[0],) * 3 + (v[1],)
        if ctype == 2: return tuple(v) + (255,)
        return tuple(v)
    return [[px(line, x) for x in range(w)] for line in rows]

# ---------------------------------------------------------------- textures (written without PIL)
def png(w, h, px):
    raw = b''.join(b'\x00' + b''.join(bytes(px[y][x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + \
        chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

TW, TH = 128, 64   # the one texture (Bedrock / GeckoLib models have one): Steve's 64x64 skin left, the frame's swatches right
SWX, SWY = 64, 0   # the swatch strip's top-left texel
# Frame palette: swatches 4x4 px along x. Cube faces sample a swatch: top light, sides mid, underside dark.
SW = {'light': (0x6e, 0x76, 0x80), 'mid': (0x4c, 0x53, 0x5c), 'dark': (0x2f, 0x34, 0x3b),
      'hinge': (0x23, 0x27, 0x2d), 'pin': (0x7d, 0x86, 0x91), 'core': (0x33, 0xe0, 0xe6),
      'glass': (0xff, 0x9a, 0x1a)}
SWN = list(SW)
def paint_swatches(px, y0):
    for i, k in enumerate(SWN):
        c = SW[k]
        for y in range(4):
            for x in range(4):
                s = 6 if (x + y) % 3 == 0 else 0           # a little pixel grain, no noise
                a = 150 if k == 'glass' else 255
                px[y0 + y][SWX + i * 4 + x] = (min(c[0] + s, 255), min(c[1] + s, 255), min(c[2] + s, 255), a)

# ---------------------------------------------------------------- gauntlet art, taken from the big suit
# The gauntlet is the big suit's (power_suit.bbmodel): a banded cuff (forearm_r, 8x7x8) over a slate fist (fist_r, 7x5x7),
# sized down to the frame's arm (cuff 5x5x5, fist 5x4x5: the cuff as wide as the fist, so it leaves room at the chest's side). Its art is the suit's own painted faces, resampled by picking
# columns / rows (not scaling), so the bands, seams and finger lines stay whole pixels. The suit's texture is its grey
# base (paint swaps the steel shades), so the frame's gauntlets are that grey too.
SUIT = os.path.join(HERE, '..', 'power_suit.bbmodel')
# per cube: the suit cube, the frame size per face (w, h), and which of the suit face's columns / rows to keep
GAUNT = {
    'cuff': ('forearm_r', {
        'north': ([0, 1, 3, 6, 7], [0, 1, 2, 3, 5]), 'east': ([0, 1, 3, 6, 7], [0, 1, 2, 3, 5]),
        'south': ([0, 1, 4, 6, 7], [0, 1, 2, 3, 5]), 'west': ([0, 1, 3, 6, 7], [0, 1, 2, 3, 5]),
        'up': ([0, 1, 2, 6, 7], [0, 1, 2, 6, 7]), 'down': ([0, 1, 4, 5, 7], [0, 1, 4, 5, 7])}),
    'fist': ('fist_r', {
        'north': ([0, 1, 3, 5, 6], [0, 2, 3, 4]), 'east': ([0, 1, 3, 5, 6], [0, 2, 3, 4]),
        'south': ([0, 1, 3, 5, 6], [0, 2, 3, 4]), 'west': ([0, 1, 2, 3, 6], [0, 2, 3, 4]),
        'up': ([0, 1, 3, 5, 6], [0, 1, 3, 5, 6]), 'down': ([0, 1, 3, 5, 6], [1, 2, 3, 4, 5])}),
}
# The boots have their own art (the fist's read as hands on feet), in the suit's colours (sampled from forearm_r /
# fist_r): a platform boot, a steel upper (the gauntlet cuff's top band, a brighter toe in front) over a seam and a thick
# three-row slate sole, lugs underneath (picked from drafts A slate / B steel sabaton / C mechanical ankle / D platform).
# One letter per texel; rows top to bottom as the face shows. East is the outer side of the right boot (its last column
# at the toe), west the inner; the left boot mirrors them.
PAL = {'A': (0x58, 0x60, 0x6a), 'B': (0x75, 0x7c, 0x85), 'C': (0x9f, 0xa4, 0xa9), 'D': (0x3c, 0x42, 0x4c),
       'K': (0x4c, 0x55, 0x59), 'M': (0x1f, 0x23, 0x26)}
# The tread: lines across the sole (along x) every other row, run out to the edges so their ends are the notches on
# the outer / inner sides; the back meets a line, so its bottom row is a plain strip. The face UVs run: east back ->
# front, west front -> back, north outer -> inner, the sole's rows back -> front and columns inner -> outer; hence
# west's notches start a pixel later than east's. The front's bottom row is the sole's front row seen edge on: only its
# corners are fixed (they meet the side notches), the rest is the front's design: a toe lug across the toe's foot, its
# end the sole's front line (picked from drafts plain / toe lug / bumper / riveted).
SIDE = ['ABBABC', 'AAAAAC', 'DDDDDD', 'KKKKKK', 'MMMMMM', 'KMKMKM']
FRONT = ['ABBBBC', 'ACCCCC', 'DDDDDD', 'KKKKKK', 'MMMMMM', 'MKKKKM']   # the steel toe, its seam, the sole and toe lug
assert FRONT[-1][0] == FRONT[-1][-1] == 'M', "the front row's corners meet the side notches"
BOOT = {
    'north': FRONT,
    'south': SIDE[:5] + ['KKKKKK'],
    'east': SIDE, 'west': SIDE[:5] + ['MKMKMK'],
    'up': ['AAAAAA'] + ['AMMMMA'] * 4 + ['AAAAAA'],                            # shows round his leg: a rim, dark inside
    'down': ['KKKKKK', 'MMMMMM'] * 2 + ['KKKKKK', FRONT[-1][::-1]],            # the tread, rows back -> front
}
FACES = ('north', 'east', 'south', 'west', 'up', 'down')
# where each painted face sits in the texture: right of the swatches, one row per cube (u, v, w, h)
GAUNT_UV = {}
for row, (part, (_, picks)) in enumerate(GAUNT.items()):
    u = SWX
    for f in FACES:
        w, h = len(picks[f][0]), len(picks[f][1])
        GAUNT_UV[part, f] = (u, 8 + row * 8, w, h)
        assert u + w <= TW
        u += w
u = SWX
for f in FACES:
    GAUNT_UV['boot', f] = (u, 8 + len(GAUNT) * 8, len(BOOT[f][0]), len(BOOT[f]))
    u += len(BOOT[f][0])

def paint_gauntlet(px):
    m = json.load(open(SUIT))
    img = unpng(base64.b64decode(m['textures'][0]['source'].split(',', 1)[1]))
    assert len(img[0]) == m['resolution']['width'], 'suit texture and UV resolution differ'
    els = {e['name']: e for e in m['elements']}
    for part, (src, picks) in GAUNT.items():
        for f in FACES:
            u0, v0, u1, v1 = (int(c) for c in els[src]['faces'][f]['uv'])
            face = [img[v][min(u0, u1):max(u0, u1)] for v in range(min(v0, v1), max(v0, v1))]
            if u1 < u0: face = [r[::-1] for r in face]        # as it shows on the suit (its UV flips applied)
            if v1 < v0: face = face[::-1]
            cols, rows = picks[f]
            u, v, w, h = GAUNT_UV[part, f]
            for j, sy in enumerate(rows):
                for i, sx_ in enumerate(cols):
                    px[v + j][u + i] = face[sy][sx_]
    for f in FACES:
        u, v, w, h = GAUNT_UV['boot', f]
        for j, line in enumerate(BOOT[f]):
            for i, ch in enumerate(line):
                px[v + j][u + i] = PAL[ch] + (255,)

def model_tex():   # Steve's real skin at 0,0 + the frame's swatches + the gauntlet art
    px = [[(0, 0, 0, 0)] * TW for _ in range(TH)]
    for y, row in enumerate(steve_skin()):
        px[y][:len(row)] = row
    paint_swatches(px, SWY)
    paint_gauntlet(px)
    for (u, v), rows in PLATE_ART:
        for j, line in enumerate(rows):
            for i, ch in enumerate(line):
                px[v + j][u + i] = PAL[ch] + (255,)
    return png(TW, TH, px)

def b64(b): return 'data:image/png;base64,' + base64.b64encode(b).decode()

# ---------------------------------------------------------------- model building
def uid(): return str(uuid.uuid4())

FY = 1   # the mechanical feet are 3 px tall (like the fists): everything above them is lifted 1 px from the numbers below
groups, elements = [], []
def group(name, origin, parent, visible=True):
    g = {'name': name, 'uuid': uid(), 'export': True, 'locked': False, 'scope': 0, 'selected': False, 'visibility': visible,
         'origin': [origin[0], origin[1] + FY, origin[2]], 'rotation': [0, 0, 0], 'bedrock_binding': '', 'color': 0, 'children': [],
         'reset': False, 'shade': True, 'mirror_uv': False, 'autouv': 0, 'isOpen': True, 'primary_selected': False}
    groups.append(g)
    g['_parent'] = parent
    if parent is not None: parent['children'].append(g)
    return g

def uv_swatch(face, mat):
    k = 'light' if face == 'up' else 'dark' if face == 'down' else mat
    if mat in ('light', 'mid', 'dark'): k = 'light' if face == 'up' else 'dark' if face == 'down' else 'mid'
    j = SWN.index(k)
    return [SWX + j * 4 + 1, SWY + 1, SWX + j * 4 + 3, SWY + 3]

def cube(g, name, a, b, mat='mid', raw=False):
    (x0, y0, z0), (x1, y1, z1) = a, b
    if not raw: y0, y1 = y0 + FY, y1 + FY
    e = {'name': name, 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True, 'scope': 0,
         'allow_mirror_modeling': True, 'from': [min(x0, x1), min(y0, y1), min(z0, z1)],
         'to': [max(x0, x1), max(y0, y1), max(z0, z1)], 'autouv': 0, 'color': 1, 'origin': list(g['origin']),
         'faces': {f: {'uv': uv_swatch(f, mat), 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')},
         'type': 'cube', 'uuid': uid()}
    elements.append(e)
    e['_g'] = g
    g['children'].append(e['uuid'])
    return e

def painted(g, side, part, name, a, b, raw=False):
    """A gauntlet cube (right side coordinates) with its art; the left one is the mirror image (east / west swap,
    every face's u reversed), so both arms read the same."""
    r = side == 'r'
    e = cube(g, name, *((a, b) if r else mir(a, b)), raw=raw)
    e['_shell'] = True
    for f in FACES:
        src = f if r else {'east': 'west', 'west': 'east'}.get(f, f)
        u, v, w, h = GAUNT_UV[part, src]
        e['faces'][f] = {'uv': [u, v, u + w, v + h] if r else [u + w, v, u, v + h], 'texture': 0}
    return e

# Plates: flat plates painted with letter grids (PAL). Their faces get UVs once every plate is made (pack_plates):
# identical grids share texels, and they're shelf-packed tallest first into the free texture below the boot art.
PLATE_ART, _wanted = [], {}
def _alloc(rows):
    uv = []
    _wanted.setdefault(tuple(rows), []).append(uv)
    return uv

def pack_plates(v0=31):
    u, v, shelf = SWX, v0, 0
    for rows in sorted(_wanted, key=lambda r: -len(r)):
        w, h = len(rows[0]), len(rows)
        if u + w > TW: u, v, shelf = SWX, v + shelf, 0
        assert v + h <= TH, 'out of texture room for plates'
        PLATE_ART.append(((u, v), rows))
        for uv in _wanted[rows]: uv[:] = [u, v, u + w, v + h]
        u, shelf = u + w, max(shelf, h)

# For each face a plate can show its grid on: the face whose UV runs along the grid's first column (the viewer's
# left), the size axes (grid width, thickness) and the opposite face.
VIEW = {'north': ('east', 'west', 0, 2, 'south'), 'south': ('west', 'east', 0, 2, 'north'),
        'east': ('south', 'north', 2, 0, 'west'), 'west': ('north', 'south', 2, 0, 'east')}

def plate(g, name, a, b, rows, face='north', tx=1):
    """A plate whose `face` shows `rows` as the viewer sees it (first column on the viewer's left: from the front the
    suit's right, from behind its left, from its right side its back, from its left side its front). Its edges carry
    the grid's rim: the side faces its outer columns, the top its first row, the bottom its last. `tx` texels per px
    across (2 for a plate whose width ends on a half pixel: its grid is drawn in half-pixel columns)."""
    e = cube(g, name, a, b)
    sz = [e['to'][i] - e['from'][i] for i in range(3)]
    left, right, wa, da, opp = VIEW[face]
    assert (len(rows[0]), len(rows)) == (sz[wa] * tx, sz[1]), (name, sz)
    d = max(1, round(sz[da]))
    top, bot = (rows[0], rows[-1]) if wa == 0 else ([c * d for c in rows[0]], [c * d for c in rows[-1]])
    e['faces'] = {f: {'uv': uv, 'texture': 0} for f, uv in {
        face: _alloc(rows), opp: _alloc([r[::-1] for r in rows]),
        left: _alloc([r[0] * d for r in rows]), right: _alloc([r[-1] * d for r in rows]),
        'up': _alloc([top] * d if wa == 0 else top), 'down': _alloc([bot] * d if wa == 0 else bot)}.items()}
    return e

def steve(g, name, a, b, box):
    """Steve's part: a box-UV cube at the skin's (u, v) for a w x h x d box, faces laid out as Blockbench's box UV does
    (east = the part's right side at u, north = front, west, south = back; up / down flipped), so it reads like vanilla."""
    u, v, w, h, d = box
    e = cube(g, name, a, b)
    e['box_uv'] = True
    e['uv_offset'] = [u, v]
    f = e['faces']
    for k, uv in {'east': [u, v + d, u + d, v + d + h], 'north': [u + d, v + d, u + d + w, v + d + h],
                  'west': [u + d + w, v + d, u + 2 * d + w, v + d + h], 'south': [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
                  'up': [u + d + w, v + d, u + d, v], 'down': [u + d + 2 * w, v, u + d + w, v + d]}.items():
        f[k] = {'uv': uv, 'texture': 0}
    return e

# the 64x64 skin's boxes (u, v, w, h, d); the left limbs have their own areas (not mirrors of the right ones)
STEVE = {'head': (0, 0, 8, 8, 8), 'torso': (16, 16, 8, 12, 4), 'arm_r': (40, 16, 4, 12, 4), 'arm_l': (32, 48, 4, 12, 4),
         'leg_r': (0, 16, 4, 12, 4), 'leg_l': (16, 48, 4, 12, 4)}
M = lambda xs: [-x for x in xs]
def mir(a, b): return (-a[0], a[1], a[2]), (-b[0], b[1], b[2])

root = group('marine', (0, 0, 0), None)
body = group('body', (0, 14, 0), root)
torso = group('torso', (0, 14, 0), body)
head = group('head', (0, 26, 0), torso)

# ---------- head: no frame. The helmet is a separate armour piece (a later step); the bone is here for it.
pilot = group('pilot_head', (0, 26, 0), head)
steve(pilot, 'st_head', (-4, 26, -4), (4, 34, 4), STEVE['head'])

# ---------- torso: an exoskeleton, not a ribcage: load-bearing members from the belt up to the shoulders, no hoops.
# Nothing on the torso may rise above y 26: Steve's head (8x8) overhangs the torso front and back.
# Front: a cuirass of plates, 1 px thick, 1 px off his chest (z -3..-2) like the limb bands, under y 26 (his head
# overhangs the torso). A full-width chest plate over a 6 px abdomen plate (a pixel of his shirt shows either side;
# picked from twin pecs / breastplate over lames / cuirass, then 6 over 4 px). Struts at the plates' edges hold them:
# two up from the chest plate's top corners, two down from the abdomen plate's bottom corners to the belt.
plate(torso, 'fr_cuirass', (-4, 20, -3), (4, 25, -2), ['CCCCCCCC', 'BAAAAAAB', 'BAMAAMAB', 'BAAAAAAB', 'DDDAADDD'])
plate(torso, 'fr_abdomen', (-3, 16, -3), (3, 20, -2), ['BAAAAB', 'ADAADA', 'BAAAAB', 'DDDDDD'])
for sx, n in ((1, 'r'), (-1, 'l')):
    cube(torso, f'fr_strut_hi_{n}', (3 * sx, 25, -3), (4 * sx, 26, -2), 'dark')
    cube(torso, f'fr_strut_lo_{n}', (2 * sx, 15, -3), (3 * sx, 16, -2), 'dark')
# Back: two shoulder-blade plates either side of a spine plate, on the back layer (z 2..3), flat: the engine's
# clamps and the vertebrae are painted, not extruded (picked from the front's plates mirrored / one rack plate / these).
# Seen from behind, a grid's first column is the suit's left.
SCAP = ['CCD', 'BMD', 'BMD', 'BMD', 'DDD']   # the left plate, outer edge first: its clamp down the middle
for sx, n in ((1, 'r'), (-1, 'l')):
    plate(torso, f'fr_scapula_{n}', (1 * sx, 20, 2), (4 * sx, 25, 3), SCAP if sx < 0 else [r[::-1] for r in SCAP], face='south')
plate(torso, 'fr_spine', (-1, 15, 2), (1, 25, 3), ['CC', 'MM', 'BA', 'AA', 'MM', 'BA', 'AA', 'MM', 'BA', 'DD'], face='south')
# Sides: a bar round each side of his chest joins the front frame to the back one, its ends wrapping the corners
# (z -3..3: flush with the front layer's and the back layer's outer faces). 0.5 px thick (x 4..4.5): his arm hangs
# close to his torso. One on the bottom pixel of the chest plate and the shoulder blades (y 20-21; the cuff's top inner
# corner is the tight spot: it fits from a 9 deg splay with the gauntlet 0.25 px off his arm; picked over 8 deg with
# 0.25 px bars), one along the belt (y 14-15). The belt bar starts 0.01 px off his side: at the leg's splay its top
# outer corner rises 0.003 px into it.
for sx, n in ((1, 'r'), (-1, 'l')):
    cube(torso, f'fr_side_chest_{n}', (4 * sx, 20, -3), (4.5 * sx, 21, 3), 'mid')
    cube(torso, f'fr_side_belt_{n}', (4.01 * sx, 14, -3), (4.5 * sx, 15, 3), 'mid')
pilot = group('pilot_torso', (0, 14, 0), torso)
steve(pilot, 'st_torso', (-4, 14, -2), (4, 26, 2), STEVE['torso'])

# ---------- belt (the hip): front / back bands, outside Steve's pelvis
cube(body, 'fr_belt_f', (-4, 14, -3), (4, 15, -2), 'dark')
cube(body, 'fr_belt_b', (-4, 14, 2), (4, 15, 3), 'dark')

# ---------- limbs (right side defined, left mirrored). Steve's limbs don't bend, so each limb is ONE rigid bone.
def segment(g, side, name, x_in, x_out, ya, yb, bar=True, mid=None):
    """One cage segment: two outer posts (z -3..-2 / 2..3) with a window bar between, bands at both ends
    (and a dark band at `mid`, a seam for the eye only)."""
    n = f'{name}_{side}'
    s = 1 if side == 'r' else -1
    mk = lambda nm, a, b, mat='mid': cube(g, nm, *((a, b) if s > 0 else mir(a, b)), mat)
    mk(f'fr_{n}_post_f', (x_out - 0.5, ya, -3), (x_out, yb, -2))
    mk(f'fr_{n}_post_b', (x_out - 0.5, ya, 2), (x_out, yb, 3))
    if bar:
        mk(f'fr_{n}_bar', (x_out - 0.5, (ya + yb) / 2 - 0.5, -3), (x_out, (ya + yb) / 2 + 0.5, 3), 'dark')
    for z0, z1, zn in ((-3, -2, 'f'), (2, 3, 'b')):
        mk(f'fr_{n}_band_hi_{zn}', (x_in, yb - 1, z0), (x_out - 0.5, yb, z1), 'mid')
        mk(f'fr_{n}_band_lo_{zn}', (x_in, ya, z0), (x_out - 0.5, ya + 1, z1), 'mid')
        if mid is not None:
            mk(f'fr_{n}_band_mid_{zn}', (x_in, mid - 0.5, z0), (x_out - 0.5, mid + 0.5, z1), 'dark')

def hinge(g, side, name, x0, x1, y, h=3, depth=3):
    s = 1 if side == 'r' else -1
    a, b = (x0, y - h / 2, -depth / 2), (x1, y + h / 2, depth / 2)
    cube(g, f'fr_{name}_{side}', *((a, b) if s > 0 else mir(a, b)), 'hinge')
    c0, c1 = (x1, y - 1, -1), (x1 + 0.5, y + 1, 1)
    cube(g, f'fr_{name}_pin_{side}', *((c0, c1) if s > 0 else mir(c0, c1)), 'pin')

for side in ('r', 'l'):
    s = 1 if side == 'r' else -1
    nm = 'right' if s > 0 else 'left'
    sx = lambda a, b: (a, b) if s > 0 else mir(a, b)
    # leg: Steve's leg x 0..4, hip pivot (±2, 14); the plate carries his foot at y 2
    leg = group(f'{nm}_leg', (2 * s, 14, 0), root)
    leg['rotation'] = [0, 0, LEG_SPLAY * s]
    # The leg's frame stands 0.5 px out from his leg, all of it on the outer side (the other leg leaves no room
    # inside), like the chest's side bars from his torso.
    LX = 4.5   # the leg cage: x 0..4.5 around his leg's 0..4
    # The boot, after the gauntlet: 6x6x6 (the gauntlet cuff's footprint) round his foot, 3 px under his sole and 3 px
    # up his leg. It turns with the leg, so the sole tilts with the splay (outer edge ~0.3 px up at 3 deg). Inside, it
    # stops 0.25 px from his leg, where the other leg's would meet it.
    painted(leg, side, 'boot', f'fr_boot_{side}', (-0.25, 0, -3), (5.75, 6, 3), raw=True)
    # The cage carries on above the boot; it stops 0.5 px under the hip pivot (y 14): its top band's outer end rises as
    # the leg splays, the belt is above. Front and back alike: a 1 px post on each side (outer and inner edge; the outer
    # one half over his leg's edge, half past it) and three
    # bands right across: two flush with the knee hinge's top and bottom, rivets painted where they cross the posts, and
    # a plain one (solid like the posts) along the cage's top pixel. A 0.5 px bar along the outer side's top joins the
    # front and back bands (none inside: his leg is there). The posts are cut into pieces between the bands (crossing pieces would z-fight). A flat knee hinge
    # on the outer side joins the posts, its corners cut off: a 2x4 middle and a 1x2 piece either side.
    for z0, z1, zn, face in ((-3, -2, 'f', 'north'), (2, 3, 'b', 'south')):
        cube(leg, f'fr_leg_band_top_{zn}_{side}', *sx((0, 12.5, z0), (LX, 13.5, z1)), 'mid')
        for bn, y0 in (('hi', 10.5), ('lo', 7.5)):
            plate(leg, f'fr_leg_band_{bn}_{zn}_{side}', *sx((0, y0, z0), (LX, y0 + 1, z1)), ['MMBBBBBMM'], face, tx=2)
        for tn, ya, yb in (('hi', 11.5, 12.5), ('mid', 8.5, 10.5), ('lo', 5, 7.5)):
            cube(leg, f'fr_leg_post_{tn}_{zn}_{side}', *sx((LX - 1, ya, z0), (LX, yb, z1)), 'mid')
            cube(leg, f'fr_leg_strut_{tn}_{zn}_{side}', *sx((0, ya, z0), (1, yb, z1)), 'mid')
    cube(leg, f'fr_leg_band_top_side_{side}', *sx((LX - 0.5, 12.5, -2), (LX, 13.5, 2)), 'mid')
    kf = 'east' if s > 0 else 'west'
    plate(leg, f'fr_knee_{side}', *sx((LX - 0.5, 7.5, -1), (LX, 11.5, 1)), ['MM', 'CC', 'CC', 'MM'], kf)
    for zn, z0 in (('f', -2), ('b', 1)):
        plate(leg, f'fr_knee_{zn}_{side}', *sx((LX - 0.5, 8.5, z0), (LX, 10.5, z0 + 1)), ['M', 'M'], kf)
    p = group(f'pilot_{nm}_leg', (2 * s, 14, 0), leg)
    steve(p, f'st_leg_{side}', *sx((0, 2, -2), (4, 14, 2)), STEVE[f'leg_{side}'])
    # arm: Steve's arm x 4..8 (his hand at the bottom, y 14), shoulder pivot (±6, 26)
    arm = group(f'{nm}_arm', (6 * s, 26, 0), torso)
    arm['rotation'] = [0, 0, ARM_SPLAY * s]   # (with legs at 3, under 5 the fist hits the leg: check() fails)
    AX = 9   # the arm cage: x 4..9 around his arm's 4..8
    # The gauntlet (the big suit's, sized down): a 5x5x5 cuff over his forearm and a 5x4x5 fist over his hand, so it
    # adds 2 px of length, not a whole fist. Both have the same footprint: 0.5 px round his arm front and back, 0.25 px
    # inside (the closest encloses() allows: the cuff's top inner corner is what comes closest to the chest's side
    # bar) and 0.75 px outside.
    painted(arm, side, 'fist', f'fr_fist_{side}', (3.75, 12, -2.5), (8.75, 16, 2.5))
    painted(arm, side, 'cuff', f'fr_cuff_{side}', (3.75, 16, -2.5), (8.75, 21, 2.5))
    segment(arm, side, 'arm', 4, AX, 21, 26, bar=False)   # the cage carries on above the cuff (no pauldron over it)
    p = group(f'pilot_{nm}_arm', (6 * s, 26, 0), arm)
    steve(p, f'st_arm_{side}', *sx((4, 14, -2), (8, 26, 2)), STEVE[f'arm_{side}'])

# ---------------------------------------------------------------- clearance check, in the posed rest pose
# Every cube in world space (its own rotation, then each bone's, all about z), as an xy quad plus a z range. Fails on
# any overlap deeper than EPS between the frame and Steve, or between frame cubes of different limbs (touching is fine).
EPS = 1e-3
def world(e):
    import math
    (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
    pts = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    def rot(pts, origin, deg):
        if not deg: return pts
        a = math.radians(deg); c, sn = math.cos(a), math.sin(a)
        return [(origin[0] + (x - origin[0]) * c - (y - origin[1]) * sn, origin[1] + (x - origin[0]) * sn + (y - origin[1]) * c)
                for x, y in pts]
    pts = rot(pts, e['origin'], e.get('rotation', [0, 0, 0])[2])
    g = e['_g']
    while g is not None:
        pts = rot(pts, g['origin'], g['rotation'][2])
        g = g['_parent']
    return pts, (z0, z1)

def overlap(a, b):
    (pa, za), (pb, zb) = a, b
    if min(za[1], zb[1]) - max(za[0], zb[0]) <= EPS: return False
    for poly in (pa, pb):   # separating axis test on the quads' edge normals
        for i in range(4):
            (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % 4]
            n = (y0 - y1, x1 - x0)
            ln = (n[0] ** 2 + n[1] ** 2) ** 0.5
            pr = lambda P: [(x * n[0] + y * n[1]) / ln for x, y in P]
            A, B = pr(pa), pr(pb)
            if min(max(A), max(B)) - max(min(A), min(B)) <= EPS: return False
    return True

def limb(e):
    g = e['_g']
    while g is not None and g['name'] not in ('right_leg', 'left_leg', 'right_arm', 'left_arm'):
        g = g['_parent']
    return g['name'] if g else 'core'

def turn(e):   # a cube's total turn in world space (its own, then its bones'; all about z)
    t, g = e.get('rotation', [0, 0, 0])[2], e['_g']
    while g is not None:
        t, g = t + g['rotation'][2], g['_parent']
    return t

def encloses(sh, st, W, margin=0.25):
    """In the shell's own frame (its world turn undone), Steve's part, cut to the shell's height, sits inside it with
    `margin` to spare at the sides and front / back (so no face of his lies in a face of the shell: no z-fighting)."""
    import math
    margin -= 1e-6
    a = math.radians(-turn(sh)); c, sn = math.cos(a), math.sin(a)
    rot = lambda P: [(x * c - y * sn, x * sn + y * c) for x, y in P]
    S, P = rot(W[sh['uuid']][0]), rot(W[st['uuid']][0])
    x0, x1 = min(x for x, _ in S), max(x for x, _ in S)
    y0, y1 = min(y for _, y in S), max(y for _, y in S)
    xs = [x for x, y in P if y0 <= y <= y1]
    for (xa, ya), (xb, yb) in zip(P, P[1:] + P[:1]):   # where his outline crosses the shell's top and bottom
        for yc in (y0, y1):
            if (ya - yc) * (yb - yc) < 0: xs.append(xa + (xb - xa) * (yc - ya) / (yb - ya))
    (za, zb), (zc, zd) = W[sh['uuid']][1], W[st['uuid']][1]
    return (not xs or (min(xs) >= x0 + margin and max(xs) <= x1 - margin)) and za <= zc - margin and zb >= zd + margin

def check():
    W = {e['uuid']: world(e) for e in elements}
    bad = []
    for i, a in enumerate(elements):
        for b in elements[i + 1:]:
            fa, fb = a['name'].startswith('fr_'), b['name'].startswith('fr_')
            if not (fa or fb): continue                                   # Steve against himself: vanilla's business
            if fa and fb and limb(a) == limb(b): continue                 # one limb's frame is built touching itself
            if limb(a) == limb(b) and (a.get('_shell') or b.get('_shell')):   # a shell round his part: must enclose it
                sh, st = (a, b) if a.get('_shell') else (b, a)
                if st['name'].startswith('fr_') or encloses(sh, st, W): continue
            if overlap(W[a['uuid']], W[b['uuid']]): bad.append((a['name'], b['name']))
    return bad

# ---------------------------------------------------------------- write the project
def outline(g):
    return {'uuid': g['uuid'], 'isOpen': True, 'children': [outline(c) if isinstance(c, dict) else c for c in g['children']]}

def tex(name, data, i):
    return {'name': name, 'path': '', 'folder': '', 'namespace': '', 'id': str(i), 'group': '', 'scope': 0,
             'width': TW, 'height': TH, 'uv_width': TW, 'uv_height': TH, 'particle': False, 'use_as_default': False,
             'layers_enabled': False, 'sync_to_project': '', 'file_format': 'png', 'render_mode': 'default',
             'render_sides': 'auto', 'wrap_mode': 'limited', 'pbr_channel': 'color', 'fps': 7, 'force_translucent': False,
             'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True,
             'internal': True, 'saved': False, 'uuid': uid(), 'source': b64(data)}

if __name__ == '__main__':
    pack_plates()
    bad = check()
    assert not bad, bad
    for g in groups:
        g.pop('_parent', None)
    for e in elements:
        e.pop('_g', None)
        e.pop('_shell', None)
    model = {
        'meta': {'format_version': '5.0', 'model_format': 'bedrock', 'box_uv': False},
        'name': 'power_frame', 'model_identifier': 'power_frame', 'visible_box': [4, 4.5, 1.75],
        'variable_placeholders': '', 'variable_placeholder_buttons': [], 'unhandled_root_fields': {},
        'resolution': {'width': TW, 'height': TH},
        'elements': elements,
        'groups': [dict(g, children=[]) for g in groups],
        'outliner': [outline(root)],
        'textures': [tex('power_frame.png', model_tex(), 0)],
        'animations': [],
    }
    # groups' children lists hold only structure in the outliner, not in `groups`
    with open(OUT, 'w') as f:
        json.dump(model, f, indent=1)
    print(f'{len(elements)} cubes ({sum(e["name"].startswith("fr_") for e in elements)} frame, '
          f'{sum(e["name"].startswith("st_") for e in elements)} Steve), {len(groups)} groups -> {os.path.normpath(OUT)}')

