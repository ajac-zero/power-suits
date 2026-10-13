// SNEAKWALK: deep crouch, rolling heel-toe creep, cannon shouldered, support hand on cannon, scanning head
const {TAU,spring,addTo,pnoise,plant,gaitFoot,REST,ik,setTo}=K;
const T=1.0, beta=0.64, S=8.5, zc=-1.4;
const Gr={x:6.6,zc,S,beta,H:2.0,yaw:REST.r.yaw,strike:7,toe:16,hang:-8,rin:0.18,rout:0.25,vm:0.35};
const Gl={...Gr,x:-6.8,yaw:REST.l.yaw};
const phR=t=>t/T, phL=t=>t/T+0.5;
const yHip=t=>-4.3-0.35*Math.cos(2*TAU*(t/T-beta/2));
const yS=spring(yHip,{f:2.2,z:0.4,T,loop:true});const rel=t=>yS(t)-yHip(t);
const n1=pnoise(31,T,0.6,[1,2,3]),n2=pnoise(32,T,0.5,[1,2,3]);
const HAND=[-12,16.6,0];               // left hand centre (left_forearm space)
const GRIP=K.GRIP||[5.2,21.5,-1.5];             // support point beside cannon body (right_forearm space)
let warm=null;
const support=(P,on)=>{ if(warm){setTo(P,'left_arm','r',warm[0]);setTo(P,'left_forearm','r',warm[1]);}
  const W=K.L.fk(K.rig,P);const tg=K.L.xf(W.right_forearm,GRIP);
  const e=ik(P,[['left_arm',0],['left_arm',1],['left_arm',2],['left_forearm',0]],'left_forearm',HAND,tg,{iters:40,damp:0.01});
  warm=[P.left_arm.r.slice(),P.left_forearm.r.slice()];return e;};
return {name:'animation.marine.sneakwalk',length:T,loop:'loop',fps:25,tol:{r:0.05,p:0.01},
 target(t,s){return gaitFoot(s,s==='r'?phR(t):phL(t),s==='r'?Gr:Gl).pivot;},
 sample(t){
  const P={},f=TAU*t/T,R=rel(t);
  addTo(P,'marine','p',[0.25*Math.sin(f),yHip(t),0]);
  const by=2.5*Math.cos(f),bz=1.8*Math.sin(f),bx=-13;
  addTo(P,'body','r',[bx,by,bz]);
  const scan=6*Math.sin(f)+n1(t);
  const ty=-3.5*Math.cos(f-0.2)+scan*0.5,tz=-2.4*Math.sin(f-0.2),tx=-5+0.6*Math.cos(2*f-0.8);
  addTo(P,'torso','r',[tx,ty,tz]);
  addTo(P,'head','r',[15-(tx+5)*0.8+n2(t),-(by+ty)*0.9+scan,-(bz+tz)*0.6]);
  // shouldered cannon held steady against the bob
  addTo(P,'right_arm','r',[16+0.8*R,-(by+ty)*0.6+scan*0.3+6,-6]);
  addTo(P,'right_forearm','r',[16+0.6*R,0,0]);
  addTo(P,'right_pad','p',[0,0.4*R,0]);addTo(P,'right_pad','r',[0,0,1.6*R]);
  addTo(P,'left_pad','p',[0,0.4*R,0]);addTo(P,'left_pad','r',[0,0,-1.6*R]);
  addTo(P,'left_arm','r',[26+3*Math.cos(f-0.4)+1.5*R,4,-6]);
  addTo(P,'left_forearm','r',[40+4*Math.cos(f-1.0)+2*R,0,0]);
  P.__ik=0;
  const fr=gaitFoot('r',phR(t),Gr),fl=gaitFoot('l',phL(t),Gl);
  plant(P,'r',fr.pivot,fr.R);plant(P,'l',fl.pivot,fl.R);
  return P;}};
