// Applies tool_bases_art.py's plan (/private/tmp/fa/bases_plan.json): checkpoint, paint the four base nets (aborts if
// a target pixel is in use), move the wrist ring from the forearm onto the socket bone as the empty socket's base
// (socket_ring -> socket_base, art kept), and give each tool its own 7x1x7 base where the ring was. Codemode function body.
const plan = JSON.parse((await tools.bash({command: "cat /private/tmp/fa/bases_plan.json # " + Date.now()})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => Cube.all.some(c => c.name === 'socket_ring') && !Cube.all.some(c => /_base$/.test(c.name)))()`);
if (ok !== true) return "ABORT state " + ok;
await tools.mcp__blockbench__save_checkpoint({name: "before tool bases"});
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
const p = await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = []; let bad = 0;
  for (let i = 0; i < h.length; i += 10) px.push([0, 2, 4, 6, 8].map(k => parseInt(h.substr(i + k, 2), 16)));
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Tool bases paint'});
  return bad ? 'ABORT ' + bad : 'painted ' + px.length; })()`);
if (String(p).startsWith("ABORT")) return p;
const b = await R(`(() => { const N = ${JSON.stringify(plan.nets)}, G = n => Group.all.find(g => g.name === n), tex = Texture.all[0];
  const ring = Cube.all.find(c => c.name === 'socket_ring');
  const bones = {chaingun_base: 'chaingun', em_base: 'em_projector', drill_base: 'drill', saw_base: 'chainsaw'};
  Undo.initEdit({elements: [ring], outliner: true});
  ring.name = 'socket_base'; ring.addTo(G('socket')); ring.origin = [-10.5, 25.625, 0];
  const made = [];
  for (const [name, bone] of Object.entries(bones)) {
    const c = new Cube({name, from: [-14, 19.625, -3.5], to: [-7, 20.625, 3.5], origin: [-10.5, 25.625, 0], box_uv: true,
      uv_offset: N[name], mirror_uv: true}).addTo(G(bone)).init();
    c.applyTexture(tex, true); made.push(c); }
  Undo.finishEdit('Tool socket bases', {elements: [ring].concat(made), outliner: true}); Canvas.updateAll();
  return made.map(c => c.name + '@' + c.parent.name).concat(['ring -> ' + ring.parent.name]); })()`);
return [p, b];
