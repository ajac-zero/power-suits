import * as B from './bb.mjs';
await B.connect(); const m=await B.model();
for (const file of process.argv.slice(2)) {
  const r=await B.build(file,m,{dry:true}); const spec=r.spec,K=r.K,L=K.L,rig=K.rig;
  const mz=rig.bones.muzzle_flash.origin; const P=[],A=[];
  for(let t=0;t<spec.length;t+=spec.length/60){const W=L.fk(rig,spec.sample(t));P.push(L.xf(W.muzzle_flash,mz));A.push(L.euler(W.right_forearm.R));}
  const rng=(arr,i)=>(Math.max(...arr.map(p=>p[i]))-Math.min(...arr.map(p=>p[i]))).toFixed(2);
  console.log(file.split('/').pop().padEnd(14),'muzzle travel px  x',rng(P,0),'y',rng(P,1),'z',rng(P,2),'| aim swing deg  pitch',rng(A,0),'yaw',rng(A,1),'roll',rng(A,2));
}
