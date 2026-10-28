"""Helpers for the key-art scenes: the suit (glTF from Blockbench), Minecraft players built from a skin, capes, lights.

Units: 1 Blender unit = 1 block = 16 model pixels (the glb is exported with Blockbench's scale 16).
Characters face +Y in their own frame, z up, so their right hand is on +X. Rotations for posing are degrees about the
character's own axes: +X swings a limb forward (arms/legs hang along -Z), -X bends a knee back, +Y/-Y swings an arm out
to the left/right side.
"""
import math
import os

import bpy
from mathutils import Euler, Matrix, Quaternion, Vector

PX = 1 / 16
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
MARKETING = os.path.join(ROOT, 'marketing')
TEXTURES = os.path.join(ROOT, 'src/main/resources/assets/power_suits/textures/entity')

# Bones the game shows only for one installed part: everything here is deleted unless a scene asks for it.
TOOL_BONES = ['chaingun', 'em_projector', 'drill', 'chainsaw']
HELMET_BONES = ['helmet', 'helm_protector', 'helm_spartan', 'helm_noble', 'helm_robot', 'helm_hunter']
ENGINE_BONES = ['backpack_parts', 'pack_tidal', 'pack_reactor']
# The visor's colours (PowerArmorEntity): amber with a pilot, green for a friendly mind, red for a rogue.
VISOR_AMBER, VISOR_GREEN, VISOR_RED = (1.0, 0.68, 0.22), (0.35, 1.0, 0.45), (1.0, 0.12, 0.08)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def rad(v):
    return [math.radians(a) for a in v]


# ---------------------------------------------------------------- materials

def pixel_material(name, image, roughness=0.55, specular=0.35, metallic=0.0, emission_image=None, emission_strength=0.0):
    """Principled material with nearest-neighbour texture sampling (crisp Minecraft pixels) and alpha from the PNG."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = image
    tex.interpolation = 'Closest'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    nt.links.new(tex.outputs['Alpha'], bsdf.inputs['Alpha'])
    bsdf.inputs['Roughness'].default_value = roughness
    bsdf.inputs['Specular IOR Level'].default_value = specular
    bsdf.inputs['Metallic'].default_value = metallic
    if emission_image is not None:
        em = nt.nodes.new('ShaderNodeTexImage')
        em.image = emission_image
        em.interpolation = 'Closest'
        nt.links.new(em.outputs['Color'], bsdf.inputs['Emission Color'])
        bsdf.inputs['Emission Strength'].default_value = emission_strength
    if hasattr(mat, 'surface_render_method'):
        mat.surface_render_method = 'DITHERED'
    return mat


def load_image(path):
    img = bpy.data.images.load(path, check_existing=True)
    return img


# ---------------------------------------------------------------- the suit

def import_suit(name='suit', paint=None, tool='chaingun', helmet=None, engine='backpack_parts', glow=None, glow_strength=6.0):
    """Imports marketing/assets/power_suit.glb under an empty called `name` and keeps only the installed parts.

    paint: dye name ('lime', 'red'...) or None for bare steel. tool / helmet / engine: the bone to keep of each set
    (None: none; with no helmet the pilot's head goes on `pilot_head`, see player_head()).
    Returns (root empty, {bone name: object}).
    """
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.join(MARKETING, 'assets', 'power_suit.glb'))
    new = [o for o in bpy.data.objects if o not in before]
    for o in new:
        o.animation_data_clear()  # rest pose (the importer may leave an animation assigned)
    bones = {o.name: o for o in new}
    root = bpy.data.objects.new(name, None)
    bpy.context.scene.collection.objects.link(root)
    for o in new:
        if o.parent is None:
            o.parent = root

    keep = {tool, helmet, engine}
    doomed = [b for b in TOOL_BONES + HELMET_BONES + ENGINE_BONES if b not in keep]
    if tool is not None:
        doomed.append('socket')
    for b in doomed:
        if b in bones:
            delete_tree(bones[b])
    bones = {o.name: o for o in bpy.data.objects if o.name in bones}

    # Texture: the glb embeds the bare steel texture; a dye swaps it for the painted one, as the game does.
    tex_path = os.path.join(TEXTURES, 'power_suit.png') if paint is None else os.path.join(TEXTURES, 'paint', f'power_suit_{paint}.png')
    img = load_image(tex_path)
    # glow: a glowmask texture name (e.g. 'reactor' for power_suit_glow_reactor.png), drawn as emission the way the
    # game's SuitGlowLayer adds it at full brightness. Masks are black/transparent except on the lit pixels.
    # Several masks (a list: e.g. [('visor', VISOR_AMBER), 'tidal']) are added together into one image, as the game
    # draws one additive layer each, every mask multiplied by its tint (SuitGlowLayer: the visor's grey mask takes the
    # suit's visor colour; the other masks are already coloured and use white).
    em = None
    for g in ([glow] if isinstance(glow, str) else glow or []):
        g, tint = (g, (1, 1, 1)) if isinstance(g, str) else g
        m = load_image(os.path.join(TEXTURES, f'power_suit_glow_{g}.png'))
        px = [v * tint[i % 4] if i % 4 < 3 else v for i, v in enumerate(m.pixels)]
        if em is None:
            em = m.copy()
            em.name = f'{name}_glow'
            acc = px
        else:
            acc = [min(1.0, a + b) for a, b in zip(acc, px)]
    if em is not None:
        em.pixels = acc
        em.pack()
    mat = pixel_material(f'{name}_armor', img, roughness=0.42, specular=0.5, emission_image=em, emission_strength=glow_strength)
    for o in bones.values():
        if o.type == 'MESH':
            o.data.materials.clear()
            o.data.materials.append(mat)
    for o in bones.values():
        o.rotation_mode = 'QUATERNION'
        o['rest_q'] = list(o.rotation_quaternion)
    return root, bones


def delete_tree(obj):
    for c in list(obj.children):
        delete_tree(c)
    bpy.data.objects.remove(obj, do_unlink=True)


def pose(obj, deg):
    """Rotates a bone by (x, y, z) degrees in its parent's frame, on top of its rest rotation."""
    rest = Quaternion(obj['rest_q'])
    off = Euler(rad(deg), 'XYZ').to_quaternion()
    obj.rotation_quaternion = off @ rest


# ---------------------------------------------------------------- players

def _box(name, mesh_origin_px, size_px, uv, tex_size, inflate=0.0, mat=None):
    """A box mesh. mesh_origin_px: min corner (x, y, z) in px in the parent's frame. uv: (u, v) of a Java box-UV net,
    or a dict face -> (u, v, w, h). Faces are mapped the way Minecraft maps skins (front = +Y, right = +X)."""
    w, h, d = size_px  # x width, z height, y depth
    if not isinstance(uv, dict):
        u, v = uv
        uv = {
            'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
        }
    x0, y0, z0 = (c - inflate for c in mesh_origin_px)
    x1, y1, z1 = mesh_origin_px[0] + w + inflate, mesh_origin_px[1] + d + inflate, mesh_origin_px[2] + h + inflate
    # Corners seen from outside: top-left, top-right, bottom-right, bottom-left (texture orientation).
    faces = {
        'front': [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
        'back': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        'right': [(x1, y0, z1), (x1, y1, z1), (x1, y1, z0), (x1, y0, z0)],
        'left': [(x0, y1, z1), (x0, y0, z1), (x0, y0, z0), (x0, y1, z0)],
        'top': [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
        'bottom': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
    }
    verts, polys, uvs = [], [], []
    tw, th = tex_size
    for face, corners in faces.items():
        if face not in uv:
            continue
        fu, fv, fw, fh = uv[face]
        tl, tr, br, bl = corners
        base = len(verts)
        # counter-clockwise from outside: TL, BL, BR, TR
        for p in (tl, bl, br, tr):
            verts.append(Vector(p) * PX)
        polys.append((base, base + 1, base + 2, base + 3))
        uvs += [(fu, fv), (fu, fv + fh), (fu + fw, fv + fh), (fu + fw, fv)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name='UV')
    for i, (pu, pv) in enumerate(uvs):
        layer.data[i].uv = (pu / tw, 1 - pv / th)
    if mat:
        me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def _empty(name, parent, loc_px):
    e = bpy.data.objects.new(name, None)
    e.empty_display_size = 0.1
    bpy.context.scene.collection.objects.link(e)
    e.parent = parent
    e.location = Vector(loc_px) * PX
    e.rotation_mode = 'XYZ'
    return e


def player(name, skin_path, slim=False, cape=None):
    """A Minecraft player (Java skin layout with outer layers). Parts are empties at vanilla's joints:
    root (hips, between the legs at y 12) > body > head / right_arm / left_arm / cape; root > right_leg / left_leg.
    Returns dict of part empties. cape: a material for a segmented flowing cape (see cape_material)."""
    img = load_image(skin_path)
    mat = pixel_material(f'{name}_skin', img, roughness=0.6, specular=0.3)
    T = (64, 64)
    aw = 3 if slim else 4
    parts = {}
    root = _empty(name, None, (0, 0, 12))
    parts['root'] = root
    body = _empty(f'{name}_body', root, (0, 0, 0))
    parts['body'] = body
    _box(f'{name}_torso', (-4, -2, 0), (8, 12, 4), (16, 16), T, mat=mat).parent = body
    _box(f'{name}_jacket', (-4, -2, 0), (8, 12, 4), (16, 32), T, 0.25, mat).parent = body
    head = _empty(f'{name}_head', body, (0, 0, 12))
    parts['head'] = head
    _box(f'{name}_skull', (-4, -4, 0), (8, 8, 8), (0, 0), T, mat=mat).parent = head
    _box(f'{name}_hat', (-4, -4, 0), (8, 8, 8), (32, 0), T, 0.5, mat).parent = head
    for side, sx, base, over in (('right', 1, (40, 16), (40, 32)), ('left', -1, (32, 48), (48, 48))):
        arm = _empty(f'{name}_{side}_arm', body, (5 * sx, 0, 10))
        parts[f'{side}_arm'] = arm
        x0 = -1 if sx > 0 else -(aw - 1)  # vanilla: the arm pivots 1 px from its inner edge
        _box(f'{name}_{side}_arm_m', (x0, -2, -12 + 2), (aw, 12, 4), base, T, mat=mat).parent = arm
        _box(f'{name}_{side}_sleeve', (x0, -2, -12 + 2), (aw, 12, 4), over, T, 0.25, mat).parent = arm
        parts[f'{side}_hand'] = _empty(f'{name}_{side}_hand', arm, (x0 + aw / 2, 0, -10))
    for side, sx, base, over in (('right', 1, (0, 16), (0, 32)), ('left', -1, (16, 48), (0, 48))):
        leg = _empty(f'{name}_{side}_leg', root, (2 * sx, 0, 0))
        parts[f'{side}_leg'] = leg
        _box(f'{name}_{side}_leg_m', (-2, -2, -12), (4, 12, 4), base, T, mat=mat).parent = leg
        _box(f'{name}_{side}_pants', (-2, -2, -12), (4, 12, 4), over, T, 0.25, mat).parent = leg
        parts[f'{side}_foot'] = _empty(f'{name}_{side}_foot', leg, (0, 0, -12))
    parts['face'] = _empty(f'{name}_face', head, (0, 8, 4))  # a point in front of the eyes, to aim the head with
    parts['neck'] = _empty(f'{name}_neck', body, (0, 0, 12))
    if cape is not None:
        parts['cape'] = cape_segments(name, body, cape)
    return parts


def player_head(name, skin_path, parent):
    """Just the head + hat layer, bottom centre on `parent` (the suit's pilot_head bone), face to the parent's +Y."""
    img = load_image(skin_path)
    mat = pixel_material(f'{name}_skin', img, roughness=0.6, specular=0.3)
    head = _empty(f'{name}_head', parent, (0, 0, 0))
    _box(f'{name}_skull', (-4, -4, 0), (8, 8, 8), (0, 0), (64, 64), mat=mat).parent = head
    _box(f'{name}_hat', (-4, -4, 0), (8, 8, 8), (32, 0), (64, 64), 0.5, mat).parent = head
    return head


def cape_material(name, top=(0.62, 0.05, 0.05), bottom=(0.38, 0.02, 0.03)):
    """A 64x32 cape texture (vanilla cape net: box 10x16x1 at 0,0) painted procedurally: a red ramp with darker
    edge columns and a few 1 px folds, so it reads as a Minecraft cape rather than flat colour."""
    W, H = 64, 32
    img = bpy.data.images.new(f'{name}_cape', W, H, alpha=True)
    px = [0.0] * (W * H * 4)
    for y in range(H):
        for x in range(W):
            if x >= 22 or y >= 17:
                continue
            t = min(1, max(0, (y - 1) / 16))
            c = [top[i] * (1 - t) + bottom[i] * t for i in range(3)]
            fx = (x - 1) % 11 if x < 12 else (x - 12) % 11
            if fx in (0, 9):
                c = [v * 0.78 for v in c]
            if fx in (3, 6) and y > 4:
                c = [v * 0.88 for v in c]
            if fx in (4, 7) and y > 6:
                c = [min(1, v * 1.12) for v in c]
            row = H - 1 - y  # Blender images start at the bottom
            i = (row * W + x) * 4
            px[i:i + 4] = [c[0], c[1], c[2], 1.0]
    img.pixels = px
    img.pack()
    return pixel_material(f'{name}_cape', img, roughness=0.75, specular=0.2)


def cape_segments(name, body, mat, n=4):
    """The cape as n hinged strips (10 px wide, 16/n tall) hanging from the top of the back: posing each hinge
    (rotate_cape) bends it like cloth while keeping Minecraft's boxy look."""
    seg_h = 16 // n
    hinges = []
    parent = _empty(f'{name}_cape', body, (0, -2.6, 12))  # top back edge of the torso, just behind the jacket
    hinges.append(parent)
    for k in range(n):
        h = parent if k == 0 else _empty(f'{name}_cape_{k}', hinges[-1], (0, 0, -seg_h))
        if k:
            hinges.append(h)
        v = 1 + k * seg_h
        uv = {
            'back': (1, v, 10, seg_h),        # outer side (seen from behind the player)
            'front': (12, v, 10, seg_h),      # side against the back
            'right': (0, v, 1, seg_h), 'left': (11, v, 1, seg_h),
        }
        if k == 0:
            uv['top'] = (1, 0, 10, 1)
        if k == n - 1:
            uv['bottom'] = (11, 0, 10, 1)
        _box(f'{name}_cape_seg{k}', (-5, -1, -seg_h), (10, seg_h, 1), uv, (64, 32), mat=mat).parent = h
    return hinges


def pose_part(obj, deg):
    obj.rotation_mode = 'XYZ'
    obj.rotation_euler = rad(deg)


# ---------------------------------------------------------------- aiming (pose by world targets)

def update():
    bpy.context.view_layer.update()


def aim(bone, tip, target, twist=0.0):
    """Turns `bone` about its pivot (shortest arc) so that `tip` (a descendant: the next joint, a hand, a muzzle)
    lies on the line from the pivot to `target` (world). twist: degrees about that line afterwards."""
    update()
    p = bone.matrix_world.translation.copy()
    t = tip.matrix_world.translation.copy()
    want = Vector(target) - p
    q = (t - p).rotation_difference(want)
    if twist:
        q = Quaternion(want.normalized(), math.radians(twist)) @ q
    bone.matrix_world = Matrix.Translation(p) @ q.to_matrix().to_4x4() @ Matrix.Translation(-p) @ bone.matrix_world
    update()


def marker(name, parent, loc_px):
    """An empty at loc_px in parent's frame, to aim with (e.g. a fist's knuckles)."""
    return _empty(name, parent, loc_px)


def world(obj):
    update()
    return obj.matrix_world.translation.copy()


# ---------------------------------------------------------------- render setup

def cycles(samples=128, res=(1600, 2000), transparent=False, preview=False, cpu=False):
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    prefs = bpy.context.preferences.addons['cycles'].preferences
    if cpu:
        sc.cycles.device = 'CPU'
    else:
        try:
            prefs.compute_device_type = 'METAL'
            # Blender 5.2's background compile of optimised Metal kernels crashes now and then (a double free in
            # ccl::path_cache_get, 2026-10-22): render with the generic kernels instead.
            if hasattr(prefs, 'kernel_optimization_level'):
                prefs.kernel_optimization_level = 'OFF'
            prefs.get_devices()
            for d in prefs.devices:
                d.use = True
            sc.cycles.device = 'GPU'
        except Exception:
            sc.cycles.device = 'CPU'
    sc.cycles.samples = 24 if preview else samples
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 35 if preview else 100
    sc.render.film_transparent = transparent
    sc.view_settings.view_transform = 'AgX'
    try:
        sc.view_settings.look = 'AgX - Medium High Contrast'
    except TypeError:
        pass
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA' if transparent else 'RGB'


def camera(loc, target, lens=50):
    cam = bpy.data.objects.new('camera', bpy.data.cameras.new('camera'))
    bpy.context.scene.collection.objects.link(cam)
    cam.location = loc
    look_at(cam, target)
    cam.data.lens = lens
    bpy.context.scene.camera = cam
    return cam


def look_at(obj, target):
    d = Vector(target) - obj.location
    obj.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()


def light(kind, name, loc, target, energy, color=(1, 1, 1), size=1.0, angle=None):
    data = bpy.data.lights.new(name, kind)
    data.energy = energy
    data.color = color
    if kind == 'AREA':
        data.size = size
    if kind == 'SUN' and angle is not None:
        data.angle = math.radians(angle)
    ob = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = loc
    look_at(ob, target)
    return ob


def sky_world(zenith=(0.16, 0.36, 0.85), horizon=(0.75, 0.86, 1.0), strength=1.0, camera_strength=1.0, nadir=None,
              rotation=None):
    """A vertical gradient world: lights the scene softly from all around and shows as the backdrop.
    rotation: a Matrix taking a real view direction into the tilted world's (see env_tilt), or None."""
    w = bpy.data.worlds.new('sky')
    bpy.context.scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    bg = nt.nodes['Background']
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    view = tc.outputs['Generated']
    if rotation is not None:
        rot = nt.nodes.new('ShaderNodeVectorRotate')
        rot.rotation_type = 'EULER_XYZ'
        rot.inputs['Rotation'].default_value = rotation.to_euler('XYZ')
        nt.links.new(view, rot.inputs['Vector'])
        view = rot.outputs['Vector']
    nt.links.new(view, sep.inputs['Vector'])
    nt.links.new(sep.outputs['Z'], ramp.inputs['Fac'])
    # Generated coordinates of a world are the view direction: Z is the sine of the elevation (-1..1).
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (*horizon, 1)
    ramp.color_ramp.elements[1].position = 0.38
    ramp.color_ramp.elements[1].color = (*zenith, 1)
    if nadir is not None:  # below the horizon: the haze over the world far below (seen when looking down)
        # The ramp only covers 0..1, so feed it Z remapped from -1..1: horizon at 0.5.
        rng = nt.nodes.new('ShaderNodeMapRange')
        rng.inputs['From Min'].default_value = -1
        rng.inputs['From Max'].default_value = 1
        nt.links.new(sep.outputs['Z'], rng.inputs['Value'])
        nt.links.new(rng.outputs['Result'], ramp.inputs['Fac'])
        ramp.color_ramp.elements[0].position = 0.5
        ramp.color_ramp.elements[1].position = 0.69
        low = ramp.color_ramp.elements.new(0.36)
        low.color = (*nadir, 1)
    nt.links.new(ramp.outputs['Color'], bg.inputs['Color'])
    bg.inputs['Strength'].default_value = strength
    # The camera sees the sky at full brightness; `strength` only sets how much it lights the scene.
    seen = nt.nodes.new('ShaderNodeBackground')
    nt.links.new(ramp.outputs['Color'], seen.inputs['Color'])
    seen.inputs['Strength'].default_value = camera_strength
    path = nt.nodes.new('ShaderNodeLightPath')
    mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(path.outputs['Is Camera Ray'], mix.inputs['Fac'])
    nt.links.new(bg.outputs['Background'], mix.inputs[1])
    nt.links.new(seen.outputs['Background'], mix.inputs[2])
    nt.links.new(mix.outputs['Shader'], nt.nodes['World Output'].inputs['Surface'])
    return w


def clouds(name, z, center=(0, 0), extent=260, cell=12, thickness=4, density=0.45, seed=7, alpha=0.85, clear=None):
    """A layer of Minecraft-style clouds: cells of cell x cell blocks, `thickness` tall, picked by smoothed noise so
    they clump into the familiar blocky shapes."""
    import random
    rnd = random.Random(seed)
    n = int(extent / cell)
    raw = [[rnd.random() for _ in range(n)] for _ in range(n)]
    verts, faces = [], []
    for i in range(n):
        for j in range(n):
            acc = 0
            for di in (-1, 0, 1):
                for dj in (-1, 0, 1):
                    acc += raw[(i + di) % n][(j + dj) % n] * (2 if di == dj == 0 else 1)
            if acc / 10 < 1 - density:
                continue
            x0 = center[0] - extent / 2 + i * cell
            y0 = center[1] - extent / 2 + j * cell
            if clear is not None:  # (direction, degrees): keep a hole in the sky there (e.g. around the sun)
                mid = Vector((x0 + cell / 2, y0 + cell / 2, z + thickness / 2))
                if mid.angle(Vector(clear[0])) < math.radians(clear[1]):
                    continue
            b = len(verts)
            for dz in (0, thickness):
                verts += [(x0, y0, z + dz), (x0 + cell, y0, z + dz), (x0 + cell, y0 + cell, z + dz), (x0, y0 + cell, z + dz)]
            faces += [(b, b + 3, b + 2, b + 1), (b + 4, b + 5, b + 6, b + 7), (b, b + 1, b + 5, b + 4),
                      (b + 1, b + 2, b + 6, b + 5), (b + 2, b + 3, b + 7, b + 6), (b + 3, b, b + 4, b + 7)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (1, 1, 1, 1)
    bsdf.inputs['Roughness'].default_value = 1.0
    bsdf.inputs['Alpha'].default_value = alpha
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.visible_shadow = False
    return ob


def square_sun(direction, distance=400, size=40, strength=12.0, color=(1.0, 0.92, 0.65)):
    """Minecraft's square sun: an emissive square far away along `direction`, facing the origin."""
    d = Vector(direction).normalized()
    me = bpy.data.meshes.new('sun_disc')
    h = size / 2
    me.from_pydata([(-h, -h, 0), (h, -h, 0), (h, h, 0), (-h, h, 0)], [], [(0, 1, 2, 3)])
    mat = bpy.data.materials.new('sun_disc')
    mat.use_nodes = True
    nt = mat.node_tree
    for node in list(nt.nodes):
        if node.type != 'OUTPUT_MATERIAL':
            nt.nodes.remove(node)
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*color, 1)
    em.inputs['Strength'].default_value = strength
    nt.links.new(em.outputs['Emission'], nt.nodes['Material Output'].inputs['Surface'])
    me.materials.append(mat)
    ob = bpy.data.objects.new('sun_disc', me)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = d * distance
    ob.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    ob.visible_shadow = False
    ob.visible_diffuse = False
    return ob


def env_tilt(cam_loc, target, pitch=0.0, roll=0.0):
    """Tilt the world round the camera instead of moving the camera (the subjects' framing stays as it was): returns
    the matrix that places the backdrop (sky, sun, clouds, ground...) as a camera pitched up by `pitch` and rolled by
    `roll` degrees would see it. Parent backdrop objects to an empty with this matrix and give its rotation's inverse
    (the view direction into the tilted world) to sky_world / the haze."""
    rot = (Vector(target) - Vector(cam_loc)).to_track_quat('-Z', 'Y').to_matrix().to_4x4()
    c = Matrix.Translation(cam_loc) @ rot
    turned = c @ Matrix.Rotation(math.radians(pitch), 4, 'X') @ Matrix.Rotation(math.radians(roll), 4, 'Z')
    return c @ turned.inverted()


def depth_of_field(cam, focus, fstop=2.8):
    cam.data.dof.use_dof = True
    cam.data.dof.focus_distance = (Vector(focus) - cam.location).length
    cam.data.dof.aperture_fstop = fstop


def render(path):
    sc = bpy.context.scene
    sc.render.filepath = os.path.abspath(path)
    bpy.ops.render.render(write_still=True)


def debug_views(prefix, center, dist=9):
    """Low-res orthographic front (from -Y), side (from +X) and top views, to check a pose in 3D."""
    sc = bpy.context.scene
    keep = sc.camera
    sc.render.resolution_percentage = 100
    sc.render.resolution_x, sc.render.resolution_y = 500, 600
    sc.cycles.samples = 12
    c = Vector(center)
    for tag, off in (('front', (0, -dist, 0)), ('side', (dist, 0, 0)), ('back', (0, dist, 0)), ('top', (0, -0.01, dist))):
        cam = camera(c + Vector(off), c)
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = 4.2
        render(f'{prefix}_{tag}.png')
    sc.camera = keep


# ---------------------------------------------------------------- clipping check (from big_daddy.py)

def meshes(bone, skip=()):
    """The mesh objects of a bone and its children, leaving out the sub-trees named in skip."""
    out = []
    for o in [bone] + list(bone.children):
        if o.name in skip:
            continue
        if o is bone:
            if o.type == 'MESH':
                out.append(o)
        else:
            out += meshes(o, skip)
    return out


def bvh(objs, shrink=0.2 * PX):
    """(BVH of the objects' faces in world space, owner name per face), each cube shrunk by `shrink` on every side so
    faces that only touch (seams) don't count as clipping."""
    from mathutils.bvhtree import BVHTree
    update()
    verts, polys, owner = [], [], []
    for o in objs:
        owner += [o.name] * len(o.data.polygons)
        b = len(verts)
        c = sum((v.co for v in o.data.vertices), Vector()) / max(1, len(o.data.vertices))
        verts += [o.matrix_world @ Vector([x - shrink * ((x > m) - (x < m)) for x, m in zip(v.co, c)])
                  for v in o.data.vertices]
        polys += [[b + i for i in p.vertices] for p in o.data.polygons]
    return BVHTree.FromPolygons(verts, polys), owner


class ClipCheck:
    """Counts the face pairs of two part sets that overlap in the current pose but not at rest. Build it at rest
    (before posing), call report() after. a, b: functions returning mesh lists (the parts move with the pose)."""

    def __init__(self, label, a, b, shrink=0.2 * PX):
        self.label, self.a, self.b, self.shrink = label, a, b, shrink
        self.rest = self._pairs()[0]

    def _pairs(self):
        ta, oa = bvh(self.a(), self.shrink)
        tb, ob = bvh(self.b(), self.shrink)
        return set(ta.overlap(tb)), oa, ob

    def report(self):
        from collections import Counter
        pairs, oa, ob = self._pairs()
        new = pairs - self.rest
        print(f'clip: {len(new)} new face pairs, {self.label}',
              Counter((oa[i], ob[j]) for i, j in new).most_common(6))
        return len(new)


def suit_clip_checks(S):
    """The checks big_daddy.py runs, for any suit pose: upper body vs hips/thighs, hoods vs thighs, arms vs chest+helmet."""
    legs = lambda: [o for o in S['left_leg'].children if o.type == 'MESH'] + [o for o in S['right_leg'].children if o.type == 'MESH']  # noqa: E731
    return [
        ClipCheck('upper body vs hips/thighs', lambda: meshes(S['torso'], skip=('left_arm', 'right_arm', 'head')),
                  lambda: meshes(S['pelvis']) + meshes(S['hips']) + legs()),
        ClipCheck('hoods/pelvis vs thighs', lambda: [o for o in meshes(S['pelvis']) + meshes(S['hips'])
                                                     if not o.name.startswith('hip_axle')], legs),
        ClipCheck('arms vs chest/helmet (> 0.5 px)', lambda: meshes(S['left_arm']) + meshes(S['right_arm']),
                  lambda: meshes(S['torso'], skip=('left_arm', 'right_arm')), shrink=0.5 * PX),
    ]
