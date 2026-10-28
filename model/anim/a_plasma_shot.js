// PLASMA_SHOT (power suit): one heavy plasma shot. Upper-body overlay (layers over idle/walk/run), from a_attack.js.
// 0.5 s, shorter than the 0.6 s cooldown: GeckoLib won't restart a triggered animation that is still playing.
// Muzzle flash at t=0 (the bolt spawns the same tick). Ends at exactly zero offset.
const {spring,addTo,vnoise,track}=K;
const T=0.5;
const g=u=>u<0?0:u<0.02?u/0.02:Math.exp(-(u-0.02)/0.05);
const kick=t=>g(t);
const kS=spring(kick,{f:6,z:0.45,T}), kM=spring(kick,{f:3.5,z:0.55,T}), kP=spring(kick,{f:9,z:0.25,T});
const brace=track([[0,0],[0.04,1,'out'],[0.22,1],[0.46,0],[0.5,0]]);
const env=t=>Math.exp(-t/0.08);
const shx=vnoise(13,38,1),shz=vnoise(17,41,1);
const fade=t=>t<0.42?1:Math.max(0,1-(t-0.42)/0.08);
return {name:'animation.marine.plasma_shot',length:T,loop:'once',fps:50,keep:[0],tol:{r:0.15,p:0.02},
 steps(){
  return [{bone:'muzzle_flash',channel:'scale',keys:[{time:0,values:[2.3,1.8,2.3]},{time:0.03,values:[1.6,2.9,1.6]},{time:0.06,values:[1.1,1.6,1.1]},{time:0.1,values:[1,1,1]}]},
   {bone:'muzzle_flash',channel:'position',keys:[{time:0,values:[0,-5,0]},{time:0.03,values:[0,-6.5,0]},{time:0.06,values:[0,-4,0]},{time:0.1,values:[0,0,0]}]},
   {bone:'muzzle_flash',channel:'rotation',keys:[{time:0,values:[0,25,0]},{time:0.1,values:[0,0,0]}]}];},
 sample(t){
  const P={},f=fade(t),k=kick(t)*f,s=kS(t)*f,m=kM(t)*f,p=kP(t)*f,b=brace(t),e=env(t)*f;
  addTo(P,'torso','r',[3.2*m-1.2*b,-2.2*m-1.5*b,1.0*m]);
  addTo(P,'body','r',[1.0*m,-0.7*m,0]);
  addTo(P,'head','r',[-1.0*m+2.0*p-0.8*b,1.6*m+1.2*b,0.6*shz(t)*e]);
  addTo(P,'right_arm','r',[4.5*s+2.5*b,0.9*shz(t)*e,-1.2*b]);
  addTo(P,'right_forearm','r',[12*k+3*s+1.5*b,0,0]);
  addTo(P,'right_forearm','p',[0,0.45*k,2.0*k]);
  addTo(P,'cannon','r',[1.5*shx(t)*e,0,1.8*shz(t)*e]);
  addTo(P,'right_pad','p',[0,0.8*(p-k*0.4),0]);
  addTo(P,'right_pad','r',[0,0,4*(p-k*0.3)]);
  addTo(P,'left_pad','p',[0,0.4*p,0]);
  addTo(P,'left_arm','r',[3*b+1.2*s,0,-1.5*m]);
  addTo(P,'left_forearm','r',[6*b+1.5*m,0,0]);
  return P;}};
