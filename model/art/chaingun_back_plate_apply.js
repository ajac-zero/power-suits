// Chaingun back plate (user, 2026-10-26): with the 5x5 socket base the barrels looked like they spun on air. The four
// barrels lose 1 px at the back (y top 19.625 -> 18.625; their sides drop the top texel row, plain stripes, so the
// muzzle ring stays) and a second barrel plate, a copy of chaingun_plate_front (6x1x6, same texels), sits in the gap
// right under the base (y 18.625..19.625) on the spinning chaingun_barrels bone: the barrel cluster turns on that plate
// against the base. Codemode function body; one checkpoint, one undoable edit.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const state = await R(`(() => { const b = [1, 2, 3, 4].map(i => Cube.all.find(c => c.name === 'chaingun_barrel_' + i));
  if (b.some(c => !c)) return 'missing barrels';
  if (Cube.all.some(c => c.name === 'chaingun_plate_back')) return 'plate exists';
  if (b.some(c => Math.abs(c.to[1] - 19.625) > 1e-6 || !c.box_uv)) return 'barrels not as expected';
  return 'ok'; })()`);
if (state !== 'ok') return 'ABORT ' + state;
await tools.mcp__blockbench__save_checkpoint({name: "before chaingun back plate"});
return await R(`(() => {
  const barrels = [1, 2, 3, 4].map(i => Cube.all.find(c => c.name === 'chaingun_barrel_' + i));
  const front = Cube.all.find(c => c.name === 'chaingun_plate_front'), bone = front.parent, tex = Texture.all[0];
  const sides = ['north', 'east', 'south', 'west'];
  const uv = {}; for (const f of Object.keys(barrels[0].faces)) uv[f] = barrels[0].faces[f].uv.slice();
  for (const f of sides) { const [u0, v0, u1, v1] = uv[f]; uv[f] = v1 > v0 ? [u0, v0 + 1, u1, v1] : [u0, v0, u1, v1 + 1]; }
  Undo.initEdit({elements: barrels, outliner: true});
  for (const c of barrels) { c.box_uv = false; c.to = [c.to[0], 18.625, c.to[2]];
    for (const f in uv) { c.faces[f].uv = uv[f].slice(); c.faces[f].texture = tex.uuid; } }
  const plate = new Cube({name: 'chaingun_plate_back', from: [front.from[0], 18.625, front.from[2]], to: [front.to[0], 19.625, front.to[2]],
    origin: front.origin.slice(), box_uv: true, uv_offset: front.uv_offset.slice(), mirror_uv: front.mirror_uv, inflate: front.inflate}).addTo(bone).init();
  plate.applyTexture(tex, true);
  Undo.finishEdit('Chaingun back plate', {elements: barrels.concat([plate]), outliner: true});
  Canvas.updateAll();
  return barrels.map(c => c.name + ' ' + JSON.stringify([c.from, c.to, c.faces.north.uv])).concat([plate.name + ' ' + JSON.stringify([plate.from, plate.to, plate.parent.name])]);
})()`);
