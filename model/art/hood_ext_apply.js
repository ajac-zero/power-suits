// Wider hip hoods (2026-10-18, user: "a little bit wider, so it matches the chest width"). Codemode function body (or
// the skill's scripts/bbrun.mjs model/art/hood_ext_apply.js). Re-run after drum_hips_apply.js (which rebuilds the hoods).
//
// The hoods (drum_hips_art.py) span x 0.25..6 on bones yawed like the legs (-7 / +7), so their outer end slanted
// (6.7 from the centre at the front, 5.3 at the back) while the chest is a straight +-7.5. Each hood piece gets an
// extension `<piece>_ext_r/l` with the same side profile, x 5.25..7.35 (0.15 inside the chest's side: faces on one plane
// flicker), turned back by the cube's own Y rotation (+7 / -7) so its outer edge runs straight front to back. The
// profile then sits <= 0.24 px off the leg's yawed axis at its outer end, inside the hood's clearance (the outer thigh,
// thigh_top + shoulder, sweeps the same circle as the drum: max radius 4.71 vs the hood's 5 / 4.65).
// Inflate = the right piece's + 0.05 (not on the old pieces' flat faces: 0.02 was too close).
// Nets: each extension's faces copied from its piece's net (end faces whole, the w-wide faces' last 2 columns), packed
// into free texture space (offsets below, found by a packer that also skips every cube's net).
// Checks: the skill's densesweep.js (legs and arms vs the extensions: 0 in every animation but stim, whose arm also
// goes 2.5 px into the chest) and zfight.mjs (TOL 0.035: nothing on *_ext_*).
const NETS = {top: [54, 52], front: [238, 14], back: [248, 19], ffill: [28, 16], bfill: [62, 8], fslab: [20, 12], mslab: [232, 51]};
const X0 = 5.25, X1 = 7.35, B = tools.mcp__blockbench__risky_eval;
await tools.mcp__blockbench__save_checkpoint({name: "before wider hoods"});
const src = JSON.parse(JSON.parse((await B({code: `(() => { Cube.all.filter(c => /^hood_.*_ext_[rl]$/.test(c.name)).forEach(c => c.remove());
  return JSON.stringify(Cube.all.filter(c => /^hood_[a-z]+_r$/.test(c.name)).map(c => ({n: c.name.slice(5, -2), f: c.from, t: c.to, r: c.rotation, i: c.inflate || 0,
    uv: c.uv_offset, s: c.size().map(v => Math.floor(v + 1e-6))}))); })()`})).content[0].text));
// paint the nets (box UV: up (u+d, v), down (u+d+w, v), east (u, v+d), north (u+d, v+d), west (u+d+w, v+d), south (u+2d+w, v+d))
const jobs = src.map(p => ({to: NETS[p.n], from: p.uv, w0: p.s[0], h: p.s[1], d: p.s[2], w: Math.floor(X1 - X0)}));
const paint = await B({code: `(() => { const jobs = ${JSON.stringify(jobs)}; let res = 'ok';
  Texture.all[0].edit(canvas => { const ctx = canvas.getContext('2d');
    const F = (u, v, w, h, d) => ({up: [u + d, v, w, d], down: [u + d + w, v, w, d], east: [u, v + d, d, h], north: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h]});
    for (const j of jobs) { const a = F(...j.from, j.w0, j.h, j.d), b = F(...j.to, j.w, j.h, j.d);
      for (const k in b) { const [sx, sy, sw, sh] = a[k], [dx, dy, dw] = b[k]; if (!dw || !sh) continue;
        ctx.putImageData(ctx.getImageData(sx + sw - dw, sy, dw, sh), dx, dy); } }
    return canvas; }, {edit_name: 'Paint hood extension nets'});
  return res; })()`});
for (const [side, s] of [["r", 1], ["l", -1]]) {
  await tools.mcp__blockbench__place_cube({group: side === "r" ? "right_hood" : "left_hood", texture: "marine.png", elements: src.map(p => ({
    name: `hood_${p.n}_ext_${side}`, from: [s > 0 ? X0 : -X1, p.f[1], p.f[2]], to: [s > 0 ? X1 : -X0, p.t[1], p.t[2]],
    origin: [s * 5.5, 22.625, 0], rotation: [p.r[0], s * 7, 0], uv_offset: NETS[p.n], mirror_uv: side === "r", inflate: +(p.i + 0.05).toFixed(3)}))});
}
return paint.content[0].text;
