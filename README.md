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
| Suit menu | Sneak + right-click an empty suit: fuel slot, tool socket, status, **Pack up** |
| Fuel | Put coal/charcoal (1:20 of walking each), lava buckets (2:40, the bucket stays) or blaze rods (4:00) in the fuel slot |
| Walk / run | WASD (5 blocks/s jog) / sprint (10 blocks/s; double-tap W or Ctrl) |
| Jump | Hold Space to charge (the suit crouches), release |
| Climb out | Shift |
| Pack it up | **Pack up** button in the suit menu (keeps damage, name, fuel and tool) |
| Install a tool | Put a power tool (Plasma Cannon) in the menu's socket slot; it appears on the suit's right arm |
| Use items | Right-click as usual while piloting: the suit's left hand holds your selected item (place blocks, eat, open doors, buckets...). 1.5 blocks extra reach |
| EM Projector | Hold attack on a dropped item or mob: it's pulled in and held floating (items go into your inventory); release to throw it, tap to shove. 2 fuel/tick while holding, 200 per throw |
| Fire | Hold attack (left click) while piloting: a plasma bolt every 0.6 s toward your crosshair (300 fuel = 15 s of walking, 5 hearts) |
| Repair | Anvil + iron ingots (the item's durability bar is the suit's health) |

Fuel works like a furnace: the suit burns one item from the fuel slot at a time. It only burns
while someone is inside: standing still uses a quarter of the walking rate, sprinting double, and each
jump a little extra. The gauge right of the hotbar shows the flame of the burning item, what's left in
the slot and how long that lasts at walking pace. Under 30 seconds left it flashes and beeps and the
exhaust coughs dark smoke. At zero the suit powers down and slumps; climb out, refuel it in the menu
and it stands back up (1.4 s). Fuel is needed in creative mode too. Suits from before this version
start empty: give them some coal.

**Plasma Cannon** (crafted: iron ingots top and bottom rows, blaze rod + diamond + glass in the middle).
It does nothing on foot. Bolts fly at 60 blocks/s from the muzzle to whatever is under your crosshair, set
targets alight for a second, never break blocks, and fizzle after ~90 blocks or in water. Kills credit you
("vaporized by"). While piloting, left click never punches or mines; with an empty socket or no fuel the
trigger just clicks.

**EM Projector** (crafted: iron ingots in the corners, copper ingots top and bottom middle, redstone either
side of a lodestone). Grabs dropped items and mobs within 16 blocks, never players, bosses, other suits,
no-AI "statue" mobs, riders/ridden mobs or very big mobs (a ravager is too heavy, an iron golem is fine).
Thrown mobs that hit a wall take damage ("slammed into a wall"). Socket only.

**Left hand**: shows your selected hotbar item and carries it forward (`hold_item`), reaching out each time
you use it (`use_item`). In first person the item is drawn at the lower left like vanilla's off-hand (the
suit's arm hangs below the cockpit view). The hand never attacks or mines (left click is the tool's), and a
powered-down suit's hand can't use anything.

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
| `./gradlew runAutopilot` | Dev tool: scripted test drive in a fresh flat world (pool, running out of fuel, cannon range, left hand, EM projector, tool items on foot), saves `run/screenshots/ap_*.png`, `run/autopilot_{water,fuel,cannon,em}.csv`, quits |
| `./gradlew runAutopilotTerrain` | Dev tool: drives a suit over real terrain in a **copy** of `run/saves/New World`, logs `run/autopilot_terrain.csv` (speed, height, ground contact, animation, camera) |
| `uv run --with pillow python3 tools/contact-sheet.py fp out.png` | Tiles autopilot screenshots (prefix `fp`, `tp`, `side`, `water`, `fuel`, `menu`) |
| `uv run --with pillow python3 tools/make-gui.py` | Rebuilds the suit menu background from vanilla's furnace screen |
| `python3 tools/extract-tool-geo.py` | Rebuilds the tool item models from the suit model (run after `export-model.mjs`) |
| `python3 tools/terrain-report.py` | Summarizes `run/autopilot_terrain.csv` per leg (speed, air time, drops, step-ups, animations) |

## Where things live

```
src/main/java/dev/ajaczero/powerarmor/
  PowerArmor.java            entry point: registers everything
  registry/ModEntities.java  the entity type (hitbox size, pilot seat height)
  registry/ModItems.java     the item
  registry/ModMenus.java     the suit menu type; ModDataComponents.java: the loadout stored on a packed item
  entity/PowerArmorEntity.java  the suit: riding, movement, fuel/power, damage, death, animation choice
  entity/SuitFuel.java       which items burn and for how long
  entity/ExhaustSmoke.java   client-side smoke from the back pipes
  menu/PowerArmorMenu.java   menu logic: fuel + socket slots, shift-click, Pack up button
  item/SuitLoadout.java      fuel/tool/half-burnt item carried by a packed suit item
  item/PowerTool.java        what a socket tool is: fuel per use, cooldown, model bone, use()
  item/PlasmaCannonItem.java the first tool: spawns the bolt toward the crosshair (also a GeckoLib item)
  item/EmProjectorItem.java  the second tool: grab / hold / collect / throw, slam damage (per-suit state in Beam)
  item/SocketAim.java        muzzle position, crosshair aim point, entity under the crosshair
  entity/EmBeamEffects.java  client-side violet beam particles
  entity/PlasmaBolt.java     the projectile (flight, hit, damage, impact effects)
  network/ToolTriggerPayload.java  client -> server "attack pressed/released"
  item/PowerArmorItem.java   deploying / packing / repairing
  PilotProtection.java       redirects damage from the pilot to the suit
  PilotHands.java            no punching/mining from inside, hand needs power, +1.5 reach
  client/                    rendering only (never loaded on servers)
    PowerArmorModel.java     points GeckoLib at the asset files, head follows your view
    PowerArmorRenderer.java  hides the head in first person
    PilotView.java           hides the pilot and hand, pulls the camera back
    PowerArmorScreen.java    draws the suit menu (preview, flame, status display, Pack up)
    FuelGauge.java           HUD fuel gauge + low-fuel beep
    ToolInput.java           attack button -> socket tool (cancels vanilla punching while piloting)
    HeldItemLayer.java       draws the pilot's selected item in the suit's left hand (third person)
    FirstPersonLeftHand.java draws it vanilla-style at the lower left in the cockpit
    PlasmaBoltRenderer.java  the glowing bolt (camera-facing quad)
    ClientSetup.java         registers the renderer
    dev/AutoPilot.java       scripted screenshot test drive (only with -Dpowerarmor.autopilot)
  test/PowerArmorTests.java  GameTests
src/main/resources/
  assets/powerarmor/geo|animations|textures/entity/   exported from Blockbench
  assets/powerarmor/textures/gui/power_armor.png      suit menu background (tools/make-gui.py)
  assets/powerarmor/geo/item/plasma_cannon|em_projector.geo.json   cut out of the suit model (tools/extract-tool-geo.py)
  assets/powerarmor/models/item/plasma_cannon.json    how the cannon sits in your hand / inventory
  data/powerarmor/damage_type/plasma.json             "vaporized" damage type (+ minecraft is_projectile tag)
  data/powerarmor/damage_type/em_slam.json            "slammed into a wall" (EM throws)
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
- Power: `shutdown` (slump, hold) when the fuel runs out, `powered_off` (its last frame) for suits that were
  already off when they came into view, `startup` (1.4 s, movement blocked meanwhile) when fuel returns.
  The torso stops following the view while powered down. Spec: `starcraft_marine_anim/a_power.js`.
- Socket: the forearm block is the socket housing; `socket` (coupling plate) shows when empty, the installed
  tool's bone (`cannon`, `em_projector`) otherwise (`PowerArmorRenderer`). The EM push reuses `plasma_shot` as `tool_recoil`. `plasma_shot` (0.5 s overlay, `a_plasma_shot.js`)
  is triggered per shot; it must stay shorter than the cooldown because GeckoLib won't restart a running one.
- Left hand: `left_hand` grip bone (replaced the grenade prop) and a third GeckoLib controller that owns the
  left arm while you hold something; `use_item` is triggered on every swing of the pilot's arm (`a_hand.js`).
- Unused animations so far: attack (3-round burst), throw (lost its grenade prop), stim, electrified, idle_fidget, sneakwalk.
