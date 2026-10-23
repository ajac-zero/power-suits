"""A Minecraft metropolis seen from the air, for key-art backdrops: a street grid of skyscrapers built from vanilla
blocks (glass curtain walls in white concrete frames, art-deco sandstone setback towers, stone-brick offices, dark
glass towers, brick low-rises), parks with trees, and a Daily Planet tower with its golden globe.

Built to be cheap: a facade is ONE quad per building face. Each building style's facade pattern (frame columns, floor
bands, windows) is composed once from the game's 16x16 block textures into a small image (one pattern period, e.g.
3 x 4 blocks) and tiled across the face by its UVs, so the city is a few thousand quads instead of millions of blocks.
The image's alpha channel is not transparency here: it marks the glass pixels, which get a glossy finish so the towers
pick up the sky. Distance haze is done in the materials (camera rays only), coloured like the sky in the same
direction, so the far city melts into the horizon.

Units: 1 Blender unit = 1 block, z up. city(...) puts the street level at z = ground.
"""
import math
import random

import bpy
import numpy as np
from mathutils import Vector

import mcblocks as B

GRASS = (0x91 / 255, 0xBD / 255, 0x59 / 255)    # plains grass tint (sRGB), as the game colours grass_block_top
FOLIAGE = (0x77 / 255, 0xAB / 255, 0x2F / 255)  # plains foliage tint (oak leaves)


def _px(name, tint=None, over=None):
    """A vanilla block texture as a 16x16x4 float array (Blender's bottom-up rows), optionally tinted (grass, leaves)
    and composited over a solid colour (glass and leaves are see-through in the game; the city's are opaque)."""
    img = B.texture(name)
    w, h = img.size
    a = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(a)
    a = a.reshape(h, w, 4)[:16, :16].copy()
    if tint is not None:
        a[..., :3] *= np.array(tint, np.float32)
    if over is not None:
        al = a[..., 3:4]
        a[..., :3] = a[..., :3] * al + np.array(over, np.float32) * (1 - al)
        a[..., 3] = 1
    return a


# Tiles: name -> (texture, tint, colour under it, glass?)
TILES = {
    'W': ('white_concrete', None, None, False),
    'Q': ('quartz_block_side', None, None, False),
    'S': ('sandstone_top', None, None, False),   # smooth sandstone's texture
    'C': ('cut_sandstone', None, None, False),
    'B': ('stone_bricks', None, None, False),
    'K': ('gray_concrete', None, None, False),
    'L': ('light_gray_concrete', None, None, False),
    'R': ('bricks', None, None, False),
    'A': ('polished_andesite', None, None, False),
    'T': ('smooth_stone', None, None, False),
    'g': ('light_blue_stained_glass', None, (0.10, 0.16, 0.24), True),   # blue curtain-wall glass
    'c': ('cyan_stained_glass', None, (0.06, 0.14, 0.16), True),
    'd': ('gray_stained_glass', None, (0.05, 0.06, 0.08), True),         # dark glass
    'w': ('glass', None, (0.12, 0.15, 0.20), True),                      # plain windows
    'y': ('yellow_stained_glass', None, (0.20, 0.15, 0.06), True),       # warm (art deco) windows
}

# Building styles: facade pattern rows from the BOTTOM up (row 0 sits on the street and repeats every len(rows)
# blocks), the roof block, and how tall they like to be. Column 0 is a frame pier, so a face n * width + 1 blocks wide
# ends on a pier at both corners.
STYLES = {
    'glass':  dict(rows=['WWW', 'Wgg', 'Wgg', 'Wgg'], roof='white_concrete', tall=1.0, share=2),
    'deco':   dict(rows=['CCCC', 'CSyS', 'CSyS'], roof='cut_sandstone', tall=0.9, share=3),
    'office': dict(rows=['BB', 'Bw', 'Bw'], roof='smooth_stone', tall=0.55, share=2),
    'dark':   dict(rows=['KKKK', 'Kddd', 'Kddd', 'Kddd'], roof='gray_concrete', tall=1.0, share=2),
    'quartz': dict(rows=['QQ', 'Qc'], roof='quartz_block_side', tall=0.8, share=1),
    'brick':  dict(rows=['RRR', 'RwR', 'RwR'], roof='smooth_stone', tall=0.3, share=3),
    'andes':  dict(rows=['AAA', 'Agg', 'Agg'], roof='polished_andesite', tall=0.7, share=1),
}


def _image(name, rows):
    """Compose a facade pattern (rows bottom-up) into one image; alpha = 1 on glass pixels."""
    ph, pw = len(rows), len(rows[0])
    arr = np.zeros((ph * 16, pw * 16, 4), np.float32)
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            tex, tint, over, glass = TILES[ch]
            t = _px(tex, tint, over if over is not None else (0, 0, 0))
            t[..., 3] = 1.0 if glass else 0.0
            arr[r * 16:(r + 1) * 16, c * 16:(c + 1) * 16] = t
    img = bpy.data.images.new(name, pw * 16, ph * 16, alpha=True)
    img.alpha_mode = 'CHANNEL_PACKED'  # alpha is the glass mask, not transparency: keep the colours as they are
    img.pixels.foreach_set(arr.ravel())
    img.pack()
    return img


def haze_group(zenith, horizon, nadir, distance, nadir_at=0.36, rotation=None):
    """A shader node group: mixes the surface toward the sky's colour in the view direction, by distance from the
    camera (1 - exp(-(d / distance)^2)), for camera rays only. The sky colours are sky_world's ramp (zenith above, horizon,
    nadir below), so the far city becomes the sky at the horizon."""
    g = bpy.data.node_groups.new('haze', 'ShaderNodeTree')
    g.interface.new_socket('Shader', in_out='INPUT', socket_type='NodeSocketShader')
    g.interface.new_socket('Shader', in_out='OUTPUT', socket_type='NodeSocketShader')
    n, l = g.nodes, g.links
    gi, go = n.new('NodeGroupInput'), n.new('NodeGroupOutput')
    path = n.new('ShaderNodeLightPath')
    # 1 - exp(-(d / distance)^2): the near city stays crisp, the far city dissolves into the sky.
    q = n.new('ShaderNodeMath'); q.operation = 'DIVIDE'; q.inputs[1].default_value = distance
    l.new(path.outputs['Ray Length'], q.inputs[0])
    k = n.new('ShaderNodeMath'); k.operation = 'MULTIPLY'
    l.new(q.outputs[0], k.inputs[0])
    l.new(q.outputs[0], k.inputs[1])
    neg = n.new('ShaderNodeMath'); neg.operation = 'MULTIPLY'; neg.inputs[1].default_value = -1.0
    l.new(k.outputs[0], neg.inputs[0])
    k = neg
    e = n.new('ShaderNodeMath'); e.operation = 'EXPONENT'
    l.new(k.outputs[0], e.inputs[0])
    f = n.new('ShaderNodeMath'); f.operation = 'SUBTRACT'; f.inputs[0].default_value = 1.0
    l.new(e.outputs[0], f.inputs[1])
    fc = n.new('ShaderNodeMath'); fc.operation = 'MULTIPLY'
    l.new(f.outputs[0], fc.inputs[0])
    l.new(path.outputs['Is Camera Ray'], fc.inputs[1])
    # The view direction is -Incoming; its z is the sine of the elevation, as in the world shader.
    geo = n.new('ShaderNodeNewGeometry')
    sep = n.new('ShaderNodeSeparateXYZ')
    incoming = geo.outputs['Incoming']
    if rotation is not None:  # a tilted world (mclib.env_tilt): look the colour up in its frame, as the sky does
        rot = n.new('ShaderNodeVectorRotate')
        rot.rotation_type = 'EULER_XYZ'
        rot.inputs['Rotation'].default_value = rotation.to_euler('XYZ')
        l.new(incoming, rot.inputs['Vector'])
        incoming = rot.outputs['Vector']
    l.new(incoming, sep.inputs[0])
    rng = n.new('ShaderNodeMapRange')
    rng.inputs['From Min'].default_value = 1   # -Incoming.z from -1..1 -> 0..1 (Incoming flips it)
    rng.inputs['From Max'].default_value = -1
    l.new(sep.outputs['Z'], rng.inputs['Value'])
    ramp = n.new('ShaderNodeValToRGB')
    el = ramp.color_ramp.elements
    el[0].position, el[0].color = 0.5, (*horizon, 1)
    el[1].position, el[1].color = 0.69, (*zenith, 1)
    low = el.new(nadir_at)  # (sin(elevation) + 1) / 2: 0.36 = 16 degrees below the horizon, as in sky_world
    low.color = (*nadir, 1)
    l.new(rng.outputs['Result'], ramp.inputs['Fac'])
    em = n.new('ShaderNodeEmission')
    l.new(ramp.outputs['Color'], em.inputs['Color'])
    mix = n.new('ShaderNodeMixShader')
    l.new(fc.outputs[0], mix.inputs['Fac'])
    l.new(gi.outputs[0], mix.inputs[1])
    l.new(em.outputs[0], mix.inputs[2])
    l.new(mix.outputs[0], go.inputs[0])
    return g


def _material(name, img, haze, glossy=True, emission=None):
    """Crisp-pixel material; where the image's alpha (glass mask) is 1 it turns smooth and shiny."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    if glossy:
        rough = nt.nodes.new('ShaderNodeMapRange')  # mask 0 -> 0.85 (stone), 1 -> 0.08 (glass)
        rough.inputs['To Min'].default_value = 0.85
        rough.inputs['To Max'].default_value = 0.08
        nt.links.new(tex.outputs['Alpha'], rough.inputs['Value'])
        nt.links.new(rough.outputs['Result'], bsdf.inputs['Roughness'])
        spec = nt.nodes.new('ShaderNodeMapRange')
        spec.inputs['To Min'].default_value = 0.3
        spec.inputs['To Max'].default_value = 1.0
        nt.links.new(tex.outputs['Alpha'], spec.inputs['Value'])
        nt.links.new(spec.outputs['Result'], bsdf.inputs['Specular IOR Level'])
    else:
        bsdf.inputs['Roughness'].default_value = 0.85
        bsdf.inputs['Specular IOR Level'].default_value = 0.3
    if emission:
        nt.links.new(tex.outputs['Color'], bsdf.inputs['Emission Color'])
        bsdf.inputs['Emission Strength'].default_value = emission
    out = nt.nodes['Material Output']
    hz = nt.nodes.new('ShaderNodeGroup')
    hz.node_tree = haze
    nt.links.new(bsdf.outputs[0], hz.inputs[0])
    nt.links.new(hz.outputs[0], out.inputs['Surface'])
    return mat


class _Mesh:
    """Quads collected per material, then built as one object."""

    def __init__(self):
        self.parts = {}  # material -> (verts, faces, uvs)

    def quad(self, mat, corners, uvs):
        v, f, u = self.parts.setdefault(mat, ([], [], []))
        b = len(v)
        v += corners
        f.append((b, b + 1, b + 2, b + 3))
        u += uvs

    def build(self, name):
        verts, faces, uvs, idx, mats = [], [], [], [], []
        for i, (mat, (v, f, u)) in enumerate(self.parts.items()):
            b = len(verts)
            verts += v
            faces += [tuple(x + b for x in q) for q in f]
            uvs += u
            idx += [i] * len(f)
            mats.append(mat)
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        layer = me.uv_layers.new(name='UV')
        layer.data.foreach_set('uv', [c for uv in uvs for c in uv])
        for m in mats:
            me.materials.append(m)
        me.polygons.foreach_set('material_index', idx)
        ob = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(ob)
        return ob


def box(mesh, x0, y0, z0, x1, y1, z1, wall, roof, pw=1, ph=1, zbase=None, bottom=False):
    """A box with its walls tiled by a facade pattern (pw x ph blocks per period, counted from zbase, the building's
    street level, so floor bands line up between setback tiers) and its roof tiled one block per texture."""
    zb = z0 if zbase is None else zbase
    v0, v1 = (z0 - zb) / ph, (z1 - zb) / ph
    # Each wall: the corner at u = 0 on the left as seen from outside, going round counter-clockwise from above.
    walls = [((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)), ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0))]
    for (ax, ay), (bx, by) in walls:
        u1 = (abs(bx - ax) + abs(by - ay)) / pw
        mesh.quad(wall, [(ax, ay, z1), (ax, ay, z0), (bx, by, z0), (bx, by, z1)],
                  [(0, v1), (0, v0), (u1, v0), (u1, v1)])
    mesh.quad(roof, [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
              [(x0, y1), (x0, y0), (x1, y0), (x1, y1)])
    if bottom:
        mesh.quad(roof, [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)],
                  [(x0, y0), (x0, y1), (x1, y1), (x1, y0)])


def city(ground=-110.0, extent=(-420, 420, -40, 760), pitch=40, street=8, seed=5,
         downtown=(0, 140), spread=150, peak=140, max_top=-12.0, keep_clear=(), planet=None,
         sky=((0.10, 0.27, 0.75), (1.0, 0.8, 0.58), (0.32, 0.48, 0.72)), haze=260.0, nadir_at=0.36,
         rotation=None):
    """Build the city. ground: street level (z). extent: x0, x1, y0, y1 of the street grid. downtown: where the towers
    are tallest (they fall off over `spread` blocks; `peak` sets how tall). max_top: no roof above this z (keeps the towers below the
    fighters). keep_clear: [(x, y, radius, max_z)] lower roofs near a point (under the subjects). planet: (x, y, top z)
    of the Daily Planet's globe top, or None. sky: (zenith, horizon, nadir) as given to mclib.sky_world, the haze's
    colours (nadir_at: where the nadir colour takes over; higher = the warm horizon glow reaches further down)."""
    rnd = random.Random(seed)
    hz = haze_group(*sky, haze, nadir_at, rotation)
    walls, roofs = {}, {}
    for key, st in STYLES.items():
        rows = st['rows']
        walls[key] = (_material(f'wall_{key}', _image(f'facade_{key}', rows), hz), len(rows[0]), len(rows))
        roofs[key] = _material(f'roof_{key}', B.texture(st['roof']), hz, glossy=False)
    mesh = _Mesh()

    # Streets: one tiled quad, the tile being one grid cell (road, kerb, pavement); lots get their own tops.
    cell_px = pitch * 16
    arr = np.zeros((cell_px, cell_px, 4), np.float32)
    road, kerb, pave = _px('gray_concrete_powder'), _px('smooth_stone'), _px('stone_bricks')
    for by in range(pitch):
        for bx in range(pitch):
            dx, dy = min(bx, pitch - 1 - bx), min(by, pitch - 1 - by)
            on_road = bx < street or by < street
            if on_road:
                t = road
                # dashed white centre lines
                if (bx == street // 2 - 1 and by >= street and by % 4 < 2) or \
                        (by == street // 2 - 1 and bx >= street and bx % 4 < 2):
                    t = _px('white_concrete')
            elif bx < street + 2 or by < street + 2 or bx >= pitch - 2 or by >= pitch - 2:
                t = kerb
            else:
                t = pave
            arr[by * 16:(by + 1) * 16, bx * 16:(bx + 1) * 16] = t
    arr[..., 3] = 0
    gimg = bpy.data.images.new('streets', cell_px, cell_px, alpha=True)
    gimg.alpha_mode = 'CHANNEL_PACKED'
    gimg.pixels.foreach_set(arr.ravel())
    gimg.pack()
    gmat = _material('streets', gimg, hz, glossy=False)
    x0, x1, y0, y1 = extent
    # Snap to the grid so the tile lines up with the lots.
    x0, y0 = math.floor(x0 / pitch) * pitch, math.floor(y0 / pitch) * pitch
    big = 3000  # the ground runs on past the city, into the haze
    mesh.quad(gmat, [(x0 - big, y1 + big, ground), (x0 - big, y0 - big, ground), (x1 + big, y0 - big, ground),
                     (x1 + big, y1 + big, ground)],
              [((-big) / pitch, (y1 - y0 + big) / pitch), (-big / pitch, -big / pitch),
               ((x1 - x0 + big) / pitch, -big / pitch), ((x1 - x0 + big) / pitch, (y1 - y0 + big) / pitch)])

    grass = _material('grass', _img1('grass_top', _px('grass_block_top', GRASS)), hz, glossy=False)
    leaves = _material('leaves', _img1('leaves', _px('oak_leaves', FOLIAGE, over=(0.08, 0.16, 0.04))), hz, glossy=False)
    log = _material('log', B.texture('oak_log'), hz, glossy=False)
    copper = _material('copper', B.texture('oxidized_copper'), hz, glossy=False)

    def dist_cam(px, py):
        return math.hypot(px, py)

    def top_limit(cx, cy):
        lim = max_top
        for kx, ky, r, mz in keep_clear:
            if math.hypot(cx - kx, cy - ky) < r:
                lim = min(lim, mz)
        return lim

    def building(bx0, by0, w, d, dist):
        style = rnd.choices(list(STYLES), [st['share'] for st in STYLES.values()])[0]
        st = STYLES[style]
        wall, pw, ph = walls[style]
        roof = roofs[style]
        f = math.exp(-(dist / spread) ** 2)
        h = 8 + (20 + peak * f) * st['tall'] * (0.35 + 0.65 * rnd.random() ** 0.8)
        lim = top_limit(bx0 + w / 2, by0 + d / 2) - ground
        h = max(6, min(h, lim))
        h = int(h / ph) * ph + 1
        # Faces n * pw + 1 wide so both corners land on a pier.
        nw, nd = max(1, (w - 1) // pw), max(1, (d - 1) // pw)
        w, d = nw * pw + 1, nd * pw + 1
        z = ground
        tiers = 1 + (h > 50) + (h > 90) if style in ('deco', 'glass', 'quartz') else 1
        tier_h = [h] if tiers == 1 else ([int(h * 0.62), int(h * 0.85), h] if tiers == 3 else [int(h * 0.75), h])
        cx, cy = bx0, by0
        for i, th in enumerate(tier_h):
            th = int(th / ph) * ph + 1 if i == len(tier_h) - 1 else int(th / ph) * ph
            box(mesh, cx, cy, z, cx + w, cy + d, ground + th, wall, roof, pw, ph, zbase=ground)
            z = ground + th
            # setback: one pattern period in from each side
            if nw > 2 and nd > 2:
                cx, cy, nw, nd = cx + pw, cy + pw, nw - 2, nd - 2
                w, d = nw * pw + 1, nd * pw + 1
        # Roof kit: a few plant boxes, and a mast on the tall ones.
        for _ in range(rnd.randint(0, 3)):
            if w > 6 and d > 6:
                ux, uy = cx + rnd.randint(1, w - 4), cy + rnd.randint(1, d - 4)
                box(mesh, ux, uy, z, ux + rnd.choice((2, 3)), uy + 2, z + rnd.choice((1, 2)), roofs['andes'],
                    roofs['andes'])
        if style == 'deco' and h > 40 and rnd.random() < 0.7:
            # A stepped pyramid crown in green copper, like the old New York towers.
            while w >= 3 and d >= 3:
                box(mesh, cx, cy, z, cx + w, cy + d, z + 2, copper, copper)
                z += 2
                cx, cy, w, d = cx + 1, cy + 1, w - 2, d - 2
        elif h > 100 and rnd.random() < 0.6 and dist_cam(cx, cy) > 200:  # masts, but not up next to the fighters
            mx, my = cx + w // 2, cy + d // 2
            box(mesh, mx, my, z, mx + 1, my + 1, z + rnd.randint(8, 16), roofs['andes'], roofs['andes'])

    def park(px0, py0, size):
        mesh.quad(grass, [(px0, py0 + size, ground + 0.02), (px0, py0, ground + 0.02), (px0 + size, py0, ground + 0.02),
                          (px0 + size, py0 + size, ground + 0.02)],
                  [(0, size), (0, 0), (size, 0), (size, size)])
        for _ in range(rnd.randint(6, 12)):
            tx, ty = px0 + rnd.randint(2, size - 4), py0 + rnd.randint(2, size - 4)
            th = rnd.randint(3, 5)
            box(mesh, tx, ty, ground, tx + 1, ty + 1, ground + th, log, log)
            box(mesh, tx - 2, ty - 2, ground + th - 1, tx + 3, ty + 3, ground + th + 2, leaves, leaves)

    for gx in range(x0, x1, pitch):
        for gy in range(y0, y1, pitch):
            lx, ly = gx + street + 2, gy + street + 2      # the lot inside the pavement
            size = pitch - street - 4
            dist = math.hypot(lx + size / 2 - downtown[0], ly + size / 2 - downtown[1])
            if planet and math.hypot(lx + size / 2 - planet[0], ly + size / 2 - planet[1]) < pitch * 0.7:
                continue  # the Daily Planet's own lot
            r = rnd.random()
            if r < 0.07:
                park(lx, ly, size)
            elif r < 0.45:
                building(lx, ly, size, size, dist)
            elif r < 0.75:  # two
                half = size // 2 - 1
                if rnd.random() < 0.5:
                    building(lx, ly, half, size, dist)
                    building(lx + half + 2, ly, half, size, dist)
                else:
                    building(lx, ly, size, half, dist)
                    building(lx, ly + half + 2, size, half, dist)
            else:  # four
                q = size // 2 - 1
                for ox in (0, q + 2):
                    for oy in (0, q + 2):
                        building(lx + ox, ly + oy, q, q, dist)
    ob = mesh.build('city')
    if planet:
        daily_planet(*planet, ground, walls['deco'], roofs['deco'], hz)
    city_objects = [ob] + ([bpy.data.objects['daily_planet'], bpy.data.objects['planet_globe']] if planet else [])
    return city_objects


def _img1(name, px):
    img = bpy.data.images.new(name, 16, 16, alpha=True)
    img.alpha_mode = 'CHANNEL_PACKED'
    px = px.copy()
    px[..., 3] = 0
    img.pixels.foreach_set(px.ravel())
    img.pack()
    return img


def daily_planet(x, y, top, ground, wall, roof, hz):
    """The Daily Planet: an art-deco setback tower in sandstone, topped by a gold globe with a ring round it."""
    w_mat, pw, ph = wall
    mesh = _Mesh()
    r = 9                                    # the globe's radius (blocks)
    gz = top - r                              # its centre
    tower_top = gz - r + 2                    # the globe sits a little into the roof
    h = tower_top - ground
    tiers = [(41, 0.5), (33, 0.75), (25, 0.92), (17, 1.0)]   # (width, share of the height)
    z = ground
    for wdt, share in tiers:
        zt = ground + int(h * share)
        box(mesh, x - wdt / 2, y - wdt / 2, z, x + wdt / 2, y + wdt / 2, zt, w_mat, roof, pw, ph, zbase=ground)
        z = zt
    mesh.build('daily_planet')
    # The globe: gold blocks in a voxel sphere, the ring a tilted band of iron round it.
    gold = B.material('gold_block', roughness=0.3)
    iron = B.material('iron_block', roughness=0.4)
    gold.node_tree.nodes['Principled BSDF'].inputs['Metallic'].default_value = 0.7  # a brass sheen, not sandstone
    for m in (gold, iron):
        _add_haze(m, hz)
    cells = {}
    cx, cy = int(x), int(y)
    for i in range(-r, r + 1):
        for j in range(-r, r + 1):
            for k in range(-r, r + 1):
                if i * i + j * j + k * k <= (r + 0.3) ** 2:
                    cells[(cx + i, cy + j, int(gz) + k)] = 'gold_block'
    tilt = math.radians(22)
    rr = r + 3
    for a in range(0, 360, 3):
        t = math.radians(a)
        px, py, pz = rr * math.cos(t), rr * math.sin(t), 0.0
        # tilt about the x axis
        py, pz = py * math.cos(tilt) - pz * math.sin(tilt), py * math.sin(tilt) + pz * math.cos(tilt)
        cells[(cx + round(px), cy + round(py), int(gz) + round(pz))] = 'iron_block'
    B.blocks('planet_globe', cells, {'gold_block': gold, 'iron_block': iron})


def _add_haze(mat, hz):
    nt = mat.node_tree
    out = nt.nodes['Material Output']
    link = out.inputs['Surface'].links[0]
    src = link.from_socket
    if src.node.type == 'GROUP':
        return
    g = nt.nodes.new('ShaderNodeGroup')
    g.node_tree = hz
    nt.links.remove(link)
    nt.links.new(src, g.inputs[0])
    nt.links.new(g.outputs[0], out.inputs['Surface'])


def city_sun(objects, direction, strength, color):
    """A sun that lights only the city (Cycles light linking: its receivers are the city's objects)."""
    coll = bpy.data.collections.new('city_receivers')
    for ob in objects:
        coll.objects.link(ob)  # an object can sit in several collections; this one isn't in the scene, only a list
    ld = bpy.data.lights.new('city_sun', 'SUN')
    ld.energy = strength
    ld.color = color
    ld.angle = math.radians(1.0)
    sun = bpy.data.objects.new('city_sun', ld)
    bpy.context.scene.collection.objects.link(sun)
    sun.rotation_euler = (-Vector(direction)).to_track_quat('-Z', 'Y').to_euler()  # -Z (the beam) away from the sun
    sun.light_linking.receiver_collection = coll
    return sun
