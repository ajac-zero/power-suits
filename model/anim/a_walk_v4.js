// WALK v4 (power armor): heavy jog, short flight phase, stride 18, 0.6 s, ~4.95 blocks/s. Cannon stabilized.
// v3 march (3.54 b/s) a_walk_v3.js, v2 (2.35) a_walk_v2.js, v1 = old IK walk + a_walk_upper.js.
const {TAU,spring,addTo,pnoise,plant,gaitFoot,REST}=K;
const T=0.6, beta=0.375, S=18, zc=-1.8;
const Gr={x:6.0,zc,S,beta,H:4.6,yaw:REST.r.yaw*0.7,strike:12,toe:22,hang:-14,rin:0.14,rout:0.24,vm:0.45};
const Gl={...Gr,x:-6.1,yaw:REST.l.yaw*0.7};
const phR=t=>t/T, phL=t=>t/T+0.5;
// jogging: hips lowest at mid-stance (loading), highest in flight
const yHip=t=>{const p=t/T;return -3.6-0.8*Math.cos(2*TAU*(p-beta/2));};
const yS=spring(yHip,{f:2.8,z:0.35,T,loop:true});
const rel=t=>yS(t)-yHip(t);
const n1=pnoise(41,T,0.4,[1,2,3]);
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
return {name:'animation.marine.walk',length:T,loop:'loop',fps:30,tol:{r:0.05,p:0.01},
 target(t,s){return gaitFoot(s,s==='r'?phR(t):phL(t),s==='r'?Gr:Gl).pivot;},
 sample(t){return SAMPLE(t);}};
function RAW(t){
  const P={},f=TAU*t/T,R=rel(t);
  addTo(P,'marine','p',[0.5*Math.sin(f),yHip(t),0]);
  const by=5*Math.cos(f),bz=3*Math.sin(f),bx=-5-1*Math.cos(2*f);
  addTo(P,'body','r',[bx,by,bz]);
  const ty=-7*Math.cos(f-0.25),tz=-4*Math.sin(f-0.15),tx=-2-1.2*Math.cos(2*f-0.6);
  addTo(P,'torso','r',[tx,ty,tz]);
  addTo(P,'head','r',[-(bx+tx)*0.8-1+0.6*Math.cos(2*f-1.2)+n1(t),-(by+ty)*0.85,-(bz+tz)*0.7]);
  // cannon arm steady, heavy lag on the bob
  addTo(P,'right_arm','r',[3+2.4*Math.cos(2*f-1.0)+1.6*R,-(by+ty)*0.45,-2]);
  addTo(P,'right_forearm','r',[2+2.8*Math.cos(2*f-1.6)+1.3*R,0,0]);
  addTo(P,'right_pad','p',[0,0.6*R,0]);addTo(P,'right_pad','r',[0,0,2.6*R]);
  // free arm swings opposite the left leg
  addTo(P,'left_arm','r',[18*Math.cos(f-0.35),0,-2+2*Math.sin(f)]);
  addTo(P,'left_forearm','r',[14+10*Math.cos(f-0.9),0,0]);
  addTo(P,'left_pad','p',[0,0.6*R,0]);addTo(P,'left_pad','r',[0,0,-2.6*R]);
  const fr=gaitFoot('r',phR(t),Gr),fl=gaitFoot('l',phL(t),Gl);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}
