# Working on Power Armor

A NeoForge 1.21.1 + GeckoLib 4.9.3 Minecraft mod: an enterable power-armor suit. Start with
`docs/design.md` (spec), `docs/implementation-plan.md` (next phases) and `README.md` (controls, files).
The how-to for this kind of work (setup, GeckoLib, riding, testing, pitfalls) is the project skill
`.agents/skills/minecraft-modding/SKILL.md`; model/animation work uses
`~/Developer/Models/.agents/skills/blockbench-mcp/SKILL.md`.

## The user
- First Minecraft mod. Explain concepts briefly as you go (client vs server, registries, events,
  synced data, menus, packets...). Keep explanations concrete and tied to the change you just made.
- Plays in `run/saves/New World` (creative test world). **Never open or modify it.** Automated runs use
  a copy (see `runAutopilotTerrain`). A sorted md5 listing of it is in `.scratch/world_baseline.txt`
  (`find . -type f ! -name session.lock -exec md5 -r {} \; | sort -k2`); the user plays it between
  sessions, so check file mtimes before blaming a run, and refresh the baseline at the start of a session.
- The art lives in Blockbench: `~/Developer/Models/starcraft_marine.bbmodel`. Checkpoint before edits,
  save it (`BarItems.save_project.trigger()` via risky_eval) **with every mod commit that
  touches the art or animations** (user's rule since Phase 5), so the model and the exported files never drift.

## Setup
Java 21 from Homebrew is keg-only. Every shell needs:
```sh
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
```

## Verification loop (do this for every change)
| Command | Use |
|---|---|
| `./gradlew build` | compile + jar |
| `./gradlew runGameTestServer` | headless GameTests in `src/main/java/.../test/PowerArmorTests.java` (must print "All N required tests passed") |
| `./gradlew runAutopilot` | scripted flat-world drive (cockpit, third person, jump, pool, run out of fuel + refuel via the menu, plasma cannon range, right hand, EM projector, drill + chainsaw, tool items on foot). Screenshots `run/screenshots/ap_*.png`, logs `run/autopilot_{water,fuel,cannon,em,mine}.csv`. Quits by itself (~75 s). |
| `./gradlew runAutopilotTerrain` | drives over real terrain in a fresh copy of `New World`, logs `run/autopilot_terrain.csv` + per-render-frame `run/autopilot_terrain_frames.csv`; summarize with `python3 tools/terrain-report.py` (its `cockpit:` line must say OK: the model may not move against the camera) |
| `uv run --with pillow python3 tools/contact-sheet.py <prefix> out.png [cols] [width]` | tile screenshots (`fp`, `tp`, `side`, `water`, `terrain`) |
| `tools/view-tuner.sh start\|shoot\|stop` | live-tune the cockpit viewmodel (`CockpitHands`): the game stays in the cockpit and re-shoots `ap_view_*.png` with `run/view/tuning.json`; `stop` always (quits the game, stops the Gradle daemons) |
| `node tools/export-model.mjs` | re-export geo/animations/texture from the open Blockbench project (MCP at localhost:3000); then `extract-tool-geo.py`, `extract-view-geo.py` and `uv run --with pillow python3 tools/make-glowmasks.py` |
macOS has no `timeout`; run game tasks in the background and poll the log for completion.
Extend the autopilot scripts (`client/dev/AutoPilot.java`, `TerrainPilot.java`) with a scenario for each
new feature and log numbers to CSV: measuring beat guessing every time in this project.

## Lessons learned (each cost a debugging round)
- **Saved attributes outlive code changes.** Entities store attribute base values in NBT; tuning lives in
  `BASE_STATS` and is re-applied in `readAdditionalSaveData`. Do the same for any new persisted tuning.
- **Ridden movement is client-authoritative.** `travel`/`tickRidden` run on the pilot's client for a
  piloted suit and on the server for an empty one. Gate movement on synced state (e.g. powered).
- **Speed constant is measured, not derived**: 42.14 blocks/s per movement-speed point (autopilot log).
- **GeckoLib animation speed multiplies the whole elapsed time**: changing it smoothly mid-loop makes the
  animation jump; use constant rates per state. Controllers added later override earlier ones per bone.
  `setCustomAnimations` runs after keyframes: add to bone values, don't replace them.
- **GeckoLib draws both faces** (`entityCutoutNoCull`): coplanar same-direction faces flicker
  (z-fighting); fix in Blockbench with `inflate` 0.1 (keeps box UVs). Scan with
  `~/Developer/Models/.agents/skills/blockbench-mcp/scripts/zfight.mjs`. The cockpit uses back-face culling.
- **Animation state flicker** comes from states toggling every few ticks (e.g. run/fall on slopes); check the
  `anim` column of the terrain CSV for rapid switches.
- **GameTest structures sit one block above the test origin** (floor at relative y=1, stand at y=2) and are
  sealed by an invisible barrier box the size of the structure (`test_pad` is 9x10x9); tests run side by
  side, so search for entities near your own position only.
- **Vanilla keeps sprinting after the sprint key is released**; call `setSprinting(false)` in scripts.
- **Suits need fuel now.** Anything that spawns or boards a suit (autopilots, tests that move) must put fuel in
  `FUEL_SLOT`, or the suit sits slumped (`powered_off`). The user's world contains old unfuelled suits and
  the saved player may already sit in one: `TerrainPilot.fuelVehicle` fuels whatever suit you're in.
- **Synced entity data must be refreshed wherever the server field changes**, not only in `tick()`: loading,
  `applyLoadout`, test setters (a GameTest caught a stale `BURN_TOTAL` after load). See `syncBurn()`/`syncSlots()`.
- **`damageSources().source(key, a, b)` takes (projectile, shooter)** despite its parameter names
  (vanilla: `source(FIREBALL, fireball, thrower)`). Swapped, kills credit the bolt instead of the pilot.
- **GeckoLib won't restart a triggered animation that is still playing**: per-use animations must be shorter
  than the tool's cooldown (`plasma_shot` 0.5 s vs 0.6 s).
- **GeckoLib items are not hooked up automatically on NeoForge**: register a `GeoItemRenderer` through
  `RegisterClientExtensionsEvent` (see `ClientSetup`), model JSON parent `builtin/entity`. Centre the item
  geo on the origin (GeckoLib adds the half-block offset) or rotations swing it out of the slot.
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
- **Never add to an unkeyed bone in `setCustomAnimations`**: GeckoLib only resets bones the playing animation keys
  (others ease back to rest over time), so a per-frame `+=` piles up and the bone flickers. Put code-driven offsets
  on a bone no animation keys and set absolute values every render (0 when off).
- **The cockpit shows a viewmodel, not the suit's arms.** Looking at the external model from inside never lined up
  (hands raised where yours aren't, tools hidden behind their base, pads clipping), so in first person only the
  suit's legs are drawn and `CockpitHands` draws the tool + gauntlet in the hand pass (`geo/view`, rebuilt by
  `tools/extract-view-geo.py` after every export). Bolts/EM beam start at its on-screen muzzle for the pilot only
  (`SocketAim.visualMuzzle`, `PlasmaBolt.visualOffset`); the server still uses the suit's muzzle.
- **Viewmodel parts must not show their cut ends**: the tool's socket face and the gauntlet's wrist stay below the
  bottom screen edge so they reach in from the corners (floating otherwise). Check/fit poses with
  `tools/viewmodel-fit.py` (also for the eat pose) before tuning visually.
- **F1 (hideGui) also skips the hand pass**: screenshots with the GUI hidden show no viewmodel. The autopilots keep
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
  The glow is additive (`RenderType.eyes`): dark base + bright mask = dead when off, glowing when on, also by day.
- **Night shots in the autopilot**: the client hears the new time of day only once a second, and place scene cameras
  from the ground height (`Heightmap`), not from where the player happens to stand. Parked suits burn no fuel, so
  run a suit dry while piloted.
- **Sides are mirrored, not rebuilt.** The arms were swapped (hand right, tools left) by mirroring the whole
  model across x = 0 (cubes x-flipped + `mirror_uv` toggled, Y/Z rotations and position X negated in rest poses and
  keys, left/right names swapped), which keeps every animation consistent; swapping only the arm keys would have
  put the arm swing in phase with the wrong leg and doubled the torso sway on the stabilised tool arm. The specs
  in `starcraft_marine_anim/` still describe the old rig (`kit.json` mirrors them on bake), so their `right_*`
  is today's `left_*`. Hand-held tool items are mirrored back in `extract-tool-geo.py`.
- Blockbench animation work: follow the skill's `SKILL.md` + `ANIMATION.md`; specs in
  `~/Developer/Models/starcraft_marine_anim/` (walk/run carry a cannon `stabilize()` wrapper). Measure gait
  ground speed with `scripts/speed.mjs` and keep `WALK_ANIM_AUTHORED` / `RUN_ANIM_AUTHORED` in sync.

## Code map (see README for the full list)
`entity/PowerArmorEntity.java` holds riding, movement, terrain/water, jump, turning, fuel/power, inventory, animation state;
`menu/` the suit menu logic, `client/PowerArmorScreen` + `FuelGauge` its picture and the HUD;
`client/` holds rendering + camera; `PilotProtection.java` redirects damage; `registry/` registers things.
Comment the "why" for anything non-obvious; the user reads the code to learn.
