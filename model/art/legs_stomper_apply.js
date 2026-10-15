// Apply .scratch/stomper-plan.json (from legs_stomper_art.py) to the live project. Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/legs_stomper_apply.js"})).output)(tools);
//   await apply("/Users/ajac-zero/Developer/PowerArmor/.scratch/stomper-plan.json");
// Checkpoint "before stomper legs"; paint (one undoable tex.edit); in one undoable edit remove the old leg cubes,
// move the leg bones' pivots out and widen the pelvis shell; then place the new cubes.
return async function apply(planPath) {
  const plan = JSON.parse((await tools.bash({command: `cat '${planPath}'`})).output);
  const ev = code => tools.mcp__blockbench__risky_eval({code}).then(r => r.content[0].text);
  await tools.mcp__blockbench__save_checkpoint({name: "before stomper legs"});
  const painted = await ev(`(() => { const tex = Texture.all[0]; const hex = "${plan.pixels}"; let n = 0;
    tex.edit(canvas => { const ctx = canvas.getContext("2d");
      for (let i = 0; i < hex.length; i += 12) { const v = j => parseInt(hex.substr(i + j, 2), 16);
        const id = ctx.createImageData(1, 1); id.data[0] = v(4); id.data[1] = v(6); id.data[2] = v(8); id.data[3] = v(10);
        ctx.putImageData(id, v(0), v(2)); n++; }
      return canvas; }, {edit_name: "Stomper legs texture"});
    return n; })()`);
  const rebuilt = await ev(`(() => { const removed = ${JSON.stringify(plan.removed)}, groups = ${JSON.stringify(plan.groups)}, pel = ${JSON.stringify(plan.pelvis)};
    const gone = Cube.all.filter(c => removed.includes(c.name)); const shell = Cube.all.find(c => c.name === pel.name);
    Undo.initEdit({elements: gone.concat([shell]), outliner: true});
    gone.forEach(c => c.remove());
    Group.all.forEach(g => { if (groups[g.name]) g.origin.splice(0, 3, ...groups[g.name]); });
    shell.from.splice(0, 3, ...pel.from_); shell.to.splice(0, 3, ...pel.to); shell.uv_offset = pel.uv_offset.slice();
    Undo.finishEdit("Stomper legs: remove old leg cubes, leg pivots out, wider pelvis");
    Canvas.updateAll(); return gone.length + " removed"; })()`);
  for (const c of plan.cubes) await tools.mcp__blockbench__place_cube({group: c.group, elements: [{name: c.name,
    from: c.from_, to: c.to, origin: c.origin, uv_offset: c.uv_offset, mirror_uv: c.mirror_uv,
    ...(c.inflate ? {inflate: c.inflate} : {}), ...(c.rotation ? {rotation: c.rotation} : {})}]});
  return painted + " px, " + rebuilt + ", " + plan.cubes.length + " placed";
};
