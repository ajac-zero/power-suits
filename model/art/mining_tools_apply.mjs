import {connect,call,evalJSON} from '../../.agents/skills/blockbench-mcp/scripts/bb.mjs';
import fs from 'node:fs';
await connect();
const plan=JSON.parse(fs.readFileSync('plan.json','utf8'));
const rects=JSON.parse(fs.readFileSync('paint.json','utf8'));
await call('save_checkpoint',{name:'before drill + chainsaw'});
// 1. paint (aborts if any target pixel is already used)
const R=JSON.stringify(rects);
const code=`(() => { const rects=${R}; const tex=Texture.all[0]; let bad=null;
 tex.edit(canvas => { const ctx=canvas.getContext('2d');
   for (const r of rects) { const img=ctx.getImageData(r.x,r.y,r.w,r.h);
     for (let i=0;i<r.w*r.h;i++){ const a=parseInt(r.hex.substr(i*8+6,2),16); if(a && img.data[i*4+3]) { bad=[r.x+i%r.w, r.y+Math.floor(i/r.w)]; return canvas; } } }
   for (const r of rects) { const img=ctx.getImageData(r.x,r.y,r.w,r.h);
     for (let i=0;i<r.w*r.h;i++){ const a=parseInt(r.hex.substr(i*8+6,2),16); if(!a) continue;
       for(let k=0;k<4;k++) img.data[i*4+k]=parseInt(r.hex.substr(i*8+k*2,2),16); }
     ctx.putImageData(img,r.x,r.y); }
   return canvas; }, {edit_name:'Paint drill + chainsaw'});
 return JSON.stringify({bad}); })()`;
const p=await evalJSON(code); console.log('paint',p); if(p.bad) process.exit(1);
// 2. bones
const B=a=>call('bone_rigging',{action:'create',bone_data:a});
await B({name:'drill',origin:[12.5,14.625,0],parent:'right_forearm'});
await B({name:'drill_bit',origin:[12.5,15.625,0],parent:'drill'});
await B({name:'chainsaw',origin:[12.5,14.625,0],parent:'right_forearm'});
await B({name:'chainsaw_chain',origin:[12.5,11.625,0],parent:'chainsaw'});
// 3. cubes
const collar=n=>({name:n,from:[7,15.625,-5],to:[18,17.625,5],origin:[12.5,14.625,0],uv_offset:[198,38]});
await call('place_cube',{group:'drill',texture:'marine.png',elements:[collar('drill_collar')]});
await call('place_cube',{group:'chainsaw',texture:'marine.png',elements:[collar('saw_collar')]});
for(const c of plan){
  const group=c.bone==='drill'&&c.name!=='drill_collar'?'drill_bit':(c.name==='saw_chain'?'chainsaw_chain':c.bone);
  const el={name:c.name,from:c.from_,to:c.to,origin:c.origin,uv_offset:c.uv_offset};
  if(c.rotation)el.rotation=c.rotation;
  await call('place_cube',{group,texture:'marine.png',elements:[el]});
}
console.log('done');
