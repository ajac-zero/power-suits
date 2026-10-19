// Applies model/art/head_round_art.py's plan (.scratch/head/round_plan.json): checkpoint, paint (the plan may cover
// the rebuilt cubes' own old faces: the art script frees only pixels no other cube uses), update the helmet cubes that
// exist and create the new ones on the plan's bone (per-face UV, inflate 0). Codemode function body.
const plan = JSON.parse((await tools.bash({command: "cat .scratch/head/round_plan.json # " + Date.now()})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
await tools.mcp__blockbench__save_checkpoint({name: "before round helmet " + (plan.tag || "")});
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
const p = await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = [];
  for (let i = 0; i < h.length; i += 10) px.push([0, 2, 4, 6, 8].map(k => parseInt(h.substr(i + k, 2), 16)));
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Round helmet paint'});
  return 'painted ' + px.length; })()`);
const b = await R(`(() => { const P = ${JSON.stringify(plan)}, tex = Texture.all[0], bone = Group.all.find(g => g.name === P.bone);
  const old = Cube.all.filter(c => c.name in P.cubes);
  Undo.initEdit({elements: old, outliner: true}); const all = [];
  for (const [name, s] of Object.entries(P.cubes)) {
    let c = Cube.all.find(c => c.name === name);
    if (!c) c = new Cube({name, origin: P.origin.slice()}).addTo(bone).init();
    c.box_uv = false; c.inflate = 0; c.from = s.from.slice(); c.to = s.to.slice();
    for (const f in s.faces) { if (!s.faces[f]) { c.faces[f].texture = null; continue; }
      c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f].slice(); }
    all.push(c); }
  Undo.finishEdit('Round helmet', {elements: all, outliner: true}); Canvas.updateAll();
  const gone = Cube.all.filter(c => (P.remove || []).includes(c.name));
  if (gone.length) { Undo.initEdit({elements: gone, outliner: true}); gone.forEach(c => c.remove()); Undo.finishEdit('Round helmet: removed pieces', {elements: [], outliner: true}); Canvas.updateAll(); }
  return 'helmet cubes ' + all.length + ' (' + (all.length - old.length) + ' new)'; })()`);
// the shell's back (z 5) lay on the nape's back face: flicker from behind -> the nape 0.05 smaller all round
const n = await R(`(() => { const c = Cube.all.find(c => c.name === 'nape'); if (!c || c.inflate === -0.05) return 'nape ok';
  Undo.initEdit({elements: [c]}); c.inflate = -0.05; Undo.finishEdit('Nape off the helmet back plane', {elements: [c]}); Canvas.updateAll(); return 'nape -0.05'; })()`);
return [p, b, n];
