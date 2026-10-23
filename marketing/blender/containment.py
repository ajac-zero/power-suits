"""Key art: "Containment". A Metroid nod: an orange suit with the Hunter helmet (Samus's), the star reactor and the
EM projector hauls an escaped big slime (the Hunter recipe's "Metroid") back toward its broken containment tank in
the cargo bay of a spaceship, a Minecraft world turning in the window behind.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/containment.py -- [--preview] [--pct N]
        [--views] [--check] [--crop x0,y0,x1,y1] [--cpu] [--out path]  (+ the pose/camera numbers below)

--check only poses: prints the foot heights and the clipping checks. --views: orthographic checks of the pose.

The EM beam is the game's (EmBeamEffects): two strands of redstone dust (vanilla redstone red and a hotter orange-red)
twisting round the line from the projector's prongs to the middle of what it holds, sparks at the prongs and dust
swirling round the held thing. Held things float 3.5 blocks out from the projector's tip (EmProjectorItem).

World: the camera looks roughly along +Y (screen right ~ +X, up = +Z), the floor's top at z = 0. Poses: see mclib.
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
OUT = args[args.index('--out') + 1] if '--out' in args else os.path.join(M.MARKETING, 'out', 'containment_raw.png')


def arg(name, default):
    return type(default)(args[args.index(name) + 1]) if name in args else default


M.reset()
UP = Vector((0, 0, 1))
rnd = random.Random(23)
RES = (1600, 2000) if '--portrait' in args else (2400, 1600)

# ------------------------------------------------------------------ the suit
# Orange (Samus's power suit), the Hunter helmet's visor lit amber (a pilot inside), the star reactor glowing on its
# back, the projector's redstone lens and prong tips lit (it is holding something: SuitGlowLayer's em_core + em_arc).
# --visor green (Samus's visor; in the game: a friendly mind drives) or amber (a pilot inside).
VISOR = {'amber': M.VISOR_AMBER, 'green': M.VISOR_GREEN}[args[args.index('--visor') + 1] if '--visor' in args else 'green']
root, S = M.import_suit('hunter', paint='orange', tool='em_projector', helmet='helm_hunter', engine='pack_reactor',
                        glow=[('visor', VISOR), 'reactor', 'em_core', 'em_arc'], glow_strength=arg('--glow', 1.2))

CHECKS = M.suit_clip_checks(S)

# The projector's tip: the middle of its four prongs' far ends (found at rest, then carried by the forearm).
M.update()
prongs = [o for o in M.meshes(S['em_projector']) if o.name.startswith('em_prong')]
pv = [o.matrix_world @ v.co for o in prongs for v in o.data.vertices]
lens = sum((o.matrix_world @ v.co for o in M.meshes(S['em_projector']) if o.name.startswith('em_lens')
            for v in o.data.vertices), Vector())
lens /= sum(len(o.data.vertices) for o in M.meshes(S['em_projector']) if o.name.startswith('em_lens'))
farthest = max(v.y for v in pv)
TIP_REST = Vector((lens.x, farthest, lens.z))
tip = M.marker('em_tip', S['left_forearm'], (0, 0, 0))
tip.matrix_world = Matrix.Translation(TIP_REST)

# ------------------------------------------------------------------ camera
CAM = Vector((arg('--cx', -0.2), -arg('--dist', 6.0), arg('--cz', 2.0)))
LENS = arg('--lens', 18.0)
LOOK = Vector((arg('--lookx', 1.9), 0, arg('--lookz', 2.9)))


# The stance: braced, the near (right) leg forward, the far (left, projector) leg back, the whole suit leaning back a
# little against the weight. The camera stands CAM_ANGLE round from its front toward its right (fist) side, so the
# projector is on the far side and its beam goes out to the right of the frame without crossing the body.
CAM_ANGLE = arg('--camangle', 12.0)
YAW = 180 + CAM_ANGLE
LEAN = arg('--lean', -3.0)
HUNCH = arg('--hunch', 2.0)
root.rotation_euler = M.rad((-LEAN, 0, YAW))
yaw = math.radians(YAW)
FWD = Vector((-math.sin(yaw), math.cos(yaw), 0))
RIGHT = Vector((math.cos(yaw), math.sin(yaw), 0))

LEGS = {
    'right_leg': (LEAN + 24, -8, 0),
    'right_shin': (-26, 0, 0),
    'left_leg': (LEAN - 20, 10, 0),
    'left_shin': (-10, 0, 0),
}
for bone, deg in LEGS.items():
    M.pose(S[bone], deg)
for side in ('right', 'left'):
    M.pose(S[f'{side}_foot'], (LEAN - LEGS[f'{side}_leg'][0] - LEGS[f'{side}_shin'][0], 0, 0))
TWIST = arg('--twist', 12.0)  # the upper body turns toward the slime (the game's torso twist, legs stay)
for bone in ('body', 'hips'):
    M.pose(S[bone], (-HUNCH, 0, 0))
M.pose(S['torso'], (0, 0, TWIST))
M.update()

# Where the slime hangs (world, blocks): in front of the suit and to its left (screen right), a little closer to the
# lens than the suit, high. The game holds a target's feet 3.5 blocks out from the tip and aims at its middle; the
# distance printed below should stay near 3.5 + 1.
SLIME = Vector((arg('--sx', 4.3), -arg('--sy', 1.9), arg('--sz', 4.3)))
M.pose(S['left_arm'], (arg('--arm', 70.0), 4, 0))
SIZE = 4  # the biggest natural slime: 2.04 blocks wide
M.aim(S['left_forearm'], tip, SLIME)
M.aim(S['left_forearm'], tip, SLIME)  # (again: the tip sits off the forearm's pivot, so one turn is close, not exact)
TIP = M.world(tip)
print(f'hold distance {(SLIME - TIP).length:.2f} (game: 3.5 to the feet, ~4.5 to the middle)')
# The free fist: clenched by the hip, braced.
M.pose(S['right_arm'], (arg('--fistarm', -25.0), -4, 0))
el = M.world(S['right_forearm'])
M.aim(S['right_forearm'], S['right_hand'], el + FWD * 0.05 - UP * 0.6 + RIGHT * 0.08)
# The head follows the slime, turned HEADCAM of the way toward the lens so the visor reads.
face = M.marker('face', S['head'], (0, 20, 0))
hp = M.world(S['head'])
HC = arg('--headcam', 0.6)
M.aim(S['head'], face, hp + ((SLIME - hp).normalized() * (1 - HC) + (CAM - hp).normalized() * HC) * 20)


def sole(side):
    M.update()
    return min((o.matrix_world @ Vector(c)).z for o in M.meshes(S[f'{side}_foot']) for c in o.bound_box)


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
drop = min(sole('left'), sole('right'))
root.location.z -= drop
M.update()
TIP, SLIME = TIP - UP * drop, SLIME - UP * drop
for b in ('left_foot', 'right_foot'):
    lowz = sorted((o.matrix_world @ Vector(c)).z for o in M.meshes(S[b]) for c in o.bound_box)[:4]
    print(f'{b}: sole z {lowz[0]:.3f}..{lowz[-1]:.3f}')
for c in CHECKS:
    c.report()
print('tip', [round(v, 2) for v in TIP], 'slime', [round(v, 2) for v in SLIME])

# ------------------------------------------------------------------ helpers
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


def tinted_sprite_material(name, tex, color, strength, kind='particle'):
    """A particle texture (white with alpha, like the game's dust) tinted `color` and glowing: Minecraft tints dust
    sprites by vertex colour; here the texture's grey is multiplied by the colour and emitted."""
    m = M.bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type != 'OUTPUT_MATERIAL':
            nt.nodes.remove(n)
    tx = nt.nodes.new('ShaderNodeTexImage')
    tx.image = B.texture(tex, kind)
    tx.interpolation = 'Closest'
    mul = nt.nodes.new('ShaderNodeMix')
    mul.data_type = 'RGBA'
    mul.blend_type = 'MULTIPLY'
    mul.inputs['Factor'].default_value = 1.0
    nt.links.new(tx.outputs['Color'], mul.inputs['A'])
    mul.inputs['B'].default_value = (*color, 1)
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Strength'].default_value = strength
    nt.links.new(mul.outputs['Result'], em.inputs['Color'])
    tr = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(tx.outputs['Alpha'], mix.inputs['Fac'])
    nt.links.new(tr.outputs[0], mix.inputs[1])
    nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
    return m


def sprites(name, items, mat_list):
    """Camera-facing squares: items = (centre, size in blocks, material index)."""
    verts, polys, uvs, mi = [], [], [], []
    for p, size, k in items:
        p = Vector(p)
        n = (CAM - p).normalized()
        r = n.cross(UP).normalized() * (size / 2)
        u = r.cross(n).normalized() * (size / 2)
        b = len(verts)
        verts += [p - r + u, p - r - u, p + r - u, p + r + u]
        polys.append((b, b + 1, b + 2, b + 3))
        uvs += [(0, 1), (0, 0), (1, 0), (1, 1)]
        mi.append(k)
    me = M.bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name='UV')
    for i, uv in enumerate(uvs):
        layer.data[i].uv = uv
    for m in mat_list:
        me.materials.append(m)
    for poly, k in zip(me.polygons, mi):
        poly.material_index = k
    ob = M.bpy.data.objects.new(name, me)
    M.bpy.context.scene.collection.objects.link(ob)
    ob.visible_shadow = False
    return ob


# ------------------------------------------------------------------ the slime
# Vanilla's slime model (SlimeModel + SlimeOuterLayer, 64x32 texture): an opaque 6 px inner cube with two eyes and a
# mouth on its front, inside a translucent 8 px jelly cube. Model space is Minecraft's (y down, front at -z): here x is
# flipped (its right on +X), front = +Y, up = +Z, centred on the cube's middle. Scaled by its size (4), stretched a
# little as the game stretches a slime in the air.
slime_img = B.texture('slime/slime', kind='entity')
inner_mat = M.pixel_material('slime_inner', slime_img, roughness=0.35, specular=0.4)
outer_mat = M.pixel_material('slime_outer', slime_img, roughness=0.12, specular=0.6)
ob = outer_mat.node_tree.nodes['Principled BSDF']
# Jelly: the texture's alpha (180/255) keeps the game's see-through look; a little transmission makes the light glow
# through it instead of the cube reading as frosted plastic.
ob.inputs['Transmission Weight'].default_value = arg('--jelly', 0.0)
_nt = outer_mat.node_tree
_am = _nt.nodes.new('ShaderNodeMath')  # thinner jelly than the texture's 180/255, so the face inside reads
_am.operation = 'MULTIPLY'
_am.inputs[1].default_value = arg('--jellyalpha', 0.7)
_nt.links.new(next(n for n in _nt.nodes if n.type == 'TEX_IMAGE').outputs['Alpha'], _am.inputs[0])
_nt.links.new(_am.outputs[0], ob.inputs['Alpha'])
slime = M._empty('slime', None, (0, 0, 0))
slime.location = SLIME


def mc_box(name, origin, size, uv, inflate=0.0, mat=None):
    """A box given in Minecraft model space (origin = min corner, y down, front -z) under the slime's centre."""
    x0, y0, z0 = origin
    w, h, d = size
    return M._box(name, (-x0 - w, -z0 - d, 24 - y0 - h - 4), (w, h, d), uv, (64, 32), inflate, mat)


for nm, o, s, uv, mat in (('slime_cube', (-3, 17, -3), (6, 6, 6), (0, 16), inner_mat),
                          ('slime_eye_r', (-3.25, 18, -3.5), (2, 2, 2), (32, 0), inner_mat),
                          ('slime_eye_l', (1.25, 18, -3.5), (2, 2, 2), (32, 4), inner_mat),
                          ('slime_mouth', (0, 21, -3.5), (1, 1, 1), (32, 8), inner_mat),
                          ('slime_jelly', (-4, 16, -4), (8, 8, 8), (0, 0), outer_mat)):
    mc_box(nm, o, s, uv, mat=mat).parent = slime
STRETCH = arg('--stretch', 1.12)
slime.scale = (SIZE / STRETCH ** 0.5, SIZE / STRETCH ** 0.5, SIZE * STRETCH)
# It faces the lens (eyes and mouth read), turned SLIMEYAW toward the suit and rolled a little: tumbling in the field.
look = (CAM - SLIME).normalized()
q = look.to_track_quat('Y', 'Z')
q = q @ Quaternion(Vector((0, 0, 1)), math.radians(arg('--slimeyaw', -15.0)))
q = q @ Quaternion(Vector((0, 1, 0)), math.radians(arg('--slimeroll', -10.0)))
slime.rotation_mode = 'QUATERNION'
slime.rotation_quaternion = q
M.light('POINT', 'slime_glow', SLIME, SLIME, arg('--slimeglow', 25.0), (0.45, 1.0, 0.35), size=0.6)

# ------------------------------------------------------------------ the beam (EmBeamEffects)
# Each game tick lays two strands of dust along a helix (radius 0.18 sin(pi t), 9 rad of twist over the beam) that
# spins 0.6 rad per tick; dust lives 1-2 s, so what you see is the newest helix bright and a few older ones fading
# round it, each drifting a little. Strand 0 is vanilla's redstone red, strand 1 a hotter orange-red.
line = SLIME - TIP
length = line.length
d = line / length
side = d.cross(UP).normalized()
up2 = side.cross(d)
STRAND = [(0.9, 0.03, 0.02), (1.0, 0.25, 0.08)]
dust = [tinted_sprite_material(f'dust_{i}_{k}', f'generic_{g}', STRAND[k], s)
        for i, (g, s) in enumerate(((5, 1.6), (4, 1.2), (3, 0.9))) for k in (0, 1)]
items = []
GENS = arg('--gens', 8)
steps = int(length * 2.5 * arg('--dense', 2.5))
for g in range(GENS):
    spin = -0.6 * g * arg('--genstep', 1.0) / arg('--dense', 2.5)
    age = g / max(1, GENS - 1)
    for i in range(steps + 1):
        t = i / steps
        r = 0.18 * math.sin(math.pi * t)
        for k in (0, 1):
            if rnd.random() < age * 0.45:
                continue  # older dust has started to die off
            a = spin + t * 9 + k * math.pi
            p = TIP + line * t + side * math.cos(a) * r + up2 * math.sin(a) * r
            p += Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))) * 0.025 * (1 + 2 * age)
            size = rnd.uniform(0.13, 0.2) * (1 - 0.3 * age)
            items.append((p, size, 2 * min(2, int(age * 3)) + k))
# Sparks crackling between the prong tips (small, hot) and now and then along the beam.
SPARK = [tinted_sprite_material('spark', 'generic_2', (1.0, 0.45, 0.25), 8.0)]
for _ in range(10):
    items.append((TIP + Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))) * 0.12, rnd.uniform(0.05, 0.08), 6))
for _ in range(6):
    items.append((TIP + line * rnd.random() + Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))) * 0.1,
                  rnd.uniform(0.05, 0.07), 6))
# Dust swirling round the slime (radius 0.7 x its width).
for _ in range(arg('--swirl', 26)):
    a = rnd.uniform(0, 2 * math.pi)
    rr = SIZE * 0.51 * 0.7 * rnd.uniform(0.95, 1.2)
    items.append((SLIME + Vector((math.cos(a) * rr, math.sin(a) * rr, rnd.gauss(0, 1) * SIZE * 0.51 * 0.3)),
                  rnd.uniform(0.09, 0.14), rnd.choice((0, 2, 2, 4))))
sprites('em_beam', items, dust + SPARK)
# The lightning-rod crack at the prongs (vanilla's electric spark: the glow sprite, nearly white).
sprites('em_crack', [(TIP + d * 0.05, 0.22, 0)], [tinted_sprite_material('crack', 'glow', (1.0, 0.9, 1.0), 10.0)])
for t in (0.0, 0.5, 1.0):
    M.light('POINT', f'beam_{t}', TIP + line * t, TIP, arg('--beamlight', 60.0) * (1.6 if t == 0 else 1), (1.0, 0.18, 0.08), size=0.3)

# Slime drips falling off it (vanilla's item_slime particles: bits of the slime ball texture).
drips = []
for _ in range(9):
    a = rnd.uniform(0, 2 * math.pi)
    rr = rnd.uniform(0.2, 1.0)
    drips.append((SLIME + Vector((math.cos(a) * rr, math.sin(a) * rr, -SIZE * 0.5 * STRETCH * 0.5 - rnd.uniform(0.1, 2.2))),
                  rnd.uniform(0.08, 0.13), 0))
dm = M.pixel_material('drip', B.texture('slime_ball', 'item'), roughness=0.2, specular=0.5)
sprites('slime_drips', drips, [dm])

if '--check' in args:
    sys.exit(0)

# ------------------------------------------------------------------ the ship's cargo bay
cells = {}


def put(x, y, z, t):
    cells[(x, y, z)] = t


# A low bay (7 high inside): deck plates of polished deepslate with iron seams across every 5 blocks, a dark ceiling
# with iron beams and sea-lantern light strips running fore and aft, oxidized copper walls with grate bands.
X0, X1, Y0, Y1, H = -10, 12, -9, 9, 7   # inside: x X0..X1-1, y Y0..Y1-1, z 0..H-1
for x in range(X0 - 1, X1 + 1):
    for y in range(Y0 - 1, Y1 + 1):
        put(x, y, -1, 'smooth_stone' if y % 5 == 0 else 'polished_deepslate')
        put(x, y, H, 'polished_tuff' if y % 5 == 0 else 'sea_lantern' if x in (-2, 6) else 'deepslate_tiles')
for z in range(0, H):
    for x in range(X0 - 1, X1 + 1):
        put(x, Y1, z, 'oxidized_cut_copper' if z not in (0, H - 1) else 'polished_deepslate')
        put(x, Y0 - 1, z, 'oxidized_cut_copper')
    for y in range(Y0 - 1, Y1 + 1):
        for x in (X0 - 1, X1):
            t = 'polished_deepslate' if y % 5 == 0 or z in (0, H - 1) else 'oxidized_copper_grate' if z in (2, 3) else 'oxidized_cut_copper'
            put(x, y, z, t)
# The viewport behind: one wide window of glass between iron mullions, from knee height to near the ceiling.
WX0, WX1, WZ0, WZ1 = -8, 11, 1, 6
for x in range(WX0, WX1):
    for z in range(WZ0, WZ1):
        put(x, Y1, z, 'polished_deepslate' if (x - WX0) % 5 == 4 else 'glass')
# The containment tank the slime broke out of, back left: a copper pedestal ringed by hazard stripes, glass walls with
# the front smashed out, a copper cap and a feed pipe, red alarm lamps either side on the back wall.
TX, TY = arg('--tankx', -5), arg('--tanky', 6)
for x in range(TX - 2, TX + 3):
    for y in range(TY - 2, TY + 3):
        if abs(x - TX) == 2 or abs(y - TY) == 2:
            put(x, y, -1, 'yellow_concrete' if (x + y) % 2 == 0 else 'black_concrete')
for x in range(TX - 1, TX + 2):
    for y in range(TY - 1, TY + 2):
        put(x, y, 0, 'oxidized_chiseled_copper')
        put(x, y, 4, 'oxidized_chiseled_copper')
for z in range(1, 4):
    for x in range(TX - 1, TX + 2):
        for y in range(TY - 1, TY + 2):
            if (x, y) == (TX, TY):
                continue
            broken = y == TY - 1 or (x == TX + 1 and z == 1)
            if not broken:
                put(x, y, z, 'glass')
put(TX - 1, TY - 1, 1, 'glass')  # a stump of the smashed front
for z in range(5, H):
    put(TX, TY, z, 'oxidized_copper_grate')
for x in (TX - 2, TX + 2):
    put(x, Y1, 4, 'redstone_lamp_on')
# Cargo along the right wall: iron crates, a vault and crafters, for scale.
for (x, y, z, t) in ((X1 - 1, 0, 0, 'vault_side_off'), (X1 - 1, 1, 0, 'crafter_west'), (X1 - 1, 1, 1, 'crafter_west'),
                     (X1 - 2, 5, 0, 'iron_block'), (X1 - 1, 5, 0, 'iron_block'), (X1 - 1, 5, 1, 'copper_grate'),
                     (X1 - 1, 6, 0, 'iron_block'), (X1 - 2, 6, 0, 'copper_grate'), (X1 - 1, 4, 0, 'iron_block'),
                     (X0, 6, 0, 'iron_block'), (X0, 7, 0, 'iron_block'), (X0, 7, 1, 'iron_block')):
    put(x, y, z, t)

mats = {n: B.material(n) for n in set(cells.values())}
mats['sea_lantern'] = B.material('sea_lantern', emission=arg('--lantern', 2.5))
mats['redstone_lamp_on'] = B.material('redstone_lamp_on', emission=5)
for n in ('iron_block', 'oxidized_cut_copper', 'polished_deepslate', 'deepslate_tiles', 'oxidized_chiseled_copper'):
    mats[n].node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.45  # ship metal: a dull sheen
mats['glass'].node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.05
B.blocks('cargo_bay', cells, mats)
for x in (-2, 6):
    for y in (-6, 0, 6):
        M.light('AREA', f'strip_{x}_{y}', (x + 0.5, y, H - 0.05), (x + 0.5, y, 0), arg('--strip', 70.0), (0.75, 0.9, 1.0), size=1)
for x in (TX - 2, TX + 2):
    M.light('POINT', f'alarm_{x}', (x + 0.5, Y1 - 0.6, 4.5), (x, 0, 2), 80, (1.0, 0.15, 0.08), size=0.4)

# ------------------------------------------------------------------ space outside the window
# A Minecraft world seen from orbit (blocky continents, ocean, ice caps, clouds, lit from one side), Minecraft's square
# sun, and sparse square stars (the user found starry skies busy: few and faint).
N = 96
planet_img = M.bpy.data.images.new('planet', N, N, alpha=True)


def vnoise(x, y, scale, seed):
    def h(i, j):
        return random.Random(i * 7919 + j * 104729 + seed).random()
    fx, fy = x / scale, y / scale
    i, j = math.floor(fx), math.floor(fy)
    u, v = fx - i, fy - j
    u, v = u * u * (3 - 2 * u), v * v * (3 - 2 * v)
    return (h(i, j) * (1 - u) + h(i + 1, j) * u) * (1 - v) + (h(i, j + 1) * (1 - u) + h(i + 1, j + 1) * u) * v


LIGHT = Vector((-0.6, 0.45, 0.66)).normalized()  # where the sun lights it from (as seen from the ship)
px = []
for j in range(N):
    for i in range(N):
        x, y = (i + 0.5) / N * 2 - 1, (j + 0.5) / N * 2 - 1
        r2 = x * x + y * y
        if r2 > 1:
            atm = max(0.0, 1 - (math.sqrt(r2) - 1) / 0.04)
            px += [0.35 * atm, 0.6 * atm, 1.0 * atm, atm]
            continue
        z = math.sqrt(1 - r2)
        land = vnoise(i, j, 9, 5) * 0.65 + vnoise(i, j, 4, 6) * 0.35
        lat = abs(y)
        if lat > 0.9:
            c = (0.92, 0.95, 1.0)
        elif land > 0.56:
            c = (0.86, 0.78, 0.5) if (lat < 0.3 and vnoise(i, j, 6, 8) > 0.55) else (0.33, 0.55, 0.2)
            if vnoise(i, j, 3, 9) > 0.7:
                c = (0.22, 0.42, 0.14)
        else:
            c = (0.12, 0.27, 0.62) if land > 0.48 else (0.08, 0.18, 0.5)
        if vnoise(i + 40, j, 5, 11) > 0.72:
            c = (0.95, 0.97, 1.0)
        lit = max(0.0, Vector((x, y, z)).dot(LIGHT))
        lit = 0.04 + 0.96 * min(1.0, lit * 1.4)
        lit = round(lit * 6) / 6  # banded light, like the game's smooth lighting steps
        px += [c[0] * lit, c[1] * lit, c[2] * lit, 1.0]
planet_img.pixels = px
planet_img.pack()
PLANET_DIR = Vector((arg('--planetx', 0.36), 1.0, arg('--planetz', -0.28))).normalized()
PLANET_SIZE = arg('--planet', 680.0)
pl = M.square_sun(PLANET_DIR, distance=900, size=PLANET_SIZE)
pl.name = 'planet'
pm = M.bpy.data.materials.new('planet')
pm.use_nodes = True
nt = pm.node_tree
for n in list(nt.nodes):
    if n.type != 'OUTPUT_MATERIAL':
        nt.nodes.remove(n)
tx = nt.nodes.new('ShaderNodeTexImage')
tx.image = planet_img
tx.interpolation = 'Closest'
em = nt.nodes.new('ShaderNodeEmission')
em.inputs['Strength'].default_value = arg('--planetglow', 1.0)
nt.links.new(tx.outputs['Color'], em.inputs['Color'])
mix = nt.nodes.new('ShaderNodeMixShader')
nt.links.new(tx.outputs['Alpha'], mix.inputs['Fac'])
nt.links.new(nt.nodes.new('ShaderNodeBsdfTransparent').outputs[0], mix.inputs[1])
nt.links.new(em.outputs[0], mix.inputs[2])
nt.links.new(mix.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
pl.data.materials.clear()
pl.data.materials.append(pm)
pl.location = CAM + PLANET_DIR * 900
layer = pl.data.uv_layers.new(name='UV')
for i, uv in enumerate([(0, 0), (1, 0), (1, 1), (0, 1)]):
    layer.data[i].uv = uv

stars = M.bpy.data.meshes.new('stars')
verts, faces = [], []
R = 1500
for _ in range(arg('--stars', 500)):
    az, el = rnd.uniform(math.radians(20), math.radians(160)), math.asin(rnd.uniform(-0.5, 0.9))
    n = Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
    s = R * math.radians(rnd.choice((0.06, 0.08, 0.08, 0.1, 0.14)))
    a = UP.cross(n).normalized()
    b = n.cross(a)
    c = CAM + n * R
    k = len(verts)
    verts += [c + (a + b) * s / 2, c + (a - b) * s / 2, c + (-a - b) * s / 2, c + (-a + b) * s / 2]
    faces.append((k, k + 1, k + 2, k + 3))
stars.from_pydata(verts, [], faces)
stars.materials.append(emission_material('stars', (0.85, 0.9, 1.0), arg('--starglow', 2.0)))
so = M.bpy.data.objects.new('stars', stars)
M.bpy.context.scene.collection.objects.link(so)
so.visible_shadow = False
so.visible_diffuse = False

# ------------------------------------------------------------------ light
M.sky_world(zenith=(0.003, 0.004, 0.012), horizon=(0.006, 0.008, 0.02), strength=arg('--amb', 0.2), camera_strength=1.0)
# Sunlight through the window (from behind, high left): rims the suit and the slime, throws the window's frame shadows.
SUN = Vector((-0.5, 1.0, 0.45)).normalized()
M.light('SUN', 'sun', SUN * 50, (0, 0, 0), arg('--sun', 3.0), (1.0, 0.95, 0.85), angle=0.6)
# A cool key from the front left (a light panel out of frame) and a soft warm fill so the orange reads orange.
M.light('AREA', 'key', (-4.5, -5.5, 4.5), (0, 0, 2.0), arg('--key', 420.0), (0.85, 0.92, 1.0), size=3)
M.light('AREA', 'fill', (2.5, -6.5, 1.5), (0.5, 0, 2.0), arg('--fill', 90.0), (1.0, 0.8, 0.6), size=2)

M.cycles(res=RES, preview=PREVIEW, cpu='--cpu' in args)
if '--pct' in args:
    M.bpy.context.scene.render.resolution_percentage = int(args[args.index('--pct') + 1])
cam = M.camera(CAM, LOOK, lens=LENS)
M.depth_of_field(cam, M.world(S['body']), fstop=arg('--fstop', 8.0))
from bpy_extras.object_utils import world_to_camera_view  # noqa: E402
sc = M.bpy.context.scene
M.update()
hf = (M.world(face) - M.world(S['head'])).normalized()
print('head looks', [round(v, 2) for v in hf], f'{math.degrees(hf.angle(SLIME - M.world(S["head"]))):.0f} deg off the slime,',
      f'{math.degrees(hf.angle(CAM - M.world(S["head"]))):.0f} off the lens')
for nm, p in (('head', M.world(S['head']) + UP * 0.3), ('feet', (M.world(S['left_foot']) + M.world(S['right_foot'])) / 2),
              ('tip', TIP), ('slime top', SLIME + UP * 1.2), ('slime bottom', SLIME - UP * 1.2),
              ('slime right', SLIME + Vector((1.2, 0, 0)))):
    c = world_to_camera_view(sc, cam, p)
    print(f'FRAME {nm}: ({c.x:.2f}, {c.y:.2f})')
if '--frame' in args:
    sys.exit(0)
if '--crop' in args:
    sc = M.bpy.context.scene
    sc.render.use_border = True
    sc.render.use_crop_to_border = True
    sc.render.border_min_x, sc.render.border_min_y, sc.render.border_max_x, sc.render.border_max_y = \
        [float(v) for v in args[args.index('--crop') + 1].split(',')]
if '--views' in args:
    for o in M.bpy.data.objects:  # the pose alone: the bay's walls would block the side views
        if o.name in ('cargo_bay', 'stars', 'planet'):
            o.hide_render = True
    M.debug_views(OUT.replace('.png', ''), (1.5, 0, 2.2), dist=12)
else:
    M.render(OUT)
