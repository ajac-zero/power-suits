// Apply .scratch/elbow/plan.json (from elbow_art.py) to the live project. Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/elbow_apply.js"})).output)(tools);
//   await apply("/Users/ajac-zero/Developer/PowerSuits/.scratch/elbow/plan.json");
// Also applies pads_shell_art.py's plan: apply(path, "before shell pads", "Shell pads"). Checkpoint (default "before elbows"); paint (one undoable tex.edit); remove the old upper arms / joints (one edit); shorten the
// pads and gauntlets (one edit); place the new cubes.
return async function apply(planPath, checkpoint = "before elbows", label = "Elbows") {
  const plan = JSON.parse((await tools.bash({command: `cat '${planPath}'`})).output);
  const ev = code => tools.mcp__blockbench__risky_eval({code}).then(r => r.content[0].text);
  await tools.mcp__blockbench__save_checkpoint({name: checkpoint});
  const painted = await ev(`(() => { const tex = Texture.all[0]; const hex = "${plan.pixels}"; let n = 0;
    tex.edit(canvas => { const ctx = canvas.getContext("2d");
      for (let i = 0; i < hex.length; i += 12) { const v = j => parseInt(hex.substr(i + j, 2), 16);
        const id = ctx.createImageData(1, 1); id.data[0] = v(4); id.data[1] = v(6); id.data[2] = v(8); id.data[3] = v(10);
        ctx.putImageData(id, v(0), v(2)); n++; }
      return canvas; }, {edit_name: "${label} texture"});
    return n; })()`);
  const removed = await ev(`(() => { const names = ${JSON.stringify(plan.removed)};
    const gone = Cube.all.filter(c => names.includes(c.name));
    Undo.initEdit({elements: gone, outliner: true}); gone.forEach(c => c.remove()); Undo.finishEdit("${label}: remove old cubes");
    Canvas.updateAll(); return gone.length + " removed"; })()`);
  const edited = await ev(`(() => { const edits = ${JSON.stringify(plan.edits)}; const cs = edits.map(e => Cube.all.find(c => c.name === e.name));
    Undo.initEdit({elements: cs}); edits.forEach((e, i) => { cs[i].from.splice(0, 3, ...e.frm); cs[i].to.splice(0, 3, ...e.to); });
    Undo.finishEdit("${label}: resize cubes"); Canvas.updateAll(); return cs.length + " edited"; })()`);
  for (const c of plan.cubes) await tools.mcp__blockbench__place_cube({group: c.group, elements: [{name: c.name,
    from: c.from_, to: c.to, origin: c.origin, uv_offset: c.uv_offset, mirror_uv: c.mirror_uv,
    ...(c.inflate ? {inflate: c.inflate} : {}), ...(c.rotation ? {rotation: c.rotation} : {})}]});
  return painted + " px, " + removed + ", " + edited + ", " + plan.cubes.length + " placed";
};
