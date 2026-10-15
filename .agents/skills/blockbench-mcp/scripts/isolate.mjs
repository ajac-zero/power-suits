// node isolate.mjs on|off : show only the right leg + hips (on), restore (off)
import * as B from './bb.mjs';
await B.connect();
const mode=process.argv[2];
const code = mode==='on' ? `(() => { const keep = new Set(['right_leg','right_shin','right_foot','pelvis']);
  window.__isoHidden = []; Cube.all.forEach(c => { if (!keep.has(c.parent && c.parent.name) && c.visibility) { c.visibility = false; window.__isoHidden.push(c.uuid); } });
  Canvas.updateVisibility(); return window.__isoHidden.length + ' hidden'; })()`
 : `(() => { const ids = window.__isoHidden || []; Cube.all.forEach(c => { if (ids.includes(c.uuid)) c.visibility = true; }); window.__isoHidden = [];
  Canvas.updateVisibility(); return ids.length + ' restored'; })()`;
const r=await B.call('risky_eval',{code});console.log(r.content[0].text);
