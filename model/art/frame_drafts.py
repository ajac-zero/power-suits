#!/usr/bin/env python3
"""One review file for a round of frame drafts, instead of a model file per option.

Run: python3 model/art/frame_drafts.py 'LEGS=A' 'LEGS=B' 'LEGS=C ARM_SPLAY=10'   (-> /tmp/frame/draft.bbmodel)

Each argument is one option: the env settings frame_model.py builds it with (its draft knobs, os.environ.get(...)).
The option's whole model goes into a group proto_<settings> (the repo's convention for drafts: hidden proto_* groups),
with its own texture; the first option is shown, the rest hidden, so the options are flipped with the eye icon in one
Blockbench tab. Nothing here touches power_frame.bbmodel: when one is picked, its knob becomes frame_model.py's code and
frame_model.py is re-run. The file is overwritten every round (env DRAFT_OUT for another path).
To show one option from a script (review shots), risky_eval:
  Group.all.filter(g => g.name.startsWith('proto_')).forEach(g => { const v = g.name == NAME; g.visibility = v;
      g.forEachChild(c => c.visibility = v) }); Canvas.updateVisibility()
"""
import json, os, re, subprocess, sys, tempfile, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get('DRAFT_OUT', '/tmp/frame/draft.bbmodel')

def build(settings):
    env = dict(os.environ)
    for kv in settings.split():
        k, v = kv.split('=', 1)
        env[k] = v
    with tempfile.TemporaryDirectory() as d:
        env['FRAME_OUT'] = os.path.join(d, 'm.bbmodel')
        subprocess.run([sys.executable, os.path.join(HERE, 'frame_model.py')], env=env, check=True)
        return json.load(open(env['FRAME_OUT']))

def main(options):
    assert options, __doc__
    draft = None
    for i, settings in enumerate(options):
        m = build(settings)
        name = 'proto_' + re.sub(r'\W+', '_', settings).strip('_')
        shown = i == 0
        tex = m['textures'][0]
        tex.update(name=f'{name}.png', id=str(i), uuid=str(uuid.uuid4()))
        for e in m['elements']:
            e['visibility'] = shown
            for f in e['faces'].values(): f['texture'] = i
        g = {'name': name, 'uuid': str(uuid.uuid4()), 'export': False, 'locked': False, 'scope': 0, 'selected': False,
             'visibility': shown, 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'bedrock_binding': '', 'color': i % 8,
             'children': [], 'reset': False, 'shade': True, 'mirror_uv': False, 'autouv': 0, 'isOpen': False,
             'primary_selected': False}
        node = {'uuid': g['uuid'], 'isOpen': False, 'children': m['outliner']}
        if draft is None:
            # Generic format, not the model's Bedrock: Bedrock is single-texture (Blockbench points every face at the
            # first texture on load), and each option has its own texture (its plates pack differently).
            draft = dict(m, name='frame_draft', model_identifier='frame_draft', elements=[], groups=[], outliner=[], textures=[],
                         meta=dict(m['meta'], model_format='free'))
        draft['elements'] += m['elements']
        draft['groups'] += [g] + m['groups']
        draft['outliner'].append(node)
        draft['textures'].append(tex)
        print(f'{name}: {len(m["elements"])} cubes' + ('' if shown else ' (hidden)'))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        json.dump(draft, f, indent=1)
    print('->', OUT)

if __name__ == '__main__':
    main(sys.argv[1:])
