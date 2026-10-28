// HAND (power suit): the left hand holding / using the pilot's selected item. Left-arm overlays only
// (left_arm, left_forearm, left_pad), on their own controller in the game, which overrides the gait's left arm.
//   hold_item (loop 2 s):  carry pose, forearm forward at waist height (like holding a torch), gentle sway
//   use_item  (once 0.3 s): quick reach forward and up (place / use) and back to the hold pose
// The arm stays below the cockpit view: in first person the game draws the item vanilla-style instead.
// Arm IK puts the grip (left_hand bone) on a target given in the torso's rest frame, so it rides the torso.
const {track,addTo}=K;const L=K.L,rig=K.rig;
const MODE=K.MODE||'hold_item';
const GRIP=rig.bones.left_hand.origin;                 // grip point, in model rest coordinates
const HOLD=[-10,26,-18];                               // grip target in the torso's rest frame, px
// (An eat_item pose at the chin was tried and dropped: the forearm covers the visor. The pilot eats inside.)
const W0=L.fk(rig,{}), PAD0=L.euler(W0.left_pad.R);
// Every solve starts from the same seed (upper arm down, elbow flexed forward), so the same target always gives
// the same pose: both animations meet exactly at the hold pose, and the elbow never flips backwards.
const SEED={arm:[15,0,10],fore:[80,0,0]};
function reach(target,{padKeep=0.6}={}){
  const P={};addTo(P,'left_arm','r',SEED.arm);addTo(P,'left_forearm','r',SEED.fore);
  K.ik(P,[['left_arm',0],['left_arm',1],['left_arm',2],['left_forearm',0]],'left_forearm',GRIP,target,{iters:80,damp:0.01});
  // keep the shoulder pad closer to level so it doesn't swing up into the cockpit view
  const cur=L.euler(L.fk(rig,P).left_pad.R);
  K.orient(P,'left_pad',L.rot(cur.map((v,i)=>v+K.wrapDeg(PAD0[i]-v)*padKeep)));
  return P;}
if(MODE==='use_item'){const T=0.3;
  const k=track([[0,0],[0.11,1,'out'],[0.3,0,'io']]);
  return {name:'animation.marine.use_item',length:T,loop:'once',fps:60,tol:{r:0.1,p:0.01},
    sample(t){return reach(L.add(HOLD,L.sc([0.5,2.5,-5],k(t))));}};}
const T=2.0;
return {name:'animation.marine.hold_item',length:T,loop:'loop',fps:20,tol:{r:0.08,p:0.01},
  sample(t){const f=2*Math.PI*t/T;return reach(L.add(HOLD,[0.25*Math.sin(f),0.35*Math.sin(2*f+0.7),0.2*Math.cos(f)]));}};
