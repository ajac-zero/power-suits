// Apply .scratch/knee-plan.json (from knee_cut_art.py) to the live project. Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/knee_cut_apply.js"})).output)(tools);
//   await apply("/Users/ajac-zero/Developer/PowerArmor/.scratch/knee-plan.json");
// Checkpoint, then paint (one undoable tex.edit), resize/re-net the existing knee cubes, place the new ones.
return async function apply(planPath, checkpoint = "before knee cutout") {
  const plan = JSON.parse((await tools.bash({command: `cat '${planPath}'`})).output);
  await tools.mcp__blockbench__save_checkpoint({name: checkpoint});
  const paint = `(() => { const tex = Texture.all[0]; const hex = "${plan.pixels}"; let n = 0;
    tex.edit(canvas => { const ctx = canvas.getContext("2d");
      for (let i = 0; i < hex.length; i += 12) { const v = j => parseInt(hex.substr(i + j, 2), 16);
        const id = ctx.createImageData(1, 1); id.data[0] = v(4); id.data[1] = v(6); id.data[2] = v(8); id.data[3] = v(10);
        ctx.putImageData(id, v(0), v(2)); n++; }
      return canvas; }, {edit_name: "Knee cutout texture"});
    return n; })()`;
  const painted = await tools.mcp__blockbench__risky_eval({code: paint});
  for (const e of plan.edits) await tools.mcp__blockbench__modify_cube({id: e.name, from: e.from_, to: e.to,
    ...(e.uv_offset ? {uv_offset: e.uv_offset} : {}), ...(e.inflate !== undefined ? {inflate: e.inflate} : {})});
  for (const c of plan.cubes) await tools.mcp__blockbench__place_cube({group: c.group, elements: [{name: c.name,
    from: c.from_, to: c.to, origin: c.origin, uv_offset: c.uv_offset, mirror_uv: c.mirror_uv,
    ...(c.inflate ? {inflate: c.inflate} : {})}]});
  return painted.content[0].text;
};
