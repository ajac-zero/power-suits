// Snapshot of the live project for the art scripts: {model, texture (data URL)} -> file.
// usage: node snapshot.mjs out.json   (Power Armor: .scratch/stomper-source.json for model/art/legs_stomper_art.py)
import * as B from './bb.mjs';
import fs from 'node:fs';
await B.connect();const m=await B.model();
const r=await B.call('risky_eval',{code:'Texture.all[0].getDataURL()'});let t=r.content[0].text;try{t=JSON.parse(t);}catch(e){}
fs.writeFileSync(process.argv[2],JSON.stringify({model:m,texture:t}));console.log('ok',m.elements.length,'cubes');
