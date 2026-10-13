// kit part 2: legs, feet, gait
const SIDE={r:{leg:'right_leg',shin:'right_shin',foot:'right_foot'},l:{leg:'left_leg',shin:'left_shin',foot:'left_foot'}};
const FOOT={};
for(const s of ['r','l']){const fb=rig.bones[SIDE[s].foot];const pts=[];
  fb.cubes.forEach(c=>cubeCorners(c).forEach(p=>pts.push(sub(p,fb.origin))));
  const sole=fb.cubes.find(c=>c.name.startsWith('sole'));const cx=(sole.from[0]+sole.to[0])/2-fb.origin[0];const y=sole.from[1]-fb.origin[1];
  FOOT[s]={pts,heel:[cx,y,sole.to[2]-fb.origin[2]],toe:[cx,y,sole.from[2]-fb.origin[2]]};}
function minY(s,R){let m=1e9;for(const p of FOOT[s].pts){const y=mv(R,p)[1];if(y<m)m=y;}return m;}
const footR=(yaw,pitch=0,roll=0)=>mm(ry(yaw),mm(rx(pitch),rz(roll)));
const REST=(()=>{const W=fk(rig,{});const o={};for(const s of ['r','l']){const f=SIDE[s].foot;
  o[s]={pivot:xf(W[f],rig.bones[f].origin),yaw:euler(W[f].R)[1]};}return o;})();
// pivot of a flat foot standing on ground (y=0) at x,z
const flatPivot=(s,x,z,yaw)=>[x,-minY(s,footR(yaw)),z];
// plant: put foot pivot at world point with world rotation Rw (IK on thigh x,z + shin x)
const WARM={};
function plant(pose,s,pivot,Rw){const S=SIDE[s];
  pose[S.foot]=pose[S.foot]||{};pose[S.foot].r=[0,0,0];
  if(WARM[s]){if(!(pose[S.leg]&&pose[S.leg].r))setTo(pose,S.leg,'r',WARM[s][0]);if(!(pose[S.shin]&&pose[S.shin].r))setTo(pose,S.shin,'r',WARM[s][1]);}
  const err=ik(pose,[[S.leg,0],[S.leg,2],[S.shin,0]],S.foot,rig.bones[S.foot].origin,pivot,{iters:40});
  WARM[s]=[pose[S.leg].r.slice(),pose[S.shin].r.slice()];
  orient(pose,S.foot,Rw);return err;}
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
