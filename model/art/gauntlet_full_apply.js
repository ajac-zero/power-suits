// Applies model/art/gauntlet_full_art.py's plan (.scratch/gauntlet/plan.json). Codemode function body, run with the
// project folder as cwd. Checkpoint, paint the new side faces (aborts if a target pixel is in use), then turn each
// forearm block into the full-length box (per-face UV: new sides, its old top and wrist faces) and remove the stepped
// pieces above it (fa_ramp / fa_mid / fa_back) and the elbow ramps on the upper arms. Paint and build are separate undo
// entries.
const plan = JSON.parse((await tools.bash({command: "cat .scratch/gauntlet/plan.json"})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
if (await R(`Cube.all.some(c => c.name === 'fa_ramp_r') ? 'ok' : 'gone'`) !== "ok") return "ABORT already applied (fa_ramp_r gone)";
await tools.mcp__blockbench__save_checkpoint({name: "before full gauntlets"});
const out = [];
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
out.push(await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = [];
  for (let i = 0; i < h.length; i += 10) px.push([parseInt(h.substr(i, 2), 16), parseInt(h.substr(i + 2, 2), 16), parseInt(h.substr(i + 4, 2), 16), parseInt(h.substr(i + 6, 2), 16), parseInt(h.substr(i + 8, 2), 16)]);
  let bad = 0;
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Full gauntlets paint'});
  return bad ? 'ABORT ' + bad + ' pixels in use' : 'painted ' + px.length; })()`));
if (String(out[0]).startsWith("ABORT")) return out;
out.push(await R(`(() => { const P = ${JSON.stringify({cubes: plan.cubes, remove: plan.remove})};
  const tex = Texture.all[0];
  const fore = Object.keys(P.cubes).map(n => Cube.all.find(c => c.name === n));
  const old = Cube.all.filter(c => P.remove.includes(c.name));
  Undo.initEdit({elements: fore.concat(old), outliner: true});
  old.forEach(c => c.remove());
  for (const c of fore) { const s = P.cubes[c.name];
    c.box_uv = false; c.from = s.from.slice(); c.to = s.to.slice();
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f].slice(); } }
  Undo.finishEdit('Full gauntlets', {elements: fore, outliner: true});
  Canvas.updateAll(); return 'built ' + fore.map(c => c.name).join(',') + '; removed ' + old.map(c => c.name).join(','); })()`));
return out;
