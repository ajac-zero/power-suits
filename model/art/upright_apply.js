// Upright upper body (2026-10-18, user: "fully upright, the head in the middle of the chest, the pads can stay").
// Codemode function body (or the skill's scripts/bbrun.mjs model/art/upright_apply.js).
//
// Before: body -2 + torso -5 = 7 deg forward lean, the head turned back +6 to look level (net -1), its pivot 3 px in
// front of the torso's, so the helmet stuck out ~6 px in front of the chest between the pads ("hunched").
// After: body, torso and head rest rotations 0; the head bone (helmet, face) and the collars beside it move 4 px back,
// which centres the helmet shell (z -4..5) and the pilot's head (8 deep, z -3.5..4.5) over the chest block (z -5..6).
// The arms keep their world angle (they hang beside the hips, fitted to the thighs): new rest = Rx(-7) * old rest,
// re-decomposed in Blockbench's ZYX order (right [10,0,15] -> [3.24,1.81,14.89], left [18,0,-18] -> [11.34,-2.16,-17.87]).
// The shoulders move ~1.5 px back with the straightened torso. Animations add to the rest rotations, so every pose
// straightens the same way (they get re-baked after the rework anyway).
const DZ = 4;
const B = tools.mcp__blockbench__risky_eval;
// Undo.initEdit needs {groups} to undo rotations/pivots ({outliner: true} did not).
await tools.mcp__blockbench__save_checkpoint({name: "before upright"});
const r = await B({code: `(() => {
  const G = n => Group.all.find(g => g.name === n);
  const sub = g => g.children.flatMap(c => c instanceof Group ? sub(c) : [c]);
  const head = G('head'), moved = [...sub(head), ...Cube.all.filter(c => /^collar_[lr]$/.test(c.name))];
  const groups = ['body', 'torso', 'head', 'helmet', 'right_arm', 'left_arm'].map(G);
  Undo.initEdit({groups, elements: moved});
  G('body').rotation = [0, 0, 0]; G('torso').rotation = [0, 0, 0]; head.rotation = [0, 0, 0];
  G('right_arm').rotation = [3.24, 1.81, 14.89]; G('left_arm').rotation = [11.34, -2.16, -17.87];
  for (const g of [head, ...head.children.filter(c => c instanceof Group)]) g.origin[2] += ${DZ};
  for (const c of moved) { c.from[2] += ${DZ}; c.to[2] += ${DZ}; c.origin[2] += ${DZ}; }
  Undo.finishEdit('Upright upper body');
  Canvas.updateAll();
  return moved.map(c => c.name).join(',');
})()`});
return r.content[0].text;
