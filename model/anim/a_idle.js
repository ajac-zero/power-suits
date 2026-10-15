// IDLE: combat-ready stance, breathing, weight shift, look-around with saccades, cannon readjust
const {TAU,track,pnoise,spring,addTo,plant,flatPivot,footR,REST,smooth}=K;
const T=4.0;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),R:footR(REST[s].yaw)};
const w=t=>Math.sin(TAU*t/T);                 // weight shift (+ = onto right leg)
const b=t=>Math.sin(TAU*2*t/T-0.6);          // breathing, 2 breaths per loop
const bl=spring(b,{f:1.1,z:0.6,T,loop:true}); // lagged breath for arms/pads
const wl=spring(w,{f:0.9,z:0.7,T,loop:true});
// gaze: hold, saccade left, hold, glance right, back
const yawT=track([[0,0],[1.0,0],[1.28,17],[2.05,17],[2.3,-10],[3.05,-10],[3.4,0],[4,0]]);
const pitchT=track([[0,0],[1.0,0],[1.28,-4],[2.05,-3],[2.3,3],[3.05,2],[3.4,0],[4,0]]);
const yawS=spring(yawT,{f:3.2,z:0.5,T,loop:true});
const pitS=spring(pitchT,{f:3.2,z:0.55,T,loop:true});
const torsoYaw=spring(t=>yawT(t)*0.32,{f:1.2,z:0.8,T,loop:true});
const n1=pnoise(1,T,0.5),n2=pnoise(2,T,0.6),n3=pnoise(3,T,0.4),n4=pnoise(4,T,0.7),n5=pnoise(5,T,0.5);
// cannon readjust (heft) around 3.45-3.95
const heft=track([[0,0],[3.4,0],[3.6,1,'out'],[3.95,0],[4,0]]);
const heftS=spring(heft,{f:2.4,z:0.35,T,loop:true});
return {name:'animation.marine.idle',length:T,loop:'loop',fps:20,sample(t){
  const P={};
  const W=w(t),B=b(t),BL=bl(t),WL=wl(t);
  addTo(P,'marine','p',[0.9*W,0.2+0.12*Math.cos(2*TAU*t/T)+0.08*B,0.15*Math.sin(TAU*t/T+1)]);
  addTo(P,'body','r',[0.4*B,0.6*n1(t),1.4*W]);
  const ty=torsoYaw(t);
  addTo(P,'torso','r',[1.1*B+0.3*n2(t),ty+0.5*n3(t),-2.1*WL]);
  addTo(P,'torso','p',[0,0.16*B,0]);
  addTo(P,'head','r',[pitS(t)-0.7*B+0.3*n4(t),yawS(t)-ty+0.4*n5(t),0.8*W]);
  // cannon arm: heavy, lags breath, readjust heft
  const H=heftS(t);
  addTo(P,'right_arm','r',[1.3*BL+3.5*H+0.3*n3(t),0.4*n1(t),-0.6*WL]);
  addTo(P,'right_forearm','r',[0.9*BL+4*H,-3*H,0]);
  addTo(P,'right_pad','r',[0,0,0.9*BL+2.5*H]);
  addTo(P,'right_pad','p',[0,0.18*BL+0.4*H,0]);
  // free arm: relaxed, swings from torso motion
  addTo(P,'left_arm','r',[-1.2*BL+1.2*n2(t),0,-0.8*BL+1.3*WL]);
  addTo(P,'left_forearm','r',[2.5*BL+1.5*n4(t),0,0.8*n5(t)]);
  addTo(P,'left_pad','r',[0,0,-0.9*BL]);
  addTo(P,'left_pad','p',[0,0.18*BL,0]);
  for(const s of ['r','l'])plant(P,s,STANCE[s].pv,STANCE[s].R);
  return P;}};
