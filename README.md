# Power Armor

A Minecraft mod (NeoForge 1.21.1 + GeckoLib 4.9) that adds a power armor suit you climb into,
Fallout / Titanfall style. Model and animations come from `~/Developer/Models/starcraft_marine.bbmodel`.

## Playing it

| Action | How |
|---|---|
| Get the suit | Creative tab *Tools & Utilities*, or craft it (iron blocks, iron helmet/chestplate/boots, pistons) |
| Deploy | Right-click the ground with the item (needs a 3-block-tall space) |
| Climb in | Right-click the suit |
| Walk / run | WASD (2.35 blocks/s) / sprint (6.35 blocks/s; double-tap W or Ctrl) |
| Jump | Hold Space to charge (horse jump bar), release |
| Climb out | Shift |
| Pack it up | Shift + right-click an empty suit (keeps its damage) |
| Repair | Anvil + iron ingots (the item's durability bar is the suit's health) |

While inside: the suit takes hits instead of you, it has no fall damage, steps up full blocks,
and the camera pulls back in third person (F5).

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
  test/PowerArmorTests.java  GameTests
src/main/resources/
  assets/powerarmor/geo|animations|textures/entity/   exported from Blockbench
  data/powerarmor/recipe/power_armor.json             crafting recipe
  data/powerarmor/structure/test_pad.nbt              floor used by the GameTests
```

## Notes

- Animation playback rates are tied to movement speed so the feet don't slide. The authored
  ground speeds were measured in Blockbench: walk 2.35, run 6.36, sneak 0.83 blocks/s.
  If you change `WALK_SPEED` / `SPRINT_MULTIPLIER`, the rates follow automatically.
- Airborne: `jump` (push-off) flows into the `fall` loop; `land` plays after ~0.5 s in the air.
- First person hides the suit's head, torso and hip shell (cockpit view: arms, pads, cannon stay).
- Animation sources (procedural specs) live in `~/Developer/Models/starcraft_marine_anim/`.
- Unused animations so far: attack, throw, stim, electrified, idle_fidget, sneakwalk.
