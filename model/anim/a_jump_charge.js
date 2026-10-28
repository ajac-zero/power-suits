// JUMP_CHARGE (power suit): wind-up crouch while the pilot holds jump. Ends exactly on jump t=0 (hold last frame).
const {track,addTo,plant,flatPivot,footR,REST,spring,pnoise}=K;
const T=0.35;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),R:footR(REST[s].yaw)};
// jump.js at t=0: root -3, lean -10 (body 40%, torso 60%), body base -4, head 8, arms cocked back
const END={marine:[0,-3,0],body:[-8,0,0],torso:[-6,0,0],head:[8,0,0],right_arm:[-6,0,-4],right_forearm:[-4,0,0],left_arm:[-18,0,-6],left_forearm:[10,0,0]};
const w=track([[0,0],[T,1,'io']]);
return {name:'animation.marine.jump_charge',length:T,loop:'hold',fps:40,tol:{r:0.06,p:0.01},
 target(t,s){return STANCE[s].pv;},
 sample(t){
  const P={},k=w(t);
  for(const [b,v] of Object.entries(END))addTo(P,b,b==='marine'?'p':'r',v.map(x=>x*k));
  addTo(P,'right_pad','p',[0,0,0]);addTo(P,'left_pad','p',[0,0,0]);
  for(const s of ['r','l'])plant(P,s,STANCE[s].pv,STANCE[s].R);
  return P;}};
