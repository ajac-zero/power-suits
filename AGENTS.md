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
| `./gradlew runAutopilot` | scripted flat-world drive (cockpit, third person, jump, pool, run out of fuel + refuel via the menu, plasma cannon range, left hand, EM projector, drill + chainsaw, tool items on foot). Screenshots `run/screenshots/ap_*.png`, logs `run/autopilot_{water,fuel,cannon,em,mine}.csv`. Quits by itself (~75 s). |
| `./gradlew runAutopilotTerrain` | drives over real terrain in a fresh copy of `New World`, logs `run/autopilot_terrain.csv` + per-render-frame `run/autopilot_terrain_frames.csv`; summarize with `python3 tools/terrain-report.py` (its `cockpit:` line must say OK: the model may not move against the camera) |
| `uv run --with pillow python3 tools/contact-sheet.py <prefix> out.png [cols] [width]` | tile screenshots (`fp`, `tp`, `side`, `water`, `terrain`) |
| `node tools/export-model.mjs` | re-export geo/animations/texture from the open Blockbench project (MCP at localhost:3000) |
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
- **The suit's arm is too short to show an item in the cockpit view without blocking it** (a hand target in
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
  on a bone no animation keys (`right_shoulder`, `tool_mount`) and set absolute values every render (0 when off).
- **Rotation signs in `setCustomAnimations` match Blockbench for the arm/tool bones** (+rotX = Blockbench +X): the
  assumed GeckoLib X negation swung the cockpit tool pose out of view. Check new additive poses in game.
- **From the cockpit you look down the right arm**: a tool pointing away from you hides behind its own base. The
  cockpit-only pose in `PowerArmorModel` bends it up at the socket; preview cockpit views in Blockbench (camera at
  [0, 41.6, -8.8], fov 70, shell + left arm hidden) before running the autopilot, and keep `SocketAim`'s muzzle
  between the cockpit and animated muzzle positions (`muzzle_flash` bone).
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
- Blockbench animation work: follow the skill's `SKILL.md` + `ANIMATION.md`; specs in
  `~/Developer/Models/starcraft_marine_anim/` (walk/run carry a cannon `stabilize()` wrapper). Measure gait
  ground speed with `scripts/speed.mjs` and keep `WALK_ANIM_AUTHORED` / `RUN_ANIM_AUTHORED` in sync.

## Code map (see README for the full list)
`entity/PowerArmorEntity.java` holds riding, movement, terrain/water, jump, turning, fuel/power, inventory, animation state;
`menu/` the suit menu logic, `client/PowerArmorScreen` + `FuelGauge` its picture and the HUD;
`client/` holds rendering + camera; `PilotProtection.java` redirects damage; `registry/` registers things.
Comment the "why" for anything non-obvious; the user reads the code to learn.
