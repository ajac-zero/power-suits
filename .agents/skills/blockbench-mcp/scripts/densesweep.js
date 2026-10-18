// Dense clip sweep on the live project (codemode function body): like the skill's livesweep.js, but samples a grid on
// every face of the A cubes (step opts.step, default 0.5 px), so edges cutting through faces count too.
// const sweep = new Function("tools", (await tools.bash({command: "cat .agents/skills/blockbench-mcp/scripts/densesweep.js"})).output)(tools);
// await sweep({pairs: [["^thigh_.*_r$", "^hip_"]], anims: ["walk", "run"], samples: 40})
return async function sweep(opts) {
  const code = `(() => {
  const pairs = ${JSON.stringify(opts.pairs)}.map(([a, b]) => [new RegExp(a), new RegExp(b)]);
  const N = ${opts.samples || 40}, STEP = ${opts.step || 0.5}, ANIMS = ${JSON.stringify(opts.anims || ["walk", "run", "idle"])};
  const sel = re => Cube.all.filter(c => re.test(c.name));
  const sets = pairs.map(([a, b]) => [sel(a), sel(b)]);
  const grids = new Map();
  function grid(c) { if (grids.has(c)) return grids.get(c);
    const g = c.mesh.geometry; if (!g.boundingBox) g.computeBoundingBox(); const bb = g.boundingBox, out = [];
    const ax = [0, 1, 2], lo = [bb.min.x, bb.min.y, bb.min.z], hi = [bb.max.x, bb.max.y, bb.max.z];
    const n = ax.map(i => Math.max(1, Math.ceil((hi[i] - lo[i]) / STEP)));
    for (const i of ax) for (const side of [lo[i], hi[i]]) { const [j, k] = ax.filter(x => x !== i);
      for (let a = 0; a <= n[j]; a++) for (let b = 0; b <= n[k]; b++) { const p = [0, 0, 0]; p[i] = side;
        p[j] = lo[j] + (hi[j] - lo[j]) * a / n[j]; p[k] = lo[k] + (hi[k] - lo[k]) * b / n[k]; out.push(new THREE.Vector3(...p)); } }
    grids.set(c, out); return out; }
  function measure() {
    Outliner.root.forEach(n => n.mesh && n.mesh.updateMatrixWorld(true));
    return sets.map(([A, B]) => { let best = {d: 0, a: "", b: ""};
      const inv = B.map(b => [b, new THREE.Matrix4().copy(b.mesh.matrixWorld).invert(), b.mesh.geometry.boundingBox]);
      for (const a of A) { const M = a.mesh.matrixWorld; for (const p0 of grid(a)) { const p = p0.clone().applyMatrix4(M);
        for (const [b, Mi, bb] of inv) { if (a === b) continue; const q = p.clone().applyMatrix4(Mi);
          const d = Math.min(q.x - bb.min.x, bb.max.x - q.x, q.y - bb.min.y, bb.max.y - q.y, q.z - bb.min.z, bb.max.z - q.z);
          if (d > best.d) best = {d: +d.toFixed(2), a: a.name, b: b.name}; } } }
      return best; });
  }
  const res = pairs.map(() => ({}));
  Canvas.updateAll(); measure().forEach((m, i) => res[i].rest = m);
  Modes.options.animate.select();
  for (const name of ANIMS) { const an = Animation.all.find(a => a.name === "animation.marine." + name); an.select();
    for (let s = 0; s <= N; s++) { const t = an.length * s / N; Timeline.setTime(t); Animator.preview();
      const leg = Group.all.find(g => g.name === "right_leg"), lr = Group.all.find(g => g.name === "left_leg");
      measure().forEach((m, i) => { const cur = res[i][name];
        if (!cur || m.d > cur.d) res[i][name] = {t: +t.toFixed(2), ...m, thighR: +leg.mesh.rotation.x.toFixed(2), thighL: +lr.mesh.rotation.x.toFixed(2)}; }); } }
  Timeline.setTime(0); Modes.options.edit.select(); Canvas.updateAll();
  return JSON.stringify(res); })()`;
  const r = await tools.mcp__blockbench__risky_eval({code});
  let v = JSON.parse(r.content[0].text); if (typeof v === "string") v = JSON.parse(v); return v;
};
