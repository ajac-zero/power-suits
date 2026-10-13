// returns async upload(tools, {name, length, loop:'loop'|'once'|'hold', channels, steps})
return async function upload(tools,A){
  const full=A.name.startsWith('animation.')?A.name:'animation.'+A.name;
  const rm=await tools.mcp__blockbench__risky_eval({code:`(() => { const a=Animation.all.filter(a=>a.name===${JSON.stringify(full)}); a.forEach(x=>x.remove(true,false)); return a.length; })()`});
  const bones={};
  const all=[...A.channels.map(c=>({...c,interp:'smooth'})),...(A.steps||[]).map(c=>({...c,interp:'stepped'}))];
  for(const c of all){const arr=bones[c.bone]=bones[c.bone]||[];
    for(const k of c.keys){let e=arr.find(x=>Math.abs(x.time-k.time)<1e-6);if(!e){e={time:k.time};arr.push(e);}
      e[c.channel]=c.channel==='scale'?k.values:k.values;}}
  for(const b in bones)bones[b].sort((a,b)=>a.time-b.time);
  const cr=await tools.mcp__blockbench__create_animation({name:full,animation_length:A.length,loop:A.loop==='loop',bones});
  const txt=cr.content[0].text; if(!txt.includes('uuid'))throw new Error('create failed '+txt);
  const errs=[];
  for(const c of all){
    const r=await tools.mcp__blockbench__animation_graph_editor({action:c.interp,animation_id:full,bone_name:c.bone,channel:c.channel,axis:'all'}).catch(e=>({err:e.message}));
    if(r.err||r.isError)errs.push(c.bone+'.'+c.channel+':'+(r.err||JSON.stringify(r.content).slice(0,120)));}
  if(A.loop==='hold'){
    await tools.mcp__blockbench__risky_eval({code:`(() => { const a=Animation.all.find(a=>a.name===${JSON.stringify(full)}); Undo.initEdit({animations:[a]}); a.loop='hold'; Undo.finishEdit('Set loop hold'); return a.loop; })()`});}
  return {removed:rm.content[0].text,created:JSON.parse(txt).uuid,channels:all.length,errs};
};
