const D2R = Math.PI / 180;
function rx(a){a*=D2R;const c=Math.cos(a),s=Math.sin(a);return [1,0,0,0,c,-s,0,s,c];}
function ry(a){a*=D2R;const c=Math.cos(a),s=Math.sin(a);return [c,0,s,0,1,0,-s,0,c];}
function rz(a){a*=D2R;const c=Math.cos(a),s=Math.sin(a);return [c,-s,0,s,c,0,0,0,1];}
function mm(A,B){const C=new Array(9);for(let i=0;i<3;i++)for(let j=0;j<3;j++)C[i*3+j]=A[i*3]*B[j]+A[i*3+1]*B[3+j]+A[i*3+2]*B[6+j];return C;}
function mv(A,v){return [A[0]*v[0]+A[1]*v[1]+A[2]*v[2],A[3]*v[0]+A[4]*v[1]+A[5]*v[2],A[6]*v[0]+A[7]*v[1]+A[8]*v[2]];}
function tr(A){return [A[0],A[3],A[6],A[1],A[4],A[7],A[2],A[5],A[8]];}
function rot(e){return mm(rz(e[2]),mm(ry(e[1]),rx(e[0])));}
function euler(R){const sy=-R[6];const y=Math.asin(Math.max(-1,Math.min(1,sy)));let x,z;
  if(Math.abs(sy)<0.99999){x=Math.atan2(R[7],R[8]);z=Math.atan2(R[3],R[0]);}else{x=Math.atan2(-R[5],R[4]);z=0;}
  return [x/D2R,y/D2R,z/D2R];}
const add=(a,b)=>[a[0]+b[0],a[1]+b[1],a[2]+b[2]];
const sub=(a,b)=>[a[0]-b[0],a[1]-b[1],a[2]-b[2]];
const sc=(a,k)=>[a[0]*k,a[1]*k,a[2]*k];
const len=(a)=>Math.hypot(a[0],a[1],a[2]);
function makeRig(model){
  const g={};model.groups.forEach(x=>g[x.uuid]=x);
  const el={};model.elements.forEach(e=>el[e.uuid]=e);
  const bones={},order=[];
  function walk(n,parent){ if(typeof n==="string")return; const gg=g[n.uuid];
    bones[gg.name]={name:gg.name,origin:gg.origin,rest:gg.rotation||[0,0,0],parent,cubes:[]}; order.push(gg.name);
    (n.children||[]).forEach(c=>{ if(typeof c==="string") bones[gg.name].cubes.push(el[c]); else walk(c,gg.name);});}
  model.outliner.forEach(n=>walk(n,null));
  return {bones,order};
}
const CFG={psign:[1,1,1]};
// pose: {bone:{r:[deg],p:[units],s:[k,k,k]}} -> world transforms {R,t}
function fk(rig,pose){const W={};
  for(const n of rig.order){const b=rig.bones[n],a=pose[n]||{};const r=a.r||[0,0,0],p=a.p||[0,0,0];
    let R=rot(add(b.rest,r)); if(a.s){const S=a.s;R=mm(R,[S[0],0,0,0,S[1],0,0,0,S[2]]);}
    const o=add(b.origin,[p[0]*CFG.psign[0],p[1]*CFG.psign[1],p[2]*CFG.psign[2]]);
    let M={R,t:sub(o,mv(R,b.origin))};
    if(b.parent){const P=W[b.parent]; M={R:mm(P.R,M.R),t:add(mv(P.R,M.t),P.t)};}
    W[n]=M;}
  return W;}
const xf=(M,v)=>add(mv(M.R,v),M.t);
function cubeCorners(c){const f=c.from,t=c.to,i=c.inflate||0;const pts=[];
  for(const x of [f[0]-i,t[0]+i])for(const y of [f[1]-i,t[1]+i])for(const z of [f[2]-i,t[2]+i]){
    let p=[x,y,z]; if(c.rotation&&c.rotation.some(v=>v)){const o=c.origin;p=add(mv(rot(c.rotation),sub(p,o)),o);} pts.push(p);}
  return pts;}
return {D2R,rx,ry,rz,mm,mv,tr,rot,euler,add,sub,sc,len,makeRig,CFG,fk,xf,cubeCorners};
