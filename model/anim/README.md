# Power Armor animation sources

Procedural specs that generated the animations in `../power_armor.bbmodel`. Pipeline docs: `../../.agents/skills/blockbench-mcp/ANIMATION.md`.

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

**Shoulder limits (2026-10-14).** The live animations are clamped for a heavy suit: the pauldrons don't move on
their own (all `right_pad`/`left_pad` keys zero) and the arms' Z rotation stays within 6° of the rest pose (sideways out or in); the feet's twist (Y) and roll (Z)
stay within 10° (the ankle cuffs). The specs still bake
the old motion: after re-baking, run `../art/shoulders_apply.js` step `limits` and re-check with the skill's
`scripts/livesweep.js`. The arm pivots also moved 2 px in (x ±9, same date); specs that solve arm IK need a re-check.

**Upright stance and heavier legs (2026-10-14 overhaul).** The legs' rest pose is upright and symmetric (thigh 11,
shin -20, foot 9 with 7 deg toe-out, splay ±2; it was a crouched, staggered combat stance: 32/-42 and 12/-24, splay
±8), on hips 11 px apart with heavier legs and armoured boots (`../art/legs_stomper_art.py`). The back of the knee is
open: it bends cleanly to ~100. The ankle bends inside the boot's cuff from -30 (toes down) to +40 (toes up):
`kit.json` `"ankle"` makes the kit's `plant()` roll the boot about its heel or toe past that (no slip while it touches
the ground). Keep the knees under ~100 and run the skill's `scripts/knees.mjs` on every changed spec (knee / thigh /
hip / ankle ranges; `DUMP=1` per frame). A tight ankle punishes bent stance knees: a bent knee tilts the shin forward by
about half its angle, the boot rolls onto its toe, the ankle rises and the knee folds more (a first try at ±18 folded
the walk's knee to ~100). Hip heights are relative to the stance: 0 = feet flat with the knees at ~22. `a_death.js`'s
FK leg keys were written on the old rest pose and are converted (`OLD_REST`), its feet clamped to the ankle range.
After re-baking, run `../art/shoulders_apply.js` step `limits` (re-run it if it reports 0 clamped right after an
upload) and `livesweep.js` (`^shin(_knee)?_[rl]$` vs `^thigh(_knee)?_[rl]$`, arms vs thighs).

| File | Animation | Type |
|---|---|---|
| a_idle.js | idle | full body loop, IK feet |
| a_walk.js | walk v5 (2026-10-14): a real walk, duty 0.6, stride 15, 0.6 s, 2.60 blocks/s, hips from the leg reach (`reachHip`, knee >= 8), knees <= 70, weight rolling over the planted leg (sway 1.1, waist lean), knees tracking out, toes out, tool stabilized (v4 jog in a_walk_v4.js, v3/v2 in a_walk_v3.js/a_walk_v2.js) | full body gait loop |
| a_walk_upper.js | old walk upper-body layer (v1 walk, superseded by a_walk.js) | legacy |
| a_run.js / a_sneak.js | run v6 (2026-10-14): energetic bounding sprint, duty 0.3, 0.56 s, 6.25 blocks/s, lean 11, hips -0.3 +-1.4 (rising into the push-off), own swing path (heel kick, knee drive, paw-back), knees <= 101, hard arm pump, footfall jolt, tool arm half stabilized (v5 "shuffle" in a_run_v5.js, v4 10 blocks/s in a_run_v4.js, v3/v2/v1 in a_run_v3/v2/v1.js) / sneakwalk | full body gait loops |
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
