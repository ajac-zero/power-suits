// 1 px neck notch in the chest's top front (2026-10-18, user: "a 1 px notch in the chest to give more space" for the
// pilot's face). Codemode function body; opts {front: true} also builds the notch's filler (chest_rim_f) so the result
// can be compared pixel for pixel with the unsplit chest (the mapping check), {front: false} leaves the notch open.
//
// The chest (15x13x11, box UV, mirrored) is split, keeping its art exactly: the body comes down 1 px (y 27.625..39.625)
// and the top 1 px layer becomes three rims around the notch (left / right x 4.5..7.5, back x -4.5..4.5 behind z -3,
// where the collars stand). The notch is x -4.5..4.5 (the helmet shell's width), z -5..-3, y 39.625..40.625.
// Every piece uses per-face UV cut out of the chest's own box-UV faces (no new texture pixels): face u1 / v1 sit on a
// fixed edge of each face (Blockbench's convention; the mirror flag only swaps the numbers), so a piece's face is the
// linear sub-range of the parent face over the piece's extent.
const E = {north: ["x", 1, "y", 1], south: ["x", 0, "y", 1], east: ["z", 1, "y", 1], west: ["z", 0, "y", 1],
           up: ["x", 0, "z", 0], down: ["x", 0, "z", 1]};   // face: [u axis, u1 at max?, v axis, v1 at max?]
const AX = {x: 0, y: 1, z: 2};
return async function notch(opts = {}) {
  const B = tools.mcp__blockbench__risky_eval;
  const ch = JSON.parse(JSON.parse((await B({code: "JSON.stringify((() => { const c = Cube.all.find(c => c.name === 'chest'); return {from: c.from, to: c.to, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, f.uv]))}; })())"})).content[0].text));
  const sub = (face, from, to) => { const [ua, uMax, va, vMax] = E[face], uv = ch.faces[face];
    const t = (ax, atMax, val) => { const i = AX[ax], lo = ch.from[i], hi = ch.to[i]; const s = (val - lo) / (hi - lo); return atMax ? 1 - s : s; };
    const a = (ax, atMax, from_, to_) => { const i = AX[ax]; return [t(ax, atMax, atMax ? to_[i] : from_[i]), t(ax, atMax, atMax ? from_[i] : to_[i])]; };
    const [u0, u1] = a(ua, uMax, from, to), [v0, v1] = a(va, vMax, from, to);
    return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]; };
  const Y0 = 39.625, Y1 = 40.625, NX = 4.5, NZ = -3;
  const pieces = [
    ["chest", [-7.5, 27.625, -5], [7.5, Y0, 6]],
    ["chest_rim_l", [-7.5, Y0, -5], [-NX, Y1, 6]],
    ["chest_rim_r", [NX, Y0, -5], [7.5, Y1, 6]],
    ["chest_rim_b", [-NX, Y0, NZ], [NX, Y1, 6]],
  ];
  if (opts.front) pieces.push(["chest_rim_f", [-NX, Y0, -5], [NX, Y1, NZ]]);
  const spec = pieces.map(([name, from, to]) => ({name, from, to, faces: Object.fromEntries(Object.keys(E).map(f => [f, sub(f, from, to)]))}));
  await tools.mcp__blockbench__save_checkpoint({name: "before neck notch"});
  const r = await B({code: `(() => { const S = ${JSON.stringify(spec)};
    const chest = Cube.all.find(c => c.name === 'chest'), torso = chest.parent, tex = Texture.all[0];
    if (Cube.all.some(c => /^chest_rim_/.test(c.name))) return 'ABORT rims exist';
    Undo.initEdit({elements: [chest], outliner: true});
    const made = [];
    for (const s of S) {
      const c = s.name === 'chest' ? chest : new Cube({name: s.name, origin: chest.origin.slice()}).addTo(torso).init();
      c.box_uv = false; c.from = s.from; c.to = s.to;
      for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f]; }
      made.push(c); }
    Undo.finishEdit('Neck notch: chest split into body + rims', {elements: made, outliner: true});
    Canvas.updateAll(); return 'ok ' + made.length; })()`});
  return r.content[0].text;
};
