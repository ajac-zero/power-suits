// core kit part 1. Scope has L, rig.
const {add,sub,sc,mv,mm,tr,rot,euler,fk,xf,rx,ry,rz,len,cubeCorners}=L;
const PI=Math.PI, TAU=2*Math.PI;
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const lerp=(a,b,t)=>a+(b-a)*t;
const smooth=t=>{t=clamp(t,0,1);return t*t*(3-2*t);};
const smoother=t=>{t=clamp(t,0,1);return t*t*t*(t*(t*6-15)+10);};
const easeOut=t=>{t=clamp(t,0,1);return 1-Math.pow(1-t,3);};
const easeIn=t=>{t=clamp(t,0,1);return t*t*t;};
const backOut=(t,s=1.7)=>{t=clamp(t,0,1)-1;return t*t*((s+1)*t+s)+1;};
const wrapDeg=a=>{while(a>180)a-=360;while(a<-180)a+=360;return a;};
// keyed track: keys [[t, value, ease]]; ease of the segment arriving at that key:
// 'io' smoother (default), 'lin', 'out', 'in', 'back', 's', 'cat' (catmull-rom)
function track(keys){
  const E={io:smoother,lin:t=>clamp(t,0,1),out:easeOut,in:easeIn,back:t=>backOut(t),s:smooth};
  const isArr=Array.isArray(keys[0][1]);
  const val=i=>keys[clamp(i,0,keys.length-1)][1];
  return function(t){
    const n=keys.length;
    if(t<=keys[0][0])return keys[0][1];
    if(t>=keys[n-1][0])return keys[n-1][1];
    let i=0; while(keys[i+1][0]<t)i++;
    const [t0,a]=keys[i],[t1,b,e]=keys[i+1]; const u=(t-t0)/(t1-t0);
    if(e==='cat'){const p0=val(i-1),p3=val(i+2);
      const f=(x0,x1,x2,x3)=>0.5*((2*x1)+(-x0+x2)*u+(2*x0-5*x1+4*x2-x3)*u*u+(-x0+3*x1-3*x2+x3)*u*u*u);
      return isArr?a.map((_,k)=>f(p0[k],a[k],b[k],p3[k])):f(p0,a,b,p3);}
    const w=(E[e||'io']||E.io)(u);
    return isArr?a.map((x,k)=>x+(b[k]-x)*w):a+(b-a)*w;};
}
// seamless periodic noise over loop length T
function pnoise(seed,T,amp=1,harm=[2,3,5,7]){
  let s=(seed*7919)%233280; const r=()=>{s=(s*9301+49297)%233280;return s/233280;};
  const comps=harm.map(h=>({h,ph:r()*TAU,a:(0.6+0.8*r())/h}));
  const norm=comps.reduce((q,c)=>q+c.a,0);
  return t=>amp*1.6*comps.reduce((q,c)=>q+c.a*Math.sin(TAU*c.h*t/T+c.ph),0)/norm;
}
// non-periodic noise for one-shots (hash-based smooth value noise)
function vnoise(seed,freq,amp=1){
  const h=i=>{let x=Math.sin(i*127.1+seed*311.7)*43758.5453;return (x-Math.floor(x))*2-1;};
  return t=>{const x=t*freq,i=Math.floor(x),f=x-i,u=f*f*(3-2*f);return amp*(h(i)*(1-u)+h(i+1)*u);};
}
// damped spring follower of input(t); loop -> periodic steady state
function spring(input,{f=3,z=0.35,T=1,loop=false,dt=1/480}={}){
  const w=TAU*f, N=Math.ceil(T/dt), cycles=loop?5:1;
  let x=input(0),v=0; const out=new Float64Array(N+1);
  for(let c=0;c<cycles;c++){for(let i=0;i<=N;i++){const tg=input(Math.min(i*dt,T));
    if(!(loop&&i===0&&c>0)){const a=w*w*(tg-x)-2*z*w*v; v+=a*dt; x+=v*dt;}
    if(c===cycles-1)out[i]=x;}}
  return t=>{const k=clamp(t/dt,0,N),i=Math.floor(k),fr=k-i;return i>=N?out[N]:out[i]*(1-fr)+out[i+1]*fr;};
}
function addTo(pose,bone,ch,v){pose[bone]=pose[bone]||{};const c=pose[bone][ch]||[0,0,0];pose[bone][ch]=[c[0]+v[0],c[1]+v[1],c[2]+v[2]];}
function setTo(pose,bone,ch,v){pose[bone]=pose[bone]||{};pose[bone][ch]=v.slice();}
function solve3(J,f){const A=J.map((r,i)=>[...r,f[i]]);
  for(let i=0;i<3;i++){let p=i;for(let k=i+1;k<3;k++)if(Math.abs(A[k][i])>Math.abs(A[p][i]))p=k;[A[i],A[p]]=[A[p],A[i]];
    const d=A[i][i]||1e-9;for(let k=i+1;k<3;k++){const m=A[k][i]/d;for(let j=i;j<4;j++)A[k][j]-=m*A[i][j];}}
  const x=[0,0,0];for(let i=2;i>=0;i--){let s=A[i][3];for(let j=i+1;j<3;j++)s-=A[i][j]*x[j];x[i]=s/(A[i][i]||1e-9);}return x;}
// damped-least-squares IK on rotation vars [[bone,axis]], so world(localPt in endBone) -> target
function ik(pose,vars,endBone,localPt,target,{iters=40,damp=0.002,tol=2e-3,maxStep=12}={}){
  const get=()=>vars.map(([b,a])=>((pose[b]||{}).r||[0,0,0])[a]);
  const set=q=>vars.forEach(([b,a],i)=>{pose[b]=pose[b]||{};const r=(pose[b].r||[0,0,0]).slice();r[a]=q[i];pose[b].r=r;});
  const endp=()=>xf(fk(rig,pose)[endBone],localPt);
  let q=get(),err=1e9;
  for(let it=0;it<iters;it++){set(q);const p=endp();const e=sub(target,p);err=len(e);if(err<tol)break;
    const J=[],h=0.01;
    for(let i=0;i<q.length;i++){const q2=q.slice();q2[i]+=h;set(q2);J.push(sc(sub(endp(),p),1/h));}
    const M=[[0,0,0],[0,0,0],[0,0,0]];
    for(let a=0;a<3;a++)for(let b=0;b<3;b++){let s=0;for(const c of J)s+=c[a]*c[b];M[a][b]=s+(a===b?damp:0);}
    const y=solve3(M,e); let dq=J.map(c=>c[0]*y[0]+c[1]*y[1]+c[2]*y[2]);
    const m=Math.max(...dq.map(Math.abs)); if(m>maxStep)dq=dq.map(d=>d*maxStep/m);
    q=q.map((v,i)=>v+dq[i]);}
  set(q); return err;}
function orient(pose,bone,Rw){const b=rig.bones[bone];const W=fk(rig,pose);const PR=W[b.parent].R;
  const e=euler(mm(tr(PR),Rw));pose[bone]=pose[bone]||{};pose[bone].r=e.map((v,i)=>wrapDeg(v-b.rest[i]));}
