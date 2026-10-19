// Flat empty socket (user, 2026-10-18): nothing hangs under the tool arm's gauntlet when no tool is fitted. socket_base
// (the old wrist ring) is removed and its net cleared; the coupling (grey plate, cyan pins) is painted on the gauntlet's
// underside instead (user's pick, 2026-10-18: a 6x6 steel plate, lit top/left edges, four bolts, a 2x2 cyan centre). forearm_l shared forearm_r's net (unmirrored), so it first gets its own copy in free texture
// space (transparent and outside every net / per-face UV), and only that copy's down face is painted: the right
// gauntlet keeps its art. Only the tools bring a 7x1x7 base now. Codemode function body; after tool_bases_apply.js.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => Cube.all.some(c => c.name === 'socket_base') && JSON.stringify(Cube.all.find(c => c.name === 'forearm_l').uv_offset) === '[216,21]')()`);
if (ok !== true) return "ABORT state " + ok;
await tools.mcp__blockbench__save_checkpoint({name: "before flat empty socket"});
const p = await R(`(() => { const tex = Texture.all[0], W = tex.width, H = tex.height, by = n => Cube.all.find(c => c.name === n);
  const base = by('socket_base'), fl = by('forearm_l');
  const rects = skip => { const out = []; Cube.all.forEach(c => { if (c === skip) return;
    if (c.box_uv) { const s = c.size().map(v => Math.floor(v + 1e-6)), [u, v] = c.uv_offset || [0, 0]; out.push([u, v, 2 * (s[0] + s[2]), s[2] + s[1]]); }
    else for (const f in c.faces) { const uv = c.faces[f].uv; if (!uv) continue; const x0 = Math.floor(Math.min(uv[0], uv[2])), y0 = Math.floor(Math.min(uv[1], uv[3]));
      out.push([x0, y0, Math.ceil(Math.max(uv[0], uv[2])) - x0, Math.ceil(Math.max(uv[1], uv[3])) - y0]); } }); return out; };
  const inR = (rs, x, y) => rs.some(([u, v, w, h]) => x >= u && x < u + w && y >= v && y < v + h);
  const others = rects(base);
  let spot = null;
  tex.edit(canvas => { const ctx = canvas.getContext('2d'); const d = ctx.getImageData(0, 0, W, H); const A = (x, y) => d.data[(y * W + x) * 4 + 3];
    const free = (u, v) => { for (let y = v; y < v + 15; y++) for (let x = u; x < u + 32; x++) if (A(x, y) || inR(others, x, y)) return false; return true; };
    for (let v = 0; v + 15 <= H && !spot; v++) for (let u = 0; u + 32 <= W && !spot; u++) if (free(u, v)) spot = [u, v];
    if (!spot) return canvas;
    const [u, v] = spot;
    for (let y = 0; y < 15; y++) for (let x = 0; x < 32; x++) { const a = ((21 + y) * W + 216 + x) * 4, b = ((v + y) * W + u + x) * 4; for (let k = 0; k < 4; k++) d.data[b + k] = d.data[a + k]; }
    const C = h => [parseInt(h.substr(0, 2), 16), parseInt(h.substr(2, 2), 16), parseInt(h.substr(4, 2), 16)];
    const CY = {'3,3': 'a1fbe8', '4,3': '2ce3d9', '3,4': '2ce3d9', '4,4': '12a1a0'};
    for (let y = 1; y <= 6; y++) for (let x = 1; x <= 6; x++) { const edge = x === 1 || x === 6 || y === 1 || y === 6;
      const bolt = (x === 2 || x === 5) && (y === 2 || y === 5);
      const col = edge ? (x === 1 || y === 1 ? '89939a' : '565f63') : bolt ? 'a2acb1' : (CY[x + ',' + y] || '6f797e'); const i = ((v + y) * W + u + 16 + x) * 4;
      const c = C(col); d.data[i] = c[0]; d.data[i + 1] = c[1]; d.data[i + 2] = c[2]; d.data[i + 3] = 255; }
    let n = 0; for (let y = 0; y < 8; y++) for (let x = 0; x < 28; x++) if (!inR(others, x, y) && A(x, y)) { d.data[(y * W + x) * 4 + 3] = 0; n++; }
    spot.push(n);
    ctx.putImageData(d, 0, 0); return canvas; }, {edit_name: 'Flat empty socket paint'});
  return spot ? spot : 'ABORT no space'; })()`);
if (!Array.isArray(p)) return p;
const rm = await R(`(() => { const c = Cube.all.find(c => c.name === 'socket_base'); Undo.initEdit({elements: [c], outliner: true}); c.remove();
  Undo.finishEdit('Remove socket_base', {elements: [], outliner: true}); return 'removed'; })()`);
const uv = await R(`(() => { const c = Cube.all.find(c => c.name === 'forearm_l'); Undo.initEdit({elements: [c]});
  c.uv_offset = [${p[0]}, ${p[1]}]; Canvas.updateUV(c); Undo.finishEdit('forearm_l own net', {elements: [c]}); Canvas.updateAll(); return c.uv_offset; })()`);
return {net: p, rm, uv};
