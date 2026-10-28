// Review shots of the open Blockbench project, tiled into one labelled image, including the
// in-game third-person view from behind (what the player looks at most: the suit's back).
// Codemode function body. Usage:
//   const SKILL = "/Users/ajac-zero/Developer/PowerSuits/.agents/skills/blockbench-mcp/scripts";
//   const src = (await tools.bash({command: `cat ${SKILL}/shots.js`})).output;   // (tools.read can serve a stale copy)
//   const shots = new Function("tools", "image", src)(tools, image);
//   await shots();                                    // default set -> /private/tmp/shots/review.jpg
//   await shots({views: ["tp", "tp_down"], out: "/private/tmp/shots/hips_v2.jpg"});
//   await shots({views: [{name: "hip", pos: [30,16,-50], target: [0,20,0]}]});   // custom view
// View fields: pos, target, fov (35), w/h (600), size (jpeg max side, 400), row (new sheet row).
// Third-person numbers come from the mod: eye 2.6 blocks up (ModEntities eyeHeight), the pilot sits
// 0.55 forward (passengerAttachments), PilotView pulls the camera 6.5 blocks back, vanilla FOV 70
// (vertical), 16:9 window. The model is 16 px per block and faces -Z, so "behind" is +Z.
const EYE = [0, 2.6 * 16, -0.55 * 16], BACK = 6.5 * 16;
const tp = (pitchDown) => {
  const p = pitchDown * Math.PI / 180;
  return {pos: [0, EYE[1] + BACK * Math.sin(p), EYE[2] + BACK * Math.cos(p)], target: EYE,
          fov: 70, w: 854, h: 480, size: 854};
};
const PRESETS = {
  front: {pos: [0, 26, -95], target: [0, 24, 0]},
  front3q: {pos: [60, 34, -70], target: [0, 24, 0]},
  side: {pos: [95, 26, 0], target: [0, 24, 0]},
  back3q: {pos: [-60, 34, 70], target: [0, 24, 0]},
  tp: tp(0),                                       // third person, looking level
  tp_down: tp(25),                                 // third person, looking 25 degrees down
  // Same camera positions zoomed in on the suit (narrow FOV) so the details are readable.
  tp_close: {...tp(0), target: [0, 24, 0], fov: 34, w: 600, h: 600, size: 400, row: true},
  tp_down_close: {...tp(25), target: [0, 24, 0], fov: 34, w: 600, h: 600, size: 400},
  back: {pos: [0, 26, 95], target: [0, 24, 0]},
};
return async function shots(opts = {}) {
  const names = opts.views || ["tp", "tp_down", "tp_close", "tp_down_close", "back3q", "front", "side"];
  const views = names.map(v => typeof v === "string" ? {name: v, ...PRESETS[v]} : v);
  const dir = "/private/tmp/shots", out = opts.out || dir + "/review.jpg";
  await tools.bash({command: `mkdir -p ${dir} && rm -f ${dir}/shot_*`});
  const files = [];
  for (const [i, v] of views.entries()) {
    const id = `shots${i}_${Date.now() % 100000}`, w = v.w || 600, h = v.h || 600;
    try { await tools.mcp__blockbench__delete_offscreen_view({view: id}); } catch (e) {}
    await tools.mcp__blockbench__create_offscreen_view({id, width: w, height: h, copy_view: "none"});
    const s = await tools.mcp__blockbench__set_camera_angle({view: id, position: v.pos, target: v.target,
      projection: "perspective", fov: v.fov || 35, format: "jpeg", max_size: v.size || opts.size || 400});
    const img = s.content.find(c => c.type === "image");
    const f = `${dir}/shot_${String(i).padStart(2, "0")}_${v.name}`;
    await tools.write({path: f + ".b64", content: img.data});
    files.push(f);
    try { await tools.mcp__blockbench__delete_offscreen_view({view: id}); } catch (e) {}
  }
  const decode = files.map(f => `base64 -d -i ${f}.b64 -o ${f}.jpg`).join(" && ");
  const r = await tools.bash({command: `${decode} && uv run --with pillow python3 ` +
    `/Users/ajac-zero/Developer/PowerSuits/.agents/skills/blockbench-mcp/scripts/montage.py ${out} ` +
    `--cols ${opts.cols || 5} ${files.map((f, i) => (i && views[i].row ? "--row " : "") + f + ".jpg").join(" ")}`});
  if (r.exit_code) throw new Error(r.output);
  if (opts.show !== false) {          // read a fresh copy: tools.read may serve a cached file by path
    const view = `${dir}/view_${Date.now()}.jpg`;
    await tools.bash({command: `rm -f ${dir}/view_*.jpg; cp ${out} ${view}`});
    image(await tools.read({path: view}));
  }
  return out;
};
