// DRY bake + IK/foot diagnostics + measured ground speed for spec files
import * as B from './bb.mjs';
import fs from 'node:fs';
await B.connect(); const m=await B.model();
for (const file of process.argv.slice(2)) {
  const r=await B.build(file,m,{dry:true,K:JSON.parse(process.env.K_JSON||'{}')}); const K=r.K, spec=r.spec;
  const diag=new Function(fs.readFileSync(new URL('./diag.js', import.meta.url),'utf8'))();
  const d=diag(K,spec,0.01);
  console.log(file, JSON.stringify(r.out), 'lowMin',d.lowMin,'errMax',d.errMax); d.rows.forEach(x=>console.log('  ',x));
}
