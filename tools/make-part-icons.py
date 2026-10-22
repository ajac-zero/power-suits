"""Draws the item icons of the suit's parts (textures/item/suit_<part>.png: torso, arm, leg, helmet) and of the suit
itself (textures/item/power_armor.png) from the model.

    uv run --with pillow python3 tools/make-part-icons.py [out_dir]

Why: the parts are crafting ingredients (craft each part, then assemble the suit), drawn as flat icons like the tools.
Unlike tools/make-item-icons.py this reads the Blockbench source (model/power_armor.bbmodel, its embedded texture), not
the exported geo: the icons then show the model as it is in Blockbench even while the export lags behind.

How: each part is a set of bones cut from the suit (the arm and the leg once: both sides are the same part). Its root
bone's rest rotation is dropped (an arm hangs straight instead of splayed), the bones below keep theirs (the elbow
stays bent); then tools/make-item-icons.py's software renderer draws it. Re-run it whenever the art changes.
"""
import base64
import importlib.util
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('icons', ROOT / 'tools/make-item-icons.py')
icons = importlib.util.module_from_spec(spec)
spec.loader.exec_module(icons)

# icon: (root bones, bones left out with everything below them, view)
# view = (spin about Y, tilt about X, roll about Z) in degrees; the model faces -Z, so spin 180 shows its front.
STYLES = ('helm_protector', 'helm_spartan', 'helm_noble', 'helm_robot')  # the helmet styles' bones
PARTS = {
    'torso': (['body', 'hips'], {'head', 'right_arm', 'left_arm', 'pack_tidal', 'pack_reactor'}, (205, 12, 0)),  # with its firebox
    'arm': (['right_arm'], set(), (210, 10, 0)),
    'leg': (['right_leg'], set(), (210, 10, 0)),
    'helmet': (['head'], {'pilot_head', *STYLES}, (205, 12, 0)),  # the standard helmet: not the pilot, not the styles
    # the helmet styles (design sec. 4c): each a whole helmet of its own under the head bone (suit_helmet_<style>.png)
    **{b.replace('helm_', 'helmet_'): ([b], set(), (205, 12, 0)) for b in STYLES},
    # the whole suit (the Power Armor item): no pilot, empty socket (the tools come separately)
    'power_armor': (['marine'], {'pilot_head', 'chaingun', 'em_projector', 'drill', 'chainsaw', 'pack_tidal', 'pack_reactor', *STYLES}, (205, 10, 0)),
    # the engines (design sec. 4b): each one's backpack, seen from behind
    'firebox': (['backpack_parts'], set(), (25, 12, 0)),
    'tidal_engine': (['pack_tidal'], set(), (25, 12, 0)),
    'star_reactor': (['pack_reactor'], set(), (25, 12, 0)),
}
# drawn running: the glowmasks (tools/make-glowmasks.py) added on top, as SuitGlowLayer does in game
LIT = {'firebox', 'tidal_engine', 'star_reactor'}
FILES = {'power_armor': 'power_armor', 'firebox': 'firebox', 'tidal_engine': 'tidal_engine', 'star_reactor': 'star_reactor'}  # icon -> texture name (the rest: suit_<part>)


def euler(r):
    """Blockbench's rotation (degrees) as a matrix: Z * Y * X."""
    x, y, z = (math.radians(a) for a in r)
    return icons.mul(icons.rot_z(z), icons.mul(icons.rot_y(y), icons.rot_x(x)))


def turn(p, m, o):
    return tuple(a + b for a, b in zip(icons.apply(m, tuple(q - c for q, c in zip(p, o))), o))


def load():
    m = json.loads((ROOT / 'model/power_armor.bbmodel').read_text())
    tex = Image.open(io.BytesIO(base64.b64decode(m['textures'][0]['source'].split(',', 1)[1]))).convert('RGBA')
    res = (m['resolution']['width'], m['resolution']['height'])
    if tex.size != res:  # UVs are in the project's resolution
        tex = tex.resize(res, Image.NEAREST)
    groups = {g['uuid']: g for g in m['groups']}
    els = {e['uuid']: e for e in m['elements']}
    return m['outliner'], groups, els, tex


def cubes_of(outliner, groups, els, roots, skip):
    """[(cube, [group chain from the part's root down to the cube's bone])] for a part."""
    out = []

    def walk(node, chain, inside):
        g = groups[node['uuid']]
        if g['name'] in skip or g['name'].startswith('proto_'):  # design drafts (hidden sketches) aren't part of it
            return
        inside = inside or g['name'] in roots
        chain = chain + [g] if inside else chain
        for k in node.get('children', []):
            if isinstance(k, str):
                if inside and els[k].get('export', True):
                    out.append((els[k], chain))
            else:
                walk(k, chain, inside)

    for n in outliner:
        walk(n, [], False)
    return out


def quads(cubes):
    out = []
    for c, chain in cubes:
        g = c.get('inflate', 0)
        lo = tuple(a - g for a in c['from'])
        hi = tuple(a + g for a in c['to'])
        if c.get('box_uv', True):
            size = [math.floor(b - a + 1e-6) for a, b in zip(c['from'], c['to'])]  # box UV floors the net
            u0, v0 = c.get('uv_offset', [0, 0])
            uvs = {f: (u0 + a, v0 + b, u0 + cc, v0 + d) for f, (a, b, cc, d) in icons.box_uv(size, c.get('mirror_uv', False)).items()}
        else:
            uvs = {f: tuple(face['uv']) for f, face in c['faces'].items() if face.get('texture') is not None}
        cube_rot = euler(c.get('rotation', [0, 0, 0]))
        for face, corners in icons.face_corners(lo, hi).items():
            if face not in uvs:
                continue
            pts = [turn(p, cube_rot, c['origin']) for p in corners]
            for depth, grp in enumerate(reversed(chain)):  # innermost bone first
                if depth == len(chain) - 1:
                    break  # the part's root: its rest rotation is dropped
                pts = [turn(p, euler(grp.get('rotation', [0, 0, 0])), grp['origin']) for p in pts]
            u1, v1, u2, v2 = uvs[face]
            out.append((pts, [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]))
    return out


def main():
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else icons.ASSETS / 'textures/item'
    outliner, groups, els, tex = load()
    lit = tex.copy()
    for mask in sorted((ROOT / 'src/main/resources/assets/powerarmor/textures/entity').glob('power_armor_glow_*.png')):
        m = Image.open(mask).convert('RGBA').resize(tex.size, Image.NEAREST)
        px, mp = lit.load(), m.load()
        for y in range(tex.height):
            for x in range(tex.width):
                if mp[x, y][3]:
                    r, g, b, a = px[x, y]
                    px[x, y] = (min(255, r + mp[x, y][0]), min(255, g + mp[x, y][1]), min(255, b + mp[x, y][2]), a)
    for part, (roots, skip, (spin, tilt, roll)) in PARTS.items():
        cubes = cubes_of(outliner, groups, els, set(roots), skip)
        view = icons.mul(icons.rot_z(math.radians(roll)), icons.mul(icons.rot_x(math.radians(tilt)), icons.rot_y(math.radians(spin))))
        img = icons.render_quads(quads(cubes), view, lit if part in LIT else tex)
        path = out_dir / f"{FILES.get(part, 'suit_' + part)}.png"
        img.save(path)
        print('wrote', path, len(cubes), 'cubes')


if __name__ == '__main__':
    main()
