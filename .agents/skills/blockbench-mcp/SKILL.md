---
name: blockbench-mcp
description: Use when inspecting or editing a model in the user's open Blockbench project through the Blockbench MCP server (rigs, groups, cubes, UVs, textures, animations, walk/run cycles, screenshots). Covers codemode orchestration, reading the live project JSON, viewing screenshots, risky_eval rules, safe undo/saving, FK/IK-solved animation, a procedural animation pipeline (layers, springs, IK, gait, bake + key reduction, overlays), clipping/clearance checks, adding textured box-UV parts, a direct-HTTP fallback when the MCP connection drops, and known pitfalls (box UV, z-fighting, rotation signs, rebuilt groups breaking animations).
---

# Blockbench MCP best practices

Learned from real sessions: rigging/posing a Bedrock entity, fixing its walk cycle and flicker, adding textured parts, a full premium animation set (idle, walk, run, sneak, attack, throw, death, electrified, helmet off/on, stim, hit, fidget) built with the toolkit in `scripts/`, and a chest-armour redesign driven by a clearance check. Follow these before touching the user's project.

- **Building or reworking animations?** Read [ANIMATION.md](ANIMATION.md) (pipeline, toolkit API, recipes, pitfalls) and use `scripts/`.
- **Taking a model into Minecraft (NeoForge + GeckoLib)?** See `~/Developer/PowerArmor/.agents/skills/minecraft-modding/SKILL.md` (export pipeline, rendering, flicker, testing in game).
- Example production specs for a real rig: `model/anim/a_*.js` (model: `model/power_armor.bbmodel`).

## 1. Access: MCP tools live in `codemode`
- Blockbench tools are not direct function calls. Call them as `tools.mcp__blockbench__<name>(args)` inside `codemode`.
- Discover with `await searchTools("rotat", {limit: 60})`, then `await describeTool(name)` for the exact schema. `searchTools` truncates; search several keywords (group, bone, cube, animation, keyframe, texture, uv).
- Batch independent calls with `Promise.all`, and chain dependent ones in one script.
- `setTimeout` does not exist in codemode. To wait for playback or rendering, make another call instead of sleeping.
- Return only what you need. Never `return` a screenshot result: its base64 gets serialized as text (tens of thousands of tokens) and you still cannot see it.
- **Keep scripts short.** A long codemode script can hit the output-token limit and is then not executed at all. Put reusable helpers (FK, solvers) in a source string, `store("lib", src)`, and rebuild them in later scripts with `const L = new Function(load("lib"))()`. `store`/`load` also carry computed data (e.g. solved keyframes) between scripts.
- `tools.read({path})` works inside codemode, which is the way to pull a file produced by bash (e.g. a generated pixel payload) into a script.
- **The `write` tool has an output limit too.** A ~250-line file in one call was rejected ("response hit the output token limit"). Split code into files of ≤ ~120 lines (e.g. `k1.js`, `k2.js`, `k3.js`) and concatenate when loading: `new Function("L","rig", src1 + src2 + src3 + "\nreturn {...}")`. Code that lives on disk can be re-run after edits with `sed`/small `edit` calls instead of re-sending it.
- **If Blockbench itself lost the project** (`risky_eval` errors like `Collection.all is not iterable`, `Project` is 0):
  reopen it with `risky_eval` `Blockbench.read(["<path>.bbmodel"], {}, files => loadModelFile(files[0]))`.
- **Scripts from node** when codemode's Blockbench tools are gone: `scripts/bbrun.mjs file.js '[args]'` runs a
  codemode-style function body (e.g. an art `*_apply.js`, `shots_run.js` for the review sheet) with a node `tools`
  facade; `views.mjs` (rest-pose or animation stills from given cameras), `film.mjs` (frames of an animation),
  `gif.mjs` (an animated GIF of a loop, side by side views) and `isolate.mjs` work the same way.
- **The `read` tool can lag a minute or more on new image files** (ENOENT, or a stale copy for a reused name): write
  each render to a new file name and wait (`sleep 60`) before reading it.
- **If the Blockbench tools vanish mid-session** (`tools.mcp__blockbench__* does not exist`), the agent's MCP connection dropped, not Blockbench. Check `lsof -nP -iTCP:3000 -sTCP:LISTEN`; if Blockbench still listens, drive it directly with `scripts/bb.mjs` (Node streamable-HTTP JSON-RPC client: `initialize` → `mcp-session-id` header → `notifications/initialized` → `tools/call`/`resources/read`). It exposes a `tools` facade with the same shape as codemode, so the same upload code works. Mention the fallback to the user.

## 2. Read the live project as JSON (best inspection tool)
`get_project_info` returns a resource URI like `blockbench://project/<uuid>.bbmodel`. Read it for the full model including unsaved edits:
```js
const r = await tools.read_mcp_resource({server: "blockbench", uri: "blockbench://project/<uuid>.bbmodel"});
const model = JSON.parse((r.contents || r.content)[0].text);
```
- `model.groups` holds group `origin` and **rest `rotation`** (the outliner only holds uuids/children; map them through `groups`). `model.elements` holds cubes (`from/to/origin/inflate/uv_offset`). Map cubes to parent groups by walking `model.outliner`.
- `model.animations[].animators[uuid].keyframes[]` has `channel`, `time`, `interpolation`, `data_points` (numbers **or Molang strings**). Some animators have no `keyframes` key, so use `an.keyframes || []`.
- This is how to list animations and see whether a walk is Molang-driven or keyframed. No list-animations tool exists.

## 3. Seeing the model: use `image()`
- `capture_app_screenshot` and `set_camera_angle` return image blocks. Display them with `image(block)`:
  ```js
  const s = await tools.mcp__blockbench__capture_app_screenshot({max_size: 800, format: "jpeg"});
  image(s.content.find(c => c.type === "image"));
  ```
- Use `format: "jpeg"` and `max_size` ~250-900 to keep it cheap. Small images (250-350) are fine for sequences of animation frames.
- **Model reviews: run `scripts/shots.js`** (codemode helper, usage in its header). It tiles the in-game third-person views
  from behind (exact camera: eye 2.6 blocks, 6.5 back, FOV 70; level and looking down 25, full frame + zoomed) with
  back 3/4, front and side into one labelled sheet, as the project stands (keep it in its rest pose). The player mostly sees the
  suit's back: show these with every model change (user's request). Load it with `bash cat`, not `tools.read` (which served a stale copy).
- Use a new offscreen view id per shot: recreating a just-deleted id reused the old view's camera target.
- Do not move the user's camera. Make an offscreen view, inspect, and delete it when done:
  ```js
  await tools.mcp__blockbench__create_offscreen_view({id: "insp", width: 800, height: 600, copy_view: "none"});
  await tools.mcp__blockbench__set_camera_angle({view: "insp", position: [x,y,z], target: [x,y,z],
    projection: "perspective", fov: 35, format: "jpeg", max_size: 600});
  await tools.mcp__blockbench__delete_offscreen_view({view: "insp"});
  ```
- `set_camera_angle` requires `position` and `projection`. Max 4 offscreen views. Always delete them.
- Orthographic views: the default `zoom` (0.5) is very close; for a ~45-unit model use `zoom` 0.25-0.45. A camera on the +X axis gives a clean side view for stride/knee analysis; a camera **below** the model (`[0.01, -120, 0]`) shows the soles and is the easiest way to tell which foot is forward. Use an asymmetric feature (a gun in one hand, a visor) to orient yourself: work out which image direction is +X / front.
- To inspect an animation pose: `animation_timeline({action: "set_time", time, animation_id})`, then screenshot. Sample several times through the cycle, and reset the playhead afterwards.
- Rotated or posed parts move away from their rest coordinates. Aim the camera at where the part actually is, not at its `from/to`. Take several angles (front, side, from below) and close-ups before judging.
- Ask the user to select the problem element, then call `get_selection`. This beat many rounds of guessing.
- `delete_offscreen_view` can throw `onContextMenu is not defined` (plugin 1.10) yet still remove the view. Catch it and confirm with `risky_eval` `Preview.all.map(p=>p.id)`.
- For animation review, film several times into files and tile them into one image (`scripts/montage.py`), then `read` the montage: one image per pose set instead of many. Use `max_size` 260-420 with a 500x560 view; 220 is too small to judge. Film side views from the side **without** a big weapon (it hides the legs), plus front 3/4 and a close-up of the problem area.

## 4. Tools first, `risky_eval` only for gaps
Prefer dedicated tools:
- Structure: `list_outline` (`include_cubes`, `max_depth`), `find_elements_by_criteria` (name, parent_group, type), `get_project_info`, `get_selection`.
- Cubes: `modify_cube` (from/to/origin/rotation/inflate/uv_offset), `get_cube_uv`, `place_cube` (accepts `group` name, `texture`, and per-element `uv_offset`, `mirror_uv`, `inflate`, `origin`), `rename_element`, `duplicate_element`, `remove_element` (id or unique name), `flip_in_place`.
- Rigging: `bone_rigging` (create, parent, unparent, delete, rename, set_pivot, mirror). `add_group` for new groups.
- Animation: `animation_timeline` (play/pause/stop/set_time), `manage_keyframes` (create/delete/edit/select; `values` are numbers only), `animation_copy_paste` (copies Molang expressions too), `batch_keyframe_operations`, `animation_graph_editor`, `create_animation`.
- Textures: `get_texture` (returns an image; it is also saved to a temp file you can process with bash). For an exact PNG of the current (possibly unsaved, embedded) texture use `risky_eval` `Texture.all[0].getDataURL()` and decode the base64.

Known gaps with no tool (use `risky_eval`): delete an animation, save the project, write raw texture pixels, list animations (read the JSON resource instead, see §2).

`risky_eval` rules:
- Code is evaluated as an **expression**; a top-level `return` is a syntax error. Wrap logic in an IIFE: `(() => { ...; return JSON.stringify(x); })()`.
- The code must not contain the substring `console` **anywhere, including inside data** (a base64 payload tripped this). Encode binary payloads as **hex** (letters a-f only).
- It also rejects `//` and `/* */` comments: strip them from snippets loaded from files (`src.replace(/^\/\/.*$/mg, '')`).
- It creates no undo entry: make writes go through Blockbench's own undoable APIs, do a read-only check first, and tell the user.
- Patterns that worked:
  - Save: `BarItems.save_project.trigger()` (writes to the existing `Project.save_path`), then confirm with `ls -l` and by parsing the file on disk.
  - Delete an animation: first read `{name, uuid, path}`; then `anim.remove(true, false)` (undoable; `false` = do not delete it from an external animation file on disk).
  - Set an animation's loop mode (`create_animation` only knows loop true/false; Bedrock also has `hold`): `const a = Animation.all.find(...); Undo.initEdit({animations:[a]}); a.loop = 'hold'; Undo.finishEdit('Set loop hold');`.
  - Read live bone matrices (verify FK, measure feet/clipping exactly as Blockbench interpolates): `Group.all.forEach(g => { g.mesh.updateMatrixWorld(true); out[g.name] = g.mesh.matrixWorld.elements.slice(); })` (column-major; world(p) = R·(p − group.origin) + t).
  - Paint pixels undoably: `tex.edit(canvas => { const ctx = canvas.getContext("2d"); ... ctx.putImageData(...); return canvas; }, {edit_name: "..."})`. Inside the callback, first check the target region is fully transparent and abort if not. Check `Texture.all` for `layers_enabled`/`path` first.

## 5. Undo and safety
- Call `save_checkpoint({name})` before each meaningful change. It shows up in `get_undo_stack`. Report checkpoint names to the user.
- Never save, export or overwrite files unless asked (or the project has a standing rule: the Power Armor user wants `power_armor.bbmodel` saved with every mod commit that changes it). Say the project is unsaved when you finish. Saving the `.bbmodel` does not export Bedrock geo/animation files; mention that.
- Tell the user before using anything not clearly read-only.
- **Moving the project file** (user asked): check the target does not exist, `mv` it, then `risky_eval` `Project.save_path = "<new path>"` so later saves go to the new place (otherwise Blockbench recreates the old file).

## 6. Rotation conventions: verify, don't assume
- In the Bedrock project tested, animation rotations **add to the group's rest rotation with the same sign**, and the math matched three.js Euler order `ZYX` (matrix `Rz·Ry·Rx`, right-handed). There, +X on a downward-hanging limb swings it toward -Z (the model's front).
- This was later verified **exactly** (error 1e-14) against live `matrixWorld` with a test animation using non-trivial x/y/z rotations and positions on root, leg, shin, foot, torso and head: per bone `M = Parent · T(origin + pos) · R(rest + anim) · T(−origin)`, position keys in editor coordinates with the **same sign on all axes**. `scripts/lib.js` implements this (`fk`, `rot`, `euler`). Re-verify on a new project the same way (create a temp animation, probe matrices, compare, delete it).
- Handy direction table (model faces −Z): torso/head +X tilts the top back (head looks up); +Y turns the front toward −X (the model's left) and moves the +X shoulder/hip forward; +Z lifts the +X side and drops the −X side; a hanging limb under +X swings forward/up, under +Z moves toward +X.
- Verify signs empirically per project: pose at a known time, look from the side/below, and compare with your FK prediction. For a new channel (e.g. a foot Z correction), apply it and check that the result is level (wrong sign = double error, easy to see).
- Groups often have **rest rotations** (e.g. a crouched stance: thigh 32°, shin -42°, foot 10°, legs splayed ±8° in Z). Animation sits on top of these; account for them in any motion math.
- Mirrored limbs often need opposite-sign rotation to go outward. Test and look.

## 7. Animation: diagnosing and solving walk cycles
- **Moonwalking**: the knee bends while the foot sweeps backward (stance) instead of during the forward swing. Check the phase of the knee expression against the thigh's direction of travel. A quick fix is to swap expressions between bones: copy A to a temporary bone that has no keys, copy B to A, copy temp to B (`animation_copy_paste` keeps Molang), then delete the temp key.
- **Swimming/floating feet**: sine thigh swings plus a crouched rest pose make the planted foot rise and sink by several units, and the soles tilt. Measure with forward kinematics before changing anything.
- **FK in codemode**: world point = `P0 + R0(P1 - P0 + R1(P2 - P1 + R2(p - P2)))` with each `R = rot(rest + anim)`. Use it to compute sole heights, foot pitch and clip depth at every phase.
- **IK by numeric solve**: per sample, solve (thigh, shin) with a 2x2 Newton step (numeric Jacobian, clamp the step) so a contact point hits a target (y on the ground, z moving linearly backward at constant speed during stance). Set foot = desired world pitch - (thigh + shin) to keep the sole flat or to roll it. Warm-start each sample from the previous one. Use symmetric targets for both legs even if the rest poses differ.
- A good gait: heel strike with the toe up ~12° (pivot on the heel edge), flat mid-stance, toe-off with the heel up ~25° (pivot on the toe edge), swing with the foot hanging near its rest angle relative to the shin. Raise the ankle wherever the lowest boot point would come within a clearance curve of the floor (e.g. `1.5*sin(pi*u)`).
- **Splayed legs roll the foot sideways** when the knee bends a lot: the Z splay at the hip and the Z correction at the foot stop cancelling once an X rotation sits between them. Solve a per-key foot Z correction that keeps the inner and outer sole edges level.
- **Bake** into keyframes with `manage_keyframes` create (`interpolation: "catmullrom"`, 25-49 keys per cycle; finer around short heel/toe rolls; the last key at the loop length equals the first). Delete old keys on the same channel first. Check interpolated curves numerically (sample a Catmull-Rom between keys) for ground penetration and planted slip, not only the samples. Leave unrelated Molang (body bob, sway) alone.
- Mention that in-game foot slip depends on the entity's speed matching stride / cycle time.
- For new cycles (run, sneak) or many animations, use the generic pipeline in [ANIMATION.md](ANIMATION.md): a 3-DOF damped-least-squares leg IK (thigh x, thigh z, shin x) to a foot pivot, plus exact foot orientation via the parent's inverse rotation, and a gait generator with heel/toe roll around the contact edge (no slip).

## 8. Pitfalls found
- **Box UV and resizing.** In box-UV projects the UV net is generated from the cube size. Resizing a cube remaps its faces to different, possibly transparent, texture pixels. Prefer moving a cube over resizing it. **`inflate` does not change the box UV**, so it is the safe way to grow a cube slightly. Check with `get_cube_uv`.
- **Z-fighting (flicker).** Coplanar overlapping faces. Two kinds:
  - Static: two cubes in the same group with flush overlapping faces.
  - Animated: faces in a parent and child group that lie on a plane the joint rotation preserves (e.g. x = 8.5 on both thigh and shin for a knee rotating about X). They only overlap at some joint angles, so the flicker appears only during the animation.
  - Detect both by scanning cube `from/to` for equal planes across the groups of a limb. Fix by inflating one cube by ~0.25 (keeps the UVs) or offsetting it by ~0.5.
- **Child pivots.** Rotating a parent group carries its child groups. Child groups with their own rotation do not compensate, so feet, hands and plates can detach or clip after a parent change. Check the end-of-chain parts.
- **Rebuilding a group changes its UUID.** Animation keyframes are keyed by group UUID. After `rename` + `create` + `remove_element`, the new group may have no keyframes. Warn the user and check animations afterwards.
- **Clipping on the live project: `scripts/livesweep.js`** (codemode helper): pairs of cube-name regexes, deepest
  point of A inside B at rest and over every animation, using Blockbench's own meshes. Parts animated on their own
  (the Power Armor pauldrons had keys of their own) clip things their parent bone never would: sweep, don't reason.
- **Bigger parts clip more in motion.** After adding geometry to a moving limb, sample points on the new cubes, transform them into the neighbouring bone's frame at every animation phase, and measure how deep they go inside its boxes. Compare against the rest-pose overlap (intended armor wrap) as the baseline.
- **Armour clearance ("chest clips the helmet").** Build the head's swept envelope: for every animation sample, transform head/helmet cube points into the torso's rest frame (only head/helmet channels matter, since head is a child of torso) and record the lowest y per z slice. Redesign the chest below that envelope (a single plate tilted back ~34° worked) and re-check with `scripts/clipcheck.mjs`, restricted to the new cubes. Tweak the worst animation (e.g. less head pitch, more yaw/roll toward the side that rises) instead of shrinking the armour. Ignore intended overlaps (the neck socket inside the chest block) by measuring only the visible-front cubes.
- **Cross-body arm reach clips bulky torsos.** A support hand on a cannon across the chest gave ≥ 2 units of upper-arm/elbow penetration for every grip point and elbow swivel tried. Check with the clip checker before committing; prefer a guard pose or a reach that stays on the arm's own side.
- **Moving a part to the other side** (weapon arm <-> hand arm): mirror the whole model and its animations instead of rebuilding or swapping arm keys; recipe + verification in [ANIMATION.md](ANIMATION.md) (Swapping sides).
- **Shape readability.** Two rounded plates side by side with a centre strip and a dark underside read as breasts. For robots use one continuous plate with horizontal seams and a central emissive core.
- `/tmp` on macOS is a symlink; if the `read` tool cannot see a file there, use `/private/tmp/...`.

- **Box UV floors fractional sizes.** A 2.5x3.3x4.7 cube gets a 2x3x4 net (checked with `place_cube` + `faces.*.uv`).
  Paint nets at `floor(size)`; painting at `ceil` shifted every face and left transparent pixels in the model.
- **Rotated cubes have leaning ends.** A box turned about X (a slope) has end faces perpendicular to the slope, so one
  corner of each end swings out past where you expect (rotating about the top front corner pushes the bottom front
  corner forward by `T*sin(angle)`). Plan every rotated cube so its ends sit inside an axis-aligned neighbour (a thin
  front block, a back strip) and check each corner numerically before placing; a fillet that "almost" covers leaves
  see-through slivers or steps the user will spot.
- **An inflate ladder kills flicker.** Overlapping cubes (same bone or not) whose side faces land on one plane z-fight.
  Give nested pieces different inflates (0 / -0.05 / -0.1 / -0.15) and re-run `zfight.mjs` after every geometry
  change (it caught a shin piece against the boot below it and the hip V against its band).
- **A narrower inner piece hides inside an outer one.** A foot 0.1-1 px narrower than the shin above it can swing up
  into the shin without poking through; the same for a joint block inset 1 px from both limbs: let its excess sit
  inside them instead of fitting it exactly.
- **Bones meet at an angle in the rest pose.** A piece on the shin that reaches up inside the thigh is straight while the
  thigh leans (Power Armor: thigh 6 deg forward, shin -6); its corner poked out of the thigh's front. Check overlaps in
  the posed model, not in model coordinates.
- **`livesweep.js` samples box corners and edge midpoints only**: an edge cutting through the middle of a face is missed
  (it read 0 for a thigh sliding 1.9 px into the hips). Use `scripts/densesweep.js` (a grid on every face) to confirm 0.
- **Rotating limbs vs static armour, in 2D first**: side-view slices with shapely (limb profile rotated over the swing range,
  armour region above) give each design's clean range in seconds; then build and confirm with `densesweep.js`. For a
  joint that must never clip at any angle, make the limb's end round about the axis (chord slabs, like a heel arc) and
  keep the static part outside the **swept region** (union of the limb's profile over the swing, grown for roll/yaw).
- **Where does a part show?** `scripts/visible.sh` films an animation with and without cubes matching a regex and tints the
  difference magenta (found dark undersides reading as a hollow "bracket" from the side).
- **The user's visibility changes during a session** (they hide cubes to inspect): wrap renders in `scripts/showall.sh
  show|restore`, and save with `savekeep.mjs`.
- **Moving a pivot without moving the model**: `scripts/movepivot.py` shifts the bone's subtree by (I - R^-1)(o' - o) so the
  rest pose stays put (checked to 1e-14); sweep the gaits on that copy before deciding on a hinge.
- **codemode `tools.bash` can return a cached result for an identical command**: re-reading an edited file with the same
  `cat` gave the old text. Vary the command (`cat f # v2`). Bash output over ~512 KB is cut (a `.bbmodel` won't parse
  through it): extract the part you need with python first.
- **New bones get empty animators in every animation** (Blockbench adds them on preview): ignore animators without keys
  when diffing animations.
- **Hide everything else to focus** (`scripts/isolate.mjs on|off`), and restore before saving: visibility is saved.
- **Faces nobody should see still show as slivers**: where a slope meets a block, the block's top or the slope's
  underside peeks through at grazing angles. Paint such faces in the armour colour, not dark iron.

## 9. Adding new textured parts (box UV)
1. `get_texture`, copy the temp PNG, and map alpha occupancy (Python with `uv run --with pillow`) to find free space. **Also mark every existing cube's net rectangle as used** (`2(w+d)` x `d+h` at `uv_offset`; a cube without `uv_offset` in the project JSON sits at [0,0]): transparent pixels can still belong to a net. Example packer + painter: `model/art/mining_tools_art.py`.
   - Cubes can have their own Y rotation (any angle in Bedrock): steps of a cone rotated 22.5 deg apart read as a twisted auger.
2. Use integer cube sizes (w,h,d). The net is `2(w+d)` wide and `d+h` tall at `uv_offset (u,v)`: top `(u+d, v)`, bottom `(u+d+w, v)`, then the side strip at `y = v+d`: east `(u)`, north `(u+d)`, west `(u+d+w)`, south `(u+2d+w)`. Confirm with `get_cube_uv` on an existing cube.
3. Sample the palette from existing parts (highlight row, main tone, darker right and bottom edges, trim greys). Generate the face pixels procedurally, preview them upscaled with `read`, and paint them with `tex.edit` (hex payload; see §4).
4. Place cubes with `place_cube({group, texture, elements:[{name, from, to, origin: <bone pivot>, uv_offset, mirror_uv, inflate}]})`. Give the mirrored side `mirror_uv: true` and share the net. Use `inflate` (e.g. 0.25) to push faces off planes already used by neighbouring cubes, so you avoid new z-fighting while keeping integer UV sizes.
5. Keep new parts above the sole or ground contact so the existing ground solve stays valid. Remove old cubes the new ones fully enclose.

## 10. Rebuild a group to change its rotation (no direct tool)
```js
const B = tools.mcp__blockbench__bone_rigging;
await tools.mcp__blockbench__save_checkpoint({name: "before rework"});
await B({action: "rename", bone_data: {name: "left_arm", children: ["left_arm_old"]}});
await B({action: "create", bone_data: {
  name: "left_arm", origin: [-11, 39.6, 0], rotation: [10, 0, -15], parent: "torso",
  children: ["left_pad", "left_forearm", "upper_arm_l", "shoulder_joint_l"]}});
await tools.mcp__blockbench__remove_element({id: "left_arm_old"}); // verify it is empty first (list_outline)
```
Verify the old group has no children before removing it. Re-check animations afterwards.

## 11. Playing an animation
`set_mode({mode_id: "animate"})` then `animation_timeline({action: "play", animation_id: "animation.<name>"})`. Switch back with `set_mode({mode_id: "edit"})`.

## 12. Working style
- Look at a screenshot before and after every change. Say what you could and could not verify (stills vs live playback).
- Quantify before fixing: FK numbers (heights, pitch, clip depth) turned vague complaints ("going backwards", "swimming", "clipping") into precise causes.
- When a request is ambiguous (e.g. "feet too flat": shape or motion?), inspect, then ask with concrete options.
- Change one thing at a time. Make small, reversible edits and report each with its checkpoint name.
