# starcraft_marine animation sources

Procedural specs that generated the animations in `../starcraft_marine.bbmodel`. Pipeline docs: `../../.agents/skills/blockbench-mcp/ANIMATION.md`.

With the model open in Blockbench (MCP plugin on `localhost:3000`), run this from the repo root (`~/Developer/PowerArmor`):

```sh
node .agents/skills/blockbench-mcp/scripts/run.mjs model/anim/a_idle.js 0,1,2,3 fl   # bake, upload, film
DRY=1 node .agents/skills/blockbench-mcp/scripts/run.mjs model/anim/a_run.js none     # bake only
```

Uploading replaces the animation of the same name. Save in Blockbench afterwards.

**The model is mirrored relative to these specs.** On 2026-10-13 Power Armor swapped the arms (working hand on the
right, socket tools on the left) by mirroring the whole model across x = 0 (checkpoint "before arm swap mirror").
The specs below are still written for the original rig (cannon on `right_forearm`, hand on `left_hand`), and
`kit.json` (`"mirror": true`) tells `bb.mjs` to show them the model mirrored back and to mirror what they bake onto
the live rig. Re-baking a spec reproduces the live animation exactly (checked for walk, plasma_shot, hold_item,
use_item). So in these files and in `check`/`trace`/`speed`/`cannon`/`clipcheck` output, `right_*` means today's
`left_*` and vice versa; the live model, exports and game code use today's names (`right_hand`, tools under
`left_forearm`).

Rest poses live in the model, not in the specs: specs add offsets on top of them. When a rest pose changes (the tool
arm's hip carry, 2026-10-13), the offset animations follow by themselves; re-bake the specs that solve IK against the
rest pose (walk/run `stabilize()`, `a_hand.js` for the hand arm) and re-check clipping (`clipcheck.mjs`).

| File | Animation | Type |
|---|---|---|
| a_idle.js | idle | full body loop, IK feet |
| a_walk.js | walk v4: heavy jog, stride 18, 0.6 s, 5.0 blocks/s, cannon stabilized (v3/v2 in a_walk_v3.js/a_walk_v2.js) | full body gait loop |
| a_walk_upper.js | old walk upper-body layer (v1 walk, superseded by a_walk.js) | legacy |
| a_run.js / a_sneak.js | run v4 (stride 19, 0.42 s, 10 blocks/s, cannon stabilized; v3/v2/v1 in a_run_v3.js/a_run_v2.js/a_run_v1.js) / sneakwalk | full body gait loops |
| a_jump_charge.js | jump_charge (hold): crouch wind-up, ends on jump t=0 | full body |
| a_jump.js, a_fall.js, a_land.js | jump (once, ends on fall t=0) -> fall (loop), land (once, ends at rest stance) | full body, shared airborne pose block |
| a_attack.js, a_throw.js, a_hit.js, a_stim.js, a_fidget.js | attack, throw, hit, stim, idle_fidget | upper-body overlays |
| a_helmet.js | helmetoff / helmeton (`K_JSON='{"MODE":"on"}'`) | overlay; **removed from the model** (robot has no unmasking), kept as a prop-in-hand reference |
| a_death.js, a_elec.js | death (hold), electrified (loop) | full body |
| a_hand.js | hold_item (loop 2 s, hand carry), use_item (once 0.3 s, reach): `K_JSON='{"MODE":"use_item"}'`; arm IK to the `left_hand` grip (spec frame; live: `right_hand`) | hand-arm overlays (live: right arm) |
| a_tool_recoil.js | tool_recoil (once, 0.5 s): the tool arm's kick for the EM projector's push (arm/torso recoil of the old shot animations, no gun parts); the chaingun kicks in code | upper-body overlay |
| a_rivet_shot.js | rivet_shot: the rivet gun's shot (piston, drum); **removed from the model** with the rivet gun, kept as reference | legacy |
| a_plasma_shot.js | plasma_shot: the old plasma cannon's shot with muzzle flash (**removed from the model**, kept as reference) | legacy |
| a_power.js | shutdown (hold, 1.6 s), powered_off (hold, last frame), startup (once, 1.4 s): `K_JSON='{"MODE":"startup"}'` etc. | full body, planted feet |
| a_tools.js | drill_run (loop 0.5 s: `drill_bit` 2 turns, head buzz), chainsaw_run (`K_JSON='{"MODE":"chainsaw_run"}'`: stepped chain shimmer, saw buzz). Keys only tool bones (GeckoLib overrides whole bones per controller) | tool overlays (Power Armor Phase 5) |
