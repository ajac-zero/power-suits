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
| `hip_notch_art.py` | Notches under the pelvis shell: stepped seat plates at the back (10x1x1 over 6x1x1), a narrow 2x2x1 codpiece at the front; both hide the dark hip core. |
| `boot_art.py` | Shin + foot as one boot: the foot's pieces repainted in the shin's blue armour (grey welt over the dark sole), the ankle a 9x2x10 cuff (inflate 0.25) around the shin's lower 1 px, roomy enough that the bending shin stays inside it (foot twist/roll limited to 10°, `shoulders_apply.js` `limits`). The ankle still bends. |
| `shoulders_apply.js`, `shoulders_art.py`, `collar_art.py` | Shoulders that join the torso: arms 2 px in (pivots x ±9) with the pauldrons left in place 1 px higher, knee-sized shoulder joints on the arm bones, blue upper arms, collars 3 → 2 wide. Mobility limits in the animations (rigid pauldrons, arm Z rotation within 6° of rest either way, foot twist/roll within 10°): `shoulders_apply.js` step `limits`, re-run after any re-bake. |
| `arms_refit_art.py` | Arms refitted to one rule (axis x ±10.5): pauldrons fit the 7x7 upper arm (0.5 px clearance, no tilt of their own), knee-sized elbow joints on both arms, right forearm an 8x7x8 gauntlet + 5x2x5 wrist, hand re-centred. Resized cubes keep their art (middle rows/columns dropped). |
| `pauldron_art.py` | Space-marine pauldrons: outer wall and front/back plates 5 → 10 px tall (down to y 31.625, 3 px above the elbow); faces keep their top rows and grey rim, the added rows are plain armour. |
| `knee_cut_art.py` + `knee_cut_apply.js` | Knee cutout (animation overhaul): the shin was deeper than the thigh and started 1 px under the knee pivot, so its back top corner entered the thigh from ~15 deg of knee bend (2.75 px at 90). Step 1 cut the shin's back 4 px (clean to 70); step 2, for the sprint's heel kick, cuts it 6 px and the thigh's back 2 px: lower shin 8x4x8 + front knee guard 8x5x4 (`shin_knee_*`), upper thigh 6x6x7 + front 6x2x4 (`thigh_knee_*`, inflate 0.15). The dark knee joint shows in the pit behind. Clean to 115, 0.55 px at 120. New faces sample the original cubes at the same 3D place; the pit's faces are darker. |
| `legs_stomper_art.py` + `legs_stomper_apply.js` | Heavier legs on a wider stance (the top half looked too heavy for thin legs close together). v1, Fallout-style stompers (hips ±6.5, 11x16 boots, a greave over a hidden ankle), was rejected: hollow-looking boots, comically big legs. v2 (solid shin in an ankle cuff, hips ±5.5) was replaced by v3, the user's sketch: the lower leg one 9-wide block in front of the leg's axis with real slopes (rotated cubes) hiding both joints: a knee cap rising above the knee in front of the thigh and a 45° ramp down to the back (knee clean to ~100); an underside sloping up 35° toward the front, a back running down behind the ankle and side skirts down to the foot, so the 7-wide boot (upper, toe cap turned down 25°, sole) swings up into the slot (toes-up ~33° hidden, `model/anim/kit.json` ankle -30..+30, `ankleSquare`: the foot never twists against the shin, toe-out comes from a 7° hip twist, no leg splay). Thighs 7x6x8 + front 7x2x4, hip/knee joints 6x5x6, pelvis shell 18 wide. Re-running it rebuilds every leg cube from a fresh snapshot. |
| `hand_art.py` | The right hand as a closed 7x5x7 fist straight under the gauntlet (no wrist, no open fingers): blue plating, grey thumb / knuckles / curled fingers, dark palm. The `right_hand` grip bone moves to the fist's lower end (y 15.625). |

## T-pose visual pass (`model/t-pose`)

The newer scripts above use stage-specific live snapshots in `.scratch/` (see their docstrings).
Each snapshot contains `{model: Codecs.project.compile(), texture: Texture.all[0].getDataURL()}`.
Run from the repository root with `uv run --with pillow python3 model/art/<script>.py`.
They produce edit plans and texture PNG hex, not saved models; apply plans in Blockbench with an Undo transaction.
These are references for individual iterations, **not an automatic replay pipeline**.

The model was edited in a T-pose (72edc69) during this pass; the T-pose is retired and the saved
model is back in its in-game rest pose (the bone rest rotations of 72edc69^, which the animations
are built on). Front shin/thigh plates were removed; hip joints reuse the knee texture. The abdomen
belongs to `body`, the torso turns above it at `[0, 27.125, 0.5]`, and the hips (`pelvis`, from
`hips_art.py`) are attached to `body`. No game assets have been exported from this pass. Before
integrating: check animations and leg/hip clearance, update game-side torso pivot/aim offsets and
anything placed from the arm pivots (now x ±9, e.g. `SocketAim`, muzzles, cockpit viewmodel; `tools/extract-view-geo.py`
filters the gauntlet by cube size: the 8x8x8 cuff is now the 8x7x8 `forearm_r` + 5x4x5 `elbow_joint_r`; `CockpitHands`
places the gauntlet by its fingertips at y -17 from the elbow, the fist now ends at -13), and
export all derived assets.
