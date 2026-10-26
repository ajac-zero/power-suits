// Tool socket bases 7x1x7 -> 5x1x5 (user, 2026-10-26: in the cockpit the 7x7 plate covered the tool's near end).
// Every power tool's *_base shrinks about its centre (x -14..-9, z -4..1, y unchanged) and keeps its look: each 5x5 face
// is the old 7x7 face with two texel rows / columns dropped (top: the outer dark ring, so the bevel ring + cyan contacts
// stay; underside and sides: the second row in from each edge, so the rim / bolt ends stay). The new faces go in the
// top-left corner of each old face's texels (per-face UV, as Blockbench shows them: mirrored faces are resampled the way
// they're drawn), the rest of the old texels are cleared (free texture space). Aborts if any old base texel is shared
// with another cube. Codemode function body; one checkpoint, one texture edit, one cube edit.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const NAMES = ['chaingun_base', 'em_base', 'drill_base', 'saw_base', 'flame_base', 'tnt_base', 'rail_base'];
const state = await R(`(() => { const N = ${JSON.stringify(NAMES)};
  const cs = N.map(n => Cube.all.find(c => c.name === n));
  if (cs.some(c => !c)) return 'missing ' + N.filter((n, i) => !cs[i]);
  const bad = cs.filter(c => Math.abs(c.to[0] - c.from[0] - 7) > 1e-6 || Math.abs(c.to[2] - c.from[2] - 7) > 1e-6);
  return bad.length ? 'not 7x7: ' + bad.map(c => c.name) : 'ok'; })()`);
if (state !== 'ok') return 'ABORT ' + state;
await tools.mcp__blockbench__save_checkpoint({name: "before 5x5 tool bases"});
return await R(`(() => {
  const N = ${JSON.stringify(NAMES)}, tex = Texture.all[0], W = tex.width, H = tex.height;
  const cubes = N.map(n => Cube.all.find(c => c.name === n));
  const KEEP = {top: [1, 2, 3, 4, 5], other: [0, 2, 3, 4, 6]};
  const rect = uv => [Math.min(uv[0], uv[2]), Math.min(uv[1], uv[3]), Math.max(uv[0], uv[2]), Math.max(uv[1], uv[3])];
  const mine = new Set(), plan = [];
  for (const c of cubes) for (const f of ['north', 'east', 'south', 'west', 'up', 'down']) {
    const r = rect(c.faces[f].uv); for (let y = r[1]; y < r[3]; y++) for (let x = r[0]; x < r[2]; x++) mine.add(x + ',' + y); }
  let shared = [];
  for (const o of Cube.all) { if (cubes.includes(o)) continue;
    for (const f of Object.keys(o.faces)) { const fc = o.faces[f]; if (!fc.texture && fc.texture !== 0) continue; const r = rect(fc.uv);
      for (let y = Math.floor(r[1]); y < Math.ceil(r[3]); y++) for (let x = Math.floor(r[0]); x < Math.ceil(r[2]); x++)
        if (mine.has(x + ',' + y)) shared.push(o.name + '.' + f); } }
  shared = [...new Set(shared)];
  if (shared.length) return 'ABORT shared texels: ' + shared.slice(0, 12);
  let out = null;
  tex.edit(canvas => {
    const ctx = canvas.getContext('2d'), d = ctx.getImageData(0, 0, W, H), src = new Uint8ClampedArray(d.data);
    const px = (x, y) => { const i = (y * W + x) * 4; return [src[i], src[i + 1], src[i + 2], src[i + 3]]; };
    const newUV = {};
    for (const c of cubes) { newUV[c.name] = {};
      const faces = {};
      for (const f of ['north', 'east', 'south', 'west', 'up', 'down']) {
        const uv = c.faces[f].uv, flat = f === 'up' || f === 'down', w = 5, h = flat ? 5 : 1;
        const keepX = f === 'up' ? KEEP.top : KEEP.other, keepY = flat ? keepX : [0];
        const ow = flat ? 7 : 7, oh = flat ? 7 : 1, img = [];
        for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
          const sx = Math.floor(uv[0] + (keepX[x] + 0.5) * (uv[2] - uv[0]) / ow), sy = Math.floor(uv[1] + (keepY[y] + 0.5) * (uv[3] - uv[1]) / oh);
          img.push(px(sx, sy)); }
        faces[f] = {r: rect(uv), w, h, img}; }
      for (const f in faces) { const r = faces[f].r;
        for (let y = r[1]; y < r[3]; y++) for (let x = r[0]; x < r[2]; x++) { const i = (y * W + x) * 4; d.data[i] = d.data[i + 1] = d.data[i + 2] = d.data[i + 3] = 0; } }
      for (const f in faces) { const {r, w, h, img} = faces[f];
        for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = ((r[1] + y) * W + r[0] + x) * 4, p = img[y * w + x];
          d.data[i] = p[0]; d.data[i + 1] = p[1]; d.data[i + 2] = p[2]; d.data[i + 3] = p[3]; }
        newUV[c.name][f] = [r[0], r[1], r[0] + w, r[1] + h]; } }
    ctx.putImageData(d, 0, 0); out = newUV; return canvas; }, {edit_name: '5x5 tool bases paint'});
  Undo.initEdit({elements: cubes});
  for (const c of cubes) {
    const cx = (c.from[0] + c.to[0]) / 2, cz = (c.from[2] + c.to[2]) / 2;
    c.box_uv = false; c.from = [cx - 2.5, c.from[1], cz - 2.5]; c.to = [cx + 2.5, c.to[1], cz + 2.5];
    for (const f in out[c.name]) { c.faces[f].uv = out[c.name][f]; c.faces[f].texture = tex.uuid; } }
  Undo.finishEdit('5x5 tool bases', {elements: cubes});
  Canvas.updateAll();
  return cubes.map(c => c.name + ' ' + JSON.stringify([c.from, c.to]) + ' up ' + JSON.stringify(c.faces.up.uv));
})()`);
