// RUN v6 (power armor, 2026-10-14): an energetic bounding sprint (the suit smashes glass at this pace). Forward lean,
// long flight, big vertical bounce, push-off with the leg fully extended behind, the heel kicks up behind and the knee
// drives forward-high before the foot reaches out and paws back into the next strike; free arm pumps hard, the torso
// counter-twists, every footfall lands with a jolt. 6.25 blocks/s. Knee up to 101 deg in the heel kick (the knee
// bends cleanly to ~100, model/art/legs_stomper_art.py); past the boot's ankle range (-30..40) the boot rolls.
// v5 (flat, low swing: "a shuffle") in a_run_v5.js; v4 (10 b/s, hips 4 px down, knees to 150) in a_run_v4.js.
const {TAU,spring,addTo,pnoise,plant,gaitFoot,footR,REST,smooth,clamp,lerp}=K;
const O=Object.assign({T:0.56,beta:0.3,S:16.8,zc:3.5,hip:-0.3,bob:1.4,H:3.0,up:0.48,wide:0.5,kick:0.28,
  strike:12,toe:30,hang:-20,lean:-11,arm:44,low:0.35},K.P||{});
const {T,beta,S,zc}=O;
const Gr={x:REST.r.pivot[0],zc,S,beta,H:0,yaw:REST.r.yaw,strike:O.strike,toe:O.toe,hang:-10,rin:0.12,rout:0.32,vm:0.5};
const Gl={...Gr,x:REST.l.pivot[0],yaw:REST.l.yaw};
const phR=t=>t/T, phL=t=>t/T+0.5;            // right heel strike at t=0, left at T/2
const Tsw=(1-beta)*T, vg=S/(beta*T);          // swing time, ground speed (px/s)
// Sprint swing (body frame, +z = back): the foot keeps travelling back with the ground after toe-off and rises
// (heel kick), peaks early (u = O.up) tucked under the seat, then whips forward and paws back slightly at strike.
function foot(s,t){
  const ph=((((s==='r'?phR(t):phL(t))%1)+1)%1),G=s==='r'?Gr:Gl;
  if(ph<beta)return gaitFoot(s,ph,G);
  const u=(ph-beta)/(1-beta),A=gaitFoot(s,beta-1e-6,G),B=gaitFoot(s,0,G);
  const h00=2*u*u*u-3*u*u+1,h10=u*u*u-2*u*u+u,h01=-2*u*u*u+3*u*u,h11=u*u*u-u*u;
  const v0=vg*Tsw*O.kick, v1=vg*Tsw*0.12;     // dz/du: still moving back at toe-off, pawing back at strike
  const z=h00*A.pivot[2]+h10*v0+h01*B.pivot[2]+h11*v1;
  const w=Math.pow(u,Math.log(0.5)/Math.log(O.up));   // skewed bump: highest at u = O.up
  const lift=O.H*Math.pow(Math.sin(Math.PI*w),O.wide);
  const base=lerp(A.pivot[1],B.pivot[1],smooth(u));
  // pitch: toe-off -> toes hanging (heel kicked up) -> toes up for the strike
  const pitch=u<O.up?lerp(-O.toe,O.hang,smooth(u/O.up)):lerp(O.hang,O.strike,smooth((u-O.up)/(1-O.up)));
  return {pivot:[G.x,base+lift,z],R:footR(G.yaw,pitch)};}
// running: hips lowest at mid-stance (loading), highest in flight
const yHip=t=>{const p=t/T;return O.hip-O.bob*Math.cos(2*TAU*(p-beta*O.low));};   // lowest a little before mid-stance: rising into the push-off
const yS=spring(yHip,{f:3.2,z:0.3,T,loop:true});
const rel=t=>yS(t)-yHip(t);
// footfall jolt: an impulse at each strike through a stiff spring (torso/head/arms take it with different lags)
const imp=t=>{let v=0;for(const t0 of [0,T/2,T,-T/2]){const d=t-t0;if(d>=0&&d<T/2)v+=d<0.015?d/0.015:Math.exp(-(d-0.015)/0.05);}return v;};
const jolt=spring(imp,{f:6,z:0.35,T,loop:true});
const n1=pnoise(21,T,0.4,[1,2,3]);
// --- tool arm stabilizer (same block in a_walk.js / a_run.js; spec names: right_* = the live tool arm) -----------
// IK the upper arm so the elbow stays near its cycle-average position, and fix the forearm's world orientation.
// keepPos / keepRot = how much of the original bob/sway survives (0 = rigid mount). The cockpit shows a viewmodel,
// so in a sprint the tool arm may pump (third person only).
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
const SAMPLE=stabilize(RAW,T,{keepPos:0.6,keepRot:0.5});
return {name:'animation.marine.run',length:T,loop:'loop',fps:60,tol:{r:0.04,p:0.01},
 target(t,s){return foot(s,t).pivot;},
 sample(t){return SAMPLE(t);}};
function RAW(t){
  const P={},f=TAU*t/T,R=rel(t),J=jolt(t);
  addTo(P,'marine','p',[0.45*Math.sin(f),yHip(t),0]);
  // lean into the run from the waist; the hips twist with the legs, the shoulders against them
  const by=8*Math.cos(f),bz=3*Math.sin(f),bx=O.lean-1.5*Math.cos(2*f-0.4)-2.5*J;
  addTo(P,'body','r',[bx,by,bz]);
  const ty=-14*Math.cos(f-0.2),tz=-4*Math.sin(f-0.2),tx=-4-1.5*Math.cos(2*f-1.0)-2*J;
  addTo(P,'torso','r',[tx,ty,tz]);
  addTo(P,'head','r',[-(bx+tx)*0.85-3+1.5*Math.cos(2*f-1.6)+3.5*J+n1(t),-(by+ty)*0.8,-(bz+tz)*0.6]);
  // tool arm: heavy, pumps a little against the free arm, jolts on impact
  addTo(P,'right_arm','r',[10-12*Math.cos(f-0.35)+3*Math.cos(2*f-1.2)+2.5*R+4*J,-(by+ty)*0.3,0]);
  addTo(P,'right_forearm','r',[8+3*Math.cos(2*f-1.8)+2*R+5*J,0,0]);
  // free arm pumps hard opposite the left leg, elbow bent ~90, driving forward-up and back
  addTo(P,'left_arm','r',[-4+O.arm*Math.cos(f-0.35),0,0]);
  addTo(P,'left_forearm','r',[62+22*Math.cos(f-0.9),0,0]);
  const fr=foot('r',t),fl=foot('l',t);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}
