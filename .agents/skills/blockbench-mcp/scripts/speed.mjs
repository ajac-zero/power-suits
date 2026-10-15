import * as B from './bb.mjs';
await B.connect(); const m=await B.model();
for (const file of process.argv.slice(2)) {
  const r=await B.build(file,m,{dry:true}); const spec=r.spec, K=r.K, L=K.L;
  // foot pivot world z from FK of the baked spec, during flat stance: the foot level (pitch ~0) and its pivot at the
  // flat-stance height (the most common low height; a heel strike rolled about the heel can sit lower than flat)
  const dt=0.002, pts=[];
  for(let t=0;t<=spec.length;t+=dt){const P=spec.sample(t);const W=L.fk(K.rig,P);const f=K.SIDE.r.foot;
    pts.push([t,...L.xf(W[f],K.rig.bones[f].origin),Math.abs(L.euler(W[f].R)[0])]);}
  const hist={};for(const p of pts)if(p[4]<0.6){const k=p[2].toFixed(2);hist[k]=(hist[k]||0)+1;}
  const yflat=+Object.entries(hist).sort((a,b)=>b[1]-a[1])[0][0]; const v=[];
  const flat=p=>Math.abs(p[2]-yflat)<0.03&&p[4]<0.6;
  for(let i=1;i<pts.length;i++)if(flat(pts[i])&&flat(pts[i-1]))v.push((pts[i][3]-pts[i-1][3])/dt);
  v.sort((a,b)=>a-b); console.log(file, 'stance px/s', v[v.length>>1].toFixed(1), 'blocks/s', (v[v.length>>1]/16).toFixed(3), 'flat samples', v.length);
}
