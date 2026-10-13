// ELECTRIFIED: rigid convulsions with high-frequency jitter, arched spine, arms locked out, feet planted.
const {pnoise,addTo,plant,flatPivot,footR,REST}=K;
const T=1.2;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),R:footR(REST[s].yaw)};
const HF=[9,11,14,17,21],MF=[1,2,3,4];
let seed=100;const J=(a,h=HF)=>pnoise(seed++,T,a,h);
const j={mx:J(0.35),my:J(0.5),mz:J(0.3),bx:J(3),by:J(4),bz:J(3),tx:J(4),ty:J(5),tz:J(3),hx:J(9),hy:J(10),hz:J(6),
  rax:J(8),raz:J(7),rfx:J(9),lax:J(9),laz:J(8),lfx:J(14),pr:J(0.6),pl:J(0.6),prr:J(6),plr:J(6),
  wave:J(1,MF),wave2:J(1,MF),cn:J(5)};
return {name:'animation.marine.electrified',length:T,loop:'loop',fps:40,tol:{r:0.4,p:0.03},sample(t){
  const P={},w=j.wave(t),w2=j.wave2(t);
  addTo(P,'marine','p',[j.mx(t),-1.6+0.7*w+j.my(t),j.mz(t)]);
  addTo(P,'body','r',[6+3*w+j.bx(t),j.by(t),j.bz(t)]);
  addTo(P,'torso','r',[9+4*w2+j.tx(t),j.ty(t),j.tz(t)]);
  addTo(P,'head','r',[14+6*w+j.hx(t),j.hy(t),j.hz(t)]);
  addTo(P,'right_arm','r',[-22+6*w2+j.rax(t),0,26+5*w+j.raz(t)]);
  addTo(P,'right_forearm','r',[-18+j.rfx(t),0,0]);
  addTo(P,'cannon','r',[j.cn(t),0,j.cn(t+0.3)]);
  addTo(P,'left_arm','r',[-18+6*w+j.lax(t),0,-42+5*w2+j.laz(t)]);
  addTo(P,'left_forearm','r',[-10+j.lfx(t),0,0]);
  addTo(P,'right_pad','p',[2+j.pr(t),0.6+Math.abs(j.pr(t+0.1)),0]);addTo(P,'right_pad','r',[0,0,6+j.prr(t)]);
  addTo(P,'left_pad','p',[-2.5+j.pl(t),0.6+Math.abs(j.pl(t+0.1)),0]);addTo(P,'left_pad','r',[0,0,-6+j.plr(t)]);
  for(const s of ['r','l'])plant(P,s,STANCE[s].pv,STANCE[s].R);
  return P;}};
