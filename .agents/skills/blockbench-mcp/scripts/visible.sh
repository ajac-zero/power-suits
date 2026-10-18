# Where do some cubes show? Films an animation with and without the cubes matching REGEX and tints the pixels that
# change magenta (on top: the plain frames). Found the "hollow bracket" at Power Armor's hip (dark undersides seen at an
# angle) and checked new hip joints for showing between the legs. Arm/tool bones are hidden unless HIDE is set.
# usage: bash visible.sh <anim> <times csv> <view json> <cube regex> <out.jpg>     env HIDE=<group regex>
S=$(cd "$(dirname "$0")" && pwd); A=$1; TS=$2; V=$3; RE=$4; OUT=$5; HIDE=${HIDE:-arm|forearm|hand|socket|elbow|shoulder|pad|chaingun|drill|chainsaw|em_}
EV() { node -e "import('$S/bb.mjs').then(async B=>{await B.connect();const r=await B.call('risky_eval',{code:process.argv[1]});console.log(r.content[0].text)})" "$1"; }
bash $S/showall.sh show >/dev/null
EV "(() => { window.__armHid = []; Cube.all.forEach(c => { let p = c.parent, hit = false; while (p && p.name) { if (/$HIDE/.test(p.name)) hit = true; p = p.parent; } if (hit && c.visibility) { c.visibility = false; window.__armHid.push(c.uuid); } }); Canvas.updateVisibility(); return window.__armHid.length; })()" >/dev/null
node $S/film.mjs $A $TS "$V" /private/tmp/vis_on.jpg 4 >/dev/null
EV "(() => { window.__ovHid = Cube.all.filter(c => new RegExp('$RE').test(c.name) && c.visibility).map(c => { c.visibility = false; return c.uuid; }); Canvas.updateVisibility(); return window.__ovHid.length; })()" >/dev/null
node $S/film.mjs $A $TS "$V" /private/tmp/vis_off.jpg 4 >/dev/null
EV "(() => { const ids = (window.__ovHid || []).concat(window.__armHid || []); Cube.all.forEach(c => { if (ids.includes(c.uuid)) c.visibility = true; }); Canvas.updateVisibility(); return ids.length; })()" >/dev/null
node -e "import('$S/bb.mjs').then(async B=>{await B.connect();await B.call('animation_timeline',{action:'set_time',time:0});await B.call('set_mode',{mode_id:'edit'});await B.call('risky_eval',{code:'(Canvas.updateAll(), 1)'});})"
bash $S/showall.sh restore >/dev/null
uv run -q --with pillow --with numpy python3 - "$OUT" <<'PY'
import sys
from PIL import Image
import numpy as np
on = np.asarray(Image.open('/private/tmp/vis_on.jpg').convert('RGB')).astype(int); off = np.asarray(Image.open('/private/tmp/vis_off.jpg').convert('RGB')).astype(int)
m = np.abs(on - off).sum(2) > 40; o = on.copy(); o[m] = (o[m] * 0.35 + np.array([255, 40, 200]) * 0.65).astype(int)
Image.fromarray(np.concatenate([on, o], 0).astype(np.uint8)).save(sys.argv[1])
PY
echo "$OUT"
