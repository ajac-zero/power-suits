// EM winding flicker (user, 2026-10-26: with the 5x5 socket base the prongs' back ends show and flickered): each copper
// winding (inflate 0.25) reached exactly the prongs' back end (y 19.625), so its top face z-fought the prong's top and
// touched the lens's. The windings move 0.25 px toward the tips (a move, so their box UV stays): tops at 19.375, a
// quarter pixel under the prong ends and the lens. Also the chaingun's axle ends 1 px lower, inside the back plate
// (its top met the plate's top under the base; flat-coloured sides, nothing lost). Codemode function body.
const R = async code => { const t = (await tools.mcp__blockbench__risky_eval({code})).content[0].text; let v = t; for (let i = 0; i < 2 && typeof v === 'string'; i++) { try { v = JSON.parse(v); } catch (e) { break; } } return v; };
const ok = await R(`(() => { const w = [1, 2, 3, 4].map(i => Cube.all.find(c => c.name === 'em_winding_' + i)), a = Cube.all.find(c => c.name === 'chaingun_axle');
  return w.every(c => c && Math.abs(c.to[1] - 19.375) < 1e-6 && c.inflate === 0.25) && a && Math.abs(a.to[1] - 19.625) < 1e-6; })()`);
if (ok !== true) return 'ABORT not in the expected state';
await tools.mcp__blockbench__save_checkpoint({name: "before EM winding flicker fix"});
return await R(`(() => { const w = [1, 2, 3, 4].map(i => Cube.all.find(c => c.name === 'em_winding_' + i)), a = Cube.all.find(c => c.name === 'chaingun_axle');
  Undo.initEdit({elements: w.concat([a])});
  for (const c of w) { c.from = [c.from[0], c.from[1] - 0.25, c.from[2]]; c.to = [c.to[0], c.to[1] - 0.25, c.to[2]]; }
  a.to = [a.to[0], 18.625, a.to[2]];
  Undo.finishEdit('EM windings off the prong ends, chaingun axle inside the back plate', {elements: w.concat([a])});
  Canvas.updateAll(); return w.concat([a]).map(c => c.name + ' ' + JSON.stringify([c.from, c.to])); })()`);
