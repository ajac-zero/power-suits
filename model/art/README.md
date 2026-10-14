# Power Armor art scripts

One-off scripts that added parts to `../power_armor.bbmodel` (kept as reference / to redo a part).

| File | What |
|---|---|
| `mining_tools_art.py` | Power Armor Phase 5: plans the `drill` and `chainsaw` cubes, finds free texture space (alpha **and** every existing cube's box-UV net, missing `uv_offset` = [0,0]), paints the nets procedurally, writes `plan.json` / `paint.json`. Inputs: `tex.png` (texture from `Texture.all[0].getDataURL()`) and `model.json` (live project JSON) in `/private/tmp/p5/`. |
| `mining_tools_apply.mjs` | Checkpoint, paint (`tex.edit`, aborts if a target pixel is used), create the bones under `right_forearm`, place the cubes. Uses `../.agents/skills/blockbench-mcp/scripts/bb.mjs`. |
| `tool_forearms_art.py` (+ `tool_forearms_painters.py`) | Power Armor: the right arm ends in an elbow socket and each socket tool is the forearm. Plans every cube under `right_forearm` (except `elbow_r` and the muzzle `flash`), frees the old nets, packs + paints the new ones; writes `tf_plan.json` / `tf_paint.json` / `tf_preview.png`. Inputs in `/private/tmp/sa/` (`tex.png`, `model.json`). |
| `tool_forearms_apply.mjs` | Checkpoint, repaint, remove the old cubes, move the tool bone pivots to the socket (`[12.5, 25.625, 0]`), raise the muzzle flash into the new muzzle brake, place the cubes. |
| `em_redstone_art.py` | Phase 7 step 3: the EM projector as a redstone electromagnet. Rebuilds every cube of `em_projector` (blue forearm block with a redstone-vented collar, redstone lens, 4 prongs with copper windings and redstone tips), packs + paints the nets (glowing parts in the reserved colours of `tools/make-glowmasks.py`), clears the old nets. Writes `em_plan.json` / `em_paint.json` (`off` = the texture, `on` = with the glow added, for lit previews in Blockbench). Inputs in `/private/tmp/em/` (`tex.png`, `model.json` from the live project). |
| `stompers_art.py` | Simple metal boots: sloped blue toe guards, blue ankle armour, dark soles; no side bolts or heel pistons. |
| `girdle_art.py`, `solid_girdle_art.py` | Waist-armour iterations: a blue band, then a solid hip housing. Final core width matches the chest (15 pixels). |
| `chest_extension_art.py`, `backpack_extension_art.py` | Extend the chest and backpack down to y=27.625, preserving upper texture rows. The backpack plan also removes the lower pipe. |
| `abdomen_joint_art.py` | Shorten the girdle from the top and texture the exposed abdomen like the knee joints. Plan records the new torso pivot `[0, 27.125, 0.5]`. |
| `tassets_art.py`, `tassets_refit_art.py` | Cover the hip housing with blue tassets, then refit them into six separated front/rear/side panels with reduced flare and shorter sides. |
| `hips_art.py` | Replace the girdle + tassets with real hips (`pelvis` bone under `body`, pivot on the hip axis): an 18x4x10 blue pelvis shell whose lower edge is the hip axis, a crotch piece between the thighs and dark bearing caps at the hip pivots; drops the hidden hip joints. |
| `thighs_art.py` | Then slimmer thighs so the hips match the chest: thighs 6x8x7 centred on the hip pivots (armour ends 1 below the hip axis), knee-sized hip joints (knee texture) on the leg bones, a dark hip core in the crotch, pelvis shell 16x4x10 on the hip axis; side caps dropped. |

## T-pose visual pass (`model/t-pose`)

The newer scripts above use stage-specific live snapshots in `.scratch/` (see their docstrings).
Each snapshot contains `{model: Codecs.project.compile(), texture: Texture.all[0].getDataURL()}`.
Run from the repository root with `uv run --with pillow python3 model/art/<script>.py`.
They produce edit plans and texture PNG hex, not saved models; apply plans in Blockbench with an Undo transaction.
These are references for individual iterations, **not an automatic replay pipeline**.

The saved model is deliberately in an editing T-pose. Front shin/thigh plates were removed;
hip joints reuse the knee texture. The abdomen belongs to `body`, the torso turns above it at
`[0, 27.125, 0.5]`, and the hips (`pelvis`, from `hips_art.py`) are attached to `body`.
No game assets have been exported from this pass. Before integrating: restore the original base
rotations, check animations and tasset/leg clearance, update game-side torso pivot/aim offsets,
and export all derived assets. The T-pose starting commit retains the old model's geometry;
the preceding commit retains the original base rotations.
