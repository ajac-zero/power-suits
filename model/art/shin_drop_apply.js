// Apply .scratch/shin-drop-plan.json (from shin_drop_art.py) to the live project. Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/shin_drop_apply.js"})).output)(tools);
//   await apply("/Users/ajac-zero/Developer/PowerArmor/.scratch/shin-drop-plan.json");
// Checkpoint "before shorter shins"; paint (one undoable tex.edit); move/shorten the shin cubes in one undoable edit;
// then place the lower knee joint boxes.
return async function apply(planPath) {
  const plan = JSON.parse((await tools.bash({command: `cat '${planPath}'`})).output);
  const ev = code => tools.mcp__blockbench__risky_eval({code}).then(r => r.content[0].text);
  await tools.mcp__blockbench__save_checkpoint({name: "before shorter shins"});
  const painted = await ev(`(() => { const tex = Texture.all[0]; const hex = "${plan.pixels}"; let n = 0;
    tex.edit(canvas => { const ctx = canvas.getContext("2d");
      for (let i = 0; i < hex.length; i += 12) { const v = j => parseInt(hex.substr(i + j, 2), 16);
        const id = ctx.createImageData(1, 1); id.data[0] = v(4); id.data[1] = v(6); id.data[2] = v(8); id.data[3] = v(10);
        ctx.putImageData(id, v(0), v(2)); n++; }
      return canvas; }, {edit_name: "Shorter shins texture"});
    return n; })()`);
  const moved = await ev(`(() => { const edits = ${JSON.stringify(plan.edits)};
    const cubes = edits.map(e => Cube.all.find(c => c.name === e.name));
    if (cubes.some(c => !c)) return "missing cube";
    Undo.initEdit({elements: cubes});
    edits.forEach((e, i) => { const c = cubes[i]; c.from.splice(0, 3, ...e.from_); c.to.splice(0, 3, ...e.to); c.origin.splice(0, 3, ...e.origin); });
    Undo.finishEdit("Shorter shins: shin top down");
    Canvas.updateAll(); return cubes.length + " edited"; })()`);
  for (const c of plan.cubes) await tools.mcp__blockbench__place_cube({group: c.group, elements: [{name: c.name,
    from: c.from_, to: c.to, origin: c.origin, uv_offset: c.uv_offset, mirror_uv: c.mirror_uv, inflate: c.inflate}]});
  return painted + " px, " + moved + ", " + plan.cubes.length + " placed";
};
