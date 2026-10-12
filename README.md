# Power Armor

A Minecraft mod (NeoForge 1.21.1 + GeckoLib 4.9.3) that adds a power armor suit you climb into,
Fallout / Titanfall style. Model and animations come from `~/Developer/Models/starcraft_marine.bbmodel`.

Design spec: `docs/design.md`. Next steps: `docs/implementation-plan.md`. Working notes for agents: `AGENTS.md`.

## Playing it

| Action | How |
|---|---|
| Get the suit | Creative tab *Tools & Utilities*, or craft it (iron blocks, iron helmet/chestplate/boots, pistons) |
| Deploy | Right-click the ground with the item (needs a 3-block-tall space) |
| Climb in | Right-click the suit |
| Walk / run | WASD (5 blocks/s jog) / sprint (10 blocks/s; double-tap W or Ctrl) |
| Jump | Hold Space to charge (the suit crouches), release |
| Climb out | Shift |
| Pack it up | Shift + right-click an empty suit (keeps its damage) |
| Repair | Anvil + iron ingots (the item's durability bar is the suit's health) |

While inside: the suit takes hits instead of you, it has no fall damage, steps up full blocks,
and the camera pulls back in third person (F5).

Water (Fallout-style): the suit sinks and walks along the bottom at half speed. It never runs out
of air, but you do: once your head is under, your air bar drains and you drown in survival. Walk out
up a slope or stairs, or jump (weaker underwater).

## Developing

Java 21 is installed with Homebrew but not on the PATH by default. Every terminal needs:

```sh
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
```

(add that line to `~/.zshrc` to make it permanent)

| Command | What it does |
|---|---|
| `./gradlew runClient` | Starts Minecraft with the mod (offline dev account) |
| `./gradlew runGameTestServer` | Runs the automated in-game tests headless, prints pass/fail |
| `./gradlew build` | Builds the mod jar into `build/libs/` (put it in a real `mods/` folder, plus GeckoLib) |
| `node tools/export-model.mjs` | Re-exports model, animations and texture from the open Blockbench project |
| `./gradlew runAutopilot` | Dev tool: scripted test drive in a fresh flat world (incl. a pool), saves `run/screenshots/ap_*.png` and `run/autopilot_water.csv`, quits |
| `./gradlew runAutopilotTerrain` | Dev tool: drives a suit over real terrain in a **copy** of `run/saves/New World`, logs `run/autopilot_terrain.csv` (speed, height, ground contact, animation, camera) |
| `uv run --with pillow python3 tools/contact-sheet.py fp out.png` | Tiles autopilot screenshots (prefix `fp`, `tp`, `side`, `water`) |
| `python3 tools/terrain-report.py` | Summarizes `run/autopilot_terrain.csv` per leg (speed, air time, drops, step-ups, animations) |

## Where things live

```
src/main/java/dev/ajaczero/powerarmor/
  PowerArmor.java            entry point: registers everything
  registry/ModEntities.java  the entity type (hitbox size, pilot seat height)
  registry/ModItems.java     the item
  entity/PowerArmorEntity.java  the suit: riding, movement, damage, death, animation choice
  item/PowerArmorItem.java   deploying / packing / repairing
  PilotProtection.java       redirects damage from the pilot to the suit
  client/                    rendering only (never loaded on servers)
    PowerArmorModel.java     points GeckoLib at the asset files, head follows your view
    PowerArmorRenderer.java  hides the head in first person
    PilotView.java           hides the pilot and hand, pulls the camera back
    ClientSetup.java         registers the renderer
    dev/AutoPilot.java       scripted screenshot test drive (only with -Dpowerarmor.autopilot)
  test/PowerArmorTests.java  GameTests
src/main/resources/
  assets/powerarmor/geo|animations|textures/entity/   exported from Blockbench
  data/powerarmor/recipe/power_armor.json             crafting recipe
  data/powerarmor/structure/test_pad.nbt              floor used by the GameTests
```

## Notes

- Animation playback rates are tied to movement speed so the feet don't slide. The authored
  ground speeds were measured in Blockbench: walk 5.0, run 9.99, sneak 0.83 blocks/s.
- The walk and run keep the cannon stabilized (IK on the arm), so it stays steady in the cockpit view.
- Cockpit feel: the view dips with the hips on each footfall, and the FOV widens a little with speed.
- Terrain: walking off ledges up to 2 blocks steps down instead of falling (keeps speed, no fall
  pose), and step-ups/downs are eased for the camera and the model over ~6 ticks.
- Saved suits: Minecraft stores attribute base values with each entity. `readAdditionalSaveData`
  resets them to the current tuning (`BASE_STATS`), so suits placed with older versions get new speeds.
  If you change `WALK_SPEED` / `SPRINT_MULTIPLIER`, the rates follow automatically.
- Jumping: holding Space plays `jump_charge` (crouch); release plays `jump` (push-off) and the suit
  launches 2 ticks later, then `fall` loops; `land` plays after ~0.5 s in the air. Other players
  see the jump via a synced counter bumped by the server.
- Turning: legs (`yBodyRot`) follow the direction of travel; the torso (with arms and cannon) twists
  up to 90 degrees and leans to face where the pilot looks (`PowerArmorModel.setCustomAnimations`).
- First person: the eye sits at the visor (0.55 forward), the head/torso/hip shell is hidden and the
  suit is drawn with back-face culling, so no insides of cubes show.
- Animation sources (procedural specs) live in `~/Developer/Models/starcraft_marine_anim/`.
- Unused animations so far: attack, throw, stim, electrified, idle_fidget, sneakwalk.
