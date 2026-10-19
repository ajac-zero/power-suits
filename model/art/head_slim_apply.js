// Slimmer helmet (2026-10-18, user: head closer to a player's head so it fits the space-marine chest). The face
// pieces stood up to 3 px in front of the shell (brow z -7, visor / cheeks / chin z -6) and the cheeks and ears 1 px out
// at the sides (x +-5.5): the helmet was 11 x 9 x 12 px around a 9 x 8 x 9 shell. Now the face is a 0.75 px relief on
// the shell's front (frame at z -4.75, visor glass 0.25 behind it), the cheeks keep only their inner column (x 3.5..4.5)
// and the ear pieces go: 9 x 9 x 9.75 (the shell must still hold the pilot's 8 px head, x +-4, z -3.5..4.5).
// Each piece is the FRONT slab of the old cube moved back, on per-face UV cut from the old box-UV faces, so the art
// is kept (the neck notch's method, model/art/neck_notch_apply.js). Codemode function body with `opts` ({only: [names]}
// redoes some pieces; 2026-10-18 the collars were added in a second run with only: ["collar_r", "collar_l"]).
const E = {north: ["x", 1, "y", 1], south: ["x", 0, "y", 1], east: ["z", 1, "y", 1], west: ["z", 0, "y", 1],
           up: ["x", 0, "z", 0], down: ["x", 0, "z", 1]};   // face: [u axis, u0 at max?, v axis, v0 at max?]
const AX = {x: 0, y: 1, z: 2};
// [cube, region of the old cube to keep (from, to), shift]
const PIECES = [
  ["helmet_brow", [-4.5, 46, -7], [4.5, 48, -6.25], [0, 0, 2.25]],
  ["visor", [-3.5, 41, -6], [3.5, 46, -5.5], [0, 0, 1.5]],
  ["cheek_r", [3.5, 40, -6], [4.5, 46, -5.25], [0, 0, 1.25]],
  ["cheek_l", [-4.5, 40, -6], [-3.5, 46, -5.25], [0, 0, 1.25]],
  ["chin_guard", [-3.5, 39, -6], [3.5, 41, -5.25], [0, 0, 1.25]],
  // the collars beside the head keep their outer column, 1.1 px clear of the shell like the chest's gorget sides
  // (they hugged the old cheeks / ears; the shell swung up to 1.1 px into them when the head turns)
  ["collar_r", [5.5, 38.625, -3], [6.5, 43.625, 2], [0.1, 0, 0]],
  ["collar_l", [-6.5, 38.625, -3], [-5.5, 43.625, 2], [-0.1, 0, 0]],
].filter(p => !opts.only || opts.only.includes(p[0]));
const REMOVE = opts.only ? [] : ["ear_l", "ear_r"];
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
const live = await R(`JSON.stringify(Object.fromEntries(Cube.all.filter(c => ${JSON.stringify(PIECES.map(p => p[0]))}.includes(c.name)).map(c => [c.name, {from: c.from, to: c.to, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, f.uv]))}])))`);
const L = typeof live === "string" ? JSON.parse(live) : live;
const spec = PIECES.map(([name, from, to, d]) => {
  const ch = L[name];
  if (!ch) throw new Error("missing " + name);
  const sub = face => { const [ua, uMax, va, vMax] = E[face], uv = ch.faces[face];
    const t = (ax, atMax, val) => { const i = AX[ax], s = (val - ch.from[i]) / (ch.to[i] - ch.from[i]); return atMax ? 1 - s : s; };
    const a = (ax, atMax) => { const i = AX[ax]; return [t(ax, atMax, atMax ? to[i] : from[i]), t(ax, atMax, atMax ? from[i] : to[i])]; };
    const [u0, u1] = a(ua, uMax), [v0, v1] = a(va, vMax);
    return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]; };
  return {name, from: from.map((v, i) => v + d[i]), to: to.map((v, i) => v + d[i]), faces: Object.fromEntries(Object.keys(E).map(f => [f, sub(f)]))};
});
await tools.mcp__blockbench__save_checkpoint({name: opts.only ? "before slim helmet: " + opts.only.join(" ") : "before slim helmet"});
const a = await R(`(() => { const S = ${JSON.stringify(spec)}, tex = Texture.all[0];
  const cubes = S.map(s => Cube.all.find(c => c.name === s.name));
  Undo.initEdit({elements: cubes});
  S.forEach((s, i) => { const c = cubes[i]; c.box_uv = false; c.from = s.from; c.to = s.to; if (!/^collar_/.test(c.name)) c.inflate = 0;
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f]; } });
  Undo.finishEdit('Slim helmet: face pieces flattened onto the shell', {elements: cubes});
  Canvas.updateAll(); return 'moved ' + cubes.length; })()`);
const b = !REMOVE.length ? 'nothing removed' : await R(`(() => { const cubes = Cube.all.filter(c => ${JSON.stringify(REMOVE)}.includes(c.name));
  Undo.initEdit({elements: cubes, outliner: true}); cubes.forEach(c => c.remove());
  Undo.finishEdit('Slim helmet: ear pieces removed', {elements: [], outliner: true});
  Canvas.updateAll(); return 'removed ' + cubes.length; })()`);
return [a, b];
