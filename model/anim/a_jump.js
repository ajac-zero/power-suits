// JUMP (power suit): crouch-launch, toe push-off, legs tuck into the airborne pose (ends exactly on fall t=0).
// Played the moment the suit leaves the ground; chain: jump (once) -> fall (loop).
// --- shared airborne pose (same block in a_jump.js and a_fall.js) ---
const {TAU,track,spring,addTo,plant,flatPivot,footR,rolled,REST,lerp,smoother,pnoise}=K;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),yaw:REST[s].yaw};
const AIR={marine:[0,0.5,0],body:[-4,0,0],torso:[-3,0,0],head:[5,0,0],
  right_arm:[12,0,-6],right_forearm:[10,0,0],left_arm:[8,0,-32],left_forearm:[12,0,0]};
// right foot tucked forward-up, left trailing; toes hanging down
const AIRF={r:{d:[0,4.2,-4],pitch:-12},l:{d:[0,3.4,3.5],pitch:-18}};
const airFoot=(s,dz=0,dy=0)=>{const b=STANCE[s].pv,a=AIRF[s];return {pv:[b[0],b[1]+a.d[1]+dy,b[2]+a.d[2]+dz],pitch:a.pitch};};
const setFoot=(P,s,pv,pitch)=>plant(P,s,pv,footR(STANCE[s].yaw,pitch));
const lerp3=(a,b,w)=>a.map((x,i)=>x+(b[i]-x)*w);
// ---
const T=0.36;
const W=track([[0,0],[0.12,0],[0.36,1,'io']]);                    // launch -> air blend for upper body
const root=track([[0,[0,-3,0]],[0.1,[0,1.2,-0.5],'out'],[0.36,AIR.marine,'io']]);
const lean=track([[0,[-10,0,0]],[0.1,[-6,0,0],'out'],[0.36,[0,0,0]]]);
const lArmK=track([[0,[-18,0,-6]],[0.1,[26,0,-24],'out'],[0.36,AIR.left_arm,'io']]);
const lForeK=track([[0,[10,0,0]],[0.1,[22,0,0],'out'],[0.36,AIR.left_forearm,'io']]);
const rArmK=track([[0,[-6,0,-4]],[0.1,[6,0,-4],'out'],[0.36,AIR.right_arm,'io']]);
const rForeK=track([[0,[-4,0,0]],[0.12,[16,0,0],'out'],[0.36,AIR.right_forearm,'io']]);
const imp=t=>t<0.02?t/0.02:Math.exp(-(t-0.02)/0.05);
const padS=spring(imp,{f:6,z:0.3,T});
// feet: planted (heel rolls up onto the toe by 0.1), then lift into the tuck
const toe=track([[0,0],[0.1,-30,'in'],[0.36,0]]);
const lift=track([[0,0],[0.1,0],[0.36,1,'io']]);
function feet(t,s){const a=airFoot(s),L=lift(t);
  const g=rolled(s,STANCE[s].pv,STANCE[s].yaw,toe(t));              // pivot that keeps the toe edge on the ground
  return {pv:lerp3(g.pivot,a.pv,L),pitch:toe(t)*(1-L)+a.pitch*L};}
return {name:'animation.marine.jump',length:T,loop:'once',fps:40,tol:{r:0.08,p:0.012},
 target(t,s){return feet(t,s).pv;},
 sample(t){
  const P={},w=W(t),R=padS(t)-imp(t);
  addTo(P,'marine','p',root(t));
  addTo(P,'body','r',lerp3([-4,0,0],AIR.body,w).map((v,i)=>v+(i?0:lean(t)[0]*0.4)));
  addTo(P,'torso','r',lerp3([0,0,0],AIR.torso,w).map((v,i)=>v+(i?0:lean(t)[0]*0.6)));
  addTo(P,'head','r',lerp3([8,0,0],AIR.head,w));
  addTo(P,'right_arm','r',rArmK(t));addTo(P,'right_forearm','r',rForeK(t));
  addTo(P,'left_arm','r',lArmK(t));addTo(P,'left_forearm','r',lForeK(t));
  addTo(P,'right_pad','p',[0,-0.8*R,0]);addTo(P,'right_pad','r',[0,0,-3*R]);
  addTo(P,'left_pad','p',[0,-0.8*R,0]);addTo(P,'left_pad','r',[0,0,3*R]);
  for(const s of ['r','l']){const f=feet(t,s);setFoot(P,s,f.pv,f.pitch);}
  return P;}};
