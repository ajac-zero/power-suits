import * as B from './bb.mjs';
await B.connect(); const m=await B.model(); const K=B.kit(m), L=K.L, rig=K.rig;
async function liveW(anim,t){
  await B.call('animation_timeline',{action:'set_time',time:t,animation_id:anim});
  const M=await B.evalJSON(`(() => { const o={}; Group.all.forEach(g=>{g.mesh.updateMatrixWorld(true);o[g.name]=g.mesh.matrixWorld.elements.slice();}); return JSON.stringify(o); })()`);
  const W={}; for(const n of rig.order){const e=M[n];const R=[e[0],e[4],e[8],e[1],e[5],e[9],e[2],e[6],e[10]];W[n]={R,t:L.sub([e[12],e[13],e[14]],L.mv(R,rig.bones[n].origin))};} return W;}
const SKIP=new Set((process.env.SKIP||'').split(',').filter(Boolean));
function scan(W){
// world-space faces of every cube (rest pose): 6 quads with normal + plane offset
const faces=[];
for(const n of rig.order){if(SKIP.has(n))continue;for(const c of rig.bones[n].cubes){
  const inf=c.inflate||0; const f=c.from.map(v=>v-inf), t=c.to.map(v=>v+inf);
  // cube local rotation about c.origin
  const cr=c.rotation?L.rot(c.rotation):null;
  const P=(x,y,z)=>{let p=[x,y,z]; if(cr)p=L.add(L.mv(cr,L.sub(p,c.origin)),c.origin); return L.xf(W[n],p);};
  const quads=[[[f[0],f[1],f[2]],[f[0],t[1],f[2]],[f[0],t[1],t[2]],[f[0],f[1],t[2]]],[[t[0],f[1],f[2]],[t[0],t[1],f[2]],[t[0],t[1],t[2]],[t[0],f[1],t[2]]],
    [[f[0],f[1],f[2]],[t[0],f[1],f[2]],[t[0],f[1],t[2]],[f[0],f[1],t[2]]],[[f[0],t[1],f[2]],[t[0],t[1],f[2]],[t[0],t[1],t[2]],[f[0],t[1],t[2]]],
    [[f[0],f[1],f[2]],[t[0],f[1],f[2]],[t[0],t[1],f[2]],[f[0],t[1],f[2]]],[[f[0],f[1],t[2]],[t[0],f[1],t[2]],[t[0],t[1],t[2]],[f[0],t[1],t[2]]]];
  for(const q of quads){const w=q.map(p=>P(...p));const nrm=(()=>{const a=L.sub(w[1],w[0]),b=L.sub(w[3],w[0]);const c=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];const l=L.len(c);return l<1e-9?null:c.map(v=>v/l);})();
    if(!nrm)continue; const ctr=P((f[0]+t[0])/2,(f[1]+t[1])/2,(f[2]+t[2])/2); const fc=w.reduce((a,p)=>L.add(a,p.map(v=>v/4)),[0,0,0]); const o=L.sub(fc,ctr); const nn=(o[0]*nrm[0]+o[1]*nrm[1]+o[2]*nrm[2])<0?nrm.map(v=>-v):nrm; faces.push({bone:n,cube:c.name,w,n:nn,d:L.len([0,0,0])+nrm[0]*w[0][0]+nrm[1]*w[0][1]+nrm[2]*w[0][2]});}
}}
// overlap test: project both quads onto the plane basis, intersect via polygon clipping (convex)
const basis=n=>{const a=Math.abs(n[0])<0.9?[1,0,0]:[0,1,0];const u=(()=>{const c=[a[1]*n[2]-a[2]*n[1],a[2]*n[0]-a[0]*n[2],a[0]*n[1]-a[1]*n[0]];const l=L.len(c);return c.map(v=>v/l);})();const v=[n[1]*u[2]-n[2]*u[1],n[2]*u[0]-n[0]*u[2],n[0]*u[1]-n[1]*u[0]];return [u,v];};
const area=p=>{let s=0;for(let i=0;i<p.length;i++){const a=p[i],b=p[(i+1)%p.length];s+=a[0]*b[1]-b[0]*a[1];}return s/2;};
function clip(subject,clipper){let out=subject;const ccw=area(clipper)>0?1:-1;
  for(let i=0;i<clipper.length;i++){const A=clipper[i],Bp=clipper[(i+1)%clipper.length];const inp=out;out=[];if(!inp.length)break;
    const side=p=>ccw*((Bp[0]-A[0])*(p[1]-A[1])-(Bp[1]-A[1])*(p[0]-A[0]));
    for(let j=0;j<inp.length;j++){const P=inp[j],Q=inp[(j+1)%inp.length];const sp=side(P),sq=side(Q);
      if(sp>=0)out.push(P); if((sp>=0)!==(sq>=0)){const t=sp/(sp-sq);out.push([P[0]+(Q[0]-P[0])*t,P[1]+(Q[1]-P[1])*t]);}}}
  return out;}
const hits=[];
for(let i=0;i<faces.length;i++)for(let j=i+1;j<faces.length;j++){const a=faces[i],b=faces[j];
  if(a.cube===b.cube&&a.bone===b.bone)continue;
  const dot0=0; const dot=a.n[0]*b.n[0]+a.n[1]*b.n[1]+a.n[2]*b.n[2]; if(Math.abs(Math.abs(dot)-1)>1e-4)continue;
  const db=a.n[0]*b.w[0][0]+a.n[1]*b.w[0][1]+a.n[2]*b.w[0][2]; const da=a.n[0]*a.w[0][0]+a.n[1]*a.w[0][1]+a.n[2]*a.w[0][2];
  if(Math.abs(da-db)>TOL)continue;
  const [u,v]=basis(a.n);const pr=w=>w.map(p=>[p[0]*u[0]+p[1]*u[1]+p[2]*u[2],p[0]*v[0]+p[1]*v[1]+p[2]*v[2]]);
  const ov=Math.abs(area(clip(pr(a.w),pr(b.w)))); if(ov>0.05)if(dot>0)hits.push({a:a.bone+'/'+a.cube,b:b.bone+'/'+b.cube,dir:a.n.map(v=>Math.round(v*100)/100),ov:+ov.toFixed(2),gap:+(da-db).toFixed(3)});}
return hits;}
const TOL=+(process.env.TOL||0.02);
const agg={};
if(!process.env.ANIMS){for(const h of scan(L.fk(rig,{})))console.log(JSON.stringify(h));}
else{await B.call('set_mode',{mode_id:'animate'});
for(const spec of process.env.ANIMS.split(';')){const [anim,n]=spec.split('@');const a=m.animations.find(x=>x.name===anim);
  for(let i=0;i<+n;i++){const t=a.length*i/(+n);const hs=scan(await liveW(anim,t));
    for(const h of hs){const k=h.a+' | '+h.b;const g=agg[k]=agg[k]||{n:0,ov:0,where:[]};g.n++;g.ov=Math.max(g.ov,h.ov);if(g.where.length<4)g.where.push(anim.split('.').pop()+'@'+t.toFixed(2)+' gap'+h.gap);}}}
await B.call('animation_timeline',{action:'set_time',time:0});await B.call('set_mode',{mode_id:'edit'});
for(const [k,g] of Object.entries(agg).sort((x,y)=>y[1].n-x[1].n))console.log(g.n,k,'maxOv',g.ov,g.where.join(' '));}
