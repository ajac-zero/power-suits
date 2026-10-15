// kit part 2: legs, feet, gait
const SIDE={r:{leg:'right_leg',shin:'right_shin',foot:'right_foot'},l:{leg:'left_leg',shin:'left_shin',foot:'left_foot'}};
const FOOT={};
for(const s of ['r','l']){const fb=rig.bones[SIDE[s].foot];const pts=[];
  fb.cubes.forEach(c=>cubeCorners(c).forEach(p=>pts.push(sub(p,fb.origin))));
  // heel / toe edges: the cube named *sole*, else the foot cubes' bounding box (a boot whose sole is painted on)
  let sole=fb.cubes.find(c=>/(^|_)sole/.test(c.name));
  if(!sole){const lo=[1e9,1e9,1e9],hi=[-1e9,-1e9,-1e9];fb.cubes.forEach(c=>cubeCorners(c).forEach(p=>{for(let i=0;i<3;i++){lo[i]=Math.min(lo[i],p[i]);hi[i]=Math.max(hi[i],p[i]);}}));sole={from:lo,to:hi};}
  const cx=(sole.from[0]+sole.to[0])/2-fb.origin[0];const y=sole.from[1]-fb.origin[1];
  FOOT[s]={pts,heel:[cx,y,sole.to[2]-fb.origin[2]],toe:[cx,y,sole.from[2]-fb.origin[2]]};}
function minY(s,R){let m=1e9;for(const p of FOOT[s].pts){const y=mv(R,p)[1];if(y<m)m=y;}return m;}
const footR=(yaw,pitch=0,roll=0)=>mm(ry(yaw),mm(rx(pitch),rz(roll)));
const REST=(()=>{const W=fk(rig,{});const o={};for(const s of ['r','l']){const f=SIDE[s].foot;
  o[s]={pivot:xf(W[f],rig.bones[f].origin),yaw:euler(W[f].R)[1]};}return o;})();
// pivot of a flat foot standing on ground (y=0) at x,z
const flatPivot=(s,x,z,yaw)=>[x,-minY(s,footR(yaw)),z];
// plant: put foot pivot at world point with world rotation Rw (IK on thigh x,z + shin x)
const WARM={};
function plantRaw(pose,s,pivot,Rw){const S=SIDE[s];
  pose[S.foot]=pose[S.foot]||{};pose[S.foot].r=[0,0,0];
  if(WARM[s]){if(!(pose[S.leg]&&pose[S.leg].r))setTo(pose,S.leg,'r',WARM[s][0]);if(!(pose[S.shin]&&pose[S.shin].r))setTo(pose,S.shin,'r',WARM[s][1]);}
  let err=ik(pose,[[S.leg,0],[S.leg,2],[S.shin,0]],S.foot,rig.bones[S.foot].origin,pivot,{iters:40});
  // two knee solutions reach the same pivot, mirrored about flex = -offset (pivot line straight). The solver follows
  // its warm start; when that would bend the knee backward (flex < KNEE_FLOOR) it takes the mirrored one. With the
  // pivot in front of the shin's axis (offset < 0) the straight-legged stance is the lower solution, the swing needs
  // the upper one (it slid into hyperextension otherwise); a pivot behind the axis only ever has the upper one valid.
  const kOff=kneeOff(s)*180/PI,flex=()=>-(pose[S.shin].r[0]+rig.bones[S.shin].rest[0]);
  if(flex()<OPT.kneeFloor){const k=Math.max(-2*kOff-flex(),OPT.kneeFloor+5);setTo(pose,S.shin,'r',[-k-rig.bones[S.shin].rest[0],0,0]);
    err=ik(pose,[[S.leg,0],[S.leg,2],[S.shin,0]],S.foot,rig.bones[S.foot].origin,pivot,{iters:60});}
  WARM[s]=[pose[S.leg].r.slice(),pose[S.shin].r.slice()];
  orient(pose,S.foot,Rw);
  // squared foot: no twist against the shin, a little roll (+-4: the slot's play) to keep the sole flat
  if(OPT.square){const rr=rig.bones[S.foot].rest;pose[S.foot].r[1]=-rr[1];pose[S.foot].r[2]=clamp(pose[S.foot].r[2]+rr[2],-4,4)-rr[2];
    // the squared foot follows the shin's roll, so one sole edge can dip under a target set for a level foot (0.2 px
    // on a toed-out, swaying walk): lift the foot until its lowest corner is back on the ground (y 0)
    if(!OPT.lifting){const lo=footLow(pose,s);if(lo<-0.005&&pivot[1]+minY(s,Rw)<0.05){OPT.lifting=true;
      try{return plantRaw(pose,s,[pivot[0],pivot[1]-lo,pivot[2]],Rw);}finally{OPT.lifting=false;}}}}
  return err;}
// Ankle bend limits (degrees, foot X rotation incl. its rest, + = toes up toward the shin; null = free). A rig whose
// ankle is hidden inside a boot (Power Armor's stompers: -25..+30) can't bend the foot far against the shin: plant() then rolls the foot instead,
// about its lowest edge while it touches the ground (the contact point doesn't slip), lifting it if it would dig in.
const OPT={ankle:null,square:false,kneeFloor:2};   // ankle: [min, max] degrees or null (free); square: the ankle only bends (pitch)
function setAnkleLimit(d){OPT.ankle=Array.isArray(d)?d:[-d,d];}
// A foot that sits in a slot of the shin (Power Armor's boots) can't twist or roll against it: plant() then keeps the
// foot's Y/Z at zero total (square to the shin) and only bends it; the sole follows the shin's small yaw/roll.
function setAnkleSquare(b){OPT.square=!!b;}
function plant(pose,s,pivot,Rw){const S=SIDE[s];
  if(!OPT.ankle)return plantRaw(pose,s,pivot,Rw);
  const warm=WARM[s]&&WARM[s].map(a=>a.slice()),[lo0,hi0]=OPT.ankle;
  const contact=pivot[1]+minY(s,Rw)<0.05;
  // the foot turned by d degrees (+ = toe up) about the edge that ends up lowest, which stays put while in contact
  const at=d=>{const R=mm(Rw,rx(d));let pv=pivot;
    if(d){let best=null,by=1e9;for(const p of FOOT[s].pts){const y=mv(R,p)[1];if(y<by){by=y;best=p;}}
      if(contact)pv=sub(add(pivot,mv(Rw,best)),mv(R,best));else if(pivot[1]+by<0)pv=[pivot[0],-by,pivot[2]];}
    if(warm){const y=pose[S.leg]&&pose[S.leg].r?pose[S.leg].r[1]:warm[0][1];setTo(pose,S.leg,'r',[warm[0][0],y,warm[0][2]]);setTo(pose,S.shin,'r',warm[1]);}   // keep a thigh twist the spec set
    const err=plantRaw(pose,s,pv,R);return {err,x:pose[S.foot].r[0]+rig.bones[S.foot].rest[0]};};
  const ok=r=>r.x>=lo0-0.05&&r.x<=hi0+0.05;
  let r=at(0);if(ok(r))return r.err;
  // the smallest roll that brings the ankle back within its limit (scan, then bisect)
  const dir=r.x>hi0?-1:1;let lo=0,hi=null;
  for(let d=2;d<=80;d+=2){if(ok(at(dir*d))){hi=d;break;}lo=d;}
  if(hi===null){r=at(0);pose[S.foot].r[0]=clamp(r.x,lo0,hi0)-rig.bones[S.foot].rest[0];return r.err;}
  for(let i=0;i<8;i++){const mid=(lo+hi)/2;if(ok(at(dir*mid)))hi=mid;else lo=mid;}
  return at(dir*hi).err;}
// foot rolled about heel (pitch>0, toe up) or toe (pitch<0) around a flat reference pivot
function rolled(s,flatPv,yaw,pitch,roll=0){
  const R0=footR(yaw),R=footR(yaw,pitch,roll);
  if(Math.abs(pitch)<1e-6)return {pivot:flatPv,R};
  const c=pitch>0?FOOT[s].heel:FOOT[s].toe;
  const cw=add(flatPv,mv(R0,c));
  return {pivot:sub(cw,mv(R,c)),R};}
// gait foot: ph in [0,1), 0 = heel strike; stance [0,beta), swing [beta,1)
// G: {x,zc,S,beta,H,yaw,strike,toe,hang,rin,rout,vm}
function gaitFoot(s,ph,G){
  const {x,zc,S,beta,H,yaw,strike=10,toe=22,hang=-14,rin=0.14,rout=0.22,vm=0.4}=G;
  ph=((ph%1)+1)%1;
  const zf=zc-S/2,zb=zc+S/2;
  const stance=u=>{const z=zf+(zb-zf)*u;const fp=flatPivot(s,x,z,yaw);
    let p=0; if(u<rin)p=strike*(1-smooth(u/rin)); else if(u>1-rout)p=-toe*smooth((u-(1-rout))/rout);
    return rolled(s,fp,yaw,p);};
  if(ph<beta)return stance(ph/beta);
  const u=(ph-beta)/(1-beta);
  const A=stance(1),B=stance(0);
  // pitch through swing: toe-off -> hang -> strike
  const pitch=u<0.4?lerp(-toe,hang,smooth(u/0.4)):lerp(hang,strike,smooth((u-0.4)/0.6));
  const R=footR(yaw,pitch);
  // horizontal: hermite with ground-speed matched end velocities (body frame: +z)
  const T=1-beta, v=(zb-zf)/beta*T*vm; // dz/du at ends
  const h00=2*u*u*u-3*u*u+1,h10=u*u*u-2*u*u+u,h01=-2*u*u*u+3*u*u,h11=u*u*u-u*u;
  const z=h00*A.pivot[2]+h10*v+h01*B.pivot[2]+h11*v;
  const clear=H*Math.pow(Math.sin(PI*u),0.9);
  // height: lowest point at clearance, blended with stance endpoint heights near ends
  let y=clear-minY(s,R);
  const yA=A.pivot[1],yB=B.pivot[1];
  const w0=1-smooth(u/0.15),w1=smooth((u-0.85)/0.15);
  y=Math.max(y, w0*yA+(1-w0)*y); y=Math.max(y,w1*yB+(1-w1)*y);
  return {pivot:[x,y,z],R};
}
// sample a pose's lowest point of all foot cubes (diagnostics)
function footLow(pose,s){const W=fk(rig,pose);const f=SIDE[s].foot;let m=1e9;
  for(const p of FOOT[s].pts){const w=xf(W[f],add(p,rig.bones[f].origin));if(w[1]<m)m=w[1];}return m;}
// whole-body lowest point (for death/ground contact)
function bodyLow(pose){const W=fk(rig,pose);let m=1e9;
  for(const n of rig.order){if(n==='muzzle_flash'||n==='grenade')continue;for(const c of rig.bones[n].cubes)for(const p of cubeCorners(c)){const y=xf(W[n],p)[1];if(y<m)m=y;}}return m;}
// leg length hip pivot -> ankle pivot with the knee bent kneeDeg (thigh/shin lengths from the rig). kneeDeg is the
// bone's flex (-(shin x rotation incl. rest)), not the angle between the pivot lines: an ankle pivot behind the knee
// (Power Armor's boot hinges at its heel, 4 px back) makes the pivot line lean back ~19 deg at a straight bone, and
// treating the two as equal let reachHip hyperextend the knee by that much.
// An ankle pivot IN FRONT of the shin's axis (a boot hinged at the shin's front edge) gives a negative offset: the
// pivot line is straightest with the knee bent by -offset, and a straighter knee bends the pivot line the other way
// (the length only depends on |flex + offset|; plantRaw picks which knee).
function kneeOff(s){const S=SIDE[s],h=rig.bones[S.leg].origin,k=rig.bones[S.shin].origin,a=rig.bones[S.foot].origin;
  const u=sub(k,h),v=sub(a,k);return -Math.atan2(u[1]*v[2]-u[2]*v[1],u[1]*v[1]+u[2]*v[2]);}   // radians; legs hang down
function legLen(s,kneeDeg){const S=SIDE[s],h=rig.bones[S.leg].origin,k=rig.bones[S.shin].origin,a=rig.bones[S.foot].origin;
  const A=len(sub(k,h)),B=len(sub(a,k));
  const g=kneeDeg*PI/180+kneeOff(s);return Math.sqrt(A*A+B*B+2*A*B*Math.cos(g));}
// Hip height (root 'p' y offset) a gait can afford: as high as the planted feet allow with the knees bent at least
// kmin degrees. The raw limit has sharp dips (double support), so it is eroded (min over +-2w of the cycle) and then
// box-blurred twice (+-w each): every blurred value only averages limits taken within reach of its own time, so the
// curve never asks more of a leg than kmin, yet stays smooth and close under the limit (a Fourier low-pass had to be
// shifted down by its worst overshoot, which sank the whole curve). feet: [t => ({s, pivot, contact})] (contact = the
// foot carries weight); sway/dz(t) = root x/z offset. Samples without contact (flight) are bridged. cap = highest
// allowed offset. Returns t => y offset (periodic, linear between N samples).
function reachHip(T,feet,{kmin=10,N=240,w=0.05,cap=1e9,sway=()=>0,dz=()=>0}={}){
  const raw=[];
  for(let i=0;i<N;i++){const t=T*i/N;let best=NaN;
    for(const f of feet){const r=f(t);if(!r.contact)continue;const hp=rig.bones[SIDE[r.s].leg].origin;
      const dx=r.pivot[0]-(hp[0]+sway(t)),dzz=r.pivot[2]-(hp[2]+dz(t)),L=legLen(r.s,kmin);
      const y=r.pivot[1]+Math.sqrt(Math.max(0,L*L-dx*dx-dzz*dzz))-hp[1];if(!(best<=y))best=y;}
    raw.push(Math.min(best,cap));}
  const idx=raw.map((v,i)=>isNaN(v)?-1:i).filter(i=>i>=0);if(!idx.length)throw new Error('reachHip: no contact');
  const fill=raw.map((v,i)=>{if(!isNaN(v))return v;let a=i,b=i;while(isNaN(raw[((a%N)+N)%N]))a--;while(isNaN(raw[b%N]))b++;
    const va=raw[((a%N)+N)%N],vb=raw[b%N];return va+(vb-va)*(i-a)/(b-a);});
  const R=Math.max(1,Math.round(w*N)),at=(a,i)=>a[((i%N)+N)%N];
  const ero=fill.map((_,i)=>{let m=1e9;for(let k=-2*R;k<=2*R;k++)m=Math.min(m,at(fill,i+k));return m;});
  const blur=a=>a.map((_,i)=>{let s=0;for(let k=-R;k<=R;k++)s+=at(a,i+k);return s/(2*R+1);});
  const y=blur(blur(ero));
  return t=>{const u=((t/T)%1+1)%1*N,i=Math.floor(u),f=u-i;return at(y,i)*(1-f)+at(y,i+1)*f;};}
