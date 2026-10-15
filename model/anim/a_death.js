// DEATH: hit, stagger, knees buckle onto the ground, topple face-first, bounce, settle, last twitch. Ground-contact solved.
const {track,spring,addTo,plant,flatPivot,footR,REST,smoother,bodyLow}=K;const L=K.L;
const T=2.68;
const STANCE={};for(const s of ['r','l'])STANCE[s]={pv:flatPivot(s,REST[s].pivot[0],REST[s].pivot[2],REST[s].yaw),R:footR(REST[s].yaw)};
const k=(keys)=>track(keys);
const mRot=k([[0,[0,0,0]],[0.1,[5,0,0],'out'],[0.35,[6,0,2]],[0.85,[-6,0,3],'in'],[1.05,[-12,2,4]],[1.48,[-84,4,7],'in'],[1.62,[-77,4,6],'out'],[1.85,[-86,4,7]],[2.68,[-86,4,7]]]);
const mPos=k([[0,[0,0,0]],[0.1,[0,0,1.2],'out'],[0.35,[0,-1.2,2.2]],[0.85,[0,-9,1.5],'in'],[1.48,[0,-9,-1],'in'],[2.68,[0,-9,-1]]]);
const lift=k([[0,0],[1.48,0],[1.6,1.6,'out'],[1.8,0,'in'],[1.92,0.3,'out'],[2.02,0,'in'],[2.68,0]]);
const contact=k([[0,0],[0.62,0],[0.8,1],[2.68,1]]);
const ikW=k([[0,1],[0.3,1],[0.65,0],[2.68,0]]);
const torso=k([[0,[0,0,0]],[0.1,[16,6,0],'out'],[0.35,[8,10,4]],[0.85,[-10,6,0]],[1.0,[-17,6,2],'out'],[1.2,[-12,6,2]],[1.48,[4,8,3]],[1.62,[-3,8,3]],[1.85,[2,10,3]],[2.68,[2,10,3]]]);
const head=k([[0,[0,0,0]],[0.1,[24,10,0],'out'],[0.35,[10,14,0]],[0.85,[-12,16,-4]],[1.0,[-17,20,-6],'out'],[1.25,[-11,20,-4]],[1.48,[18,24,0]],[1.62,[8,28,4]],[1.85,[20,32,6]],[2.68,[20,32,6]]]);
const body=k([[0,[0,0,0]],[0.1,[6,0,0],'out'],[0.35,[4,4,0]],[0.85,[-6,4,0]],[1.48,[2,4,2]],[2.68,[2,4,2]]]);
const rArm=k([[0,[0,0,0]],[0.1,[-24,0,14],'out'],[0.35,[-30,0,10]],[0.85,[-46,0,-6]],[1.25,[-40,0,-8]],[1.4,[10,0,6],'in'],[1.55,[-30,0,26],'out'],[1.85,[-52,0,34]],[2.68,[-52,0,34]]]);
const rFore=k([[0,0],[0.1,-16],[0.35,-12],[0.85,-26],[1.48,-10],[1.85,-14],[2.68,-14]]);
const lArm=k([[0,[0,0,0]],[0.1,[-32,0,-28],'out'],[0.35,[-20,0,-18]],[0.85,[-6,0,6]],[1.25,[-4,0,4]],[1.4,[30,0,-10],'in'],[1.55,[0,0,-30],'out'],[1.85,[-14,0,-38]],[2.68,[-14,0,-38]]]);
const lFore=k([[0,0],[0.1,-12],[0.35,-4],[0.85,10],[1.48,-6],[1.85,4],[2.3,4],[2.38,22,'out'],[2.5,2],[2.58,8],[2.68,6]]);
// FK leg keys after the IK stance hands over (kneel -> legs extended). Authored as offsets on the old crouched rest
// pose (before the 2026-10-14 upright stance); OLD_REST turns them back into the same totals on any rest pose.
// The kneel is 10 deg shallower than first authored (knee 85, the knee cutout bends cleanly to ~80).
const OLD_REST={right_leg:[32,0,8],right_shin:[-42,0,0],right_foot:[10,-6,-8],left_leg:[12,0,-8],left_shin:[-24,0,0],left_foot:[12,6,8]};
const FIX={};for(const n in OLD_REST)FIX[n]=OLD_REST[n].map((v,i)=>v-K.rig.bones[n].rest[i]);
const rLeg=k([[0,[0,0,0]],[0.35,[12,0,0]],[0.85,[-27,0,2],'in'],[1.25,[-27,0,2]],[1.48,[-30,0,4]],[1.85,[-28,0,6]],[2.68,[-28,0,6]]]);
const rShin=k([[0,[0,0,0]],[0.35,[-16,0,0]],[0.85,[-43,0,0],'in'],[1.25,[-43,0,0]],[1.48,[22,0,0]],[1.62,[14,0,0]],[1.85,[24,0,0]],[2.68,[24,0,0]]]);
const rFoot=k([[0,[0,0,0]],[0.85,[-28,0,0]],[1.48,[-40,0,0]],[2.68,[-42,0,0]]]);
const lLeg=k([[0,[0,0,0]],[0.35,[16,0,0]],[0.85,[-7,0,-2],'in'],[1.25,[-7,0,-2]],[1.48,[-12,0,-6]],[1.85,[-10,0,-10]],[2.68,[-10,0,-10]]]);
const lShin=k([[0,[0,0,0]],[0.35,[-20,0,0]],[0.85,[-61,0,0],'in'],[1.25,[-61,0,0]],[1.48,[10,0,0]],[1.62,[2,0,0]],[1.85,[14,0,0]],[2.68,[14,0,0]]]);
const lFoot=k([[0,[0,0,0]],[0.85,[-26,0,0]],[1.48,[-36,0,0]],[2.68,[-38,0,0]]]);
const imp=t=>[0.05,0.85,1.48].reduce((s,ti,i)=>s+(t>=ti?[1,0.8,1][i]*Math.exp(-(t-ti)/0.05):0),0);
const padS=spring(imp,{f:7,z:0.25,T});
const O=K.rig.bones.marine.origin;let C=null;
function sampleRaw(t){
  const P={};const mr=mRot(t);let mp=mPos(t);
  if(C){const R=L.rot(mr);const d=L.sub(C,O);mp=L.add(mp,L.sub(d,L.mv(R,d)));}
  addTo(P,'marine','r',mr);addTo(P,'marine','p',mp);
  addTo(P,'body','r',body(t));addTo(P,'torso','r',torso(t));addTo(P,'head','r',head(t));
  addTo(P,'right_arm','r',rArm(t));addTo(P,'right_forearm','r',[rFore(t),0,0]);
  addTo(P,'left_arm','r',lArm(t));addTo(P,'left_forearm','r',[lFore(t),0,0]);
  const ps=padS(t);addTo(P,'right_pad','p',[0.4*ps,0.6*ps,0]);addTo(P,'left_pad','p',[-0.4*ps,0.6*ps,0]);
  addTo(P,'right_pad','r',[0,0,4*ps]);addTo(P,'left_pad','r',[0,0,-4*ps]);
  // legs: blend planted IK stance into FK collapse
  const w=ikW(t);const fkL={right_leg:rLeg(t),right_shin:rShin(t),right_foot:rFoot(t),left_leg:lLeg(t),left_shin:lShin(t),left_foot:lFoot(t)};
  for(const n in fkL)fkL[n]=fkL[n].map((v,i)=>v+FIX[n][i]);
  for(const n of ['right_foot','left_foot']){const r0=K.rig.bones[n].rest[0];fkL[n][0]=Math.max(-30,Math.min(40,fkL[n][0]+r0))-r0;}   // the boots' ankle range (kit.json)
  if(w>0){const Q=JSON.parse(JSON.stringify(P));Q.marine.p=[mp[0],mp[1]+0.2,mp[2]];
    for(const s of ['r','l'])plant(Q,s,STANCE[s].pv,STANCE[s].R);
    for(const n in fkL)P[n]={r:fkL[n].map((v,i)=>v*(1-w)+Q[n].r[i]*w)};
    P.marine.p=[mp[0],mp[1]+0.2*w,mp[2]];}
  else for(const n in fkL)P[n]={r:fkL[n]};
  // ground contact
  const low=bodyLow(P);const c=contact(t);
  const dy=c*(-low)+(1-c)*Math.max(0,-low);
  P.marine.p=[P.marine.p[0],P.marine.p[1]+dy+lift(t),P.marine.p[2]];
  return P;}
{const P=sampleRaw(0.85);const W=L.fk(K.rig,P);const a=L.xf(W.right_shin,K.rig.bones.right_shin.origin),b=L.xf(W.left_shin,K.rig.bones.left_shin.origin);
 const m=L.sc(L.add(a,b),0.5);C=[0,m[1]-P.marine.p[1],m[2]-P.marine.p[2]];}
return {name:'animation.marine.death',length:T,loop:'hold',fps:30,tol:{r:0.2,p:0.02},info:()=>C,sample:sampleRaw};
