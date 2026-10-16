// Sweep a gait spec's parameters and report legs + smoothness per set (dry bake, 120 fps, no upload).
// usage: node gaitsweep.mjs <spec.js> '[{...K.P overrides}, ...]'      ({} = the spec's defaults)
// env: MODEL=file.bbmodel sweeps a saved model instead of the live project (e.g. `git show REV:model/x.bbmodel > f`)
// Columns (spec bone names; the knee = bone flex incl. rest, + = bent):
//   knee / thigh / ankle  ranges over the cycle, both legs
//   flatKnee   knee range while a foot is flat on the ground (the loaded leg: what reads as crouched or not)
//   low        lowest foot point (< -0.05: a foot sinks into the ground)
//   step       biggest one-frame knee change (a STUTTER is a jump here: 47 deg when the IK flipped knee solutions)
//   kink       biggest second difference (a corner: a clamp hit, a contact toggling, a foot slapping down)
//   turns      knee direction changes per cycle, right leg (a clean run/walk: 4; a wobble adds 2 per bump)
import * as B from './bb.mjs';
import fs from 'node:fs';
const [file,cs='[{}]']=process.argv.slice(2);
let m;if(process.env.MODEL)m=JSON.parse(fs.readFileSync(process.env.MODEL,'utf8'));else{await B.connect();m=await B.model();}
for(const P of JSON.parse(cs)){const r=await B.build(file,m,{dry:true,K:{P}});const spec=r.spec,K=r.K,rig=K.rig,L=K.L;
  const N=Math.round(spec.length*120),R={knee:[999,-999],thigh:[999,-999],ankle:[999,-999],flat:[999,-999]};let low=99,step=0,kink=0;const prev={},pp={},kr=[];
  const ext=(a,v)=>{a[0]=Math.min(a[0],v);a[1]=Math.max(a[1],v);};
  for(let i=0;i<=N;i++){const Q=spec.sample(spec.length*i/N);const W=L.fk(rig,Q);
    for(const s of ['r','l']){const S=K.SIDE[s];const k=-(Q[S.shin].r[0]+rig.bones[S.shin].rest[0]);
      ext(R.knee,k);ext(R.thigh,Q[S.leg].r[0]+rig.bones[S.leg].rest[0]);ext(R.ankle,Q[S.foot].r[0]+rig.bones[S.foot].rest[0]);
      const lo=K.footLow(Q,s);low=Math.min(low,lo);if(lo<0.05&&Math.abs(L.euler(W[S.foot].R)[0])<2)ext(R.flat,k);
      if(prev[s]!=null){step=Math.max(step,Math.abs(k-prev[s]));if(pp[s]!=null)kink=Math.max(kink,Math.abs(k-2*prev[s]+pp[s]));pp[s]=prev[s];}prev[s]=k;
      if(s==='r'&&i<N)kr.push(k);}}
  let turns=0,dir=0,last=kr[0];for(let i=1;i<=kr.length;i++){const v=kr[i%kr.length];if(Math.abs(v-last)<1.5)continue;const d=Math.sign(v-last);if(dir&&d!==dir)turns++;dir=d;last=v;}
  const f=a=>a[0]>a[1]?'-':a.map(x=>x.toFixed(0)).join('..');
  console.log(JSON.stringify(P),'knee',f(R.knee),'flatKnee',f(R.flat),'thigh',f(R.thigh),'ankle',f(R.ankle),'low',low.toFixed(2),'step',step.toFixed(1),'kink',kink.toFixed(1),'turns',turns);}
