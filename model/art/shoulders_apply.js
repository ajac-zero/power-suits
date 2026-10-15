// Live Blockbench steps of the shoulder rework (2026-10-14), kept for reference / replay. Codemode:
//   const src = (await tools.bash({command: "cat model/art/shoulders_apply.js"})).output;
//   await new Function("tools", src)(tools)({step: "limits"});   // or "geometry", "all"
// Each step is one undoable Blockbench edit. Then shoulders_art.py (joints + blue upper arms) and
// collar_art.py (collars 3 -> 2 wide, resize them first). Verify with the blockbench-mcp skill's
// scripts/livesweep.js: shoulder joints vs pauldrons clean in all 23 animations.
//
// geometry: every arm part except the pauldron 2 px in toward the chest (arm pivots x +-11 -> +-9),
//   pauldrons 1 px up (room for a knee-sized joint under their top plate).
// limits (mobility of a heavy suit, user's call):
//   - pauldrons no longer move on their own: every right_pad / left_pad key zeroed (they bobbed up to
//     6 degrees in run and swung back 23 in use_item, through anything at the shoulder);
//   - arm abduction (raising the arm sideways, rotation Z away from the body) at most 6 degrees past
//     the rest pose (fall and jump flung the arms 30+ degrees; the pauldron's inner edge then dipped
//     into the collar and helmet);
//   - arm adduction (toward the body) at most 6 degrees past the rest pose (added with the arm refit,
//     arms_refit_art.py: hold_item / use_item pushed the gauntlet 1.6 px into the hips);
//   - feet: twist (Y) and roll (Z) within 10 degrees (added with the boots, boot_art.py: run twisted the
//     foot 28 degrees under the shin, through the ankle cuff). The ankle's bend (X) stays free: the feet
//     need up to 35 degrees of it to stay flat on the ground.
// Re-run "limits" after re-baking animations from model/anim/ (the bake would bring both back).
return async function apply({step = "all"} = {}) {
  const run = code => tools.mcp__blockbench__risky_eval({code}).then(r => r.content[0].text);
  const out = [];
  if (step === "geometry" || step === "all") out.push(await run(`(() => {
    const cubes = [], groups = [];
    for (const [arm, pad, dx] of [["right_arm", "right_pad", -2], ["left_arm", "left_pad", 2]]) {
      const g = Group.all.find(x => x.name === arm), p = Group.all.find(x => x.name === pad);
      const walk = n => { if (n === p) return; if (n instanceof Group) { groups.push([n, dx]); n.children.forEach(walk); } else if (n instanceof Cube) cubes.push([n, dx]); };
      walk(g);
      p.children.forEach(c => { if (c instanceof Cube) cubes.push([c, 0, 1]); }); groups.push([p, 0, 1]); }
    Undo.initEdit({elements: cubes.map(c => c[0]), outliner: true});
    for (const [c, dx, dy] of cubes) { c.from[0] += dx; c.to[0] += dx; c.origin[0] += dx; if (dy) { c.from[1] += dy; c.to[1] += dy; c.origin[1] += dy; } }
    for (const [g, dx, dy] of groups) { g.origin[0] += dx; if (dy) g.origin[1] += dy; }
    Undo.finishEdit("Arms 2 px in, pauldrons 1 px up"); Canvas.updateAll(); return "geometry ok"; })()`));
  if (step === "limits" || step === "all") out.push(await run(`(() => {
    const pads = [], arms = [], feet = [];
    Animation.all.forEach(a => Object.values(a.animators).forEach(an => {
      if (an.name === "right_pad" || an.name === "left_pad") an.keyframes.forEach(k => pads.push(k));
      if (an.name === "right_arm" || an.name === "left_arm") an.keyframes.filter(k => k.channel === "rotation").forEach(k => arms.push([k, an.name === "right_arm" ? 1 : -1]));
      if (an.name === "right_foot" || an.name === "left_foot") an.keyframes.filter(k => k.channel === "rotation").forEach(k => feet.push(k)); }));
    Undo.initEdit({keyframes: pads.concat(arms.map(a => a[0]), feet)});
    pads.forEach(k => ["x", "y", "z"].forEach(c => { k.data_points[0][c] = "0"; }));
    let n = 0;
    arms.forEach(([k, s]) => { const z = parseFloat(k.data_points[0].z) || 0; if (Math.abs(z) > 6) { k.data_points[0].z = String(Math.sign(z) * 6); n++; } });
    feet.forEach(k => ["y", "z"].forEach(c => { const v = parseFloat(k.data_points[0][c]) || 0; if (Math.abs(v) > 10) { k.data_points[0][c] = String(Math.sign(v) * 10); n++; } }));
    Undo.finishEdit("Mobility limits: rigid pauldrons, arm Z within 6 of rest, foot Y/Z within 10"); return "limits ok: " + pads.length + " pauldron keys zeroed, " + n + " arm / foot values clamped"; })()`));
  return out.join("\n");
};
