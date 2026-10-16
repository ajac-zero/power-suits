// WALK v6 (2026-10-15, legs v10: solid shin, front ankle hinge): a slow, heavy stride. A 1.0 s cycle instead of v5's 0.6 with steps half as long
// again (stance 22 px instead of 15), 2.22 blocks/s: longer double support (duty 0.62), the hips dropping between
// the feet and rising over the planted leg (reachHip, knee >= KMIN), a weight-accepting knee dip after each heel strike,
// a footfall jolt through the torso, more weight roll over the planted leg, a slower and wider free-arm swing.
// Tool arm stabilized. v5 (0.6 s, 2.6 blocks/s, shorter quicker steps) in a_walk_v5.js; v4 jog in a_walk_v4.js.
const {TAU,spring,addTo,pnoise,plant,gaitFoot,reachHip,REST}=K;
const O=Object.assign({T:1.0,beta:0.62,S:22,zc:-2,H:2.6,KMIN:23,strike:20,toe:20,hang:-8,sway:1.5,rout:0.26,w:0.03,kneeOut:0,roll:4.5,
  dip:0.4,jolt:1.0,arm:18,yaw:5,
  // legs v10 (2026-10-15): the shin is a solid block down to the sole and the boot hinges at its front bottom edge. Toes
  // down at most 4 deg (the shin's back would sink into the sole), toes up to 40 (the shin's back lifts off the heel).
  // KMIN 23: with the hinge 5 px in front of the shin's axis the leg is stretched along hip-knee-hinge with the knee
  // bent ~23, so the planted knee bends 38-60 (12-20 with the old heel hinge); zc -2 places the stance for the hinge.
  ankle:[-4,40]},K.P||{});
const {T,beta,S,zc}=O;
if(O.ankle)K.setAnkleLimit(O.ankle);   // [toes down, toes up] against the shin; kit.json's range otherwise
const Gr={x:REST.r.pivot[0],zc,S,beta,H:O.H,yaw:REST.r.yaw,strike:O.strike,toe:O.toe,hang:O.hang,rin:0.14,rout:O.rout,vm:0.45};
const Gl={...Gr,x:REST.l.pivot[0],yaw:REST.l.yaw};
const phR=t=>t/T, phL=t=>t/T+0.5;
const xr=Math.sign(K.rig.bones.right_leg.origin[0]);       // sway toward the stance leg
const swayX=t=>O.sway*xr*Math.sin(TAU*t/T);
const foot=(s,t)=>{const ph=s==='r'?phR(t):phL(t);return gaitFoot(s,ph,s==='r'?Gr:Gl);};
const inStance=(s,t)=>{const ph=(((s==='r'?phR(t):phL(t))%1)+1)%1;return ph<beta;};
// walking: hips highest over the planted leg, lowest in double support (the curve comes from the leg reach)
const yHip=O.fixedHip!=null?(t=>O.fixedHip+O.bob*Math.cos(2*TAU*(t/T-beta/2))):reachHip(T,['r','l'].map(s=>t=>({s,pivot:foot(s,t).pivot,contact:inStance(s,t)})),{kmin:O.KMIN,w:O.w,sway:swayX});
// weight acceptance: the planted knee gives a little after each heel strike (an impulse shaped dip, both feet)
const hit=u=>{u=((u%1)+1)%1;return u<0.06?Math.sin(Math.PI/2*u/0.06):Math.exp(-(u-0.06)/0.09);};
const dipY=t=>-O.dip*(hit(phR(t))+hit(phL(t)));
const yS=spring(yHip,{f:2.6,z:0.4,T,loop:true});
const rel=t=>yS(t)-yHip(t);
const n1=pnoise(41,T,0.3,[1,2,3]);
// footfall impulse (both heel strikes) through springs: torso pitch forward, the head nods after it, the free arm swings on
const imp=t=>hit(phR(t)-0.0)+hit(phL(t)-0.0);
const sT=spring(imp,{f:4,z:0.35,T,loop:true}),sH=spring(imp,{f:3,z:0.3,T,loop:true}),sA=spring(imp,{f:2.5,z:0.25,T,loop:true});
const jolt=t=>({t:sT(t),hs:sH(t),a:sA(t)-imp(t)});
// --- tool arm stabilizer (same block in a_walk.js / a_run.js; spec names: right_* = the live tool arm) -----------
// Holds the tool steady relative to the suit (it sits in the cockpit view): IK the upper arm so the elbow stays
// near its cycle-average position, and fix the forearm's world orientation.
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
return {name:'animation.marine.walk',length:T,loop:'loop',fps:30,tol:{r:0.05,p:0.01},speed:S/(beta*T)/16,
 target(t,s){return foot(s,t).pivot;},
 sample(t){return SAMPLE(t);}};
function RAW(t){
  const P={},f=TAU*t/T,R=rel(t);
  addTo(P,'marine','p',[swayX(t),yHip(t)+dipY(t),0]);
  // footfall jolt: each heel strike shakes the upper body (springs of different stiffness: torso, head, tool arm)
  const J=jolt(t);
  // a heavy walk: the weight rolls over onto each planted leg (sway + the waist leaning over it, lagging a little),
  // the hips turn with the legs, the shoulders counter-turn, little pitch
  const by=O.yaw*Math.cos(f),bz=-O.roll*xr*Math.sin(f-0.35),bx=-0.6*Math.cos(2*f);
  addTo(P,'body','r',[bx,by,bz]);
  const ty=-5*Math.cos(f-0.25),tz=1.2*xr*Math.sin(f-0.6),tx=-0.8*Math.cos(2*f-0.6);
  addTo(P,'torso','r',[tx-1.2*O.jolt*J.t,ty,tz]);   // -x: the top pitches forward
  addTo(P,'head','r',[-(bx+tx)*0.8+O.jolt*(0.8*J.t-1.0*J.hs)+0.4*Math.cos(2*f-1.2)+n1(t),-(by+ty)*0.85,-(bz+tz)*0.7]);
  // tool arm steady, a little lag on the bob
  addTo(P,'right_arm','r',[2+1.5*Math.cos(2*f-1.0)+1.2*R,-(by+ty)*0.45,0]);
  addTo(P,'right_forearm','r',[1+1.8*Math.cos(2*f-1.6)+1.0*R,0,0]);
  addTo(P,'right_pad','p',[0,0,0]);
  // free arm swings opposite the left leg
  addTo(P,'left_arm','r',[O.arm*Math.cos(f-0.35)+O.jolt*1.5*J.a,0,0]);
  addTo(P,'left_forearm','r',[12+0.55*O.arm*Math.cos(f-0.9)+O.jolt*2*J.a,0,0]);
  // extra knee-out twist while loaded (0: the rest pose's 7 deg hip twist already points knees and toes out; the foot
  // must stay square to the shin, whose slot it sits in)
  for(const s of ['r','l']){const out=Math.sign(K.rig.bones[s==='r'?'right_leg':'left_leg'].origin[0]);
    const load=0.6+0.4*Math.cos(f-(s==='r'?0.9:0.9+Math.PI));addTo(P,s==='r'?'right_leg':'left_leg','r',[0,-out*O.kneeOut*load,0]);}
  const fr=foot('r',t),fl=foot('l',t);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}
