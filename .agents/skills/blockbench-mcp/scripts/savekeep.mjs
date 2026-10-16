// Save the live project with every cube visible (cube visibility is saved in the .bbmodel), then hide again the cubes
// the user had hidden in Blockbench. usage: node savekeep.mjs
import * as B from './bb.mjs';
await B.connect();
console.log((await B.call('risky_eval',{code:`(() => { const hid = Cube.all.filter(c => c.visibility === false); hid.forEach(c => c.visibility = true); Canvas.updateVisibility();
  window.__userHidden = hid.map(c => c.uuid); return JSON.stringify({path: Project.save_path, hidden: hid.length}); })()`})).content[0].text);
await B.call('risky_eval',{code:'(BarItems.save_project.trigger(), 1)'});
console.log((await B.call('risky_eval',{code:`(() => { const ids = window.__userHidden || []; Cube.all.forEach(c => { if (ids.includes(c.uuid)) c.visibility = false; }); Canvas.updateVisibility(); return ids.length + ' re-hidden'; })()`})).content[0].text);
