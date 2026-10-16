// Where a gait spec kinks: per-frame second differences (120 fps) of the right leg's angles and the root height above
// a threshold, as time:value. usage: node kinks.mjs <spec.js> ['{K.P}'] [threshold=3]
// Pair with gaitsweep.mjs: it says how bad, this says when (then read the spec's events at that time: strike, toe-off,
// a clamp or limit kicking in).
import * as B from './bb.mjs';
await B.connect();const m=await B.model();const [file,P='{}',thr='3']=process.argv.slice(2);
const r=await B.build(file,m,{dry:true,K:{P:JSON.parse(P)}});const spec=r.spec,K=r.K;
const N=Math.round(spec.length*120);const F=[];for(let i=0;i<N;i++)F.push(spec.sample(spec.length*i/N));
for(const [b,c,j] of [[K.SIDE.r.shin,'r',0],[K.SIDE.r.leg,'r',0],[K.SIDE.r.foot,'r',0],['marine','p',1]]){const v=F.map(Q=>Q[b]&&Q[b][c]?Q[b][c][j]:0);const out=[];
  for(let i=0;i<N;i++){const k=v[(i+1)%N]-2*v[i]+v[(i-1+N)%N];if(Math.abs(k)>+thr)out.push(`${(spec.length*i/N).toFixed(3)}:${k.toFixed(1)}`);}
  console.log(b,c,j,out.join(' ')||'-');}
