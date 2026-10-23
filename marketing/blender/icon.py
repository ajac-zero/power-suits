"""Mod icon: one helmet floating alone, rendered on a transparent background (icon_compose.py puts it on the badge).

    Blender -b -P marketing/blender/icon.py -- [--preview] [--helmet helm_protector] [--paint blue] [--yaw 28]
        [--pitch 8] [--cam-dist 3.2] [--lens 70] [--out path] [--cpu]

The helmet is the standard one by default (bone `helmet`). Only the helmet's own bone tree is kept: everything else of
the suit is hidden, so the picture is the helmet and nothing else.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mclib as M  # noqa: E402
from mathutils import Vector  # noqa: E402

args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []


def arg(name, default):
    return type(default)(args[args.index(name) + 1]) if name in args else default


HELMET = arg('--helmet', 'helmet')
PAINT = arg('--paint', 'blue')
PAINT = None if PAINT == 'none' else PAINT
YAW, PITCH = arg('--yaw', 28.0), arg('--pitch', 8.0)
OUT = arg('--out', os.path.join(M.MARKETING, 'out', 'icon_raw.png'))

M.reset()
root, S = M.import_suit('icon', paint=PAINT, tool=None, helmet=HELMET, engine=None,
                        glow=[('visor', M.VISOR_AMBER)], glow_strength=1.0)


def tree(o):
    yield o
    for c in o.children:
        yield from tree(c)


keep = set(tree(S[HELMET]))
for o in S.values():
    if o not in keep:
        o.hide_render = True
        o.hide_viewport = True
M.update()

# Helmet bounds in world space
pts = []
for o in keep:
    if o.type == 'MESH':
        pts += [o.matrix_world @ Vector(c) for c in o.bound_box]
lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
C = (lo + hi) / 2
size = max(hi - lo)
print('helmet centre', tuple(C), 'size', size)

# The helmet faces +Y; the camera sits in front of it (+Y), turned YAW to the side and PITCH above.
dist = arg('--cam-dist', 3.0) * size / 0.6
d = Vector((math.sin(math.radians(YAW)) * math.cos(math.radians(PITCH)),
            math.cos(math.radians(YAW)) * math.cos(math.radians(PITCH)),
            math.sin(math.radians(PITCH))))
cam = M.camera(C + d * dist, C, lens=arg('--lens', 70.0))

M.cycles(samples=160, res=(1024, 1024), transparent=True, preview='--preview' in args, cpu='--cpu' in args)
if '--preview' in args:
    import bpy
    bpy.context.scene.render.resolution_percentage = 50

# Lights: warm key from upper left, cool fill from the right, a rim from behind to separate the edge.
M.light('AREA', 'key', C + Vector((-3, 3.5, 3.5)), C, 260, color=(1.0, 0.93, 0.82), size=3.0)
M.light('AREA', 'fill', C + Vector((4, 3, 0.5)), C, 70, color=(0.7, 0.82, 1.0), size=3.0)
M.light('AREA', 'rim', C + Vector((1.5, -4, 2.5)), C, 200, color=(0.8, 0.9, 1.0), size=2.0)
import bpy  # noqa: E402
w = bpy.context.scene.world or bpy.data.worlds.new('w')
bpy.context.scene.world = w
w.use_nodes = True
bg = w.node_tree.nodes['Background']
bg.inputs['Color'].default_value = (0.5, 0.55, 0.65, 1)
bg.inputs['Strength'].default_value = 0.35
M.render(OUT)
