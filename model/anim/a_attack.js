// ATTACK: 3-round burst (0, .22, .44). Upper-body only so it layers over idle/walk/run.
const {spring,addTo,vnoise,track,clamp}=K;
const T=0.88, SH=[0,0.22,0.44];
const g=u=>u<0?0:u<0.018?u/0.018:Math.exp(-(u-0.018)/0.045);
const kick=t=>SH.reduce((s,ts)=>s+g(t-ts),0);
const kS=spring(kick,{f:7,z:0.38,T}), kM=spring(kick,{f:4,z:0.45,T}), kP=spring(kick,{f:9,z:0.22,T});
const brace=track([[0,0],[0.06,1,'out'],[0.55,1],[0.85,0],[0.88,0]]);
const env=t=>SH.reduce((s,ts)=>s+(t>=ts?Math.exp(-(t-ts)/0.07):0),0);
const shx=vnoise(3,38,1),shz=vnoise(7,41,1);
return {name:'animation.marine.attack',length:T,loop:'once',fps:50,keep:[0,0.22,0.44],tol:{r:0.15,p:0.02},
 steps(){
  const rots=[0,40,18],scl=[[1.9,1.5,1.9],[1.3,2.4,1.3],[0.9,1.3,0.9]];
  const pos=[],sc=[],rt=[];
  SH.forEach((ts,i)=>{
    const k=i%2?0.92:1.0;
    sc.push({time:ts,values:scl[0].map(v=>v*k)},{time:ts+0.025,values:scl[1]},{time:ts+0.05,values:scl[2]},{time:ts+0.075,values:[1,1,1]});
    pos.push({time:ts,values:[0,-5,0]},{time:ts+0.025,values:[0,-6,0]},{time:ts+0.05,values:[0,-4,0]},{time:ts+0.075,values:[0,0,0]});
    rt.push({time:ts,values:[0,rots[i],0]});});
  rt.push({time:0.52,values:[0,0,0]});
  return [{bone:'muzzle_flash',channel:'scale',keys:sc},{bone:'muzzle_flash',channel:'position',keys:pos},{bone:'muzzle_flash',channel:'rotation',keys:rt}];},
 sample(t){
  const P={},k=kick(t),s=kS(t),m=kM(t),p=kP(t),b=brace(t)*(t<0.8?1:1),e=env(t);
  // brace: shoulder tucks, cannon comes up into a firm hold
  addTo(P,'torso','r',[2.6*m-1.5*b,-1.8*m-2*b,0.8*m]);
  addTo(P,'body','r',[0.8*m,-0.6*m,0]);
  addTo(P,'head','r',[-0.8*m+1.8*p-1*b,1.5*m+1.5*b,0.6*shz(t)*e]);
  addTo(P,'right_arm','r',[3.5*s+3*b,0.8*shz(t)*e,-1.5*b]);
  addTo(P,'right_forearm','r',[9*k+2.5*s+2*b,0,0]);
  addTo(P,'right_forearm','p',[0,0.35*k,1.5*k]);
  addTo(P,'cannon','r',[1.2*shx(t)*e,0,1.6*shz(t)*e]);
  addTo(P,'right_pad','p',[0,0.7*(p-k*0.4),0]);
  addTo(P,'right_pad','r',[0,0,3.5*(p-k*0.3)]);
  addTo(P,'left_pad','p',[0,0.35*p,0]);
  addTo(P,'left_arm','r',[5*b+1.2*s,0,-1.5*m]);
  addTo(P,'left_forearm','r',[10*b+1.5*m,0,0]);
  return P;}};
