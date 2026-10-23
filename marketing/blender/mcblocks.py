"""Minecraft blocks and plants for key-art backdrops, textured with the game's own textures.

The textures come straight out of the Minecraft client jar in the Gradle cache (they are Mojang's, so they are not
copied into the repo): `texture('sand')` extracts assets/minecraft/textures/block/sand.png into marketing/.cache/mc and
keeps only the first frame of animated ones (prismarine, sea lanterns, kelp... are vertical strips of frames).

Units: 1 Blender unit = 1 block, z up. Block (x, y, z) fills the cube [x, x+1] x [y, y+1] x [z, z+1].
"""
import glob
import os
import zipfile

import bpy

from mclib import MARKETING, pixel_material

CACHE = os.path.join(MARKETING, '.cache', 'mc')


def _jar():
    jars = sorted(glob.glob(os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar')))
    if not jars:
        raise RuntimeError('no Minecraft client jar in the Gradle cache: run ./gradlew build once')
    return jars[-1]


def texture(name, kind='block'):
    """The first 16x16 (square) frame of a vanilla texture as a Blender image, e.g. texture('sea_lantern')."""
    key = f'mc_{kind}_{name}'
    if key in bpy.data.images:
        return bpy.data.images[key]
    path = os.path.join(CACHE, kind, f'{name}.png')
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with zipfile.ZipFile(_jar()) as z:
            with open(path, 'wb') as f:
                f.write(z.read(f'assets/minecraft/textures/{kind}/{name}.png'))
    src = bpy.data.images.load(path, check_existing=True)
    w, h = src.size
    if h <= w:  # square, or a wide entity texture (slime.png is 64x32): not an animation strip
        src.name = key
        return src
    # Animated strip: frame 0 is at the top of the PNG, which is the END of Blender's bottom-up pixel array.
    px = list(src.pixels)
    frame = px[(h - w) * w * 4:]
    img = bpy.data.images.new(key, w, w, alpha=True)
    img.pixels = frame
    img.pack()
    bpy.data.images.remove(src)
    return img


_mats = {}


def material(name, emission=0.0, roughness=0.8, kind='block'):
    """A crisp-pixel material for a vanilla texture; emission > 0 makes it glow with its own colours (sea lanterns)."""
    key = (name, emission, kind)
    if key not in _mats:
        img = texture(name, kind)
        _mats[key] = pixel_material(f'mc_{name}', img, roughness=roughness, specular=0.3,
                                    emission_image=img if emission else None, emission_strength=emission)
    return _mats[key]


# Face corners (unit cube, seen from outside: top-left, top-right, bottom-right, bottom-left) and the neighbour that
# hides each face.
_FACES = {
    (0, -1, 0): [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
    (0, 1, 0): [(1, 1, 1), (0, 1, 1), (0, 1, 0), (1, 1, 0)],
    (1, 0, 0): [(1, 0, 1), (1, 1, 1), (1, 1, 0), (1, 0, 0)],
    (-1, 0, 0): [(0, 1, 1), (0, 0, 1), (0, 0, 0), (0, 1, 0)],
    (0, 0, 1): [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    (0, 0, -1): [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)],
}


def blocks(name, cells, materials):
    """One mesh for a set of blocks. cells: {(x, y, z): texture name}; materials: {texture name: material}.
    Only faces next to an empty cell are built (a solid wall is its surface, as in the game)."""
    verts, polys, uvs, mat_idx = [], [], [], []
    names = sorted(set(cells.values()))
    slot = {n: i for i, n in enumerate(names)}
    for (x, y, z), tex in cells.items():
        for (dx, dy, dz), corners in _FACES.items():
            if (x + dx, y + dy, z + dz) in cells:
                continue
            b = len(verts)
            tl, tr, br, bl = corners
            for c in (tl, bl, br, tr):
                verts.append((x + c[0], y + c[1], z + c[2]))
            polys.append((b, b + 1, b + 2, b + 3))
            uvs += [(0, 1), (0, 0), (1, 0), (1, 1)]
            mat_idx.append(slot[tex])
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name='UV')
    for i, uv in enumerate(uvs):
        layer.data[i].uv = uv
    for n in names:
        me.materials.append(materials[n])
    for p, m in zip(me.polygons, mat_idx):
        p.material_index = m
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def plants(name, items):
    """Crossed-quad plants (seagrass, kelp...). items: list of (x, y, z, texture name, height in blocks) with (x, y, z)
    the block the plant stands in; tall plants stack their texture once per block (kelp_plant below, kelp on top)."""
    verts, polys, uvs, mat_idx = [], [], [], []
    names = []
    for x, y, z, tex, *rest in items:
        hgt = rest[0] if rest else 1
        for k in range(hgt):
            t = tex if not isinstance(tex, tuple) else (tex[1] if k == hgt - 1 else tex[0])
            if t not in names:
                names.append(t)
            for (ax, ay, bx, by) in ((0.15, 0.15, 0.85, 0.85), (0.15, 0.85, 0.85, 0.15)):
                b = len(verts)
                z0, z1 = z + k, z + k + 1
                verts += [(x + ax, y + ay, z1), (x + ax, y + ay, z0), (x + bx, y + by, z0), (x + bx, y + by, z1)]
                polys.append((b, b + 1, b + 2, b + 3))
                uvs += [(0, 1), (0, 0), (1, 0), (1, 1)]
                mat_idx.append(names.index(t))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name='UV')
    for i, uv in enumerate(uvs):
        layer.data[i].uv = uv
    for n in names:
        me.materials.append(material(n, roughness=0.7))
    for p, m in zip(me.polygons, mat_idx):
        p.material_index = m
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def sprites(name, points, tex, kind='particle', size=0.125, facing=None, emission=0.0):
    """Flat square particles (bubbles...): one quad per point, `size` blocks wide, facing the `facing` point (the camera)."""
    from mathutils import Vector
    verts, polys, uvs = [], [], []
    for p in points:
        p = Vector(p)
        n = (Vector(facing) - p).normalized() if facing is not None else Vector((0, -1, 0))
        right = n.cross(Vector((0, 0, 1))).normalized() * (size / 2)
        up = right.cross(n).normalized() * (size / 2)
        b = len(verts)
        verts += [p - right + up, p - right - up, p + right - up, p + right + up]
        polys.append((b, b + 1, b + 2, b + 3))
        uvs += [(0, 1), (0, 0), (1, 0), (1, 1)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name='UV')
    for i, uv in enumerate(uvs):
        layer.data[i].uv = uv
    me.materials.append(material(tex, kind=kind, roughness=0.2, emission=emission))
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.visible_shadow = False
    return ob
