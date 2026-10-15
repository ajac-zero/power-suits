// animated GIF of a live animation from several views side by side: node gif.mjs <anim> <fps> <out.gif> view1,view2
import * as B from './bb.mjs';
import {execSync} from 'node:child_process';
await B.connect();
const [anim,fps='20',out='/private/tmp/anim.gif',views='side2,tp',cycles='2']=process.argv.slice(2);
const V={side:{pos:[115,22,-4],target:[0,22,-4]},side2:{pos:[-115,20,-4],target:[0,20,-4]},tp:{pos:[0,41.6,95.2],target:[0,24,0]},
  q3:{pos:[-80,30,-80],target:[0,20,-4]}};
const name='animation.marine.'+anim;
const m=await B.model();const len=m.animations.find(a=>a.name===name).length;
const N=Math.round(len*+fps),times=[...Array(N).keys()].map(i=>+(i*len/N).toFixed(4));
const sets=[];for(const v of views.split(','))sets.push(await B.shots(name,times,{...V[v],size:360,fov:36,w:440,h:520},'gif_'+v));
execSync(`uv run -q --with pillow python3 - <<'PY'
from PIL import Image
sets=${JSON.stringify(sets)}
frames=[]
for i in range(len(sets[0])):
    ims=[Image.open(s[i]).convert('RGB') for s in sets]; w=sum(x.width for x in ims); h=max(x.height for x in ims)
    f=Image.new('RGB',(w,h),'white'); x=0
    for im in ims: f.paste(im,(x,0)); x+=im.width
    frames.append(f.convert('P',palette=Image.ADAPTIVE))
frames[0].save('${out}',save_all=True,append_images=frames[1:],duration=int(1000/${fps}),loop=0)
PY`);
console.log(out,N,'frames');
