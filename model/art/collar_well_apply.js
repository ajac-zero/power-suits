// Collar well (2026-10-19, user: "the chest is still covering part of the helmet"). Codemode function body, run BEFORE
// collar_ring_art.py with WELL = True (which takes the collar walls and back down to the new floor).
//
// The helmet's bottom is y 38.95 and its grill y 39..41, but the floor inside the collar was the chest top (39.625, in the
// neck notch) and the chest rims (40.625): 0.7 px of the helmet hidden at the front (the grill's bottom row) and 1.7 px on
// the sides and back. The chest's top 1 px is cut away inside the collar, down to the breastplate's top (38.625), which is
// also the floor of the groove behind the neck guard: one flat floor, the whole helmet above it.
//   chest          y 31.625..38.625 (was ..39.625), the whole width
//   chest_top_r/l  x 6.6..7.5, y 38.625..39.625     the chest's old top layer outside the collar walls
//   chest_rim_r/l  x 6.6..7.5, y 39.625..40.625     the rims, outside the walls (were x 4.5..7.5)
//   chest_rim_b    removed (it was inside the collar; collar_back now reaches down to the floor)
// Every piece keeps the chest's / rim's own pixels: per-face UV cut out of the parent's faces (as neck_notch_apply.js).
// The floor shows the chest's own top face (the full net, now 1 px lower).
const E = {north: ["x", 1, "y", 1], south: ["x", 0, "y", 1], east: ["z", 1, "y", 1], west: ["z", 0, "y", 1],
           up: ["x", 0, "z", 0], down: ["x", 0, "z", 1]};   // face: [u axis, u1 at max?, v axis, v1 at max?]
const AX = {x: 0, y: 1, z: 2};
const sub = (p, face, from, to) => { const [ua, uMax, va, vMax] = E[face], uv = p.faces[face];
  const t = (ax, atMax, val) => { const i = AX[ax], lo = p.from[i], hi = p.to[i]; const s = (val - lo) / (hi - lo); return atMax ? 1 - s : s; };
  const a = (ax, atMax, from_, to_) => { const i = AX[ax]; return [t(ax, atMax, atMax ? to_[i] : from_[i]), t(ax, atMax, atMax ? from_[i] : to_[i])]; };
  const [u0, u1] = a(ua, uMax, from, to), [v0, v1] = a(va, vMax, from, to);
  return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]; };
return async function well() {
  const B = tools.mcp__blockbench__risky_eval;
  let got = (await B({code: "JSON.stringify(Object.fromEntries(Cube.all.filter(c => /^(chest|chest_rim_[lrb])$/.test(c.name)).map(c => [c.name, {from: c.from, to: c.to, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, f.uv]))}])))"})).content[0].text;
  while (typeof got === "string") got = JSON.parse(got);
  const P = got;
  if (!P.chest || P.chest.to[1] !== 39.625) return "ABORT the chest top isn't at 39.625 (already cut?)";
  const X = 6.6, Y0 = 38.625, Y1 = 39.625, Y2 = 40.625;
  const pieces = [
    ["chest", "chest", [-7.5, 31.625, -5], [7.5, Y0, 6]],
    ["chest_top_r", "chest", [X, Y0, -5], [7.5, Y1, 6]],
    ["chest_top_l", "chest", [-7.5, Y0, -5], [-X, Y1, 6]],
    ["chest_rim_r", "chest_rim_r", [X, Y1, -5], [7.5, Y2, 6]],
    ["chest_rim_l", "chest_rim_l", [-7.5, Y1, -5], [-X, Y2, 6]],
  ];
  const spec = pieces.map(([name, parent, from, to]) => ({name, from, to, faces: Object.fromEntries(Object.keys(E).map(f => [f, sub(P[parent], f, from, to)]))}));
  await tools.mcp__blockbench__save_checkpoint({name: "before collar well"});
  const r = await B({code: `(() => { const S = ${JSON.stringify(spec)};
    const chest = Cube.all.find(c => c.name === 'chest'), parent = chest.parent, tex = Texture.all[0];
    const keep = Object.fromEntries(Cube.all.filter(c => /^(chest|chest_rim_[lr])$/.test(c.name)).map(c => [c.name, c]));
    const rimb = Cube.all.find(c => c.name === 'chest_rim_b');
    Undo.initEdit({elements: Object.values(keep).concat(rimb ? [rimb] : []), outliner: true});
    if (rimb) rimb.remove();
    const made = [];
    for (const s of S) {
      const c = keep[s.name] || new Cube({name: s.name, origin: chest.origin.slice()}).addTo(parent).init();
      c.box_uv = false; c.from = s.from; c.to = s.to;
      for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f]; }
      made.push(c); }
    Undo.finishEdit('Collar well: chest top cut inside the collar', {elements: made, outliner: true});
    Canvas.updateAll(); return 'ok ' + made.map(c => c.name).join(','); })()`});
  return r.content[0].text;
};
