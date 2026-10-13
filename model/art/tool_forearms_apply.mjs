// Tool forearms (run from /private/tmp/sa after tool_forearms_art.py): checkpoint, repaint (clear the old
// nets, paint the new ones), remove the old forearm/tool cubes, move the bone pivots to the elbow socket,
// raise the muzzle flash into the new muzzle brake, place the new cubes. Verifies pivots at the end.
import {connect,call,evalJSON} from '../../.agents/skills/blockbench-mcp/scripts/bb.mjs';
import fs from 'node:fs';
await connect();
const plan=JSON.parse(fs.readFileSync('tf_plan.json','utf8'));
const paint=fs.readFileSync('tf_paint.json','utf8');
await call('save_checkpoint',{name:'before tool forearms'});
const p=await evalJSON(`(() => { const P=${paint}; const tex=Texture.all[0];
 tex.edit(canvas => { const ctx=canvas.getContext('2d');
   for (const [x,y] of P.clear) ctx.clearRect(x,y,1,1);
   for (const r of P.rects) { const img=ctx.createImageData(r.w,r.h);
     for (let i=0;i<r.w*r.h;i++) for(let k=0;k<4;k++) img.data[i*4+k]=parseInt(r.hex.substr(i*8+k*2,2),16);
     ctx.putImageData(img,r.x,r.y); }
   return canvas; }, {edit_name:'Paint tool forearms'});
 return JSON.stringify({ok:true}); })()`);
console.log('paint',p);
for(const n of plan.removed) await call('remove_element',{id:n});
const piv=(name,origin)=>call('bone_rigging',{action:'set_pivot',bone_data:{name,origin}});
for(const b of ['cannon','em_projector','drill','chainsaw','socket']) await piv(b,[12.5,25.625,0]);
await piv('drill_bit',[12.5,17.625,0]);
await piv('chainsaw_chain',[12.5,14.125,0]);
await piv('muzzle_flash',[12.5,11.625,0]);
await call('modify_cube',{id:'flash',from:[10.5,10.125,-2],to:[14.5,14.125,2],origin:[12.5,11.625,0]});
for(const c of plan.cubes){
  const el={name:c.name,from:c.from_,to:c.to,origin:c.origin,uv_offset:c.uv_offset};
  if(c.rotation) el.rotation=c.rotation;
  await call('place_cube',{group:c.bone,texture:'marine.png',elements:[el]});
}
console.log(await evalJSON(`(() => JSON.stringify(Object.fromEntries(['right_forearm','socket','cannon','muzzle_flash','em_projector','drill','drill_bit','chainsaw','chainsaw_chain'].map(n=>{const g=Group.all.find(g=>g.name===n);return [n,{o:g.origin,r:g.rotation,cubes:g.children.filter(c=>c instanceof Cube).map(c=>c.name).join(' ')}];}))))()`));
