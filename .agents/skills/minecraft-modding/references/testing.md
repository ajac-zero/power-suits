# Testing and verification

Three layers, all used on every change: build, GameTests (logic, headless), autopilot (real client,
screenshots + per-tick CSV). Example implementations: `src/main/java/dev/ajaczero/powerarmor/test/`
and `client/dev/` in `~/Developer/PowerArmor`.

## GameTests (headless server)
```java
@GameTestHolder(MyMod.MODID) @PrefixGameTestTemplate(false)
public final class MyTests {
  @GameTest(template = "test_pad", timeoutTicks = 200)
  public static void sinks(GameTestHelper h) { ...; h.runAfterDelay(150, () -> { h.assertTrue(...); h.succeed(); }); }
}
```
- Template = `data/<modid>/structure/<name>.nbt`. Make one without NBT libraries:
  `python3 <skill>/scripts/make_test_structure.py out.nbt --size 9 10 9` (stone floor at y=0).
- **Coordinates**: the structure is placed one block above the test origin. Relative floor is y=1 and
  entities stand at y=2 (`helper.spawn(type, new BlockPos(4, 2, 4))` uses the bottom centre).
- **Barrier box**: each test is sealed in barriers the size of the structure. Make it tall enough for tall
  entities (an entity whose eyes reach the ceiling takes suffocation damage, which looks like a bug).
- Tests run **side by side** (~14 blocks apart): search for entities in a small box around your own
  position, never `inflate(12)`.
- `helper.makeMockPlayer(GameType.SURVIVAL)` is not in the world and does not tick: good for
  `interact`, `startRiding`, `hurt`, inventories; not for air/movement over time.
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
