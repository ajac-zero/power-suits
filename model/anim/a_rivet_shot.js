// RIVET_SHOT (power armor): one rivet. Upper-body overlay, replaces plasma_shot (same arm/torso recoil, from a_attack.js).
// 0.5 s, shorter than the 0.6 s cooldown: GeckoLib won't restart a triggered animation that is still playing.
// The rivet leaves at t=0. Gun parts: the piston slams toward the muzzle and eases back; the ammo drum turns 90 deg in
// stepped keys and snaps back to 0 at the end (it's square, so the snap is invisible, and GeckoLib doesn't slowly
// unwind it after the animation). Spec frame: the tool arm is right_* here (kit.json mirrors it onto the live left arm).
const {spring,addTo,vnoise,track}=K;
const T=0.5;
const g=u=>u<0?0:u<0.02?u/0.02:Math.exp(-(u-0.02)/0.05);
const kick=t=>g(t);
const kS=spring(kick,{f:6,z:0.45,T}), kM=spring(kick,{f:3.5,z:0.55,T}), kP=spring(kick,{f:9,z:0.25,T});
const brace=track([[0,0],[0.04,1,'out'],[0.22,1],[0.46,0],[0.5,0]]);
const env=t=>Math.exp(-t/0.08);
const shx=vnoise(13,38,1),shz=vnoise(17,41,1);
const fade=t=>t<0.42?1:Math.max(0,1-(t-0.42)/0.08);
const piston=track([[0,0],[0.025,1,'out'],[0.08,1],[0.3,0,'io'],[0.5,0]]);   // 1 = slammed forward
return {name:'animation.marine.rivet_shot',length:T,loop:'once',fps:50,keep:[0,0.025,0.08],tol:{r:0.15,p:0.02},
 steps(){
  const turn=[0,0.06,0.09,0.12,0.15,0.18].map((t,i)=>({time:t,values:[0,[0,0,18,45,72,90][i],0]}));
  return [{bone:'rivet_drum',channel:'rotation',keys:[...turn,{time:0.46,values:[0,0,0]}]}];},
 sample(t){
  const P={},f=fade(t),k=kick(t)*f,s=kS(t)*f,m=kM(t)*f,p=kP(t)*f,b=brace(t),e=env(t)*f;
  addTo(P,'torso','r',[3.2*m-1.2*b,-2.2*m-1.5*b,1.0*m]);
  addTo(P,'body','r',[1.0*m,-0.7*m,0]);
  addTo(P,'head','r',[-1.0*m+2.0*p-0.8*b,1.6*m+1.2*b,0.6*shz(t)*e]);
  addTo(P,'right_arm','r',[4.5*s+2.5*b,0.9*shz(t)*e,-1.2*b]);
  addTo(P,'right_forearm','r',[12*k+3*s+1.5*b,0,0]);
  addTo(P,'right_forearm','p',[0,0.45*k,2.0*k]);
  addTo(P,'rivet_gun','r',[1.5*shx(t)*e,0,1.8*shz(t)*e]);
  addTo(P,'rivet_piston','p',[0,-2*piston(t),0]);
  addTo(P,'right_pad','p',[0,0.8*(p-k*0.4),0]);
  addTo(P,'right_pad','r',[0,0,4*(p-k*0.3)]);
  addTo(P,'left_pad','p',[0,0.4*p,0]);
  addTo(P,'left_arm','r',[3*b+1.2*s,0,-1.5*m]);
  addTo(P,'left_forearm','r',[6*b+1.5*m,0,0]);
  return P;}};
