// returns diag(K,spec,dt): IK errors, foot lows, stance slip, joint ranges
return function diag(K,spec,dt=0.02){
  const L=K.L,rig=K.rig;let lowMin=1e9,errMax=0;const rng={};const rows=[];
  const prev={};let slip=0;
  for(let t=0;t<=spec.length+1e-9;t+=dt){const P=spec.sample(t);
    const W=L.fk(rig,P);
    for(const s of ['r','l']){const y=K.footLow(P,s);lowMin=Math.min(lowMin,y);
      const f=K.SIDE[s].foot;const piv=L.xf(W[f],rig.bones[f].origin);
      if(spec.target){const tg=spec.target(t,s);if(tg)errMax=Math.max(errMax,L.len(L.sub(piv,tg)));}
    }
    for(const n of ['right_leg','right_shin','left_leg','left_shin','right_foot','left_foot']){const r=(P[n]||{}).r;if(!r)continue;
      const o=rng[n]=rng[n]||{min:[1e9,1e9,1e9],max:[-1e9,-1e9,-1e9]};r.forEach((v,i)=>{o.min[i]=Math.min(o.min[i],v);o.max[i]=Math.max(o.max[i],v);});}
  }
  for(const n in rng)rows.push(n+' '+rng[n].min.map(v=>v.toFixed(0)).join(',')+' .. '+rng[n].max.map(v=>v.toFixed(0)).join(','));
  return {lowMin:+lowMin.toFixed(3),errMax:+errMax.toFixed(3),rows};
};
