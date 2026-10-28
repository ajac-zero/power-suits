// Blockbench MCP (streamable HTTP) client + animation pipeline helpers.
// Works even when the agent's MCP connection dropped, as long as Blockbench's MCP plugin listens on BB_URL.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execSync} from 'node:child_process';
const DIR=path.dirname(fileURLToPath(import.meta.url))+'/';
const URL_=process.env.BB_URL||'http://localhost:3000/bb-mcp';
export const OUT=process.env.BB_OUT||path.join(os.tmpdir(),'bb-shots')+'/';
fs.mkdirSync(OUT,{recursive:true});
let sid=null,idc=1;
async function rpc(method,params,notify=false){
  const h={'Content-Type':'application/json','Accept':'application/json, text/event-stream'};
  if(sid)h['mcp-session-id']=sid;
  const body={jsonrpc:'2.0',method,params};if(!notify)body.id=idc++;
  const r=await fetch(URL_,{method:'POST',headers:h,body:JSON.stringify(body)});
  const s=r.headers.get('mcp-session-id');if(s)sid=s;
  if(notify)return null;
  const txt=await r.text();let msg;
  if((r.headers.get('content-type')||'').includes('event-stream')){
    const lines=txt.split('\n').filter(l=>l.startsWith('data:')).map(l=>JSON.parse(l.slice(5)));
    msg=lines.find(m=>m.id!==undefined)||lines[lines.length-1];
  }else msg=JSON.parse(txt);
  if(msg.error)throw new Error(method+': '+JSON.stringify(msg.error));
  return msg.result;
}
export async function connect(){await rpc('initialize',{protocolVersion:'2025-03-26',capabilities:{},clientInfo:{name:'bb-anim',version:'1'}});await rpc('notifications/initialized',{},true);}
export async function call(name,args){const r=await rpc('tools/call',{name,arguments:args});
  if(r.isError)throw new Error(name+': '+JSON.stringify(r.content).slice(0,400));return r;}
export async function readRes(uri){return rpc('resources/read',{uri});}
// facade with the same shape as codemode's `tools` (tools.mcp__blockbench__x(args), tools.read({path}))
export const tools=new Proxy({},{get:(_,k)=>{
  if(k==='read')return async({path:p})=>fs.readFileSync(p,'utf8');
  const name=String(k).replace(/^mcp__blockbench__/,'');return args=>call(name,args);}});
export async function evalJSON(code){const r=await call('risky_eval',{code});return JSON.parse(JSON.parse(r.content[0].text));}
// live project JSON (includes unsaved edits)
export async function model(){const info=await call('get_project_info',{});
  const link=info.content.find(c=>c.type==='resource_link');const r=await readRes(link.uri);return JSON.parse(r.contents[0].text);}
export const src=f=>fs.readFileSync(DIR+f,'utf8');
export const KIT_EXPORTS='PI,TAU,clamp,lerp,smooth,smoother,easeOut,easeIn,backOut,wrapDeg,track,pnoise,vnoise,spring,addTo,setTo,ik,orient,SIDE,FOOT,minY,footR,REST,flatPivot,plant,rolled,gaitFoot,footLow,bodyLow,legLen,kneeOff,setKneeFloor,reachHip,setAnkleLimit,setAnkleSquare,bake,catEval,reduce,L,rig';
export function kit(m,extra={}){
  const L=new Function(src('lib.js'))();const rig=L.makeRig(m);
  const K=new Function('L','rig',src('k1.js')+'\n'+src('k2.js')+'\n'+src('k3.js')+`\nreturn {${KIT_EXPORTS}};`)(L,rig);
  Object.assign(K,extra);return K;}
// ---- Mirrored rigs. A model can be mirrored across x = 0 after its specs were written (Power Suits swapped its arms
// that way: the tool arm went from right to left). A `kit.json` with {"mirror": true} next to the spec files says
// "these specs are authored for the un-mirrored rig": the spec then sees the model mirrored back (mirrorModel), and
// what it bakes is mirrored onto the live rig (mirrorChannels) before upload. Tools that measure a spec (check,
// trace, speed, cannon, clipcheck) work in the spec's frame, so use the spec's (pre-mirror) bone names with them.
export const swapSide=n=>n.replace(/^right_/,'\u0001').replace(/^left_/,'right_').replace('\u0001','left_')
  .replace(/_r$/,'\u0002').replace(/_l$/,'_r').replace('\u0002','_l');
const z=v=>v||0; // no -0
// project JSON mirrored across x = 0: cubes/pivots flipped, Y/Z rotations negated, box UV mirrored, left/right names swapped
export function mirrorModel(m){const c=JSON.parse(JSON.stringify(m));
  for(const e of c.elements){const f=e.from.slice(),t=e.to.slice();e.from[0]=z(-t[0]);e.to[0]=z(-f[0]);
    if(e.origin)e.origin[0]=z(-e.origin[0]);if(e.rotation)e.rotation=[e.rotation[0],z(-e.rotation[1]),z(-e.rotation[2])];
    e.mirror_uv=!e.mirror_uv;e.name=swapSide(e.name);}
  for(const g of c.groups){g.origin[0]=z(-g.origin[0]);if(g.rotation)g.rotation=[g.rotation[0],z(-g.rotation[1]),z(-g.rotation[2])];g.name=swapSide(g.name);}
  return c;}
// baked channels / stepped channels mirrored the same way (rotation y,z and position x negated; scale kept)
export const mirrorChannels=chs=>chs.map(c=>({...c,bone:swapSide(c.bone),keys:c.keys.map(k=>({...k,values:
  c.channel==='rotation'?[k.values[0],z(-k.values[1]),z(-k.values[2])]:c.channel==='position'?[z(-k.values[0]),k.values[1],k.values[2]]:k.values}))}));
export function specConfig(file){const f=path.join(path.dirname(path.resolve(file)),'kit.json');return fs.existsSync(f)?JSON.parse(fs.readFileSync(f,'utf8')):{};}
// the model as a spec file expects it (mirrored back if its folder's kit.json says so)
export const specModel=(m,file)=>specConfig(file).mirror?mirrorModel(m):m;
// bake a spec file (path relative to cwd) and upload it, replacing any animation with the same name
export async function build(file,m,opts={}){
  const mirror=!!specConfig(file).mirror;
  const K=kit(mirror?mirrorModel(m):m,opts.K);if(specConfig(file).ankle)K.setAnkleLimit(specConfig(file).ankle);if(specConfig(file).ankleSquare)K.setAnkleSquare(true);if(specConfig(file).kneeFloor!=null)K.setKneeFloor(specConfig(file).kneeFloor);const spec=new Function('K',fs.readFileSync(path.resolve(file),'utf8'))(K);const b=K.bake(spec);
  const live=ch=>mirror?mirrorChannels(ch):ch;   // channels for the live rig
  const out={name:spec.name,total:b.total,channels:b.channels.length,mirrored:mirror};
  if(!opts.dry){const up=new Function(src('up.js'))();
    const r=await up(tools,{name:opts.name||spec.name,length:spec.length,loop:opts.loop||spec.loop,channels:live(b.channels),steps:live([...(spec.steps?spec.steps():[]),...(opts.steps||[])])});out.errs=r.errs;}
  return {out,spec,K,b,live};}
// screenshots of an animation at times -> files in OUT (offscreen view, user camera untouched)
export async function shots(anim,times,o={},tag='s'){
  const pos=o.pos||[-55,38,-85],target=o.target||[0,24,0];const files=[];
  await call('create_offscreen_view',{id:'film',width:o.w||520,height:o.h||620,copy_view:'none'});
  try{for(const t of times){
    await call('animation_timeline',{action:'set_time',time:t,animation_id:anim});
    const s=await call('set_camera_angle',{view:'film',position:pos,target,projection:'perspective',fov:o.fov||38,format:'jpeg',max_size:o.size||400});
    const im=s.content.find(c=>c.type==='image');const f=`${OUT}shot_${tag}_${String(t).replace('.','_')}.jpg`;
    fs.writeFileSync(f,Buffer.from(im.data,'base64'));files.push(f);}}
  finally{await call('delete_offscreen_view',{view:'film'}).catch(()=>{});}   // may throw "onContextMenu is not defined" but still deletes
  return files;}
export function montage(outFile,files){execSync(`uv run -q --with pillow python ${DIR}montage.py ${outFile} ${files.join(' ')}`);return outFile;}
