// STIMPACK: slam injector on chest, violent rush (arch + shudder + panting), snap into an aggressive ready stance. Upper body overlay.
const {track,spring,addTo,ik,setTo,vnoise,smoother}=K;const L=K.L,rig=K.rig;
const T=1.5, TI=0.16;
const HAND=[-12,16.6,0], R0=[-16.8,18.7,-6.8];
const handT=track([[0,R0],[0.12,[-5,33,-11],'in'],[0.24,[-5.5,33,-10.5]],[0.42,R0,'out'],[T,R0]]);
const reachW=track([[0,0],[0.1,1,'out'],[0.3,1],[0.48,0]]);
const hitI=t=>t>=TI?Math.exp(-(t-TI)/0.06)*Math.min(1,(t-TI)/0.02):0;
const hS=spring(hitI,{f:3.2,z:0.35,T}),hF=spring(hitI,{f:7,z:0.25,T});
const shake=t=>t<TI?0:Math.exp(-(t-TI)/0.28);
const nz=[1,2,3,4,5,6,7,8].map(i=>vnoise(40+i,22,1));
const pant=t=>t<0.3||t>1.2?0:Math.sin((t-0.3)*Math.PI*2/0.3)*Math.sin(Math.PI*(t-0.3)/0.9);
const ready=track([[0,0],[0.62,0],[0.82,1,'back'],[1.12,1],[1.5,0]]);
let warm=null;
return {name:'animation.marine.stim',length:T,loop:'once',fps:40,tol:{r:0.2,p:0.02},sample(t){
  const P={},h=hS(t),f=hF(t),s=shake(t),r=ready(t),pa=pant(t);
  addTo(P,'body','r',[3*h-3*r+nz[0](t)*1.5*s,nz[1](t)*2*s,0]);
  addTo(P,'torso','r',[13*h+1.5*pa-7*r+nz[2](t)*3*s,nz[3](t)*3*s,nz[4](t)*2*s]);
  addTo(P,'torso','p',[0,0.3*pa,0]);
  addTo(P,'head','r',[26*f+9*h-1.5*pa-6*r+nz[5](t)*7*s,nz[6](t)*8*s,nz[7](t)*4*s]);
  addTo(P,'right_arm','r',[-10*h+12*r+nz[1](t+0.2)*4*s,0,12*h+nz[2](t+0.3)*4*s]);
  addTo(P,'right_forearm','r',[-8*h+10*r,0,0]);
  addTo(P,'right_pad','p',[0.8*h,0.7*f+0.2*pa,0]);addTo(P,'right_pad','r',[0,0,8*f]);
  addTo(P,'left_pad','p',[-0.8*h,0.7*f+0.2*pa,0]);addTo(P,'left_pad','r',[0,0,-8*f]);
  // left hand: slam injector, then fist up into ready guard
  const w=reachW(t);
  let la=[-14*h*(1-w),0,-16*h*(1-w)],lf=[0,0,0];
  if(w>0){const Q=JSON.parse(JSON.stringify(P));setTo(Q,'left_arm','r',warm?warm[0]:[40,0,30]);setTo(Q,'left_forearm','r',warm?warm[1]:[70,0,0]);
    const W=L.fk(rig,Q);ik(Q,[['left_arm',0],['left_arm',1],['left_arm',2],['left_forearm',0]],'left_forearm',HAND,L.xf(W.torso,handT(t)),{iters:60,damp:0.02});
    warm=[Q.left_arm.r.slice(),Q.left_forearm.r.slice()];const k=smoother(w);la=la.map((v,i)=>v*(1-k)+Q.left_arm.r[i]*k);lf=Q.left_forearm.r.map(v=>v*k);}
  addTo(P,'left_arm','r',L.add(la,[26*r,0,-4*r]));addTo(P,'left_forearm','r',L.add(lf,[62*r+nz[0](t+0.5)*5*s,0,0]));
  return P;}};
