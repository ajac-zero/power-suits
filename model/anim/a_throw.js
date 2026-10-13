// THROW: overhand grenade. Upper body only (layers over idle/walk). Grenade flies a ballistic arc after release.
const {track,spring,addTo,setTo,orient,vnoise}=K;const L=K.L,rig=K.rig;
const T=1.56, TR=0.72;
// total arm pitch path (rest 10) -> anim = total-10 ; forearm total (rest 14)
const arm=track([[0,[0,0,0]],[0.18,[50,4,-4]],[0.3,[56,4,-6]],[0.55,[190,10,-34]],[0.72,[150,0,-24],'in'],[0.86,[45,-4,-8],'out'],[1.15,[10,0,-2]],[1.56,[0,0,0]]]);
const fore=track([[0,0],[0.18,88],[0.3,92],[0.55,100],[0.66,80,'in'],[0.72,10,'in'],[0.86,20,'out'],[1.15,8],[1.56,0]]);
const torso=track([[0,[0,0,0]],[0.18,[-3,-8,0]],[0.3,[-2,-6,0]],[0.55,[7,28,4]],[0.72,[-8,-12,-2],'in'],[0.86,[-12,-24,-3],'out'],[1.15,[-4,-8,-1]],[1.56,[0,0,0]]]);
const body=track([[0,[0,0,0]],[0.18,[-1,-2,0]],[0.55,[3,10,1]],[0.72,[-3,-4,0],'in'],[0.86,[-5,-8,-1],'out'],[1.2,[-1,-2,0]],[1.56,[0,0,0]]]);
const look=track([[0,[0,0]],[0.12,[-16,-6]],[0.32,[-14,-4]],[0.5,[6,0]],[0.62,[5,0]],[1.56,[0,0]]]);
const cannon=track([[0,[0,0,0]],[0.55,[-6,0,6]],[0.8,[-8,0,10]],[1.2,[-2,0,2]],[1.56,[0,0,0]]]);
const relKick=t=>t>=TR?Math.exp(-(t-TR)/0.06)*Math.min(1,(t-TR)/0.02):0;
const kS=spring(relKick,{f:5,z:0.3,T});
const jit=vnoise(9,30,1);
const basePose=t=>{const P={};
  const tr=torso(t),bd=body(t),lk=look(t);
  addTo(P,'body','r',bd);addTo(P,'torso','r',tr);
  addTo(P,'head','r',[lk[0]-(tr[0]+bd[0])*0.7+1.5*kS(t),lk[1]-(tr[1]+bd[1])*0.85,-(tr[2]+bd[2])*0.6]);
  const a=arm(t);addTo(P,'left_arm','r',a);
  addTo(P,'left_forearm','r',[fore(t)+(t>0.18&&t<0.32?1.5*jit(t):0),0,0]);
  addTo(P,'left_pad','r',[0,0,-6*kS(t)+(a[0]>90?8*Math.min(1,(a[0]-90)/60):0)]);
  addTo(P,'left_pad','p',[-0.8*Math.min(1,a[0]/120),0.6*Math.min(1,a[0]/120),0]);
  const c=cannon(t);addTo(P,'right_arm','r',c);addTo(P,'right_pad','p',[0,0.3*kS(t),0]);
  return P;};
// grenade: held, dropped into fingers at .15, released at TR
const GO=rig.bones.grenade.origin;
const heldOff=t=>t<0.15?[0,-3*K.smoother(t/0.15),0]:[0,-3,0];
const gWorld=t=>{const P=basePose(t);P.grenade={p:heldOff(t)};const W=L.fk(rig,P);return L.xf(W.grenade,GO);};
const p0=gWorld(TR),pm=gWorld(TR-0.02);const v0=[-10,130,-270];
const G=-160;
return {name:'animation.marine.throw',length:T,loop:'once',fps:30,keep:[0.15,TR,1.05,1.06],
 steps(){return [{bone:'grenade',channel:'scale',keys:[{time:0,values:[1,1,1]},{time:1.05,values:[1,1,1]},{time:1.06,values:[0,0,0]},{time:1.55,values:[0,0,0]},{time:1.56,values:[1,1,1]}]}];},
 info:{p0,v0},
 sample(t){
  const P=basePose(t);
  if(t<=TR){P.grenade={p:heldOff(t),r:[0,0,0]};}
  else{const u=Math.min(t,1.2)-TR;const target=L.add(L.add(p0,L.sc(v0,u)),[0,0.5*G*u*u,0]);
    const W=L.fk(rig,P);const loc=L.sub(L.mv(L.tr(W.left_forearm.R),L.sub(target,W.left_forearm.t)),GO);
    let off=loc;
    if(t>1.2){const w=K.smoother((t-1.2)/0.3);off=loc.map(v=>v*(1-w));}
    P.grenade={p:off};
    const spin=-1100*u;orient(P,'grenade',L.rot([spin,0,0]));
    if(t>1.2){const w=K.smoother((t-1.2)/0.3);P.grenade.r=P.grenade.r.map(v=>v*(1-w));}
  }
  return P;}};
