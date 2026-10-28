// WALK v3 (power suit): heavy power-march, no double support, stride 17, 0.6 s. ~3.5 blocks/s.
// v2 (2.35 blocks/s) in a_walk_v2.js; v1 = old IK walk + a_walk_upper.js.
const {TAU,spring,addTo,pnoise,plant,gaitFoot,REST}=K;
const T=0.6, beta=0.5, S=17, zc=-1.8;
const Gr={x:6.0,zc,S,beta,H:4.2,yaw:REST.r.yaw*0.7,strike:12,toe:22,hang:-14,rin:0.14,rout:0.24,vm:0.45};
const Gl={...Gr,x:-6.1,yaw:REST.l.yaw*0.7};
const phR=t=>t/T, phL=t=>t/T+0.5;
// walking: hips highest at mid-stance, lowest at double support
const yHip=t=>{const p=t/T;return -3.4+1.0*Math.cos(2*TAU*(p-beta/2));};
const yS=spring(yHip,{f:2.8,z:0.35,T,loop:true});
const rel=t=>yS(t)-yHip(t);
const n1=pnoise(41,T,0.4,[1,2,3]);
return {name:'animation.marine.walk',length:T,loop:'loop',fps:30,tol:{r:0.05,p:0.01},
 target(t,s){return gaitFoot(s,s==='r'?phR(t):phL(t),s==='r'?Gr:Gl).pivot;},
 sample(t){
  const P={},f=TAU*t/T,R=rel(t);
  addTo(P,'marine','p',[0.5*Math.sin(f),yHip(t),0]);
  const by=5*Math.cos(f),bz=3*Math.sin(f),bx=-5-1*Math.cos(2*f);
  addTo(P,'body','r',[bx,by,bz]);
  const ty=-7*Math.cos(f-0.25),tz=-4*Math.sin(f-0.15),tx=-2-1.2*Math.cos(2*f-0.6);
  addTo(P,'torso','r',[tx,ty,tz]);
  addTo(P,'head','r',[-(bx+tx)*0.8-1+0.6*Math.cos(2*f-1.2)+n1(t),-(by+ty)*0.85,-(bz+tz)*0.7]);
  // cannon arm steady, heavy lag on the bob
  addTo(P,'right_arm','r',[3+2.4*Math.cos(2*f-1.0)+1.6*R,-(by+ty)*0.45,-2]);
  addTo(P,'right_forearm','r',[2+2.8*Math.cos(2*f-1.6)+1.3*R,0,0]);
  addTo(P,'right_pad','p',[0,0.6*R,0]);addTo(P,'right_pad','r',[0,0,2.6*R]);
  // free arm swings opposite the left leg
  addTo(P,'left_arm','r',[18*Math.cos(f-0.35),0,-2+2*Math.sin(f)]);
  addTo(P,'left_forearm','r',[14+10*Math.cos(f-0.9),0,0]);
  addTo(P,'left_pad','p',[0,0.6*R,0]);addTo(P,'left_pad','r',[0,0,-2.6*R]);
  const fr=gaitFoot('r',phR(t),Gr),fl=gaitFoot('l',phL(t),Gl);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}};
