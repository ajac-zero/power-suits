# Power Armor: lessons from the game code

Each of these cost a debugging round on this mod (moved here from `AGENTS.md` on 2026-10-26; the rules that follow from
them are in `AGENTS.md`). Read the section that matches what you're changing. *(26.3)* marks what changed with the port
to Minecraft 26.3 / GeckoLib 5. Add a new lesson to the right section when something costs a round.

## Entity state, saving and synced data
- **Saved attributes outlive code changes.** Entities store attribute base values in NBT; tuning lives in
  `BASE_STATS` and is re-applied in `readAdditionalSaveData`. Do the same for any new persisted tuning.
- *(26.3)* **Entities save through `ValueInput` / `ValueOutput`** (`getIntOr`, `read(name, codec)`...), saved data through a
  codec (`SavedDataType`); keep the old keys and formats (UUID / BlockPos codecs write the same int arrays) so 1.21.1
  saves still load.
- **Synced entity data must be refreshed wherever the server field changes**, not only in `tick()`: loading,
  `applyLoadout`, test setters (a GameTest caught a stale `BURN_TOTAL` after load). Since 2026-10-26 the code does it: a
  server field with a synced copy has one private setter that writes both (`SuitPower.setBurnState`), and the slot copies
  are re-checked every tick (`checkSlotCopies`: a stack shrunk in place never tells its container). Keep new pairs that way.
- *(26.3)* **No `ItemStack` in static initializers** ("Components not bound yet" crashed the client at startup):
  create them on first use.
- **Balance values live in `PowerArmorConfig`** (COMMON config, hot-reloaded): read `.get()` where used, never
  cache in static fields; GameTests must compare against the config, not literal numbers (the dev `run/config`
  file is shared by the client, the GameTest server and your own edits).
- **No deprecated Minecraft calls** (cleared 2026-10-26; check with `-Xlint:deprecation`): damage with
  `hurtServer(level, ...)` (not `hurt`), water with `isEyeInFluidType(NeoForgeMod.WATER_TYPE.value())`, fluid pushing by
  overriding `isPushedByFluid(FluidType)`, `level.isLoaded(pos)`, `EventHooks.finalizeMobSpawn`; item tooltips come from
  `item/ModTooltip` (one NeoForge tooltip appender registered in `PowerArmor`), not `Item.appendHoverText`.

## Client and server
- **Shared code never touches client classes** (`Minecraft`, cameras, screens): they don't exist on a dedicated server, which
  crashes the moment such a line runs (the compiler doesn't notice). Ask through a hook the client fills in at startup
  (`entity/ClientView`, `SocketAim.visualMuzzle`, set in `client/ClientSetup`).
- **Client-side caches must die with the world** (review 2026-10-26): leaving a world silences every sound without marking
  it stopped (`SoundEngine.stopAll`), so `SuitSounds` kept loops (each holding its suit and the old level) forever. Check
  `SoundManager.isActive`, clear static lists on `ClientPlayerNetworkEvent.LoggingOut`, and don't trust game time across worlds.
- **Client particles linger** (dust ~1-2 s): ones that drift next to the cockpit camera become huge blobs.
  Don't spawn them within ~1.3 blocks of the pilot's eye.
- **Looping sounds are client side** (`SuitSounds`): the server only syncs state (powered, piloted, `TOOL_RUNNING`);
  one-shots still use `playSound` on the server. Sounds are re-pitched vanilla events in `sounds.json`.

## Riding and movement
- **Ridden movement is client-authoritative.** `travel`/`tickRidden` run on the pilot's client for a
  piloted suit and on the server for an empty one. Gate movement on synced state (e.g. powered).
- **Speed constant is measured, not derived**: 43.15 blocks/s per movement-speed point on 26.3 (42.14 on 1.21.1:
  the same attribute went 2.4% further after the port). Re-measure with the autopilot's `walk speed` log line.
- *(26.3)* **The server doesn't get a passenger's `xxa` / `zza` any more** (the input packet only fills
  `ServerPlayer.getLastClientInput()`, keys pressed): read the pilot's WASD on the server through
  `SuitDriving.pilotInput` (smashing blocks and the fuel drain both broke silently on 0 input).
- **Vanilla keeps sprinting after the sprint key is released**; call `setSprinting(false)` in scripts.
- **Smoothing on top of Minecraft's interpolation**: entities are drawn at `lerp(partial, yOld, y)`, so an offset that
  cancels a jump must only change at the END of the tick (`StepSmoothing`): also shifting its start value dropped the
  model a block below the camera for a frame on every step. `stepsDoNotJerkTheModelAgainstTheCamera` checks this frame
  by frame; in game, `ViewportEvent.ComputeCameraAngles` still holds last frame's camera position, so measure with
  `player.getEyePosition(partial)`.
- **Wrap yaw differences** (`Mth.wrapDegrees`): yaws are unbounded (720 after two turns) and GeckoLib's
  `netHeadYaw` is a raw subtraction; unwrapped, the torso twisted 90 deg the wrong way (right pad in the cockpit).
- **The pilot's head pokes out of the suit's hitbox** (eye 0.55 forward, player width 0.6): pressed against a wall, vanilla
  thought the pilot was suffocating (hull damage every 10 ticks, block texture over the screen). `PilotProtection` drops
  IN_WALL unless the suit itself is in a wall; `PilotView.cockpitOverlays` cancels the overlay.
- **Blocks broken by walking must be broken ahead of the suit** (`SuitSmash`): ridden movement is client-side, so the client
  hits a pane before the server could break it; the server clears a box reaching 0.6 / 1.1 blocks ahead along the input.

## Damage, fire and the seal
- **`damageSources().source(key, a, b)` takes (projectile, shooter)** despite its parameter names
  (vanilla: `source(FIREBALL, fireball, thrower)`). Swapped, kills credit the projectile instead of the pilot.
- **The pilot's own tick re-ignites them** (their box touches a fire block at the suit's feet): clearing the pilot's fire in
  the suit's tick left the burning flag on every tick. Move pilot state at the end of their tick (`PlayerTickEvent.Post`,
  `SuitSeal.afterPlayerTick`) and cancel client overlays as a backstop.
- **NeoForge renames poison damage**: the poison effect hurts with `neoforge:poison` (tag `neoforge:is_poison`), not vanilla's
  `magic`; a check for `DamageTypes.MAGIC` alone missed it. Match damage by tags where NeoForge has one.
- **Fire is tracked on both sides**: the pilot's client moves the suit, touches fire blocks itself and draws flames from its
  own fire ticks, while the server's baseTick (early in the tick) sets the "on fire" flag other clients see, and a piloted
  suit also touches fire between ticks (move packets). Putting a running suit out needs `clearFire()` at the start and end
  of the tick on both sides (`PowerArmorEntity.putOutFire`); log the client's `isOnFire()`, not only the server's.
- **Damage you cancel never reaches vanilla's hit immunity**: a fire block "hits" every tick, so paying fuel per absorbed hit
  needs its own half-second cooldown (`SuitSeal.SEAL_COOLDOWN_TICKS`).

## The machine mind (AI)
- **A powered suit always runs (the machine mind, Phase 8)**: with fuel and nobody inside it burns the idle rate, looks at
  players and walks back to its home; anything that leaves fuelled suits around (tests, autopilot scenes) gets that.
  To park one, take the fuel out. Its state is only set on its first tick: tests wait 2 ticks before boarding/aiming.
- **Vanilla mobs move at speed squared**: `MoveControl` passes the speed as the forward input (zza) and `moveRelative`
  applies it again (a 0.119 suit crawled at 0.6 blocks/s). `PowerArmorEntity.travel` normalises the mind's input, so
  a navigation speed of 1.0 = the pilot's walk. Measure position deltas, not `getDeltaMovement()` (it already holds the
  next tick's drag: it read 2.9 for a 5.2 blocks/s walk).
- **`Mob.serverAiStep` is final**: gate the mind with each goal's `canUse`/`canContinueToUse` + `updateControlFlags`.
  `LookControl` zeroes the pitch and eases the head to the body every tick unless asked to look; `customServerAiStep`
  runs after the goals and before the look control, so the mind's aim (`SuitBrain.setAim`) is applied there.
- **Paths of wide mobs end short**: vanilla's path for the 1.4-wide suit stopped 1.7 blocks from home (node offsets);
  `ReturnHome` walks the last stretch with `MoveControl.setWantedPosition`. The pathfinder returns null for a mob in
  the air (just spawned): wait for `onGround()`.
- **Vanilla's pathfinder wants floor(height + 1) free blocks above each step** (and floor(width + 1) across): the suit
  at 3.0 high couldn't path through its own 3-high drill hall; its hitbox is 2.95 now.
- **The mind's legs use the pilot's leg rule** (`SuitDriving.turnLegs`): vanilla's mob body rotation let the head get past the
  torso's 90 deg twist (drilled while facing away) and each client derived the unsynced body yaw from tiny moves (the
  torso swung side to side). Judge facing from the client: the mind CSV logs clientHead/clientBody/twist/aimErr.
- **Minecraft doesn't move no-AI mobs by velocity** (`isEffectiveAi` false skips `travel`), so the EM projector
  ignores them (they're map statues anyway). Use normal mobs in GameTests that move things; give them room
  before the test pad's barrier walls (a wall hit zeroes the velocity you meant to assert).

## GeckoLib 5 and rendering
- *(26.3)* **GeckoLib 5 advances an animation by (time passed x speed) each frame**, so its speed may change any time
  (GeckoLib 4 scaled the whole elapsed time and the animation jumped). Set it in the state handler
  (`state.controller().setAnimationSpeed`). Controllers added later override earlier ones per bone.
- *(26.3)* **GeckoLib 5 renders from a snapshot**: `addRenderData` (renderer or layer) copies what's needed from the
  entity into the render state (DataTickets, `SuitRenderData`); the model is posed later in
  `adjustModelBonesForRender`, from that state only. Each frame every bone starts from a fresh `BoneSnapshot` holding
  the animation's offset from its rest pose, so add to it (offsets no longer pile up between frames). Drawing is only
  queued ("submitted"); anything a renderer changes on itself (a field) is gone by then: put it in the render state.
- *(26.3)* **GeckoLib 5 assets** live in `assets/<mod>/geckolib/models/<id>.geo.json` and
  `geckolib/animations/<id>.animation.json` (id `entity/power_armor`), not `geo/` + `animations/`.
- *(26.3)* **Render type names flipped**: `RenderTypes.entityCutout` is the old *NoCull* (GeckoLib's default, both faces),
  `entityCutoutCull` culls. `LightTexture.FULL_BRIGHT` is `LightCoordsUtil.FULL_BRIGHT`.
- *(26.3)* **`LivingEntityRenderState` is a `GeoRenderState`** only through GeckoLib's interface injection: the build
  pulls it in with `interfaceInjectionData "com.geckolib:..."` (without it nothing compiles).
- **GeckoLib draws both faces** (`entityCutout` since 26.3, was `entityCutoutNoCull`): coplanar same-direction faces flicker
  (z-fighting); fix in Blockbench with `inflate` 0.1 (keeps box UVs). Scan with
  `.agents/skills/blockbench-mcp/scripts/zfight.mjs`. The cockpit uses back-face culling.
- **Animation state flicker** comes from states toggling every few ticks (e.g. run/fall on slopes); check the
  `anim` column of the terrain CSV for rapid switches.
- **GeckoLib won't restart a triggered animation that is still playing**: per-use animations must be shorter
  than the tool's cooldown (`tool_recoil` 0.5 s vs the EM push's cooldown); rapid fire (the chaingun) kicks in code from a synced shot counter instead.
- **(GeckoLib 4 only) Never add to an unkeyed bone in `setCustomAnimations`**: GeckoLib 4 only reset bones the playing
  animation keyed, so a per-frame `+=` piled up and the bone flickered. GeckoLib 5's per-frame snapshots fixed that; code
  offsets still go on bones no animation keys (the chaingun barrels: `ToolRotors`), set every frame.
- *(26.3)* **Code offsets on GeckoLib 5 bones have Blockbench's signs on all three axes** (the Bedrock export negates X/Y,
  GeckoLib negates them back for the geo and the keyframes): `rotX += bbX`. The GeckoLib 4 rule (`rotX -= bbX`, Y too)
  survived the port and for six days the torso twisted away from the view, leaned back when the pilot looked down
  (third person: nearly always) and the tool "raise" swung the arm back. Check any code pose with `runAutopilotPose`
  (fixed side + top cameras, marker blocks along the view yaws), not with one action shot.
- **GeckoLib `thenPlay` uses the JSON's loop setting**: `thenPlay("electrified")` (a `"loop": true` animation) convulsed
  forever and never reached the chained slump. Chain after a looping animation with `then(name, LoopType.PLAY_ONCE)`.

## Cockpit and viewmodel
- **The cockpit shows a viewmodel, not the suit's arms.** Looking at the external model from inside never lined up
  (hands raised where yours aren't, tools hidden behind their base, pads clipping), so in first person only the
  suit's legs are drawn and `CockpitHands` draws the tool + gauntlet in the hand pass (`geo/view`, rebuilt by
  `tools/extract-view-geo.py` after every export). Bullets/EM beam start at its on-screen muzzle for the pilot only
  (`SocketAim.visualMuzzle`, `Bullet.visualOffset`); the server still uses the suit's muzzle.
- **Viewmodel tools aim at the crosshair**: a tool tilted up (the first poses: 20-40 degrees) makes its shots/beam leave at a
  different angle than its barrels point. Solve raised poses with `tools/viewmodel-fit.py aim x roll scale 0 0 -10 tool`
  (axis through the crosshair 10 blocks out, cut end hidden); keep parts in the screen corners (the hotbar hides the
  middle of the bottom edge). Preview with the view tuner by copying the raised pose into the rest slot.
- **Viewmodel parts must not show their cut ends**: the tool's socket face stays below the bottom screen edge so it
  reaches in from the corner (floating otherwise). Check/fit poses with `tools/viewmodel-fit.py` before tuning visually.
- **The cockpit's main hand is vanilla's** (design decision): held items are left to vanilla's first-person renderer
  (same place and motions as on foot, no arm, as in vanilla); only an empty hand is ours, the gauntlet laid along
  vanilla's empty-arm pose (`CockpitHands.gauntletTransform`). Don't give items a custom cockpit pose. The view tuner
  shoots `view_vanilla_*` on foot next to `view_*` in the suit to compare.
- **(Superseded by the cockpit viewmodel)** The suit's arm is too short to show an item in the cockpit view without blocking it (a hand target in
  view folded the forearm across the visor). Third person uses the 3D hand; first person draws the item
  vanilla-style (`FirstPersonLeftHand`). Check new poses from the cockpit camera, not only from outside.
- **F1 (hide HUD, `mc.gui.hud.isHidden()` since 26.x) also skips the hand pass**: screenshots with the GUI hidden show no viewmodel. The autopilots keep
  the GUI on. Tune the viewmodel live with `tools/view-tuner.sh start|shoot|stop` (edit `run/view/tuning.json`).

## Tools, projectiles and effects
- **Tool items are flat, like vanilla tools** (design decision): `models/item/<tool>.json` is `item/handheld` with an
  hand-drawn 16 px icon (`model/art/item_icons_art.py`; until 2026-10-23 rendered from the tool's geo); the 3D tools exist only on the suit and in the cockpit
  viewmodel. (A 3D GeckoLib item needs a `GeoItemRenderer` registered in `RegisterClientExtensionsEvent` and a
  `builtin/entity` model; GeckoLib doesn't hook items up on NeoForge. The tools were 3D until 2026-10-13.)
- **The pilot can't break blocks, so the mining tools post their own BreakEvent** (protection mods must be able to
  veto) and `PilotHands.noBreaking` lets those through via `ToolMining.isToolBreak()`. Any new block-breaking
  tool must go through `ToolMining.breakBlock`, or it breaks nothing (or bypasses claims).
- **Crack overlays need negative breaker ids** (`destroyBlockProgress` skips the player whose entity id matches).
- **Right click is shared by the hand and the tool** (vanilla's main hand -> off hand order): `ToolInput` claims a press
  only when NeoForge's InteractionKeyMappingTriggered fires for OFF_HAND (= the main hand passed), then cancels every
  use event until the button is released. Scripts that use a tool must empty the pilot's hand first (food or blocks
  would win).
- **Variable-speed spinning parts are code-driven**: GeckoLib scales an animation's whole elapsed time by its speed,
  so the chaingun's barrels turn by an angle each client accumulates from the synced spin (`ToolRotors`, absolute
  set on an unkeyed bone). Rapid fire needs: hit immunity bypass (`target.invulnerableTime = 0`), the damage type in
  `minecraft:no_knockback` (vanilla's shove per hit launches mobs) and light particles per shot.
- **Shoot from the eye, draw from the gun**: projectiles from an offset muzzle hit things the pilot can't see (parallax)
  and a per-frame offset that only converges over time lags behind a fast projectile (it's already a tick out when first
  drawn). The chaingun's bullets fly from the eye along the crosshair line; each client draws them from its muzzle
  (cockpit viewmodel / suit) along a straight line to the target, pointing the streak along that drawn line.
- **Projectile orientation**: `ProjectileUtil.rotateTowardsMovement` stores yaw as `atan2(z, x) + 90` and pitch with up
  negative, unlike vanilla arrows: after `YP(yRot - 90)`, `ZP(xRot)` the model's forward is -X (`BulletRenderer`; the
  rivet gun's first rivet rendered backwards, shank out of the wall).
- **Geometry that isn't an entity** (the rail beam): copy what to draw into the level render state in NeoForge's
  `ExtractLevelRenderStateEvent` (`setRenderData` with a `ContextKey`) and draw it in `SubmitCustomGeometryEvent`; its pose
  stack is camera-relative (translate by position minus the camera's, like `submitBlockOutline`). A hitscan beam from the
  eye along the view is seen end-on from the third-person back camera (nothing to see but the impact): film it from the side.
- **Added light hardly shows against a day sky** (rail cannon, 2026-10-26): the beam drawn only in the `eyes` type
  at 60% was invisible in every daytime shot (a pixel diff found nothing); a see-through blue sheath
  (`entityTranslucentEmissive`, alpha fades) round an additive white core reads by day and glows by night. Check new effects
  with a diff of a frame with and without them, not by eye.
- **`AABB.clip` misses a segment that starts inside the box**: line-of-fire checks also test `box.contains(start)`.
- **Floats don't sum to whole numbers**: 80 x (1/80) < 1 (overheat came one bullet late); compare with a tolerance.
- **A tool's visor HUD** (2026-10-27) is a client class in `client/hud`, paired with the item in `ClientSetup`
  (`ToolHuds`): the item is shared code and can't name `GuiGraphicsExtractor`. Its numbers stay on the shared side
  (`ToolEffects.hud`), so tests and the server can read them. To put a mark on the world (the TNT arc, the EM box) use
  vanilla's projection, `mc.gameRenderer.projectPointToScreen(p)` (NDC; `z > 1` = behind the camera; GUI x =
  (x + 1) / 2 * guiWidth, y = (1 - y) / 2 * guiHeight): it's this frame's camera, so marks sit on the world as drawn.
  A mob's model reaches past its hit box (a pig's head by a third of a block): don't put text right beside the box.
- **A predicted path must replay the projectile's own tick** (`TntShell.predict`): the same order (hit check on this
  tick's move, move, drag, gravity), the same ray casts (`clipIncludingBorder`, COLLIDER, the shell's box expanded by
  its move and inflated 1 for the entity search) and the entity margin vanilla grows from the projectile's tickCount
  (`(t - 2) / 20`, max 0.3; tickCount is already 1 in the first tick). Then a test can demand it to 1e-6
  (`tntArcPredictsTheRound`). Fire and predict from one function (`TntLauncherItem.launch`, partial 1 on the server).

## Items, recipes and data (26.3)
- *(26.3)* **Items need `assets/<mod>/items/<item>.json`** (which model to show) next to `models/item/`; recipe
  ingredients are plain strings (`"minecraft:iron_ingot"`, `"#tag"`); config types are LOCAL / CLIENT / SYNCED /
  STARTUP (ours is LOCAL, file name kept as `powerarmor-common.toml`).
- **Variants of an item are a data component, not new items** (the helmet styles): recipes set it in their result
  (`"components": {"powerarmor:helmet_style": "spartan"}`) and remove it with `"!powerarmor:helmet_style": {}`; a
  smithing_transform's template is optional in 26.3 (`helmet_standard.json` has none); the item model picks the icon
  with `minecraft:select` on `minecraft:component`.
- *(26.3)* **Loot conditions are keyed `"type"`** (`{"type": "neoforge:loot_table_id", ...}`, `minecraft:any_of` too):
  with the old `"condition"` key NeoForge logs "Couldn't parse data file" and skips the loot modifier, nothing else
  fails. Loot modifiers live in `data/<mod>/loot_modifiers/` (no global list); a GameTest rolling the chest's table
  (`helmetTemplatesHideInStructureChests`) caught it.

## Suit setup in tests and scenes
- **Suits need fuel now.** Anything that spawns or boards a suit (autopilots, tests that move) must put fuel in
  `FUEL_SLOT`, or the suit sits slumped (`powered_off`). Old worlds contain unfuelled suits and
  the saved player may already sit in one: `TerrainPilot.fuelVehicle` fuels whatever suit you're in.
- **Engines (2026-10-21): a suit comes without an engine (since 2026-10-23; scenes call `DevUtil.firebox`, GameTests'
  `suitAt` installs one), and a firebox floods in deep water** (water over the backpack,
  2.2 blocks up: 3+ deep): scenes or tests that put a fuelled suit in deep water need a tidal engine (+ a water bucket to
  climb out) or a reactor. Swapping the engine drops the half-burnt item one tick later (`SuitPower.tick` notices the swap),
  so set burn values after a swap has been seen, not in the same tick.
- **The helmet (2026-10-19) seals the suit and lets the mind run**: anything that needs the seal or the mind (tests,
  autopilot scenes) must put a `SUIT_HELMET` in `HELMET_SLOT` next to the fuel (GameTests' `suitAt` does).

## GameTests
- *(26.3)* **GameTest structures start at the test origin** (1.21.1 placed them one block higher): `test_pad` keeps its
  floor at relative y=1 (stand at y=2) by having an empty bottom layer; it's 9x11x9 and sealed by an invisible barrier
  box the size of the structure. Tests run side by side, so search for entities near your own position only.
- *(26.3)* **GameTests are registry entries** (no `@GameTest` annotation): `@SuitTest` methods are registered as test
  functions + instances by `test/SuitTests` (ids are the lower-cased method names, e.g. `powerarmor:pilotclimbsinandout`).
- *(26.3)* **NeoForge won't send mod packets on a connection that didn't negotiate them**: `makeMockServerPlayerInLevel`
  players crashed the test server on GeckoLib's animation triggers. Tests create them with `mockServerPlayer` (adds
  GeckoLib's channels to the fake connection).
- **Menus can't be opened by GameTest mock players**: `makeMockPlayer` ignores `openMenu`, and
  `makeMockServerPlayerInLevel` throws on NeoForge's `advanced_open_screen` payload. Test menu logic by
  constructing `PowerArmorMenu` directly; check the real screen in the autopilot (`ap_menu_*`).
- **Mock players look where their HEAD points** (2026-10-26): `getViewVector` uses `yHeadRot`, and a mock player's head
  starts at a random yaw, so a test that only called `setYRot` aimed up to 5 degrees off (`bulletHitsWhatThePilotAimsAt`
  missed 1 run in 5 for a week). Turn test pilots with `PowerArmorTests.lookAt` / `turn` (body, head, pitch, now and last
  tick, like a real client).
- **GameTests run side by side, also copies of the same test**: no shared mutable state in tests. World-wide settings
  (game rules, weather) go in the batch's test environment (`SuitTests.environmentFor`: the framework sets them before the
  batch and restores them after), never set-and-restore inside a test; test-only event listeners keep per-position data
  (`ProtectedBlock.POSITIONS`, `BreakWatch.BREAKERS`), not one static field. Check with `runGameTestOnly -Prepeat=5`.
- **Mock players don't get their game mode's abilities**: `makeMockPlayer(GameType.CREATIVE)` still has
  `hasInfiniteMaterials()` false; set `getAbilities().instabuild = true` in creative GameTests.
- **GameTests run at random spots of a world with real biomes**: a weather test landed in a desert (no rain). The "rain"
  batch sets the weather, opens the sky (`TestData.skyAccess`: the default barrier ceiling keeps rain off) and paints
  the pad plains (`FillBiomeCommand.fill`).

## Running the game and autopilot scenes
- **Server and client tick independently in the autopilot**: a server-side event can show up a few client
  ticks later; screenshot every tick around an event instead of guessing the frame. Server-side CSV
  columns can lead the screenshots.
- **The game can't start while the Mac's display sleeps** ("Failed to locate a primary monitor"; Gradle then
  hangs). Run game tasks under `caffeinate -d -u -i`; if it hangs, kill the gradlew client process.
- **Dev games must not linger**: `pkill -f` on a system property doesn't match the game's command line. The view
  tuner writes its PID, quits on `run/view/quit` and after 10 idle minutes; always `tools/view-tuner.sh stop`, or use `once` (start, shoot, stop).
- **Run game tasks with a watchdog** (background + poll the log): if it stalls (e.g. in "Saving worlds"), `jstack` the
  game's PID before killing it instead of waiting on a blocked command.
- *(26.3)* **Opening a 1.21.1 world upgrades it for good** (Minecraft asks to back it up first). A world in `run/saves`
  is its owner's to upgrade; `runAutopilotTerrain` clicks "Load without backup" on its copy.
- **26.3 asks "Upgrading World Completed" after upgrading an old world's files**: the terrain pilot sat on it and drove
  nothing (no CSV, no shots) until it learned to press Yes. Check output file dates before trusting a report: an old
  `autopilot_terrain.csv` was read as a fresh "cockpit OK".
- **Tree canopies next to the suit pull the third-person camera into the model** (camera collision): keep leaves
  out of the camera path in autopilot scenes and grow the tree only when it's needed.
- **Night shots in the autopilot**: the client hears the new time of day only once a second, and place scene cameras
  from the ground height (`Heightmap`), not from where the player happens to stand.
- **A scripted camera entity freezes the player on the server**: the client only sends the player's position while the
  player is the camera entity (`LocalPlayer.isControlledCamera`). A scene that moves the player (an EM rogue lifting
  them) must use the player's own (third-person) camera, or the server never sees them move.
- **A scene's first shots can come out unlit** (black ground, dark suits: the fresh chunks' light not computed yet, seen
  once in `runAutopilotStyles` 100 blocks out); a rerun was fine. Check the shots before judging the art.
- **Autopilot worlds are peaceful** (`AutoPilot` creates them so): monsters spawned by a scene vanish the same tick. Set
  the difficulty in the scene (the mind scene sets NORMAL before its zombies).
- **A killed mob stays in the world for its 20-tick death animation**: scene CSVs that count entities see a kill 20 ticks
  late; count `isAlive()` to time a hit.

## Java
- **A ternary of double and int inside String.format can box as Integer** (javac boxed each branch): `cond ? d : -1`
  crashed `%f` the first time the branch ran (the mech log, with no pilot). Write `-1D`.
