// WALK upper-body layer synced with existing IK legs (right heel strike 0.26, left 0.78)
const {TAU,spring,addTo,pnoise}=K;
const T=1.04;
const ph=t=>TAU*(t-0.26)/T;                          // 0 at right heel strike
const bob=t=>-Math.abs(Math.sin(TAU*t/T))*1.2;        // matches marine.position molang
const bobS=spring(bob,{f:2.6,z:0.35,T,loop:true});    // lagging mass (pads, cannon)
const rel=t=>bobS(t)-bob(t);                          // + when body dropped faster than the part
const n1=pnoise(11,T,0.4,[1,2,3]);
return {name:'animation.marine.walk',length:T,loop:'loop',fps:25,sample(t){
  const P={},f=ph(t),R=rel(t);
  const by=4*Math.cos(f), bz=2.5*Math.sin(f), bx=-2.5-0.8*Math.cos(2*f);
  addTo(P,'body','r',[bx,by,bz]);
  const ty=-6*Math.cos(f-0.25), tz=-4*Math.sin(f-0.15), tx=-1.5-1.2*Math.cos(2*f-0.6);
  addTo(P,'torso','r',[tx,ty,tz]);
  addTo(P,'head','r',[-(bx+2.5+tx+1.5)*0.8+0.6*Math.cos(2*f-1.2)+n1(t),-(by+ty)*0.85,-(bz+tz)*0.7]);
  // cannon arm keeps aim steady, heavy lag
  addTo(P,'right_arm','r',[2*Math.cos(2*f-1.0)+1.2*R,-(by+ty)*0.45,0]);
  addTo(P,'right_forearm','r',[2.4*Math.cos(2*f-1.6)+1.0*R,0,0]);
  addTo(P,'right_pad','p',[0,0.55*R,0]);
  addTo(P,'right_pad','r',[0,0,2.2*R]);
  // free arm swings opposite the left leg, forearm drags behind
  addTo(P,'left_arm','r',[12*Math.cos(f-0.35),0,-1.5+1.5*Math.sin(f)]);
  addTo(P,'left_forearm','r',[9+7*Math.cos(f-0.85),0,0]);
  addTo(P,'left_pad','p',[0,0.55*R,0]);
  addTo(P,'left_pad','r',[0,0,-2.2*R]);
  return P;}};
