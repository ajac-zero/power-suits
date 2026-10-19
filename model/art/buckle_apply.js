// Applies model/art/buckle_art.py's plan (.scratch/head/buckle_plan.json): checkpoint, paint (aborts if a target
// pixel is in use), place the cube(s) on the plan's bone with per-face UV. Codemode function body.
const plan = JSON.parse((await tools.bash({command: "cat .scratch/head/buckle_plan.json # " + Date.now()})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
await tools.mcp__blockbench__save_checkpoint({name: "before belt buckle"});
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
const p = await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = []; let bad = 0;
  for (let i = 0; i < h.length; i += 10) px.push([0, 2, 4, 6, 8].map(k => parseInt(h.substr(i + k, 2), 16)));
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Belt buckle paint'});
  return bad ? 'ABORT ' + bad : 'painted ' + px.length; })()`);
if (String(p).startsWith("ABORT")) return p;
const b = await R(`(() => { const P = ${JSON.stringify(plan)}; const bone = Group.all.find(g => g.name === P.bone), tex = Texture.all[0];
  if (Object.keys(P.cubes).some(n => Cube.all.some(c => c.name === n))) return 'ABORT exists';
  Undo.initEdit({elements: [], outliner: true}); const made = [];
  for (const [name, s] of Object.entries(P.cubes)) { const c = new Cube({name, origin: P.origin.slice(), from: s.from, to: s.to, box_uv: false}).addTo(bone).init();
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f].slice(); } made.push(c); }
  Undo.finishEdit('Belt buckle', {elements: made, outliner: true}); Canvas.updateAll(); return 'built ' + made.length; })()`);
return [p, b];
