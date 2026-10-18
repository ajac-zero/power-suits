// Breastplate shape prototypes (2026-10-18, after chest_glacis / chest_lower were removed): untextured blocks (flat
// colours sampled from the old glacis net, per-face UV) in groups proto_<id> under `torso`, for side-by-side shots.
// Codemode function body; returns the ids. Delete the proto_* groups (or undo to "before breastplate protos") after.
// Torso frame: chest block x +-7.5, y 27.625..40.625, front z -5; the pilot's face z -3.5, chin y 40; the arms hang
// from x +-9, so nothing in front goes wider than +-6.5 (+-7 for a trim at the bottom).
const SW = {L: [116, 116], M: [116, 117], D: [119, 117], DD: [120, 117], G: [117, 117]};   // 62a3ef 3a7bd6 2d6bcc 2358b2 a2acb1
const PROTOS = {
  // A keel: plan view convex, a vertical ridge down the middle (Gothic breastplate), flared grey lip at the bottom
  A: [[-6.5, 29.125, -6, 6.5, 39.625, -5, "D"], [-4.5, 29.625, -7, 4.5, 39.125, -6, "M"], [-1.5, 30.125, -8, 1.5, 38.625, -7, "L"],
      [-7, 28.125, -6.5, 7, 29.125, -4.5, "G"]],
  // C barrel: convex both ways (a boiler's dome), each step 1 px out and narrower / shorter
  C: [[-6.5, 29.125, -6, 6.5, 39.625, -5, "DD"], [-5, 30.125, -7, 5, 38.625, -6, "D"], [-3, 31.125, -8, 3, 37.125, -7, "M"],
      [-7, 28.125, -6.5, 7, 29.125, -4.5, "G"]],
  // D plates + lames: an upper breastplate, a plackart overlapping it from below, one lame over the abdomen
  D: [[-6.5, 33.625, -7, 6.5, 39.625, -5, "M"], [-5.5, 30.625, -7.5, 5.5, 34.625, -5, "D"], [-6, 28.125, -6.5, 6, 31.125, -5, "DD"],
      [-6.6, 31.025, -6.6, 6.6, 31.225, -5, "G"], [-5.6, 34.525, -7.6, 5.6, 34.725, -7, "G"]],
};
const B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before breastplate protos"});
const r = await B({code: `(() => { const P = ${JSON.stringify(PROTOS)}, SW = ${JSON.stringify(SW)};
  const torso = Group.all.find(g => g.name === 'torso'), tex = Texture.all[0], made = [];
  Undo.initEdit({outliner: true, elements: []});
  for (const [id, cubes] of Object.entries(P)) {
    const g = new Group({name: 'proto_' + id, origin: [0, 27.125, 0.5]}).addTo(torso).init();
    cubes.forEach(([x0, y0, z0, x1, y1, z1, s], i) => {
      const c = new Cube({name: 'proto_' + id + '_' + i, from: [x0, y0, z0], to: [x1, y1, z1], origin: [0, 27.125, 0.5], box_uv: false}).addTo(g).init();
      c.export = false; c.visibility = false;
      const [u, v] = SW[s];
      for (const f in c.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = [u + 0.25, v + 0.25, u + 0.75, v + 0.75]; }
      made.push(c); });
  }
  Undo.finishEdit('Breastplate prototypes', {outliner: true, elements: made});
  Canvas.updateAll(); return Object.keys(P).join(','); })()`});
return r.content[0].text;
