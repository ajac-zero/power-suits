// Nape cut to its visible strips (2026-10-19, user: "the nape overlaps with the helmet a lot", option B).
// Codemode function body; returns async function strips(opts). opts {middle: true} also builds the cut-out middle
// (nape_m) so the result can be compared pixel for pixel with the whole nape (the mapping check); {middle: false}
// leaves the collar open.
//
// The old nape (12x4x7, x -6..6, y 39.625..43.625, z -2..5, inflate -0.05) came from the marine model: a solid neck under
// a closed helmet. 9 of its 12 px of width sit inside the helmet shell (x -4.5..4.5) and bury the lower half of the
// pilot's head; with the helmet on only the 1.5 px strips beside the shell show (head pitched +-20, rolled 19 / yawed
// 18: hiding the middle changes nothing, the chest top and chest_rim_b sit under it). So the nape becomes nape_r /
// nape_l (x +-4.5..6, same height, depth and inflate). Every piece uses per-face UV cut out of the nape's own box-UV
// faces (no new texture pixels), as in neck_notch_apply.js: a piece's face is the linear sub-range of the parent face
// over the piece's extent. The strips' inner faces (facing the head) cover the nape's full depth and height, so they
// get the nape's same-facing side face whole.
const E = {north: ["x", 1, "y", 1], south: ["x", 0, "y", 1], east: ["z", 1, "y", 1], west: ["z", 0, "y", 1],
           up: ["x", 0, "z", 0], down: ["x", 0, "z", 1]};   // face: [u axis, u1 at max?, v axis, v1 at max?]
const AX = {x: 0, y: 1, z: 2};
return async function strips(opts = {}) {
  const B = tools.mcp__blockbench__risky_eval;
  const n = JSON.parse(JSON.parse((await B({code: "JSON.stringify((() => { const c = Cube.all.find(c => c.name === 'nape'); return c ? {from: c.from, to: c.to, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, f.uv]))} : null; })())"})).content[0].text));
  if (!n) return "ABORT no nape";
  const sub = (face, from, to) => { const [ua, uMax, va, vMax] = E[face], uv = n.faces[face];
    const t = (ax, atMax, val) => { const i = AX[ax], lo = n.from[i], hi = n.to[i]; const s = (val - lo) / (hi - lo); return atMax ? 1 - s : s; };
    const a = (ax, atMax, from_, to_) => { const i = AX[ax]; return [t(ax, atMax, atMax ? to_[i] : from_[i]), t(ax, atMax, atMax ? from_[i] : to_[i])]; };
    const [u0, u1] = a(ua, uMax, from, to), [v0, v1] = a(va, vMax, from, to);
    return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]; };
  const SX = 4.5, [x0, y0, z0] = n.from, [x1, y1, z1] = n.to;
  const pieces = [["nape_r", [SX, y0, z0], [x1, y1, z1]], ["nape_l", [x0, y0, z0], [-SX, y1, z1]]];
  if (opts.middle) pieces.push(["nape_m", [-SX, y0, z0], [SX, y1, z1]]);
  const spec = pieces.map(([name, from, to]) => ({name, from, to, faces: Object.fromEntries(Object.keys(E).map(f => [f, sub(f, from, to)]))}));
  await tools.mcp__blockbench__save_checkpoint({name: "before nape strips"});
  const r = await B({code: `(() => { const S = ${JSON.stringify(spec)};
    const nape = Cube.all.find(c => c.name === 'nape'), parent = nape.parent, tex = Texture.all[0];
    if (Cube.all.some(c => /^nape_/.test(c.name))) return 'ABORT strips exist';
    Undo.initEdit({elements: [nape], outliner: true});
    const made = [];
    for (const s of S) {
      const c = s.name === 'nape_r' ? nape : new Cube({name: s.name, origin: nape.origin.slice(), inflate: nape.inflate}).addTo(parent).init();
      c.name = s.name; c.box_uv = false; c.inflate = nape.inflate; c.from = s.from; c.to = s.to;
      for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f]; }
      made.push(c); }
    Undo.finishEdit('Nape cut to the strips beside the helmet', {elements: made, outliner: true});
    Canvas.updateAll(); return 'ok ' + made.map(c => c.name).join(','); })()`});
  return r.content[0].text;
};
