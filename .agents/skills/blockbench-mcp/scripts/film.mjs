// film a live animation: node film.mjs <anim> <times csv> <view> <out.jpg> [cols]
import * as B from './bb.mjs';
import {execSync} from 'node:child_process';
await B.connect();
const [anim,times,view='side',out='/private/tmp/film.jpg',cols='4']=process.argv.slice(2);
const V={side:{pos:[115,22,-4],target:[0,22,-4]},side2:{pos:[-115,20,-4],target:[0,20,-4]},q3:{pos:[-80,30,-80],target:[0,20,-4]},tp:{pos:[0,41.6,95.2],target:[0,24,0]},back:{pos:[38,34,70],target:[0,20,0]},front:{pos:[-40,30,-75],target:[0,21,-4]},
  legs:{pos:[70,9,-4],target:[0,9,-4]},backlow:{pos:[25,14,60],target:[0,11,0]}};
const name=anim.startsWith('animation.')?anim:'animation.marine.'+anim;
const f=await B.shots(name,times.split(',').map(Number),{...V[view],size:360,fov:36,w:440,h:520},view+'_'+anim);
execSync(`uv run -q --with pillow python3 - <<'PY'
from PIL import Image, ImageDraw
fs=${JSON.stringify(f)}; ts=${JSON.stringify(times.split(','))}
ims=[Image.open(x) for x in fs]; w,h=ims[0].size; c=${cols}; r=(len(ims)+c-1)//c
sheet=Image.new('RGB',(w*c,h*r),'white'); d=ImageDraw.Draw(sheet)
for i,(im,t) in enumerate(zip(ims,ts)): sheet.paste(im,((i%c)*w,(i//c)*h)); d.text(((i%c)*w+6,(i//c)*h+4),t,fill='black')
sheet.save('${out}')
PY`);
console.log(out);
