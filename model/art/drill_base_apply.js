// Drill base: blue -> slate, and a plug painted on its back (user, 2026-10-19: flat, no 3D plug). Repaints drill_base's
// net (7x1x7 at [200, 76]) in the colours round the forearm's connector; its top face (against the gauntlet) gets the
// plug: a 5x5 steel collar round a 3x3 cyan contact, centred under the coupling's cyan centre on the gauntlet's
// underside (both centred on x -10.5, z 0; the base's 7 px grid sits half a pixel off the gauntlet's 8, so a painted 2x2
// can't line up: the 3x3 covers it). Hidden when fitted; seen on the tool alone / the item icon. Codemode function body;
// call with STYLE = 'slate' (dark iron like the gauntlet's underside) or 'steel' (the coupling plate's grey).
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const S = {
  //          top fill, top lit edge, top shade edge, underside fill, underside rim, side, side ends, side centre
  slate: ['2f3437', '4c5559', '25292c', '2f3437', '3b4246', '3b4246', '6f797e', '4c5559'],
  steel: ['6f797e', '89939a', '565f63', '565f63', '6f797e', '6f797e', 'a2acb1', '565f63'],
}[typeof STYLE === 'undefined' ? 'slate' : STYLE];
const [U, V] = [200, 76], px = [];
const CY = {'2,2': 'a1fbe8', '3,2': '2ce3d9', '2,3': '2ce3d9', '4,4': '12a1a0', '3,4': '12a1a0', '4,3': '12a1a0'};
for (let y = 0; y < 7; y++) for (let x = 0; x < 7; x++) {
  const lit = x === 0 || y === 0, shade = x === 6 || y === 6, collar = x >= 1 && x <= 5 && y >= 1 && y <= 5, inner = x >= 2 && x <= 4 && y >= 2 && y <= 4;
  const top = inner ? (CY[x + ',' + y] || '2ce3d9') : collar ? ((x === 1 || y === 1) ? '89939a' : '565f63') : lit ? S[1] : shade ? S[2] : S[0];
  px.push([U + 7 + x, V + y, top]);
  px.push([U + 14 + x, V + y, (lit || shade) ? S[4] : S[3]]); }
for (let s = 0; s < 4; s++) for (let x = 0; x < 7; x++) px.push([U + 7 * s + x, V + 7, x === 0 || x === 6 ? S[6] : x === 3 ? S[7] : S[5]]);
const hex = a => a.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
const paint = (h, name) => `Texture.all[0].edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const h = '${h}'; for (let i = 0; i < h.length; i += 10) { const x = parseInt(h.substr(i, 2), 16), y = parseInt(h.substr(i + 2, 2), 16), k = (y * canvas.width + x) * 4;
    d.data[k] = parseInt(h.substr(i + 4, 2), 16); d.data[k + 1] = parseInt(h.substr(i + 6, 2), 16); d.data[k + 2] = parseInt(h.substr(i + 8, 2), 16); d.data[k + 3] = 255; }
  ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: '${name}'})`;
const out = [await R(`(() => { ${paint(hex(px), 'Drill base ' + (typeof STYLE === 'undefined' ? 'slate' : STYLE) + ' + painted plug')}; return 'base painted'; })()`)];
return out;
