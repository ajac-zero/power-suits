"""Key art pilot: "Man of Tomorrow" homage (mirrored). A Superman-skinned player uppercuts Lex Luthor, who pilots a
lime-painted, helmetless power suit (his own head in the collar, like the game's PilotHeadLayer) with the chainsaw
and the Nether Star reactor on his back.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/man_of_tomorrow.py -- [--preview] [--out path]
        [--bg white|sky|city|far|near|blur|clean]

The final picture (2026-10-23) is --bg white: the comic cover's pure white background, lit flat, finished with
`post.py raw.png out.png 0 0 --white`. The other backdrops stay for other uses (sky: the metropolis far below).

World: camera on -Y looking +Y (screen right = +X, up = +Z). Poses: degrees, see mclib's docstring.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mclib as M  # noqa: E402

args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
PREVIEW = '--preview' in args
OUT = args[args.index('--out') + 1] if '--out' in args else os.path.join(M.MARKETING, 'out', 'man_of_tomorrow.png')
SKINS = os.path.join(M.MARKETING, 'skins')

M.reset()

# The moment before contact: Lex lunges in from the left, Superman dives in from the right, their fists about to meet
# at CLASH (no overlapping parts to hide, and the gap carries the tension).
from mathutils import Vector  # noqa: E402

CLASH = Vector((0.0, -0.25, 2.0))
GAP = 0.3  # half the gap between the two fists

# ------------------------------------------------------------------ Lex in the suit (left)
lex_root, S = M.import_suit('lex', paint='lime', tool='chainsaw', helmet=None, engine='pack_reactor', glow='reactor')
M.player_head('lex', os.path.join(SKINS, 'lex_luthor.png'), S['pilot_head'])
lex_root.location = (-1.25, 0.35, 0.05)
lex_root.rotation_euler = M.rad((0, 0, -128))
for bone, deg in {
    'body': (-20, 0, 0),          # leaning into the leap
    'torso': (-8, 0, 12),         # right shoulder driven forward behind the punch
    'head': (6, 0, -22),          # eyes on Superman (and toward us)
    'left_leg': (55, 0, 0),       # a leap, not a step: front knee tucked up...
    'left_shin': (-75, 0, 0),
    'right_leg': (-70, 0, 0),     # ...back leg (the near one) stretched out behind from the push-off
    'right_shin': (-10, 0, 0),
}.items():
    M.pose(S[bone], deg)
# The hand is drawn back at his hip (a cocked punch) instead of reaching across his chest, so the armour shows.
LEX_BACK = Vector((-0.79, 0.62, 0))  # opposite his facing (yaw -128)
sh = M.world(S['right_arm'])
M.aim(S['right_arm'], S['right_forearm'], sh + LEX_BACK * 0.5 + Vector((0, 0, -0.5)))
M.aim(S['right_forearm'], S['right_hand'], sh + LEX_BACK * 0.15 + Vector((0, -0.2, -0.75)))

# ------------------------------------------------------------------ Superman (right), diving fist first
sup = M.player('sup', os.path.join(SKINS, 'superman.png'), cape=M.cape_material('sup'))
sup['root'].rotation_euler = M.rad((-55, 0, 102))   # body nearly flat, heading for Lex and a little toward the camera
M.update()
toward = Vector((-1.0, -0.05, -0.2)).normalized()  # the punch direction: at Lex's fist
M.aim(sup['right_arm'], sup['right_hand'], M.world(sup['right_arm']) + toward)   # punching arm
hand = M.world(sup['right_hand']) - M.world(sup['root'])
sup['root'].location = CLASH + Vector((GAP, 0, 0)) - hand
M.update()
hips = M.world(sup['root'])
back = (hips - M.world(sup['neck'])).normalized()   # along the body, toward the feet
M.aim(sup['left_arm'], sup['left_hand'], M.world(sup['left_arm']) + back + Vector((0, -0.25, -0.1)))  # pulled back at his side
M.aim(sup['right_leg'], sup['right_foot'], hips + back * 0.75 + Vector((0, 0.08, -0.08)))
M.aim(sup['left_leg'], sup['left_foot'], hips + back * 0.75 + Vector((0.05, -0.18, 0.06)))
M.pose_part(sup['head'], (62, 0, 36))  # chin up: looking ahead while flat
for hinge, deg in zip(sup['cape'], [(-4, 6, 0), (16, 2, 6), (-18, -3, -6), (16, -6, 8)]):
    M.pose_part(hinge, deg)              # streaming back, rippling

# ------------------------------------------------------------------ the chainsaw, raised overhead to chop down at him
blade_tip = M.marker('lex_blade_tip', S['chainsaw'], (0, 0, -16))  # the end of the blade (geo: y 25.6 -> 9.6)
M.aim(S['left_arm'], S['left_forearm'], M.world(S['left_arm']) + Vector((0.12, 0.2, 0.62)))   # raised overhead
M.aim(S['left_forearm'], blade_tip, M.world(sup['head']) + Vector((-0.15, 0.25, 0.95)))  # about to chop down

# ------------------------------------------------------------------ light, sky, camera
# Backdrop variants (--bg): white (the pick, 2026-10-23: like the reference cover, the fighters alone on white, lit
# flat); sky (a clear sky with clouds above, the metropolis far below at a tilt, hard sun from the south-west); far
# (the first pick: the cloud sea far below, quiet but bland next to the other pictures); near = big close clouds;
# blur = near, out of focus; clean = sky only; city = the metropolis close under the fight.
BG = args[args.index('--bg') + 1] if '--bg' in args else 'white'
# On white everything is sharp, like a drawing (a blurred edge on white reads as a smudge).
FSTOP = float(args[args.index('--fstop') + 1]) if '--fstop' in args else {'blur': 1.0, 'far': 2.8, 'white': 64}.get(BG, 2.8)
CAM_LOC, CAM_AT = (0.6, -5.9, 4.6), (-0.3, 0, 1.86)


def num(name, default):
    return float(args[args.index(name) + 1]) if name in args else default


ZENITH, HORIZON, NADIR = (0.10, 0.27, 0.75), (1.0, 0.8, 0.58), (0.32, 0.48, 0.72)
SUNMODE = 'key'
if BG == 'white':
    # The comic cover's look: the fighters alone on pure white, lit flat like a drawing. The render's background is
    # transparent and post.py --white lays it on white (a white world comes out light grey through AgX). No sun and
    # no lamps (the user's pick B): an even white world lights them from all round, so colours read as flat areas.
    # --sun N / --key N / --fill N bring lights back.
    SUN_DIR = (-0.55, -0.7, 0.5)
    M.sky_world(zenith=(1, 1, 1), horizon=(1, 1, 1), strength=num('--ambient', 1.0), camera_strength=1.0)
    if '--sun' in args:
        M.light('SUN', 'sun', Vector(SUN_DIR) * 10, (0, 0, 0), num('--sun', 10.0), (1.0, 0.95, 0.85), angle=1.5)
elif BG == 'sky':
    # The fighters keep their framing and the world turns round the camera instead (mclib.env_tilt): pitched so the
    # horizon comes into the frame (clear sky and clouds above, the city below) and rolled for a tilted horizon.
    import metropolis as City  # noqa: E402
    import math  # noqa: E402
    TILT = M.env_tilt(CAM_LOC, CAM_AT, pitch=num('--pitch', 12.0), roll=num('--roll', -10.0))
    VIEW = TILT.to_3x3().inverted()   # a real view direction -> the tilted world's
    env = M.bpy.data.objects.new('env', None)
    M.bpy.context.scene.collection.objects.link(env)
    env.matrix_world = TILT
    SUNMODE = args[args.index('--sunmode') + 1] if '--sunmode' in args else 'key'
    if SUNMODE == 'rim':
        # Golden hour, the sun low over the horizon to the right, in the frame behind Superman: it rims both hard.
        el, az = math.radians(num('--sunel', 7.0)), math.radians(num('--sunaz', 14.0))
        SUN_DIR = (math.sin(az) * math.cos(el), math.cos(az) * math.cos(el), math.sin(el))
        sun_col, city_col, disc = (1.0, 0.8, 0.5), (1.0, 0.75, 0.48), num('--sundisc', 30.0)
    else:
        # A clear midday sky, the sun behind the camera's left shoulder (south-west, with the view facing north): a
        # hard key on both fighters. From the west (the left) its glare washed out the chainsaw and Superman's face;
        # from straight behind the camera everything came out flat. Between the two, the faces toward us are lit and
        # the sides fall into shadow. The direction is given as seen from the camera and turned into the tilted world.
        SUN_DIR = tuple(VIEW @ Vector((num('--sunx', -0.55), num('--suny', -0.7), num('--sunz', 0.5))).normalized())
        ZENITH, HORIZON, NADIR = (0.04, 0.2, 0.8), (0.5, 0.7, 1.0), (0.42, 0.56, 0.8)
        sun_col, city_col, disc = (1.0, 0.95, 0.85), (1.0, 0.95, 0.85), 0.0
    M.sky_world(zenith=ZENITH, horizon=HORIZON, strength=0.45, camera_strength=1.0, nadir=NADIR, rotation=VIEW)
    backdrop = [M.light('SUN', 'sun', Vector(SUN_DIR) * 10, (0, 0, 0), num('--sun', 10.0), sun_col, angle=1.5)]
    if disc:
        backdrop.append(M.square_sun(SUN_DIR, distance=900, size=60, strength=disc, color=(1.0, 0.85, 0.5)))
    ALT = num('--alt', 320.0)
    GRID = math.radians(num('--grid', 35.0))

    def on_ground(u, v):
        """The street-grid point (the city's own coordinates) seen at frame point (u, v) (0..1 from the bottom
        left): places downtown and the Daily Planet by where they should show, whatever the tilt."""
        rot = (Vector(CAM_AT) - Vector(CAM_LOC)).to_track_quat('-Z', 'Y').to_matrix()
        d = VIEW @ (rot @ Vector(((u - 0.5) * 36, (v - 0.5) * 24, -33)))
        o = TILT.inverted() @ Vector(CAM_LOC)
        p = o + d * ((-ALT - o.z) / d.z)
        return tuple(M.Matrix.Rotation(-GRID, 3, 'Z') @ Vector((p.x, p.y, 0)))[:2]

    pu, pv = (float(c) for c in (args[args.index('--planetuv') + 1] if '--planetuv' in args else '0.2,0.3').split(','))
    du, dv = (float(c) for c in (args[args.index('--downuv') + 1] if '--downuv' in args else '0.55,0.3').split(','))
    PLANET = (*on_ground(pu, pv), -ALT + num('--ph', 190.0))
    city = City.city(ground=-ALT, extent=(-1600, 1600, -600, 2600), max_top=-ALT + num('--top', 260.0), planet=PLANET,
                     downtown=on_ground(du, dv), spread=num('--spread', 350.0), peak=num('--peak', 240.0),
                     haze=num('--haze', 2500.0), sky=(ZENITH, HORIZON, NADIR),
                     nadir_at=num('--nadirat', 0.41), rotation=VIEW)
    grid = M.bpy.data.objects.new('grid', None)  # the street grid turned so the avenues run across the frame
    M.bpy.context.scene.collection.objects.link(grid)
    grid.rotation_euler = (0, 0, GRID)
    for ob in city:
        ob.parent = grid
    backdrop.append(grid)
    backdrop.append(City.city_sun(city, SUN_DIR, num('--citysun', 2.5), city_col))
    # Clouds above: a sparse layer a little over the fighters and far out, so it lies along the horizon as wisps
    # (higher or nearer, the clouds overhead were big slabs and a busy ceiling of chips).
    backdrop.append(M.clouds('clouds_high', num('--cloudz', 30.0), center=(0, 2600), extent=3600, cell=32, seed=11,
                             density=num('--clouds', 0.3), alpha=0.9, clear=(SUN_DIR, 6)))
    for ob in backdrop:
        ob.parent = env
else:
    # High above the clouds at golden hour: Minecraft's square sun behind Superman's shoulder rims both fighters.
    SUN_DIR = (0.22, 1.0, 0.4)
    M.sky_world(zenith=ZENITH, horizon=HORIZON, strength=0.45, camera_strength=1.0, nadir=NADIR)
    M.square_sun(SUN_DIR, size=30, strength=5, color=(1.0, 0.8, 0.35))
    M.light('SUN', 'sun', Vector(SUN_DIR) * 10, (0, 0, 0), 4.5, (1.0, 0.82, 0.55), angle=2)
    if BG == 'city':
        import metropolis as City  # noqa: E402
        ALT = num('--alt', 180.0)
        PLANET = (num('--px', 70.0), num('--py', 300.0), -ALT + num('--ph', 150.0))
        city = City.city(ground=-ALT, max_top=-ALT + 110.0, planet=PLANET, haze=num('--haze', 850.0),
                         keep_clear=[(0, 30, num('--clear', 90.0), -ALT + 55.0)],
                         sky=(ZENITH, HORIZON, (0.42, 0.53, 0.70)), nadir_at=0.41)
        # The city gets its own, stronger golden sun (same direction, so the shadows agree): light linking keeps
        # it off the fighters.
        City.city_sun(city, SUN_DIR, num('--citysun', 6.0), (1.0, 0.72, 0.42))
    elif BG in ('near', 'blur'):
        M.clouds('clouds_low', -40, center=(0, 200), extent=560, seed=3, density=0.42)
    elif BG == 'far':
        M.clouds('clouds_low', -160, center=(0, 600), extent=1700, seed=3, density=0.42)
    M.clouds('clouds_high', 70, center=(0, 330), extent=520, seed=11, density=0.4, alpha=0.6, clear=(SUN_DIR, 9))
# A warm key from the front left lights the faces, a cool fill from the right.
# Under the hard midday sun (--bg sky) they only soften its shadows, and the armour is shinier so the sun's
# highlights read on the plates.
HARD = BG in ('sky', 'white') and SUNMODE == 'key'
FLAT = BG == 'white'
M.light('AREA', 'key', (-3.2, -4.5, 4.2), (-0.4, 0, 1.8), num('--key', 0.0 if FLAT else 150.0 if HARD else 700.0), (1.0, 0.94, 0.86), size=3)
M.light('AREA', 'fill', (3.5, -4, 0.5), (0, 0, 1.6), num('--fill', 0.0 if FLAT else 70.0 if HARD else 300.0), (0.7, 0.8, 1.0), size=5)
if '--rim' in args:  # a light from behind and above the fighters: a bright edge round them against the city
    M.light('AREA', 'rim', (1.5, 5.0, 6.0), (-0.3, 0, 2.0), num('--rim', 600.0), (1.0, 0.95, 0.85), size=4)
if HARD or '--gloss' in args:
    M.bpy.data.materials['lex_armor'].node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = num('--gloss', 0.3)
M.cycles(res=(2400, 1600), preview=PREVIEW, cpu='--cpu' in args, transparent=BG == 'white')
if '--outline' in args:  # comic ink lines round the shapes (Freestyle), as thick as --outline pixels at full size
    sc = M.bpy.context.scene
    sc.render.use_freestyle = True
    sc.render.line_thickness_mode = 'ABSOLUTE'
    sc.render.line_thickness = num('--outline', 3.0)
    ls = sc.view_layers[0].freestyle_settings.linesets[0]
    ls.select_by_visibility = True
    ls.select_crease = False      # every cube edge inked was busy: only the shapes' outlines
    ls.select_border = False
    ls.select_silhouette = True
    ls.select_contour = True
    ls.select_external_contour = True
    if ls.linestyle is None:  # a new scene's line set has no style yet
        ls.linestyle = M.bpy.data.linestyles.new('ink')
    ls.linestyle.color = (0.05, 0.05, 0.08)
if PREVIEW:
    M.bpy.context.scene.render.resolution_percentage = 50
cam = M.camera(CAM_LOC, CAM_AT, lens=num('--lens', 40.0 if BG == 'white' else 33.0))  # from above: Lex's head reads
# (on white the lens is longer: with nothing behind them the fighters fill more of the frame)
if BG in ('sky', 'city'):
    cam.data.clip_end = 100000  # the default 1000 cut the city off a kilometre out (it looked like an island)
M.depth_of_field(cam, CLASH, fstop=FSTOP)
if BG in ('city', 'sky'):  # where the Daily Planet's globe and the sun land in the frame (0..1 from the bottom left)
    from bpy_extras.object_utils import world_to_camera_view  # noqa: E402
    M.update()
    for label, name in (('planet globe', 'planet_globe'), ('sun', 'sun_disc')):
        ob = M.bpy.data.objects.get(name)
        if ob is None:
            continue
        if name == 'planet_globe':
            p = ob.matrix_world @ Vector(PLANET)  # its mesh is built at PLANET in the city's frame
        else:
            p = ob.matrix_world.translation
        print(label, 'in frame:', tuple(round(c, 3) for c in world_to_camera_view(M.bpy.context.scene, cam, p)))
if '--views' in args:
    M.debug_views(OUT.replace('.png', ''), (0, 0, 1.5))
else:
    M.render(OUT)
