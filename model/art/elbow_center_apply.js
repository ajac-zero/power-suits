// Centre the elbow joints on the forearms (2026-10-19, user: "the joint is not centred on the hands"). The joint pieces
// (elbow_joint, elbow_joint_back, elbow_arc_*) were built 6.5 wide at x 7.75..14.25 to stay inside the pad's inner
// edge (7.5) while the gauntlet sat at 6.5..14.5: 0.5 px off its centre, toward the outside. After both forearms moved
// 0.5 px outward (forearm_out_apply.js) the joint can be centred on the gauntlet (pivot +-3.25) and still clear the pad's
// inner edge by 0.25. Moves only the joint cubes, along x, toward each arm's centre line.
// Codemode function body:
//   const c = new Function("tools", (await tools.bash({command: "cat model/art/elbow_center_apply.js"})).output)(tools);
//   await c();
return async function center() {
  await tools.mcp__blockbench__save_checkpoint({name: "before centred elbow joints"});
  const r = await tools.mcp__blockbench__risky_eval({code: `(() => { const out = [];
    const cubes = Cube.all.filter(c => /^elbow_.*_[rl]$/.test(c.name));
    Undo.initEdit({elements: cubes});
    for (const sd of ['r', 'l']) {
      const piv = Group.all.find(g => g.name === (sd === 'r' ? 'right_forearm' : 'left_forearm')).origin[0];
      const mine = cubes.filter(c => c.name.endsWith('_' + sd));
      const lo = Math.min(...mine.map(c => c.from[0])), hi = Math.max(...mine.map(c => c.to[0])), dx = piv - (lo + hi) / 2;
      mine.forEach(c => { c.from[0] += dx; c.to[0] += dx; c.origin[0] += dx; });
      out.push(sd + ' dx ' + dx.toFixed(3));
    }
    Undo.finishEdit('Centre elbow joints'); Canvas.updateAll(); return out.join(', '); })()`});
  return r.content[0].text;
};
