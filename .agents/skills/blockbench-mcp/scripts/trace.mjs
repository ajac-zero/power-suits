import * as B from './bb.mjs';
await B.connect(); const m=await B.model();
const [file,dt='0.05']=process.argv.slice(2);
const r=await B.build(file,m,{dry:true,K:JSON.parse(process.env.K_JSON||'{}')}); const K=r.K,spec=r.spec,L=K.L;
for(let t=0;t<=spec.length+1e-9;t+=+dt){const P=spec.sample(t);const W=L.fk(K.rig,P);
  const row=['right_leg','right_shin','right_foot','left_leg','left_shin'].map(n=>(P[n].r[0]).toFixed(0)).join(' ');
  const err=['r','l'].map(s=>{const f=K.SIDE[s].foot;const tg=spec.target?spec.target(t,s):null;return tg?L.len(L.sub(L.xf(W[f],K.rig.bones[f].origin),tg)).toFixed(3):'-';}).join('/');
  console.log(t.toFixed(3),'legs',row,'low',K.footLow(P,'r').toFixed(2),K.footLow(P,'l').toFixed(2),'err',err);}
