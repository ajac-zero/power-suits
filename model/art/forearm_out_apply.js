// Right forearm 0.5 px outward (2026-10-19, user, after elbow_forward_apply.js): the gauntlet sat 1.25 px inward of the
// shell pad's centre; moving the right elbow pivot and everything below it (joint, gauntlet, fist, hand) DX along x puts
// the forearm a little further out and the hand further from the thigh (previews at 0 / 0.5 / 1 / 1.25:
// .scratch/elbow2/right_out_0_05_1_125.jpg; hand-thigh contact 0.5: walk / run gone, idle_fidget 0.15, land 0.45,
// death 1.25). The pad stays; shapes and animations unchanged. The left (tool) arm is untouched.
// Codemode function body:
//   const out = new Function("tools", (await tools.bash({command: "cat model/art/forearm_out_apply.js"})).output)(tools);
//   await out("right_forearm", 0.5);
// Also moves along another axis: out("left_forearm", -2.25, 2) = the tool arm's elbow 2.25 px further forward (user,
// 2026-10-19: bent 62 deg, its joint looked too far back; previews .scratch/elbow2/left_elbow_fwd_more.jpg).
return async function out(bone = "right_forearm", DX = 0.5, AXIS = 0) {
  await tools.mcp__blockbench__save_checkpoint({name: `before ${bone} ${AXIS ? "forward" : "out"}`});
  const r = await tools.mcp__blockbench__risky_eval({code: `(() => {
    const root = Group.all.find(g => g.name === '${bone}'); const groups = [], cubes = [];
    const walk = g => { groups.push(g); g.children.forEach(ch => ch instanceof Group ? walk(ch) : cubes.push(ch)); }; walk(root);
    Undo.initEdit({groups, elements: cubes});
    groups.forEach(g => { g.origin[${AXIS}] += ${DX}; });
    cubes.forEach(c => { c.from[${AXIS}] += ${DX}; c.to[${AXIS}] += ${DX}; c.origin[${AXIS}] += ${DX}; });
    Undo.finishEdit("${bone} axis ${AXIS} ${DX}"); Canvas.updateAll();
    return JSON.stringify({groups: groups.map(g => g.name + ' ' + g.origin.join(',')), cubes: cubes.length}); })()`});
  return r.content[0].text;
};
