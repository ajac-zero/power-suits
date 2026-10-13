"""Builds the cockpit viewmodel (what the pilot sees of the suit's arms) from the suit model, so it stays in sync
with Blockbench:

    python3 tools/extract-view-geo.py        (after node tools/export-model.mjs)

Two roots, each moved so its joint sits on the origin (CockpitHands places them in front of the camera):
  view_tool      every socket tool (left forearm) with its moving parts (drill_bit, chainsaw_chain, the rivet gun's drum, piston and vent), and the
                 empty-socket stump (ring + plate + pins, only shown when nothing is installed); hangs down -Y
                 from the socket like on the suit.
  view_gauntlet  the right wrist, hand and fingers with the right_hand grip bone for the held item; hangs down -Y
                 from the elbow.
Trimmed for the first-person view: the socket ring and the gauntlet's elbow cuff sat right in front of the
camera as big blocks, so the ring only shows as part of the empty stump and the cuff is left out.
Bone names match the suit, so the suit's animation file drives the tools' moving parts here too.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/powerarmor'
SUIT = ASSETS / 'geo/entity/power_armor.geo.json'
OUT = ASSETS / 'geo/view/power_armor_view.geo.json'

# root name: (bone whose cubes it takes, size filter, keep (True) or drop (False) those sizes, bone the cubes go to,
#             top-level bones re-parented under the root, joint to centre on)
ROOTS = {
    'view_tool': ('left_forearm', [[7, 1, 7]], True, 'socket', ['socket', 'rivet_gun', 'em_projector', 'drill', 'chainsaw'], 'socket'),
    'view_gauntlet': ('right_forearm', [[8, 8, 8]], False, None, ['right_hand'], 'right_forearm'),
}


def main():
    geo = json.loads(SUIT.read_text())['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}
    out = [{'name': 'view', 'pivot': [0, 0, 0]}]
    for root, (cube_bone, sizes, keep_sizes, cubes_to, tops, joint) in ROOTS.items():
        shift = [-v for v in bones[joint]['pivot']]
        move = lambda p: [round(p[i] + shift[i], 4) for i in range(3)]

        def moved(cubes):
            res = []
            for c in cubes:
                c = json.loads(json.dumps(c))
                c['origin'] = move(c['origin'])
                if 'pivot' in c:
                    c['pivot'] = move(c['pivot'])
                res.append(c)
            return res

        own = moved([c for c in bones[cube_bone].get('cubes', []) if (c['size'] in sizes) == keep_sizes])
        out.append({'name': root, 'parent': 'view', 'pivot': [0, 0, 0], 'cubes': [] if cubes_to else own})
        keep = list(tops)
        changed = True
        while changed:
            changed = False
            for b in geo['bones']:
                if b.get('parent') in keep and b['name'] not in keep:
                    keep.append(b['name'])
                    changed = True
        for n in keep:
            b = json.loads(json.dumps(bones[n]))
            b['pivot'] = move(b['pivot'])
            if n in tops:
                b['parent'] = root
                b.pop('rotation', None)
            if 'cubes' in b:
                b['cubes'] = moved(b['cubes'])
            if n == cubes_to:
                b['cubes'] = b.get('cubes', []) + own
            out.append(b)
    desc = dict(geo['description'], identifier='geometry.power_armor_view', visible_bounds_width=3,
                visible_bounds_height=3, visible_bounds_offset=[0, 0, 0])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({'format_version': json.loads(SUIT.read_text())['format_version'],
                               'minecraft:geometry': [{'description': desc, 'bones': out}]}, indent=1))
    print(f'wrote {OUT.relative_to(ROOT)}: {len(out)} bones, {sum(len(b.get("cubes", [])) for b in out)} cubes')


main()
