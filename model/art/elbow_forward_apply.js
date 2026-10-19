// Elbows forward (2026-10-19, user): the forearm + elbow joint move DZ along z (toward the front) under the unchanged
// shoulder pads, so the joint comes out of the middle of the pad's sloped underside instead of beside its back plate.
// 3 px was tried first and rejected (the forearms floated in front of the pads); the user chose 1.5 from previews at
// 0 / 1 / 1.5 / 2 (.scratch/elbow2/forward_0_1_15_2.jpg). Further forward the pad's underside is higher above the joint,
// so the joint's front box grows to JOINT_TOP above the pivot: its top front corner meets the slope at the right arm's
// 14 deg rest bend, t = ((6.418 + s) tan 30 - 2.75 sin 14) / (cos 14 + tan 30 sin 14) + 0.1 (s = -DZ). Moves the forearm bones' pivots (the elbow axis), every bone below them (hand, socket, tools, muzzle, barrels,
// bits, chain) and all their cubes by the same amount: the forearm's shape and every animation stay as they were.
// Codemode function body:
//   const fwd = new Function("tools", (await tools.bash({command: "cat model/art/elbow_forward_apply.js"})).output)(tools);
//   await fwd(-1.5);
return async function forward(DZ = -1.5, JOINT_TOP = null) {
  const s = -DZ, t = JOINT_TOP ?? ((6.418 + s) * Math.tan(Math.PI / 6) - 2.75 * Math.sin(14 * Math.PI / 180)) / (Math.cos(14 * Math.PI / 180) + Math.tan(Math.PI / 6) * Math.sin(14 * Math.PI / 180)) + 0.1;
  await tools.mcp__blockbench__save_checkpoint({name: "before elbows forward"});
  const r = await tools.mcp__blockbench__risky_eval({code: `(() => {
    const roots = Group.all.filter(g => g.name === 'right_forearm' || g.name === 'left_forearm');
    const groups = [], cubes = [];
    const walk = g => { groups.push(g); g.children.forEach(ch => ch instanceof Group ? walk(ch) : cubes.push(ch)); };
    roots.forEach(walk);
    Undo.initEdit({groups, elements: cubes});
    groups.forEach(g => { g.origin[2] += ${DZ}; });
    cubes.forEach(c => { c.from[2] += ${DZ}; c.to[2] += ${DZ}; c.origin[2] += ${DZ}; });
    cubes.filter(c => /^elbow_joint_[rl]$/.test(c.name)).forEach(c => { c.to[1] = 28.625 + ${t}; });
    Undo.finishEdit("Elbows forward ${DZ}");
    Canvas.updateAll();
    return JSON.stringify({groups: groups.map(g => g.name + ' ' + g.origin.join(',')), cubes: cubes.length});
  })()`});
  // A taller joint box gets more side rows in its box-UV net (floor of its height): repeat the old bottom row into them
  // (only into transparent pixels) so its sides show no holes.
  const p = await tools.mcp__blockbench__risky_eval({code: `(() => { const c = Cube.all.find(c => c.name === 'elbow_joint_r');
    const [w, h, d] = [0, 1, 2].map(i => Math.floor(c.to[i] - c.from[i] + 1e-6)), [u, v] = c.uv_offset, W = 2 * (w + d);
    let n = 0; Texture.all[0].edit(canvas => { const ctx = canvas.getContext('2d');
      let last = v + d; while (last + 1 < v + d + h && ctx.getImageData(u, last + 1, W, 1).data[3] !== 0) last++;
      const src = ctx.getImageData(u, last, W, 1);
      for (let y = last + 1; y < v + d + h; y++) { const dst = ctx.getImageData(u, y, W, 1);
        if (Array.from(dst.data).some((x, i) => i % 4 === 3 && x !== 0)) continue; ctx.putImageData(src, u, y); n += W; }
      return canvas; }, {edit_name: 'Elbow joint: side rows'}); return n + ' px'; })()`});
  return r.content[0].text + ' / joint rows ' + p.content[0].text;
};
