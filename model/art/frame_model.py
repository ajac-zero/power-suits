#!/usr/bin/env python3
"""Builds model/power_frame.bbmodel: the Fallout-style exosuit FRAME (no armour) around a real-scale Steve.

Run: python3 model/art/frame_model.py        (writes ../power_frame.bbmodel; no dependencies)

Scale: 1 px = 1/16 block, Steve exactly as vanilla (4x12x4 limbs, 8x12x4 torso, 8^3 head, 32 px tall), standing on
3 px boots (his soles at y 3; 5x6x5) and wearing the big suit's gauntlets, sized down (a cuff over his forearm, a fist
over his hand: 2 px past it); boots and gauntlets have the same 5x5 footprint round his 4x4 limbs. The frame is a
hollow cage around him: every limb's is in line with its gauntlet / boot, two posts on the OUTER side joined by
front and back bands at its ends and a bar along the outer side's top; the inner side stays open (the neighbouring
limb / torso is there). The rest pose is open (ARM_SPLAY, LEG_SPLAY).
The torso is an exoskeleton instead: plates on his chest, a spine on his back, a bar round each side, a belt.

SHELLS: every moving part is ONE box round Steve's (an arm: fist, cuff and cage; a leg: boot and cage; the torso;
the belt), its frame painted on the faces and its gaps see-through texels (alpha 0), like the player skin's jacket
and sleeve layer. So the frame is flat (bars and plates are paint on the box's faces, no thickness of their own) and
the renderer must draw the faces' backs too (entityCutoutNoCull): through a gap you see the far side's bars.
Clearance to Steve is 0, never an overlap: see check() at the bottom (it also keeps the parts' solid texels apart).

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

TW, TH = 128, 128   # the one texture (Bedrock / GeckoLib models have one): Steve's 64x64 skin top left, the shells below
# The frame's bar colour (the old cube frame's side faces), with a little pixel grain, no noise.
BAR = (0x4c, 0x53, 0x5c)
def bar(i, j): return tuple(min(k + (6 if (i + j) % 3 == 0 else 0), 255) for k in BAR) + (255,)

# ---------------------------------------------------------------- gauntlet art, taken from the big suit
# The gauntlet is the big suit's (power_suit.bbmodel): a banded cuff (forearm_r, 8x7x8) over a slate fist (fist_r,
# 7x5x7), sized down to the frame's arm (cuff 5x4x5, fist 5x4x5: the cuff as wide as the fist, so it leaves room at the
# chest's side; 4 tall, its fifth pixel given to the shoulder's cage: two bands, each a riveted row over a dark seam).
# Its art is the suit's own painted faces, resampled by picking columns / rows (not scaling), so the bands, seams and
# finger lines stay whole pixels. The suit's texture is its grey base (paint swaps the steel shades), so the frame's
# gauntlets are that grey too.
SUIT = os.path.join(HERE, '..', 'power_suit.bbmodel')
# per cube: the suit cube, the frame size per face (w, h), and which of the suit face's columns / rows to keep
GAUNT = {
    'cuff': ('forearm_r', {
        'north': ([0, 1, 3, 6, 7], [0, 2, 3, 5]), 'east': ([0, 1, 3, 6, 7], [0, 2, 3, 5]),
        'south': ([0, 1, 4, 6, 7], [0, 2, 3, 5]), 'west': ([0, 1, 3, 6, 7], [0, 2, 3, 5]),
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
# The boot is 5x6x5: its footprint round his leg is the gauntlet's round his arm (5x5 round 4x4), so the upper's band
# is the 5 px cuff's (ABABC).
# The tread: lines across the sole (along x) every other row, run out to the edges so their ends are the notches on
# the outer / inner sides; with 5 rows it starts and ends on a line, so both sides read line / gap / line from back to
# front and the back's bottom row is the back line, a plain dark strip. The face UVs run: east back -> front, west
# front -> back, north outer -> inner, the sole's rows back -> front and columns inner -> outer. The front's bottom row
# is the sole's front row seen edge on: only its corners are fixed (they meet the side notches), the rest is the
# front's design: a toe lug across the toe's foot, its end the sole's front line (picked from drafts plain / toe lug /
# bumper / riveted).
SIDE = ['ABABC', 'AAAAC', 'DDDDD', 'KKKKK', 'MMMMM', 'MKMKM']
FRONT = ['ABBBC', 'ACCCC', 'DDDDD', 'KKKKK', 'MMMMM', 'MKKKM']   # the steel toe, its seam, the sole and toe lug
assert FRONT[-1][0] == FRONT[-1][-1] == 'M', "the front row's corners meet the side notches"
BOOT = {
    'north': FRONT,
    'south': SIDE[:5] + ['MMMMM'],
    'east': SIDE, 'west': SIDE,
    'up': ['AAAAA'] + ['AMMMA'] * 3 + ['AAAAA'],                               # shows round his leg: a rim, dark inside
    'down': ['MMMMM', 'KKKKK'] * 2 + [FRONT[-1][::-1]],                        # the tread, rows back -> front
}
FACES = ('north', 'east', 'south', 'west', 'up', 'down')
# where each part's art is drawn in the SOURCE canvas the shells copy from (not the texture): (u, v, w, h)
GAUNT_UV = {}
for row, (part, (_, picks)) in enumerate(GAUNT.items()):
    u = 0
    for f in FACES:
        w, h = len(picks[f][0]), len(picks[f][1])
        GAUNT_UV[part, f] = (u, 8 + row * 8, w, h)
        u += w
u = 0
for f in FACES:
    GAUNT_UV['boot', f] = (u, 8 + len(GAUNT) * 8, len(BOOT[f][0]), len(BOOT[f]))
    u += len(BOOT[f][0])

# ---------------------------------------------------------------- shells
# A limb's faces are composed top to bottom from a cage pattern ('#' a bar texel, '.' a hole) over
# the gauntlet's (cuff, fist) or the boot's art, in the same face column order as theirs (north outer -> inner, east
# back -> front, south inner -> outer, west front -> back). The cage is the cubes' one: outer posts front and back,
# a band across front and back at the top and bottom, a bar along the outer side's top, the inner side open; posts and
# bars are 1 px (whole texels; the cube frame before the shells had 0.5 px ones).
# 'T' is a bar texel that tucks into its neighbour: the top band's inner texel, which a whole texel pushes 0.25 px past
# his limb's edge, into the chest plate's strut and his torso (arms; hidden in them) or into the other leg's same texel
# (legs; same colour, same face plane: nothing to flicker). The clearance check skips it.
# Texel density per face: `rows` texel rows per px up the sides (the legs: 2, so the cage can end on a half pixel,
# 14.5 under the belt, while the boot's rows stay whole; every art row is drawn twice) and the arms' top face 2 texels
# per px, so its rim is 0.5 px (a 1 px rim would lie in the plane of his arm's top and overlap it, so flicker).
def _cage(n, band=1):
    """n texel rows of cage, `band` of them per band."""
    mid = n - 2 * band
    return {'north': ['####T'] * band + ['#....'] * mid + ['#####'] * band,
            'south': ['T####'] * band + ['....#'] * mid + ['#####'] * band,
            'east': ['#####'] * band + ['#...#'] * (mid + band),
            'west': ['.....'] * (mid + band) + ['#...#'] * band}
def _rim(n):
    """The top face, n x n texels, rows front -> back, columns inner -> outer: the band and bar tops. The bands'
    inner ends tuck (the splay dips them into the chest's struts)."""
    return ['T' + '#' * (n - 1)] + ['.' * (n - 1) + '#'] * (n - 2) + ['T' + '#' * (n - 1)]
# The torso's plates (letter grids in PAL, as the viewer sees them: from the front the first column is the suit's
# right, from behind its left). Front: a full-width chest plate (x -4..4, y 21-26) over a 6 px abdomen plate (x -3..3,
# y 17-21; a pixel of his shirt shows either side; picked from twin pecs / breastplate over lames / cuirass, then 6
# over 4 px), struts at their edges (x 3-4 up to the top, x 2-3 down to the belt). Back: two shoulder-blade plates
# (x 1..4, y 21-26: the engine's clamps painted, not extruded) either side of a spine plate (x -1..1, y 16-26), a strut
# up from each plate's outer top corner. Nothing rises above y 27: Steve's head overhangs the torso front and back.
CUIRASS = ['CCCCCCCC', 'BAAAAAAB', 'BAMAAMAB', 'BAAAAAAB', 'DDDAADDD']
ABDOMEN = ['BAAAAB', 'ADAADA', 'BAAAAB', 'DDDDDD']
SCAP = ['CCD', 'BMD', 'BMD', 'BMD', 'DDD']   # the left plate, outer edge first: its clamp down the middle
SPINE = ['CC', 'MM', 'BA', 'AA', 'MM', 'BA', 'AA', 'MM', 'BA', 'DD']

def _torso():
    """The torso box: x -4.5..4.5 (his torso is -4..4; the half pixel each side holds the side bars), y 16..27, z -3..3
    (the plates stand 1 px off his chest and back; now they are paint on the box's front and back, at z -3 / 3). Its
    front and back have 2 texels per px across, because the plates' edges are on whole pixels and the box's on halves
    (every plate column is drawn twice); rows are 1 per px, top (y 26-27) first."""
    W, H = 18, 11
    grid = lambda: [['.'] * W for _ in range(H)]
    def put(g, row, col, cells):
        for k, c in enumerate(cells): g[row][col + k] = c
    dbl = lambda r: ''.join(c * 2 for c in r)
    fc = lambda x: round((4.5 - x) * 2)   # the front's column whose texel starts at x (going -x: the viewer's right)
    bc = lambda x: round((x + 4.5) * 2)   # the back's column whose texel starts at x (going +x)
    front, back = grid(), grid()
    for j, r in enumerate(CUIRASS): put(front, 1 + j, fc(4), dbl(r))
    for j, r in enumerate(ABDOMEN): put(front, 6 + j, fc(3), dbl(r))
    for x in (4, -3): put(front, 0, fc(x), '##')         # struts up from the chest plate's corners
    for x in (3, -2): put(front, 10, fc(x), '##')        # and down from the abdomen plate's to the belt
    for j, r in enumerate(SCAP): put(back, 1 + j, bc(-4), dbl(r))
    for j, r in enumerate(SCAP): put(back, 1 + j, bc(1), dbl(r[::-1]))
    for j, r in enumerate(SPINE): put(back, 1 + j, bc(-1), dbl(r))
    for x in (-4, 3): put(back, 0, bc(x), '##')          # struts up from the shoulder blades' outer corners
    # The side bars: one round each side of his chest on the plates' bottom pixel (y 21-22), joining the front frame
    # to the back one; their ends are the front's and back's outer half-columns. (His arm hangs close to his torso:
    # the cuff's top inner corner is the tight spot, it clears from a 9 deg splay.)
    for g in (front, back): g[5][0] = g[5][-1] = '#'
    side = ['......'] * 5 + ['######'] + ['......'] * 5
    return {'north': [''.join(r) for r in front], 'south': [''.join(r) for r in back], 'east': side, 'west': side,
            'up': ['.' * W] * 6, 'down': ['.' * W] * 6}

def _belt():
    """The belt (the hip, on the body bone: the torso can bend over it): a ring round his pelvis, x -4.5..4.5, y 15..16,
    z -3..3, as wide as the torso box; its top and bottom are rims (front and back rows, the half-px side columns)."""
    rim = lambda c: ['#' * 18] + [c + '.' * 16 + c] * 4 + ['#' * 18]
    # underneath, the side columns are 'T': at the splay each leg's top outer corner rises 0.1 px past the belt's
    # bottom, 0.003 px inside its side column (hidden in the ring)
    return {'north': ['#' * 18], 'south': ['#' * 18], 'east': ['#' * 6], 'west': ['#' * 6], 'up': rim('#'), 'down': rim('T')}

SHELLS = {'arm_shell': dict(cage=_cage(6), below=['cuff', 'fist'], rows=1, up=_rim(10)),
          'leg_shell': dict(cage=_cage(17, 2), below=['boot'], rows=2, up=['.' * 5] * 5),
          'torso_shell': dict(faces=_torso()), 'belt_shell': dict(faces=_belt())}
# where each shell's faces sit in the texture (below / beside nothing else), one strip per shell
SHELL_AT = {'arm_shell': (0, 64), 'torso_shell': (35, 64), 'leg_shell': (0, 80), 'belt_shell': (35, 80)}
def shell_art(part, f):
    """The face's rows: letters ('#' bar, 'T' tucked bar, '.' hole, PAL colours) and (u, v) refs into the source
    canvas for the gauntlet / boot art copied below a limb's cage."""
    sp = SHELLS[part]
    if 'faces' in sp: return sp['faces'][f]
    if f == 'up': return sp['up']
    if f == 'down':
        u, v, w, h = GAUNT_UV[sp['below'][-1], 'down']
        return [[(u + i, v + j) for i in range(w)] for j in range(h)]
    rows = list(sp['cage'][f])
    for b in sp['below']:
        u, v, w, h = GAUNT_UV[b, f]
        rows += [[(u + i, v + j) for i in range(w)] for j in range(h) for _ in range(sp['rows'])]
    return rows

SHELL_UV, _used = {}, set()
for part, (u, v) in SHELL_AT.items():
    for f in FACES:
        art = shell_art(part, f)
        w, h = len(art[0]), len(art)
        SHELL_UV[part, f] = (u, v, w, h)
        cells = {(u + i, v + j) for i in range(w) for j in range(h)}
        assert u + w <= TW and v + h <= TH and not cells & _used and all(x >= 64 or y >= 64 for x, y in cells), (part, f)
        _used |= cells
        u += w

def paint_shells(px, src):
    for part in SHELLS:
        for f in FACES:
            u, v, w, h = SHELL_UV[part, f]
            for j, line in enumerate(shell_art(part, f)):
                for i, c in enumerate(line):
                    if c == '.': px[v + j][u + i] = (0, 0, 0, 0)
                    elif c in ('#', 'T'): px[v + j][u + i] = bar(i, j)
                    elif isinstance(c, str): px[v + j][u + i] = PAL[c] + (255,)
                    else: px[v + j][u + i] = src[c[1]][c[0]]

def shell_slabs(part, a, b):
    """The shell's solid texels ('T' aside) as thin boxes (right side coordinates), for the clearance check: a hole
    may pass through another part, a bar may not."""
    (x0, y0, z0), (x1, y1, z1) = a, b
    t, out = 0.01, []
    for f in FACES:
        art = shell_art(part, f)
        n, m = len(art[0]), len(art)
        cw = (z1 - z0 if f in ('east', 'west') else x1 - x0) / n
        rh = (z1 - z0 if f in ('up', 'down') else y1 - y0) / m
        for j, line in enumerate(art):
            for i, c in enumerate(line):
                if c in ('.', 'T'): continue
                ya, yb = y1 - (j + 1) * rh, y1 - j * rh
                if f == 'north': out.append(((x1 - (i + 1) * cw, ya, z0), (x1 - i * cw, yb, z0 + t)))
                elif f == 'south': out.append(((x0 + i * cw, ya, z1 - t), (x0 + (i + 1) * cw, yb, z1)))
                elif f == 'east': out.append(((x1 - t, ya, z1 - (i + 1) * cw), (x1, yb, z1 - i * cw)))
                elif f == 'west': out.append(((x0, ya, z0 + i * cw), (x0 + t, yb, z0 + (i + 1) * cw)))
                elif f == 'up': out.append(((x0 + i * cw, y1 - t, z0 + j * rh), (x0 + (i + 1) * cw, y1, z0 + (j + 1) * rh)))
                else: out.append(((x0 + i * cw, y0, z0 + j * rh), (x0 + (i + 1) * cw, y0 + t, z0 + (j + 1) * rh)))
    return out

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

def model_tex():   # Steve's real skin at 0,0 + the shells (their gauntlet / boot art copied from a source canvas)
    px = [[(0, 0, 0, 0)] * TW for _ in range(TH)]
    for y, row in enumerate(steve_skin()):
        px[y][:len(row)] = row
    src = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    paint_gauntlet(src)
    paint_shells(px, src)
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

def cube(g, name, a, b, raw=False):
    """A cube in bone g, its faces filled in by the caller (painted / steve)."""
    (x0, y0, z0), (x1, y1, z1) = a, b
    if not raw: y0, y1 = y0 + FY, y1 + FY
    e = {'name': name, 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True, 'scope': 0,
         'allow_mirror_modeling': True, 'from': [min(x0, x1), min(y0, y1), min(z0, z1)],
         'to': [max(x0, x1), max(y0, y1), max(z0, z1)], 'autouv': 0, 'color': 1, 'origin': list(g['origin']),
         'faces': {}, 'type': 'cube', 'uuid': uid()}
    elements.append(e)
    e['_g'] = g
    g['children'].append(e['uuid'])
    return e

def painted(g, side, part, name, a, b, raw=False):
    """A shell's cube (right side coordinates) with its art; the left one is the mirror image (east / west swap,
    every face's u reversed), so both sides read the same."""
    r = side == 'r'
    e = cube(g, name, *((a, b) if r else mir(a, b)), raw=raw)
    e['_shell'] = True
    for f in FACES:
        src = f if r else {'east': 'west', 'west': 'east'}.get(f, f)
        u, v, w, h = SHELL_UV[part, src]
        e['faces'][f] = {'uv': [u, v, u + w, v + h] if r else [u + w, v, u, v + h], 'texture': 0}
    return e

def hollow(g, side, part, name, a, b, raw=False):
    """A shell: a painted cube whose see-through texels the clearance check ignores (it checks shell_slabs)."""
    e = painted(g, side, part, name, a, b, raw=raw)
    dy = 0 if raw else FY
    slabs = []
    for p, q in shell_slabs(part, (a[0], a[1] + dy, a[2]), (b[0], b[1] + dy, b[2])):
        if side == 'l': p, q = mir(p, q)
        slabs.append({'name': name, 'from': [min(p[i], q[i]) for i in range(3)], 'to': [max(p[i], q[i]) for i in range(3)],
                      'origin': e['origin'], 'uuid': uid(), '_g': g})
    e['_hollow'] = slabs
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
def mir(a, b): return (-a[0], a[1], a[2]), (-b[0], b[1], b[2])

root = group('marine', (0, 0, 0), None)
body = group('body', (0, 14, 0), root)
torso = group('torso', (0, 14, 0), body)
head = group('head', (0, 26, 0), torso)

# ---------- head: no frame. The helmet is a separate armour piece (a later step); the bone is here for it.
pilot = group('pilot_head', (0, 26, 0), head)
steve(pilot, 'st_head', (-4, 26, -4), (4, 34, 4), STEVE['head'])

# ---------- torso: an exoskeleton, not a ribcage: load-bearing members from the belt up to the shoulders, no hoops
# (its art: _torso()).
hollow(torso, 'r', 'torso_shell', 'fr_torso_shell', (-4.5, 15, -3), (4.5, 26, 3))
pilot = group('pilot_torso', (0, 14, 0), torso)
steve(pilot, 'st_torso', (-4, 14, -2), (4, 26, 2), STEVE['torso'])

# ---------- belt (the hip): a ring outside Steve's pelvis, on the body bone (its art: _belt())
hollow(body, 'r', 'belt_shell', 'fr_belt_shell', (-4.5, 14, -3), (4.5, 15, 3))

# ---------- limbs (right side defined, left mirrored). Steve's limbs don't bend, so each limb is ONE rigid bone.
for side in ('r', 'l'):
    s = 1 if side == 'r' else -1
    nm = 'right' if s > 0 else 'left'
    sx = lambda a, b: (a, b) if s > 0 else mir(a, b)
    # leg: Steve's leg x 0..4, hip pivot (±2, 14)
    leg = group(f'{nm}_leg', (2 * s, 14, 0), root)
    leg['rotation'] = [0, 0, LEG_SPLAY * s]
    # One box: the boot and the cage above it (its art: SHELLS, _cage, BOOT). The boot is 5x6x5 round his foot, placed
    # on his leg as the gauntlet is on his arm (0.25 px inside, 0.75 px outside, 0.5 px front and back), 3 px under
    # his sole; the cage is in line with it. It turns with the leg, so the sole tilts with the splay (outer edge ~0.25
    # px up at 3 deg). It stops at y 14.5, 0.5 px under the belt: its top band's outer end rises as the leg splays.
    hollow(leg, side, 'leg_shell', f'fr_leg_shell_{side}', (-0.25, 0, -2.5), (4.75, 14.5, 2.5), raw=True)
    p = group(f'pilot_{nm}_leg', (2 * s, 14, 0), leg)
    steve(p, f'st_leg_{side}', *sx((0, 2, -2), (4, 14, 2)), STEVE[f'leg_{side}'])
    # arm: Steve's arm x 4..8 (his hand at the bottom, y 14), shoulder pivot (±6, 26)
    arm = group(f'{nm}_arm', (6 * s, 26, 0), torso)
    arm['rotation'] = [0, 0, ARM_SPLAY * s]   # (with legs at 3, under 5 the fist hits the leg: check() fails)
    # One box: the gauntlet (the big suit's, sized down: a 5x4x5 fist over his hand, so 2 px past it, a 5x4x5 cuff over
    # his forearm) and the cage above it, up to his shoulder's top (no pauldron). Its footprint is 0.5 px round his arm
    # front and back, 0.25 px inside (the closest encloses() allows: the cuff's top inner corner is what comes closest
    # to the chest's side bar) and 0.75 px outside.
    hollow(arm, side, 'arm_shell', f'fr_arm_shell_{side}', (3.75, 12, -2.5), (8.75, 26, 2.5))
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
    items = [s for e in elements for s in e.get('_hollow', [e])]   # a shell is checked by its solid texels
    of = {s['uuid']: e for e in elements for s in e.get('_hollow', [])}
    W = {e['uuid']: world(e) for e in elements + items}
    bad = []
    for i, a in enumerate(items):
        for b in items[i + 1:]:
            if limb(a) == limb(b) and (a['uuid'] in of or b['uuid'] in of):   # a shell's texel and his part
                sh, st = (of[a['uuid']], b) if a['uuid'] in of else (of[b['uuid']], a)
                if st['name'].startswith('fr_') or encloses(sh, st, W): continue
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
             'render_sides': 'double', 'wrap_mode': 'limited', 'pbr_channel': 'color', 'fps': 7, 'force_translucent': False,
             'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True,
             'internal': True, 'saved': False, 'uuid': uid(), 'source': b64(data)}

if __name__ == '__main__':
    bad = check()
    assert not bad, bad
    for g in groups:
        g.pop('_parent', None)
    for e in elements:
        e.pop('_g', None)
        e.pop('_shell', None)
        e.pop('_hollow', None)
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

