// Paint .scratch/pads-clean/plan.json (from pads_clean_art.py) into the live texture: one checkpoint, one undoable
// tex.edit. Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/pads_clean_apply.js"})).output)(tools);
//   await apply("/Users/ajac-zero/Developer/PowerSuits/.scratch/pads-clean/plan.json");
return async function apply(planPath, checkpoint = "before clean pads", label = "Clean pads") {
  const plan = JSON.parse((await tools.bash({command: `cat '${planPath}'`})).output);
  await tools.mcp__blockbench__save_checkpoint({name: checkpoint});
  const r = await tools.mcp__blockbench__risky_eval({code: `(() => { const tex = Texture.all[0]; const hex = "${plan.pixels}"; let n = 0;
    tex.edit(canvas => { const ctx = canvas.getContext("2d");
      for (let i = 0; i < hex.length; i += 12) { const v = j => parseInt(hex.substr(i + j, 2), 16);
        const id = ctx.createImageData(1, 1); id.data[0] = v(4); id.data[1] = v(6); id.data[2] = v(8); id.data[3] = v(10);
        ctx.putImageData(id, v(0), v(2)); n++; }
      return canvas; }, {edit_name: "${label} texture"});
    return n; })()`});
  return r.content[0].text + " px painted";
};
