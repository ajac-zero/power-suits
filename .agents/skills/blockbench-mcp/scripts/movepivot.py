"""Copy of a model with some bones' pivots moved, the rest pose unchanged (for IK sweeps before building a hinge).

A bone's world placement is parent * T(o) R T(-o); moving its pivot o -> o' with the same rest rotation R shifts
everything under it unless the bone's cubes and child bones move by d = (I - R^-1)(o' - o). The knee hinge at the
shin's back-top corner and the hip hinge at the thigh's front-top edge were both tried this way (gaitsweep on the copy:
walk knees 75-117 planted, run thigh to 98: a squat) before touching the live project.
usage: uv run --with numpy python3 movepivot.py in.json out.bbmodel BONE=y,z [BONE=y,z ...]
       (in.json: a snapshot.mjs file {model, texture} or a .bbmodel; only y/z move, x is kept)
       then: MODEL=out.bbmodel node gaitsweep.mjs spec.js '[{}]'
"""
import json, math, sys
import numpy as np
src, out, moves = sys.argv[1], sys.argv[2], dict(a.split('=') for a in sys.argv[3:])
s = json.load(open(src)); m = s['model'] if 'model' in s else s
m = json.loads(m) if isinstance(m, str) else m
def R(rot):
    x, y, z = [math.radians(v) for v in rot]
    Rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx                                    # Blockbench / Bedrock euler order ZYX
gb = {g['uuid']: g for g in m['groups']}
def find(nodes, name):
    for n in nodes:
        if isinstance(n, dict):
            if gb[n['uuid']]['name'] == name: return n
            r = find(n.get('children', []), name)
            if r: return r
def subtree(node, acc):
    for ch in node.get('children', []):
        if isinstance(ch, str): acc['cubes'].add(ch)
        else: acc['groups'].add(ch['uuid']); subtree(ch, acc)
for name, yz in moves.items():
    node = find(m['outliner'], name); g = gb[node['uuid']]; y, z = map(float, yz.split(','))
    o = np.array(g['origin']); o2 = np.array([o[0], y, z])
    d = (np.eye(3) - np.linalg.inv(R(g.get('rotation', [0, 0, 0])))) @ (o2 - o)
    acc = {'cubes': set(), 'groups': set()}; subtree(node, acc); g['origin'] = o2.tolist()
    for gg in m['groups']:
        if gg['uuid'] in acc['groups']: gg['origin'] = (np.array(gg['origin']) + d).tolist()
    for e in m['elements']:
        if e['uuid'] in acc['cubes']:
            for k in ('from', 'to', 'origin'):
                if k in e: e[k] = (np.array(e[k]) + d).tolist()
    print(name, 'pivot', o.round(3).tolist(), '->', o2.round(3).tolist(), 'subtree shift', d.round(3).tolist())
json.dump(m, open(out, 'w'))
