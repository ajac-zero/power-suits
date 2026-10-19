// Chainsaw: the side "sprocket covers" (saw_hub_r/l, 1x3x3 with a diamond bolt) become bar clamps (user, 2026-10-19:
// a diamond there made no sense; "a support for the bottom of the saw bar"): 1x3x4 slate plates on both sides of the
// bar's root, up against the base (y 16.625..19.625, z -2..2), two steel bolts through the bar, no diamond.
// New net packed into free space, the old one cleared. Codemode function body; after chainsaw_redesign_apply.js.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => Cube.all.filter(c => /^saw_hub_[lr]$/.test(c.name)).length === 2)()`);
if (ok !== true) return "ABORT state " + ok;
await tools.mcp__blockbench__save_checkpoint({name: "before saw bar clamps"});
const [w, h, d] = [1, 3, 4];
const paint = (f, x, y, fw, fh) => {
  if (f === 'up') return '4c5559'; if (f === 'down') return '2f3437';
  if (f === 'north' || f === 'south') return y === 0 ? '4c5559' : '3b4246';
  if (y === 1 && (x === 0 || x === fw - 1)) return 'a2acb1';                          // bolts through the bar
  return y === 0 ? '4c5559' : y === fh - 1 ? '2f3437' : '3b4246'; };
const spot = await R(`(() => { const tex = Texture.all[0], W = tex.width, H = tex.height, hubs = Cube.all.filter(c => /^saw_hub_[lr]$/.test(c.name));
  const net = c => { const s = c.size().map(v => Math.floor(v + 1e-6)), [u, v] = c.uv_offset || [0, 0]; return [u, v, 2 * (s[0] + s[2]), s[2] + s[1]]; };
  const rs = []; Cube.all.forEach(c => { if (hubs.includes(c)) return; if (c.box_uv) rs.push(net(c));
    else for (const f in c.faces) { const uv = c.faces[f].uv; if (uv) { const x0 = Math.floor(Math.min(uv[0], uv[2])), y0 = Math.floor(Math.min(uv[1], uv[3])); rs.push([x0, y0, Math.ceil(Math.max(uv[0], uv[2])) - x0, Math.ceil(Math.max(uv[1], uv[3])) - y0]); } } });
  const inR = (x, y) => rs.some(([u, v, w, h]) => x >= u && x < u + w && y >= v && y < v + h);
  let found = null;
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, W, H);
    const [u0, v0, w0, h0] = net(hubs[0]); for (let y = v0; y < v0 + h0; y++) for (let x = u0; x < u0 + w0; x++) if (!inR(x, y)) d.data[(y * W + x) * 4 + 3] = 0;
    for (let v = 0; v + ${d + h} <= H && !found; v++) for (let u = 0; u + ${2 * (w + d)} <= W && !found; u++) { let ok = true;
      for (let y = v; y < v + ${d + h} && ok; y++) for (let x = u; x < u + ${2 * (w + d)} && ok; x++) if (d.data[(y * W + x) * 4 + 3] || inR(x, y)) ok = false; if (ok) found = [u, v]; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Saw clamps: clear hub net'});
  return found; })()`);
if (!Array.isArray(spot)) return "ABORT no space";
const [u, v] = spot, px = [];
const F = {up: [u + d, v, w, d], down: [u + d + w, v, w, d], east: [u, v + d, d, h], north: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h]};
for (const [f, [x0, y0, fw, fh]] of Object.entries(F)) for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) px.push([x0 + x, y0 + y, paint(f, x, y, fw, fh)]);
const hex = px.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
await R(`(() => { const h = '${hex}'; Texture.all[0].edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
  for (let i = 0; i < h.length; i += 10) { const k = (parseInt(h.substr(i + 2, 2), 16) * canvas.width + parseInt(h.substr(i, 2), 16)) * 4;
    d.data[k] = parseInt(h.substr(i + 4, 2), 16); d.data[k + 1] = parseInt(h.substr(i + 6, 2), 16); d.data[k + 2] = parseInt(h.substr(i + 8, 2), 16); d.data[k + 3] = 255; }
  ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Saw clamps: paint'}); return 1; })()`);
return R(`(() => { const hubs = Cube.all.filter(c => /^saw_hub_[lr]$/.test(c.name));
  Undo.initEdit({elements: hubs});
  hubs.forEach(c => { const x = c.name.endsWith('_r') ? -9.5 : -12.5; c.name = c.name.replace('saw_hub', 'saw_clamp');
    c.from = [x, 16.625, -2]; c.to = [x + 1, 19.625, 2]; c.uv_offset = [${u}, ${v}]; c.preview_controller.updateGeometry(c); Canvas.updateUV(c); });
  Undo.finishEdit('Saw bar clamps', {elements: hubs}); Canvas.updateAll(); return hubs.map(c => c.name + ' ' + c.uv_offset); })()`);
