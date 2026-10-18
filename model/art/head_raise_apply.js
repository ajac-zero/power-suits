// Head raised, model kept under 3 blocks, chest front cleared (2026-10-18, user: "I prefer 3 px but keep the model under
// 3 blocks; shave the top layer off the helmet; remove chest_glacis and chest_lower"). Codemode function body.
// Removal and move are separate undo steps: in one step (with the head's groups) a redo brought the dome back.
//
// The top is set by the pilot's bare head, not the helmet: it is 8 px tall and the helmet can be shaved, the head can't.
// Raised 3 px its top would be at 48.625 (3 blocks = 48: the soles stand at y 0), so the head goes up 2.375 (top at
// 48.0, 0.6 px short of the 3 px look, the chin 0.6 px under the chest's top edge). The helmet goes up with it and loses
// its 1 px dome (shell top at 48.0). The exhaust stacks were already over (caps at 48.78 since the torso stands
// upright): 1 px lower (their bottoms sit inside the backpack). chest_glacis and chest_lower go (their nets are free).
// Cockpit eye: 41.6 + 2.375 px = 2.75 blocks (ModEntities eyeHeight, at export).
const UP = 2.375;
const B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before head raise"});
const r = await B({code: `(() => {
  const G = n => Group.all.find(g => g.name === n);
  const subG = g => [g, ...g.children.filter(c => c instanceof Group).flatMap(subG)];
  const head = G('head'), groups = subG(head);
  const gone = Cube.all.filter(c => ['helmet_dome', 'chest_glacis', 'chest_lower'].includes(c.name));
  const cubes = groups.flatMap(g => g.children.filter(c => c instanceof Cube && !gone.includes(c)));
  const ex = Cube.all.filter(c => /^exhaust_(cap_)?[lr]$/.test(c.name));
  if (gone.length !== 3 || ex.length !== 4) return 'ABORT ' + gone.length + ' ' + ex.length;
  Undo.initEdit({elements: gone, outliner: true});
  gone.forEach(c => c.remove());
  Undo.finishEdit('Remove helmet dome, chest glacis, chest lower', {elements: [], outliner: true});
  Undo.initEdit({groups, elements: [...cubes, ...ex]});
  for (const g of groups) g.origin[1] += ${UP};
  for (const c of cubes) { c.from[1] += ${UP}; c.to[1] += ${UP}; c.origin[1] += ${UP}; }
  for (const c of ex) { c.from[1] -= 1; c.to[1] -= 1; c.origin[1] -= 1; }
  Undo.finishEdit('Head raised, exhausts lowered');
  Canvas.updateAll();
  Outliner.root.forEach(n => n.mesh && n.mesh.updateMatrixWorld(true));
  const top = Cube.all.filter(c => c.export !== false || c.name === 'pilot_head_standin')
    .map(c => [c.name, +new THREE.Box3().setFromObject(c.mesh).max.y.toFixed(3)]).sort((a, b) => b[1] - a[1]).slice(0, 5);
  return JSON.stringify(top); })()`});
return r.content[0].text;
