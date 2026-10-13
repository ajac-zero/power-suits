// Bake + upload one animation spec, then film it.
// usage: node run.mjs <spec.js> [times csv|none] [view fl|front|left|right|back|top] [size]
// env: DRY=1 (bake only), K_JSON='{"MODE":"on"}' (extra values exposed to the spec as K.*), BB_OUT=dir
import * as B from './bb.mjs';
await B.connect();
const [file,times='0',view='fl',size='300']=process.argv.slice(2);
const m=await B.model();
const r=await B.build(file,m,{dry:process.env.DRY==='1',K:JSON.parse(process.env.K_JSON||'{}')});
console.log(JSON.stringify(r.out));
const V={fl:{pos:[-70,40,-80],target:[0,22,-4]},front:{pos:[-0.01,30,-110],target:[0,24,-4]},left:{pos:[-140,28,-6],target:[0,22,-6]},
  right:{pos:[140,28,-6],target:[0,22,-6]},back:{pos:[50,40,90],target:[0,24,0]},top:{pos:[-40,110,-60],target:[0,10,-10]}};
if(times!=='none'&&process.env.DRY!=='1'){
  const tag=file.split('/').pop().replace('.js','');
  const f=await B.shots(r.spec.name,times.split(',').map(Number),{...V[view],size:+size,fov:36,w:500,h:560},tag);
  console.log('montage',B.montage(`${B.OUT}m_${tag}.jpg`,f));}
