// Legs v10 check (run from the repo root): node model/art/heelgap.mjs [file.bbmodel]
// per walk frame: is the shin's back bottom edge on boot material (gap = distance outside every boot cube), and does
// boot material poke out behind the shin's back face above its bottom (poke)? node heelgap.mjs [model.bbmodel]
import * as B from '../../.agents/skills/blockbench-mcp/scripts/bb.mjs';
import fs from 'node:fs';
const f=process.argv[2];let m;if(f)m=JSON.parse(fs.readFileSync(f,'utf8'));else{await B.connect();m=await B.model();}
const r=await B.build('model/anim/a_walk.js',m,{dry:true});const K=r.K,L=K.L,rig=K.rig,spec=r.spec;
const S=K.SIDE.r,shin=rig.bones[S.shin],foot=rig.bones[S.foot];
const xs=shin.cubes.filter(c=>/shin_main_b/.test(c.name))[0];const X=(xs.from[0]+xs.to[0])/2;
const local=(c,p)=>{let q=p;if(c.rotation&&c.rotation.some(v=>v))q=L.add(L.mv(L.tr(L.rot(c.rotation)),L.sub(p,c.origin)),c.origin);return q;};
const inside=(c,p)=>{const q=local(c,p),i=c.inflate||0;let d=1e9;for(let k=0;k<3;k++)d=Math.min(d,q[k]-(c.from[k]-i),(c.to[k]+i)-q[k]);return d;};
let worstGap=0,worstPoke=0,at={};
for(let i=0;i<=100;i++){const t=spec.length*i/100;const P=spec.sample(t);const W=L.fk(rig,P);
  const toFoot=p=>L.mv(L.tr(W[S.foot].R),L.sub(p,W[S.foot].t)), toShin=p=>L.mv(L.tr(W[S.shin].R),L.sub(p,W[S.shin].t));
  // shin back bottom edge, a little inside the corner
  const e=toFoot(L.xf(W[S.shin],[X,0.7,3.9]));let best=-1e9;for(const c of foot.cubes)best=Math.max(best,inside(c,e));
  const gap=Math.max(0,-best);
  // boot cube corners/edge points in shin space: behind the back face (z>4) and above the shin's bottom (y>0.625)
  let poke=0;for(const c of foot.cubes){for(const fx of [0,1])for(const fy of [0,0.5,1])for(const fz of [0,0.5,1]){const i=c.inflate||0;
    let p=[c.from[0]+(c.to[0]-c.from[0])*fx,c.from[1]-i+(c.to[1]-c.from[1]+2*i)*fy,c.from[2]-i+(c.to[2]-c.from[2]+2*i)*fz];
    if(c.rotation&&c.rotation.some(v=>v))p=L.add(L.mv(L.rot(c.rotation),L.sub(p,c.origin)),c.origin);
    const q=toShin(L.xf(W[S.foot],p));if(q[1]>0.7&&q[2]>4)poke=Math.max(poke,q[2]-4);}}
  if(gap>worstGap){worstGap=gap;at.gap=t.toFixed(2);}if(poke>worstPoke){worstPoke=poke;at.poke=t.toFixed(2);}}
console.log(f||'live','back-edge gap',worstGap.toFixed(2),'@',at.gap,'| boot poking out behind the shin',worstPoke.toFixed(2),'@',at.poke);
