"""Key art: "Suppressing Fire". A green suit with the Spartan helmet (Master Chief's), the firebox and the chaingun
holds its ground in a burning warzone at night, the chaingun firing a stream of tracers up into the starry sky at an
enemy on the ground off the right edge of the frame. The camera stands in front of it, a little to its right, at
hip height (1.2 blocks), looking up at it.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/suppressing_fire.py -- [--preview] [--pct N]
        [--views] [--check] [--score] [--crop x0,y0,x1,y1] [--cpu] [--out path]  (+ the pose/camera numbers below)

--check only poses: prints the foot heights and the clipping checks. --views: orthographic checks of the pose.
--score measures (no render) how much of the helmet's front shows and how many tracers are in the frame.

Why this angle: the helmet sits low between the big pads, so it only shows from the front half (seen from the side,
the near pad hides it, measured 0 % with --score) and from the ground the chest hides its lower half (the user wanted
the whole helmet in frame: at 0.3 blocks up 23 % of its lens-facing surface showed, at 1.2 about 40 %, at helmet
height 66 %). The head looks where the gun fires; the gun fires level, to the right, about 70 degrees off the
camera's line, so the tracers cross the frame.

World: the camera looks roughly along +Y (screen right ~ +X, up = +Z), the ground's top at z = 0. Poses: see mclib.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mclib as M  # noqa: E402
import mcblocks as B  # noqa: E402
from mathutils import Matrix, Quaternion, Vector  # noqa: E402

args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
PREVIEW = '--preview' in args
OUT = args[args.index('--out') + 1] if '--out' in args else os.path.join(M.MARKETING, 'out', 'suppressing_fire_raw.png')


def arg(name, default):
    return type(default)(args[args.index(name) + 1]) if name in args else default


M.reset()
UP = Vector((0, 0, 1))

# ------------------------------------------------------------------ the suit
# Visor amber (a pilot inside), the firebox burning, the barrels hot from a long burst (the game's heat glow: dull red
# tips from a third of the way to overheating).
root, S = M.import_suit('chief', paint='green', tool='chaingun', helmet='helm_spartan', engine='backpack_parts',
                        glow=[('visor', M.VISOR_AMBER), 'fire', ('heat', (0.9, 0.9, 0.9)), ('heat_barrel', (0.5, 0.5, 0.5))],
                        glow_strength=1.8)
CHECKS = M.suit_clip_checks(S)

# The stance: braced against the recoil, the far (left, gun) leg back, the near leg forward, leaning back a little
# into the recoil. The camera sits on the ground CAM_ANGLE round from its front toward its right (fist) side: the gun
# is then on the far side and fires up and to the right without crossing the body or the helmet. The camera stays on
# -Y looking along +Y, so the suit is turned instead: screen right = +X.
CAM_ANGLE = arg('--camangle', 10.0)
YAW = 180 + CAM_ANGLE
LEAN = arg('--lean', -3.0)    # the whole suit tips from the feet (+ forward)
HUNCH = arg('--hunch', 4.0)   # body + hip hoods together (never the torso alone: the abdomen would clip the hoods)
root.rotation_euler = M.rad((-LEAN, 0, YAW))
yaw = math.radians(YAW)
FWD = Vector((-math.sin(yaw), math.cos(yaw), 0))
RIGHT = Vector((math.cos(yaw), math.sin(yaw), 0))
# ------------------------------------------------------------------ camera
# DIST blocks out, at hip height (cz); the frame turned SHIFT degrees right so the suit stands left of centre and the
# tracers get the right of the frame.
DIST = arg('--dist', 5.0)
CAM = Vector((0, -DIST, arg('--cz', 1.2)))
LENS = arg('--lens', 21.0)
TARGET = Vector((0, 0, arg('--lookz', 2.2)))
LOOK = CAM + (Quaternion(UP, math.radians(-arg('--shift', 12.0))) @ (TARGET - CAM))
LEGS = {
    'right_leg': (LEAN + 26, -8, 0),   # near leg: stepped forward
    'right_shin': (-30, 0, 0),
    'left_leg': (LEAN - 20, 10, 0),    # far leg: back and out, pushing (solved below to meet the ground)
    'left_shin': (-10, 0, 0),
}
for bone, deg in LEGS.items():
    M.pose(S[bone], deg)
for side in ('right', 'left'):
    M.pose(S[f'{side}_foot'], (LEAN - LEGS[f'{side}_leg'][0] - LEGS[f'{side}_shin'][0], 0, 0))
# The upper body turns TWIST degrees to its left, toward the target, over the legs (the game twists the torso over
# the legs up to 90 degrees to follow the view, by the torso bone; the hip hoods stay with the legs).
TWIST = arg('--twist', 20.0)
for bone in ('body', 'hips'):
    M.pose(S[bone], (-HUNCH, 0, 0))
M.pose(S['torso'], (0, 0, TWIST))  # the bone the game turns (PowerArmorRenderer.aim)
M.update()

# Where it fires: up at AIM_UP degrees, turned AIM_OUT past the torso's facing out to its left (away from us). The
# upper arm swings forward and up (the pads allow that, not swinging out to the side), the forearm points the barrels
# down the line.
AIM_OUT, AIM_UP = arg('--aimout', 40.0), arg('--aimup', -2.0)
AIM = (Quaternion(UP, math.radians(TWIST + AIM_OUT)) @ FWD)
AIM = (AIM * math.cos(math.radians(AIM_UP)) + UP * math.sin(math.radians(AIM_UP))).normalized()
M.pose(S['left_arm'], (arg('--arm', 25.0), 4, 0))
el = M.world(S['left_forearm'])
M.aim(S['left_forearm'], S['muzzle'], el + AIM * 40)
# The free fist: clenched down by the hip, the arm a little back (a forearm pointing forward came at the lens as a box).
M.pose(S['right_arm'], (arg('--fistarm', -12.0), -4, 0))
el = M.world(S['right_forearm'])
M.aim(S['right_forearm'], S['right_hand'], el + FWD * 0.25 - UP * 0.6)
# Eyes down the gun (the user's pick); --headcam f turns the head that fraction of the way toward the lens.
face = M.marker('face', S['head'], (0, 20, 0))
hp = M.world(S['head'])
M.aim(S['head'], face, hp + (AIM * (1 - arg('--headcam', 0.0)) + (CAM - hp).normalized() * arg('--headcam', 0.0)) * 20)


def sole(side):
    M.update()
    return min((o.matrix_world @ Vector(c)).z for o in M.meshes(S[f'{side}_foot']) for c in o.bound_box)


# The back leg swings back (knee nearly straight, foot flat) until its sole meets the floor with the front one.
BACK_KNEE = LEGS['left_shin'][0]
lo, hi = LEAN, LEAN - 70.0
for _ in range(20):
    k = (lo + hi) / 2
    M.pose(S['left_leg'], (k, LEGS['left_leg'][1], 0))
    M.pose(S['left_foot'], (LEAN - k - BACK_KNEE, 0, 0))
    if sole('left') < sole('right'):
        lo = k
    else:
        hi = k
print(f'back thigh {k - LEAN:.1f} deg from the lean')
root.location.z -= min(sole('left'), sole('right'))
M.update()
for b in ('left_foot', 'right_foot'):
    lowz = sorted((o.matrix_world @ Vector(c)).z for o in M.meshes(S[b]) for c in o.bound_box)[:4]
    print(f'{b}: sole z {lowz[0]:.3f}..{lowz[-1]:.3f}')
for c in CHECKS:
    c.report()

# The barrels' real line after posing: from the elbow through the muzzle.
SHOT = (M.world(S['muzzle']) - M.world(S['left_forearm'])).normalized()
# The flash starts at the barrels' ends (the game's `muzzle` marker sits a third of a block further out, where bullets
# appear): the middle of the barrel vertices that reach furthest along the shot line.
bv = [o.matrix_world @ v.co for o in M.meshes(S['chaingun_barrels']) for v in o.data.vertices]
far = max(v.dot(SHOT) for v in bv)
ends = [v for v in bv if v.dot(SHOT) > far - 0.02]
MUZZLE = sum(ends, Vector()) / len(ends)
print('shot line', [round(v, 3) for v in SHOT], 'muzzle', [round(v, 2) for v in MUZZLE])

RES = (2400, 1600)

# ------------------------------------------------------------------ tracers and the muzzle flash
rnd = random.Random(11)


def emission_material(name, color, strength):
    m = M.bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type != 'OUTPUT_MATERIAL':
            nt.nodes.remove(n)
    e = nt.nodes.new('ShaderNodeEmission')
    e.inputs['Color'].default_value = (*color, 1)
    e.inputs['Strength'].default_value = strength
    nt.links.new(e.outputs['Emission'], nt.nodes['Material Output'].inputs['Surface'])
    return m


def tint(ob, color, alpha, glow=0.0):
    """Smoke: a copy of the sprite's material with its colour multiplied by `color` and its alpha by `alpha`; glow > 0
    also makes it shine in that colour."""
    m = ob.data.materials[0].copy()
    nt = m.node_tree
    tex = next(n for n in nt.nodes if n.type == 'TEX_IMAGE')
    bsdf = nt.nodes['Principled BSDF']
    mul = nt.nodes.new('ShaderNodeMix')
    mul.data_type = 'RGBA'
    mul.blend_type = 'MULTIPLY'
    mul.inputs['Factor'].default_value = 1.0
    nt.links.new(tex.outputs['Color'], mul.inputs['A'])
    mul.inputs['B'].default_value = (*color, 1)
    nt.links.new(mul.outputs['Result'], bsdf.inputs['Base Color'])
    am = nt.nodes.new('ShaderNodeMath')
    am.operation = 'MULTIPLY'
    am.inputs[1].default_value = alpha
    nt.links.new(tex.outputs['Alpha'], am.inputs[0])
    nt.links.new(am.outputs[0], bsdf.inputs['Alpha'])
    bsdf.inputs['Roughness'].default_value = 1.0
    bsdf.inputs['Specular IOR Level'].default_value = 0.0
    if glow:
        nt.links.new(mul.outputs['Result'], bsdf.inputs['Emission Color'])
        bsdf.inputs['Emission Strength'].default_value = glow
    ob.data.materials[0] = m


def boxes(name, items, mats):
    """One mesh of oriented boxes: items = (centre, direction, length, thickness, material index)."""
    import bmesh
    bm = bmesh.new()
    for centre, d, length, thick, mi in items:
        d = Vector(d).normalized()
        rot = d.to_track_quat('X', 'Z').to_matrix().to_4x4()
        mat = Matrix.Translation(centre) @ rot @ Matrix.Diagonal((length, thick, thick, 1))
        r = bmesh.ops.create_cube(bm, size=1.0, matrix=mat)
        for f in {f for v in r['verts'] for f in v.link_faces}:
            f.material_index = mi
    me = M.bpy.data.meshes.new(name)
    bm.to_mesh(me)
    for m in mats:
        me.materials.append(m)
    ob = M.bpy.data.objects.new(name, me)
    M.bpy.context.scene.collection.objects.link(ob)
    ob.visible_shadow = False
    return ob


# The game's tracer: a streak up to 3 blocks long, a white-yellow core in an orange sheath (BulletRenderer). A long
# burst from spinning barrels sprays a little, so the stream fans out: each streak a few degrees off the line.
core = emission_material('tracer_core', (1.0, 0.9, 0.6), 30)
sheath = emission_material('tracer_sheath', (1.0, 0.42, 0.06), 10)
SPREAD = arg('--spread', 2.5)
items, d = [], 0.6
while d < 60:
    dirn = (Quaternion(UP, math.radians(rnd.gauss(0, SPREAD))) @ SHOT
            + UP * math.radians(rnd.gauss(0, SPREAD * 0.6))).normalized()
    length = rnd.uniform(0.7, 1.2)
    mid = MUZZLE + dirn * (d + length / 2)
    items.append((mid, dirn, length, 0.5 * M.PX, 0))
    items.append((mid, dirn, length + 0.05, 1.1 * M.PX, 1))
    d += length + rnd.uniform(0.5, 1.0)
boxes('tracers', items, [core, sheath])
tracer_items = items

# The muzzle flash, drawn in pixels like the game's particles: a white-hot core, a long spike forward that goes
# yellow then orange, four shorter spikes raked forward round it, and a few loose orange pixels.
flash_mats = [emission_material('flash_white', (1, 0.95, 0.85), 30), emission_material('flash_yellow', (1, 0.72, 0.22), 16),
              emission_material('flash_orange', (1, 0.36, 0.05), 9)]
side = SHOT.cross(UP).normalized()
up2 = side.cross(SHOT).normalized()
FL = arg('--flashlen', 1.0)
items = [(MUZZLE + SHOT * 0.05, SHOT, 4 * M.PX, 4 * M.PX, 0)]
for t0, t1, th, tier in ((0.0, 0.2, 3.5, 0), (0.2, 0.45, 2.5, 1), (0.45, 0.75, 1.5, 2)):
    items.append((MUZZLE + SHOT * FL * (t0 + t1) / 2, SHOT, FL * (t1 - t0), th * M.PX, tier))
for k in range(4):
    a = k * math.pi / 2 + math.pi / 4 + 0.25
    d = (SHOT * 0.8 + (side * math.cos(a) + up2 * math.sin(a))).normalized()
    for t0, t1, th, tier in ((0.0, 0.12, 2.5, 0), (0.12, 0.28, 1.5, 1), (0.28, 0.4, 1, 2)):
        items.append((MUZZLE + SHOT * 0.03 + d * FL * (t0 + t1) / 2, d, FL * (t1 - t0), th * M.PX, tier))
for _ in range(14):
    p = MUZZLE + SHOT * rnd.uniform(0.1, 0.8) * FL + side * rnd.gauss(0, 0.12) + up2 * rnd.gauss(0, 0.12)
    items.append((p, SHOT, 1 * M.PX, 1 * M.PX, rnd.choice((1, 2, 2))))
boxes('muzzle_flash', items, flash_mats)
M.light('POINT', 'flash_light', MUZZLE + SHOT * 0.25, MUZZLE, arg('--flash', 1200.0), (1.0, 0.62, 0.3), size=0.15)

# Smoke from the barrels (the game's muzzle burst leaves a puff of smoke per shot), drifting up behind the flash.
smoke = [MUZZLE - SHOT * rnd.uniform(0.0, 0.5) + UP * rnd.uniform(0.15, 0.6) + side * rnd.uniform(-0.2, 0.2) for _ in range(5)]
tint(B.sprites('gun_smoke', smoke, 'big_smoke_3', facing=CAM, size=0.18), (0.3, 0.3, 0.3), 0.4)


def score():
    """Measures what the camera sees (no render): how much of the helmet's front shows (it sits low between the pads,
    which hide it from the side and from below), its size on screen, and how many tracers are in the frame. Used to
    search the camera and pose numbers instead of guessing them."""
    from bpy_extras.object_utils import world_to_camera_view
    sc = M.bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = RES
    cam = M.camera(CAM, LOOK, lens=LENS)
    M.update()
    dg = M.bpy.context.evaluated_depsgraph_get()
    helm = M.meshes(S['helm_spartan'])
    names = {o.name for o in helm}
    fwd = (S['head'].matrix_world.to_3x3() @ Vector((0, 1, 0))).normalized()
    seen = total = whole = unhidden = 0
    xs, ys = [], []
    for o in helm:
        mw = o.matrix_world
        for p in o.data.polygons:
            n = (mw.to_3x3() @ p.normal).normalized()
            front = n.dot(fwd) >= 0.7
            vs = [mw @ o.data.vertices[i].co for i in p.vertices]  # triangles (the glb's faces)
            for u in (0.1, 0.25, 0.4, 0.55, 0.7, 0.85):
                for v in (0.1, 0.25, 0.4, 0.55, 0.7, 0.85):
                    if u + v > 0.95:
                        continue
                    pt = vs[0] + (vs[1] - vs[0]) * u + (vs[2] - vs[0]) * v
                    total += front
                    if n.dot(CAM - pt) <= 0:
                        continue
                    whole += 1  # every helmet point turned toward the lens...
                    d = pt - CAM
                    hit, loc, _, _, ob, _ = sc.ray_cast(dg, CAM, d.normalized(), distance=d.length + 0.01)
                    if hit and ob.name in names and (loc - pt).length < 0.02:
                        unhidden += 1  # ...and those nothing hides (the pads, the gun)
                        if front:
                            seen += n.dot(-d.normalized())  # a face seen edge-on hardly counts
                        c = world_to_camera_view(sc, cam, pt)
                        xs.append(c.x)
                        ys.append(c.y)
    inframe = sum(1 for i in range(0, len(tracer_items), 2)
                  if 0 < world_to_camera_view(sc, cam, tracer_items[i][0]).x < 1
                  and 0 < world_to_camera_view(sc, cam, tracer_items[i][0]).y < 1)
    for i in range(0, min(len(tracer_items), 12), 2):
        mid, dn, L = tracer_items[i][:3]
        a0, a1 = world_to_camera_view(sc, cam, mid - dn * L / 2), world_to_camera_view(sc, cam, mid + dn * L / 2)
        print(f'  tracer {i // 2}: x {a0.x:.2f}..{a1.x:.2f} y {a0.y:.2f}..{a1.y:.2f} len {L:.2f}')
    c = world_to_camera_view(sc, cam, M.world(S['head']))
    print(f'SCORE face {seen / max(1, total):.2f} helmet_unhidden {unhidden / max(1, whole):.2f} feet_y {world_to_camera_view(sc, cam, M.world(S["left_foot"])).y:.2f} tracers {inframe} head at ({c.x:.2f}, {c.y:.2f})'
          f' muzzle_dir {[round(v, 2) for v in SHOT]} passes_cam {((CAM - MUZZLE) - SHOT * (CAM - MUZZLE).dot(SHOT)).length:.2f}')


if '--score' in args:
    score()
    sys.exit(0)

# ------------------------------------------------------------------ the battlefield
cells = {}


def put(x, y, z, t):
    cells[(x, y, z)] = t


def noise(x, y, scale, seed):
    def h(i, j):
        return random.Random(i * 7919 + j * 104729 + seed).random()
    fx, fy = x / scale, y / scale
    i, j = math.floor(fx), math.floor(fy)
    u, v = fx - i, fy - j
    u, v = u * u * (3 - 2 * u), v * v * (3 - 2 * v)
    return (h(i, j) * (1 - u) + h(i + 1, j) * u) * (1 - v) + (h(i, j + 1) * (1 - u) + h(i + 1, j + 1) * u) * v


# Shell craters: (x, y, radius, depth).
CRATERS = [(-7, 7, 2.6, 2), (5, 6, 1.8, 1), (-15, 18, 3.5, 2), (11, 22, 3.0, 2), (-2, 26, 2.5, 1), (20, 12, 2.2, 1),
           (-26, 34, 4, 2)]
ground = {}
for x in range(-60, 61):
    for y in range(-14, 90):
        # Flat out to 25 blocks (seen from a camera a quarter block up, a one-block step hides everything behind it),
        # then low rolling ground.
        far = max(0.0, (math.hypot(x, y) - 25) / 30)
        top = 0 if far == 0 else int(noise(x, y, 9, 3) * (1.0 + 3 * far))
        mat = None
        for cx, cy, r, dep in CRATERS:
            dist = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if dist < r:
                top = min(top, -dep + int(dep * (dist / r) ** 2))
                mat = 'gravel' if dist < r * 0.5 else 'coarse_dirt'
            elif dist < r + 1.2:
                if math.hypot(cx, cy) > 15:  # clods thrown up round the rim (not near the lens: a block there is a wall)
                    top = max(top, 1 if rnd.random() < 0.3 else top)
                mat = 'coarse_dirt'
        ground[(x, y)] = top
        for z in range(top - 2, top):
            t = mat or rnd.choices(['coarse_dirt', 'dirt', 'podzol_top', 'rooted_dirt'], [5, 3, 2, 1])[0]  # (grey gravel tiles stood out)
            put(x, y, z, t if z == top - 1 else 'dirt')

# A burnt-out village house behind the suit on the left: cobblestone footing, broken plank walls, charred posts.
HX, HY = -16, 14
for x in range(HX, HX + 7):
    for y in range(HY, HY + 6):
        g = ground.get((x, y), 0)
        edge = x in (HX, HX + 6) or y in (HY, HY + 5)
        put(x, y, g, 'cobblestone')
        if not edge:
            continue
        corner = x in (HX, HX + 6) and y in (HY, HY + 5)
        h = 4 if corner else int(3.4 * noise(x, y, 2.5, 9) + 0.6)
        if x == HX + 3 and y == HY:
            h = 0  # the doorway, blown in
        for z in range(g + 1, g + 1 + h):
            put(x, y, z, 'dark_oak_log' if corner else rnd.choice(['oak_planks', 'oak_planks', 'dark_oak_planks', 'cobblestone']))
# On the right, toward the enemy: the stump of a stone watchtower, burning, and a broken wall; rubble everywhere.
TX, TY = 13, 24
for x in range(TX, TX + 4):
    for y in range(TY, TY + 4):
        if x in (TX, TX + 3) or y in (TY, TY + 3):
            h = int(3 + 6 * noise(x * 3, y * 3, 2, 31)) if (x, y) != (TX, TY + 1) else 2
            for z in range(0, h):
                put(x, y, z, rnd.choice(['stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks', 'stone_bricks']))
for x in range(5, 11):
    for z in range(0, int(2.4 * noise(x, 0, 2, 4) + (0 if x % 3 else 1))):
        put(x, 17, z, rnd.choice(['stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks', 'cobblestone']))
# Rubble heaps (lone blocks read as crates): a few blocks wide, one or two high, of whatever the shells knocked down.
for _ in range(22):
    x0, y0 = rnd.randint(-30, 30), rnd.randint(5, 45)
    if abs(x0) < 5 and y0 < 10:
        continue
    w, d = rnd.randint(2, 4), rnd.randint(1, 3)
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + d):
            if rnd.random() < 0.8:
                g = ground.get((x, y), 0)
                put(x, y, g, rnd.choice(['cobblestone', 'mossy_cobblestone', 'stone_bricks', 'cracked_stone_bricks',
                                         'gravel', 'dark_oak_planks']))
                if x not in (x0, x0 + w - 1) and rnd.random() < 0.5:
                    put(x, y, g + 1, rnd.choice(['cobblestone', 'cracked_stone_bricks', 'dark_oak_planks']))
# Low hills far off, black against the glow on the horizon.
for x in range(-160, 161, 1):
    for y in (130, 131):
        hgt = int(noise(x, 0, 14, 21) * 9 + noise(x, 0, 5, 22) * 3)
        for z in range(-1, hgt):
            put(x, y, z, 'blackstone')

mats = {n: B.material(n) for n in set(cells.values())}
for m in mats.values():  # dry dirt and stone: no sheen (at the camera's grazing angle it lit the ground up like wet tiles)
    m.node_tree.nodes['Principled BSDF'].inputs['Specular IOR Level'].default_value = 0.0
    m.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 1.0
B.blocks('battlefield', cells, mats)

# Fires: crossed-quad fire blocks (the game's own texture, glowing) on the ruins and in the craters, each with a
# warm light, and smoke rising from them, leaning with the wind toward the enemy side.
FIRES = [(HX + 1, HY + 1), (HX + 2, HY + 4), (HX + 5, HY + 2), (HX + 4, HY + 4), (HX + 6, HY + 3), (TX + 1, TY + 1),
         (TX + 2, TY + 2), (-7, 7), (-15, 18),
         (11, 22), (-2, 26), (20, 12), (-26, 34), (-9, 13), (26, 40), (-36, 48), (3, 52), (40, 30)]
fire_items = []
for fx, fy in FIRES:
    z = max(k[2] for k in cells if k[0] == fx and k[1] == fy) + 1 if any(k[0] == fx and k[1] == fy for k in cells) else 0
    fire_items.append((fx, fy, z, 'fire_0', 1))
    if rnd.random() < 0.6:
        fire_items.append((fx + rnd.choice((-1, 1)), fy, ground.get((fx, fy), 0), 'fire_0', 1))
    M.light('POINT', f'fire_{fx}_{fy}', (fx + 0.5, fy + 0.5, z + 0.8), (fx, fy, 0), 400 if math.hypot(fx, fy) < 30 else 2500,
            (1.0, 0.5, 0.16), size=0.8)
# Scorched dry grass and dead bushes in the dirt.
dry = []
for _ in range(200):
    x, y = rnd.randint(-30, 30), rnd.randint(5, 40)  # none close to the lens: up close they were huge pale blobs
    if (x, y, ground.get((x, y), 0)) in cells:
        continue
    dry.append((x, y, ground.get((x, y), 0), rnd.choice(['short_dry_grass', 'short_dry_grass', 'dead_bush', 'tall_dry_grass']), 1))
B.plants('dry_grass', dry)
fires = B.plants('fires', fire_items)
fire_mat = B.material('fire_0', emission=6)
fires.data.materials.clear()
fires.data.materials.append(fire_mat)

smoke_pts = []
for (fx, fy), n in ((FIRES[0], 60), (FIRES[2], 40), ((TX + 1, TY + 1), 50), ((-15, 18), 45), ((11, 22), 30),
                   ((-26, 34), 50), ((3, 52), 50)):
    for _ in range(n):
        h = rnd.random() ** 0.8 * 22
        smoke_pts.append(Vector((fx + 0.5 - h * 0.45 + rnd.uniform(-1, 1) * (0.4 + h * 0.12),
                                 fy + 0.5 + rnd.uniform(-1, 1) * (0.4 + h * 0.1), 1.5 + h)))
for k in range(4):
    tint(B.sprites(f'smoke_{k}', smoke_pts[k::4], f'big_smoke_{k * 3 + 2}', facing=CAM, size=2.4 + k * 0.5),
         (0.35, 0.33, 0.32), 0.55)

# (No far-off tracers from other guns: tried firing up, level and climbing at 30 degrees; the user preferred the
# suit's own burst alone.)

# Embers rising from the fires and drifting over the field (the game's lava-pop sparks), a few close to the lens.
embers = []
for _ in range(arg('--embers', 160)):
    fx, fy = rnd.choice(FIRES)
    h = rnd.random() ** 1.5 * 9
    p = Vector((fx + 0.5 + rnd.gauss(0, 1 + h * 0.4) - h * 0.3, fy + 0.5 + rnd.gauss(0, 1 + h * 0.3), 0.6 + h))
    embers.append((p, Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), 1)), rnd.choice((0.75, 1, 1.25)) * M.PX * (1 + p.y / 25),
                   rnd.choice((0.75, 1)) * M.PX * (1 + p.y / 25), rnd.choice((0, 1, 1))))
boxes('embers', embers, [emission_material('ember_hot', (1.0, 0.45, 0.08), 5), emission_material('ember', (1.0, 0.25, 0.03), 3)])

# ------------------------------------------------------------------ the night sky
# No stars (the user: they made the sky too busy); --stars puts Minecraft's back: small white squares scattered over
# the sky, each turned at random. The square full moon stays.
if '--stars' in args:
    stars = M.bpy.data.meshes.new('stars')
    verts, faces = [], []
    R = 900
    for _ in range(2600):
        az, el = rnd.uniform(0, 2 * math.pi), math.asin(rnd.uniform(0.02, 1.0))
        n = Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
        s = R * math.radians(rnd.choice((0.08, 0.1, 0.1, 0.12, 0.15, 0.2)))
        a = Vector((0, 0, 1)).cross(n).normalized()
        b = n.cross(a)
        tw = rnd.uniform(0, math.pi / 2)
        a, b = a * math.cos(tw) + b * math.sin(tw), b * math.cos(tw) - a * math.sin(tw)
        c = CAM + n * R
        k = len(verts)
        verts += [c + (a + b) * s / 2, c + (a - b) * s / 2, c + (-a - b) * s / 2, c + (-a + b) * s / 2]
        faces.append((k, k + 1, k + 2, k + 3))
    stars.from_pydata(verts, [], faces)
    stars.materials.append(emission_material('stars', (0.85, 0.9, 1.0), 4))
    so = M.bpy.data.objects.new('stars', stars)
    M.bpy.context.scene.collection.objects.link(so)
    so.visible_shadow = False
    so.visible_diffuse = False

MOON = Vector((0.55, 0.75, 0.62)).normalized()
moon_img = B.texture('celestial/moon/full_moon', kind='environment')
mm = M.bpy.data.materials.new('moon')
mm.use_nodes = True
nt = mm.node_tree
for n in list(nt.nodes):
    if n.type != 'OUTPUT_MATERIAL':
        nt.nodes.remove(n)
tx = nt.nodes.new('ShaderNodeTexImage')
tx.image = moon_img
tx.interpolation = 'Closest'
em = nt.nodes.new('ShaderNodeEmission')
em.inputs['Strength'].default_value = 3
nt.links.new(tx.outputs['Color'], em.inputs['Color'])
mix = nt.nodes.new('ShaderNodeAddShader')  # drawn additively, as in the game: its black square disappears
nt.links.new(nt.nodes.new('ShaderNodeBsdfTransparent').outputs[0], mix.inputs[0])
nt.links.new(em.outputs[0], mix.inputs[1])
nt.links.new(mix.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
mo = M.square_sun(MOON, distance=800, size=800 * math.radians(arg('--moon', 9.0)))
mo.data.materials.clear()
mo.data.materials.append(mm)
mo.location = CAM + MOON * 800
me = mo.data
layer = me.uv_layers.new(name='UV')
for i, uv in enumerate([(0, 0), (1, 0), (1, 1), (0, 1)]):
    layer.data[i].uv = uv

# Night: a dark blue sky, warmer and lighter at the horizon (the fires); the moon's cold light rims the suit from behind.
M.sky_world(zenith=(0.004, 0.007, 0.022), horizon=(0.06, 0.03, 0.022), strength=arg('--amb', 0.12), camera_strength=1.0)
M.light('SUN', 'moonlight', MOON * 50, (0, 0, 0), arg('--moonlight', 0.8), (0.6, 0.72, 1.0), angle=1.0)
# The fires out of frame behind the camera light the side we see, low and warm; a faint cool fill keeps the green green.
M.light('AREA', 'fire_fill', (-4.0, -5.0, 1.5), (0, 0, 1.6), arg('--fill', 40.0), (1.0, 0.55, 0.25), size=2.5)
M.light('AREA', 'cool_fill', (-1.0, -5.0, 3.0), (0, 0, 1.6), arg('--cool', 25.0), (0.55, 0.7, 1.0), size=3)
rim = M.light('AREA', 'rim', (-1.0, 4.5, 4.0), (0, 0, 1.8), arg('--rim', 260.0), (0.6, 0.75, 1.0), size=2)

M.cycles(res=RES, preview=PREVIEW, cpu='--cpu' in args)
if '--pct' in args:
    M.bpy.context.scene.render.resolution_percentage = int(args[args.index('--pct') + 1])
cam = M.camera(CAM, LOOK, lens=LENS)
M.depth_of_field(cam, M.world(S['body']), fstop=arg('--fstop', 5.6))
if '--crop' in args:  # a region of the frame only (x0 y0 x1 y1, 0..1 from the bottom left), for close checks
    sc = M.bpy.context.scene
    sc.render.use_border = True
    sc.render.use_crop_to_border = True
    sc.render.border_min_x, sc.render.border_min_y, sc.render.border_max_x, sc.render.border_max_y = \
        [float(v) for v in args[args.index('--crop') + 1].split(',')]
if '--views' in args:
    M.debug_views(OUT.replace('.png', ''), (0, 0, 1.4))
elif '--check' not in args:
    M.render(OUT)
