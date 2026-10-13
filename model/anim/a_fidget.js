// IDLE FIDGET: neck crack both sides, heave the cannon up for a look, shoulder roll. Upper-body overlay for idle variety.
const {track,spring,addTo}=K;
const T=3.0;
const head=track([[0,[0,0,0]],[0.3,[-4,4,14]],[0.42,[-4,4,19],'in'],[0.5,[-4,4,17],'out'],[0.8,[-4,-4,-14]],[0.92,[-4,-4,-19],'in'],[1.0,[-4,-4,-17],'out'],[1.25,[-6,-14,0]],[1.95,[-8,-18,2]],[2.3,[2,0,0]],[2.6,[-1,0,0]],[3.0,[0,0,0]]]);
const pop=t=>[0.42,0.92].reduce((s,ti)=>s+(t>=ti?Math.exp(-(t-ti)/0.03):0),0);
const popS=spring(pop,{f:9,z:0.3,T});
const rArm=track([[0,[0,0,0]],[1.0,[0,0,0]],[1.3,[16,8,-6]],[1.55,[18,12,-6]],[1.8,[16,4,-6]],[2.1,[0,0,0]],[2.35,[-4,0,3]],[2.6,[2,0,-1]],[3.0,[0,0,0]]]);
const rFore=track([[0,[0,0,0]],[1.0,[0,0,0]],[1.3,[22,0,0]],[1.55,[24,-14,0]],[1.8,[22,10,0]],[2.1,[0,0,0]],[3.0,[0,0,0]]]);
const torso=track([[0,[0,0,0]],[0.3,[0,0,-2]],[0.8,[0,0,2]],[1.25,[-2,-6,0]],[1.95,[-2,-8,0]],[2.3,[1,0,0]],[3.0,[0,0,0]]]);
const pad=track([[0,0],[2.1,0],[2.3,1],[2.5,-0.3],[2.75,0],[3.0,0]]);
const lArm=track([[0,[0,0,0]],[1.2,[0,0,0]],[1.5,[6,0,4]],[1.95,[6,0,4]],[2.3,[0,0,0]],[3.0,[0,0,0]]]);
return {name:'animation.marine.idle_fidget',length:T,loop:'once',fps:30,sample(t){
  const P={},p=popS(t),pd=pad(t);
  addTo(P,'head','r',L_add(head(t),[0,0,3*p*(t<0.7?1:-1)]));
  addTo(P,'torso','r',torso(t));
  addTo(P,'right_arm','r',L_add(rArm(t),[0,0,0]));addTo(P,'right_forearm','r',rFore(t));
  addTo(P,'right_pad','p',[0,0.9*pd,0]);addTo(P,'right_pad','r',[0,0,5*pd]);
  addTo(P,'left_arm','r',lArm(t));
  return P;}};
function L_add(a,b){return [a[0]+b[0],a[1]+b[1],a[2]+b[2]];}
