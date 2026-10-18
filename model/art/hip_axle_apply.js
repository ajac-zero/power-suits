// Hip axle (2026-10-18, user: the thin dark plate between the hoods, 0.5 x 8 x 9 and hanging below them, should be
// "short and wide, a more physical connector between the two legs"). Codemode function body (or the skill's
// scripts/bbrun.mjs model/art/hip_axle_apply.js). Replaces `hip_centre` (made by drum_hips_apply.js: re-run this after it).
//
// hip_axle      5x4x5 dark joint (the shoulder/elbow joints' size and texture net) on the static `hips` bone, centred
//               on the hip axis. Its ends reach 1.5 px into each thigh's drum, inside the drum's circle (radius 4, the
//               corners sit at 3.2), so the thighs turn around it in every pose without its ends showing.
// hip_axle_top  2 px wide post on top of it, front face flush, up to 1 px under the chest / hoods (y 27.625): the
//               body bone dips the chest 0.6 px in the run, 0.17 in stim / shutdown / powered_off, so a top at 26.55
//               clears every animation but death (0.17). Only 2 wide: above the axis the drums' inner ends come within
//               0.35 px of the centre in the walk / run (gait roll), so a wider post would stick out of the drums' tops;
//               this one lets them dip into its sides (reads as the drums turning on a shaft). Own net at [180, 37]
//               (12x6, floored box UV 2x2x4), painted from the shoulder joint's net (copy below).
// Checks used: the skill's scripts/densesweep.js (post vs hood/chest/abdomen: 0 in all animations but death).
const B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before hip axle"});
await B({code: `(() => { Cube.all.filter(c => /^hip_(centre|axle|axle_top)$/.test(c.name)).forEach(c => c.remove()); Canvas.updateAll(); return 1; })()`});
// Net for the post: top/bottom/sides cropped from the joint net at [100, 78] (5x4x5: top 105,78; bottom 110,78; sides y 83).
const paint = await B({code: `(() => { const t = Texture.all[0]; let res = 'ok';
  t.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(180, 37, 12, 6);
    for (let i = 0; i < d.data.length; i += 4) if (d.data[i + 3]) { res = 'net area not empty'; return canvas; }
    const copy = [[106, 78, 2, 4, 4, 0], [111, 79, 2, 4, 6, 0], [100, 84, 4, 2, 0, 4], [106, 84, 2, 2, 4, 4], [110, 84, 4, 2, 6, 4], [116, 84, 2, 2, 10, 4]];
    for (const [sx, sy, w, h, dx, dy] of copy) ctx.putImageData(ctx.getImageData(sx, sy, w, h), 180 + dx, 37 + dy);
    return canvas; }, {edit_name: 'Paint hip_axle_top net'});
  return res; })()`});   // 'net area not empty' on a re-run: the net is already there, the cubes reuse it
await tools.mcp__blockbench__place_cube({group: "hips", texture: "marine.png", elements: [
  {name: "hip_axle", from: [-2.5, 20.625, -2.5], to: [2.5, 24.625, 2.5], origin: [0, 22.625, 0], uv_offset: [100, 78], mirror_uv: true},
  {name: "hip_axle_top", from: [-1, 24.125, -2.5], to: [1, 26.55, 2.0], origin: [0, 22.625, 0], uv_offset: [180, 37], mirror_uv: true, inflate: -0.05}]});
return paint.content[0].text;
