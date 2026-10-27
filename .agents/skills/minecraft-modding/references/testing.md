# Testing and verification

Three layers, all used on every change: build, GameTests (logic, headless), autopilot (real client,
screenshots + per-tick CSV). Example implementations: `src/dev/java/dev/ajaczero/powerarmor/test/`
and `client/dev/` in `~/Developer/PowerArmor`.

**Flaky tests**: run only the suspect, many times side by side, with a gameTestServer run that passes vanilla's
`--tests <id with wildcards>` and `--repeatCount N` program arguments (Power Armor: `runGameTestOnly -Ptests=... -Prepeat=40`),
make the failure message carry a per-tick trace (`helper.onEachTick`), and run the whole suite with a repeat count to
find tests that share state. Two causes found that way: mock players aim with their head yaw (`yHeadRot`, random at
spawn: set it with the body yaw), and tests that set a world-wide game rule and "restore" it (use a `SetGameRules` test
environment for the batch instead).

**Keep test and dev code out of the release jar**: a second source set (`src/dev/`) that compiles against main,
loaded with main as one mod in every run. With ModDevGradle 2:
```groovy
sourceSets { dev { compileClasspath += sourceSets.main.output; runtimeClasspath += sourceSets.main.output } }
configurations { devImplementation.extendsFrom implementation; devRuntimeOnly.extendsFrom runtimeOnly }
tasks.named('check') { dependsOn tasks.named('devClasses') }   // build still compiles it
neoForge {
    addModdingDependenciesTo sourceSets.dev
    runs { configureEach { sourceSet = sourceSets.dev } }
    mods { "${mod_id}" { sourceSet(sourceSets.main); sourceSet(sourceSets.dev) } }
}
```
The dev half needs its own entry point: a second `@Mod(MODID)` class (several per mod id are allowed) that registers
the GameTests; `@EventBusSubscriber` classes there register themselves. Test structures go to `src/dev/resources`.
Main must never reference dev classes (it can't: they aren't on its classpath, so the compiler enforces it).

## GameTests (headless server)
**On 26.x** (since 1.21.5) a GameTest is two registry entries: a test *function* (`Registries.TEST_FUNCTION`, a
`Consumer<GameTestHelper>`) and a test *instance* (`FunctionGameTestInstance(functionKey, new TestData<>(environment,
structureId, maxTicks, setupTicks, required))`, registered in NeoForge's `RegisterGameTestsEvent` on the mod bus,
together with a test environment: tests are batched per environment). There is no `@GameTest` annotation any more.
Power Armor keeps the old style with its own annotation: `test/SuitTest.java` (`@SuitTest(timeoutTicks = 200, batch =
"...")`) and `test/SuitTests.java` (finds the methods by reflection, registers both entries, only when
`GameTestHooks.isGametestEnabled()`). Test ids are lower-case (`mymod:mytestname`).
```java
@SuitTest(timeoutTicks = 200)
public static void sinks(GameTestHelper h) { ...; h.runAfterDelay(150, () -> { h.assertTrue(...); h.succeed(); }); }
```
(1.21.1: `@GameTestHolder(MODID) @PrefixGameTestTemplate(false)` on the class, `@GameTest(template = "test_pad")`.)
- Template = `data/<modid>/structure/<name>.nbt`. Make one without NBT libraries:
  `python3 <skill>/scripts/make_test_structure.py out.nbt --size 9 10 9` (stone floor at y=0).
- **Coordinates**: on 1.21.1 the structure was placed one block above the test origin; on 26.x it starts at the
  origin. Power Armor's `test_pad` therefore has an empty bottom layer (9x11x9, floor at y=1), so the relative floor
  is y=1 and entities stand at y=2 on both (`helper.spawn(type, new BlockPos(4, 2, 4))` uses the bottom centre).
- **Mock server players** (`makeMockServerPlayerInLevel`) have a connection that never negotiated mod channels; on
  26.x NeoForge throws when anything sends them a mod packet (GeckoLib's `entity_anim_trigger`). Add the channels:
  `ChannelAttributes.getOrCreateCommonChannels(player.connection.getConnection(), ConnectionProtocol.PLAY).add(id)`.
- **Barrier box**: each test is sealed in barriers the size of the structure. Make it tall enough for tall
  entities (an entity whose eyes reach the ceiling takes suffocation damage, which looks like a bug).
- Tests run **side by side** (~14 blocks apart): search for entities in a small box around your own
  position, never `inflate(12)`.
- `helper.makeMockPlayer(GameType.SURVIVAL)` is not in the world and does not tick: good for
  `interact`, `startRiding`, `hurt`, inventories; not for air/movement over time.
- **Menus**: a mock `Player` silently ignores `openMenu`, and `helper.makeMockServerPlayerInLevel()` throws
  `Payload neoforge:advanced_open_screen may not be sent to the client` (its fake connection never negotiated
  NeoForge channels). Construct the menu directly (`new MyMenu(id, player.getInventory(), container, entity)`)
  and test `mayPlace`, `quickMoveStack`, `stillValid`, `clickMenuButton`; check the real screen in the autopilot
  (open it server-side with `entity.interact(serverPlayer, hand)` while `setShiftKeyDown(true)`, then screenshot).
- Things that tick on the server (fuel burn, timers) work with a mock pilot: set `pilot.zza = 1` /
  `setSprinting(true)` to simulate WASD. On 26.x a real `ServerPlayer`'s `xxa/zza` stay 0 (the input packet fills
  `getLastClientInput()` instead), so server code reads both (Power Armor: `SuitDriving.pilotInput`).
- Build scenes with `helper.setBlock`; use `helper.absolutePos/absoluteVec` for world coordinates.
- Trace a mystery failure by sampling state every few ticks (`runAfterDelay` loop) and putting it in the
  assertion message; `getLastDamageSource()` forgets after 40 ticks.
- `./gradlew runGameTestServer` exits non-zero on failure; grep the log for `failed at` / `required tests`.

## Autopilot (scripted real client)
A client-only `@EventBusSubscriber(value = Dist.CLIENT)` class, inert unless a system property is set
by a dedicated run config. On `ClientTickEvent.Post`:
1. On the `TitleScreen`, create a world:
   `mc.createWorldOpenFlows().createFreshLevel(name, new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(seed, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen)`
   or open a **copy** of a real save: copy `saves/<world>` (skip `session.lock`) to `saves/autopilot-x`
   and `mc.createWorldOpenFlows().openWorld("autopilot-x", onFail)`.
2. Set `mc.options.pauseOnLostFocus = false` (otherwise the pause menu opens when unfocused).
3. Do server-side setup on the integrated server thread: `mc.getSingleplayerServer().execute(() -> ...)`
   (spawn entities, `startRiding`, game rules, `setGameMode`, build scenes with `level.setBlock`).
4. Drive a script by tick: hold keys with `KeyMapping.setDown(true)` and **re-assert every tick**; set
   `player.setYRot/setXRot` (+ `yRotO/xRotO`); `options.setCameraType(...)`; `options.hideGui`.
   Stop sprinting explicitly (`setSprinting(false)`): vanilla keeps sprinting after the key is released.
5. Screenshots: `Screenshot.grab(mc.gameDirectory, "ap_name.png", mc.getMainRenderTarget(), msg -> {})`
   (saved in `run/screenshots/`). Take consecutive-tick shots to catch flicker.
6. Free cameras: a client-only `ArmorStand` with position, `yRot`, **`yHeadRot`** (the camera follows head
   yaw) and the `*O` fields set, then `mc.setCameraEntity(stand)`.
7. Log a CSV row per tick (position, speed from position deltas x20, vertical speed, onGround, state
   flags, animation name, camera eye y, player air/health). Log with your mod's `LOGGER` for quick numbers.
8. `mc.stop()` at the end; the Gradle task then exits. A full run takes ~20-40 s after startup.

Analysis: tile screenshots with `scripts/contact_sheet.py`; summarise CSVs per phase (average speed,
% airborne, drops, step-ups, collisions, animation counts, animation switches). Compare before/after runs.
Crop + upscale regions when the subject is small.

## Protect the user's world
- Never open the user's save. Copy it, run on the copy, and prove the original is unchanged:
  ```sh
  cd "run/saves/New World" && find . -type f ! -name session.lock | sort | while read f; do echo "$(md5 -q "$f") $f"; done > /tmp/before.txt
  # ... run ... then the same command | diff -q - /tmp/before.txt
  ```
  (Sort the file list: unsorted `find` order made a checksum look changed once.)
- Before copying, make sure the game isn't running on it (`pgrep -fl java`; a long-running Gradle daemon
  is not the game) and compare file mtimes with the session.

## What each layer caught on Power Armor
| Bug | Caught by |
|---|---|
| Suits spawned inside the GameTest floor (y offset), neighbour tests' entities found | GameTests |
| Pilot dismounted on top of the suit | GameTest assertion on dismount height |
| First-person view inside the helmet; shoulder pads flickering in the cockpit | autopilot screenshots |
| Jump animation started after lift-off | side-camera screenshot sequence |
| "Feels slow" = stale saved speed attribute (1.52 instead of 5 blocks/s) | terrain CSV + NBT inspection (`uv run --with nbtlib`) |
| Camera popping a block per step; fall pose on every slope block; arm flicker on descents | terrain CSV (eye y, anim switches) |
| Speed constant off by ~2% | autopilot speed log |
| Synced burn numbers stale after loading a saved suit | GameTest (save -> load -> compare) |
| Sprint exhaust plume too thick; HUD placement | autopilot screenshots (`ap_tp_run`, `ap_fuel_fp_hud`) |
| Terrain drive "stuck": the saved player was already riding an old unfuelled suit | terrain report (`powered_off` in every leg) |
