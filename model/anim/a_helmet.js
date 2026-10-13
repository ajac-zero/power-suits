// HELMET OFF / ON: left hand reaches up, unlocks, lifts the helmet off and stows it at the hip (ON = reverse + seat click)
const {track,spring,addTo,ik,orient,setTo}=K;const L=K.L,rig=K.rig;
const MODE=K.MODE||'off';
const T0=1.3, TG=0.36, TH=1.0; // length, grab time, hide time
const HAND=[-12,16.6,0];
// hand targets in torso space
const R0=[-16.8,18.7,-6.8];
const handT=track([[0,null],[0.3,[-9.5,44,-4]],[TG,[-9.5,43.6,-4]],[0.44,[-9.5,46,-4],'out'],[0.66,[-12,55,-3]],[0.84,[-19,42,-3]],[TH,[-17,27,-4]],[1.3,null]].map(k=>k[1]?k:[k[0],R0]));
const reachW=track([[0,0],[0.3,1],[TH,1],[1.3,0]]);
const head=track([[0,[0,0,0]],[0.3,[-3,-6,-4]],[TG,[-5,-6,-4]],[0.42,[-9,-4,-2]],[0.62,[4,0,0]],[0.86,[2,8,0]],[0.98,[-1,-9,0]],[1.1,[2,5,0]],[1.3,[0,0,0]]]);
const torso=track([[0,[0,0,0]],[0.3,[-1,-4,-3]],[0.66,[1,-2,-2]],[TH,[0,4,1]],[1.3,[0,0,0]]]);
const unlock=t=>t>=TG?Math.exp(-(t-TG)/0.05)*Math.min(1,(t-TG)/0.015):0;
const uS=spring(unlock,{f:8,z:0.3,T:T0});
let warm=null,G=null;
function pose(t){
  const P={};addTo(P,'torso','r',torso(t));addTo(P,'head','r',head(t));
  addTo(P,'right_arm','r',[-2*reachW(t),0,0]);
  // arm: rest pose blended toward IK solution
  const w=reachW(t);
  if(w>0){const Q=JSON.parse(JSON.stringify(P));if(warm){setTo(Q,'left_arm','r',warm[0]);setTo(Q,'left_forearm','r',warm[1]);}
    else{setTo(Q,'left_arm','r',[60,0,-60]);setTo(Q,'left_forearm','r',[60,0,0]);}
    const W=L.fk(rig,Q);const tg=L.xf(W.torso,handT(t));
    ik(Q,[['left_arm',0],['left_arm',1],['left_arm',2],['left_forearm',0]],'left_forearm',HAND,tg,{iters:60,damp:0.02});
    warm=[Q.left_arm.r.slice(),Q.left_forearm.r.slice()];
    const s=K.smoother(w);P.left_arm={r:Q.left_arm.r.map(v=>v*s)};P.left_forearm={r:Q.left_forearm.r.map(v=>v*s)};}
  else{P.left_arm={r:[0,0,0]};P.left_forearm={r:[0,0,0]};}
  addTo(P,'left_pad','r',[0,0,-0.15*P.left_arm.r[2]]);
  // helmet: unlock jolt, then rigidly follows the hand
  P.helmet={p:[0,0.4*uS(t),0],r:[0,0,0]};
  if(t>TG){
    const W=L.fk(rig,P);
    if(!G){const Pg=pose.cache;const Wg=L.fk(rig,Pg);const A=Wg.left_forearm,Hm=Wg.helmet;
      G={R:L.mm(L.tr(A.R),Hm.R),t:L.mv(L.tr(A.R),L.sub(Hm.t,A.t))};}
    const A=W.left_forearm;const Rd=L.mm(A.R,G.R),td=L.add(L.mv(A.R,G.t),A.t);
    const O=rig.bones.helmet.origin;const pivW=L.add(L.mv(Rd,O),td);
    const Hd=W.head;const loc=L.mv(L.tr(Hd.R),L.sub(pivW,Hd.t));
    orient(P,'helmet',Rd);P.helmet.p=L.sub(loc,O);
  }
  if(t<=TG)pose.cache=JSON.parse(JSON.stringify(P));
  return P;}
// precompute sequentially (IK warm start + grab capture)
const FPS=30,N=Math.ceil(T0*FPS);const table=[];for(let i=0;i<=N;i++)table.push(pose(Math.min(i/FPS,T0)));
const at=t=>table[Math.round(Math.min(t,T0)*FPS)];
const hideKeys=MODE==='off'?[{time:0,values:[1,1,1]},{time:TH,values:[0,0,0]}]:[{time:0,values:[0,0,0]},{time:+(T0-TH).toFixed(3),values:[1,1,1]}];
return {name:MODE==='off'?'animation.marine.helmetoff':'animation.marine.helmeton',length:T0,loop:MODE==='off'?'hold':'once',fps:FPS,
 steps(){return [{bone:'helmet',channel:'scale',keys:hideKeys}];},
 sample(t){
  const fade=u=>{const w=1-K.smoother((u-TH-0.04)/0.2);return w;};
  if(MODE==='off'){const P=JSON.parse(JSON.stringify(at(t)));const w=fade(t);P.helmet.p=P.helmet.p.map(v=>v*w);P.helmet.r=P.helmet.r.map(v=>v*w);return P;}
  // ON: reverse time, helmet only visible from T0-TH; final seat click
  const P=JSON.parse(JSON.stringify(at(T0-t)));const w=fade(T0-t);P.helmet.p=P.helmet.p.map(v=>v*w);P.helmet.r=P.helmet.r.map(v=>v*w);
  const c=t>1.0?Math.exp(-(t-1.0)/0.06)*Math.min(1,(t-1.0)/0.02):0;
  addTo(P,'head','r',[4*c,0,0]);P.helmet.p=L.add(P.helmet.p,[0,-0.3*c,0]);
  return P;}};
