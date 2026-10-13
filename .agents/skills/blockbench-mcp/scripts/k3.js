// kit part 3: bake + key reduction
const CH={r:'rotation',p:'position',s:'scale'};
function catEval(keys,t){ // uniform catmull-rom as Blockbench/Bedrock, ends clamped
  const n=keys.length; if(t<=keys[0].t)return keys[0].v; if(t>=keys[n-1].t)return keys[n-1].v;
  let i=0; while(keys[i+1].t<t)i++;
  const k0=keys[Math.max(0,i-1)],k1=keys[i],k2=keys[i+1],k3=keys[Math.min(n-1,i+2)];
  const u=(t-k1.t)/(k2.t-k1.t);
  return k1.v.map((_,c)=>{const x0=k0.v[c],x1=k1.v[c],x2=k2.v[c],x3=k3.v[c];
    return 0.5*((2*x1)+(-x0+x2)*u+(2*x0-5*x1+4*x2-x3)*u*u+(-x0+3*x1-3*x2+x3)*u*u*u);});}
function reduce(samples,tol,keep=[],maxGap=0.5){
  // samples [{t,v}]; greedy: start with ends + keep, insert worst sample until within tol
  const idx=new Set([0,samples.length-1]);
  keep.forEach(kt=>{let b=0;samples.forEach((s,i)=>{if(Math.abs(s.t-kt)<Math.abs(samples[b].t-kt))b=i;});idx.add(b);});
  for(let guard=0;guard<500;guard++){
    const ks=[...idx].sort((a,b)=>a-b);
    // enforce max gap
    let added=false;
    for(let j=0;j+1<ks.length;j++){if(samples[ks[j+1]].t-samples[ks[j]].t>maxGap+1e-9){idx.add(Math.round((ks[j]+ks[j+1])/2));added=true;}}
    if(added)continue;
    const keys=ks.map(i=>samples[i]);let worst=-1,we=tol;
    samples.forEach((s,i)=>{if(idx.has(i))return;const e=catEval(keys,s.t);const d=Math.max(...e.map((x,c)=>Math.abs(x-s.v[c])));if(d>we){we=d;worst=i;}});
    if(worst<0)break; idx.add(worst);}
  return [...idx].sort((a,b)=>a-b).map(i=>samples[i]);}
// spec: {length, fps, sample(t)->pose, keep:[times], tol:{r,p,s}, bones?:[names]}
function bake(spec){
  const fps=spec.fps||30, N=Math.ceil(spec.length*fps-1e-6);
  const series={};
  for(let i=0;i<=N;i++){const t=+Math.min(i/fps,spec.length).toFixed(4);const pose=spec.sample(Math.min(t,spec.length));
    for(const [b,chs] of Object.entries(pose)){for(const [c,v] of Object.entries(chs)){if(!CH[c])continue;
      const key=b+'|'+c;(series[key]=series[key]||[]).push({t,v:v.map(x=>+x.toFixed(3))});}}}
  const tol=Object.assign({r:0.12,p:0.015,s:0.005},spec.tol||{});
  const out=[];let total=0;
  for(const [key,s] of Object.entries(series)){const [b,c]=key.split('|');
    if(s.length!==N+1)throw new Error('inconsistent channel '+key);
    if(c==='r')for(let i=1;i<s.length;i++)s[i].v=s[i].v.map((x,k)=>{const p=s[i-1].v[k];while(x-p>180)x-=360;while(x-p<-180)x+=360;return +x.toFixed(3);});
    const neutral=c==='s'?1:0; if(s.every(x=>x.v.every(y=>Math.abs(y-neutral)<1e-3)))continue;
    const ks=reduce(s,tol[c],spec.keep||[],spec.maxGap||0.5);total+=ks.length;
    out.push({bone:b,channel:CH[c],keys:ks.map(k=>({time:k.t,values:k.v}))});}
  return {channels:out,total};}
