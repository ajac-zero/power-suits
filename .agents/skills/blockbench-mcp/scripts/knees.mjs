// Knee/hip report per spec: node knees.mjs spec.js ...   (K_JSON for multi-mode specs)
import * as B from './bb.mjs';
await B.connect(); const m=await B.model();
for(const file of process.argv.slice(2)){
  const r=await B.build(file,m,{dry:true,K:JSON.parse(process.env.K_JSON||'{}')}); const K=r.K,spec=r.spec,rig=K.rig;
  const st={ankle:[999,-999],knee:[999,-999],kneeGround:-999,thigh:[999,-999],low:999,hip:[999,-999],ankleX:-999};
  const N=Math.ceil(spec.length*30);
  for(let i=0;i<=N;i++){const t=Math.min(i/30,spec.length);const P=spec.sample(t);
    const hy=(P.marine&&P.marine.p?P.marine.p[1]:0);st.hip[0]=Math.min(st.hip[0],hy);st.hip[1]=Math.max(st.hip[1],hy);
    for(const s of ['r','l']){const S=K.SIDE[s];const k=-((P[S.shin]?.r?.[0]||0)+rig.bones[S.shin].rest[0]);
      const th=(P[S.leg]?.r?.[0]||0)+rig.bones[S.leg].rest[0];const lo=K.footLow(P,s);const an=(P[S.foot]?.r?.[0]||0)+rig.bones[S.foot].rest[0];st.ankle[0]=Math.min(st.ankle[0],an);st.ankle[1]=Math.max(st.ankle[1],an);
      st.knee[0]=Math.min(st.knee[0],k);st.knee[1]=Math.max(st.knee[1],k);st.thigh[0]=Math.min(st.thigh[0],th);st.thigh[1]=Math.max(st.thigh[1],th);
      st.low=Math.min(st.low,lo);if(lo<0.3)st.kneeGround=Math.max(st.kneeGround,k);
      if(process.env.DUMP)console.log(t.toFixed(3),s,'knee',k.toFixed(0),'thigh',th.toFixed(0),'low',lo.toFixed(2),'hip',hy.toFixed(2));}}
  const f=a=>a.map(x=>x.toFixed(1)).join('..');
  console.log(file.split('/').pop(),spec.name.replace('animation.marine.',''),'knee',f(st.knee),'kneeOnGround',st.kneeGround.toFixed(0),'thigh',f(st.thigh),'hipY',f(st.hip),'footLow',st.low.toFixed(2),'ankle',f(st.ankle));
}
