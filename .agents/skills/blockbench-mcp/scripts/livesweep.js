// Clipping sweep on the LIVE Blockbench project: for each pair [A, B] of cube-name regexes, the
// deepest point of any A cube inside any B cube, at rest and over every animation (samples per
// animation, default 24). Uses Blockbench's own meshes (cube rotation, inflate and the bone chain
// exactly as Blockbench draws them), so no rig maths to keep in sync.
// Codemode function body. Usage:
//   const SKILL = "/Users/ajac-zero/Developer/PowerSuits/.agents/skills/blockbench-mcp/scripts";
//   const sweep = new Function("tools", (await tools.bash({command: `cat ${SKILL}/livesweep.js`})).output)(tools);
//   const r = await sweep({pairs: [["^shoulder_joint_r$", "^pad_.*_r$"], ["^upper_arm_r$", "^chest"]]});
//   // r = {"<A> > <B>": {rest: {d, a, b}, worst: {anim, t, d, a, b}, anims: {name: {t, d, a, b}}}}
// Depth = how far the point is inside the box (distance to its nearest face). Compare with `rest`:
// some overlap is intended (a joint sunk into the chest); what matters is how much worse animation gets.
// Leaves Blockbench in edit mode at time 0. (Canvas.updateAll() puts the bones back in the rest pose:
// call it for the rest sample only, never between Animator.preview() and the measurement. No comments
// inside the eval string: risky_eval rejects them.)
return async function sweep(opts) {
  const code = `(() => {
  const names = ${JSON.stringify(opts.pairs.map(p => p.join(" > ")))};
  const pairs = ${JSON.stringify(opts.pairs)}.map(([a, b]) => [new RegExp(a), new RegExp(b)]);
  const N = ${opts.samples || 24}, F = [0, 0.5, 1];
  const sel = re => Cube.all.filter(c => re.test(c.name) && c.visibility !== false);
  const sets = pairs.map(([a, b]) => [sel(a), sel(b)]);
  function pts(c) {
    const bb = c.mesh.geometry.boundingBox || (c.mesh.geometry.computeBoundingBox(), c.mesh.geometry.boundingBox), out = [];
    for (const fx of F) for (const fy of F) for (const fz of F) { if (fx === .5 && fy === .5 && fz === .5) continue;
      out.push(new THREE.Vector3(bb.min.x + (bb.max.x - bb.min.x) * fx, bb.min.y + (bb.max.y - bb.min.y) * fy,
        bb.min.z + (bb.max.z - bb.min.z) * fz).applyMatrix4(c.mesh.matrixWorld)); }
    return out;
  }
  function measure() {
    Outliner.root.forEach(n => n.mesh && n.mesh.updateMatrixWorld(true));
    return sets.map(([A, B]) => { let best = {d: 0, a: "", b: ""};
      const inv = B.map(b => [b, new THREE.Matrix4().copy(b.mesh.matrixWorld).invert(), b.mesh.geometry.boundingBox]);
      for (const a of A) for (const p of pts(a)) for (const [b, M, bb] of inv) { if (a === b) continue;
        const q = p.clone().applyMatrix4(M);
        const d = Math.min(q.x - bb.min.x, bb.max.x - q.x, q.y - bb.min.y, bb.max.y - q.y, q.z - bb.min.z, bb.max.z - q.z);
        if (d > best.d) best = {d: +d.toFixed(2), a: a.name, b: b.name}; }
      return best; });
  }
  const res = pairs.map(() => ({anims: {}}));
  Canvas.updateAll();
  measure().forEach((m, i) => res[i].rest = m);
  Modes.options.animate.select();
  for (const an of Animation.all) { an.select();
    for (let s = 0; s <= N; s++) { const t = an.length * s / N; Timeline.setTime(t); Animator.preview();
      const key = an.name.split(".").pop();
      measure().forEach((m, i) => { const cur = res[i].anims[key];
        if (!cur || m.d > cur.d) res[i].anims[key] = {t: +t.toFixed(2), ...m}; }); } }
  Timeline.setTime(0); Modes.options.edit.select(); Canvas.updateAll();
  const out = {};
  res.forEach((r, i) => { let w = {d: 0}; for (const [k, v] of Object.entries(r.anims)) if (v.d > w.d) w = {anim: k, ...v};
    out[names[i]] = {rest: r.rest, worst: w, anims: r.anims}; });
  return JSON.stringify(out); })()`;
  const r = await tools.mcp__blockbench__risky_eval({code});
  const text = r.content[0].text;
  if (!/^\s*["{]/.test(text)) throw new Error(text.slice(0, 500));     // risky_eval reports errors as text
  let v = JSON.parse(text);
  if (typeof v === "string") { if (!v.startsWith("{")) throw new Error(v.slice(0, 500)); v = JSON.parse(v); }
  return v;
};
