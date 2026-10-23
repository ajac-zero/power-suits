"""Key art: "Big Daddy". A brown suit with the Protector helmet (Subject Delta's riveted visor), the tidal engine and
the drill stands guard on the sea floor in front of an ocean monument, hunched and heavy like BioShock's Big Daddies,
its drill levelled at the viewer (pose after the BioShock cover). Light comes down in shafts through holes in the surface.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/big_daddy.py -- [--preview] [--pct N]
        [--visor amber|red|green] [--pose drill|cover] [--arm DEG] [--drill X] [--up Z] [--views] [--check] [--out path]

--arm / --drill / --up: the drill arm's forward swing and where the bit points beside the lens (see ARM_GUARD).
--check only poses: prints the foot heights and the clipping checks.

World: camera on -Y looking +Y (screen right = +X, up = +Z), the sea floor's top at z = 0. Poses: see mclib's docstring.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mclib as M  # noqa: E402
import mcblocks as B  # noqa: E402
from mathutils import Vector  # noqa: E402

args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
PREVIEW = '--preview' in args
OUT = args[args.index('--out') + 1] if '--out' in args else os.path.join(M.MARKETING, 'out', 'big_daddy.png')

M.reset()
# The visor colour: a Big Daddy's portholes are yellow, and so is the suit's visor with a pilot inside (--visor red:
# a rogue, green: a friendly mind).
VISOR = {'amber': M.VISOR_AMBER, 'red': M.VISOR_RED, 'green': M.VISOR_GREEN}[args[args.index('--visor') + 1] if '--visor' in args else 'amber']

# ------------------------------------------------------------------ the suit
root, S = M.import_suit('daddy', paint='brown', tool='drill', helmet='helm_protector', engine='pack_tidal',
                        glow=[('visor', VISOR), 'tidal'], glow_strength=1.8)
# Light levels (W or Blender strengths): the sun only reaches the sea through the holes in the surface, so it can be
# strong enough for visible shafts without touching the suit, which has its own lights.
SUN_STRENGTH, AMBIENT, KEY, TOP, FILL = 40, 0.04, 550, 23000, 70
POSE = args[args.index('--pose') + 1] if '--pose' in args else 'drill'
# The drill arm (pose 'drill'): its forward swing (deg) and where the bit points, relative to the lens (blocks: + is
# screen right / up). The user's pick: low enough that the drill sits at chest height, clear of the helmet.
ARM_GUARD = float(args[args.index('--arm') + 1]) if '--arm' in args else 45
DRILL_OFF = float(args[args.index('--drill') + 1]) if '--drill' in args else 0.0
DRILL_UP = float(args[args.index('--up') + 1]) if '--up' in args else 0.3
import math  # noqa: E402
from mathutils.bvhtree import BVHTree  # noqa: E402


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


def bvh(objs, shrink=0.2 * M.PX):
    """One BVH of the objects' faces in world space, each cube shrunk by `shrink` on every side (in its own frame) so
    faces that only touch (seams) don't count as clipping: what overlaps after that goes deeper than 0.2 px."""
    M.update()
    verts, polys = [], []
    bvh.owner = []
    for o in objs:
        bvh.owner += [o.name] * len(o.data.polygons)
        b = len(verts)
        c = sum((v.co for v in o.data.vertices), Vector()) / max(1, len(o.data.vertices))
        verts += [o.matrix_world @ Vector([x - shrink * ((x > m) - (x < m)) for x, m in zip(v.co, c)])
                  for v in o.data.vertices]
        polys += [[b + i for i in p.vertices] for p in o.data.polygons]
    return BVHTree.FromPolygons(verts, polys)


# Clipping check: the upper body (torso, without arms and head) against the hips and thighs. Pairs of faces that already
# touch at rest (coplanar seams) are subtracted, so only what the pose adds is counted.
UPPER = lambda: meshes(S['torso'], skip=('left_arm', 'right_arm', 'head'))  # noqa: E731
LOWER = lambda: meshes(S['pelvis']) + meshes(S['hips']) + [o for o in S['left_leg'].children if o.type == 'MESH'] \
    + [o for o in S['right_leg'].children if o.type == 'MESH']  # noqa: E731
REST_CLIP = set(bvh(UPPER()).overlap(bvh(LOWER())))
# ... and the hoods + pelvis against the thighs (they hinge on the same axis but don't turn with the hunch). The hip
# axle is left out: it runs into both thighs by design (hidden inside them, on their hinge axis).
HOODS = lambda: [o for o in meshes(S['pelvis']) + meshes(S['hips']) if not o.name.startswith('hip_axle')]  # noqa: E731
THIGHS = lambda: [o for o in S['left_leg'].children if o.type == 'MESH'] + [o for o in S['right_leg'].children if o.type == 'MESH']  # noqa: E731
REST_CLIP2 = set(bvh(HOODS()).overlap(bvh(THIGHS())))
# ... and each arm against the chest and helmet (the raised drill and the thrust fist swing the pads up by the head).
CHEST = lambda: meshes(S['torso'], skip=('left_arm', 'right_arm'))  # noqa: E731
ARMS = lambda: meshes(S['left_arm']) + meshes(S['right_arm'])  # noqa: E731
REST_CLIP3 = set(bvh(ARMS(), 0.5 * M.PX).overlap(bvh(CHEST(), 0.5 * M.PX)))

# The pose after BioShock's cover (mirrored, so the drill is on its own left arm): the Big Daddy comes at the viewer,
# leaning in from the hips, its near (right) foot stepping toward the lens. The camera is down at the sand, close and
# wide. Arms: --pose drill (the picture): the drill levelled at us at chest height, the fist clenched low; --pose cover:
# as on the cover, the fist thrust at us beside the helmet and the drill raised high, pointing up.
ARM_DRILL, ARM_FIST = 100, 75  # the upper arms' forward swing (deg, in the torso's frame, which leans 28 deg forward)
YAW = 192    # facing us, its right (fist) shoulder a little forward
HUNCH = 16   # the upper body bends forward at the hips (see below)
LEAN = 18    # the whole suit tips forward from the feet (on the root, not the waist: bending at the waist made the
             # abdomen clip the hips), the legs make up for it below
root.rotation_euler = M.rad((-LEAN, 0, YAW))
LEGS = {
    'right_leg': (LEAN + 30, -10, 0),   # the stepping leg: thigh forward toward us
    'right_shin': (-44, 0, 0),
    'left_leg': (LEAN - 22, 12, 0),     # the pushing leg, behind
    'left_shin': (-12, 0, 0),
}
for bone, deg in LEGS.items():
    M.pose(S[bone], deg)
for side in ('right', 'left'):  # soles flat on the sand: the foot undoes the leg's and the root's pitch
    M.pose(S[f'{side}_foot'], (LEAN - LEGS[f'{side}_leg'][0] - LEGS[f'{side}_shin'][0], 0, 0))
# The hunch: the body and the hip hoods (which hang from their own root bone, `hips`) tip forward together about the
# hip axis, so the abdomen stays seated in the hoods; turning the torso alone pushed the abdomen into them.
for bone in ('body', 'hips'):
    M.pose(S[bone], (-HUNCH, 0, 0))
M.update()

yaw = math.radians(YAW)
FWD = Vector((-math.sin(yaw), math.cos(yaw), 0))    # its forward and right on the floor (no lean)
RIGHT = Vector((math.cos(yaw), math.sin(yaw), 0))
UP = Vector((0, 0, 1))
CAM = Vector((0.1, -3.7, 0.3))  # down at the sand, close: the suit towers over the viewer
LOOK, LENS = Vector((0.1, 0, 1.8)), 22

drill_meshes = meshes(S['drill'])
fpiv = M.world(S['left_forearm'])
far = max((o.matrix_world @ Vector(c) for o in drill_meshes for c in o.bound_box), key=lambda p: (p - fpiv).length)
tip = M.marker('drill_tip', S['left_forearm'], (0, 0, 0))
tip.matrix_world = M.Matrix.Translation(far)  # the bit's point, wherever the geo puts it

# The pads are rigid on the upper arms and sit against the collar: an upper arm can swing forward (even up past
# horizontal) but barely out to the side (15 deg out pushed the pad 1+ px into the collar). So the arms reach by swinging
# forward and bending at the elbow, and the out-to-side angles stay within 5 deg.
if POSE == 'cover':
    # Drill raised: the upper arm forward to about level, the forearm up, the bit high beside the helmet.
    M.pose(S['left_arm'], (ARM_DRILL, 5, 0))
    el = M.world(S['left_forearm'])
    M.aim(S['left_forearm'], tip, el + UP * 1.0 - RIGHT * 0.55 + FWD * 0.1)  # up and out: the cone against the water
    # Fist thrust at the viewer beside the helmet, at about head height.
    M.pose(S['right_arm'], (ARM_FIST, -3, 0))
    el = M.world(S['right_forearm'])
    M.aim(S['right_forearm'], S['right_hand'], el + (CAM + Vector((-0.9, 0, 1.4)) - el).normalized())
else:
    M.pose(S['left_arm'], (ARM_GUARD, 5, 0))
    M.aim(S['left_forearm'], tip, CAM + Vector((DRILL_OFF, 0, DRILL_UP)))
    M.pose(S['right_arm'], (25, -3, 0))
    el = M.world(S['right_forearm'])
    M.aim(S['right_forearm'], S['right_hand'], el + FWD * 0.6 + RIGHT * 0.1 - UP * 0.5)

# The helmet looks straight at us: point a line from the neck through the face at the lens.
face = M.marker('face', S['head'], (0, 20, 0))
M.aim(S['head'], face, CAM + Vector((0, 0, 0.3)))


# Both feet on the floor: bend the back knee (its foot kept flat) until both soles are at the same height.
def sole(side):
    M.update()
    return min((o.matrix_world @ Vector(c)).z for o in meshes(S[f'{side}_foot']) for c in o.bound_box)


# The back leg pushes: its knee stays nearly straight and the thigh swings back until the foot meets the floor.
BACK_KNEE = -12
M.pose(S['left_shin'], (BACK_KNEE, 0, 0))
lo, hi = LEAN, LEAN - 70.0
for _ in range(20):
    k = (lo + hi) / 2
    M.pose(S['left_leg'], (k, LEGS['left_leg'][1], 0))
    M.pose(S['left_foot'], (LEAN - k - BACK_KNEE, 0, 0))
    if sole('left') < sole('right'):
        lo = k  # the back foot still reaches lower: swing it further back
    else:
        hi = k
print(f'back thigh {k - LEAN:.1f} deg from the lean')
# Stand on the floor: drop the suit until its lowest foot touches z = 0.
root.location.z -= min(sole('left'), sole('right'))
M.update()
for b in ('left_foot', 'right_foot'):
    lowz = sorted((o.matrix_world @ Vector(c)).z for o in meshes(S[b]) for c in o.bound_box)[:4]
    print(f'{b}: sole z {lowz[0]:.3f}..{lowz[-1]:.3f}')  # the four lowest corners: equal = flat sole
bu = bvh(UPPER())
ou = bvh.owner
bl = bvh(LOWER())
clip = set(bu.overlap(bl)) - REST_CLIP
from collections import Counter  # noqa: E402
print(f'clip: {len(clip)} new face pairs between the upper body and the hips/thighs',
      Counter((ou[a], bvh.owner[b]) for a, b in clip).most_common(8))
bh = bvh(HOODS())
oh = bvh.owner
clip2 = set(bh.overlap(bvh(THIGHS()))) - REST_CLIP2
print(f'clip: {len(clip2)} new face pairs between the hoods/pelvis and the thighs',
      Counter((oh[a], bvh.owner[b]) for a, b in clip2).most_common(6))
ba = bvh(ARMS(), 0.5 * M.PX)
oa = bvh.owner
clip3 = set(ba.overlap(bvh(CHEST(), 0.5 * M.PX))) - REST_CLIP3
print(f'clip: {len(clip3)} new face pairs between the arms and the chest/helmet (> 1 px deep)',
      Counter((oa[a], bvh.owner[b]) for a, b in clip3).most_common(6))
hf = S['head'].matrix_world.to_3x3() @ Vector((0, 1, 0))
print(f'head looks {math.degrees(hf.angle(CAM - M.world(S["head"]))):.0f} deg off the lens')

# ------------------------------------------------------------------ the sea floor and the monument
rnd = random.Random(5)
cells = {}


def put(x, y, z, t):
    cells[(x, y, z)] = t


def noise(x, y, scale, seed):
    """Smooth value noise (0..1) for the dunes."""
    import math as m
    def h(i, j):
        return random.Random(i * 7919 + j * 104729 + seed).random()
    fx, fy = x / scale, y / scale
    i, j = m.floor(fx), m.floor(fy)
    u, v = fx - i, fy - j
    u, v = u * u * (3 - 2 * u), v * v * (3 - 2 * v)
    return (h(i, j) * (1 - u) + h(i + 1, j) * u) * (1 - v) + (h(i, j + 1) * (1 - u) + h(i + 1, j + 1) * u) * v


# Sand, rising into low dunes away from the suit (the near floor stays flat: the feet stand on it).
for x in range(-34, 35):
    for y in range(-10, 60):
        rise = 0 if y < 5 else int(noise(x, y, 7, 1) * min(3, (y - 5) / 4) + 0.25)
        for z in range(-1, rise):
            put(x, y, z, 'gravel' if z == rise - 1 and y > 6 and rnd.random() < 0.05 else 'sand')

# The ocean monument, Minecraft's sunken palace: a wide prismarine facade in three tiers with corner towers, dark
# prismarine pillars standing out of it, sea lanterns on the pillars and tower tops, and a deep gate in the middle lit
# from inside, right behind the suit (its doorway frames it).
FY = 34  # front face of the facade
MX = 11  # the facade's middle: its gate shows to the right of the suit, under the drill


def floor_at(x, y):
    z = 0
    while (x, y, z) in cells:
        z += 1
    return z


for x in range(-24, 25):
    for y in range(FY, FY + 14):
        z0 = -1
        top = 9 if abs(x) > 14 else 15 if abs(x) > 7 else 20
        if 18 <= abs(x) <= 23:
            top = 16  # towers
        for z in range(z0, top):
            t = 'prismarine_bricks'
            if z == top - 1:
                t = 'prismarine'
            if (abs(x) - 18) % 5 == 0 and abs(x) >= 18 and z == top - 1:
                t = 'sea_lantern'  # tower corners
            put(MX + x, y, z, t)
# Pillars in front of the facade, every 4 blocks, with a lantern band.
for x in range(-22, 23, 4):
    if abs(x) <= 4:
        continue
    top = 9 if abs(x) > 14 else 15
    for z in range(0, top):
        put(MX + x, FY - 1, z, 'sea_lantern' if z in (3, top - 2) else 'dark_prismarine')
# The gate: a 7 x 8 opening cut 5 blocks deep, its frame in dark prismarine, lanterns on the back wall.
for x in range(-3, 4):
    for z in range(0, 8):
        for y in range(FY, FY + 5):
            cells.pop((MX + x, y, z), None)
for x in range(-4, 5):
    put(MX + x, FY - 1, 8, 'dark_prismarine')
for z in range(0, 8):
    put(MX - 4, FY - 1, z, 'dark_prismarine')
    put(MX + 4, FY - 1, z, 'dark_prismarine')
for x in (-2, 0, 2):
    put(MX + x, FY + 5, 2, 'sea_lantern')
    put(MX + x, FY + 5, 5, 'sea_lantern')
# Upper tier windows: lantern slots.
for x in (-11, -9, 9, 11):
    put(MX + x, FY - 1, 12, 'sea_lantern')

# Ocean ruins in the middle distance: broken stone-brick walls and a fallen pillar, half buried, for depth.
for (x0, y0, w, h, d) in ((-12, 14, 4, 3, 1), (9, 18, 3, 4, 1), (-7, 22, 2, 2, 2)):
    for dx in range(w):
        for z in range(h - (dx % 2) - (1 if dx == w - 1 else 0)):
            for dy in range(d):
                put(x0 + dx, y0 + dy, floor_at(x0 + dx, y0 + dy) + 0 if z == 0 else z + floor_at(x0, y0) - 1,
                    rnd.choice(['stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks']))
for dx in range(4):  # fallen pillar
    put(13 + dx, 11, floor_at(13 + dx, 11), 'chiseled_stone_bricks' if dx == 0 else 'stone_bricks')

mats = {n: B.material(n) for n in set(cells.values())}
mats['sea_lantern'] = B.material('sea_lantern', emission=5)
B.blocks('floor_and_wall', cells, mats)

# Lights inside the gate glow out into the water.
M.light('POINT', 'gate_light', (MX, FY + 3, 3.5), (0, 0, 0), 2500, (0.6, 0.95, 1.0), size=2)

items = []
corals = ['tube_coral', 'brain_coral', 'horn_coral', 'fire_coral', 'bubble_coral',
          'tube_coral_fan', 'brain_coral_fan', 'fire_coral_fan']
for _ in range(320):
    x, y = rnd.randint(-26, 26), rnd.randint(-4, 30)
    if abs(x) < 3 and -3 < y < 3 or y < -2 or (x, y, floor_at(x, y)) in cells:
        continue  # keep clear of the suit's feet and out of the lens
    z = floor_at(x, y)
    r = rnd.random()
    if r < 0.13 and abs(x) > 5:
        items.append((x, y, z, ('kelp_plant', 'kelp'), rnd.randint(5, 16)))
    elif r < 0.25 and y > 6:
        items.append((x, y, z, rnd.choice(corals), 1))
    else:
        items.append((x, y, z, 'seagrass', 1))
B.plants('plants', items)

# Bubbles from the tidal pack, rising.
M.update()
pack = [o for o in [S['pack_tidal']] + list(S['pack_tidal'].children_recursive) if o.type == 'MESH']
pts = [o.matrix_world @ Vector(c) for o in pack for c in o.bound_box]
top = Vector((sum(p.x for p in pts) / len(pts), sum(p.y for p in pts) / len(pts), max(p.z for p in pts)))
bubbles = [top + Vector((rnd.uniform(-0.25, 0.25), rnd.uniform(-0.2, 0.2), rnd.uniform(0, 3.5))) for _ in range(26)]
B.sprites('bubbles', bubbles, 'bubble', facing=CAM, size=0.14, emission=0.8)  # catch the light like real bubbles

# ------------------------------------------------------------------ water, light, camera
# The sea: a volume around everything scatters the light blue-green and swallows the distance; light comes from the
# surface far above (a soft sun straight down) and from the sea lanterns, the visor and the Heart of the Sea.
water = M.bpy.data.objects.new('water', M.bpy.data.meshes.new('water'))
M.bpy.context.scene.collection.objects.link(water)
import bmesh  # noqa: E402
bm = bmesh.new()
bmesh.ops.create_cube(bm, size=1.0)
bm.to_mesh(water.data)
water.scale = (110, 130, 28)  # from below the floor up to the surface (SURFACE_Z): above it there is no water to glow
water.location = (0, 35, 12)
wm = M.bpy.data.materials.new('water')
wm.use_nodes = True
nt = wm.node_tree
for n in list(nt.nodes):
    if n.type != 'OUTPUT_MATERIAL':
        nt.nodes.remove(n)
vol = nt.nodes.new('ShaderNodeVolumePrincipled')
vol.inputs['Color'].default_value = (0.04, 0.15, 0.18, 1)  # dark: the water only glows where light crosses it
vol.inputs['Density'].default_value = 0.03
vol.inputs['Anisotropy'].default_value = 0.55  # water scatters forward: shafts glow when the light is ahead of us
vol.inputs['Absorption Color'].default_value = (0.12, 0.45, 0.58, 1)  # red goes first, then green: deep blue-green
nt.links.new(vol.outputs['Volume'], nt.nodes['Material Output'].inputs['Volume'])
water.data.materials.append(wm)
# The surface far above, as a roof with a few holes placed so that the sun comes down in shafts where we want them: one
# on the suit (a top light from behind), a few in the distance, and none on the floor in front (it stays in shadow).
SUN = Vector((0.15, 0.35, 1.0))  # from behind and high: the shafts come down steeply, leaning toward us
SURFACE_Z = 26
HOLES = [ ((9, 18, 0), 4), ((-9, 24, 0), 3), ((17, 28, 6), 3), ((3, 44, 10), 5), ((-20, 40, 4), 4)]
sd = SUN.normalized()
holes = []
for target, size in HOLES:
    t = (SURFACE_Z - target[2]) / sd.z
    holes.append((Vector(target) + sd * t, size / 2))
verts, faces = [], []
CELL = 2
for i in range(-60, 60):
    for j in range(-40, 80):
        x0, y0 = i * CELL, j * CELL
        cx, cy = x0 + CELL / 2, y0 + CELL / 2
        if any(abs(cx - h.x) < r and abs(cy - h.y) < r for h, r in holes):
            continue
        b = len(verts)
        verts += [(x0, y0, SURFACE_Z), (x0 + CELL, y0, SURFACE_Z), (x0 + CELL, y0 + CELL, SURFACE_Z), (x0, y0 + CELL, SURFACE_Z)]
        faces.append((b, b + 1, b + 2, b + 3))
surface = M.bpy.data.objects.new('surface', M.bpy.data.meshes.new('surface'))
surface.data.from_pydata(verts, [], faces)
M.bpy.context.scene.collection.objects.link(surface)
surface.visible_camera = False

M.sky_world(zenith=(0.03, 0.16, 0.24), horizon=(0.01, 0.05, 0.08), strength=AMBIENT, camera_strength=0.4)
M.light('SUN', 'surface_sun', SUN * 30, (0, 0, 0), SUN_STRENGTH, (0.7, 0.95, 1.0), angle=0.5)
# A warm key from the front left (a lamp out of frame): keeps the brown paint brown against the blue-green water.
key = M.light('SPOT', 'key', (-3.0, -4.0, 3.5), (0, 0, 1.6), KEY, (1.0, 0.8, 0.55), size=0.6)
key.data.spot_size = M.math.radians(34)  # on the suit, not the floor in front (that stays in the surface's shadow)
key.data.spot_blend = 0.6
# The suit's own shaft (a spot, so its pool of light on the sand can be dimmer than the sun's): from above and behind,
# it rims the helmet and pads and lights a circle of sand round the feet.
# It hangs just under the surface, far above the frame (lower down, its cone's bright tip showed in the water).
top = M.light('SPOT', 'top', Vector((0.1, 0.4, 0)) + sd * ((SURFACE_Z - 0.5) / sd.z), (0.1, 0.4, 0), TOP,
              (0.75, 0.95, 1.0), size=0.5)
top.data.spot_size = M.math.radians(7.5)
top.data.spot_blend = 0.5
# A cool fill from low beside the lens (the gate's glow): the drill levelled at us must not sink into shadow.
M.light('AREA', 'fill', (1.6, -4.0, 1.0), (0.6, -1.0, 1.6), FILL, (0.55, 0.9, 1.0), size=1.5)
rim = M.light('AREA', 'rim', (3.0, 4.0, 4.0), (0, 0, 1.8), 300, (0.55, 1.0, 0.95), size=2)
rim.data.use_shadow = False  # its shadow fell toward the camera and lifted the feet off the floor

M.cycles(res=(1600, 2000), preview=PREVIEW, cpu='--cpu' in args)
M.bpy.context.scene.cycles.volume_bounces = 1
if '--pct' in args:
    M.bpy.context.scene.render.resolution_percentage = int(args[args.index('--pct') + 1])
cam = M.camera(CAM, LOOK, lens=LENS)
# Centre the suit: turn the camera until the line from the helmet down to between the feet sits in the middle of the
# frame (the arms reach out to both sides, so the body is the anchor, not the bounding box).
from bpy_extras.object_utils import world_to_camera_view  # noqa: E402
sc = M.bpy.context.scene
mid = (M.world(S['head']) + (M.world(S['left_foot']) + M.world(S['right_foot'])) / 2) / 2
for _ in range(4):
    M.update()
    off = world_to_camera_view(sc, cam, mid).x - 0.5
    LOOK = LOOK + cam.matrix_world.to_3x3() @ Vector((1, 0, 0)) * off * (LOOK - CAM).length * 0.8
    M.look_at(cam, LOOK)
M.update()
print('suit centre on screen x', round(world_to_camera_view(sc, cam, mid).x, 3))
M.depth_of_field(cam, M.world(S['head']), fstop=4)
if '--views' in args:
    water.hide_render = True
    M.debug_views(OUT.replace('.png', ''), (0, 0, 1.4))
elif '--check' not in args:
    M.render(OUT)
