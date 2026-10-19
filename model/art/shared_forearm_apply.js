// Shared tool forearm (user, 2026-10-18): the left (tool) arm gets the right gauntlet mirrored (forearm_l, 8x7x8 on
// left_forearm, forearm_r's net unmirrored), the socket moves from the elbow to the wrist (ring, plate and pins 6 px
// down, the ring now under the gauntlet like the right fist), and each tool keeps only its head: the 7x6 forearm
// blocks (chaingun_motor, em_housing, drill_motor, saw_motor) and the details on them (drill_fin, saw_exhaust) go,
// and their nets are cleared where no remaining cube uses the pixels. Bone pivots don't move (animations, SocketAim,
// the viewmodel stay valid). Codemode function body; run from the project folder.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const GONE = ["chaingun_motor", "em_housing", "drill_motor", "drill_fin", "saw_motor", "saw_exhaust"];
const check = await R(`(() => JSON.stringify({gone: ${JSON.stringify(GONE)}.filter(n => Cube.all.filter(c => c.name === n).length === 1).length,
  exists: Cube.all.some(c => c.name === 'forearm_l'), ring: (Cube.all.find(c => c.name === 'socket_ring') || {}).from}))()`);
if (check.gone !== GONE.length || check.exists || !check.ring || check.ring[1] !== 25.625) return "ABORT " + JSON.stringify(check);
await tools.mcp__blockbench__save_checkpoint({name: "before shared tool forearm"});
// 1. clear the removed cubes' nets, except pixels another (kept) cube's net covers
const p = await R(`(() => { const gone = new Set(${JSON.stringify(GONE)}), tex = Texture.all[0];
  const net = c => { const s = c.size().map(v => Math.floor(v + 1e-6)), [u, v] = c.uv_offset || [0, 0]; return [u, v, 2 * (s[0] + s[2]), s[2] + s[1]]; };
  const keep = Cube.all.filter(c => !gone.has(c.name) && c.box_uv).map(net), clear = Cube.all.filter(c => gone.has(c.name)).map(net);
  const used = (x, y) => keep.some(([u, v, w, h]) => x >= u && x < u + w && y >= v && y < v + h);
  let n = 0;
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [u, v, w, h] of clear) for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++)
      if (!used(x, y)) { const i = (y * canvas.width + x) * 4; if (d.data[i + 3]) n++; d.data[i + 3] = 0; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Clear tool forearm nets'});
  return 'cleared ' + n; })()`);
// 2. remove the tool forearms (its own edit: removals together with other changes misbehaved on redo)
const rm = await R(`(() => { const cs = Cube.all.filter(c => ${JSON.stringify(GONE)}.includes(c.name));
  Undo.initEdit({elements: cs, outliner: true}); cs.forEach(c => c.remove()); Undo.finishEdit('Remove tool forearms', {elements: [], outliner: true});
  Canvas.updateAll(); return 'removed ' + cs.length; })()`);
// 3. socket to the wrist + the mirrored gauntlet
const b = await R(`(() => { const by = n => Cube.all.find(c => c.name === n), moved = ['socket_ring', 'socket_plate', 'socket_pins'].map(by);
  const fa = Group.all.find(g => g.name === 'left_forearm'), tex = Texture.all[0];
  Undo.initEdit({elements: moved, outliner: true});
  moved.forEach(c => { c.from[1] -= 6; c.to[1] -= 6; });
  const g = new Cube({name: 'forearm_l', from: [-14.5, 20.625, -4], to: [-6.5, 27.625, 4], origin: [-10.5, 28.625, 0],
    box_uv: true, uv_offset: [216, 21], mirror_uv: false}).addTo(fa).init();
  g.applyTexture(tex, true);
  Undo.finishEdit('Shared tool forearm', {elements: moved.concat([g]), outliner: true}); Canvas.updateAll();
  return 'placed; ring ' + moved[0].from[1] + '..' + moved[0].to[1]; })()`);
return [p, rm, b];
