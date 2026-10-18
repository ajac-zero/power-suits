// Stand-in pilot head (see pilot_head_art.py, run it first). Codemode function body (or the skill's
// scripts/bbrun.mjs model/art/pilot_head_apply.js).
// Adds the bone `pilot_head` under `head`: its pivot is the bottom centre of the pilot's head (like vanilla's head
// part, which hangs its 8x8x8 cube above the neck pivot), centred over the chest (z 0.5) with the eyes at the cockpit
// camera's height (y 41.6, the old helmet's middle). The game draws the pilot's real head there; in Blockbench an
// 8x8x8 Steve head (`pilot_head_standin`, per-face UV, not exported) shows where it goes.
const P = JSON.parse((await tools.bash({command: "cat /private/tmp/ub/pilot_head.json"})).output);
const B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before pilot head"});
const r = await B({code: `(() => { const P = ${JSON.stringify(P)};
  if (Group.all.some(g => g.name === 'pilot_head')) return 'ABORT pilot_head exists';
  const tex = Texture.all[0]; let bad = 0;
  tex.edit(canvas => { const ctx = canvas.getContext('2d');
    for (const [x, y] of P.paint) if (ctx.getImageData(x, y, 1, 1).data[3]) bad++;
    if (bad) return canvas;
    for (const [x, y, hex] of P.paint) { ctx.fillStyle = '#' + hex; ctx.fillRect(x, y, 1, 1); }
    return canvas; }, {edit_name: 'Paint stand-in pilot head'});
  if (bad) return 'ABORT ' + bad + ' used pixels';
  Undo.initEdit({outliner: true, elements: []});
  const head = Group.all.find(g => g.name === 'head');
  const g = new Group({name: 'pilot_head', origin: [0, 37.625, 0.5]}).addTo(head).init();
  const c = new Cube({name: 'pilot_head_standin', from: [-4, 37.625, -3.5], to: [4, 45.625, 4.5], origin: [0, 37.625, 0.5], box_uv: false}).addTo(g).init();
  c.export = false;
  for (const [f, [u, v]] of Object.entries(P.faces)) { c.faces[f].texture = tex.uuid; c.faces[f].uv = [u, v, u + 8, v + 8]; }
  Undo.finishEdit('Stand-in pilot head', {outliner: true, elements: [c]});
  Canvas.updateAll(); return 'ok'; })()`});
return r.content[0].text;
