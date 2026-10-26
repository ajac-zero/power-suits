// Applies chaingun_back_plate_net.py's plan (args: [planPath]): paints the back plate's own cold net into free texture
// space (aborts if a target pixel is in use) and points chaingun_plate_back at it. Codemode function body.
const planPath = (typeof args !== 'undefined' && args[0]) || '/private/tmp/cg_back_plan.json';
const plan = JSON.parse((await tools.bash({command: `cat ${planPath} # ${Date.now()}`})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => { const c = Cube.all.find(c => c.name === 'chaingun_plate_back'), f = Cube.all.find(c => c.name === 'chaingun_plate_front');
  return !!c && !!f && c.box_uv && c.uv_offset[0] === f.uv_offset[0] && c.uv_offset[1] === f.uv_offset[1]; })()`);
if (ok !== true) return 'ABORT back plate not sharing the front plate net';
await tools.mcp__blockbench__save_checkpoint({name: "before chaingun back plate net"});
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, '0') + y.toString(16).padStart(2, '0') + c).join('');
return await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = []; let bad = 0;
  for (let i = 0; i < h.length; i += 10) px.push([0, 2, 4, 6, 8].map(k => parseInt(h.substr(i + k, 2), 16)));
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Chaingun back plate net paint'});
  if (bad) return 'ABORT ' + bad + ' target pixels in use';
  const c = Cube.all.find(c => c.name === 'chaingun_plate_back');
  Undo.initEdit({elements: [c]}); c.uv_offset = ${JSON.stringify(plan.uv_offset)}; Undo.finishEdit('Chaingun back plate own net', {elements: [c]});
  Canvas.updateAll(); return 'painted ' + px.length + ', back plate uv ' + JSON.stringify(c.uv_offset); })()`);
