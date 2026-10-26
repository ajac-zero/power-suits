// Rail cannon: the two prongs drawn as exact mirror images, the band's emitter centred on the slot (2026-10-26, user:
// "the pixels are a little off, off center and the prongs are different sizes, most visible in rail_prong_0 in and out").
// Codemode function body (or the skill's scripts/bbrun.mjs model/art/rail_mirror_apply.js). Idempotent: run it again
// after rebuilding the prongs with rail_drafts_art.py (fork_segments), which packs each prong's faces on its own texels.
//
// Why they differed: the segments are 2.5 / 2 / 1.5 px wide, so a face shows 2.5 texels and the half texel lands at
// the face's u-end. Blockbench counts u from +x on a north face, so on the outer prong (x -16..-13.5) the half texel
// sat on its outer edge (light 1 px along the slot, dark 1/2 px outside) but on the inner one (x -9.5..-7) on its slot
// edge (dark 1 px outside, light 1/2 px): the two prongs looked different widths. The sides were painted from the same
// strip with u running the other way round, so one prong was lit from the top and the other from the bottom.
// Fix: each inner-prong face uses its outer twin's texels with u reversed (east <-> west), so it is the mirror image
// texel for texel; its own old texels are cleared. Measure with rail_symcheck.py (mirror check of every face).
//
// The band's underside (rail_band_0 down, shared by rail_band_1) is 9.5 x 3.5 px: on a grid starting at its corner the
// hole was 1/4 px off the slot and the emitter 1/4 px off centre both ways. Its texels move to a fresh 10 x 5 block
// (240..249 x 121..125) mapped from u 240.25 / v 121.75, so texel edges fall on the slot's edges (x -13.5 / -9.5,
// z 0 / -3): copper rim, a 4 x 3 dark hole, the 2 x 1 emitter in its middle (x -12.5..-10.5, z -2..-1), same colours.
// The receiver's visible row was shaded light-to-dark across (lit from one side, 4.1 texels wide): 4 texels stretched
// over its 4.1 px, light edges both sides.
const B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before rail cannon symmetry"});
const code = `(() => {
 const C = n => Cube.all.find(c => c.name === n);
 const rev = uv => [uv[2], uv[1], uv[0], uv[3]];
 const tex = Texture.all[0];
 const ins = [0,1,2].map(i => C('rail_prong_in_' + i)), outs = [0,1,2].map(i => C('rail_prong_out_' + i));
 const bands = [C('rail_band_0'), C('rail_band_1')], rec = C('rail_receiver');
 const inUse = (u, v, skip) => Cube.all.some(c => Object.keys(c.faces).some(f => { if (skip.includes(c)) return false; const uv = c.faces[f].uv;
   return u + 1 > Math.min(uv[0], uv[2]) && u < Math.max(uv[0], uv[2]) && v + 1 > Math.min(uv[1], uv[3]) && v < Math.max(uv[1], uv[3]); }));
 const clear = [];
 const freeRect = (uv, skip) => { for (let v = Math.floor(Math.min(uv[1], uv[3])); v < Math.ceil(Math.max(uv[1], uv[3])); v++)
   for (let u = Math.floor(Math.min(uv[0], uv[2])); u < Math.ceil(Math.max(uv[0], uv[2])); u++) if (!inUse(u, v, skip)) clear.push([u, v]); };
 ins.forEach(c => { for (const f in c.faces) freeRect(c.faces[f].uv, ins); });
 bands.forEach(b => { if (b.faces.down.uv[0] !== 240.25) freeRect(b.faces.down.uv, bands); });
 const CU = '#7a3f22', D = '#2f3437', Z = '#183850';
 tex.edit(canvas => { const ctx = canvas.getContext('2d');
   clear.forEach(p => ctx.clearRect(p[0], p[1], 1, 1));
   for (let r = 0; r < 5; r++) for (let k = 0; k < 10; k++) {
     const hole = r >= 1 && r <= 3 && k >= 3 && k <= 6, emit = r === 2 && (k === 4 || k === 5);
     ctx.fillStyle = emit ? Z : hole ? D : CU; ctx.fillRect(240 + k, 121 + r, 1, 1); }
   [[204, 64], [133, 70]].forEach(([u, v]) => ['#5d666b', '#434b4f', '#434b4f', '#5d666b'].forEach((col, k) => {
     ctx.fillStyle = col; ctx.fillRect(u + k, v, 1, 1); }));
   return canvas; }, {edit_name: 'rail cannon symmetric texels'});
 Undo.initEdit({elements: [...ins, ...bands, rec]});
 ins.forEach((n, i) => { const o = outs[i].faces;
   ['north', 'south', 'up', 'down'].forEach(f => n.faces[f].uv = rev(o[f].uv));
   n.faces.east.uv = rev(o.west.uv); n.faces.west.uv = rev(o.east.uv); });
 bands.forEach(b => b.faces.down.uv = [240.25, 121.75, 249.75, 125.25]);
 rec.faces.north.uv = [204, 63, 208, 65.95]; rec.faces.south.uv = [133, 69, 137, 71.95];
 [...ins, ...bands, rec].forEach(c => c.preview_controller.updateUV(c));
 Undo.finishEdit('rail cannon: mirror-symmetric UVs');
 return JSON.stringify({cleared: clear.length}); })()`;
return (await B({code})).content[0].text;
