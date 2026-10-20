// Applies model/art/collar_ring_art.py's plan (.scratch/collar-ring/plan.json) to the live project. Codemode function
// body (run with the project folder as cwd). Checkpoint, paint the new faces (aborts if a target pixel is in use), remove
// the old collar blocks (collar_r/l, gorget_r/l, nape_r/l, nape_back) and build the ring with per-face UV. Paint and build
// are separate undo entries.
// If the ring is already built, only the cubes whose box changed are repainted and rebuilt, and ring cubes the plan no
// longer has (chest_rim_f) are removed: re-running after a parameter change doesn't need an undo (the user's own edits
// may sit on top of the undo stack).
const plan = JSON.parse((await tools.bash({command: "cat .scratch/collar-ring/plan.json"})).output);
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; try { return JSON.parse(t); } catch (e) { return t; } };
const RING = ["collar_post_r", "collar_r", "collar_post_l", "collar_l", "collar_back", "neck_guard", "neck_floor", "chest_rim_f"];
const live = await R(`JSON.stringify(Object.fromEntries(Cube.all.filter(c => ${JSON.stringify(RING)}.includes(c.name)).map(c => [c.name, [c.from, c.to]])))`);
const existing = typeof live === "string" ? JSON.parse(live) : live;
const update = "neck_guard" in existing;
const same = (n) => existing[n] && plan.cubes[n] && JSON.stringify(existing[n]) === JSON.stringify([plan.cubes[n].from, plan.cubes[n].to]);
const build = Object.keys(plan.cubes).filter(n => !update || !same(n));
const remove = update ? Object.keys(existing).filter(n => !same(n)) : plan.remove;
// pixels of the faces being built (every painted rect is the face uv of a cube cut from it, or contains it)
const rects = build.flatMap(n => Object.values(plan.cubes[n].faces));
const inBuilt = ([x, y]) => rects.some(([u0, v0, u1, v1]) => x >= Math.floor(Math.min(u0, u1)) && x < Math.ceil(Math.max(u0, u1)) && y >= Math.floor(Math.min(v0, v1)) && y < Math.ceil(Math.max(v0, v1)));
const pixels = update ? plan.pixels.filter(inBuilt) : plan.pixels;
const out = [{update, build, remove, pixels: pixels.length}];
await tools.mcp__blockbench__save_checkpoint({name: update ? "before collar ring update" : "before collar ring"});
const hex = pixels.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
out.push(await R(`(() => { const h = '${hex}', tex = Texture.all[0], px = [];
  for (let i = 0; i < h.length; i += 10) px.push([parseInt(h.substr(i, 2), 16), parseInt(h.substr(i + 2, 2), 16), parseInt(h.substr(i + 4, 2), 16), parseInt(h.substr(i + 6, 2), 16), parseInt(h.substr(i + 8, 2), 16)]);
  let bad = 0;
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (const [x, y] of px) if (d.data[(y * canvas.width + x) * 4 + 3] !== 0) bad++;
    if (bad) return canvas;
    for (const [x, y, r, g, b] of px) { const i = (y * canvas.width + x) * 4; d.data[i] = r; d.data[i + 1] = g; d.data[i + 2] = b; d.data[i + 3] = 255; }
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Collar ring paint'});
  return bad ? 'ABORT ' + bad + ' pixels in use' : 'painted ' + px.length; })()`));
if (String(out[1]).startsWith("ABORT")) return out;
const cubes = Object.fromEntries(build.map(n => [n, plan.cubes[n]]));
out.push(await R(`(() => { const P = ${JSON.stringify({cubes, origin: plan.origin, remove})};
  const tex = Texture.all[0];
  const old = Cube.all.filter(c => P.remove.includes(c.name));
  Undo.initEdit({elements: old, outliner: true});
  old.forEach(c => c.remove());
  const made = [];
  for (const [name, s] of Object.entries(P.cubes)) {
    const parent = Group.all.find(g => g.name === s.parent);
    const c = new Cube({name, origin: P.origin.slice()}).addTo(parent).init();
    c.box_uv = false; c.from = s.from.slice(); c.to = s.to.slice(); c.inflate = 0;
    for (const f in s.faces) { c.faces[f].texture = tex.uuid; c.faces[f].uv = s.faces[f].slice(); }
    made.push(c); }
  Undo.finishEdit('Collar ring', {elements: made, outliner: true});
  Canvas.updateAll(); return 'removed ' + old.map(c => c.name).join(',') + '; built ' + made.map(c => c.name).join(','); })()`));
return out;
