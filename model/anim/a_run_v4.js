// RUN v4 (power armor): bounding sprint, long flight phase, stride 19, 0.42 s, ~10 blocks/s. Cannon stabilized.
// v3 (8.52 b/s) a_run_v3.js, v2 (6.36) a_run_v2.js, v1 (3.35) a_run_v1.js
const {TAU,spring,addTo,pnoise,plant,gaitFoot,REST}=K;
const T=0.42, beta=0.283, S=19, zc=-1.9;
const Gr={x:6.2,zc,S,beta,H:5.8,yaw:REST.r.yaw*0.6,strike:12,toe:28,hang:-30,rin:0.12,rout:0.3,vm:0.5};
const Gl={...Gr,x:-6.3,yaw:REST.l.yaw*0.6};
const phR=t=>t/T, phL=t=>t/T+0.5;            // right heel strike at t=0, left at T/2
const yHip=t=>{const p=t/T;return -4.0-1.1*Math.cos(2*TAU*(p-beta/2));};
const yS=spring(yHip,{f:3.2,z:0.3,T,loop:true});
const rel=t=>yS(t)-yHip(t);
const n1=pnoise(21,T,0.5,[1,2,3]);
// --- cannon stabilizer (same block in a_walk.js / a_run.js) ---------------------------------------
// Holds the cannon steady relative to the suit (it sits right in the cockpit view): IK the upper arm
// so the elbow stays near its cycle-average position, and fix the forearm's world orientation.
// keepPos / keepRot = how much of the original bob/sway survives (0 = rigid mount).
function stabilize(raw,T,{keepPos=0.25,keepRot=0.15,N=30}={}){
  const L=K.L,rig=K.rig,EL=rig.bones.right_forearm.origin;let ref=null;
  const fwd=P=>{const W=L.fk(rig,P);return {E:L.xf(W.right_arm,EL),R:W.right_forearm.R};};
  const mean=()=>{const acc={};for(let i=0;i<N;i++){const P=raw(T*i/N);
    for(const b in P)for(const ch in P[b]){const v=P[b][ch];if(!Array.isArray(v))continue;
      const a=((acc[b]=acc[b]||{})[ch]=acc[b][ch]||[0,0,0]);v.forEach((x,j)=>a[j]+=x/N);}}return fwd(acc);};
  return t=>{const P=raw(t);if(!ref)ref=mean();const cur=fwd(P);
    const E=L.add(ref.E,L.sc(L.sub(cur.E,ref.E),keepPos));
    const e0=L.euler(ref.R),e1=L.euler(cur.R);
    const R=L.rot(e0.map((v,i)=>v+K.wrapDeg(e1[i]-v)*keepRot));
    K.ik(P,[['right_arm',0],['right_arm',1],['right_arm',2]],'right_arm',EL,E,{iters:30,damp:0.01});
    K.orient(P,'right_forearm',R);
    return P;};
}
// ---
const SAMPLE=stabilize(RAW,T);
return {name:'animation.marine.run',length:T,loop:'loop',fps:60,tol:{r:0.04,p:0.01},
 target(t,s){return gaitFoot(s,s==='r'?phR(t):phL(t),s==='r'?Gr:Gl).pivot;},
 sample(t){return SAMPLE(t);}};
function RAW(t){
  const P={},f=TAU*t/T,R=rel(t);
  addTo(P,'marine','p',[0.35*Math.sin(f),yHip(t),0]);
  const by=6*Math.cos(f),bz=3*Math.sin(f),bx=-11-1.5*Math.cos(2*f-0.4);
  addTo(P,'body','r',[bx,by,bz]);
  const ty=-9*Math.cos(f-0.2),tz=-4.5*Math.sin(f-0.2),tx=-5-1.5*Math.cos(2*f-1.0);
  addTo(P,'torso','r',[tx,ty,tz]);
  addTo(P,'head','r',[-(bx+tx)*0.85-2+1.2*Math.cos(2*f-1.6)+n1(t),-(by+ty)*0.8,-(bz+tz)*0.6]);
  // cannon: carried low-ready, swings with shoulders, bounces with lag
  addTo(P,'right_arm','r',[8+3*Math.cos(2*f-1.2)+2.5*R,-(by+ty)*0.3,-4]);
  addTo(P,'right_forearm','r',[6+3*Math.cos(2*f-1.8)+2*R,0,0]);
  addTo(P,'right_pad','p',[0,0.8*R,0]);addTo(P,'right_pad','r',[0,0,3.5*R]);
  // free arm pumps opposite left leg
  addTo(P,'left_arm','r',[-4+30*Math.cos(f-0.35),0,-3+3*Math.sin(f)]);
  addTo(P,'left_forearm','r',[38+18*Math.cos(f-1.0),0,0]);
  addTo(P,'left_pad','p',[0,0.8*R,0]);addTo(P,'left_pad','r',[0,0,-3.5*R]);
  const fr=gaitFoot('r',phR(t),Gr),fl=gaitFoot('l',phL(t),Gl);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}
