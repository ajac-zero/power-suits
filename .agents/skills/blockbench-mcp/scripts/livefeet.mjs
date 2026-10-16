// Boot sole heights through Blockbench's own playback: heel / toe height and pitch of each foot per time. Catches what a
// spec can't: keys baked for an older rig (Power Armor: a walk baked for the heel hinge, played after the hinge moved,
// tilted the planted boot up to 30 deg toes-up = "walking up a slope", and sank it 3 px).
// usage: node livefeet.mjs [anim=walk] [heelZ=3.85] [toeZ=-8.8] [soleY=-0.225] [x=5.5]   (Power Armor's boot by default)
import * as B from './bb.mjs';
await B.connect();const [an='walk',hz='3.85',tz='-8.8',sy='-0.225',xx='5.5']=process.argv.slice(2);const anim='animation.marine.'+an;
const len=(await B.model()).animations.find(a=>a.name===anim).length;
for(let i=0;i<20;i++){const t=+(len*i/20).toFixed(4);await B.call('animation_timeline',{action:'set_time',time:t,animation_id:anim});
  const r=await B.evalJSON(`(() => { const o={}; for (const s of ['right','left']) { const g=Group.all.find(g=>g.name===s+'_foot'); g.mesh.updateMatrixWorld(true);
    const x=s==='right'?${xx}:-${xx}; const P=(y,z)=>{const v=new THREE.Vector3(x,y,z).sub(new THREE.Vector3(...g.origin)); v.applyMatrix4(g.mesh.matrixWorld); return [v.y,v.z];};
    const h=P(${sy},${hz}), f=P(${sy},${tz}); o[s]=[h[0],f[0],Math.atan2(f[0]-h[0],h[1]-f[1])*180/Math.PI]; } return JSON.stringify(o); })()`);
  const f=a=>`heel ${a[0].toFixed(2)} toe ${a[1].toFixed(2)} pitch ${a[2].toFixed(1)}`;console.log(t.toFixed(3),'R',f(r.right),'| L',f(r.left));}
await B.call('set_mode',{mode_id:'edit'});
