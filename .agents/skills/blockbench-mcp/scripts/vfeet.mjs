// Live check: lowest foot point per animation as Blockbench itself interpolates it (catches key-reduction errors).
// usage: node vfeet.mjs animation.x.walk [more...]   (bones right_foot/left_foot assumed)
import * as B from './bb.mjs';
await B.connect();const m=await B.model();const K=B.kit(m);const L=K.L,rig=K.rig;
const feet=['right_foot','left_foot'];
const probe=`(() => { const out={}; for(const n of ${JSON.stringify(feet)}){const g=Group.all.find(x=>x.name===n); g.mesh.updateMatrixWorld(true); out[n]=g.mesh.matrixWorld.elements.slice(); } return JSON.stringify(out); })()`;
for(const name of process.argv.slice(2)){const a=m.animations.find(x=>x.name===name);let lo=1e9,at=0;
  for(let i=0;i<64;i++){const t=a.length*i/64;await B.call('animation_timeline',{action:'set_time',time:t,animation_id:name});
    const M=await B.evalJSON(probe);
    for(const f of feet){const e=M[f],o=rig.bones[f].origin;const R=[e[0],e[4],e[8],e[1],e[5],e[9],e[2],e[6],e[10]];
      for(const c of rig.bones[f].cubes)for(const p of L.cubeCorners(c)){const y=L.add(L.mv(R,L.sub(p,o)),[e[12],e[13],e[14]])[1];if(y<lo){lo=y;at=t;}}}}
  console.log(name,'min foot y',lo.toFixed(3),'at',at.toFixed(3));}
