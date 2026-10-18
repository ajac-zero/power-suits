// Apply .scratch/drum-hips-plan.json (from drum_hips_art.py) to the live project. Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/drum_hips_apply.js"})).output)(tools);
//   await apply("/Users/ajac-zero/Developer/PowerArmor/.scratch/drum-hips-plan.json");
// Checkpoint "before drum hips"; paint; remove the V / band / fill / tip, the hip joints, thigh_top and the old
// thigh_main; shorten the abdomen (now inside the hoods and the chest); create the hips / right_hood / left_hood bones
// under the root; place the new cubes.
return async function apply(planPath) {
  const plan = JSON.parse((await tools.bash({command: `cat '${planPath}' # ${Date.now()}`})).output);
  const ev = code => tools.mcp__blockbench__risky_eval({code}).then(r => r.content[0].text);
  await tools.mcp__blockbench__save_checkpoint({name: plan.checkpoint || "before drum hips"});
  const painted = await ev(`(() => { const tex = Texture.all[0]; const hex = "${plan.pixels}"; let n = 0;
    tex.edit(canvas => { const ctx = canvas.getContext("2d");
      for (let i = 0; i < hex.length; i += 12) { const v = j => parseInt(hex.substr(i + j, 2), 16);
        const id = ctx.createImageData(1, 1); id.data[0] = v(4); id.data[1] = v(6); id.data[2] = v(8); id.data[3] = v(10);
        ctx.putImageData(id, v(0), v(2)); n++; }
      return canvas; }, {edit_name: "Drum hips texture"});
    return n; })()`);
  const removed = await ev(`(() => { const names = ${JSON.stringify(plan.removed)}, edits = ${JSON.stringify(plan.edits)};
    const gone = Cube.all.filter(c => names.includes(c.name)), ed = edits.map(e => Cube.all.find(c => c.name === e.name));
    Undo.initEdit({elements: gone.concat(ed), outliner: true}); gone.forEach(c => c.remove());
    edits.forEach((e, i) => { ed[i].from.splice(0, 3, ...e.from_); ed[i].to.splice(0, 3, ...e.to); });
    Undo.finishEdit("Drum hips: remove the old hips and thigh tops, shorten the abdomen"); Canvas.updateAll(); return gone.length; })()`);
  const have = JSON.parse(JSON.parse(await ev(`JSON.stringify({groups: Group.all.map(g => g.name), cubes: Cube.all.map(c => c.name)})`)));
  for (const g of plan.groups) if (!have.groups.includes(g.name))
    await tools.mcp__blockbench__bone_rigging({action: "create", bone_data: {name: g.name, origin: g.origin, rotation: g.rotation, parent: g.parent}});
  for (const c of plan.restore || []) if (!have.cubes.includes(c.name) || plan.removed.includes(c.name))   // cubes the drum v1 removed
    await tools.mcp__blockbench__place_cube({group: c.group, elements: [{name: c.name, from: c.from_, to: c.to, origin: c.origin,
      uv_offset: c.uv_offset, mirror_uv: c.mirror_uv, ...(c.inflate ? {inflate: c.inflate} : {}), ...(c.rotation ? {rotation: c.rotation} : {})}]});
  for (const c of plan.cubes) await tools.mcp__blockbench__place_cube({group: c.group, elements: [{name: c.name,
    from: c.from_, to: c.to, origin: c.origin, uv_offset: c.uv_offset, mirror_uv: c.mirror_uv,
    ...(c.inflate ? {inflate: c.inflate} : {}), ...(c.rotation ? {rotation: c.rotation} : {})}]});
  return `${painted} px, ${removed} removed, ${plan.groups.length} bones, ${plan.cubes.length} placed`;
};
