// FALL (power armor): airborne loop. Legs pedal slowly, free arm balances, cannon drifts. t=0 == end of jump.
// --- shared airborne pose (same block in a_jump.js and a_fall.js) ---
const {TAU,track,spring,addTo,plant,flatPivot,footR,rolled,REST,lerp,smoother,pnoise}=K;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),yaw:REST[s].yaw};
const AIR={marine:[0,0.5,0],body:[-4,0,0],torso:[-3,0,0],head:[5,0,0],
  right_arm:[12,0,-6],right_forearm:[10,0,0],left_arm:[8,0,-32],left_forearm:[12,0,0]};
// right foot tucked forward-up, left trailing; toes hanging down
const AIRF={r:{d:[0,6.5,-4],pitch:-18},l:{d:[0,5,3.5],pitch:-30}};
const airFoot=(s,dz=0,dy=0)=>{const b=STANCE[s].pv,a=AIRF[s];return {pv:[b[0],b[1]+a.d[1]+dy,b[2]+a.d[2]+dz],pitch:a.pitch};};
const setFoot=(P,s,pv,pitch)=>plant(P,s,pv,footR(STANCE[s].yaw,pitch));
const lerp3=(a,b,w)=>a.map((x,i)=>x+(b[i]-x)*w);
// ---
const T=1.2;
const S1=t=>Math.sin(TAU*t/T), S2=t=>Math.sin(2*TAU*t/T);
const n1=pnoise(51,T,0.8,[1,2,3]),n2=pnoise(52,T,0.8,[1,2,3]);
const fallFeet=(t,s)=>{const a=S1(t),b=S2(t);return s==='r'?airFoot('r',-1.5*a,0.8*b):airFoot('l',1.5*a,-0.8*b);};
return {name:'animation.marine.fall',length:T,loop:'loop',fps:20,tol:{r:0.08,p:0.012},
 target(t,s){return fallFeet(t,s).pv;},
 sample(t){
  const P={},a=S1(t),b=S2(t);
  addTo(P,'marine','p',[0.3*a,AIR.marine[1]+0.3*b,0]);
  addTo(P,'body','r',[AIR.body[0]+1.2*b,1.5*a,1.2*a]);
  addTo(P,'torso','r',[AIR.torso[0]+1.0*b,-2.2*a+0.5*n1(t),-1.6*a]);
  addTo(P,'head','r',[AIR.head[0]-1.5*b,0.7*a+n2(t),1.2*a]);
  addTo(P,'right_arm','r',[AIR.right_arm[0]+2.5*b,0,AIR.right_arm[2]-1.5*a]);
  addTo(P,'right_forearm','r',[AIR.right_forearm[0]+2*b,0,0]);
  addTo(P,'left_arm','r',[AIR.left_arm[0]+5*a,0,AIR.left_arm[2]-5*b]);
  addTo(P,'left_forearm','r',[AIR.left_forearm[0]+6*a,0,0]);
  addTo(P,'right_pad','p',[0,0,0]);addTo(P,'right_pad','r',[0,0,1.2*b]);
  addTo(P,'left_pad','p',[0,0,0]);addTo(P,'left_pad','r',[0,0,-1.2*b]);
  const r=airFoot('r',-1.5*a,0.8*b),l=airFoot('l',1.5*a,-0.8*b);
  setFoot(P,'r',r.pv,r.pitch+4*a);setFoot(P,'l',l.pv,l.pitch-4*a);
  return P;}};
