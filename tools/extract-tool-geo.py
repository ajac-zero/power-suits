"""Builds the hand-held model of a socket tool from the suit model, so the tool looks the same in your
hand as on the suit's arm and stays in sync with Blockbench.

    python3 tools/extract-tool-geo.py        (after node tools/export-model.mjs)

For each tool: copy its bone subtree (+ extra cubes: the elbow socket ring the tool locks onto), move it
so its bounding box is centred on the origin (GeckoLib already moves item models to the middle of the block),
and put it under one root bone. How it sits in your hand / GUI is set in models/item/<tool>.json.
The suit carries its tools on the LEFT forearm; on foot you hold them in your right hand, so the copy is mirrored
back to the right-arm version (the same shape the item always had).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/powerarmor'
SUIT = ASSETS / 'geo/entity/power_armor.geo.json'

TOOLS = {
    # tool name: (bone subtree, extra bone whose cubes of these sizes are included, bones to drop)
    'plasma_cannon': ('cannon', ('left_forearm', [[7, 1, 7]]), {'muzzle_flash'}),
    'em_projector': ('em_projector', ('left_forearm', [[7, 1, 7]]), set()),
    'drill': ('drill', ('left_forearm', [[7, 1, 7]]), set()),
    'chainsaw': ('chainsaw', ('left_forearm', [[7, 1, 7]]), set()),
}


def mirrored(bone):
    """The bone mirrored across x = 0 (left forearm -> right forearm): x flipped, Y/Z rotations negated, box UV
    mirrored (Blockbench's 'mirror' flag), exactly like the model's own left/right pairs."""
    b = json.loads(json.dumps(bone))
    b['pivot'][0] = -b['pivot'][0]
    if 'rotation' in b:
        b['rotation'] = [b['rotation'][0], -b['rotation'][1], -b['rotation'][2]]
    for c in b.get('cubes', []):
        c['origin'][0] = -(c['origin'][0] + c['size'][0])
        if 'pivot' in c:
            c['pivot'][0] = -c['pivot'][0]
        if 'rotation' in c:
            c['rotation'] = [c['rotation'][0], -c['rotation'][1], -c['rotation'][2]]
        if c.pop('mirror', False) is False:
            c['mirror'] = True
    return b


def main():
    suit = json.loads(SUIT.read_text())
    geo = suit['minecraft:geometry'][0]
    bones = {b['name']: mirrored(b) for b in geo['bones']}
    for tool, (subtree, (extra_bone, extra_sizes), drop) in TOOLS.items():
        keep = [subtree]
        changed = True
        while changed:
            changed = False
            for b in geo['bones']:
                if b.get('parent') in keep and b['name'] not in keep and b['name'] not in drop:
                    keep.append(b['name'])
                    changed = True
        extra = [c for c in bones[extra_bone].get('cubes', []) if c['size'] in extra_sizes]
        cubes = extra + [c for n in keep for c in bones[n].get('cubes', [])]
        lo = [min(c['origin'][i] for c in cubes) for i in range(3)]
        hi = [max(c['origin'][i] + c['size'][i] for c in cubes) for i in range(3)]
        shift = [-(lo[0] + hi[0]) / 2, -(lo[1] + hi[1]) / 2, -(lo[2] + hi[2]) / 2]
        move = lambda p: [round(p[i] + shift[i], 4) for i in range(3)]

        def moved_cubes(cs):
            out = []
            for c in cs:
                c = json.loads(json.dumps(c))
                c['origin'] = move(c['origin'])
                if 'pivot' in c:
                    c['pivot'] = move(c['pivot'])
                out.append(c)
            return out

        out_bones = [{'name': tool + '_root', 'pivot': [0, 0, 0], 'cubes': moved_cubes(extra)}]
        for n in keep:
            b = json.loads(json.dumps(bones[n]))
            b['pivot'] = move(b['pivot'])
            b['parent'] = tool + '_root' if n == subtree else b['parent']
            b['cubes'] = moved_cubes(b.get('cubes', []))
            b.pop('rotation', None) if n == subtree else None
            out_bones.append(b)
        desc = dict(geo['description'], identifier=f'geometry.{tool}', visible_bounds_width=2,
                    visible_bounds_height=2, visible_bounds_offset=[0, 0.5, 0])
        out = {'format_version': suit['format_version'], 'minecraft:geometry': [{'description': desc, 'bones': out_bones}]}
        path = ASSETS / f'geo/item/{tool}.geo.json'
        path.write_text(json.dumps(out, indent=1))
        print(f'wrote {path.relative_to(ROOT)}: {len(cubes)} cubes, size {[hi[i] - lo[i] for i in range(3)]}')


main()
