// Chainsaw redesign (user, 2026-10-19: "too plain next to the drill"): one slate base (saw_base, painted by
// drill_base_apply.js with NET [200, 84]), the second base (saw_guard) removed, the bar taken up to the base (2x9x5,
// steel with a slate band where the orange stripe was, rivets), diamond chain teeth (cyan, the drill's palette; the chain
// keeps its 2-row period, so the chainsaw_run 1 px bob still reads as a running chain; it reaches 1 px into the base so
// the bob never opens a gap under it), and a drive-sprocket cover on each side of the bar (1x3x3 steel, diamond bolt).
// Old nets cleared where no other cube uses them, new ones packed into free space. Codemode function body.
// The sprocket covers were then turned into bar clamps by saw_clamp_apply.js (run it after this one).
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => Cube.all.some(c => c.name === 'saw_guard') && !Cube.all.some(c => c.name === 'saw_hub_r'))()`);
if (ok !== true) return "ABORT state " + ok;
await tools.mcp__blockbench__save_checkpoint({name: "before chainsaw redesign"});
// box nets: [w, h, d] -> faces; painters get (face, x, y, fw, fh)
const NETS = {
  saw_bar: [2, 9, 5, (f, x, y, w, h) => {
    if (f === 'up' || f === 'down') return '565f63';
    if (y === 0) return '3b4246'; if (y === 1) return '2f3437';                      // slate band under the base
    if (f === 'north' || f === 'south') return y === h - 1 ? '565f63' : '6f797e';
    if (y === h - 1) return '565f63';
    if (x === 2 && (y === 3 || y === 5 || y === 7)) return 'c8d0d3';                // rivets (row 3 added by the user)
    return x === 0 ? 'a2acb1' : x === w - 1 ? '565f63' : '6f797e'; }],
  saw_chain: [1, 11, 6, (f, x, y, w, h) => {
    const tooth = y % 2 === 0;
    if (f === 'up') return '25292c';
    if (f === 'down') return y % 2 === 0 ? '2ce3d9' : '0e7b7a';                      // 1 wide: alternate along its 6
    if (f === 'north' || f === 'south') return tooth ? 'a1fbe8' : '12a1a0';   // alternating cyans, no black (user)
    if (y === h - 1) return x % 2 === 0 ? '2ce3d9' : '0e7b7a';                         // the row under the bar
    if (x === 0) return tooth ? '2ce3d9' : '0e7b7a';
    if (x === w - 1) return tooth ? '0e7b7a' : '2ce3d9';
    return '25292c'; }],
  saw_hub: [1, 3, 3, (f, x, y) => {
    if (f !== 'east' && f !== 'west') return '6f797e';
    if (x === 1 && y === 1) return '2ce3d9';                                          // diamond bolt
    return (x === 1 || y === 1) ? '89939a' : '565f63'; }],
};
const faces = (u, v, [w, h, d]) => ({up: [u + d, v, w, d], down: [u + d + w, v, w, d], east: [u, v + d, d, h],
  north: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h]});
// 1. clear the old guard / bar / chain nets, 2. find space for the new nets
const spots = await R(`(() => { const gone = ['saw_guard', 'saw_bar', 'saw_chain'], tex = Texture.all[0], W = tex.width, H = tex.height;
  const net = c => { const s = c.size().map(v => Math.floor(v + 1e-6)), [u, v] = c.uv_offset || [0, 0]; return [u, v, 2 * (s[0] + s[2]), s[2] + s[1]]; };
  const rs = []; Cube.all.forEach(c => { if (gone.includes(c.name)) return; if (c.box_uv) rs.push(net(c));
    else for (const f in c.faces) { const uv = c.faces[f].uv; if (uv) { const x0 = Math.floor(Math.min(uv[0], uv[2])), y0 = Math.floor(Math.min(uv[1], uv[3])); rs.push([x0, y0, Math.ceil(Math.max(uv[0], uv[2])) - x0, Math.ceil(Math.max(uv[1], uv[3])) - y0]); } } });
  const inR = (x, y) => rs.some(([u, v, w, h]) => x >= u && x < u + w && y >= v && y < v + h);
  const old = Cube.all.filter(c => gone.includes(c.name)).map(net), out = {};
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, W, H);
    for (const [u, v, w, h] of old) for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) if (!inR(x, y)) d.data[(y * W + x) * 4 + 3] = 0;
    const busy = (x, y) => d.data[(y * W + x) * 4 + 3] || inR(x, y);
    for (const [name, w, h] of [['saw_chain', 14, 17], ['saw_bar', 14, 14], ['saw_hub', 8, 6]]) {
      let found = null; for (let v = 0; v + h <= H && !found; v++) for (let u = 0; u + w <= W && !found; u++) { let ok = true;
        for (let y = v; y < v + h && ok; y++) for (let x = u; x < u + w && ok; x++) if (busy(x, y)) ok = false; if (ok) found = [u, v]; }
      if (!found) { out.error = name; break; } out[name] = found; rs.push([found[0], found[1], w, h]); }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Chainsaw: clear old nets'});
  return out; })()`);
if (spots.error) return "ABORT no space for " + spots.error;
// 3. paint the new nets
const px = [];
for (const [name, [w, h, d, fn]] of Object.entries(NETS)) {
  const [u, v] = spots[name];
  for (const [f, [x0, y0, fw, fh]] of Object.entries(faces(u, v, [w, h, d])))
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) px.push([x0 + x, y0 + y, fn(f, x, y, fw, fh)]);
}
const hex = px.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
const p = await R(`(() => { const h = '${hex}'; let bad = 0; Texture.all[0].edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
  for (let i = 0; i < h.length; i += 10) { const k = (parseInt(h.substr(i + 2, 2), 16) * canvas.width + parseInt(h.substr(i, 2), 16)) * 4; if (d.data[k + 3]) bad++; }
  if (bad) return canvas;
  for (let i = 0; i < h.length; i += 10) { const k = (parseInt(h.substr(i + 2, 2), 16) * canvas.width + parseInt(h.substr(i, 2), 16)) * 4;
    d.data[k] = parseInt(h.substr(i + 4, 2), 16); d.data[k + 1] = parseInt(h.substr(i + 6, 2), 16); d.data[k + 2] = parseInt(h.substr(i + 8, 2), 16); d.data[k + 3] = 255; }
  ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Chainsaw: paint new nets'}); return bad ? 'ABORT ' + bad : 'painted ' + (h.length / 10); })()`);
if (String(p).startsWith("ABORT")) return p;
// 4. geometry: guard out (own edit), bar + chain reshaped, sprocket covers in
const g1 = await R(`(() => { const c = Cube.all.find(c => c.name === 'saw_guard'); Undo.initEdit({elements: [c], outliner: true}); c.remove();
  Undo.finishEdit('Chainsaw: remove guard', {elements: [], outliner: true}); return 'guard removed'; })()`);
const g2 = await R(`(() => { const S = ${JSON.stringify(spots)}, by = n => Cube.all.find(c => c.name === n), bar = by('saw_bar'), chain = by('saw_chain');
  const saw = Group.all.find(g => g.name === 'chainsaw'), tex = Texture.all[0], vis = bar.visibility;
  Undo.initEdit({elements: [bar, chain], outliner: true});
  bar.from = [-11.5, 10.625, -2.5]; bar.to = [-9.5, 19.625, 2.5]; bar.uv_offset = S.saw_bar.slice();
  chain.from = [-11, 9.625, -3]; chain.to = [-10, 20.625, 3]; chain.uv_offset = S.saw_chain.slice(); chain.inflate = -0.05;   // off the base's top plane
  const hubs = [['saw_hub_r', -9.5, false], ['saw_hub_l', -12.5, true]].map(([name, x, mir]) => { const c = new Cube({name, from: [x, 15.625, -1.5], to: [x + 1, 18.625, 1.5],
    origin: [-10.5, 25.625, 0], box_uv: true, uv_offset: S.saw_hub.slice(), mirror_uv: mir}).addTo(saw).init(); c.applyTexture(tex, true); c.visibility = vis; return c; });
  [bar, chain].forEach(c => { c.preview_controller.updateGeometry(c); Canvas.updateUV(c); });
  Undo.finishEdit('Chainsaw: bar, chain, sprocket covers', {elements: [bar, chain].concat(hubs), outliner: true}); Canvas.updateAll(); return 'reshaped'; })()`);
return {spots, p, g1, g2};
