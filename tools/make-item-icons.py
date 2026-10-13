"""Draws the item icons of the socket tools (textures/item/<tool>.png) from their 3D models.

    uv run --with pillow python3 tools/make-item-icons.py        (after extract-tool-geo.py)

Why: the tool items are flat like vanilla tools (models/item/<tool>.json = item/handheld: in slots it's the
picture, in hand the picture extruded 1 px thick); only the suit wears them in 3D. The icon is drawn from the
3D model so both look like the same tool.

How: a tiny software renderer. It reads geo/item/<tool>.geo.json + the suit texture, lays out each
cube's box UV like Blockbench does, turns the tool so its working end points to the top right (like a
vanilla pickaxe), shades faces by their direction, renders 8x too big and shrinks to 32x32 (same size
as the suit's icon), then adds a dark 1 px outline so the icon reads on any slot colour.
Re-run it whenever the tools' art changes.
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/powerarmor'
TEXTURE = Image.open(ASSETS / 'textures/entity/power_armor.png').convert('RGBA')
TOOLS = ['chaingun', 'em_projector', 'drill', 'chainsaw']
SIZE = 32          # icon size in pixels
SS = 8             # supersampling factor
MARGIN = 1.5       # icon pixels kept free around the tool (for the outline)
# View: spin the tool about its own axis (show two sides), tip its working end towards the camera (show the
# barrel mouths / prong tips), then lay it diagonally with the working end (-Y) to the top right.
SPIN, TILT, ROLL = 35, -20, 135
LIGHT = (-0.45, 0.75, 0.5)  # towards the light: upper left, in front


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return ((1, 0, 0), (0, c, -s), (0, s, c))


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, 0, s), (0, 1, 0), (-s, 0, c))


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def box_uv(size, mirror):
    """Blockbench's box-UV net: face -> (u1, v1, u2, v2) relative to the cube's uv offset."""
    w, h, d = size
    faces = {
        'east': [0, d, d, h], 'west': [d + w, d, d, h],
        'up': [d + w, d, -w, -d], 'down': [d + 2 * w, 0, -w, d],
        'south': [2 * d + w, d, w, h], 'north': [d, d, w, h],
    }
    if mirror:  # mirror_uv flips every face sideways and swaps east/west
        for f in faces.values():
            f[0] += f[2]
            f[2] = -f[2]
        faces['east'], faces['west'] = faces['west'], faces['east']
    return {k: (u, v, u + du, v + dv) for k, (u, v, du, dv) in faces.items()}


def face_corners(lo, hi):
    """For each face, its corners in texture order: (u1,v1) (u2,v1) (u2,v2) (u1,v2), as Minecraft maps them."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


def quads(tool):
    """Every textured face of the tool in Blockbench space (the geo file is Bedrock: X mirrored)."""
    geo = json.loads((ASSETS / f'geo/item/{tool}.geo.json').read_text())['minecraft:geometry'][0]
    out = []
    for bone in geo['bones']:  # the item bones carry no rotations, only cubes do
        for c in bone.get('cubes', []):
            (ox, oy, oz), (sx, sy, sz) = c['origin'], c['size']
            ox = -(ox + sx)  # Bedrock -> Blockbench x
            g = c.get('inflate', 0)
            lo, hi = (ox - g, oy - g, oz - g), (ox + sx + g, oy + sy + g, oz + sz + g)
            m = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
            pivot = (0, 0, 0)
            if 'rotation' in c:  # Bedrock stores (-x, -y, z) of Blockbench's angles; Blockbench applies X, Y, Z
                rx, ry, rz = (math.radians(a) for a in c['rotation'])
                m = mul(rot_z(rz), mul(rot_y(-ry), rot_x(-rx)))
                p = c['pivot']
                pivot = (-p[0], p[1], p[2])
            uvs = box_uv(c['size'], c.get('mirror', False))
            for face, corners in face_corners(lo, hi).items():
                pts = [tuple(a + b for a, b in zip(apply(m, tuple(q - p for q, p in zip(pt, pivot))), pivot))
                       for pt in corners]
                out.append((pts, [(c['uv'][0] + u, c['uv'][1] + v) for u, v in
                                  ((uvs[face][0], uvs[face][1]), (uvs[face][2], uvs[face][1]),
                                   (uvs[face][2], uvs[face][3]), (uvs[face][0], uvs[face][3]))]))
    return out


def render(tool):
    view = mul(rot_z(math.radians(ROLL)), mul(rot_x(math.radians(TILT)), rot_y(math.radians(SPIN))))
    faces = []
    for pts, uv in quads(tool):
        p = [apply(view, q) for q in pts]
        e1 = [p[1][i] - p[0][i] for i in range(3)]
        e2 = [p[3][i] - p[0][i] for i in range(3)]
        # Outward normal: the corners run clockwise seen from outside, so it's e2 x e1 (e1 x e2 points inwards:
        # the first version used it, culled every face turned to the camera and drew the tools' insides).
        n = (e2[1] * e1[2] - e2[2] * e1[1], e2[2] * e1[0] - e2[0] * e1[2], e2[0] * e1[1] - e2[1] * e1[0])
        ln = math.sqrt(sum(a * a for a in n)) or 1
        n = tuple(a / ln for a in n)
        # No culling: the depth buffer hides the far faces, and like GeckoLib (both faces drawn) the inside still
        # shows through transparent texels. A face seen from behind is lit like its front.
        if n[2] < 0:
            n = tuple(-a for a in n)
        ll = math.sqrt(sum(a * a for a in LIGHT))
        shade = 0.55 + 0.45 * max(0.0, sum(a * b / ll for a, b in zip(n, LIGHT)))
        faces.append((p, uv, shade))
    xs = [q[0] for f in faces for q in f[0]]
    ys = [q[1] for f in faces for q in f[0]]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    big = SIZE * SS
    scale = (SIZE - 2 * MARGIN) * SS / span
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2

    def screen(q):
        return (big / 2 + (q[0] - cx) * scale, big / 2 - (q[1] - cy) * scale, q[2])

    color = [None] * (big * big)
    depth = [-1e9] * (big * big)
    tex = TEXTURE.load()
    tw, th = TEXTURE.size
    for p, uv, shade in faces:
        s = [screen(q) for q in p]
        for tri in ((0, 1, 2), (0, 2, 3)):
            (ax, ay, az), (bx, by, bz), (qx, qy, qz) = (s[i] for i in tri)
            ta, tb, tc = (uv[i] for i in tri)
            den = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
            if abs(den) < 1e-9:
                continue
            for py in range(max(0, int(min(ay, by, qy))), min(big, int(max(ay, by, qy)) + 1)):
                for px in range(max(0, int(min(ax, bx, qx))), min(big, int(max(ax, bx, qx)) + 1)):
                    x, y = px + 0.5, py + 0.5
                    l1 = ((by - qy) * (x - qx) + (qx - bx) * (y - qy)) / den
                    l2 = ((qy - ay) * (x - qx) + (ax - qx) * (y - qy)) / den
                    l3 = 1 - l1 - l2
                    if min(l1, l2, l3) < -1e-6:
                        continue
                    z = l1 * az + l2 * bz + l3 * qz
                    i = py * big + px
                    if z <= depth[i]:
                        continue
                    u = l1 * ta[0] + l2 * tb[0] + l3 * tc[0]
                    v = l1 * ta[1] + l2 * tb[1] + l3 * tc[1]
                    r, g, b, a = tex[min(tw - 1, max(0, int(u))), min(th - 1, max(0, int(v)))]
                    if a < 128:  # cut-out texture: transparent pixels are holes
                        continue
                    depth[i] = z
                    color[i] = (round(r * shade), round(g * shade), round(b * shade), 255)
    img = Image.new('RGBA', (big, big))
    img.putdata([c or (0, 0, 0, 0) for c in color])
    # Shrink by averaging (premultiplied, so the transparent background doesn't darken the edges).
    small = img.resize((SIZE, SIZE), Image.BOX)
    px = small.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255 if a >= 100 else 0)
    alpha = small.getchannel('A')
    ring = alpha.filter(ImageFilter.MaxFilter(3))
    out = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    outline = Image.new('RGBA', (SIZE, SIZE), (24, 22, 26, 255))
    out.paste(outline, mask=ring)
    out.paste(small, mask=alpha)
    return out


if __name__ == '__main__':
    for tool in TOOLS:
        path = ASSETS / f'textures/item/{tool}.png'
        render(tool).save(path)
        print('wrote', path.relative_to(ROOT))
