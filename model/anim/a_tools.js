// Power-tool loops (power armor, Phase 5). Played by the suit's "tool" controller while the tool runs.
// Only the tools' own bones are keyed: GeckoLib controllers override per bone, so touching the forearm
// here would freeze the walk/run arm. MODE (K_JSON='{"MODE":"chainsaw_run"}'):
//  - drill_run (default): drill_bit spins 2 turns per 0.5 s loop (4 rev/s), the drill head buzzes.
//  - chainsaw_run: the chain shimmers (stepped 1-px shifts every tick), the saw buzzes.
const {pnoise,addTo}=K;
const MODE=(K.MODE)||'drill_run';
const T=0.5;
const bx=pnoise(3,T,1,[9,13,17,23]),bz=pnoise(5,T,1,[11,15,19,25]),by=pnoise(7,T,1,[10,14,21]);
if(MODE==='chainsaw_run'){
  return {name:'animation.marine.chainsaw_run',length:T,loop:'loop',fps:50,tol:{r:0.1,p:0.01},
    steps(){ // chain: alternate 0 / -1 every tick (teeth are 1 px apart, so it reads as running)
      const keys=[];for(let i=0;i<=10;i++)keys.push({time:+(i*0.05).toFixed(2),values:[0,i%2?-1:0,0]});
      return [{bone:'chainsaw_chain',channel:'position',keys}];},
    sample(t){const P={};
      addTo(P,'chainsaw','p',[0.12*bx(t),0.1*by(t),0.12*bz(t)]);
      addTo(P,'chainsaw','r',[0.8*bz(t),0,0.8*bx(t)]);
      return P;}};
}
return {name:'animation.marine.drill_run',length:T,loop:'loop',fps:50,tol:{r:0.1,p:0.01},
  sample(t){const P={};
    addTo(P,'drill_bit','r',[0,720*t/T,0]);
    addTo(P,'drill','p',[0.1*bx(t),0.08*by(t),0.1*bz(t)]);
    addTo(P,'drill','r',[0.6*bz(t),0,0.6*bx(t)]);
    return P;}};
