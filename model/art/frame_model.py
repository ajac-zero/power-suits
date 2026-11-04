#!/usr/bin/env python3
"""Builds model/power_frame.bbmodel and power_frame_slim.bbmodel: the Fallout-style exosuit FRAME (no armour) around a
real-scale pilot, Steve (the wide build) or Alex (the slim build, BUILD).

Run: python3 model/art/frame_model.py        (writes both into model/; no dependencies; BUILD=wide|slim for one)

Scale: 1 px = 1/16 block, the pilot exactly as vanilla (4x12x4 limbs, Alex's arms 3 wide, 8x12x4 torso, 8^3 head,
32 px tall), standing on 4 px platform soles (5x4x5) with the big suit's fists (sized down, 5x4x5) under his hands: his
limbs fit entirely in the frame's cage, and the fists and soles are its mechanical ends (2026-11-04, user: his feet
stand on the soles, the fists start where his hands end), so he stands 4 px up (36 px to his head's top) and the arms
reach 4 px past his hands. Soles and fists have the same 5x5 footprint round his 4x4 limbs. The frame is a dense,
mechanical cage around him, from the fist / sole up to the shoulder / hip, in line with its fist / sole, closed on all
four sides and painted after the Fallout 4 T-45's frame (steel members, gunmetal joints, brass bellows, a copper valve
on the chest: ARM_ART, LEG_ART, _torso), the pilot seen through small windows, mostly at his hands and feet. It stands
straight: the legs touch like vanilla's, the arms hang off a chest wider than the waist (the spacing, CHEST_X).
The torso is an exoskeleton: a chest box over an abdomen box, a belt.

SHELLS: every part is ONE box round the pilot's (an arm: fist and cage; a leg: sole and cage; the chest; the
abdomen; the belt), its frame painted on the faces and its gaps see-through texels (alpha 0), like the player skin's jacket
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
# Two BUILDS (2026-11-04, user, like vanilla's wide Steve and slim Alex arms): 'wide' -> power_frame.bbmodel and 'slim'
# -> power_frame_slim.bbmodel (its pilot Alex). They differ only in the chest's width and so where the arms hang
# (CHEST_X): limbs, waist, belt, boots, fists and paint are shared, so are the arm, leg and helmet armour; only a
# torso armour piece needs two widths. The game is to pick by the pilot's skin model. Without BUILD (and FRAME_OUT,
# which frame_drafts.py sets) the script writes both.
BUILD = os.environ.get('BUILD')
if BUILD is None and not os.environ.get('FRAME_OUT'):
    import subprocess, sys
    for _b in ('wide', 'slim'):
        subprocess.run([sys.executable, os.path.abspath(__file__)], env=dict(os.environ, BUILD=_b), check=True)
    sys.exit()
BUILD = BUILD or 'wide'
assert BUILD in ('wide', 'slim'), BUILD
OUT = os.environ.get('FRAME_OUT') or os.path.join(HERE, '..', 'power_frame' + ('_slim' if BUILD == 'slim' else '') + '.bbmodel')
# The rest pose is OPEN, not Steve's idle: a bulky suit can't close like a body, so the arms hang away from the torso
# and the legs stand apart. The splay (degrees, outward; env overrides are for trying values) turns Steve's limbs with
# their frame bones.
# 2026-11-04 (user): none. The arms hang straight off a chest wider than the legs (CHEST_X), so the fists clear the
# legs' sides. (Was arms 9 deg / legs 3 while his limbs touched his torso, then 4 / 2, then 2 / 1 as they moved out.)
ARM_SPLAY = float(os.environ.get('ARM_SPLAY', 0))
LEG_SPLAY = float(os.environ.get('LEG_SPLAY', 0))
# Spacing (2026-11-04, user). The frame is an entity you climb into and draws the pilot itself, so his limbs needn't sit
# where vanilla's do. Legs 0.25 px out (LEG_OUT), so their frame boxes touch, like vanilla's legs; the waist and belt
# line up with them (TX, as vanilla's torso with its legs). The torso is split: a chest wider than the waist (CHEST_X,
# per BUILD) with the arm boxes against its sides (ARM_OUT follows), so the straight arms' fists hang beside the legs
# with room to spare (wide 1 px, slim 0.5). Parts touch, so the limbs' inner sides (and the torso's sides) are painted
# without holes: two touching faces lie in one plane and would flicker wherever both are seen, but with solid walls
# that plane is shut in (the pilot's opaque limb fills each cage 0.25 px from its inner wall). (Until 2026-11-04: arms
# 0.75 / legs 0.5 with a 4 / 2 deg splay; then gaps of 1 px or more between the boxes, filled by gunmetal shoulder and
# hip blocks, arms up to 2.75 out: the gaps were dropped for touching walls, which needed no fillers.)
# (Tried and not picked, 2026-11-04: a bigger frame, torso 11 x 8 and legs 6 x 6, so armour could replace its boxes
# in place; with shoulder blocks in it, but pauldrons belong to the armour tiers: copper without, iron with.)
# Draft knob HELMET ('none' / 'glass'; 2026-11-04, user): the helmet, armour (not in the frame models), drafted here
# for its size and visor. The head's size (no neck: a 1 / 2 px neck with a collar ring or a post was tried and
# dropped): the big suit's, 9 x 9 x 9 like the skin's hat layer (the least that keeps its faces out of his head's
# planes), its bottom 0.5 px under the neck, tucked into the chest armour as vanilla's helmets tuck into chestplates.
# Its visor is glass, so his face shows 0.5 px behind it: partly transparent texels (in game a translucent pass of its
# own), picked over an open slot (the visor lights up in game: the glow needs a pane to sit on). In game the skin's hat
# layer (hair, hats) must hide under a helmet: it lies in the helmet's planes.
HELMET = os.environ.get('HELMET', 'none')
assert HELMET in ('none', 'glass'), HELMET
# The pane's colour and opacity, RGBA hex (env GLASS to try others): the big suit's visor glass colour (262c34,
# tools/make-glowmasks.py) at 80 % (2026-11-04, user, over 60 % and a pale blue at 35 % that read washed out): his face
# a faint silhouette, clear only up close. In game the visor lights up (the glow layer tints it by the suit's state,
# the HUD colour, green by default): a lit pane is that glow drawn over it at part strength, so his face still shows.
_hex = lambda h: tuple(int(h[i:i + 2], 16) for i in range(0, len(h), 2))
GLASS = _hex(os.environ.get('GLASS', '262c34c8'))
GLINT = _hex(os.environ.get('GLINT', 'e8f4ff78'))   # a glint across it, top left to bottom right
LEG_OUT = 0.25
CHEST_X = {'wide': 6.0, 'slim': 5.5}[BUILD]   # the chest's half width
ARM_OUT = CHEST_X - 3.75                      # the arm box's inner wall (3.75 + ARM_OUT) against the chest's side
# The room between his torso and the torso boxes' sides is the frame's: painted solid members, the chest closed on top
# (shoulder platforms) and at the sides.
TX, TZ = 4.75 + LEG_OUT, 3   # the abdomen box's (the waist's) half width, the torso boxes' half depth
assert (TX * 2) % 1 == 0 and (CHEST_X * 2) % 1 == 0, (TX, CHEST_X)   # fronts are 2 texels per px: whole texels outside his torso
# Draft knob: the iron armour over the frame ('none' / 'iron'): helmet, torso, arms, legs. See ARMOR_BOX. Its boxes
# predate the 2026-11-04 spacing (split chest, touching limbs): check_armor() fails on them until they're refitted.
ARMOR = os.environ.get('ARMOR', 'none')
assert ARMOR in ('none', 'iron'), ARMOR
# Draft knob: the arm piece ('SP': a slim sleeve round the upper arm + a pauldron over it, out to its side, as tall
# as the sleeve; 'A': one 7 x 8 x 7 block round the upper arm, the pad and the arm in one). Power reads
# as shoulders much wider than the head (the big suit: 3.3x its helmet; vanilla iron 1.8x). Tried and dropped: an arm
# piece the arm's size (2.2x, vanilla-like); wide flat pads (8 / 10 x 5 / 6: planks); a 7 x 9 x 8 pad (too large);
# S, a 6 x 7 x 7 block.
ARM = os.environ.get('ARM', 'SP')
assert ARM in ('SP', 'A'), ARM
STEVE_PNG = {'wide': 'assets/minecraft/textures/entity/player/wide/steve.png',
             'slim': 'assets/minecraft/textures/entity/player/slim/alex.png'}[BUILD]

def steve_skin():
    """Vanilla's default Steve skin (the slim build: Alex's), read from the Minecraft jar the build already unpacked (run ./gradlew build once).
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

TW, TH = 128, 128   # (widened to 256 below) the one texture (Bedrock / GeckoLib models have one): the pilot's
# 64x64 skin top left, the shells below and right
# The frame's bar colour (the old cube frame's side faces), with a little pixel grain, no noise.
BAR = (0x4c, 0x53, 0x5c)
def bar(i, j): return tuple(min(k + (6 if (i + j) % 3 == 0 else 0), 255) for k in BAR) + (255,)

# ---------------------------------------------------------------- fist art, taken from the big suit
# The fist is the big suit's (power_suit.bbmodel: fist_r, 7x5x7) sized down to the frame's arm (5x4x5). Its art is
# the suit's own painted faces, resampled by picking columns / rows (not scaling), so the finger lines stay whole
# pixels. The suit's texture is its grey base (paint swaps the steel shades), so the frame's fists are that grey too.
# (The big suit's banded cuff over it, forearm_r, sized down to 5x4x5, was the gauntlet's top until 2026-11-04: the
# user gave its room to the frame's cage.)
SUIT = os.path.join(HERE, '..', 'power_suit.bbmodel')
# per cube: the suit cube, the frame size per face (w, h), and which of the suit face's columns / rows to keep
GAUNT = {
    'fist': ('fist_r', {
        'north': ([0, 1, 3, 5, 6], [0, 2, 3, 4]), 'east': ([0, 1, 3, 5, 6], [0, 2, 3, 4]),
        'south': ([0, 1, 3, 5, 6], [0, 2, 3, 4]), 'west': ([0, 1, 2, 3, 6], [0, 2, 3, 4]),
        'up': ([0, 1, 3, 5, 6], [0, 1, 3, 5, 6]), 'down': ([0, 1, 3, 5, 6], [1, 2, 3, 4, 5])}),
}
# The soles have their own art (the fist's read as hands on feet), in the suit's colours (sampled from forearm_r /
# fist_r): a platform sole, a seam over a thick three-row slate sole, lugs underneath (picked from drafts A slate /
# B steel sabaton / C mechanical ankle / D platform; its steel upper, a cuff like the gauntlet's, went with it).
# One letter per texel; rows top to bottom as the face shows. East is the outer side of the right boot (its last column
# at the toe), west the inner; the left boot mirrors them.
PAL = {'A': (0x58, 0x60, 0x6a), 'B': (0x75, 0x7c, 0x85), 'C': (0x9f, 0xa4, 0xa9), 'D': (0x3c, 0x42, 0x4c),
       'K': (0x4c, 0x55, 0x59), 'M': (0x1f, 0x23, 0x26)}
# The sole is 5x4x5: its footprint round his leg is the fist's round his arm (5x5 round 4x4).
# The tread: lines across the sole (along x) every other row, run out to the edges so their ends are the notches on
# the outer / inner sides; with 5 rows it starts and ends on a line, so both sides read line / gap / line from back to
# front and the back's bottom row is the back line, a plain dark strip. The face UVs run: east back -> front, west
# front -> back, north outer -> inner, the sole's rows back -> front and columns inner -> outer. The front's bottom row
# is the sole's front row seen edge on: only its corners are fixed (they meet the side notches), the rest is the
# front's design: a toe lug across the toe's foot, its end the sole's front line (picked from drafts plain / toe lug /
# bumper / riveted).
SIDE = ['DDDDD', 'KKKKK', 'MMMMM', 'MKMKM']
FRONT = ['DDDDD', 'KKKKK', 'MMMMM', 'MKKKM']   # the seam, the sole and toe lug
assert FRONT[-1][0] == FRONT[-1][-1] == 'M', "the front row's corners meet the side notches"
BOOT = {
    'north': FRONT,
    'south': SIDE[:3] + ['MMMMM'],
    'east': SIDE, 'west': SIDE,
    'up': ['DDDDD'] * 5,                                                        # inside the shell: never seen
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
# A limb's faces are composed top to bottom from its painted frame (ARM_ART / LEG_ART: PAL letters, '#' a bar texel,
# '.' a window) over the fist's or the sole's art, in the same face column order as theirs (north outer -> inner,
# east back -> front, south inner -> outer, west front -> back).
# 'T' is a bar texel that tucks into its neighbour (hidden in it); the clearance check skips it.
# Texel density per face: `rows` texel rows per px up the sides (the legs: 2, so the cage can end on a half pixel,
# under the belt, while the sole's rows stay whole; every art row is drawn twice) and the arms' top face 2 texels
# per px, so its rim is 0.5 px (a 1 px rim would lie in the plane of his arm's top and overlap it, so flicker).
# (Until 2026-11-04 the cage was plain bars, open on its inner side; then the user asked for the frame dense and
# mechanical like the Fallout 4 T-45's, his reference: the pilot seen only through small windows, mostly at his hands
# and feet.)
def _rim(n):
    """The top face, n x n texels, rows front -> back, columns inner -> outer: the band and bar tops. The bands'
    inner ends tuck (the splay dips them into the chest's struts)."""
    return ['T' + '#' * (n - 1)] + ['.' * (n - 1) + '#'] * (n - 2) + ['T' + '#' * (n - 1)]
BELT_X = TX   # the belt's half width: the torso box's
def _belt():
    """The belt (the hip, on the body bone: the torso can bend over it): a ring round his pelvis, x +-BELT_X, y 16..17,
    z -3..3; its top and bottom are rims (front and back rows, the half-px side columns)."""
    n = round(BELT_X * 4)   # half-px columns
    rim = lambda c: ['#' * n] + [c + '.' * (n - 2) + c] * 4 + ['#' * n]
    # underneath, the side columns are 'T': at the splay each leg's top outer corner rises 0.1 px past the belt's
    # bottom, 0.003 px inside its side column (hidden in the ring)
    return {'north': ['#' * n], 'south': ['#' * n], 'east': ['#' * 6], 'west': ['#' * 6], 'up': rim('#'), 'down': rim('T')}

# ---------------------------------------------------------------- armour drafts (ARMOR)
# The armour is SEALED: each piece is a closed box, every face solid (the see-through texels are the frame's only).
# It is the frame's size, not a box round it: each piece is its frame box plus 0.25 px (the least that keeps every face
# out of the frame's planes), so the suited player stays at vanilla armour's size (bigger armour read as a costume).
# Its look is the big suit's (power_suit.bbmodel, the marketing pictures): plates in its mid steel, everything
# between them (waist, upper arms, knees, inner legs) its dark slate under-armour, the helmet its helmet.
# Where pieces overlap (a shoulder pad and the helmet, the leg tops and the belt, the two legs at the crotch) each one's
# faces inside the other are hidden by it; what must never happen is two faces in one plane (they flicker):
# check_armor() tests every armour face against Steve, the frame's solid texels and the other pieces.
ARMPAL = {'g': 'cdcfcf', 'h': '9fa4a9', 'l': '757c85', 'p': '58606a', 's': '3c424c',      # the plates' steel
          'n': '4c5559', 'm': '3b4246', 'k': '2f3437', 'd': '25292c', 'x': '1a1d1f'}      # the slate under-armour
ARMPAL = {c: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) for c, h in ARMPAL.items()}

# The pieces, one letter per texel (ARMPAL), faces as the frame's: north / south as seen from the front / back (the
# limbs' north: outer column first), east back -> front, west front -> back, up rows front -> back and columns
# x- -> x+. Right side; the left mirrors it.
_TORSO = {   # 10 x 12 x 7: a breastplate over a pinched dark waist with a small plate, the waist band over the belt
    'north': ['ghhhhhhhhh', 'lpppppppps', 'lpppppppps', 'lpplppppps', 'lppppplpps', 'lppppppppp', 'ssssssssss',
              'dkkhhhhkkd', 'dmmlppsmmd', 'dkklppskkd', 'dmmssssmmd', 'dddddddddd'],
    'south': ['ghhhddhhhh', 'lpppddppps', 'lpppddppps', 'lplpddppps', 'lpppddplps', 'lpppddppps', 'ssssddssss',
              'dkkkkkkkkd', 'dmmmmmmmmd', 'dkkkkkkkkd', 'dmmmmmmmmd', 'dddddddddd'],
    'east': ['hhhhhhg', 'spppppl', 'spppppl', 'spppppl', 'spppppl', 'spppppl', 'sssssss',
             'dkkkkkd', 'dmmmmmd', 'dkkkkkd', 'dmmmmmd', 'ddddddd'],
    'up': ['hhhhhhhhhh', 'llllllllll', 'llllllllll', 'llllllllll', 'llllllllll', 'llllllllll', 'pppppppppp'],
    'down': ['dddddddddd'] * 7}
_TORSO['west'] = [r[::-1] for r in _TORSO['east']]
_LEG = {     # 5 x 12 x 5: thigh plate, a knee cap on the dark knee, shin plate down to the sole; inner side dark. The
             # inner column of the front / back is one colour: the two legs' pieces meet at the crotch
    'north': ['hhhhk', 'lpppk', 'lpppk', 'lplpk', 'ssssk', 'mhhmk', 'mssmk', 'hhhhk', 'lpppk', 'lpppk', 'lplpk', 'ssssk'],
    'east': ['hhhhh', 'plppl', 'ppppl', 'ppppl', 'sssss', 'mmmhh', 'mmmss', 'hhhhh', 'ppppl', 'plppl', 'ppppl', 'sssss'],
    'west': ['kkkkk', 'mmmmm'] * 5 + ['kkkkk', 'ddddd'],
    'up': ['kkkkk'] * 5, 'down': ['ddddd'] * 5}
_LEG['south'] = ['khhhh', 'kppps', 'kppps', 'kppps', 'kssss', 'kkkkk', 'kmmmm', 'khhhh', 'kppps', 'kppps', 'kppps', 'kssss']
def _sleeve(W, H, D):   # the slim arm piece under a pauldron: dark ribbed under-armour all round
    rib = lambda w: ['k' * w, 'm' * w] * (H // 2) + ['k' * w] * (H % 2)
    return {'north': rib(W), 'south': rib(W), 'east': rib(D), 'west': rib(D), 'up': ['k' * W] * D,
            'down': ['k' * W] * D}

def _pad(W, H, D):
    """The arm piece, a shoulder pad W x H x D: one big plate, lit from the upper left, a rolled rim (a light row) over
    its bottom edge's shadow; its top a plate with a bright front rim and outer edge; its inner side (against the
    torso) and underside slate."""
    face = lambda w: ['g' + 'h' * (w - 1)] + ['l' + 'p' * (w - 2) + 's'] * (H - 3) + ['l' * w, 's' * w]
    north = face(W)                                   # outer column first
    up = ['h' * W] + ['l' * (W - 1) + 'h'] * (D - 2) + ['p' * (W - 1) + 'l']   # rows front -> back, columns inner -> outer
    inner = ['k' * D, 'm' * D] * (H // 2) + ['k' * D] * (H % 2)
    return {'north': north, 'south': [r[::-1] for r in north], 'east': face(D), 'west': inner, 'up': up,
            'down': ['k' * W] * D}

for _f in (_TORSO, _LEG):
    for _k, _rows in _f.items(): assert len({len(r) for r in _rows}) == 1, (_k, _rows)

SUIT_HELMET = ['helmet_shell', 'visor', 'helmet_brow', 'cheek_l', 'cheek_r', 'chin_guard']
def _helmet(visor='painted'):
    """The big suit's helmet (power_suit.bbmodel: a 9 px shell round the pilot's head, a 7x5 visor, the brow band,
    cheeks and chin guard standing 0.75 px proud) as ONE 9x9x9 box: each face is the old helmet seen straight from
    that side (the front-most cube's face at every texel), so the proud parts are painted flat. Underside slate.
    visor: 'painted' as the old one, 'glass' (HELMET) a tinted pane (GLASS) with a glint."""
    m = json.load(open(SUIT))
    img = unpng(base64.b64decode(m['textures'][0]['source'].split(',', 1)[1]))
    cs = [e for e in m['elements'] if e['name'] in SUIT_HELMET]
    X0, X1 = -4.5, 4.5
    Y0, Y1 = min(e['from'][1] for e in cs), max(e['to'][1] for e in cs)
    Z0, Z1 = min(e['from'][2] for e in cs), max(e['to'][2] for e in cs)
    def sample(e, f, a, b):   # the texel of face f of cube e at fractions a (across, as the face's u runs) / b (down)
        fc = e['faces'][f]
        assert not fc.get('rotation'), (e['name'], f)
        u0, v0, u1, v1 = fc['uv']
        u, v = u0 + a * (u1 - u0), v0 + b * (v1 - v0)
        return tuple(img[min(int(v), len(img) - 1)][min(int(u), len(img[0]) - 1)][:3])
    n = 9
    xs = [X1 - (i + .5) * (X1 - X0) / n for i in range(n)]            # north columns: x+ -> x-
    ys = [Y1 - (j + .5) * (Y1 - Y0) / n for j in range(n)]
    zs = [Z0 + (i + .5) * (Z1 - Z0) / n for i in range(n)]            # front -> back
    inside = lambda e, k, t: e['from'][k] <= t <= e['to'][k]
    def view(f, cols, axis, pick):
        rows = []
        for y in ys:
            row = []
            for c in cols:
                hit = [e for e in cs if inside(e, 1, y) and inside(e, axis, c)]
                e = pick(hit)
                (fx, fy, fz), (tx, ty, tz) = e['from'], e['to']
                b = (ty - y) / (ty - fy)
                a = {'north': (tx - c) / (tx - fx) if axis == 0 else 0, 'south': (c - fx) / (tx - fx),
                     'east': (tz - c) / (tz - fz), 'west': (c - fz) / (tz - fz)}[f]
                if e['name'] == 'visor' and visor != 'painted':
                    row.append(GLASS)
                else:
                    row.append(sample(e, f, a, b) + (255,))
            rows.append(row)
        return rows
    faces = {'north': view('north', xs, 0, lambda h: min(h, key=lambda e: e['from'][2])),
             'south': view('south', xs[::-1], 0, lambda h: max(h, key=lambda e: e['to'][2])),
             'east': view('east', zs[::-1], 2, lambda h: max(h, key=lambda e: e['to'][0])),
             'west': view('west', zs, 2, lambda h: min(h, key=lambda e: e['from'][0]))}
    top = []
    for z in zs:
        row = []
        for x in xs[::-1]:
            hit = [e for e in cs if inside(e, 0, x) and inside(e, 2, z)]
            e = max(hit, key=lambda e: e['to'][1])
            (fx, _, fz), (tx, _, tz) = e['from'], e['to']
            row.append(sample(e, 'up', (x - fx) / (tx - fx), (z - fz) / (tz - fz)) + (255,))
        top.append(row)
    faces['up'] = top
    faces['down'] = ['k' * n] * n
    if visor == 'glass':   # the glint: a short diagonal across the pane's top left corner (one across the whole pane
        # read as a scar on his face)
        pane = [(i, j) for j, r in enumerate(faces['north']) for i, c in enumerate(r) if c == GLASS]
        i0, j0 = min(i for i, _ in pane), min(j for _, j in pane)
        for i, j in pane:
            if (i - i0) + (j - j0) == 2: faces['north'][j][i] = GLINT
    return faces

# Boxes, right side (the left mirrors), in the frame's numbers (helmet / torso / arms lifted by FY; legs raw, like the
# soles). Each is its frame box (or Steve's head) plus 0.25 px, except where that would put a face in a plane. Heights
# below are as built (with FY):
# - helmet: 0.5 px round his head (the old helmet's clearance), its bottom 0.5 px under his neck, in the torso piece.
# - torso: its top 0.5 px over his neck (28.5; his torso's top and his head's underside are at 28), its bottom 0.5 px
#   into the belt (16.5; the belt's top and bottom are at 17 and 16).
# - arms (ARM_BOX, in the arm's numbers before ARM_OUT moves it out): around the upper arm from y 21.25 to 0.25 px
#   over his shoulder, inner wall 0.5 px inside his arm's. OPEN: under it the frame's cage shows, down to the fist
#   (y 16), with his forearm in it: the sleeve down to the fist, or a forearm piece? A box round his arm can't clear
#   the helmet: its top inner corner, turned by the splay, tucks into the helmet's base (the sleeve 0.26 px, A 0.35 px;
#   hidden inside it). The pauldron (PAD_BOX) needn't enclose him, so it starts further out and clears the helmet
#   (by 0.4 px); 0.25 px in front of / behind the torso piece; it covers the sleeve's whole height (a pad only on top
#   of it was tried: the dark sleeve showed under it).
# - legs: from 0.25 px over the sole's top (3.75: his feet stand at 4; no face of the frame there, sole and cage are
#   one box) up into the belt (16.5; his leg's top is at 16: the leg's turned planes cross the belt's, never lie in
#   them); inner wall 0.5 px inside his leg's. With his legs out (LEG_OUT) the two legs' pieces just meet at the crotch
#   (_LEG paints their inner columns one colour, so even a touch can't flicker).
ARMOR_BOX = {'helmet': ((-4.5, 25.5, -4.5), (4.5, 34.5, 4.5)),
             'torso': ((-4.75, 14.5, -3.25), (4.75, 26.5, 3.25)),
             'leg': ((-0.5, 3.75, -2.75), (5.0, 16.5, 2.75))}   # raw (legs)
ARM_BOX = {'A': ((3.5, 19.5, -3.5), (10.5, 27.5, 3.5)), 'SP': ((3.5, 19.25, -2.75), (9.0, 26.25, 2.75))}
PAD_BOX = ((4.75, 19.0, -3.5), (10.75, 27.5, 3.5))   # its bottom 0.25 px under the sleeve's (not in its plane)
def _dx(box, dx): return tuple((c[0] + dx, c[1], c[2]) for c in box)
ARMOR_BOX['arm'] = _dx(ARM_BOX[ARM], ARM_OUT)
ARMOR_BOX['leg'] = _dx(ARMOR_BOX['leg'], LEG_OUT)
# With the arms out, the pad keeps its inner wall (grows outward), unless his arm's inner face lands in it (4.75)
PAD_BOX = ((PAD_BOX[0][0] + (0.25 if ARM_OUT == 0.75 else 0), PAD_BOX[0][1], PAD_BOX[0][2]),
           (PAD_BOX[1][0] + ARM_OUT, PAD_BOX[1][1], PAD_BOX[1][2]))
def _dims(box): return [round(box[1][k] - box[0][k]) for k in range(3)]   # its art's texels (one per px, ~)

def _legrows(face): return face[:1] + [r for r in face[1:] for _ in range(2)]   # the half-px top row once, the rest twice

# The paint, after the user's reference (2026-11-04: the threezero 1/6 Fallout 4 T-45 frame figure; it replaced a
# denser paint of rails and bands). What makes that frame read: light members over a dark inside, the holes in them
# rimmed with grime (depth without geometry), dark gunmetal at the joints (a bolted hub on the knee's and elbow's outer
# sides, the knee cap, the wrists, the hips), ribbed brass bellows above the knees and on the upper arms, and on the
# torso a copper valve in a ring on the chest, a ribbed black column down a brown leather abdomen. The members are the
# big suit's steel greys (picked over the figure's off-white bone), the accents as the figure's. One letter per texel
# (PAL; '.' a window, 'T' a tucked bar).
PAL.update({'N': (0x3a, 0x3d, 0x41), 'O': (0x62, 0x67, 0x6c), 'X': (0x1b, 0x1c, 0x1f),          # gunmetal joints
            'Q': (0xc0, 0x74, 0x40), 'R': (0x80, 0x44, 0x26),                                        # copper
            'L': (0x5e, 0x3e, 0x2c), 'H': (0x40, 0x29, 0x1d),                                        # leather
            'Y': (0xa8, 0x91, 0x60), 'Z': (0x6a, 0x58, 0x38),                                        # brass bellows
            'W': (0xb4, 0xb9, 0xbe), 'V': (0x87, 0x8e, 0x96), 'U': (0x5f, 0x66, 0x70), 'G': (0x30, 0x34, 0x3a)})  # steel
GRAIN = set('WVU')   # the members get bar()'s light pixel grain (no noise: random grain read as stone)
BAR = PAL['V']       # rims and the belt ('#') in the members' colour
# Arms: 12 rows of cage over the fist, top (shoulder) first; columns as the frame's faces (north outer -> inner, east
# back -> front, south inner -> outer, west front -> back). Shoulder cap, ribbed bellows on the upper arm, a bolted
# elbow band (hub on the outer side), the forearm a cage with a dark piston between slits / an oval window where his
# hand is, gunmetal wrist. The inner side (west: against the chest) has no holes (see the spacing).
ARM_ART = {
    'north': ['WWWWT', 'WVVVU', 'YYYYY', 'ZZZZZ', 'YYYYY', 'WVOVU', 'UNXNG', 'WG.GU', 'W...U', 'W...U', 'WG.GU', 'NNNNN'],
    'east':  ['VVVVW', 'UVVVW', 'YYYYY', 'ZZZZZ', 'YYYYY', 'VNONW', 'UOXOW', 'VNONW', 'V.N.W', 'V.N.W', 'VGNGW', 'NNNNN'],
    'west':  ['VVVVV', 'UVVVU', 'YYYYY', 'ZZZZZ', 'YYYYY', 'UNNNU', 'UGGGU', 'UGNGU', 'UGNGU', 'UGNGU', 'UVVVU', 'NNNNN'],
    'south': ['TWWWW', 'UVVVW', 'YYYYY', 'ZZZZZ', 'YYYYY', 'UVOVW', 'GNXNU', 'UG.GW', 'U...W', 'U...W', 'UG.GW', 'NNNNN']}
# Legs: one row per px from the hip (the top row is half a px: the cage is 11.5 px), drawn at 2 texel rows per px;
# columns as the arms'. Gunmetal hip band, the thigh a cage round a dark piston, bellows above the knee, the knee a
# bolted cap in front (an X plate read as a checkerboard at 3 x 3) and a bolted hub outside, the shin a long cage with
# an oval window over his shoe. The inner side (west: against the other leg) has no holes.
LEG_ART = {
    'north': ['NNNNT', 'WVVVU', 'W.N.U', 'YYYYY', 'ZZZZZ', 'WNNNU', 'WNONU', 'WNNNU', 'WVVVU', 'WG.GU', 'W...U', 'UUUUG'],
    'east':  ['NNNNN', 'UVVVW', 'U.N.W', 'YYYYY', 'ZZZZZ', 'VNONW', 'VOXOW', 'VNONW', 'UVVVW', 'UG.GW', 'U...W', 'GUUUU'],
    'west':  ['NNNNN', 'UVVVU', 'UGNGU', 'YYYYY', 'ZZZZZ', 'UNNNU', 'UGGGU', 'UNNNU', 'UVVVU', 'UGVGU', 'UGVGU', 'GGGGG'],
    'south': ['TNNNN', 'UVVVW', 'U.N.W', 'YYYYY', 'ZZZZZ', 'UNNNW', 'UGGGW', 'UNNNW', 'UVVVW', 'UG.GW', 'U...W', 'GUUUU']}

def _torso():
    """The torso's two boxes, the chest (y 22-28, x +-CHEST_X) over the abdomen (y 17-22, x +-TX), drawn as one face
    and split: front / back 2 texels per px across (the frame's edge members outside his torso, his torso's 8 px drawn
    twice each, the other edge), 11 rows (y 27-28 first); sides 6 across. The edge members are wide side struts (no
    holes: through one you'd look into the empty box beside his torso; grime instead), the sides closed (the arms hang
    beside them), the chest's top closed outside his torso (shoulder platforms), its underside where it overhangs the
    abdomen (a ledge).
    Front: a yoke over the shoulders, a dark ring round a copper valve on the chest, its pipe down into the abdomen: a
    ribbed black column between brown leather, a 1 px window each side; a bolted waist bar. Back: the yoke, shoulder
    blades with a window each beside the ribbed spine, leather lower back."""
    def edge(j):              # the left edge member's row j (outer column first)
        n = round(((CHEST_X if j < 6 else TX) - 4) * 2)   # its texels: the chest's rows (0-5) are wider
        if n == 1: return 'V' if j < 10 else 'G'
        return {0: 'W' * n, 5: 'U' * n, 10: 'G' + 'V' * (n - 1)}.get(j, 'V' + ('G' if 2 <= j <= 3 else 'N' if j >= 6 else 'U') * (n - 2) + 'U')
    def face(chest): return [edge(j) + ''.join(c * 2 for c in s) + edge(j)[::-1] for j, s in enumerate(chest)]
    front = face((
        'WWWWWWWW', 'W.UNNU.W', 'VUNQRNUV', 'VUNRRNUV', 'VG.NN.GV', 'UUURRUUU',
        'HLXNNXLH', '.LXOOXL.', '.LXNNXL.', 'HLXOOXLH', 'GVOVVOVG'))
    back = face((
        'WWWWWWWW', 'VUWNNWUV', 'V.UOOU.V', 'V.UNNU.V', 'VUUOOUUV', 'UGLNNLGU',
        'LHLOOLHL', 'L.LNNL.L', 'L.LOOL.L', 'LHLNNLHL', 'GVOVVOVG'))
    side = ['VVVVVV', 'VNNNNV', 'VNNNNV', 'UUUUUU', 'WWWWWW', 'VOVVOV', 'UUUUUU', 'LHGGHL', 'LHGGHL', 'LLLLLL', 'GVVVVG']
    W, n = len(front[0]), round((CHEST_X - 4) * 2)
    up = [p * n + '.' * (W - 2 * n) + p * n for p in 'WVVVVU']   # rows front -> back
    k = round((CHEST_X - TX) * 2)
    chest = {'north': front[:6], 'south': back[:6], 'east': side[:6], 'west': side[:6], 'up': up,
             'down': ['U' * k + '.' * (W - 2 * k) + 'U' * k] * 6}
    w = len(front[6])
    abdomen = {'north': front[6:], 'south': back[6:], 'east': side[6:], 'west': side[6:], 'up': ['.' * w] * 6,
               'down': ['.' * w] * 6}
    return chest, abdomen

SHELLS = {'arm_shell': dict(cage=dict(ARM_ART), below=['fist'], rows=1, up=_rim(10)),
          'leg_shell': dict(cage={f: _legrows(r) for f, r in LEG_ART.items()}, below=['boot'], rows=2, up=['.' * 5] * 5),
          'belt_shell': dict(faces=_belt())}
SHELLS['chest_shell'], SHELLS['abdomen_shell'] = (dict(faces=f) for f in _torso())
if ARMOR != 'none':
    SHELLS.update({'helmet_armor': dict(faces=_helmet(HELMET)), 'torso_armor': dict(faces=_TORSO),
                   'arm_armor': dict(faces=(_sleeve if ARM == 'SP' else _pad)(*_dims(ARMOR_BOX['arm']))),
                   'leg_armor': dict(faces=_LEG)})
    if ARM == 'SP': SHELLS['pad_armor'] = dict(faces=_pad(*_dims(PAD_BOX)))
elif HELMET != 'none': SHELLS['helmet_armor'] = dict(faces=_helmet(HELMET))
# where each shell's faces sit in the texture (below / beside nothing else), one strip per shell
SHELL_AT = {'arm_shell': (0, 64), 'leg_shell': (0, 80),
            'torso_armor': (35, 87), 'arm_armor': (35, 101), 'leg_armor': (35, 112), 'helmet_armor': (70, 112),
            'pad_armor': (78, 101)}
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

# The torso (out to the legs) and the belt don't fit beside the limbs' strips: a 256 wide texture, those on its right
# half.
TW = 256
SHELL_AT.update(belt_shell=(128, 32), chest_shell=(128, 80), abdomen_shell=(128, 92))

SHELL_UV, _used = {}, set()
for part, (u, v) in SHELL_AT.items():
    if part not in SHELLS: continue
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
                    elif c in ARMPAL: px[v + j][u + i] = ARMPAL[c] + (255,)
                    elif len(c) == 4: px[v + j][u + i] = c                      # a colour (the helmet's)
                    elif isinstance(c, str) and c in GRAIN:
                        px[v + j][u + i] = tuple(min(k + (6 if (i + j) % 3 == 0 else 0), 255) for k in PAL[c]) + (255,)
                    elif isinstance(c, str): px[v + j][u + i] = PAL[c] + (255,)
                    else: px[v + j][u + i] = src[c[1]][c[0]]

def shell_slabs(part, a, b):
    """The shell's solid texels ('T' aside) as thin boxes (right side coordinates) with their (face, column, row), for
    the clearance check: a hole may pass through another part, a bar may not."""
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
                if f == 'north': out.append(((x1 - (i + 1) * cw, ya, z0), (x1 - i * cw, yb, z0 + t), (f, i, j)))
                elif f == 'south': out.append(((x0 + i * cw, ya, z1 - t), (x0 + (i + 1) * cw, yb, z1), (f, i, j)))
                elif f == 'east': out.append(((x1 - t, ya, z1 - (i + 1) * cw), (x1, yb, z1 - i * cw), (f, i, j)))
                elif f == 'west': out.append(((x0, ya, z0 + i * cw), (x0 + t, yb, z0 + (i + 1) * cw), (f, i, j)))
                elif f == 'up': out.append(((x0 + i * cw, y1 - t, z0 + j * rh), (x0 + (i + 1) * cw, y1, z0 + (j + 1) * rh), (f, i, j)))
                else: out.append(((x0 + i * cw, y0, z0 + j * rh), (x0 + (i + 1) * cw, y0 + t, z0 + (j + 1) * rh), (f, i, j)))
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

FY = 2   # he stands on the 4 px soles (his feet at y 4): everything above the soles is lifted 2 px from the numbers below
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
    e['_args'] = (part, side, a, b, 0 if raw else FY)
    slab_up(e)
    return e

def slab_up(e):
    part, side, a, b, dy = e['_args']
    slabs = []
    for p, q, tid in shell_slabs(part, (a[0], a[1] + dy, a[2]), (b[0], b[1] + dy, b[2])):
        if side == 'l': p, q = mir(p, q)
        slabs.append({'name': e['name'], 'from': [min(p[i], q[i]) for i in range(3)], 'to': [max(p[i], q[i]) for i in range(3)],
                      'origin': e['origin'], 'uuid': uid(), '_g': e['_g'], '_tid': tid})
    e['_hollow'] = slabs

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
# (the slim build's pilot, Alex, has 3 px arms: on the inner side of the cage, as vanilla's slim arms hug the torso)
AW = 3 if BUILD == 'slim' else 4
STEVE = {'head': (0, 0, 8, 8, 8), 'torso': (16, 16, 8, 12, 4), 'arm_r': (40, 16, AW, 12, 4), 'arm_l': (32, 48, AW, 12, 4),
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
hollow(torso, 'r', 'chest_shell', 'fr_chest_shell', (-CHEST_X, 20, -TZ), (CHEST_X, 26, TZ))
hollow(torso, 'r', 'abdomen_shell', 'fr_abdomen_shell', (-TX, 15, -TZ), (TX, 20, TZ))
pilot = group('pilot_torso', (0, 14, 0), torso)
steve(pilot, 'st_torso', (-4, 14, -2), (4, 26, 2), STEVE['torso'])

# ---------- belt (the hip): a ring outside Steve's pelvis, on the body bone (its art: _belt())
hollow(body, 'r', 'belt_shell', 'fr_belt_shell', (-BELT_X, 14, -3), (BELT_X, 15, 3))

# ---------- limbs (right side defined, left mirrored). Steve's limbs don't bend, so each limb is ONE rigid bone.
for side in ('r', 'l'):
    s = 1 if side == 'r' else -1
    nm = 'right' if s > 0 else 'left'
    sx = lambda a, b: (a, b) if s > 0 else mir(a, b)
    # leg: Steve's leg x 0..4, hip pivot (±2, 14)
    leg = group(f'{nm}_leg', ((2 + LEG_OUT) * s, 14, 0), root)
    leg['rotation'] = [0, 0, LEG_SPLAY * s]
    # One box: the sole and the cage above it (its art: SHELLS, _cage, BOOT). The sole is 5x4x5 under his foot, placed
    # on his leg as the fist is on his arm (0.25 px inside, 0.75 px outside, 0.5 px front and back), 3 px under his
    # foot's bottom; the cage is in line with it, from the sole (y 4) up. It turns with the leg, so the sole tilts with
    # the splay (outer edge 0.1 px up at 2 deg). It stops at y 15.5, 0.5 px under the belt: its top band's outer end
    # rises as the leg splays.
    hollow(leg, side, 'leg_shell', f'fr_leg_shell_{side}', (-0.25 + LEG_OUT, 0, -2.5), (4.75 + LEG_OUT, 15.5, 2.5), raw=True)
    p = group(f'pilot_{nm}_leg', ((2 + LEG_OUT) * s, 14, 0), leg)
    steve(p, f'st_leg_{side}', *sx((0 + LEG_OUT, 2, -2), (4 + LEG_OUT, 14, 2)), STEVE[f'leg_{side}'])
    # arm: Steve's arm x 4..8 (his hand at the bottom, y 14), shoulder pivot (±6, 26)
    arm = group(f'{nm}_arm', ((6 + ARM_OUT) * s, 26, 0), torso)
    arm['rotation'] = [0, 0, ARM_SPLAY * s]
    # One box: the fist (the big suit's, sized down: 5x4x5 over his hand, so 2 px past it) and the cage above it, up to
    # his shoulder's top. Its footprint is 0.5 px round his arm front and back, 0.25 px inside (the closest encloses()
    # allows) and 0.75 px outside.
    hollow(arm, side, 'arm_shell', f'fr_arm_shell_{side}', (3.75 + ARM_OUT, 10, -2.5), (8.75 + ARM_OUT, 26, 2.5))
    p = group(f'pilot_{nm}_arm', ((6 + ARM_OUT) * s, 26, 0), arm)
    steve(p, f'st_arm_{side}', *sx((4 + ARM_OUT, 14, -2), (4 + AW + ARM_OUT, 26, 2)), STEVE[f'arm_{side}'])

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

def to_world(e, pts):
    """3D points of cube e (its coordinates) in world space: its own turn, then its bones' (all about z)."""
    import math
    def rot(P, o, deg):
        if not deg: return P
        a = math.radians(deg); c, sn = math.cos(a), math.sin(a)
        return [(o[0] + (x - o[0]) * c - (y - o[1]) * sn, o[1] + (x - o[0]) * sn + (y - o[1]) * c, z) for x, y, z in P]
    pts = rot(pts, e['origin'], e.get('rotation', [0, 0, 0])[2])
    g = e['_g']
    while g is not None:
        pts = rot(pts, g['origin'], g['rotation'][2])
        g = g['_parent']
    return pts

def quads(e):
    (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
    fs = [[(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
          [(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)], [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)],
          [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]]
    return [to_world(e, f) for f in fs]

def coplanar(A, B, eps=1e-3, area=0.05):
    """Do quads A and B lie in one plane (within eps) and overlap there by more than `area` px each way? (The frame's
    texel slabs are 0.01 px thick: their edges are no faces.)"""
    sub = lambda p, q: tuple(p[k] - q[k] for k in range(3))
    dot = lambda p, q: sum(p[k] * q[k] for k in range(3))
    crs = lambda p, q: (p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0])
    nrm = lambda p: tuple(c / dot(p, p) ** 0.5 for c in p)
    n = nrm(crs(sub(A[1], A[0]), sub(A[3], A[0])))
    m = nrm(crs(sub(B[1], B[0]), sub(B[3], B[0])))
    if abs(abs(dot(n, m)) - 1) > 1e-6 or abs(dot(n, sub(B[0], A[0]))) > eps: return False
    u = nrm(sub(A[1], A[0])); v = crs(n, u)
    P = [(dot(sub(p, A[0]), u), dot(sub(p, A[0]), v)) for p in A]
    Q = [(dot(sub(p, A[0]), u), dot(sub(p, A[0]), v)) for p in B]
    for poly in (P, Q):
        for i in range(4):
            (xa, ya), (xb, yb) = poly[i], poly[(i + 1) % 4]
            ax = (ya - yb, xb - xa)
            ln = (ax[0] ** 2 + ax[1] ** 2) ** 0.5
            if ln < 1e-9: continue
            pr = lambda R: [(x * ax[0] + y * ax[1]) / ln for x, y in R]
            a_, b_ = pr(P), pr(Q)
            if min(max(a_), max(b_)) - max(min(a_), min(b_)) <= area: return False
    return True

def check_armor():
    """The sealed pieces: each encloses Steve's part of its bone (0.1 px to spare, so no face of his lies in one of
    its faces), and no face of a piece shares a plane with any other face (Steve's, the frame's solid texels', another
    piece's). Overlapping other solid parts is fine (each hides the other's faces inside it)."""
    bad = []
    pieces = [e for e in elements if e.get('_armor')]
    others = [x for e in elements for x in e.get('_hollow', [e])]
    W = {x['uuid']: world(x) for x in others}
    for a in pieces:
        for st in elements:
            if a['_armor'] != 'addon' and st['name'].startswith('st_') and limb(st) == limb(a) \
                    and st['_g']['_parent'] is a['_g']['_parent'] \
                    and not encloses(a, st, W, margin=0.1):
                bad.append((a['name'], 'does not enclose', st['name']))
        fa = quads(a)
        for o in others:
            if o is a or {a['name'], o['name']} == {'ar_leg_r', 'ar_leg_l'}: continue   # see _leg_inner
            for B in quads(o):
                if any(coplanar(A, B) for A in fa):
                    bad.append((a['name'], 'shares a plane with', o['name']))
                    break
    return bad

def check():
    items = [s for e in elements if not e.get('_armor') for s in e.get('_hollow', [e])]   # shells by their solid texels
    of = {s['uuid']: e for e in elements for s in e.get('_hollow', [])}
    W = {e['uuid']: world(e) for e in elements + items}
    bad = []
    for i, a in enumerate(items):
        for b in items[i + 1:]:
            if limb(a) == limb(b) and (a['uuid'] in of or b['uuid'] in of):   # a shell's texel and his part
                sh, st = (of[a['uuid']], b) if a['uuid'] in of else (of[b['uuid']], a)
                if st['name'].startswith(('fr_', 'ar_')) or encloses(sh, st, W): continue
            fa, fb = a['name'].startswith(('fr_', 'ar_')), b['name'].startswith(('fr_', 'ar_'))
            if not (fa or fb): continue                                   # Steve against himself: vanilla's business
            if fa and fb and limb(a) == limb(b): continue                 # one limb's frame is built touching itself
            if overlap(W[a['uuid']], W[b['uuid']]): bad.append((a['name'], b['name']))
    return bad

# ---------------------------------------------------------------- armour (draft)
# One piece per bone, each in its own group (so a piece can be shown / hidden as one): helmet, torso, arms, legs.
def _armor(bone, side, piece, name, raw=False, box=None, addon=False):
    g = next(x for x in groups if x['name'] == bone)
    ga = next((x for x in groups if x['name'] == f'armor_{bone}'), None) or \
        group(f'armor_{bone}', [g['origin'][0], g['origin'][1] - FY, g['origin'][2]], g)
    e = painted(ga, side, f'{piece}_armor', name, *(box or ARMOR_BOX[piece]), raw=raw)
    e['_armor'] = 'addon' if addon else True   # an add-on (the pauldron) sits on its bone's piece: needn't enclose him
if 'helmet_armor' in SHELLS: _armor('head', 'r', 'helmet', 'ar_helmet')
if ARMOR != 'none':
    _armor('torso', 'r', 'torso', 'ar_torso')
    for side, nm in (('r', 'right'), ('l', 'left')):
        _armor(f'{nm}_arm', side, 'arm', f'ar_arm_{side}')
        if ARM == 'SP': _armor(f'{nm}_arm', side, 'pad', f'ar_pad_{side}', box=PAD_BOX, addon=True)
        _armor(f'{nm}_leg', side, 'leg', f'ar_leg_{side}', raw=True)

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
    bad = check() + check_armor()
    assert not bad, bad
    for g in groups:
        g.pop('_parent', None)
    for e in elements:
        e.pop('_g', None)
        e.pop('_shell', None)
        e.pop('_hollow', None)
        e.pop('_args', None)
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

