// Applies model/art/head_pumpkin_art.py's plan (.scratch/head/pumpkin_plan.json): checkpoint, paint (aborts if a
// target pixel is in use), rebuild the helmet cubes (per-face UV, inflate 0) and move the pilot's head (bone pivot +
// stand-in cube) by the plan's pilot_head_dy. With plan.overwrite (v3) the paint may cover the helmet's own old faces
// (the art script only frees pixels no other cube uses). Codemode function body.
const plan = JSON.parse((await tools.bash({command: "cat .scratch/head/pumpkin_plan.json # " + Date.now()})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
// re-run: undo the previous build + paint (only if they are the last entries on the undo stack)
const undone = await R("(() => { const names = ['Pumpkin helmet', 'Pumpkin helmet paint', 'Spine piece 2 px lower']; let n = 0; while (Undo.history[Undo.index - 1] && names.includes(Undo.history[Undo.index - 1].action) && n < 4) { Undo.undo(); n++; } Canvas.updateAll(); return n; })()");
if (!undone) await tools.mcp__blockbench__save_checkpoint({name: plan.overwrite ? "before helmet face v3" : "before pumpkin helmet"});
const hex = plan.pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
const p = await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = []; let bad = 0;
  for (let i = 0; i < h.length; i += 10) px.push([0, 2, 4, 6, 8].map(k => parseInt(h.substr(i + k, 2), 16)));
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    if (!${!!plan.overwrite}) for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Pumpkin helmet paint'});
  return bad ? 'ABORT ' + bad : 'painted ' + px.length; })()`);
if (String(p).startsWith("ABORT")) return p;
const b = await R(`(() => { const P = ${JSON.stringify(plan)}, tex = Texture.all[0];
  const cubes = Object.keys(P.cubes).map(n => Cube.all.find(c => c.name === n));
  if (cubes.some(c => !c)) return 'ABORT missing cube';
  const ph = Group.all.find(g => g.name === 'pilot_head'), st = Cube.all.find(c => c.name === 'pilot_head_standin');
  Undo.initEdit({elements: cubes.concat(st ? [st] : []), groups: [ph]});
  cubes.forEach(c => { const s = P.cubes[c.name]; c.box_uv = false; c.inflate = 0; c.from = s.from.slice(); c.to = s.to.slice();
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f].slice(); } });
  const dy = P.pilot_head_dy; ph.origin[1] += dy;
  if (st) { st.from[1] += dy; st.to[1] += dy; st.origin[1] += dy; }
  Undo.finishEdit('Pumpkin helmet', {elements: cubes.concat(st ? [st] : []), groups: [ph]});
  Canvas.updateAll(); return 'rebuilt ' + cubes.length + ', pilot head ' + JSON.stringify(ph.origin); })()`);
// the spine piece between the exhausts comes 2 px down (top 44.625 -> 42.625): the bigger, lower helmet's back hit it
// when the head tips back (run 0.9 px, death 1.5). Its top rows of art are kept (per-face UV cut from its box-UV faces).
const E = {north: ["x", 1, "y", 1], south: ["x", 0, "y", 1], east: ["z", 1, "y", 1], west: ["z", 0, "y", 1], up: ["x", 0, "z", 0], down: ["x", 0, "z", 1]};
const AX = {x: 0, y: 1, z: 2};
const sp = await R("JSON.stringify((() => { const c = Cube.all.find(c => c.name === 'spine_top'); return {from: c.from, to: c.to, box: c.box_uv, faces: Object.fromEntries(Object.entries(c.faces).map(([k, f]) => [k, f.uv]))}; })())");
const S = typeof sp === "string" ? JSON.parse(sp) : sp;
let c3 = "spine already lowered";
if (S.box !== false && S.to[1] > 43) {
  const from = [S.from[0], S.to[1] - 4, S.from[2]], to = S.to.slice(), dy = -2;
  const sub = face => { const [ua, uMax, va, vMax] = E[face], uv = S.faces[face];
    const t = (ax, atMax, val) => { const i = AX[ax], s = (val - S.from[i]) / (S.to[i] - S.from[i]); return atMax ? 1 - s : s; };
    const a = (ax, atMax) => { const i = AX[ax]; return [t(ax, atMax, atMax ? to[i] : from[i]), t(ax, atMax, atMax ? from[i] : to[i])]; };
    const [u0, u1] = a(ua, uMax), [v0, v1] = a(va, vMax);
    return [uv[0] + u0 * (uv[2] - uv[0]), uv[1] + v0 * (uv[3] - uv[1]), uv[0] + u1 * (uv[2] - uv[0]), uv[1] + v1 * (uv[3] - uv[1])]; };
  const spec = {from: [from[0], from[1] + dy, from[2]], to: [to[0], to[1] + dy, to[2]], faces: Object.fromEntries(Object.keys(E).map(f => [f, sub(f)]))};
  c3 = await R(`(() => { const s = ${JSON.stringify(spec)}, c = Cube.all.find(c => c.name === 'spine_top'), tex = Texture.all[0];
    Undo.initEdit({elements: [c]}); c.box_uv = false; c.from = s.from; c.to = s.to;
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f]; }
    Undo.finishEdit('Spine piece 2 px lower', {elements: [c]}); Canvas.updateAll(); return 'spine ' + JSON.stringify(s.to); })()`);
}
return [p, b, c3];
