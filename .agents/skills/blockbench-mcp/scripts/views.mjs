// static views of the live project (edit mode rest pose, or an animation at times): node views.mjs out.jpg '[[pos],[target]],...' [anim t]
import * as B from './bb.mjs';
import fs from 'node:fs';
import {execSync} from 'node:child_process';
await B.connect();
const [out,vs,anim,t]=process.argv.slice(2);const V=JSON.parse(vs);const files=[];
if(anim){await B.call('animation_timeline',{action:'set_time',time:+t,animation_id:'animation.marine.'+anim});}
else{await B.call('set_mode',{mode_id:'edit'}).catch(()=>{});await B.call('risky_eval',{code:'(Canvas.updateAll(), 1)'});}
for(const [i,[pos,target,fov]] of V.entries()){const id='vv'+i+'_'+Date.now()%10000;
  await B.call('create_offscreen_view',{id,width:520,height:440,copy_view:'none'});
  try{const s=await B.call('set_camera_angle',{view:id,position:pos,target,projection:'perspective',fov:fov||36,format:'jpeg',max_size:420});
    const im=s.content.find(c=>c.type==='image');const f=`/private/tmp/vv_${i}.jpg`;fs.writeFileSync(f,Buffer.from(im.data,'base64'));files.push(f);}
  finally{await B.call('delete_offscreen_view',{view:id}).catch(()=>{});}}
execSync(`uv run -q --with pillow python3 - <<'PY'
from PIL import Image
fs=${JSON.stringify(files)}
ims=[Image.open(f) for f in fs]; w=sum(i.width for i in ims); h=max(i.height for i in ims)
s=Image.new('RGB',(w,h),'white'); x=0
for i in ims: s.paste(i,(x,0)); x+=i.width
s.save('${out}')
PY`);console.log(out);
