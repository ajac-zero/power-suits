"""Builds the cockpit viewmodel (what the pilot sees of the suit's arms) from the suit model, so it stays in sync
with Blockbench:

    python3 tools/extract-view-geo.py        (after node tools/export-model.mjs)

Two roots, each moved so its joint sits on the origin (CockpitHands places them in front of the camera):
  view_tool      the tool arm's forearm (the shared gauntlet, always shown), every socket tool (its own 7x1x7 socket
                 base + head, with its moving parts: drill_bit, chainsaw_chain, the chaingun's barrels); the empty
                 socket is the coupling painted on the gauntlet's underside (the socket bone is empty). Hangs down -Y
                 from the socket like on the suit.
  view_gauntlet  the right wrist, hand and fingers (the empty main hand; held items are vanilla's); hangs down -Y
                 from the elbow.
Trimmed for the first-person view: the elbow joints sat right in front of the camera as big blocks, so they're left
out (the gauntlet's elbow cuff on the right).
Bone names match the suit, so the suit's animation file drives the tools' moving parts here too.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/powerarmor'
SUIT = ASSETS / 'geckolib/models/entity/power_armor.geo.json'
OUT = ASSETS / 'geckolib/models/view/power_armor_view.geo.json'

# root name: (bone whose cubes it takes, which of them (a test on the cube, in the bone's own coordinates), bone the
#             cubes go to, top-level bones re-parented under the root, joint to centre on)
# Since the 2026-10-19 export the forearms are a short gauntlet block, ramps and a back plate up to 1 px under the
# elbow, a 7x5x7 fist, and the elbow joint (box + arcs, reaching up past the elbow's pivot): the joint pieces are the
# cubes whose top comes within ELBOW_GAP of the elbow, the old size filter no longer told them apart.
ELBOW_GAP = 0.5


def below_elbow(bone):
    top = bone['pivot'][1] - ELBOW_GAP
    return lambda c: c['origin'][1] + c['size'][1] <= top


ROOTS = {
    # The tools: each brings its own 7x1x7 socket base (in its bone); none of the shared forearm (it would fill the
    # screen corner the tool reaches in from).
    'view_tool': ('left_forearm', lambda bone: (lambda c: False), None, ['socket', 'chaingun', 'em_projector', 'drill', 'chainsaw', 'flamethrower', 'tnt_launcher', 'rail_cannon'], 'socket'),
    # The empty main hand: the forearm armour + fist, not the elbow joint.
    'view_gauntlet': ('right_forearm', below_elbow, None, ['right_hand'], 'right_forearm'),
}


def main():
    geo = json.loads(SUIT.read_text())['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}
    out = [{'name': 'view', 'pivot': [0, 0, 0]}]
    for root, (cube_bone, which, cubes_to, tops, joint) in ROOTS.items():
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

        test = which(bones[cube_bone])
        own = moved([c for c in bones[cube_bone].get('cubes', []) if test(c)])
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
