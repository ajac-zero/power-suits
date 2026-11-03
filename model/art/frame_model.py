#!/usr/bin/env python3
"""Builds model/power_frame.bbmodel: the Fallout-style exosuit FRAME (no armour) around a real-scale Steve.

Run: python3 model/art/frame_model.py        (writes ../power_frame.bbmodel; no dependencies)

Scale: 1 px = 1/16 block, Steve exactly as vanilla (4x12x4 limbs, 8x12x4 torso, 8^3 head, 32 px tall), standing on 3 px
mechanical feet (the leg frame's footprint, like the fists on the arm frame; his soles at y 3). The frame is a hollow cage around him: every limb is two thin posts on the OUTER
side joined by front and back bands 1 px outside the limb; the inner side stays open (the neighbouring
limb / torso is there). Clearance to Steve is 0, never an overlap: see check() at the bottom.

Rig: the names and parent chain of power_suit.bbmodel (marine > body > torso > head / *_arm;
marine > *_leg), so the animation kit and the later armour can reuse them. Steve's limbs are rigid (vanilla), so
there is no elbow, knee, hand or ankle bone: each limb is one bone. Frame cubes are named fr_*, the pilot's st_*; each bone has a
`pilot_*` child group holding Steve's part (hide those to see the bare frame). The head has no frame at all: the helmet is armour, added later.
"""
import base64, json, os, struct, uuid, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'power_frame.bbmodel')

# ---------------------------------------------------------------- textures (written without PIL)
def png(w, h, px):
    raw = b''.join(b'\x00' + b''.join(bytes(px[y][x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + \
        chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

SWY = 60   # the swatch strip's top row in the one 64x64 texture
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
                px[y0 + y][i * 4 + x] = (min(c[0] + s, 255), min(c[1] + s, 255), min(c[2] + s, 255), a)

# Steve placeholder skin in the vanilla 64x64 layout (the real skin replaces it at render time).
def steve_tex():   # Steve placeholder + the frame's swatches
    px = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
    SKIN, HAIR, SHIRT, PANTS, SHOE = (0xb3, 0x82, 0x62, 255), (0x45, 0x2d, 0x1c, 255), (0x1f, 0xa6, 0xa6, 255), \
        (0x3b, 0x3d, 0x96, 255), (0x58, 0x58, 0x58, 255)
    def box(u, v, w, h, d, fn):
        faces = {'n': (u + d, v + d, w, h), 'e': (u + d + w, v + d, d, h), 's': (u + 2 * d + w, v + d, w, h),
                 'w': (u, v + d, d, h), 'u': (u + d, v, w, d), 'd': (u + d + w, v, w, d)}
        for f, (x0, y0, fw, fh) in faces.items():
            for y in range(fh):
                for x in range(fw):
                    px[y0 + y][x0 + x] = fn(f, x, y, fw, fh)
    def head(f, x, y, w, h):
        if f in 'ud' or (f in 'ews' and y < 2) or (f == 'n' and y < 2): return HAIR
        if f == 's': return HAIR
        if f == 'n' and y in (4, 5) and x in (1, 2, 5, 6): return (0xff, 0xff, 0xff, 255) if x in (1, 6) else (0x4b, 0x3c, 0x9c, 255)
        if f == 'n' and y == 7 and 2 <= x <= 5: return (0x8c, 0x5a, 0x3e, 255)
        return SKIN
    box(0, 0, 8, 8, 8, head)
    box(16, 16, 8, 12, 4, lambda f, x, y, w, h: SHIRT if f != 'd' else PANTS)
    sleeve = lambda f, x, y, w, h: SHIRT if (y < 4 or f in 'ud') and f != 'd' else SKIN
    box(40, 16, 4, 12, 4, sleeve)
    box(0, 16, 4, 12, 4, lambda f, x, y, w, h: SHOE if y > 9 else PANTS)
    paint_swatches(px, SWY)
    return png(64, 64, px)

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
    return [j * 4 + 1, SWY + 1, j * 4 + 3, SWY + 3]

def cube(g, name, a, b, mat='mid', raw=False):
    (x0, y0, z0), (x1, y1, z1) = a, b
    if not raw: y0, y1 = y0 + FY, y1 + FY
    e = {'name': name, 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True, 'scope': 0,
         'allow_mirror_modeling': True, 'from': [min(x0, x1), min(y0, y1), min(z0, z1)],
         'to': [max(x0, x1), max(y0, y1), max(z0, z1)], 'autouv': 0, 'color': 1, 'origin': list(g['origin']),
         'faces': {f: {'uv': uv_swatch(f, mat), 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')},
         'type': 'cube', 'uuid': uid()}
    elements.append(e)
    g['children'].append(e['uuid'])
    return e

def steve(g, name, a, b, box, d0, d1):
    """Steve's part: the vanilla box (u, v, w, h, d) cut to rows d0..d1 of its height (from the top)."""
    u, v, w, h, d = box
    top, bot = v + d + d0, v + d + d1
    e = cube(g, name, a, b)
    f = e['faces']
    f['north']['uv'] = [u + d, top, u + d + w, bot]
    f['east']['uv'] = [u + d + w, top, u + 2 * d + w, bot]
    f['south']['uv'] = [u + 2 * d + w, top, u + 2 * d + 2 * w, bot]
    f['west']['uv'] = [u, top, u + d, bot]
    f['up']['uv'] = [u + d, v, u + d + w, v + d]
    f['down']['uv'] = [u + d + w, v + d, u + d + 2 * w, v]
    return e

STEVE = {'head': (0, 0, 8, 8, 8), 'torso': (16, 16, 8, 12, 4), 'arm': (40, 16, 4, 12, 4), 'leg': (0, 16, 4, 12, 4)}
M = lambda xs: [-x for x in xs]
def mir(a, b): return (-a[0], a[1], a[2]), (-b[0], b[1], b[2])

root = group('marine', (0, 0, 0), None)
body = group('body', (0, 14, 0), root)
torso = group('torso', (0, 14, 0), body)
head = group('head', (0, 26, 0), torso)

# ---------- head: no frame. The helmet is a separate armour piece (a later step); the bone is here for it.
pilot = group('pilot_head', (0, 26, 0), head)
steve(pilot, 'st_head', (-4, 26, -4), (4, 34, 4), STEVE['head'], 0, 8)

# ---------- torso: a ribcage of front and back ribs, spine rack for the engine, the chest core
for i, y in enumerate((15, 18, 21, 24)):
    cube(torso, f'fr_rib_f{i}', (-4, y, -3), (4, y + 1, -2), 'mid')
    cube(torso, f'fr_rib_b{i}', (-4, y, 2), (4, y + 1, 3), 'mid')
for sx, n in ((1, 'r'), (-1, 'l')):
    xa, xb = sorted((1.5 * sx - 0.5, 1.5 * sx + 0.5))
    cube(torso, f'fr_sternum_{n}', (xa, 15, -3), (xb, 25, -2), 'dark')
cube(torso, 'fr_core', (-1, 20, -3.8), (1, 22.5, -3), 'core')
cube(torso, 'fr_rack', (-3.5, 15.5, 3), (3.5, 24.5, 4), 'dark')                 # the engine socket's back plate
for sx, n in ((1, 'r'), (-1, 'l')):
    xa, xb = sorted((3.5 * sx, 4.5 * sx))
    cube(torso, f'fr_clamp_hi_{n}', (xa, 21, 3), (xb, 23.5, 5), 'hinge')
    cube(torso, f'fr_clamp_lo_{n}', (xa, 16.5, 3), (xb, 19, 5), 'hinge')
pilot = group('pilot_torso', (0, 14, 0), torso)
steve(pilot, 'st_torso', (-4, 14, -2), (4, 26, 2), STEVE['torso'], 0, 12)

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
    segment(leg, side, 'leg', 0.25, 4, 2, 14, bar=False, mid=8)
    # hip pivot: two knobs at the thigh's front and back top corners (the arm hangs where an outer hinge would be)
    cube(leg, f'fr_hip_f_{side}', *sx((2.5, 12.5, -3.8), (4, 14.5, -3)), 'hinge')
    cube(leg, f'fr_hip_b_{side}', *sx((2.5, 12.5, 3), (4, 14.5, 3.8)), 'hinge')
    cube(leg, f'fr_foot_{side}', *sx((0.25, 0, -3), (4, 3, 3)), 'dark', raw=True)   # the mechanical foot: the leg frame's footprint, 3 px tall
    p = group(f'pilot_{nm}_leg', (2 * s, 14, 0), leg)
    steve(p, f'st_leg_{side}', *sx((0, 2, -2), (4, 14, 2)), STEVE['leg'], 0, 12)
    # arm: Steve's arm x 4..8, shoulder pivot (±6, 26); the fist extends 3 px past his hand (y 14)
    arm = group(f'{nm}_arm', (6 * s, 26, 0), torso)
    segment(arm, side, 'arm', 4, 8, 14, 26, bar=False, mid=20)
    cube(arm, f'fr_pauldron_{side}', *sx((4.5, 26, -3), (8.5, 27, 3)), 'dark')   # joins the front and back bands over his shoulder
    cube(arm, f'fr_fist_{side}', *sx((4, 11, -3), (8, 14, 3)), 'dark')            # the arm frame's footprint, 3 px past his hand
    p = group(f'pilot_{nm}_arm', (6 * s, 26, 0), arm)
    steve(p, f'st_arm_{side}', *sx((4, 14, -2), (8, 26, 2)), STEVE['arm'], 0, 12)

# ---------------------------------------------------------------- clearance check: frame cubes never intersect Steve's
def check():
    st = [e for e in elements if e['name'].startswith('st_')]
    bad = []
    for f in (e for e in elements if e['name'].startswith('fr_') and e['name'] != 'fr_visor_glass'):
        for s_ in st:
            if all(f['from'][i] < s_['to'][i] - 1e-6 and f['to'][i] > s_['from'][i] + 1e-6 for i in range(3)):
                bad.append((f['name'], s_['name']))
    return bad

# ---------------------------------------------------------------- write the project
def outline(g):
    return {'uuid': g['uuid'], 'isOpen': True, 'children': [outline(c) if isinstance(c, dict) else c for c in g['children']]}

if __name__ == '__main__':
    bad = check()
    assert not bad, bad
    for g in groups:
        g.pop('_parent', None)
    model = {
        'meta': {'format_version': '5.0', 'model_format': 'bedrock', 'box_uv': False},
        'name': 'power_frame', 'model_identifier': 'power_frame', 'visible_box': [4, 4.5, 1.75],
        'variable_placeholders': '', 'variable_placeholder_buttons': [], 'unhandled_root_fields': {},
        'resolution': {'width': 64, 'height': 64},
        'elements': elements,
        'groups': [dict(g, children=[]) for g in groups],
        'outliner': [outline(root)],
        'textures': [
            {'name': 'power_frame.png', 'path': '', 'folder': '', 'namespace': '', 'id': '0', 'group': '', 'scope': 0,
             'width': 64, 'height': 64, 'uv_width': 64, 'uv_height': 64, 'particle': False, 'use_as_default': False,
             'layers_enabled': False, 'sync_to_project': '', 'file_format': 'png', 'render_mode': 'default',
             'render_sides': 'auto', 'wrap_mode': 'limited', 'pbr_channel': 'color', 'fps': 7, 'force_translucent': False,
             'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True,
             'internal': True, 'saved': False, 'uuid': uid(), 'source': b64(steve_tex())}],
        'animations': [],
    }
    # groups' children lists hold only structure in the outliner, not in `groups`
    with open(OUT, 'w') as f:
        json.dump(model, f, indent=1)
    print(f'{len(elements)} cubes ({sum(e["name"].startswith("fr_") for e in elements)} frame, '
          f'{sum(e["name"].startswith("st_") for e in elements)} Steve), {len(groups)} groups -> {os.path.normpath(OUT)}')
