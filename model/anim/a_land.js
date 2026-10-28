// LAND (power suit): heavy impact. Feet planted in stance, hips drop and spring back, torso pitches,
// cannon and pads lag, settles to the rest stance (all offsets 0) so idle/walk blend in cleanly.
const {TAU,track,spring,addTo,plant,flatPivot,footR,REST,vnoise}=K;
const T=0.6;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),R:footR(REST[s].yaw)};
const imp=t=>t<0.03?t/0.03:Math.exp(-(t-0.03)/0.06);
const drop=track([[0,0],[0.08,-4.0,'out'],[0.2,-3.2],[0.45,-0.6],[0.6,0]]);
const tor=spring(imp,{f:3.2,z:0.45,T}), arm=spring(imp,{f:4.5,z:0.3,T}), pad=spring(imp,{f:8,z:0.22,T});
const env=t=>Math.exp(-t/0.12), sh1=vnoise(61,28,1), sh2=vnoise(62,31,1);
const fade=t=>t<0.5?1:Math.max(0,1-(t-0.5)/0.1);    // everything exactly 0 at the end
return {name:'animation.marine.land',length:T,loop:'once',fps:80,tol:{r:0.05,p:0.008},
 target(t,s){return STANCE[s].pv;},
 sample(t){
  const P={},k=fade(t),A=tor(t)*k,B=arm(t)*k,C=(pad(t)-imp(t))*k,s=env(t)*k;
  addTo(P,'marine','p',[0,drop(t),0.4*A]);
  addTo(P,'body','r',[-5*A+0.6*s*sh1(t),0,0]);
  addTo(P,'torso','r',[-9*A,0.6*s*sh2(t),0]);
  addTo(P,'head','r',[10*A,0,0]);
  addTo(P,'right_arm','r',[-10*B,0,-3*B]);addTo(P,'right_forearm','r',[-8*B,0,0]);
  addTo(P,'left_arm','r',[-6*B,0,-10*B]);addTo(P,'left_forearm','r',[16*B,0,0]);
  addTo(P,'right_pad','p',[0,-1.2*C,0]);addTo(P,'right_pad','r',[0,0,-4*C]);
  addTo(P,'left_pad','p',[0,-1.2*C,0]);addTo(P,'left_pad','r',[0,0,4*C]);
  for(const s of ['r','l'])plant(P,s,STANCE[s].pv,STANCE[s].R);
  return P;}};
