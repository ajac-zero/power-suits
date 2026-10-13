import * as B from './bb.mjs';
await B.connect(); const m=await B.model();
for (const file of process.argv.slice(2)) {
  const r=await B.build(file,m,{dry:true}); const spec=r.spec, K=r.K, L=K.L;
  // foot pivot world z from FK of the baked spec, during flat stance (pivot at its lowest)
  const dt=0.002, pts=[];
  for(let t=0;t<=spec.length;t+=dt){const P=spec.sample(t);const W=L.fk(K.rig,P);const f=K.SIDE.r.foot;pts.push([t,...L.xf(W[f],K.rig.bones[f].origin)]);}
  const ymin=Math.min(...pts.map(p=>p[2])); const v=[];
  for(let i=1;i<pts.length;i++)if(pts[i][2]<ymin+0.05&&pts[i-1][2]<ymin+0.05)v.push((pts[i][3]-pts[i-1][3])/dt);
  v.sort((a,b)=>a-b); console.log(file, 'stance px/s', v[v.length>>1].toFixed(1), 'blocks/s', (v[v.length>>1]/16).toFixed(3), 'flat samples', v.length);
}
