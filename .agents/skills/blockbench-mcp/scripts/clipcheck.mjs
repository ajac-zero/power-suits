// Worst penetration of bone A's cubes into bone B's cubes over every sample of the given spec files.
// usage: node clipcheck.mjs <specs csv> <boneA> <boneB> [cube-name regex limiting B]
// Bone names are the spec's (see kit.json / mirrorModel in bb.mjs).
import * as B from './bb.mjs';
import fs from 'node:fs';import path from 'node:path';
await B.connect();const m=await B.model();
const clip=new Function(B.src('clip.js'))();
const [files,A,Bn,re]=process.argv.slice(2);
const opt=re?{skipB:new RegExp('^(?!('+re+')$)')}:{};
for(const f of files.split(',')){const K=B.kit(B.specModel(m,f));const spec=new Function('K',fs.readFileSync(path.resolve(f),'utf8'))(K);let worst={depth:0};
  for(let t=0;t<=spec.length+1e-9;t+=0.02){const r=clip(K,spec.sample(t),A,Bn,opt);if(r.depth>worst.depth)worst={...r,t:+t.toFixed(2)};}
  console.log(f,JSON.stringify(worst));}
