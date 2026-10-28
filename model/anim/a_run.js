// RUN v8 (2026-10-15, legs v10, user: "pushing hard into the ground at every step"): a driving run, 0.7 s stride,
// foot planted 32% (22 px), 6.25 blocks/s: the suit sinks 2 px into each planted leg right after the footfall (sink,
// sinkAt) and drives back up through a push-off that throws the trailing leg out behind; hips otherwise as high as the
// legs reach (KMIN 30, cap), a 1.5 px rise in flight, swing targets eased into the leg's reach (swingK, soft), the knee
// kept on its bent solution (kneeFloor 22). v7 (0.9 s bounds, "hopping, frolicking") and v6 ("baby steps") rejected.
// RUN v6 (power suit, 2026-10-14): an energetic bounding sprint (the suit smashes glass at this pace). Forward lean,
// long flight, big vertical bounce, push-off with the leg fully extended behind, the heel kicks up behind and the knee
// drives forward-high before the foot reaches out and paws back into the next strike; free arm pumps hard, the torso
// counter-twists, every footfall lands with a jolt. 6.25 blocks/s. Knee up to 101 deg in the heel kick (the knee
// bends cleanly to ~100, model/art/legs_stomper_art.py); past the boot's ankle range (-30..40) the boot rolls.
// v5 (flat, low swing: "a shuffle") in a_run_v5.js; v4 (10 b/s, hips 4 px down, knees to 150) in a_run_v4.js.
const {TAU,spring,addTo,pnoise,plant,gaitFoot,footR,REST,smooth,clamp,lerp}=K;
// legs v10 (front ankle hinge, 2026-10-15): zc 3.5 -> -3, hips from the legs' reach (KMIN 30) + a 1 px bounce in flight
// (fixed hips were out of reach at push-off: the knee flipped 47 deg in one frame, the run's 'stutter'), a flatter and
// slower heel strike (strike 6, rin 0.3: the boot slapping flat about its heel dropped the front hinge 2 px and the
// knee straightened back mid-landing), a softened touchdown (land 0.2) and swing targets kept within reach
const O=Object.assign({T:0.7,beta:0.32,S:22.4,zc:0,hip:-0.3,bob:1.4,H:4,up:0.4,wide:0.5,kick:1,strike:6,toe:30,
  hang:-20,lean:-11,arm:14,armF:6,toolPump:true,armZ:-5,elbow:70,low:0.35,rin:0.3,paw:0.4,land:0.2,KMIN:30,fly:1.5,kneeFloor:22,cap:0.3,rw:0.02,
  swingK:42,soft:1.5,sink:2,sinkAt:0.25,waistLean:true},K.P||{});
const {T,beta,S,zc}=O;
// legs v10: stay on the bent knee solution (the front ankle hinge's straighter one flips with it near full stretch)
if(O.kneeFloor!=null)K.setKneeFloor(O.kneeFloor);
const Gr={x:REST.r.pivot[0],zc,S,beta,H:0,yaw:REST.r.yaw,strike:O.strike,toe:O.toe,hang:-10,rin:O.rin,rout:0.32,vm:0.5};
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
  const v0=vg*Tsw*O.kick, v1=vg*Tsw*O.paw;    // dz/du: still moving back at toe-off, pawing back at strike (paw 1 = ground speed)
  const z=h00*A.pivot[2]+h10*v0+h01*B.pivot[2]+h11*v1;
  const w=Math.pow(u,Math.log(0.5)/Math.log(O.up));   // skewed bump: highest at u = O.up
  // O.land: the last part of the lift eases to zero (a sin^0.5 bump hits the ground at full vertical speed: a knee kink
  // at every footfall, the run's 'stutter')
  const lift=O.H*Math.pow(Math.sin(Math.PI*w),O.wide)*(O.land>0?smooth(Math.min(1,(1-u)/O.land)):1);
  const base=lerp(A.pivot[1],B.pivot[1],smooth(u));
  // pitch: toe-off -> toes hanging (heel kicked up) -> toes up for the strike
  const pitch=u<O.up?lerp(-O.toe,O.hang,smooth(u/O.up)):lerp(O.hang,O.strike,smooth((u-O.up)/(1-O.up)));
  return {pivot:[G.x,base+lift,z],R:footR(G.yaw,pitch)};}
// running: hips lowest at mid-stance (loading), highest in flight
const yHipBob=t=>{const p=t/T;return O.hip-O.bob*Math.cos(2*TAU*(p-beta*O.low));};   // lowest a little before mid-stance: rising into the push-off
// O.KMIN set: hips as high as the planted foot allows with the knee bent at least KMIN (reachHip, as the walk), plus
// a bounce in each flight phase (O.fly px). Legs v10's front ankle hinge put the fixed-height hips out of the legs' reach
// at push-off (the knee flipped 47 deg in one frame: the run's 'stutter').
const inStanceR=(s,t)=>{const ph=(((s==='r'?phR(t):phL(t))%1)+1)%1;return ph<beta;};
const flightBump=t=>{let v=0;for(const s of ['r','l']){const ph=(((s==='r'?phR(t):phL(t))%1)+1)%1;if(ph>=beta&&ph<0.5){v=Math.max(v,Math.sin(Math.PI*(ph-beta)/(0.5-beta)));}}return v;};
const yReach=O.KMIN!=null?K.reachHip(T,['r','l'].map(s=>t=>({s,pivot:foot(s,t).pivot,contact:inStanceR(s,t)})),{kmin:O.KMIN,w:O.rw??0.04,cap:O.cap??1e9}):null;
// O.sink: the suit sinks into each planted leg (lowest at O.sinkAt of the stance, then driving back up through the
// push-off): the knee loads up under the weight and extends through the shove
const stanceSink=t=>{let v=0;for(const s of ['r','l']){const ph=(((s==='r'?phR(t):phL(t))%1)+1)%1;if(ph<beta){const u=ph/beta,a=O.sinkAt??0.4;
  v=Math.max(v,u<a?Math.sin(Math.PI/2*u/a):Math.cos(Math.PI/2*(u-a)/(1-a)));}}return v;};
const sinkS=spring(t=>stanceSink(t),{f:3.5,z:0.5,T,loop:true});
const yHip=O.KMIN!=null?(t=>yReach(t)+O.fly*flightBump(t)-(O.sink||0)*sinkS(t)):yHipBob;
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
// v8 (user): the tool arm pumps like the hand arm (same short swing, opposite phase) instead of being held steady:
// shots leave from the pilot's eye and the cockpit draws its own viewmodel, so its third-person pose never aims
const SAMPLE=O.toolPump?RAW:stabilize(RAW,T,{keepPos:0.6,keepRot:0.5});
return {name:'animation.marine.run',length:T,loop:'loop',fps:60,tol:{r:0.04,p:0.01},
 target(t,s){return foot(s,t).pivot;},
 sample(t){return SAMPLE(t);}};
// a swinging foot reaching further than the leg can with the knee bent KMIN is pulled toward the hip: at full stretch
// (legs v10: knee ~23) the solver jitters between its two knee solutions, a kink right before every footfall
function reach(s,f,P){if(O.KMIN==null)return f;const hp=K.L.add(K.rig.bones[s==='r'?'right_leg':'left_leg'].origin,P.marine.p);
  // swingK: a leg never stretches past that knee bend. Soft limit: distances past L-w ease toward L (a hard clamp held
  // the leg at its limit and left a corner going in and out)
  const d=K.L.sub(f.pivot,hp),L=K.legLen(s,O.swingK??O.KMIN),n=K.L.len(d),w=O.soft??1.5;
  if(n<=L-w)return f;const n2=L-w*Math.exp(-(n-(L-w))/w);
  return {pivot:K.L.add(hp,K.L.sc(d,n2/n)),R:f.R};}
function RAW(t){
  const P={},f=TAU*t/T,R=rel(t),J=jolt(t);
  addTo(P,'marine','p',[0.45*Math.sin(f),yHip(t),0]);
  // lean into the run from the waist; the hips twist with the legs, the shoulders against them
  const by=8*Math.cos(f),bz=3*Math.sin(f),bx=O.lean-1.5*Math.cos(2*f-0.4)-2.5*J;
  const ty=-14*Math.cos(f-0.2),tz=-4*Math.sin(f-0.2),tx=-4-1.5*Math.cos(2*f-1.0)-2*J;
  // waistLean (2026-10-18, space-marine chest): the forward lean pivots at the waist (torso, y 27.125) instead of the
  // hip axis (body, y 22.625): leaning from 4.5 px lower swung the abdomen ~2 px out in front of the static belt
  // (the hip hoods). Same angles; the body keeps only its twist and roll.
  addTo(P,'body','r',[O.waistLean?0:bx,by,bz]);
  addTo(P,'torso','r',[O.waistLean?tx+bx:tx,ty,tz]);
  addTo(P,'head','r',[-(bx+tx)*0.85-3+1.5*Math.cos(2*f-1.6)+3.5*J+n1(t),-(by+ty)*0.8,-(bz+tz)*0.6]);
  // tool arm: heavy, pumps a little against the free arm, jolts on impact
  if(O.toolPump){addTo(P,'right_arm','r',[10-O.arm*Math.cos(f-0.35)+2.5*R+4*J,-(by+ty)*0.3,0]);
    addTo(P,'right_forearm','r',[8-O.armF*Math.cos(f-0.9)+2*R+5*J,0,0]);}
  else{addTo(P,'right_arm','r',[10-12*Math.cos(f-0.35)+3*Math.cos(2*f-1.2)+2.5*R+4*J,-(by+ty)*0.3,0]);
    addTo(P,'right_forearm','r',[8+3*Math.cos(2*f-1.8)+2*R+5*J,0,0]);}
  // free arm pumps opposite the left leg, elbow bent ~90 (v8: a short, heavy pump, arm 14 / armF 6: the old 44 / 22
  // swung the fist 17 px against the tool arm's steady 2.6, user: 'jarring'); held 5 deg out from the body and the elbow
  // at 70 so the driving thigh passes under the forearm (it came up 1 px into it)
  addTo(P,'left_arm','r',[(O.armX??-4)+O.arm*Math.cos(f-0.35),0,O.armZ??0]);
  addTo(P,'left_forearm','r',[(O.elbow??62)+O.armF*Math.cos(f-0.9),0,0]);
  const fr=reach('r',foot('r',t),P),fl=reach('l',foot('l',t),P);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}
