// Back of the collar (2026-10-19, user: "a piece that completes the collar and covers the nape"), after
// nape_strips_apply.js. Codemode function body; returns async function back(opts).
//
// nape_back joins nape_r / nape_l behind the head: x -6..6, y 40.625..43.625 (standing on the chest's 1 px rims, up to the
// strips' top), z 4.9..6. Its back face is flush with the chest's back (z 6), so the chest wall runs straight up into it
// (reaching down beside the rims, it shared their back plane at z 6 and flickered) and spine_top (z 6..9) sits
// behind its middle; its front starts 0.1 inside the helmet's back face (z 5) so the two never share a plane (GeckoLib
// draws both faces) and overlaps the strips' back ends (z 4.95) so there's no seam. The helmet swings past z 5 by up to
// 1.2-1.4 px in idle / run (measured on every animation): the 1.1 px plate hides most of that, spine_top the middle.
//
// Texture: no new pixels. The faces are cut out of the old nape's box-UV net (NAPE below) as
// if the plate sat in the nape's back 1.1 px (z 3.9..5): back = the nape's back face, inner face = its front face, top =
// the back rows of its top, ends = the back of its sides.
const E = {north: ["x", 1, "y", 1], south: ["x", 0, "y", 1], east: ["z", 1, "y", 1], west: ["z", 0, "y", 1],
           up: ["x", 0, "z", 0], down: ["x", 0, "z", 1]};   // face: [u axis, u1 at max?, v axis, v1 at max?]
const AX = {x: 0, y: 1, z: 2};
// The old nape (12x4x7 at box-UV offset [80, 66], mirrored) as it was before nape_strips_apply.js.
const NAPE = {from: [-6, 39.625, -2], to: [6, 43.625, 5], faces: {north: [99, 73, 87, 77], east: [106, 73, 99, 77],
  south: [118, 73, 106, 77], west: [87, 73, 80, 77], up: [87, 73, 99, 66], down: [99, 66, 111, 73]}};
return async function back(opts = {}) {
  const B = tools.mcp__blockbench__risky_eval, n = NAPE;
  const sub = (face, from, to) => { const [ua, uMax, va, vMax] = E[face], uv = n.faces[face];
    const t = (ax, atMax, val) => { const i = AX[ax], lo = n.from[i], hi = n.to[i]; const s = (val - lo) / (hi - lo); return atMax ? 1 - s : s; };
    const a = (ax, atMax, from_, to_) => { const i = AX[ax]; return [t(ax, atMax, atMax ? to_[i] : from_[i]), t(ax, atMax, atMax ? from_[i] : to_[i])]; };
    const [u0, u1] = a(ua, uMax, from, to), [v0, v1] = a(va, vMax, from, to);
    return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]; };
  const Z0 = opts.z0 ?? 4.9, Z1 = opts.z1 ?? 6, Y0 = opts.bottom ?? 40.625, Y1 = opts.top ?? 43.625;
  const from = [-6, Y0, Z0], to = [6, Y1, Z1];
  const uvFrom = [-6, Y0, 5 - (Z1 - Z0)], uvTo = [6, Y1, 5];          // where the texture comes from in the old nape
  const faces = Object.fromEntries(Object.keys(E).map(f => [f, sub(f, uvFrom, uvTo)]));
  await tools.mcp__blockbench__save_checkpoint({name: "before nape back"});
  const r = await B({code: `(() => { const strip = Cube.all.find(c => c.name === 'nape_r');
    if (!strip) return 'ABORT run nape_strips_apply.js first';
    if (Cube.all.some(c => c.name === 'nape_back')) return 'ABORT nape_back exists';
    const tex = Texture.all[0];
    Undo.initEdit({elements: [], outliner: true});
    const c = new Cube({name: 'nape_back', origin: strip.origin.slice()}).addTo(strip.parent).init();
    c.box_uv = false; c.from = ${JSON.stringify(from)}; c.to = ${JSON.stringify(to)};
    const F = ${JSON.stringify(faces)};
    for (const f in F) { c.faces[f].texture = tex.uuid; c.faces[f].uv = F[f]; }
    Undo.finishEdit('Nape back: collar completed behind the head', {elements: [c], outliner: true});
    Canvas.updateAll(); return 'ok'; })()`});
  return r.content[0].text;
};
