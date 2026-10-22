"""Key art pilot: "Man of Tomorrow" homage (mirrored). A Superman-skinned player uppercuts Lex Luthor, who pilots a
lime-painted, helmetless power suit (his own head in the collar, like the game's PilotHeadLayer) with the chainsaw
and the Nether Star reactor on his back.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/man_of_tomorrow.py -- [--preview] [--out path]

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
# High above the clouds at golden hour: Minecraft's square sun behind Superman's shoulder rims both fighters, a warm
# key from the front left lights the faces, the sky fills the shadows blue.
SUN_DIR = (0.22, 1.0, 0.4)
M.sky_world(zenith=(0.10, 0.27, 0.75), horizon=(1.0, 0.8, 0.58), strength=0.45, camera_strength=1.0,
            nadir=(0.32, 0.48, 0.72))
M.square_sun(SUN_DIR, size=30, strength=5, color=(1.0, 0.8, 0.35))
M.light('SUN', 'sun', Vector(SUN_DIR) * 10, (0, 0, 0), 4.5, (1.0, 0.82, 0.55), angle=2)
M.light('AREA', 'key', (-3.2, -4.5, 4.2), (-0.4, 0, 1.8), 700, (1.0, 0.94, 0.86), size=3)
M.light('AREA', 'fill', (3.5, -4, 0.5), (0, 0, 1.6), 300, (0.7, 0.8, 1.0), size=5)
# Backdrop variants (--bg): far (picked: the cloud sea far below breaks into small blocks, Minecraft but quiet);
# near = big close clouds (too busy behind the fighters); blur = near, out of focus (barely softens); clean = sky only.
BG = args[args.index('--bg') + 1] if '--bg' in args else 'far'
FSTOP = {'blur': 1.0, 'far': 2.8}.get(BG, 4)
if BG in ('near', 'blur'):
    M.clouds('clouds_low', -40, center=(0, 200), extent=560, seed=3, density=0.42)
elif BG == 'far':
    M.clouds('clouds_low', -160, center=(0, 600), extent=1700, seed=3, density=0.42)
M.clouds('clouds_high', 70, center=(0, 330), extent=520, seed=11, density=0.4, alpha=0.6, clear=(SUN_DIR, 9))
M.cycles(res=(2400, 1600), preview=PREVIEW, cpu='--cpu' in args)
if PREVIEW:
    M.bpy.context.scene.render.resolution_percentage = 50
cam = M.camera((0.6, -5.9, 4.6), (-0.3, 0, 1.86), lens=33)  # from above: Lex's head in the collar reads
M.depth_of_field(cam, CLASH, fstop=FSTOP)
if '--views' in args:
    M.debug_views(OUT.replace('.png', ''), (0, 0, 1.5))
else:
    M.render(OUT)
