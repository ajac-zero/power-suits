import * as B from './bb.mjs';
await B.connect(); const m=await B.model();
await B.call('set_mode',{mode_id:'animate'});
for(const name of process.argv.slice(2)){const a=m.animations.find(x=>x.name===name);const N=120,pts=[];
  for(let i=0;i<=N;i++){const t=a.length*i/N;await B.call('animation_timeline',{action:'set_time',time:t,animation_id:name});
    const e=await B.evalJSON(`(() => { const g=Group.all.find(g=>g.name==='right_foot'); g.mesh.updateMatrixWorld(true); return JSON.stringify(g.mesh.matrixWorld.elements.slice(12,15)); })()`);pts.push([t,...e]);}
  const ymin=Math.min(...pts.map(p=>p[2]));const v=[];for(let i=1;i<pts.length;i++)if(pts[i][2]<ymin+0.1&&pts[i-1][2]<ymin+0.1)v.push((pts[i][3]-pts[i-1][3])/(pts[i][0]-pts[i-1][0]));
  v.sort((x,y)=>x-y);console.log(name,'live blocks/s',(v[v.length>>1]/16).toFixed(3),'len',a.length);}
await B.call('animation_timeline',{action:'set_time',time:0});await B.call('set_mode',{mode_id:'edit'});
