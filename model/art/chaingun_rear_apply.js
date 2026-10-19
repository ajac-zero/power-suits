// Chaingun: one base (user, 2026-10-19, option 2): the rear barrel collar (chaingun_plate_rear, 6x1x6 under the base)
// is removed and the four barrels + the axle grow 1 px at their base end (y 18.625 -> 19.625) so they start right at
// the slate base; the muzzle doesn't move. Their nets get one more side row at the top, a copy of the old top row (the
// heat stripes near the muzzle and the tips are untouched, so make-glowmasks.py's counts stay). Old nets cleared where
// no other cube uses them, new ones packed into free space (grow(): the old net with its first side row, row d,
// doubled). Codemode function body.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
if (await R(`(() => Cube.all.some(c => c.name === 'chaingun_plate_rear'))()`) !== true) return "ABORT no rear plate";
await tools.mcp__blockbench__save_checkpoint({name: "before chaingun barrels to the base"});
return R(`(() => { const tex = Texture.all[0], W = tex.width, H = tex.height, by = n => Cube.all.find(c => c.name === n);
  const rear = by('chaingun_plate_rear'), barrels = Cube.all.filter(c => /^chaingun_barrel_\\d$/.test(c.name)), axle = by('chaingun_axle');
  const net = c => { const s = c.size().map(v => Math.floor(v + 1e-6)), [u, v] = c.uv_offset || [0, 0]; return [u, v, 2 * (s[0] + s[2]), s[2] + s[1]]; };
  const gone = [rear, axle].concat(barrels);
  const rs = []; Cube.all.forEach(c => { if (gone.includes(c)) return; if (c.box_uv) rs.push(net(c));
    else for (const f in c.faces) { const uv = c.faces[f].uv; if (uv) { const x0 = Math.floor(Math.min(uv[0], uv[2])), y0 = Math.floor(Math.min(uv[1], uv[3])); rs.push([x0, y0, Math.ceil(Math.max(uv[0], uv[2])) - x0, Math.ceil(Math.max(uv[1], uv[3])) - y0]); } } });
  const inR = (x, y) => rs.some(([u, v, w, h]) => x >= u && x < u + w && y >= v && y < v + h);
  const out = {};
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, W, H); const px = (x, y) => d.data.slice((y * W + x) * 4, (y * W + x) * 4 + 4);
    const grow = c => { const [u, v, w, h] = net(c), dd = Math.floor(c.size()[2] + 1e-6), rows = [];
      for (let y = 0; y < h; y++) { const r = []; for (let x = 0; x < w; x++) r.push(px(u + x, v + y)); rows.push(r); if (y === dd) rows.push(r.map(p => p.slice())); }
      return rows; };
    const newB = grow(barrels[0]), newA = grow(axle);
    for (const c of [rear, barrels[0], axle]) { const [u, v, w, h] = net(c); for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) if (!inR(x, y)) d.data[(y * W + x) * 4 + 3] = 0; }
    const busy = (x, y) => d.data[(y * W + x) * 4 + 3] || inR(x, y);
    for (const [name, rows] of [['barrel', newB], ['axle', newA]]) { const w = rows[0].length, h = rows.length; let f = null;
      for (let v = 0; v + h <= H && !f; v++) for (let u = 0; u + w <= W && !f; u++) { let ok = true; for (let y = v; y < v + h && ok; y++) for (let x = u; x < u + w && ok; x++) if (busy(x, y)) ok = false; if (ok) f = [u, v]; }
      if (!f) { out.error = name; break; }
      rows.forEach((r, y) => r.forEach((p, x) => { const k = ((f[1] + y) * W + f[0] + x) * 4; for (let i = 0; i < 4; i++) d.data[k + i] = p[i]; }));
      rs.push([f[0], f[1], w, h]); out[name] = f; }
    if (!out.error) ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Chaingun: barrel nets one row longer'});
  if (out.error) return 'ABORT no space for ' + out.error;
  Undo.initEdit({elements: [rear], outliner: true}); rear.remove(); Undo.finishEdit('Chaingun: remove rear collar', {elements: [], outliner: true});
  const grown = barrels.concat([axle]);
  Undo.initEdit({elements: grown});
  grown.forEach(c => { c.to[1] = 19.625; c.uv_offset = (c === axle ? out.axle : out.barrel).slice(); c.preview_controller.updateGeometry(c); Canvas.updateUV(c); });
  Undo.finishEdit('Chaingun: barrels + axle to the base', {elements: grown}); Canvas.updateAll();
  return JSON.stringify(out); })()`);
