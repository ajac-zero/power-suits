// Drill hub -> first auger step (user, 2026-10-19): the 6x2x6 drill_hub (on drill_bit, it spins) read as a second base
// under drill_base (blue sides with grey ribs). Repainted in the cones' steel: sides in cone_1's flight pattern stretched
// to 6 px per face, a dark grey top/underside with a lit/shaded checker rim like the cones' tops. Geometry unchanged, so
// the bit tapers 6-5-4-3-2-1 from the base. Codemode function body.
const ev = async code => (await tools.mcp__blockbench__risky_eval({code})).content[0].text;
const C = {B: '4c5559', D: 'a2acb1', F: '89939a', H: '565f63', I: '7d888e', J: 'c8d0d3'};
const U = 0, V = 66;      // drill_hub's net (6x2x6: 24x8); rows 0-5 top (x 6-11) + bottom (x 12-17), rows 6-7 the sides
const px = [];
for (let y = 0; y < 6; y++) for (let x = 0; x < 6; x++) {
  px.push([6 + x, y, C.H]);
  const edge = x === 0 || x === 5 || y === 0 || y === 5; px.push([12 + x, y, edge ? ((x + y) % 2 ? C.I : C.F) : C.H]); }
const r0 = "DJJDJJ", r1 = "DIBDIB";
for (let x = 0; x < 24; x++) { px.push([x, 6, C[r0[x % 6]]]); px.push([x, 7, C[r1[x % 6]]]); }
await tools.mcp__blockbench__save_checkpoint({name: "before drill hub repaint"});
const hex = px.map(([x, y, c]) => (U + x).toString(16).padStart(2, "0") + (V + y).toString(16).padStart(2, "0") + c).join("");
return ev(`(() => { const h = '${hex}'; Texture.all[0].edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
  for (let i = 0; i < h.length; i += 10) { const x = parseInt(h.substr(i, 2), 16), y = parseInt(h.substr(i + 2, 2), 16), k = (y * canvas.width + x) * 4;
    d.data[k] = parseInt(h.substr(i + 4, 2), 16); d.data[k + 1] = parseInt(h.substr(i + 6, 2), 16); d.data[k + 2] = parseInt(h.substr(i + 8, 2), 16); d.data[k + 3] = 255; }
  ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Drill hub as the first auger step'}); return 1; })()`);
