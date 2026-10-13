// returns clip(K,pose,boneA,boneB,{skip}) -> max penetration depth of A's cube sample points inside B's cubes
return function clip(K,P,A,B,opt={}){
  const L=K.L,rig=K.rig,W=L.fk(rig,P);const MA=W[A],MB=W[B];
  const inv=p=>L.mv(L.tr(MB.R),L.sub(p,MB.t));
  const pts=[];
  for(const c of rig.bones[A].cubes){if(opt.skipA&&opt.skipA.test(c.name))continue;const f=c.from,t=c.to;
    for(const fx of [0,0.5,1])for(const fy of [0,0.5,1])for(const fz of [0,0.5,1]){if(fx===0.5&&fy===0.5&&fz===0.5)continue;
      let p=[f[0]+(t[0]-f[0])*fx,f[1]+(t[1]-f[1])*fy,f[2]+(t[2]-f[2])*fz];
      if(c.rotation&&c.rotation.some(v=>v))p=L.add(L.mv(L.rot(c.rotation),L.sub(p,c.origin)),c.origin);
      pts.push([L.xf(MA,p),c.name]);}}
  let best=0,who='';
  for(const [w,na] of pts){let p=inv(w);
    for(const c of rig.bones[B].cubes){if(opt.skipB&&opt.skipB.test(c.name))continue;let q=p;
      if(c.rotation&&c.rotation.some(v=>v))q=L.add(L.mv(L.tr(L.rot(c.rotation)),L.sub(p,c.origin)),c.origin);
      const i=c.inflate||0;let d=1e9;for(let k=0;k<3;k++){d=Math.min(d,q[k]-(c.from[k]-i),(c.to[k]+i)-q[k]);}
      if(d>best){best=d;who=na+'>'+c.name;}}}
  return {depth:+best.toFixed(2),who};
};
