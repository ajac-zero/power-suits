# Working on Power Armor

A NeoForge 26.3 + GeckoLib 5.5.7 Minecraft mod (Java 25): an enterable power-armor suit. Start with
`docs/design.md` (spec), `docs/implementation-plan.md` (next phases) and `README.md` (controls, files).
Ported from 1.21.1 / GeckoLib 4 on 2026-10-14 (branch `port-26.3`); the porting notes below marked *(26.3)*
replace older advice. The how-to for this kind of work (setup, GeckoLib, riding, testing, pitfalls) is the project skill
`.agents/skills/minecraft-modding/SKILL.md`; model/animation work uses
`.agents/skills/blockbench-mcp/SKILL.md`.

## The user
- First Minecraft mod. Explain concepts briefly as you go (client vs server, registries, events,
  synced data, menus, packets...). Keep explanations concrete and tied to the change you just made.
- Plays in `run/saves/New World` (creative test world). **Never open or modify it.** Automated runs use
  a copy (see `runAutopilotTerrain`). A sorted md5 listing of it is in `.scratch/world_baseline.txt`
  (`find . -type f ! -name session.lock -exec md5 -r {} \; | sort -k2`); the user plays it between
  sessions, so check file mtimes before blaming a run, and refresh the baseline at the start of a session.
- The art lives in Blockbench: `model/power_armor.bbmodel` (in this repo since 2026-10-13; it used to sit in
  `~/Developer/Models`, which is now only for generic Blockbench experiments). Checkpoint before edits,
  save it (`BarItems.save_project.trigger()` via risky_eval) **with every mod commit that
  touches the art or animations** (user's rule since Phase 5) and commit it with them, so the model and the
  exported files never drift. If Blockbench still has it open from the old place, set
  `Project.save_path` to this file before saving.
- Show every model change with the review sheet from `.agents/skills/blockbench-mcp/scripts/shots.js`: it
  includes the in-game third-person view from behind, which the user considers the most important (the player
  mostly sees the suit's back). Keep the model in its in-game rest pose (the T-pose used for a while is retired:
  the animations are built on the rest pose).

## Layout
- `src/` the mod; `tools/` export / art / test helpers; `docs/` design + plan; `run/` dev game (not in git).
- `model/` the Blockbench source: `power_armor.bbmodel`, `anim/` procedural animation specs (bake with
  `.agents/skills/blockbench-mcp/scripts/run.mjs`, see `model/anim/README.md`), `art/` one-off texture/part scripts.
- `.pi/mcp.json` connects pi to Blockbench's MCP server (`localhost:3000/bb-mcp`): start sessions here.
- `.agents/skills/`: `minecraft-modding` (this mod's how-to) and `blockbench-mcp` (Blockbench work).

## Setup
Java 25 from Homebrew (`brew install openjdk@25`) is keg-only. Every shell needs:
```sh
export JAVA_HOME=$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home
```
Reference sources: Minecraft (NeoForge-patched) in `build/moddev/artifacts/minecraft-patched-*-sources.jar`, NeoForge in
the Gradle cache (`neoforge-*-sources.jar`), GeckoLib on GitHub (`bernie-g/geckolib`, branch per Minecraft version).
Mojang's names are official since 26.1 (no Parchment). Porting primers: `neoforged/.github` repo, `primers/<version>`.

## Verification loop (do this for every change)
| Command | Use |
|---|---|
| `./gradlew build` | compile + jar |
| `./gradlew runGameTestServer` | headless GameTests in `src/main/java/.../test/PowerArmorTests.java` (must print "All N required tests passed") |
| `./gradlew runAutopilot` | scripted flat-world drive (cockpit, third person, jump, pool, run out of fuel + refuel via the menu, chaingun range, right hand, EM projector, drill + chainsaw, tool items on foot). Screenshots `run/screenshots/ap_*.png`, logs `run/autopilot_{water,fuel,cannon,em,mine}.csv`. Quits by itself (~75 s). |
| `./gradlew runAutopilotMech` | only the autopilot's last scenes (~2 min): smashing blocks, stomp, lightning, punch, lava + poison (`ap_mech_*.png`, `run/autopilot_mech.csv`), then the machine mind: walk home, drill, chaingun follow/defend/fists, EM guard (`ap_ai_*`, `run/autopilot_mind.csv`) and the workers (`client/dev/WorkScene`: `ap_work_*`, `run/autopilot_work.csv`) |
| `./gradlew runAutopilotTerrain` | drives over real terrain in a fresh copy of `New World`, logs `run/autopilot_terrain.csv` + per-render-frame `run/autopilot_terrain_frames.csv`; summarize with `python3 tools/terrain-report.py` (its `cockpit:` line must say OK: the model may not move against the camera) |
| `uv run --with pillow python3 tools/contact-sheet.py <prefix> out.png [cols] [width]` | tile screenshots (`fp`, `tp`, `side`, `water`, `terrain`) |
| `tools/view-tuner.sh start\|shoot\|stop` | live-tune the cockpit viewmodel (`CockpitHands`): the game stays in the cockpit and re-shoots `ap_view_*.png` with `run/view/tuning.json`; `stop` always (quits the game, stops the Gradle daemons) |
| `node tools/export-model.mjs` | re-export geo/animations/texture from the open Blockbench project (MCP at localhost:3000); then `extract-tool-geo.py`, `extract-view-geo.py`, `uv run --with pillow python3 tools/make-glowmasks.py` and `tools/make-item-icons.py` (the tool items' icons) |
macOS has no `timeout`; run game tasks in the background and poll the log for completion.
Extend the autopilot scripts (`client/dev/AutoPilot.java`, `TerrainPilot.java`) with a scenario for each
new feature and log numbers to CSV: measuring beat guessing every time in this project.

## Lessons learned (each cost a debugging round)
- **Saved attributes outlive code changes.** Entities store attribute base values in NBT; tuning lives in
  `BASE_STATS` and is re-applied in `readAdditionalSaveData`. Do the same for any new persisted tuning.
- **Ridden movement is client-authoritative.** `travel`/`tickRidden` run on the pilot's client for a
  piloted suit and on the server for an empty one. Gate movement on synced state (e.g. powered).
- **Speed constant is measured, not derived**: 43.15 blocks/s per movement-speed point on 26.3 (42.14 on 1.21.1:
  the same attribute went 2.4% further after the port). Re-measure with the autopilot's `walk speed` log line.
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
- *(26.3)* **GameTest structures start at the test origin** (1.21.1 placed them one block higher): `test_pad` keeps its
  floor at relative y=1 (stand at y=2) by having an empty bottom layer; it's 9x11x9 and sealed by an invisible barrier
  box the size of the structure. Tests run side by side, so search for entities near your own position only.
- *(26.3)* **GameTests are registry entries** (no `@GameTest` annotation): `@SuitTest` methods are registered as test
  functions + instances by `test/SuitTests` (ids are the lower-cased method names, e.g. `powerarmor:pilotclimbsinandout`).
- *(26.3)* **NeoForge won't send mod packets on a connection that didn't negotiate them**: `makeMockServerPlayerInLevel`
  players crashed the test server on GeckoLib's animation triggers. Tests create them with `mockServerPlayer` (adds
  GeckoLib's channels to the fake connection).
- *(26.3)* **The server doesn't get a passenger's `xxa` / `zza` any more** (the input packet only fills
  `ServerPlayer.getLastClientInput()`, keys pressed): read the pilot's WASD on the server through
  `PowerArmorEntity.pilotInput` (smashing blocks and the fuel drain both broke silently on 0 input).
- *(26.3)* **No `ItemStack` in static initializers** ("Components not bound yet" crashed the client at startup):
  create them on first use.
- *(26.3)* **Items need `assets/<mod>/items/<item>.json`** (which model to show) next to `models/item/`; recipe
  ingredients are plain strings (`"minecraft:iron_ingot"`, `"#tag"`); config types are LOCAL / CLIENT / SYNCED /
  STARTUP (ours is LOCAL, file name kept as `powerarmor-common.toml`).
- *(26.3)* **Opening a 1.21.1 world upgrades it for good** (Minecraft asks to back it up first). The user's `New World`
  is theirs to upgrade; `runAutopilotTerrain` clicks "Load without backup" on its copy.
- *(26.3)* **Entities save through `ValueInput` / `ValueOutput`** (`getIntOr`, `read(name, codec)`...), saved data through a
  codec (`SavedDataType`); keep the old keys and formats (UUID / BlockPos codecs write the same int arrays) so 1.21.1
  saves still load.
- **Vanilla keeps sprinting after the sprint key is released**; call `setSprinting(false)` in scripts.
- **Suits need fuel now.** Anything that spawns or boards a suit (autopilots, tests that move) must put fuel in
  `FUEL_SLOT`, or the suit sits slumped (`powered_off`). The user's world contains old unfuelled suits and
  the saved player may already sit in one: `TerrainPilot.fuelVehicle` fuels whatever suit you're in.
- **Synced entity data must be refreshed wherever the server field changes**, not only in `tick()`: loading,
  `applyLoadout`, test setters (a GameTest caught a stale `BURN_TOTAL` after load). See `syncBurn()`/`syncSlots()`.
- **`damageSources().source(key, a, b)` takes (projectile, shooter)** despite its parameter names
  (vanilla: `source(FIREBALL, fireball, thrower)`). Swapped, kills credit the projectile instead of the pilot.
- **GeckoLib won't restart a triggered animation that is still playing**: per-use animations must be shorter
  than the tool's cooldown (`tool_recoil` 0.5 s vs the EM push's cooldown); rapid fire (the chaingun) kicks in code from a synced shot counter instead.
- **Tool items are flat, like vanilla tools** (user decision): `models/item/<tool>.json` is `item/handheld` with an
  icon drawn from the tool's geo by `tools/make-item-icons.py`; the 3D tools exist only on the suit and in the cockpit
  viewmodel. (A 3D GeckoLib item needs a `GeoItemRenderer` registered in `RegisterClientExtensionsEvent` and a
  `builtin/entity` model; GeckoLib doesn't hook items up on NeoForge. The tools were 3D until 2026-10-13.)
- **Server and client tick independently in the autopilot**: a server-side event can show up a few client
  ticks later; screenshot every tick around an event instead of guessing the frame. Server-side CSV
  columns can lead the screenshots.
- **The game can't start while the Mac's display sleeps** ("Failed to locate a primary monitor"; Gradle then
  hangs). Run game tasks under `caffeinate -d -u -i`; if it hangs, kill the gradlew client process.
- **(Superseded by the cockpit viewmodel)** The suit's arm is too short to show an item in the cockpit view without blocking it (a hand target in
  view folded the forearm across the visor). Third person uses the 3D hand; first person draws the item
  vanilla-style (`FirstPersonLeftHand`). Check new poses from the cockpit camera, not only from outside.
- **Arm IK has two elbow solutions**; with the wrong seed it picks hyperextension (forearm bent backward).
  On this rig positive forearm X flexes the elbow; seed the solver on that side and reuse one seed for all
  animations that must meet at the same pose.
- **Smoothing on top of Minecraft's interpolation**: entities are drawn at `lerp(partial, yOld, y)`, so an offset that
  cancels a jump must only change at the END of the tick (`StepSmoothing`): also shifting its start value dropped the
  model a block below the camera for a frame on every step. `stepsDoNotJerkTheModelAgainstTheCamera` checks this frame
  by frame; in game, `ViewportEvent.ComputeCameraAngles` still holds last frame's camera position, so measure with
  `player.getEyePosition(partial)`.
- **(GeckoLib 4 only) Never add to an unkeyed bone in `setCustomAnimations`**: GeckoLib 4 only reset bones the playing
  animation keyed, so a per-frame `+=` piled up and the bone flickered. GeckoLib 5's per-frame snapshots fixed that; code
  offsets still go on bones no animation keys (the chaingun barrels: `ChaingunParts`), set every frame.
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
- **The cockpit's main hand is vanilla's** (user decision): held items are left to vanilla's first-person renderer
  (same place and motions as on foot, no arm, as in vanilla); only an empty hand is ours, the gauntlet laid along
  vanilla's empty-arm pose (`CockpitHands.gauntletTransform`). Don't give items a custom cockpit pose. The view tuner
  shoots `view_vanilla_*` on foot next to `view_*` in the suit to compare.
- **F1 (hide HUD, `mc.gui.hud.isHidden()` since 26.x) also skips the hand pass**: screenshots with the GUI hidden show no viewmodel. The autopilots keep
  the GUI on. Tune the viewmodel live with `tools/view-tuner.sh start|shoot|stop` (edit `run/view/tuning.json`).
- **Dev games must not linger**: `pkill -f` on a system property doesn't match the game's command line. The view
  tuner writes its PID, quits on `run/view/quit` and after 10 idle minutes; always `tools/view-tuner.sh stop`, or use `once` (start, shoot, stop).
- **Wrap yaw differences** (`Mth.wrapDegrees`): yaws are unbounded (720 after two turns) and GeckoLib's
  `netHeadYaw` is a raw subtraction; unwrapped, the torso twisted 90 deg the wrong way (right pad in the cockpit).
- **Minecraft doesn't move no-AI mobs by velocity** (`isEffectiveAi` false skips `travel`), so the EM projector
  ignores them (they're map statues anyway). Use normal mobs in GameTests that move things; give them room
  before the test pad's barrier walls (a wall hit zeroes the velocity you meant to assert).
- **Client particles linger** (dust ~1-2 s): ones that drift next to the cockpit camera become huge blobs.
  Don't spawn them within ~1.3 blocks of the pilot's eye.
- **Menus can't be opened by GameTest mock players**: `makeMockPlayer` ignores `openMenu`, and
  `makeMockServerPlayerInLevel` throws on NeoForge's `advanced_open_screen` payload. Test menu logic by
  constructing `PowerArmorMenu` directly; check the real screen in the autopilot (`ap_menu_*`).
- **The pilot can't break blocks, so the mining tools post their own BreakEvent** (protection mods must be able to
  veto) and `PilotHands.noBreaking` lets those through via `ToolMining.isToolBreak()`. Any new block-breaking
  tool must go through `ToolMining.breakBlock`, or it breaks nothing (or bypasses claims).
- **Crack overlays need negative breaker ids** (`destroyBlockProgress` skips the player whose entity id matches).
- **Free texture space = transparent pixels minus every cube's box-UV net** (a cube without `uv_offset` uses
  [0,0]). Transparent net corners belong to nobody, but a transparent face can be part of a net.
- **Tree canopies next to the suit pull the third-person camera into the model** (camera collision): keep leaves
  out of the camera path in autopilot scenes and grow the tree only when it's needed.
- **Balance values live in `PowerArmorConfig`** (COMMON config, hot-reloaded): read `.get()` where used, never
  cache in static fields; GameTests must compare against the config, not literal numbers (the dev `run/config`
  file is shared by the client, the GameTest server and the user's edits).
- **Looping sounds are client side** (`SuitSounds`): the server only syncs state (powered, piloted, `TOOL_RUNNING`);
  one-shots still use `playSound` on the server. Sounds are re-pitched vanilla events in `sounds.json`.
- **Right click is shared by the hand and the tool** (vanilla's main hand -> off hand order): `ToolInput` claims a press
  only when NeoForge's InteractionKeyMappingTriggered fires for OFF_HAND (= the main hand passed), then cancels every
  use event until the button is released. Scripts that use a tool must empty the pilot's hand first (food or blocks
  would win). GeckoLib bones: rest rotations load as (-X, -Y, +Z) of Blockbench, so code offsets on GeoBone are
  `rotX -= bbX`; check a raise/pose change with a same-camera before/after pair (`cannon_side_976` vs
  `cannon_side_lowered`): one shot with torso twist and recoil was misread as the wrong sign.
- **Glowing pixels are painted in reserved colours** (dark coal / smoked glass, listed in `tools/make-glowmasks.py`):
  the masks are generated from them, so never reuse those colours elsewhere (the script counts them and refuses).
  The glow is additive (`RenderTypes.eyes`): dark base + bright mask = dead when off, glowing when on, also by day.
- **Night shots in the autopilot**: the client hears the new time of day only once a second, and place scene cameras
  from the ground height (`Heightmap`), not from where the player happens to stand.
- **Projectile orientation**: `ProjectileUtil.rotateTowardsMovement` stores yaw as `atan2(z, x) + 90` and pitch with up
  negative, unlike vanilla arrows: after `YP(yRot - 90)`, `ZP(xRot)` the model's forward is -X (`BulletRenderer`; the
  rivet gun's first rivet rendered backwards, shank out of the wall).
- **Variable-speed spinning parts are code-driven**: GeckoLib scales an animation's whole elapsed time by its speed,
  so the chaingun's barrels turn by an angle each client accumulates from the synced spin (`ChaingunParts`, absolute
  set on an unkeyed bone). Rapid fire needs: hit immunity bypass (`target.invulnerableTime = 0`), the damage type in
  `minecraft:no_knockback` (vanilla's shove per hit launches mobs) and light particles per shot.
- **Shoot from the eye, draw from the gun**: projectiles from an offset muzzle hit things the pilot can't see (parallax)
  and a per-frame offset that only converges over time lags behind a fast projectile (it's already a tick out when first
  drawn). The chaingun's bullets fly from the eye along the crosshair line; each client draws them from its muzzle
  (cockpit viewmodel / suit) along a straight line to the target, pointing the streak along that drawn line.
- **Floats don't sum to whole numbers**: 80 x (1/80) < 1 (overheat came one bullet late); compare with a tolerance.
- **Mock players don't get their game mode's abilities**: `makeMockPlayer(GameType.CREATIVE)` still has
  `hasInfiniteMaterials()` false; set `getAbilities().instabuild = true` in creative GameTests.
- **Run game tasks with a watchdog** (background + poll the log): if it stalls (e.g. in "Saving worlds"), `jstack` the
  game's PID before killing it instead of waiting on a blocked command.
- **Sides are mirrored, not rebuilt.** The arms were swapped (hand right, tools left) by mirroring the whole
  model across x = 0 (cubes x-flipped + `mirror_uv` toggled, Y/Z rotations and position X negated in rest poses and
  keys, left/right names swapped), which keeps every animation consistent; swapping only the arm keys would have
  put the arm swing in phase with the wrong leg and doubled the torso sway on the stabilised tool arm. The specs
  in `model/anim/` still describe the old rig (`kit.json` mirrors them on bake), so their `right_*`
  is today's `left_*`. Hand-held tool items are mirrored back in `extract-tool-geo.py`.
- **GeckoLib `thenPlay` uses the JSON's loop setting**: `thenPlay("electrified")` (a `"loop": true` animation) convulsed
  forever and never reached the chained slump. Chain after a looping animation with `then(name, LoopType.PLAY_ONCE)`.
- **The pilot's head pokes out of the suit's hitbox** (eye 0.55 forward, player width 0.6): pressed against a wall, vanilla
  thought the pilot was suffocating (hull damage every 10 ticks, block texture over the screen). `PilotProtection` drops
  IN_WALL unless the suit itself is in a wall; `PilotView.cockpitOverlays` cancels the overlay.
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
- **Blocks broken by walking must be broken ahead of the suit** (`SuitSmash`): ridden movement is client-side, so the client
  hits a pane before the server could break it; the server clears a box reaching 0.6 / 1.1 blocks ahead along the input.
- **A powered suit always runs (the machine mind, Phase 8)**: with fuel and nobody inside it burns the idle rate, looks at
  players and walks back to its home; anything that leaves fuelled suits around (tests, autopilot scenes) gets that.
  To park one, take the fuel out. Its state is only set on its first tick: tests wait 2 ticks before boarding/aiming.
- **Vanilla mobs move at speed squared**: `MoveControl` passes the speed as the forward input (zza) and `moveRelative`
  applies it again (a 0.119 suit crawled at 0.6 blocks/s). `PowerArmorEntity.travel` normalises the mind's input, so
  a navigation speed of 1.0 = the pilot's walk. Measure position deltas, not `getDeltaMovement()` (it already holds the
  next tick's drag: it read 2.9 for a 5.2 blocks/s walk).
- **`Mob.serverAiStep` is final**: gate the mind with each goal's `canUse`/`canContinueToUse` + `updateControlFlags`.
  `LookControl` zeroes the pitch and eases the head to the body every tick unless asked to look; `customServerAiStep`
  runs after the goals and before the look control, so the mind's aim (`setAiAim`) is applied there.
- **Paths of wide mobs end short**: vanilla's path for the 1.4-wide suit stopped 1.7 blocks from home (node offsets);
  `ReturnHome` walks the last stretch with `MoveControl.setWantedPosition`. The pathfinder returns null for a mob in
  the air (just spawned): wait for `onGround()`.
- **Vanilla's pathfinder wants floor(height + 1) free blocks above each step** (and floor(width + 1) across): the suit
  at 3.0 high couldn't path through its own 3-high drill hall; its hitbox is 2.95 now.
- **A scripted camera entity freezes the player on the server**: the client only sends the player's position while the
  player is the camera entity (`LocalPlayer.isControlledCamera`). A scene that moves the player (an EM rogue lifting
  them) must use the player's own (third-person) camera, or the server never sees them move.
- **Autopilot worlds are peaceful** (`AutoPilot` creates them so): monsters spawned by a scene vanish the same tick. Set
  the difficulty in the scene (the mind scene sets NORMAL before its zombies).
- **`AABB.clip` misses a segment that starts inside the box**: line-of-fire checks also test `box.contains(start)`.
- **Gaits come from the legs, not the game speed**: legs this long (hip 22.6 px) can't walk 5 blocks/s. The old "walk" had
  each foot down only 37.5% of the cycle (a run: both feet in the air) and dropped the hips 3.6 px, which bent the knees
  to 128 deg through the thigh. Hip height is very sensitive (1 px lower ~ +20 deg of knee): derive it from the planted
  leg's reach (`reachHip` in the skill's `k2.js`) and pick the game speed from the gait (2026-10-14: walk 2.6, sprint 6.25). A limited ankle (`model/anim/kit.json` "ankle") needs straight-ish stance
  knees and short steps: a bent stance knee tilts the shin forward by about half its angle.
- **Model first, animations after** (user, 2026-10-15): while the shape is in flux the user judges the neutral pose
  only ("no part of the model is safe"); animations are redone once the model is final. Since the legs v5-v9 rework
  the leg animations in the `.bbmodel` are stale except walk and run (legs v10, re-solved 2026-10-15); re-bake every
  other leg animation before exporting. The assets aren't exported yet: on export set `WALK_ANIM_AUTHORED` to 2.218
  (the run stays 6.25) and decide the game walk speed (user: open; the animation plays faster at 2.6).
- **Read the user's sketches literally and confirm a reading in words before building**: "an opposite slope" for the
  thigh meant a thigh bottom PARALLEL to the shin top (both falling toward the back, a dark knee band between), and
  "the slot where the foot is" meant the shin coming down to the ground around the foot, not an empty pocket above it.
  Each misreading cost a full rebuild.
- **The legs are rebuilt by a script** (`model/art/legs_stomper_art.py` + `legs_stomper_apply.js`, from a fresh snapshot
  of the live project): when the user tweaks a leg cube by hand, carry the change into the script before the next
  rebuild, or it is undone (the knee joint's 6.2 depth, 2026-10-15).
- **The feet are final (user, 2026-10-15, legs v10)**: the shin is a solid block down to the boot's sole strip, the ankle
  hinges at the shin's FRONT bottom edge, and the boot's hidden heel follows the circle the shin's back edge travels
  about that hinge. Every slot/slope/band variant failed: with big steps the shin leans ~30-40 deg over the flat boot at
  push-off, so whatever sits under the shin must be shaped around the hinge. A front hinge makes the leg straightest with
  the knee bent ~23 (gaits use KMIN 23; the kit's knee guard keeps the solver off the backward-bending solution).
- **The user judges motion by feel, in words; translate each word into a measurable gait change** (2026-10-15):
  "slower, bigger steps" = longer cycle + longer stance (walk 0.6 -> 1.0 s, 15 -> 22 px); "baby steps" = short stance
  and cadence; "hopping, frolicking" = long flights, high hips, little ground time (v7 run rejected); "pushing hard into
  the ground at every step" = more ground time (foot planted 32%), hips sinking into the loaded leg right after each
  footfall and a push-off that throws the trailing leg out behind (v8 run, approved). Arms: both calm in the run (fist
  ~5 px of travel against the chest; a 17 px swing next to the steady tool arm read as "jarring"), the tool arm pumps
  like the hand in the run (its third-person pose never aims: shots leave from the eye, the cockpit has a viewmodel).
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

## Code map (see README for the full list)
`entity/PowerArmorEntity.java` holds riding, movement, terrain/water, jump, turning, fuel/power, inventory, animation state;
`entity/ai/` the machine mind's goals (`SuitMind` friendly/rogue); `menu/` the suit menu logic, `client/PowerArmorScreen` + `FuelGauge` its picture and the HUD;
`client/` holds rendering + camera; `PilotProtection.java` redirects damage; `registry/` registers things.
Comment the "why" for anything non-obvious; the user reads the code to learn.
