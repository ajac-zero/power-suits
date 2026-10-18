// Solid chestplate in front, the furnace on the back (2026-10-18, user: "armoured in the front, more exposed in the back;
// keep the grated window"). Codemode function body (or the skill's scripts/bbrun.mjs model/art/back_furnace_apply.js).
//
// Front: the firebox (8x5x1, its grated coal window on its north face) sat on the sloped chest_glacis, whose own front
// is already a blue plate in a grey frame: taking the firebox away leaves that plate. The ember slits on chest_lower's
// front (3 slits x 2 rows in the reserved colour 281614) are painted over as blue plate, so the lower band is solid too.
// Back: the same firebox cube (texture and glowmask pixels unchanged) is turned 180 deg about Y so its window faces
// back, and set 0.05 proud of the backpack's back face (z 10; flush faces flicker) at x -4..4, y 30.625..35.625,
// under the exhaust stacks. New ember slits (4 x 2 px, 281614) go on the backpack under it, and the two grey latches above
// the window are painted over (with the window under them they read as a face).
// Glowmask: fire = 12 coal + 8 slit pixels now (tools/make-glowmasks.py EXPECTED).
const B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before back furnace"});
// texture pixels: chest_lower north face at (42,4) 13x4, rows 1-2 cols 1-11; backpack south face at (218,55) 14x11
const paint = [];
const PLATE = [["3a7bd6", "2d6bcc", "2358b2"], ["2d6bcc", "2358b2", "1b4895"]];
for (const [r, [hi, mid, lo]] of PLATE.entries())
  for (let c = 1; c <= 11; c++) paint.push([42 + c, 5 + r, c === 1 ? hi : c === 11 ? lo : mid, "chest_lower"]);
for (const c of [3, 5, 8, 10]) for (const r of [8, 9]) paint.push([218 + c, 55 + r, "281614", "backpack"]);
// the two grey latches above the window (rows 1-3) read as eyes over the grate's "mouth": painted over as the plate,
// the centre stripe's light rivets as the stripe
for (const r of [1, 2, 3]) { for (const c of [2, 3, 4, 9, 10, 11]) paint.push([218 + c, 55 + r, "1a1d1f", "latch"]);
  for (const c of [6, 7]) paint.push([218 + c, 55 + r, "2f3437", "latch"]); }
const res = await B({code: `(() => { const P = ${JSON.stringify(paint)}; let bad = [];
  const tex = Texture.all[0];
  tex.edit(canvas => { const ctx = canvas.getContext('2d');
    for (const [x, y, hex, part] of P) { const d = ctx.getImageData(x, y, 1, 1).data;
      const h = [0, 1, 2].map(i => d[i].toString(16).padStart(2, '0')).join('');
      if (part === 'backpack' && h !== '1a1d1f' && h !== '2f3437') bad.push(x + ',' + y + ':' + h); }
    if (bad.length) return canvas;
    for (const [x, y, hex] of P) { ctx.fillStyle = '#' + hex; ctx.fillRect(x, y, 1, 1); }
    return canvas; }, {edit_name: 'Back furnace: chest plate + ember slits'});
  if (bad.length) return 'ABORT ' + bad.join(' ');
  const fb = Cube.all.find(c => c.name === 'firebox');
  Undo.initEdit({elements: [fb]});
  fb.from = [-4, 30.625, 10.05]; fb.to = [4, 35.625, 11.05]; fb.origin = [0, 33.125, 10.55]; fb.rotation = [0, 180, 0];
  Undo.finishEdit('Firebox to the back');
  Canvas.updateAll(); return 'ok'; })()`});
return res.content[0].text;
