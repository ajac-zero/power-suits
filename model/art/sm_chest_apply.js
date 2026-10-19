// Applies model/art/sm_chest_art.py's plan (.scratch/sm-chest/plan.json) to the live project. Codemode function body.
// Checkpoint, remove the shape prototypes (proto_* groups), paint the new faces (aborts if a target pixel is in use),
// cut the chest to its new height and place the new cubes on `torso` with per-face UV. Each step its own undo entry.
const plan = JSON.parse((await tools.bash({command: "cat .scratch/sm-chest/plan.json # " + Date.now()})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
// re-run: undo the previous build + paint (only if they are the last entries on the undo stack)
const undone = await R("(() => { const names = ['Space marine chest', 'Space marine chest paint']; let n = 0; while (Undo.history[Undo.index - 1] && names.includes(Undo.history[Undo.index - 1].action) && n < 4) { Undo.undo(); n++; } Canvas.updateAll(); return n; })()");
if (!undone) await tools.mcp__blockbench__save_checkpoint({name: "before space marine chest"});
const out = [];
out.push(await R(`(() => { const gs = Group.all.filter(g => /^proto_[A-Z]$/.test(g.name)); if (!gs.length) return 'no protos';
  const cubes = []; gs.forEach(g => g.forEachChild(c => cubes.push(c), Cube));
  Undo.initEdit({outliner: true, elements: cubes, groups: gs});
  cubes.forEach(c => c.remove()); gs.forEach(g => g.remove(false));
  Undo.finishEdit('Remove chest prototypes', {outliner: true, elements: [], groups: []});
  Canvas.updateAll(); return 'removed ' + gs.length + ' protos'; })()`));
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
out.push(await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = [];
  for (let i = 0; i < h.length; i += 10) px.push([parseInt(h.substr(i, 2), 16), parseInt(h.substr(i + 2, 2), 16), parseInt(h.substr(i + 4, 2), 16), parseInt(h.substr(i + 6, 2), 16), parseInt(h.substr(i + 8, 2), 16)]);
  let bad = 0;
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Space marine chest paint'});
  return bad ? 'ABORT ' + bad + ' pixels in use' : 'painted ' + px.length; })()`));
if (String(out[1]).startsWith("ABORT")) return out;
out.push(await R(`(() => { const P = ${JSON.stringify(plan)};
  const torso = Group.all.find(g => g.name === 'torso'), tex = Texture.all[0], chest = Cube.all.find(c => c.name === 'chest');
  if (Cube.all.some(c => c.name === 'bp_main')) return 'ABORT exists';
  Undo.initEdit({elements: [chest], outliner: true});
  const made = [];
  for (const [name, s] of Object.entries(P.cubes)) {
    const c = name === 'chest' ? chest : new Cube({name, origin: P.origin.slice()}).addTo(torso).init();
    c.box_uv = false; c.from = s.from.slice(); c.to = s.to.slice();
    if (s.origin) { c.origin = s.origin.slice(); c.rotation = s.rotation.slice(); }
    // off shared planes (flicker): the underside's sides are the breastplate's, the core's top / bottom the plate's
    c.inflate = {bp_lower: -0.05, abd_core: 0.03}[name] || 0;
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f].slice(); }
    made.push(c); }
  Undo.finishEdit('Space marine chest', {elements: made, outliner: true});
  Canvas.updateAll(); return 'built ' + made.length; })()`));
// verify the texture against the plan (an undo of a texture edit restores the image asynchronously and once left a few
// old pixels behind); repaint any mismatch
out.push(await R(`(() => { const P = ${JSON.stringify(plan.pixels)}, tex = Texture.all[0]; const fix = [];
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [u, v, h] of P) { const i = (v * canvas.width + u) * 4; const s = [d.data[i], d.data[i + 1], d.data[i + 2]].map(n => n.toString(16).padStart(2, '0')).join('');
      if (s !== h || d.data[i + 3] !== 255) { fix.push([u, v]); d.data[i] = parseInt(h.substr(0, 2), 16); d.data[i + 1] = parseInt(h.substr(2, 2), 16); d.data[i + 2] = parseInt(h.substr(4, 2), 16); d.data[i + 3] = 255; } }
    if (fix.length) ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Space marine chest paint', no_undo: false});
  return 'verified, fixed ' + fix.length; })()`));
return out;
