// POWER (power suit): out of fuel / refuelled. One spec, three animations (K.MODE):
//   shutdown    (hold, 1.6 s): power cut jolt, servos give way, sags into a slump, settles, last twitch
//   powered_off (hold, 0.05 s): the final slump pose (for suits that were already off when loaded)
//   startup     (once, 1.4 s): servo kick, pushes up out of the slump, small overshoot, ends at rest (0)
// Feet stay planted (IK). Full body (owns legs/root). The game blocks movement for the 1.4 s startup.
const {track,addTo,plant,flatPivot,footR,REST,vnoise}=K;
const MODE=K.MODE||'shutdown';
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),R:footR(REST[s].yaw)};
// The slump: hips down, leaning forward, head hanging to one side, arms dangling, pads dropped.
const SLUMP={marine_p:[0,-4.0,0.9],body:[-7,0,1.5],torso:[-13,4,-2.5],head:[-9,10,7],
  right_arm:[15,0,4],right_forearm:[-7,0,0],left_arm:[17,0,-5],left_forearm:[-12,0,0],
  right_pad:[0,0,-5],left_pad:[0,0,5],right_pad_p:[0,-0.6,0],left_pad_p:[0,-0.6,0]};
function pose(k,extra){
  const P={},e=extra||{};
  const g=(n)=>SLUMP[n].map((v,i)=>v*k+((e[n]||[0,0,0])[i]));
  addTo(P,'marine','p',g('marine_p'));
  for(const n of ['body','torso','head','right_arm','right_forearm','left_arm','left_forearm','right_pad','left_pad'])addTo(P,n,'r',g(n));
  addTo(P,'right_pad','p',g('right_pad_p'));addTo(P,'left_pad','p',g('left_pad_p'));
  for(const s of ['r','l'])plant(P,s,STANCE[s].pv,STANCE[s].R);
  return P;}
if(MODE==='powered_off')return {name:'animation.marine.powered_off',length:0.05,loop:'hold',fps:20,
  target(t,s){return STANCE[s].pv;},sample(t){return pose(1);}};
const n1=vnoise(71,26,1),n2=vnoise(72,31,1),n3=vnoise(73,23,1);
if(MODE==='startup'){
  const T=1.4;
  // 1 = slumped, 0 = rest. Kick at 0.1, push up 0.25-0.9, overshoot (negative = past rest), settle.
  const k=track([[0,1],[0.1,0.93,'out'],[0.25,0.95],[0.95,0,'io'],[1.4,0]]);
  const over=track([[0,0],[0.8,0],[1.0,3.5,'out'],[1.4,0]]); // torso rocks back past upright, settles (hips can't rise above rest)
  const sh=track([[0,0],[0.08,1,'out'],[0.3,0.5],[0.9,0.25],[1.25,0],[1.4,0]]); // servo shake envelope
  const head=track([[0,0],[0.1,6,'out'],[0.3,-2],[0.7,4],[1.05,-1.5],[1.4,0]]);  // head comes up early
  return {name:'animation.marine.startup',length:T,loop:'once',fps:40,tol:{r:0.08,p:0.01},
    target(t,s){return STANCE[s].pv;},
    sample(t){const s=sh(t);
      return pose(k(t),{head:[head(t)+1.2*s*n1(t)-0.6*over(t),0,0],torso:[over(t)+0.8*s*n2(t),0,0.6*s*n3(t)],
        right_forearm:[1.5*s*n3(t),0,0],left_forearm:[2*s*n1(t),0,0]});}};}
// shutdown
const T=1.6;
// Lights out at 0, a jolt (straightens a little), then the servos give way under the weight,
// bottom out at 0.95, bounce, a last droop of the head at 1.25, still from 1.45.
const jolt=track([[0,0],[0.07,4,'out'],[0.25,0]]); // torso/head snap back when the power cuts
const k=track([[0,0],[0.2,0.05,'out'],[0.8,0.9,'in'],[0.95,1.07,'out'],[1.12,0.97],[1.3,1.01],[1.6,1]]);
const sh=track([[0,0],[0.05,1,'out'],[0.3,0.3],[0.8,0.1],[1.0,0.35],[1.3,0],[1.6,0]]);
const droop=track([[0,0],[0.2,2],[1.18,2],[1.3,-1,'out'],[1.42,0.4],[1.6,0]]); // last head drop, ends on the slump
const fade=t=>t<1.45?1:Math.max(0,1-(t-1.45)/0.15); // noise exactly 0 at the end: the last frame equals powered_off
return {name:'animation.marine.shutdown',length:T,loop:'hold',fps:40,tol:{r:0.08,p:0.01},
  target(t,s){return STANCE[s].pv;},
  sample(t){const s=sh(t)*fade(t);
    return pose(k(t),{head:[droop(t)+1.5*s*n1(t)+jolt(t),0,0],torso:[jolt(t)+1.0*s*n2(t),0,0.8*s*n3(t)],
      right_forearm:[2*s*n3(t),0,0],left_forearm:[3*s*n1(t),0,0]});}};
