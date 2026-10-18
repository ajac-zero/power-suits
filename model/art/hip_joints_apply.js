// Hip joints (2026-10-17, user): the dark hip_core, a static block on the pelvis between the hips' V and the thighs,
// becomes one dark joint per leg on the leg bone, so it swings with its thigh instead of standing still while the legs
// move. Each is the knee joint's size and art (7 x 6 x 6.2, sharing its texture net), centred on the hip axis front to
// back (z 0; the left one inflate -0.05: when the legs line up their front faces met in one plane and flickered).
// It stays inside its own leg: x 1.25..8.25, between the thigh's inner face (x 1) and the outer top corner of the
// thigh's sloped top (8.25; further out it showed on the side of the hip). A first version reached the middle (x 0..7):
// the two joints slid through each other and showed between the legs (user). From y 17.6 (inside the thigh) to 23.6
// (just under the hip band): it shows only in the gap between the hips' V and the sloped thigh tops, and in the gaps
// that open between pelvis and thigh as the leg swings. No texture changes (hip_core's net is cleared).
// Codemode function body:
//   const apply = new Function("tools", (await tools.bash({command: "cat model/art/hip_joints_apply.js"})).output)(tools);
//   await apply();
return async function apply() {
  const ev = code => tools.mcp__blockbench__risky_eval({code}).then(r => r.content[0].text);
  await tools.mcp__blockbench__save_checkpoint({name: "before hip joints"});
  const gone = await ev(`(() => { const core = Cube.all.find(c => c.name === "hip_core"); if (!core) return "no hip_core";
    const [u, v] = core.uv_offset, tex = Texture.all[0];
    tex.edit(canvas => { canvas.getContext("2d").clearRect(u, v, 30, 10); return canvas; }, {edit_name: "Hip joints: free hip_core's net"});
    Undo.initEdit({elements: [core], outliner: true}); core.remove(); Undo.finishEdit("Hip joints: remove hip_core");
    Canvas.updateAll(); return "hip_core removed"; })()`);
  const knee = JSON.parse(JSON.parse(await ev(`JSON.stringify(Cube.all.filter(c => /^knee_joint_[rl]$/.test(c.name)).map(c => [c.name, c.uv_offset, !!c.mirror_uv]))`)));
  for (const [sd, sx] of [["r", 1], ["l", -1]]) {
    const k = knee.find(x => x[0] === "knee_joint_" + sd);
    const x0 = sx > 0 ? 1.25 : -8.25, x1 = sx > 0 ? 8.25 : -1.25;
    await tools.mcp__blockbench__place_cube({group: (sx > 0 ? "right" : "left") + "_leg", elements: [{name: "hip_joint_" + sd,
      from: [x0, 17.6, -3.1], to: [x1, 23.6, 3.1], origin: [sx * 5.5, 22.625, 0], uv_offset: k[1], mirror_uv: k[2], ...(sx < 0 ? {inflate: -0.05} : {})}]});
  }
  return gone + ", 2 hip joints placed";
};
