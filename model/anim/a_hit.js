// HIT: frontal impact flinch with spring recovery. Upper body overlay, 0.55s.
const {spring,addTo,vnoise}=K;
const T=0.55;
const I=t=>Math.exp(-t/0.04)*Math.min(1,t/0.015);
const s1=spring(I,{f:4,z:0.4,T}),s2=spring(I,{f:7,z:0.28,T}),s3=spring(I,{f:2.6,z:0.55,T});
const n=vnoise(77,30,1);
const fade=t=>t>T-0.08?Math.max(0,(T-t)/0.08):1;
return {name:'animation.marine.hit',length:T,loop:'once',fps:40,sample(t){
  const P={},a=s1(t)*fade(t),b=s2(t)*fade(t),c=s3(t)*fade(t);
  addTo(P,'body','r',[2.5*c,-2*c,1*c]);
  addTo(P,'torso','r',[9*a,-6*a,3*a]);
  addTo(P,'head','r',[15*b-3*a,-9*b,4*b+2*n(t)*b]);
  addTo(P,'right_arm','r',[-9*a,0,6*b]);addTo(P,'right_forearm','r',[-6*b,0,0]);
  addTo(P,'left_arm','r',[-14*b,0,-12*a]);addTo(P,'left_forearm','r',[12*a,0,0]);
  addTo(P,'right_pad','p',[0,0.8*b,0]);addTo(P,'right_pad','r',[0,0,6*b]);
  addTo(P,'left_pad','p',[0,0.8*b,0]);addTo(P,'left_pad','r',[0,0,-6*b]);
  return P;}};
