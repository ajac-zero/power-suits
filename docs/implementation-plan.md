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
from `model/anim/a_power.js`. Tests: 9 new GameTests (17 total); autopilot fuel scenario
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
2. Rivet gun (built 2026-10-13, then replaced by the chaingun, 2b): new `rivet_gun` bone (receiver, drum, gauge, barrel + collars, nose, piston, vent;
   `muzzle` locator, flash block removed), `rivet_shot` (`a_rivet_shot.js`), `RivetGunItem` (nugget ammo, arc aiming),
   `Rivet` entity (sticks, cools, drops a nugget), `RivetRenderer`, vent opened by the raise (`RivetGunParts`), muzzle
   burst (server for others, client for the pilot), raise clunk for every tool, HUD rivet count, item alias
   `plasma_cannon` -> `rivet_gun`. Tests: nugget use, creative, sticking + dropping (51).
2b. Chaingun (DONE 2026-10-13): `chaingun` bone (motor housing + `chaingun_barrels`: 2 clamp plates, 4 barrels, axle;
   `muzzle` locator), `ChaingunItem` as a continuous tool (spin-up timing, nugget per bullet, overheat config), `Bullet`
   tracer (immunity bypass, no_knockback tag + own push), `BulletRenderer`, synced TOOL_SPIN/TOOL_HEAT/TOOL_SHOTS ->
   code-driven barrel spin (`ChaingunParts`), recoil, muzzle burst, heat glowmask (suit + cockpit viewmodel), motor
   whine loop with spin pitch (`PowerTool.runSoundPitch`), HUD bullets + heat bar; `rivet_shot` -> arm-only
   `tool_recoil` (EM push); item aliases plasma_cannon/rivet_gun -> chaingun. Tests: tap, spin-up/coast, nuggets,
   creative, immunity bypass, overheat (52).
3. EM projector redstone restyle (DONE 2026-10-13): repaint + rework (`model/art/em_redstone_art.py`: housing, redstone
   blue forearm block with a redstone-vented collar, redstone lens, 4 short prongs with copper windings and redstone tips), `em_core`/`em_arc` glowmasks (suit + cockpit
   viewmodel, levels in `PowerArmorEntity.tickLights`), redstone beam + sparks (`EmBeamEffects`), release puff of
   redstone dust, `PowerTool.muzzleInsetPixels` (beam starts at the shorter tool's tips), autopilot close-ups
   `ap_em_close_{idle,pig}`.

## Phase 8: suit AI (decided 2026-10-13, see design sec. 8)
Each step: GameTests for the logic, an autopilot scene with numbers in a CSV, then commit.
1. (DONE 2026-10-13) Mind state (`SuitMind` friendly / rogue, synced for the visor), owner (last pilot / deployer)
   and home (exit block / deploy spot), saved; the mind drives only when powered and pilotless (`isAiDriven`), and
   a powered suit always burns fuel; visor amber when piloted, green friendly, red rogue, no hit flash; tool
   *operator* refactor (`toolOperator`, `blockBreaker` FakePlayer with the owner's UUID, `setAiAim`/`setAiTrigger`);
   idle job (`SuitGoals`: walk home at a jog, look at players/around); rogues can't be boarded. Old fuelled suits in
   the user's world wake up idle with no home (until a pilot gets out). Tests (73): owner/home, saving + old saves,
   walk home speed + arrival, unpowered stays, boarding stops the mind, rogue, visor, the mind drills as its owner,
   the mind's chaingun needs ammo. Autopilot (mech run): `ap_ai_home_*`, `ap_ai_drill_*`, `ap_ai_visor`,
   `run/autopilot_mind.csv`.
2. (DONE 2026-10-13) Hidden nugget store (`NUGGETS` synced, right-click with nuggets loads the stack up to
   `nugget_store_max` 640, menu line "Ammo N", kept in `SuitLoadout.nuggets`, dropped by a wreck; a pilot's chaingun
   uses their inventory first, then the store). Chaingun job (`SuitGoals`): `DefendOwner` target goal
   (`SuitTargets.pick`: the owner's attacker, the owner's victim, the suit's attacker, then the nearest monster within
   `guard_range` 16 of the owner), `ChaingunAttack` (aims with `setAiAim`, holds the trigger while
   `SuitTargets.lineOfFireClear`, walks for a clear shot, punches when the store is empty), `FollowOwner` (7 / 4 / sprint
   past 16, no teleport). Tests (81): loading, store survives save/pack/wreck, pilot pays pocket-first, the mind shoots
   a husk near its owner, holds fire with the owner in the way, follows, punches when empty, target rules. Autopilot
   (mech run): `ap_ai_menu_ammo`, `ap_ai_follow_*`, `ap_ai_defend_*`, `ap_ai_fists_*`.
3. (DONE 2026-10-13) EM guard job (`SuitGoals.EmGuard`, LOOK only): monster nearest home within `em_guard_radius`,
   in reach and sight, standing; aim at it, hold until the beam has it (the mind's beam only grabs its own target),
   aim up for 12 ticks (creepers: up and away), let go: `EmProjectorItem.release` throws at `em_guard_launch` for the
   mind. Tests (84): lifts + flings a husk, leaves cows/villagers alone, throws creepers away from home. Autopilot
   (mech run): `ap_ai_em_*` (2 zombies + a skeleton flung ~30 up and killed, the creeper thrown 25 blocks away).
4. (DONE 2026-10-13) Drill + chainsaw workers (`SuitGoals.Worker`: pick a block, walk until in reach and the tool would
   hit something it may work on, aim + trigger until gone, skip what it can't reach for a minute; `DrillWorker`,
   `SawWorker`; `SuitWork` rules; the drill/chainsaw refuse other blocks for the mind). Hitbox 3.0 -> 2.95 high (the
   pathfinder wants floor(height + 1) free blocks: 4 at 3.0, so the 3-high hall was unwalkable). Tests (86): the hall
   (floor, ceiling, lava walls, hole column kept), trees felled but not cabins. Autopilot: `WorkScene` (mech run, last):
   `ap_work_drill_*`, `ap_work_saw_*`, `run/autopilot_work.csv`: 230 of 287 rock blocks in 27 s, lava walls and hole
   column intact, two allays (coal, raw iron) delivered 17 + 13 to the player (they used the liked-player memory, not a
   note block), ~210 items lying on the floor (despawn after 5 min), 3 oaks felled in 9 s.
5. (DONE 2026-10-13) Rogue suits: `RogueSpawner` (overworld, a saved day countdown, `rogue_spawn_chance`, one at a
   time, not on peaceful / with mob spawning off, 48-64 blocks from a random player on dry solid ground with room);
   rogue mind: no fuel or nugget use, targets the nearest player it sees (the suit if they pilot one), else any mob
   (`SuitTargets.pickRogue`), chaingun fires at will, EM beam catches players too (`EmProjectorItem.canHold`) and hurls
   them, fists for what the beam can't hold, roams when idle; can't be boarded; drops only its tool; lightning reboots
   it friendly (nobody's, no home, 1600 fuel); leaves after `rogue_lifetime` once no player is within 64; name "Rogue
   Power Armor". Tests (91): runs on nothing + shoots a cow, tool-only drop, lightning reboot + claim, leaves only
   unwatched (own batch), friendly chaingun shoots rogues. Autopilot: `RogueScene` (`ap_rogue_*`,
   `run/autopilot_rogue.csv`): spawner placed it 57 blocks out; shoot-out with the player's suit; lightning reboot,
   claimed; an EM rogue lifts and throws the player 4 blocks up, killed by two friendly suits, drops its projector.

## Art (Blockbench, `model/power_armor.bbmodel`)
Use the `blockbench-mcp` skill (`.agents/skills/blockbench-mcp/`), checkpoint
before every change, save it with every mod commit that changes the art (user's rule), re-export with `node tools/export-model.mjs`.
- Phase 1 (done): `shutdown`, `powered_off`, `startup` (`a_power.js`, planted feet IK).
- Phase 2 (done): `socket` coupling bone under `right_forearm`, `plasma_shot` overlay; the item model is
  extracted from the suit geo. Walk/run's stabiliser holds the forearm, so it steadies any tool.
- Phase 3 (done): `left_hand` grip bone, `hold_item` / `use_item` (`a_hand.js`); grenade removed (`throw` unused).
- Phase 4 (done): `em_projector` bone (collar, 2 coils, core, 4 prongs; texture at y 98-107, x 166-225).
- Phase 5 (done): `drill` (spinning `drill_bit`, diamond bit) and `chainsaw` (`chainsaw_chain`) bones under
  `right_forearm` (now `left_forearm`, see below); nets painted by `model/art/mining_tools_*`; `drill_run` / `chainsaw_run` (`a_tools.js`).
- Arm swap (2026-10-13, done): hand on the right, tools on the left. The whole model was mirrored across x = 0
  (cubes, rest poses, all keyframes, left/right names; checkpoint "before arm swap mirror"), verified exact against
  the live bone matrices of every animation. Phase notes above use the old side names. Specs stay authored for the
  old rig: `model/anim/kit.json` makes `bb.mjs` mirror them on bake.
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
