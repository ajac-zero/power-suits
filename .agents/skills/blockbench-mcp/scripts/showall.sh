# Show every cube except the hidden-by-default tools (drill/saw/EM), remembering what the user had hidden; restore puts
# the user's visibility back. Renders and sweeps in between see the whole model. The user hides cubes while inspecting
# (it changes during a session), so wrap every review render in show/restore.
# usage: bash showall.sh show | restore      (run from the project folder)
S=$(cd "$(dirname "$0")" && pwd)
EV() { node -e "import('$S/bb.mjs').then(async B=>{await B.connect();const r=await B.call('risky_eval',{code:process.argv[1]});console.log(r.content[0].text)})" "$1"; }
if [ "$1" = show ]; then EV '(() => { window.__userHid = Cube.all.filter(c => c.visibility === false).map(c => c.uuid); Cube.all.forEach(c => { if (!/^(drill|saw|em_)/.test(c.name)) c.visibility = true; }); Canvas.updateVisibility(); return window.__userHid.length; })()';
else EV '(() => { const ids = window.__userHid || []; Cube.all.forEach(c => { c.visibility = !ids.includes(c.uuid); }); Canvas.updateVisibility(); return Cube.all.filter(c => c.visibility === false).length; })()'; fi
