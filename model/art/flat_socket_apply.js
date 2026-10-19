// Flat wrist connector (user, 2026-10-18): nothing of the socket sticks out below the wrist ring any more, so the tool
// heads never overlap it. The empty socket's plate (5x5) and pins (3x3) are painted onto the ring's underside (its box
// UV down face at [14, 0]) instead of hanging 2 px below it as cubes; socket_plate / socket_pins are removed and their
// nets cleared. The EM projector's lens and windings, which reached up into the ring, move down to end at its
// underside (y 19.625, inflate included). The empty `socket` bone stays (the viewmodel centres on its pivot).
// Codemode function body; run after shared_forearm_apply.js.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => ['socket_plate', 'socket_pins'].every(n => Cube.all.some(c => c.name === n)) && Cube.all.find(c => c.name === 'socket_ring').from[1] === 19.625)()`);
if (ok !== true) return "ABORT " + ok;
await tools.mcp__blockbench__save_checkpoint({name: "before flat socket"});
// 1. paint: ring underside = ring rim + plate face + pins face; clear the plate's and pins' nets
const p = await R(`(() => { const tex = Texture.all[0], by = n => Cube.all.find(c => c.name === n);
  const plate = by('socket_plate'), pins = by('socket_pins');
  const net = c => { const s = c.size().map(v => Math.floor(v + 1e-6)), [u, v] = c.uv_offset; return [u, v, 2 * (s[0] + s[2]), s[2] + s[1]]; };
  const pn = net(plate), qn = net(pins);
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const W = canvas.width, d = ctx.getImageData(0, 0, W, canvas.height);
    const cp = (sx, sy, tx, ty, n) => { for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) { const a = ((sy + y) * W + sx + x) * 4, b = ((ty + y) * W + tx + x) * 4; for (let k = 0; k < 4; k++) d.data[b + k] = d.data[a + k]; } };
    cp(pn[0] + 5 + 5, pn[1], 14 + 1, 0 + 1, 5);
    cp(qn[0] + 3 + 3, qn[1], 14 + 2, 0 + 2, 3);
    for (const [u, v, w, h] of [pn, qn]) for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) d.data[(y * W + x) * 4 + 3] = 0;
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Flat socket paint'});
  return 'painted'; })()`);
// 2. remove the plate and pins (own edit)
const rm = await R(`(() => { const cs = Cube.all.filter(c => ['socket_plate', 'socket_pins'].includes(c.name));
  Undo.initEdit({elements: cs, outliner: true}); cs.forEach(c => c.remove()); Undo.finishEdit('Remove socket plate + pins', {elements: [], outliner: true});
  return 'removed ' + cs.length; })()`);
// 3. EM parts down to the ring's underside
const m = await R(`(() => { const cs = Cube.all.filter(c => /^em_(lens|winding_\\d)$/.test(c.name)); const out = [];
  Undo.initEdit({elements: cs});
  cs.forEach(c => { const dy = 19.625 - (c.to[1] + (c.inflate || 0)); c.from[1] += dy; c.to[1] += dy; out.push(c.name + ' ' + dy); });
  Undo.finishEdit('EM parts below the wrist ring', {elements: cs}); Canvas.updateAll(); return out; })()`);
return [p, rm, m];
