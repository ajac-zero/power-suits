"""Appends a vanilla Steve in full iron armour (Minecraft's own HumanoidModel boxes + armour deformations, textures
from the jar) to a draft .bbmodel, standing at x = OFFSET. Usage: python3 vanilla_ref.py draft.bbmodel out.bbmodel"""
import json, sys, uuid, base64, glob, zipfile, os

OFFSET = -22
jar = sorted(glob.glob(os.path.expanduser('~/Developer/PowerSuits/build/moddev/artifacts/minecraft-patched-*.jar')))
jar = [j for j in jar if 'sources' not in j][-1]
z = zipfile.ZipFile(jar)
def tex(name, path, i):
    import struct
    data = z.read(path); w, h = struct.unpack('>II', data[16:24])   # from the PNG header
    return {'name': name, 'id': str(i), 'width': w, 'height': h, 'uv_width': w, 'uv_height': h, 'uuid': str(uuid.uuid4()),
            'render_mode': 'default', 'render_sides': 'double', 'internal': True, 'saved': False, 'visible': True,
            'particle': False, 'folder': '', 'namespace': '', 'path': '', 'group': '', 'scope': 0, 'file_format': 'png',
            'source': 'data:image/png;base64,' + base64.b64encode(data).decode()}
d = json.load(open(sys.argv[1]))
t0 = len(d['textures'])
SKIN, IRON, LEGS = t0, t0 + 1, t0 + 2
d['textures'] += [tex('vanilla_steve.png', 'assets/minecraft/textures/entity/player/wide/steve.png', SKIN),
                  tex('vanilla_iron.png', 'assets/minecraft/textures/entity/equipment/humanoid/iron.png', IRON),
                  tex('vanilla_iron_leggings.png', 'assets/minecraft/textures/entity/equipment/humanoid_leggings/iron.png', LEGS)]
def boxuv(u, v, w, h, dd):
    return {'east': [u, v + dd, u + dd, v + dd + h], 'north': [u + dd, v + dd, u + dd + w, v + dd + h],
            'west': [u + dd + w, v + dd, u + 2 * dd + w, v + dd + h], 'south': [u + 2 * dd + w, v + dd, u + 2 * dd + 2 * w, v + dd + h],
            'up': [u + dd + w, v + dd, u + dd, v], 'down': [u + dd + 2 * w, v, u + dd + w, v + dd]}
def mirror(f):
    r = lambda q: [q[2], q[1], q[0], q[3]]
    return {'east': r(f['west']), 'west': r(f['east']), 'north': r(f['north']), 'south': r(f['south']),
            'up': r(f['up']), 'down': r(f['down'])}
els, kids = [], []
def box(name, center_x, y0, size, uvbox, t, inflate=0.0, mir=False):
    w, h, dd = size
    a = [OFFSET + center_x - w / 2 - inflate, y0 - inflate, -dd / 2 - inflate]
    b = [OFFSET + center_x + w / 2 + inflate, y0 + h + inflate, dd / 2 + inflate]
    f = boxuv(*uvbox)
    if mir: f = mirror(f)
    e = {'name': name, 'box_uv': False, 'from': a, 'to': b, 'origin': [OFFSET, 0, 0], 'type': 'cube', 'uuid': str(uuid.uuid4()),
         'faces': {k: {'uv': q, 'texture': t} for k, q in f.items()}, 'visibility': True, 'export': True, 'autouv': 0,
         'color': 3, 'locked': False, 'render_order': 'default', 'allow_mirror_modeling': True}
    els.append(e); kids.append(e['uuid'])
# Steve (right = +x, like the frame's model), feet at y 0
box('v_head', 0, 24, (8, 8, 8), (0, 0, 8, 8, 8), SKIN)
box('v_body', 0, 12, (8, 12, 4), (16, 16, 8, 12, 4), SKIN)
box('v_arm_r', 6, 12, (4, 12, 4), (40, 16, 4, 12, 4), SKIN)
box('v_arm_l', -6, 12, (4, 12, 4), (32, 48, 4, 12, 4), SKIN)
box('v_leg_r', 1.9, 0, (4, 12, 4), (0, 16, 4, 12, 4), SKIN)
box('v_leg_l', -1.9, 0, (4, 12, 4), (16, 48, 4, 12, 4), SKIN)
# armour: HumanoidModel.createArmorMeshSet(inner 0.5, outer 1.0); legs get extend(-0.1)
box('v_iron_helmet', 0, 24, (8, 8, 8), (0, 0, 8, 8, 8), IRON, 1.0)
box('v_iron_chest', 0, 12, (8, 12, 4), (16, 16, 8, 12, 4), IRON, 1.0)
box('v_iron_arm_r', 6, 12, (4, 12, 4), (40, 16, 4, 12, 4), IRON, 1.0)
box('v_iron_arm_l', -6, 12, (4, 12, 4), (40, 16, 4, 12, 4), IRON, 1.0, mir=True)
box('v_iron_legs_body', 0, 12, (8, 12, 4), (16, 16, 8, 12, 4), LEGS, 0.5)
box('v_iron_legs_r', 1.9, 0, (4, 12, 4), (0, 16, 4, 12, 4), LEGS, 0.4)
box('v_iron_legs_l', -1.9, 0, (4, 12, 4), (0, 16, 4, 12, 4), LEGS, 0.4, mir=True)
box('v_iron_boot_r', 1.9, 0, (4, 12, 4), (0, 16, 4, 12, 4), IRON, 0.9)
box('v_iron_boot_l', -1.9, 0, (4, 12, 4), (0, 16, 4, 12, 4), IRON, 0.9, mir=True)
g = {'name': 'ref_vanilla_iron', 'uuid': str(uuid.uuid4()), 'export': False, 'origin': [OFFSET, 0, 0], 'rotation': [0, 0, 0],
     'visibility': True, 'children': [], 'isOpen': False, 'color': 5}
d['elements'] += els; d['groups'].append(g)
d['outliner'].append({'uuid': g['uuid'], 'isOpen': False, 'children': kids})
json.dump(d, open(sys.argv[2], 'w'), indent=1)
print(len(els), 'vanilla cubes ->', sys.argv[2])
