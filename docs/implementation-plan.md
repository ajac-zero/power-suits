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
   code-driven barrel spin (`ToolRotors`), recoil, muzzle burst, heat glowmask (suit + cockpit viewmodel), motor
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
   EM correction (2026-10-13, user): the EM grabs players for everyone (PvP setting for pilots); bosses, suits and very
   big mobs are heavy (pinned, not lifted); then the size rule became the entity tag `powerarmor:em_heavy` (bosses,
   iron golem, ravager, warden). Autopilot `ap_rogue_heavy_*`: the player's suit walking at an EM rogue
   crawled at 1.75 blocks/s while held (5 free); tests `emGrabRules`, `heavyThingsFightTheField` (93).

## Phase 9: engines (decided 2026-10-21, see design sec. 4b; step 1 done 2026-10-21)
1. (DONE 2026-10-21) Built: `SuitEngine` + `EngineItem` (firebox, tidal_engine, star_reactor; recipes), `ENGINE_SLOT` (3)
   in the suit and the menu (engine slot left of the fuel slot, `make-gui.py`), `SuitFuel` per engine (water bucket 300 /
   bottle 100 on land, leaving bucket / glass; the star powers forever), entity: synced ENGINE / ENGINE_UNDER_WATER /
   FREE_POWER, `engineCoveredByWater` (backpack centre 2.2 up, 0.53 back), `floodFirebox` (hiss + steam, burn lost),
   tidal rain + 20-tick dry grace (`tidal_dry_grace`), free power skips every drain and pays tools/seal/jumps, swap = burn
   lost, `packUp` refused while flooded, firebox minds avoid water (path malus), saves (`HasEngineSlot`) and loadouts
   (optional engine: none = firebox) from before the engines get a firebox, menu/gauge/tooltips per engine, exhaust
   smoke only on the firebox; the HUD gauge's grey flame was read from a stale spot of the menu texture since the helmet
   slot (fixed). Placeholder engine icons (the firebox tinted, `make-part-icons.py`). Tests (108): engine slot, menu
   slots, flooding + no pack-up + relight, wading, tidal wet/grace/bucket, own fuels only, reactor, saves/packing, rain
   (own batch: weather + sky access + plains biome). Autopilot `EngineScene` (`runAutopilotEngine`, also ends
   `runAutopilotMech`): `ap_engine_*`, `ap_menu_engine`, `run/autopilot_engine.csv`. The main autopilot's pool scene
   now drives a tidal engine (a firebox would flood there).
   Seen: a tidal suit walking out of a 4-deep pool surfaces its backpack on the 2nd step and dies on the 3rd after
   the 1 s grace: without water in the slot it can't climb out (by the rules: carry a bucket).
2. Art: tidal + reactor backpacks as hidden `proto_*` drafts for the user to pick (DONE 2026-10-21: tidal A tanks,
   reactor J canisters, saved in the .bbmodel as `proto_pack_tidal_A_tanks` / `proto_pack_reactor_J_canisters`, built by
   `model/art/backpack_drafts_art.py` + `backpack_kit.py`; the reactor took three rounds and an inspiration board); then one bone group per engine
   (DONE 2026-10-21: bones `backpack_parts` / `pack_tidal` / `pack_reactor`, the renderer draws the installed engine's
   (`ENGINE_BONES`); glowmasks `tidal` (8 px) + `reactor` (69 px) tinted by `getTidalGlow` / `getReactorGlow`; tidal
   bubbles from the valves only with the backpack under water, reactor aura (translucent violet potion swirls off the
   glass, `ExhaustSmoke`); engine icons drawn lit from the packs (`make-part-icons.py`); `EngineScene` back close-ups
   `ap_engine_tidal_back_*`, `ap_engine_reactor_back_*`). Engine sounds (DONE 2026-10-21, 7f39113): no loop (user: the
   constant minecart rumble drove them mad); now and then a faint one-shot per engine (firebox crackle, tidal bubbles
   under water, reactor hum), `SuitSounds.engineTick`.
3. Docs, README, export.

## Phase 10: helmet styles (decided + DONE 2026-10-22, see design sec. 4c)
Built: `HelmetStyle` (protector / spartan / noble / robot), component `powerarmor:helmet_style`, `SuitHelmetItem` (name +
flavour line), `HelmetTemplateItem` (vanilla's `SmithingTemplateItem` + the flavour line) x4, recipes
`helmet_<style>.json` (smithing_transform), `helmet_standard.json` (no template, iron block: removes the component with
`"!powerarmor:helmet_style"`), `<style>_template.json` (copying), loot tables `chests/<style>_template` added by loot
modifiers (`data/powerarmor/loot_modifiers`, conditions `neoforge:loot_table_id`), the suit's synced HELMET_STYLE, the
renderer's style bones, rogues (1 in 4 styled), the item model `select` on the component, icons
(`make-part-icons.py`: `suit_helmet_<style>`; templates: `tools/make-template-icons.py`), paint on the styles' shells,
bars, band and frame. Tests (111): smithing (each style, keeps the name, restyle, wrong object, not on iron helmets,
iron takes it off, template copies), synced + saved + packed, loot (400 rolls per chest). Autopilot `StyleScene`
(`runAutopilotStyles`): `ap_style_*` (each helmet from behind and the front, painted in its colours), `ap_menu_smithing`,
`run/autopilot_style.csv`.
Hunter style (DONE 2026-10-22, branch `helmet-samus`): drafted as `proto_helm_U_samus_A` (model/art/helmet_drafts2_art.py
`samus()`; drafts can carry cube rotations now, unused since the hoses went straight), bone `helm_hunter`, `HelmetStyle.HUNTER`
(last: synced ordinals of the others unchanged), template in desert pyramids + jungle temples, slime ball; glowmask
visor 498 -> 527 (its glass); the scene films six suits.

## Phase 11: more power tools (queued 2026-10-23, one at a time, model first)
Each tool: hidden `proto_*` drafts in Blockbench -> user picks -> game part (bone, code, icon, recipe, autopilot scene, tests).
- [x] Flamethrower (DONE 2026-10-25): a stream of fire (FlameJet), fuel only (6/tick), sets blocks alight (config), melts ice/snow,
  lights campfires/candles, TNT, cooks dropped food, dead under water; pilot light glowmask; mind = chaingun's job up close,
  rogues 1 in 5; 7 GameTests (118); `runAutopilotFlame` (FlameScene). Model: draft H -> bone `flamethrower` (design sec. 3).
- [ ] Hydraulic ram (heavy punch, knockback, breaks blocks in a line)
- [ ] Riot plate (blocks damage in front while held)
- [ ] TNT launcher (was "grenade launcher"), **decided 2026-10-25 (user)**: a new suit **ammo slot** in the menu (generic:
  later tools may draw from it too) holding the TNT; each shot lobs a primed TNT block in an arc that **explodes on hit**
  as real TNT (strength 4, breaks blocks; follows the TNT gamerule and claims, config switch); tap = one shot then ~1 s
  reload, holding keeps firing once reloaded; the machine mind never fires it and rogues never carry it. Model: a revolver
  drum, Team Fortress 2's grenade launcher as the inspiration. Round 1 drafts (hidden `proto_tnt_*`, rebuilt by
  `model/art/tnt_drafts_art.py`): A demo (solid drum), B cage (open cylinder, four TNT rounds), C loaded (rounds in the
  drum's corners), D wheel (drum magazine on the side); sheet `.scratch/tnt/round1_v2.jpg`.
- [ ] Water cannon (extinguishes, pushes mobs, waters crops)

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
