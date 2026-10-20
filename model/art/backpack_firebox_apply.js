// Backpack v3 (2026-10-20): applies the plan written by backpack_firebox_art.py (see its docstring). Codemode function body:
//   1. dump the live elements + texture (elements.json / live.png) and run the art script against the saved texture;
//   2. store("bp_plan_path", "<plan.json>") and run this file (comments stripped for risky_eval).
// Paints the pixel ops (freed old nets cleared, prototype swatches cleared, new faces painted), removes the old
// backpack / spine_top / firebox cubes, and places `backpack` + `fuel_hatch` (per-face UV) in backpack_parts. Every step
// is an undoable Blockbench edit. (The 2026-10-20 run also removed the hidden prototype groups; Group.remove threw
// "Cannot read properties of undefined (reading 'fix_rotation')" with Blockbench in Animate mode and left the group
// twice at the outliner root: remove groups in their own edit, and check Outliner.root afterwards.)
const plan = JSON.parse(await tools.read({path: load("bp_plan_path")}));
const hex = plan.ops.map(([x, y, c]) => x.toString(16).padStart(2, "0") + y.toString(16).padStart(2, "0") + c).join("");
await tools.mcp__blockbench__save_checkpoint({name: "before backpack v3"});
const code = `(() => {
  const H = '${hex}', CUBES = ${JSON.stringify(plan.cubes)}, OLD = ${JSON.stringify(plan.remove)};
  const tex = Texture.all[0];
  tex.edit(canvas => { const ctx = canvas.getContext('2d');
    for (let i = 0; i < H.length; i += 12) {
      const x = parseInt(H.substr(i, 2), 16), y = parseInt(H.substr(i + 2, 2), 16), a = parseInt(H.substr(i + 10, 2), 16);
      ctx.clearRect(x, y, 1, 1);
      if (a) { ctx.fillStyle = '#' + H.substr(i + 4, 6); ctx.fillRect(x, y, 1, 1); } }
    return canvas; }, {edit_name: 'Backpack v3 texture'});
  const gone = Cube.all.filter(c => OLD.includes(c.name));
  Undo.initEdit({outliner: true, elements: gone});
  gone.forEach(c => c.remove());
  Undo.finishEdit('Remove old backpack, spine_top, firebox');
  const parent = Group.all.find(g => g.name === 'backpack_parts'); const made = [];
  Undo.initEdit({outliner: true, elements: []});
  for (const s of CUBES) {
    const c = new Cube({name: s.name, from: s.from, to: s.to, origin: parent.origin.slice()}).addTo(parent).init();
    c.box_uv = false;
    for (const [f, uv] of Object.entries(s.uv)) { c.faces[f].uv = uv.slice(); c.faces[f].texture = tex.uuid; }
    made.push(c); }
  Undo.finishEdit('Backpack v3: backpack + fuel_hatch', {outliner: true, elements: made});
  Canvas.updateAll();
  return 'removed ' + gone.length + ', placed ' + made.map(c => c.name).join(' ');
})()`;
const r = await tools.mcp__blockbench__risky_eval({code});
return r.content[0].text;
