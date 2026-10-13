# Implementation plan

Read `docs/design.md` first (the spec) and `AGENTS.md` (how to work here). Each phase must end with:
build passes, all GameTests pass, an autopilot run looks right (screenshots / CSV), README + design
docs updated, a git commit, and a short explanation for the user (a first-time modder: teach the
concepts as you go).

Confirm the **OPEN** items in `docs/design.md` with the user before building the parts they affect.

## Phase 1: fuel, suit menu, shutdown, smoke, gauge (DONE 2026-10-12)
Built as specified in `docs/design.md` sections 4-6, with the user's decisions: furnace-style fuel
(item by item, coal shortest, blaze rod longest, no tank) and sneak + right-click for the menu.
Where things are: `SuitFuel` (fuel table), `PowerArmorEntity` (`tickFuel`, power flag + transitions,
inventory, save/load, `packUp`, animation states), `ExhaustSmoke`, `menu/PowerArmorMenu`,
`client/PowerArmorScreen` + `tools/make-gui.py`, `client/FuelGauge`, `item/SuitLoadout` +
`ModDataComponents`, `item/PowerTool` (empty marker). Animations `shutdown` / `powered_off` / `startup`
from `starcraft_marine_anim/a_power.js`. Tests: 9 new GameTests (17 total); autopilot fuel scenario
(`ap_fuel_*`, `ap_menu_*`, `run/autopilot_fuel.csv`).
Not done: hoppers into the fuel slot; exhaust pipe bone positions are computed from the entity transform
(`ExhaustSmoke.PIPE_*`), not tracked GeckoLib bones (ignores the torso lean, looks right in game).

## Phase 2: socket + plasma cannon (DONE 2026-10-12)
Built: `PowerTool` (energyPerUse, cooldownTicks, suitBone, use), `PlasmaCannonItem` (muzzle position from
measured bone offsets + torso lean, aim point by ray-tracing the pilot's view, GeckoLib item), `PlasmaBolt`
(+ `PlasmaBoltRenderer`, `powerarmor:plasma` damage type), `ToolTriggerPayload` + `client/ToolInput`
(attack intercepted, state sent on change), suit `tickTool` / `consumeEnergy`, renderer tool-bone
visibility, `socket` coupling in the model, `plasma_shot` animation (`a_plasma_shot.js`),
`tools/extract-tool-geo.py`, item display transforms, recipe. Tests: 7 new GameTests (24 total); autopilot
cannon range + item views (`ap_cannon_*`, `ap_item_*`, `run/autopilot_cannon.csv`).
Not done: muzzle position is computed from constants, not the animated bone (fine at the bolt's speed);
no glowmask (emissive) texture yet; fuel cost per shot to be balanced (user: later).
For the next tools: add the tool's bone to the suit model (child of `left_forearm` since the 2026-10-13 arm swap), its name to
`PowerArmorRenderer.TOOL_BONES` and `extract-tool-geo.py`, implement `PowerTool`, register the item + client
extension, add a `models/item/<tool>.json`.

## Phase 3: left hand holds and uses items (DONE 2026-10-12)
Decision: the left hand may not attack mobs, even with a sword. Built: `left_hand` grip bone (grenade
removed), `hold_item` / `use_item` left-arm overlays (`a_hand.js`, arm IK; an at-the-chin eat pose was
dropped because the forearm covered the visor), a third GeckoLib controller `left_hand` (hold while the
pilot holds something and the suit can operate; `use_item` on each swing), `client/HeldItemLayer`
(third person), `client/FirstPersonLeftHand` (cockpit: vanilla first-person transforms mirrored to the left,
incl. swing and eat bob), `PilotHands` (cancel AttackEntity / LeftClickBlock / BreakEvent for pilots;
cancel right-click interactions while the suit can't operate; +1.5 block/entity reach as a transient
attribute modifier on mount). Tests: 4 new GameTests (28 total); autopilot hand scenario (`ap_hand_*`).
Not done: bows/crossbows/shields use the plain carry pose; `throw` animation still keys the removed grenade.

## Phase 4: EM projector (DONE 2026-10-12)
Decisions: items + mobs only, socket only. Built: `PowerTool` continuous lifecycle (`isContinuous`, `holdTick`,
`release(interrupted)`, `serverTick`) driven by the suit's `tickTool` (releases on trigger up, power loss, pilot
leaving, tool swap, suit removal), per-suit tool state (`toolData`), `tryUseEnergy`, synced `BEAM_TARGET`;
`EmProjectorItem` (grab filter, pull/hold steering, item collection via `playerTouch`, throw, tap shove, slam
damage `powerarmor:em_slam`), `SocketAim` (shared muzzle / aim / entity-in-view), `EmBeamEffects` (twisting
violet particle beam), `em_projector` bone in the suit model (painted coils/core/prongs), item model, recipe.
Also fixed: torso twist used an unwrapped yaw difference (twisted the wrong way after yaw passed +-180).
Tests: 6 new GameTests (34 total); autopilot EM scenario (`ap_em_*`, `run/autopilot_em.csv`).

## Phase 5: 3x3 drill and chainsaw (DONE 2026-10-12)
Decisions: drill = diamond tier, pickaxe + shovel blocks, visible diamond tip; axe blocks are the chainsaw's.
Built: `ToolMining` (crosshair block within the pilot's reach, `breakBlock` = vanilla `destroyBlock` with a
virtual diamond tool: own `BreakEvent` post, `playerWillDestroy`, `onDestroyedByPlayer`, `dropResources`
(loot + XP), break effect sent to the pilot too; per-suit `Progress` with crack overlay on every target via
`destroyBlockProgress` under negative ids), `DrillItem` (3x3 in the hit face's plane, hardest-block speed,
4-tick pause, 60 fuel per block), `ChainsawItem` (axe blocks + leaves, natural-tree BFS above the cut, felling
queue in `serverTick`, 40 fuel per log), `PilotHands.noBreaking` exemption (`ToolMining.isToolBreak()`),
`PowerTool.runAnimation()` / `keepsRunning()` + synced `TOOL_RUNNING` + a 4th GeckoLib controller `tool`;
art: `drill` (collar, hub, two twisted steel cone steps, diamond bit + tip in a spinning `drill_bit`) and
`chainsaw` (collar, blue motor with grille + hazard stripe, exhaust, guide bar, chain in `chainsaw_chain`),
`drill_run` / `chainsaw_run` loops (`a_tools.js`), item models, recipes, lang.
Tests: 8 new GameTests (42 total): 3x3 + drops + fuel, obsidian/bedrock/wood, drill skids on wood, BreakEvent
veto + hand still can't break, tree felling, log cabin spared, chainsaw skids on stone, nothing on foot.
Autopilot: drill into a rock face with ores and fell a feature-grown oak (`ap_drill_*`, `ap_saw_*`,
`run/autopilot_mine.csv`). Not done: tool sounds (Phase 6), leaves of felled trees decay at vanilla speed,
fuel costs unbalanced (user: later).

## Phase 6: polish (DONE 2026-10-12)
Built: `PowerArmorConfig` (COMMON `ModConfigSpec`, `config/powerarmor-common.toml`, hot-reloaded; fuel energies,
drain rates, jump, low-fuel level, cannon energy/cooldown/damage, EM energies/range/slam, drill energy/pause,
chainsaw energy/max logs/felling speed) read at the moment of use everywhere; in-game config screen
(`client/PowerArmorClient`, a client-only `@Mod` with `ConfigurationScreen`, labels + tooltips in lang);
balance pass (cannon 300 -> 120 per shot, EM 2 -> 1 per tick and 200 -> 80 per throw, drill 60 -> 20 per
block, chainsaw 40 -> 15 per log; fuels and drain unchanged); `ModSounds` + `sounds.json` (engine, drill,
chainsaw: re-pitched vanilla loops with subtitles) and `client/SuitSounds` (tickable loops that follow the
suit, engine pitch/volume by `engineLoad()`, fade in/out); `PowerTool.runSound()`; fuel tooltips on coal,
charcoal, lava and blaze rods (`client/FuelTooltip`); walking-time display follows the configured drain.
Recipes exist for the suit and all 4 tools. Tests: config-aware fuel assertions, `runningToolIsSynced...`
(43 total). Autopilot logs the active loops (`loops` column of `autopilot_mine.csv`) and screenshots the
config screen (`ap_config`). Not done: sounds are vanilla re-pitches chosen without listening; real recordings
would replace them in `sounds.json` only.

## Phase 7: dieselpunk identity (decided 2026-10-13, see design sec. 0)
1. Identity pass (DONE 2026-10-13): `chest_core` -> 8x5 `firebox` cube (steel frame), whole visor as glass, ember vent slits; glowing pixels
   painted in reserved dark colours, `tools/make-glowmasks.py` -> `power_armor_glow_fire/visor.png`; `SuitGlowLayer`
   redraws the model with each mask in `RenderType.eyes`, tinted per frame from `PowerArmorEntity.tickLights`; item
   icon patched; autopilot night scene `ap_lights_*` (piloted, tool, hit, parked, shutdown, off).
2. Rivet gun (DONE 2026-10-13): new `rivet_gun` bone (receiver, drum, gauge, barrel + collars, nose, piston, vent;
   `muzzle` locator, flash block removed), `rivet_shot` (`a_rivet_shot.js`), `RivetGunItem` (nugget ammo, arc aiming),
   `Rivet` entity (sticks, cools, drops a nugget), `RivetRenderer`, vent opened by the raise (`RivetGunParts`), muzzle
   burst (server for others, client for the pilot), raise clunk for every tool, HUD rivet count, item alias
   `plasma_cannon` -> `rivet_gun`. Tests: nugget use, creative, sticking + dropping (51).
3. EM projector redstone restyle: model/texture, red-dust beam + sparks.

## Art (Blockbench, `~/Developer/Models/starcraft_marine.bbmodel`)
Use the `blockbench-mcp` skill (`~/Developer/Models/.agents/skills/blockbench-mcp/`), checkpoint
before every change, save it with every mod commit that changes the art (user's rule), re-export with `node tools/export-model.mjs`.
- Phase 1 (done): `shutdown`, `powered_off`, `startup` (`a_power.js`, planted feet IK).
- Phase 2 (done): `socket` coupling bone under `right_forearm`, `plasma_shot` overlay; the item model is
  extracted from the suit geo. Walk/run's stabiliser holds the forearm, so it steadies any tool.
- Phase 3 (done): `left_hand` grip bone, `hold_item` / `use_item` (`a_hand.js`); grenade removed (`throw` unused).
- Phase 4 (done): `em_projector` bone (collar, 2 coils, core, 4 prongs; texture at y 98-107, x 166-225).
- Phase 5 (done): `drill` (spinning `drill_bit`, diamond bit) and `chainsaw` (`chainsaw_chain`) bones under
  `right_forearm` (now `left_forearm`, see below); nets painted by `~/Developer/Models/starcraft_marine_art/mining_tools_*`; `drill_run` / `chainsaw_run` (`a_tools.js`).
- Arm swap (2026-10-13, done): hand on the right, tools on the left. The whole model was mirrored across x = 0
  (cubes, rest poses, all keyframes, left/right names; checkpoint "before arm swap mirror"), verified exact against
  the live bone matrices of every animation. Phase notes above use the old side names. Specs stay authored for the
  old rig: `starcraft_marine_anim/kit.json` makes `bb.mjs` mirror them on bake.
- Tool arm hip carry + shoulder fix (2026-10-13, done): the tool arm's rest pose went from raised forward (upper arm
  46 deg, elbow 38 deg: always looked extended) to a hip carry beside the body (upper arm 18 deg, elbow 62 deg, tool
  still pointing forward); walk/run re-baked (their cannon stabiliser depends on the rest pose; speeds unchanged),
  the other animations are offsets on top of the rest pose and follow. `SocketAim` muzzle re-measured. The
  `shoulder_joint` blocks moved down 1.25 px: the pads' inner edge rolled onto them in walk/run and they poked up
  through the pad tops (up to 0.85 px in run, now 0 in every animation).
- Controls swap (2026-10-13, done): the hand is fully normal (left click attacks/mines at normal speed, right click
  uses the item) and the socket tool takes right click when the hand passes, like a shield (`ToolInput`); the
  player's off-hand item is ignored while piloting; the tool arm raises to aim while used (code-driven blend on
  `left_arm`/`left_forearm`, cockpit viewmodel too); `SocketAim` uses the raised muzzle. Tests: hand fights/mines,
  unpowered hand can't, off hand blocked, raise synced (47 total).
