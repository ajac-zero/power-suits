# Power Armor: lessons from the model, rig and texture

What the Power Armor model (`model/power_suit.bbmodel`) taught us, by topic (moved here from `AGENTS.md` on
2026-10-26). Read it before rig, animation or texture work, together with this skill's `SKILL.md` + `ANIMATION.md` and
the Art direction page of the design docs. Add a new lesson to the right section when something costs a round.

## Art direction
- **The taste rulings live in the design docs' Art direction page** (model first, animations after; simple boxes with
  detail in the texture; no bevels; what "noise" means; how motion words translate into gait numbers; the final
  feet...). Every leg animation is baked on the current legs (walk/run 2026-10-15, the rest re-baked unchanged from their
  specs 2026-10-18); the known clips waiting for the upper-body rework are a roadmap item. A tap of the sprint key keeps
  the sprint going while W is held (vanilla; runAutopilotGait's sprint_tap checks it).
- **Sketches: confirm your reading in words before building**: "an opposite slope" for the thigh meant a thigh bottom
  PARALLEL to the shin top (both falling toward the back, a dark knee band between), and "the slot where the foot is"
  meant the shin coming down to the ground around the foot, not an empty pocket above it. Each misreading cost a full
  rebuild.

## Rig, joints and armour
- **Sides are mirrored, not rebuilt.** The arms were swapped (hand right, tools left) by mirroring the whole
  model across x = 0 (cubes x-flipped + `mirror_uv` toggled, Y/Z rotations and position X negated in rest poses and
  keys, left/right names swapped), which keeps every animation consistent; swapping only the arm keys would have
  put the arm swing in phase with the wrong leg and doubled the torso sway on the stabilised tool arm. The specs
  in `model/anim/` still describe the old rig (`kit.json` mirrors them on bake), so their `right_*`
  is today's `left_*`.
- **Arm IK has two elbow solutions**; with the wrong seed it picks hyperextension (forearm bent backward).
  On this rig positive forearm X flexes the elbow; seed the solver on that side and reuse one seed for all
  animations that must meet at the same pose.
- **Armour that covers a joint caps its range** (knee/hip rework, 2026-10-17/18, branch `knee-clearance`): a corner d px from
  the hinge axis rises d sin(angle). The hips sat 1 px above the hip axis over 8 px deep thighs (clean only -15..+20 deg;
  the walk needs 4..57, the run -19..70); the shin's sloped top clipped the thigh from 38 deg of knee. Fixes that kept every
  pivot and animation: shins 3 px shorter from the top (`shin_drop_art.py`, clean to 120), the thigh's underside sloped
  the other way (`thigh_flip_art.py`), and **drum hips** (`drum_hips_art.py`): each thigh's top an octagon centred on the
  hip axis under a static hood that stays outside the thigh's swept region, with a shoulder slab from the thigh's outer
  corner up into the drum. The fixes that move armour away from the joint beat the ones that shorten the swings.
- **Moving a hinge to an edge (the ankle trick) changes the leg's IK**: the knee at the shin's back corner made the leg
  longest at a 65 deg knee (re-solved walk: knee -8..21 or 96-176); the hip at the thigh's front edge turned the walk
  into a squat (planted knees 75-103). Check on a copy with the skill's `scripts/movepivot.py` + `gaitsweep.mjs` (MODEL=)
  before building a hinge anywhere else than where the bone pivots now.
- **Static armour on a joint must ride a bone that shares the joint's axis**: `pelvis` is a child of `body` (twists +-8 deg
  in the run), so the hip hoods hang from new root bones (`hips` > `right_hood` / `left_hood`) yawed like the legs (-7/+7).
- **The arms hang beside the hips**: in the walk/run they come to x 7.2-8.8 between y 22 and 29 (both sides). The old V
  and thigh tops were fitted to that; anything wider at the waist clips an arm. Measure the arm envelope before widening.
- **The ankle hinges at the shin's FRONT bottom edge** (the final feet, see Art direction): a front hinge makes the leg
  straightest with the knee bent ~23 (gaits use KMIN 23; the kit's knee guard keeps the solver off the backward-bending
  solution). The tool arm's third-person pose never aims (shots leave from the eye, the cockpit has a viewmodel).
- **The legs are rebuilt by a script** (`model/art/legs_stomper_art.py` + `legs_stomper_apply.js`, from a fresh snapshot
  of the live project): when someone tweaks a leg cube by hand, carry the change into the script before the next
  rebuild, or it is undone (the knee joint's 6.2 depth, 2026-10-15).

## Gaits and baked animations
- **Gaits come from the legs, not the game speed**: legs this long (hip 22.6 px) can't walk 5 blocks/s. The old "walk" had
  each foot down only 37.5% of the cycle (a run: both feet in the air) and dropped the hips 3.6 px, which bent the knees
  to 128 deg through the thigh. Hip height is very sensitive (1 px lower ~ +20 deg of knee): derive it from the planted
  leg's reach (`reachHip` in the skill's `k2.js`) and pick the gait from the legs (the game speed is free since the step rate follows it: walk 3.0, sprint 6.25 since 2026-10-20). A limited ankle (`model/anim/kit.json` "ankle") needs straight-ish stance
  knees and short steps: a bent stance knee tilts the shin forward by about half its angle.
- **The step rate follows the real speed along the legs** (2026-10-20, `movementAnimationRate`): a fixed rate per gait slid the
  feet whenever the suit moved at any other speed (strafe, diagonal, stopping: 0.4-1.4 blocks/s) and backing up played
  the walk forwards (a 3.15 blocks/s moonwalk). Backing up plays `walk_back` (the walk's keys reversed in time; rebuild it
  from the walk if the walk is re-baked). GeckoLib 5 can't loop an animation at a negative speed (it clamps at 0).
  Measure with `runAutopilotGait`; what's left is the legs swinging round (18 deg/tick) and turning on the spot.
- **Moving a bone pivot invalidates every baked animation on it**: the keys are joint angles, so after the ankle hinge
  moved, the old walk tilted the planted boot up to 30 deg toes-up ("looks like it's going up a slope") and sank it 3 px.
  Re-solve the IK specs (and check the live result with the skill's `scripts/livefeet.mjs`).
- **A stutter is usually one frame, not a style**: the run's "stutter" was the knee flipping between its two IK
  solutions (47 deg in one frame) where the leg ran out of reach; then corners where a clamp or a foot contact kicked
  in. Measure before tuning: `gaitsweep.mjs` (step / kink / turns per parameter set) and `kinks.mjs` (when).
- **The animation kit takes any foot cube named `*sole*` as the whole sole** (heel and toe edges): a `boot_sole_back`
  strip put the toe in the middle of the boot and skewed every gait solve until renamed (`boot_back_strip`).
- Blockbench animation work: follow the skill's `SKILL.md` + `ANIMATION.md` (its "Gait iteration" section is the method
  that produced walk v6 / run v8); specs in `model/anim/` (the walk stabilizes the tool arm, the run doesn't). Measure gait
  ground speed with `scripts/speed.mjs` and keep `WALK_ANIM_AUTHORED` / `RUN_ANIM_AUTHORED` in sync.

## Texture, glow and paint
- **The suit is bare steel and paint is a texture swap** (2026-10-20): the armour is drawn in the 8 shades of the steel
  ramp (`model/art/steel_art.py`, `STEEL` in `tools/make-paint-textures.py`); the dye textures swap those shades on the
  plate cubes (`PLATES`) for each dye's ramp. So: repaint armour only in those 8 steel shades (another grey on a plate
  won't take paint), keep them off the steel parts' trim, and re-run `make-paint-textures.py` after every texture export
  (it refuses a texture with no steel ramp). What reads as metal (and what reads as plastic or stone): Art direction.
- **Glowing pixels are painted in reserved colours** (dark coal / smoked glass, listed in `tools/make-glowmasks.py`):
  the masks are generated from them, so never reuse those colours elsewhere (the script counts them and refuses).
  The glow is drawn full-bright on top (`RenderTypes.eyes`; in 26.3 it blends by alpha, it doesn't add): dark base + bright
  mask = dead when off, glowing when on, also by day. Light with nothing behind it (the rail cannon's core: see-through
  when off, its pixels cleared by `make-glowmasks.py`'s SEE_THROUGH) fades by the tint's alpha (`SuitGlowLayer.energy`):
  a dimmed opaque colour drew a dark core where there should be air.
- **Glow fades by opacity, never by darkening** (2026-10-26): the 26.3 "eyes" pass blends (`BlendFunction.TRANSLUCENT`), so a
  mask tinted darker (or painted in dark reds meant for an additive glow) paints the part darker than the cold metal: the
  chaingun's warming barrels showed a dark smudge. Fade lights with `SuitGlowLayer.energy` (alpha) and give mask colours
  their own opacity in `make-glowmasks.py` (third item in GLOW). Texels in reserved colours glow wherever they're used:
  a cube that copies another's net (the chaingun's back plate) needs its own net in the cold colours.
- **Free texture space = transparent pixels minus every cube's box-UV net** (a cube without `uv_offset` uses
  [0,0]). Transparent net corners belong to nobody, but a transparent face can be part of a net.
- **A detail painted across blocks that form one piece** is painted in limb coordinates (`model/art/thigh_upper_world.py`);
  the texture recipe itself (the breastplate's, `sm_chest_art.py`) is in Art direction.
- **A cube's `down` face is mirrored front to back** (the export gives it a negative `uv_size`): a draft painter that
  places down-face texels by model coordinates must flip z there (`tnt_drafts_art.wf`), or a pattern lands mirrored (the
  TNT drum's chamber ends hid behind the barrel for four rounds). Symmetric paint (a centred bore) never shows it.
- **Mirrored parts with fractional widths need mirrored UVs** (rail cannon, 2026-10-26): a 2.5 px face shows 2.5 texels
  counted from its u-start edge (+x on a north face), so the half texel lands on the outer edge of one prong and the inner
  edge of its twin: they "look different sizes" though the cubes mirror exactly. Give the twin its partner's texels with u
  reversed (`rail_mirror_apply.js`), put texel edges on the features you centre (a fractional uv start) and check with a
  mirror test of the faces as Blockbench draws them (`model/art/rail_symcheck.py`), not by reading cube coordinates.

## Drafts and export
- **Design drafts live in the .bbmodel as hidden groups named `proto_*`**. `export-model.mjs` leaves them out (GeckoLib would draw them, hidden or not), but their painted texels stay in the exported texture (draft
  glass in the reserved glow colours raises `make-glowmasks.py`'s counts). A picked draft becomes a game part by
  renaming its group (the engine packs, then the helmet styles: `proto_helm_F_delta` / `G_chief` / `H_eva` / `K_guy` are
  `helm_protector` / `helm_spartan` / `helm_noble` / `helm_robot` since 2026-10-22, plus `helm_hunter` (Samus) the same day, still hidden in Blockbench, design
  sec. 4c): the renderer then shows one of a set of bones (`ENGINE_BONES`, `HelmetStyle.bone()`). Keep them hidden when saving.
- **Draft cube prefixes must be new**: `pkTA_*` was already the tidal backpack's (2026-10-21) when the TNT drafts used it;
  the apply scripts only touch their own groups, but a name-based script would have hit both. Grep the model first.
- **Re-exporting after a model rework touches code numbers** (2026-10-19 export): the tool raise (`RAISE_ARM_X`: the old
  torso leaned 7 deg and carried the tool up; upright, the tool pointed 13.5 deg down) and `SocketAim`'s muzzle were
  re-measured by forward kinematics on the .bbmodel (`.scratch/aimfk.py` recipe: chain T(o) R(rot) T(-o) per bone);
  `extract-view-geo.py` picks cubes by a rule, not by size (the elbow pieces changed size and slipped into the view);
  `make-glowmasks.py`'s counts follow the visor/firebox (check which cube owns new reserved pixels before raising them).
  First diff the bones (pivot, rotation, parent) and keyframes against the last export: the 2026-10-20 export changed
  only cubes and texture, so `SocketAim` and the raise stayed as they were.
