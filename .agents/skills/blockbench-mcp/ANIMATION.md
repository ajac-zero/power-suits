# Procedural animation pipeline (Blockbench MCP)

Used to build a full, "premium" animation set for a Bedrock entity: every animation is a **JavaScript spec** that returns a pose for any time `t`. It is baked to keys, the keys are reduced with an error check, and the result is uploaded as a new animation with Catmull-Rom curves. All of it is verified against Blockbench's own evaluation.

## Toolkit (`scripts/`)
| File | Role |
|---|---|
| `lib.js` | Math + rig: `makeRig(model)`, `fk(rig,pose)` (exact Blockbench convention), `rot`, `euler` (ZYX), `xf`, `cubeCorners`. |
| `k1.js` | Authoring: `track` (eased key tracks), `pnoise` (seamless loop noise), `vnoise` (one-shot noise), `spring` (damped follower), `addTo`/`setTo`, `ik` (damped least squares), `orient` (world rotation \u2192 local euler). |
| `k2.js` | Legs: `plant(pose, 'r'|'l', pivot, R)`, `flatPivot`, `footR(yaw,pitch,roll)`, `rolled` (heel/toe roll about the contact edge), `gaitFoot(side, phase, G)`, `REST`, `footLow`, `bodyLow`. Assumes bones `right_leg/right_shin/right_foot` (and `left_*`) and a foot cube named `sole*`; adapt `SIDE` otherwise. |
| `k2.js` (gait heights) | `legLen(side, knee)` and `reachHip(T, feet, {kmin, harm, sway})`: the hip height a gait can afford, as high as the planted feet allow with the knee bent at least `kmin`, low-passed to a few harmonics (walks: hips highest over the planted leg). `knee` is the bone's flex: legLen corrects for an ankle pivot off the shin's axis (before 2026-10-15 it took the pivot-line angle, and a heel-hinged boot 4 px back hyperextended knees by 19 deg). |
| `bbrun.mjs`, `views.mjs`, `film.mjs`, `gif.mjs`, `isolate.mjs`, `shots_run.js` | From node: run a codemode-style script, render stills / animation frames / a GIF, hide all but one leg and the hips, review sheet. |
| `knees.mjs` | `node knees.mjs spec.js ...`: knee / thigh / hip ranges and the knee while a foot is on the ground, per spec (`DUMP=1` per frame). |
| `k3.js` | `bake(spec)`: sample \u2192 drop neutral channels \u2192 unwrap euler \u2192 reduce keys (Catmull-Rom error \u2264 tol, max gap). |
| `up.js` | `upload(tools, {name,length,loop,channels,steps})`: delete same-named animation, `create_animation`, set curves (`smooth` = catmullrom, `stepped`), set `hold`. |
| `bb.mjs` | Node client for the MCP HTTP endpoint + `model()`, `kit()`, `build()`, `shots()`, `montage()`, `evalJSON()`; `mirrorModel()` / `mirrorChannels()` / `specModel()` for specs whose folder has a `kit.json` with `{"mirror": true}` (written before the model was mirrored). |
| `run.mjs` | `node run.mjs spec.js 0,0.3,0.6 fl 300` \u2192 bake, upload, film a montage. `DRY=1` bakes only; `K_JSON='{"MODE":"on"}'` passes options. |
| `clipcheck.mjs` | Worst penetration of bone A cubes into bone B cubes across specs (optionally only some B cubes). |
| `vfeet.mjs` | Live lowest-foot check through Blockbench's own interpolation. |
| `clip.js`, `diag.js`, `montage.py` | Penetration depth, IK error/joint ranges, image tiling. |
| `check.mjs` | `node check.mjs spec.js ...`: dry bake + IK error, lowest foot, joint ranges per spec. |
| `speed.mjs` / `livespeed.mjs` | Ground speed (blocks/s) of a gait from the spec (exact; flat-stance samples: foot level at its most common height) / from Blockbench's live playback via `animation_timeline`. |
| `cannon.mjs` | Muzzle travel and aim swing of the cannon over a cycle (jiggle metric). |
| `trace.mjs` | Per-time leg angles, foot lows and IK error of a spec (`check.mjs` and `trace.mjs` honour `K_JSON` for multi-mode specs). |
| `zfight.mjs` | Coplanar same-direction face scan (z-fighting). Rest pose by default; `ANIMS="anim@N;..." TOL=0.05 SKIP=bone,...` scans live animated poses. |
| `gaitsweep.mjs` | `node gaitsweep.mjs spec.js '[{P}, ...]'`: per parameter set (`K.P` overrides) knee / flat-foot knee / thigh / ankle ranges, lowest foot, biggest one-frame knee step, biggest kink (second difference), knee direction changes. `MODEL=f.bbmodel` sweeps a saved model (e.g. a git revision). The workhorse of the gait iteration below. |
| `kinks.mjs` | When a spec kinks: time:value of second differences over a threshold (legs + root height). |
| `livefeet.mjs` | Boot heel/toe heights and pitch through Blockbench's own playback (stale keys after a rig change show up here). |
| `densesweep.js` / `visible.sh` / `showall.sh` / `movepivot.py` | Clip sweep with a grid on every face (codemode) / where some cubes show in an animation (magenta diff) / show everything and restore the user's hidden cubes / a model copy with bone pivots moved, rest pose kept, for IK sweeps. |
| `savekeep.mjs` / `snapshot.mjs` | Save the project with every cube visible and re-hide the user's hidden cubes / dump `{model, texture}` for art scripts. |

Run from the project folder, e.g. `node .agents/skills/blockbench-mcp/scripts/run.mjs model/anim/a_idle.js 0,1,2 fl`. In codemode you can also load the same files with `tools.read` + `new Function` (as the original session did).

## Spec format
A spec file is a function body that receives `K` (the kit, with `K.L` and `K.rig`) and returns:
```js
return {name:'animation.x.idle', length:4.0, loop:'loop'|'once'|'hold', fps:20,
  tol:{r:0.12,p:0.015}, keep:[0.22], maxGap:0.5,          // optional
  steps(){ return [{bone:'muzzle_flash',channel:'scale',keys:[{time:0,values:[1.9,1.5,1.9]}, ...]}]; }, // stepped channels
  sample(t){ const P={}; K.addTo(P,'torso','r',[x,y,z]); ...; K.plant(P,'r',pivot,R); return P; }};
```
- Pose = `{bone:{r:[deg], p:[units], s:[k,k,k]}}`; values are **offsets on top of rest**, in editor coordinates.
- Every channel a spec emits must be present in **every** sample (initialise to `[0,0,0]`), or `bake` throws.
- `sample` is called with increasing `t`, so closures can warm-start IK. Precompute a table if a pose depends on an earlier frame (e.g. a grab transform).

## Building blocks / recipes
- **Layers** (sum of): base pose + breathing (`sin`, 2 breaths per 4 s loop) + weight shift (hip x, pelvis roll, opposite torso roll, head counter-roll) + `pnoise` micro-motion + gaze `track` with holds and fast saccades passed through a `spring` (f\u22483, z\u22480.5) for a natural overshoot. Torso follows the gaze 30% with a slow spring.
- **Secondary motion**: drive pads/cannon/head with `spring(input)`; use `spring(x) - x` as a lag offset. In loops call `spring(..., {T, loop:true})` so it is periodic.
- **Impacts** (shots, landings, hits): an impulse `u<0.02 ? u/0.02 : exp(-(u-0.02)/0.045)` summed per event, fed into springs of different stiffness for each part (forearm f7, torso f4, pads f9 z0.22). Decaying `vnoise` envelope = shake.
- **Standing animations**: plant both feet flat on the ground each frame (`flatPivot` at the rest x/z/yaw, then `plant`). This also fixes rest poses whose foot floats.
- **Gait** (`gaitFoot`): stance moves the flat foot linearly backward (zf\u2192zb); heel strike pitch and toe-off pitch roll **about the heel/toe edge** (`rolled`), so the contact point never slips; swing uses a Hermite with ground-speed-matched end velocities, lowest point lifted to `H\u00b7sin(\u03c0u)^0.9`, pitch toe-off \u2192 hang \u2192 strike. Phase 0 = heel strike; left = right + 0.5. Hip height bobs at 2\u00d7 frequency (lowest at mid-stance for running, high at mid-stance for walking).
- **Upper body vs legs in a cycle** (phase f = 0 at right heel strike): pelvis yaw `+A cos f` (right hip forward), pelvis roll `+B sin f` (drops the swing-side hip), shoulders counter-yaw/roll, head stabilises (\u2248 \u2212(pelvis + torso) \u00d7 0.85), free arm `x = C cos(f \u2212 lag)` (forward with the opposite leg), forearm drags with a larger lag.
- **Arm/hand IK**: `ik(P, [[arm,0],[arm,1],[arm,2],[forearm,0]], 'forearm', handCentreLocal, target)` with `damp` 0.01\u20130.02 and a warm start. Express targets in **torso space** (`xf(W.torso, local)`) so they ride the torso twist. Blend IK in/out with `smoother(w)` on the resulting angles.
- **Prop follows a hand** (helmet lifted off, item picked up): at grab time capture `G = inv(W.hand) \u00b7 W.prop`; afterwards desired prop world = `W.hand \u00b7 G`; set the prop bone with `orient()` for rotation and `p = inv(W.parent)(pivotWorld) \u2212 origin` for position. Hide with a stepped `scale 0` key, and fade the offsets to 0 only after it is hidden.
- **Thrown projectile**: before release keep the held offset; after release compute a world ballistic path (`p0 + v\u00b7u + \u00bd g u\u00b2`, g \u2248 \u2212160 units/s\u00b2, cap v near 300) and convert to the parent bone's local offset each frame; spin via `orient`. Hide with a stepped scale key, keep the path going while hidden, then ease back to 0.
- **Overhand throws without a 360\u00b0 wrap**: raise the arm forward/up first (total x 10 \u2192 60 \u2192 200 \u21b7 cocked behind the head), then come back over the top (200 \u2192 160 release \u2192 55 follow-through \u2192 rest). Going backward first forces a net \u2212360\u00b0 that breaks blending.
- **Falls / death**: author FK key tracks for the collapse, blend the planted-feet IK into them over ~0.3 s, then each frame lift the root so `bodyLow(pose) = 0` while in contact (only prevent penetration before contact). A short `lift` bounce track on top gives the impact. When face-down, arms "forward" point into the ground; lay them along the body/side instead.
- **Convulsions**: `pnoise` with harmonics 9\u201321 over the loop length for jitter plus 1\u20134 for slow waves; feet planted; stiff, locked-out arms.

## Lessons from the Power Suits (Minecraft/GeckoLib) port
- Arm IK (4 DOF: arm x/y/z + forearm x) has an elbow-forward and an elbow-backward branch; the solver follows its seed. Seed on the natural flex side (check the sign on the rig: here +forearm X flexes), start every sample from the same seed so related animations meet at identical poses, and look at the result from the side before trusting small residuals.
- Planted-feet IK can lower the hips but not raise them above the rest stance (legs nearly straight): put "jolt" / overshoot accents on the torso and head instead (IK error jumped 0.04 -> 0.34 at a hip rise of 0.3).
- Stabilize a held weapon in gait specs: wrap `sample` so arm IK holds the elbow near its cycle-average position (keep ~25% of the bob) and `orient` the forearm to the average world rotation (keep ~15% of the sway). Cut run aim swing 25 deg -> 3.7 deg. See `stabilize()` in `model/anim/a_walk.js`.
- Game speeds: design gaits from the target speed (S / (beta*T)); a ~19 px stride is the reach limit for this rig, so >5 blocks/s needs a jog/run gait, not a walk.
- In a spec file, code after the final `return {...}` never runs: define wrappers before it (function declarations are hoisted).
- Probes of live playback can read stale data after re-uploading; measure via `animation_timeline set_time` (livespeed.mjs) and cross-check with the spec.
- The `read` tool may serve a cached image for a path it already read; copy to a new filename to view a regenerated montage.
- Z-fight scans must orient face normals outward: flush stacked cubes (opposite normals) are harmless; same-direction coplanar faces flicker. Inflate 0.1 fixes them without touching box UVs.

## Gait iteration (Power Suits walk v6 / run v8, 2026-10-15)
The method that turned "baby steps", "hopping" and "a stutter" into gaits the user approved:
1. **Translate the user's words into gait numbers** before touching anything: cycle length T, stance length S, duty
   beta (speed = S / (beta T); keep the game speed, so a longer T means longer steps), flight time, hip height curve,
   arm amplitude. "Slower, bigger steps" -> T and S up; "baby steps" -> S and T too small; "hopping" -> too much flight;
   "pushing hard into the ground" -> more ground time, the hips sinking into the loaded leg, a push-off that throws the
   trailing leg back.
2. **Make each idea a spec parameter** (`O = Object.assign({...defaults}, K.P)`) so one spec covers every variant and
   `gaitsweep.mjs` can compare dozens of sets in seconds without uploading anything.
3. **Sweep, then read the numbers**: flat-foot knee (crouch), thigh range (leg split; a push-off needs the trailing
   thigh behind vertical), ankle range against the boot's clean range, lowest foot, and smoothness (step, kink, turns).
   A clean run/walk turns its knee 4 times a cycle; a bump at landing adds 2.
4. **Locate defects with `kinks.mjs`** and read the spec at that time (strike, toe-off, a clamp, a limit).
5. **Bake one candidate, film it** (`film.mjs` side + `tp` from behind, `gif.mjs` for the user) and only then show it.
   Keep the candidate's parameters in a file; when approved, write them into the spec's defaults, re-bake from the
   file and check it reproduces the numbers, then commit.

Lessons, each from a real failure:
- **Two IK solutions per foot.** The kit's 3-DOF leg reaches each foot pivot with a straighter and a more bent knee,
  mirrored about the knee angle where hip, knee and ankle pivot line up (`kneeOff`). An ankle pivot behind the shin's
  axis (a heel hinge) puts that point in hyperextension, so only one solution is valid. A pivot IN FRONT (Power
  Armor's front hinge, offset -22.6) puts it at a 23 deg bent knee: lowering the hips at all from standing folds the
  knee to 70-90, a crouch reads as a squat, and near full stretch the solver flips between the solutions (one-frame
  jumps of 30-47 deg). Fixes: `setKneeFloor(-offset)` for the gait (stay on the bent one), `KMIN` >= -offset in
  `reachHip`, and keep swing targets a few degrees short of full stretch.
- **`legLen` must use the bone's knee flex** (it used the pivot-line angle: a heel hinge 4 px behind the shin let
  `reachHip` hyperextend the knees by 19 deg).
- **Clamps make corners.** A hard reach clamp held a leg at its limit and left a kink going in and out: ease into the
  limit (`n2 = L - w exp(-(n - (L - w)) / w)`). The same for contacts: a heel strike whose boot gets snapped to the ankle
  limit at contact, or a foot slapping down about its heel while the ankle hinge sits near the toe, puts a bump in the
  knee (land flatter, roll slower: `strike`, `rin`).
- **Hip curves**: `reachHip` (as high as the planted legs allow) + a flight bounce + a stance sink (lowest early in the
  stance, `sinkAt` 0.25, through a spring) gave the "pushing" run; fixed bob curves went out of the legs' reach at
  push-off. `reachHip` has `cap` (max height) and `w` (smoothing window).
- **A swing path must lift where the hips are**: with the hips low, a low swing (`H`) drags the trailing shin flat along
  the ground (reads as kneeling); a high one folds the knee past 130. Look at the trailing leg from the side.
- **Name things the kit doesn't misread**: a foot cube named `*sole*` is taken as the whole sole.
- **Measure the live result too** (`livefeet.mjs`): keys baked for an older pivot play "fine" in Blockbench while the
  planted boot tilts and sinks.
- **A hinge moved off the bone's axis changes which knee the IK can use**: a pivot at a limb's edge kinks the hip-knee-ankle
  pivot line (the kit's `kneeOff`), so the leg is longest with the knee bent that much (Power Suits: knee hinge at the
  shin's back corner -> 65 deg; hip hinge at the thigh's front edge -> knee 46, a squatting walk). Sweep the gaits on a
  `movepivot.py` copy (`MODEL=`) before moving any hinge.
- **Sample finer than the bake**: at 120 fps the walk's foot dips 0.4 px for an instant between its 30 fps keys (the
  baked loop doesn't show it); check `gaitsweep` low at 120 fps before raising a spec's fps.

## Game-facing design rules (Bedrock)
- Bedrock **adds** simultaneously playing animations. Make one-shot actions that should work while moving (attack, throw, hit, stim, fidget, helmet) **upper-body overlays**: no `marine`/root, leg, shin or foot channels; start and end at exactly zero offset. Full-body animations (idle, walk, run, sneak, death, electrified) own the legs and root.
- Keep event timings stable when you rework an action (shot times, projectile release, hide frames) and tell the user about any length or timing changes: controllers and gameplay code depend on them.
- Report in-game movement speed for each locomotion cycle: stance travel / stance time (e.g. run 13 units in 0.243 s \u2248 3.3 blocks/s), so the entity speed can match and the feet don't slip.
- Variants that only hide a part (e.g. `*_unhelmet` = base + `helmet` scale 0) can be regenerated from the base spec with an extra stepped channel (`build(file, m, {name, loop, steps})`). Suggest a single overlay that only sets the scale as a lighter alternative. Ask before deleting variants; this user removed them once the model became a robot.

## Swapping sides (mirror the whole model)
Moving a weapon/hand to the other arm: mirror the **whole** model across x = 0 instead of swapping only the arms
(Power Suits, 2026-10-13). In one undoable `risky_eval` edit (`Undo.initEdit({elements: Cube.all, outliner: true,
animations: Animation.all})`): cubes `from.x/to.x` -> `-to.x/-from.x`, origin x negated, rotation y/z negated,
`mirror_uv` toggled (box UV: exactly the mirror image); groups origin x and rotation y/z negated; keyframes rotation
y/z and position x negated (data points are numeric strings; wrap Molang as `-(expr)`); swap `right_`/`left_` and
`_r`/`_l` names (groups, cubes, animator names). Groups keep their UUIDs, so no keyframes are lost. Verify: every
live world matrix after = S·before·S (S = diag(-1,1,1)) with names swapped, for all animations at a few times
(was exact, 0 deviation). Why not swap only the arm keys: the free arm would swing with the same-side leg, and an
IK-stabilised arm compensated the torso motion of the old side (sway doubles). Then add `kit.json` to the spec
folder so re-bakes stay valid, and mirror anything outside the model (game-side offsets, item models).

## Square feet sink at the edges
With `ankleSquare` the foot follows the shin's roll, so a toed-out, swaying gait put one sole edge 0.2 px under the
ground; `plant()` now lifts such a foot until its lowest corner is at y 0 (IK error against the gait's target then
reads up to that lift). `film.mjs` takes a JSON view (`'{"pos":[..],"target":[..]}'`) for close-ups.

## Baking and upload pitfalls
- `create_animation` rejects keys past `animation_length` ("must include the last keyframe"). Sample with `N = ceil(length\u00b7fps)` and clamp `t = min(i/fps, length)`; a rounded N silently drops the loop's last frame.
- Unwrap baked euler rotations (\u00b1360 relative to the previous sample) before reduction, or `orient()`-driven spins jump across \u00b1180.
- Catmull-Rom at the first/last key uses the endpoint itself as the missing neighbour. The reducer evaluates with the same rule, so the seam is checked automatically. Measured seams were \u2264 0.05 units.
- Reduction tolerances that looked right: rotation 0.12\u00b0, position 0.015 (legs 0.04\u20130.05\u00b0 / 0.01), jitter animations 0.4\u00b0. Typical results: 120\u2013900 keys per animation.
- Fast IK legs (run heel roll) dip slightly below the ground **between** keys because joint angles, not foot positions, are interpolated (\u22120.2 units at 30 fps, \u22120.11 at 60 fps). Check with `vfeet.mjs` and accept \u2264 0.25 units or raise the fps.
- `manage_keyframes` / `create_animation` take numbers only. To edit one channel inside an existing Molang animation (e.g. keep IK legs and Molang bob, replace the upper body), `manage_keyframes delete` the time-0 Molang key on that channel and `create` baked keys with `interpolation: "catmullrom"`.
- Uploading replaces the animation (new UUID, same name). Re-run the upload instead of patching keys by hand.

## Verification loop (do this for every animation)
1. `DRY=1` bake. Check IK error (`diag.js`: residual < 0.01, joint ranges plausible) and foot lows (feet \u2265 \u22120.01 in the spec).
2. Upload, then compare live matrices to the spec at a few times (deviation < 0.05 units shows the curve settings and key reduction are right).
3. Film a montage from 2\u20133 views at the key moments (contact, peak, release, impact, final hold).
4. Run clip checks between neighbours that can meet (arm\u2194torso/head, forearm\u2194leg, helmet\u2194chest), comparing against the rest-pose baseline.
5. For loops, compare a pose signature at 0 and at `length`. Finally reset the playhead and restore the user's mode.
