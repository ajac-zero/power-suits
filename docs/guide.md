# Power Armor

A Minecraft mod (Minecraft 26.3, NeoForge 26.3.0.76 + GeckoLib 5.5.7) that adds a power armor suit you climb into,
Fallout / Titanfall style. Model and animations come from `model/power_armor.bbmodel`.

Working notes for agents: `AGENTS.md` (the design spec and roadmap are kept outside the repo).

## Playing it

| Action | How |
|---|---|
| Get the suit | Creative tab *Tools & Utilities*, or craft it from its parts (see *Crafting the suit* below) |
| Deploy | Right-click the ground with the item (needs a 3-block-tall space) |
| Climb in | Right-click the suit |
| Suit menu | Sneak + right-click an empty suit: helmet slot, tool socket with its ammo slot beside it, engine slot, fuel slot, status, **Pack up** |
| Helmet | Right-click a suit with a Power Armor Helmet (or use the menu's helmet slot) to put it on. It seals the suit, and the machine mind only runs with it: without one the suit still runs with you inside (unsealed: fire, poison and water reach you, and you can eat), and an empty one stands powered down. Taking the helmet off is the easy way to park a suit. Rogues always wear one |
| Fuel | Put anything a furnace burns in the fuel slot; it lasts as many ticks as in a furnace (coal 1:20 of walking, blaze rod 2:00, lava bucket 16:40 and the bucket stays, planks 0:15) (for the standard Firebox engine; see *Engines* below) |
| Engines | The backpack is the suit's engine, swapped in the menu's engine slot. **Firebox** (a new suit has no engine: craft one): burns fuel, floods and dies when water covers it (no pack-up while flooded: drain the water or swap the engine). **Tidal Engine** (crafted with a Heart of the Sea; twin diving tanks that bubble under water): free while wet (its backpack under water, or in rain); on land it drinks water buckets (0:15) or bottles (0:05) from the fuel slot, so carry some to climb out of the water. **Star Reactor** (diamonds + an end crystal; glowing energy canisters drawing in ender particles): a Nether Star in the fuel slot runs everything, tools included, forever (bullets still take iron nuggets). Each engine is its own backpack on the suit |
| Walk / run | WASD (3.0 blocks/s walk; S backs up at half speed) / sprint (6.25 blocks/s; double-tap W or tap Ctrl once, it keeps going while W is held) |
| Jump | Hold Space to charge (the suit crouches), release |
| Climb out | Shift |
| Pack it up | **Pack up** button in the suit menu (keeps damage, name, fuel, tool, ammo, helmet and engine) |
| Install a tool | Put a power tool (Chaingun, EM Projector, Mining Drill, Chainsaw, Flamethrower, TNT Launcher, Rail Cannon) in the menu's socket slot; it appears on the suit's left arm |
| Your hand | Works as normal while piloting: the suit's right hand holds your selected item; left click punches, fights and mines, right click places, opens doors, throws potions... 1.5 blocks extra reach. No eating or drinking (you're sealed in): food in your hand counts as nothing to do, so right click goes to the tool |
| Use the tool | Right-click when your hand has nothing to do (empty hand, a sword, aiming at the air with blocks...), like a shield in the off hand; hold it as long as you need. The tool arm raises while in use |
| EM Projector | Hold right-click on a dropped item or mob: it's pulled in and held floating (items go into your inventory); release to throw it, tap to shove. 1 fuel/tick while holding, 80 per throw |
| Mining Drill | Hold right-click on a block: digs it and the 8 around it (a 3x3 face of a wall, floor or ceiling) like a diamond pickaxe + shovel. 20 fuel per block |
| Chainsaw | Hold right-click on wood or leaves to cut it; cutting a tree's trunk fells everything above the cut. 15 fuel per log |
| Flamethrower | Hold right-click: a stream of fire about 7 blocks toward your crosshair. Sets mobs and blocks alight, melts ice and snow, lights campfires and candles, sets off TNT, cooks food lying on the ground. Dead under water. 6 fuel per tick |
| TNT Launcher | Right-click: lobs a block of primed TNT toward your crosshair that blows up the moment it hits something (real TNT: it breaks blocks). Hold to keep firing, one round a second. Each shot uses one TNT from the suit's **ammo slot** (beside the socket in the menu; the HUD counts what's left); free in creative. No fuel |
| Rail Cannon | Hold right-click to charge (1.5 s, the core between its prongs glows brighter, the HUD shows a charge bar), let go at full charge: an instant beam along your crosshair, 20 hearts to every mob on the line (it goes through them all), breaking glass, panes, leaves and ice on the way; it stops at anything solid. Let go early and nothing happens. Each shot is an iron ingot from the suit's ammo slot + 100 fuel |
| Fire | Right-click while piloting (Chaingun): tap for heavy single shots, hold to spin up to a 10-a-second barrage toward your crosshair; one iron nugget per bullet (yours first, then the suit's store) |
| Repair | Anvil + iron ingots (the item's durability bar is the suit's health) |
| Sealed suit | While powered and wearing its helmet: fire, frost, magma, cactus/berry bushes and harmful effects (poison, wither, slowness...) don't reach you or the hull; keeping them out costs fuel. You breathe under water (0.5 fuel/tick extra). A running suit never catches fire (lava excepted: it burns while in it, the flames die as it steps out). Lava still eats the hull (wade through at a third of your speed). No power, no seal: an empty or knocked-out suit burns like anything else. What's already inside you stays yours: poison or wither you had when you climbed in (food poisoning) hurts you, not the suit |
| Strength | Every melee hit from inside (bare hand, sword, axe...) does +4 damage |
| Stomp | Landing from 3.5+ blocks hurts everything around you (not you): 2 damage per block fallen, up to 30, half at the edge of the shock wave (2-3.5 blocks); things get thrown. A normal jump doesn't stomp |
| Smashing through | Walking breaks leaves, glass panes, cobwebs, snow layers, berry bushes, bamboo...; sprinting also breaks glass and ice. Nothing drops. Block tags `powerarmor:suit_smashes_walking` / `_sprinting`; can be switched off in the config |
| Machine mind | A powered suit with its helmet on and nobody inside is driven by its own mind (green visor): it works for its owner (whoever last piloted it, or deployed it) from its home (the block where the pilot got out, or where it was deployed). Its job depends on the socket tool: none (or a tool whose job isn't built yet) = stand guard at home, look at people nearby, walk back if pushed away; **chaingun** = follow its owner like a tamed wolf (walks over 7 blocks away, sprints past 16, stops at 4, no teleporting) and defend them: it shoots whatever attacks the owner or the suit, whatever the owner attacks, and monsters within 16 blocks of the owner, never with the owner, a villager, a pet or another suit in the line of fire; with no nuggets left it walks up and punches (1 + melee bonus); **EM projector** = guard home: monsters within 12 blocks of home (config) are caught one by one, lifted overhead and flung ~30 blocks up (44 blocks/s, config) so the fall kills them; creepers are thrown up and away from home instead. Never animals, villagers, players or pets; **drill** = clear an open hall at home level, 3 blocks high, ring by ring outward from home within 12 blocks (config `work_radius`): it never digs down or into the ceiling, leaves a wall wherever a block touches lava or water and leaves columns standing over holes in the floor; **chainsaw** = fell the natural trees within 12 blocks of home, nearest first (never log cabins); **rail cannon** = a long-range guard at home: it charges and shoots monsters it can see within 40 blocks of home (config), and whatever attacks it or its owner, never with a friend anywhere on the beam; it needs iron ingots in the ammo slot. Drops stay on the floor: give allays the items you want (an allay given coal brings every coal it sees to you); the stone and dirt nobody collects despawn after 5 minutes. Design sec. 8. It burns fuel like a piloted suit (idle 0.25/tick): **to park a suit, take the fuel out** |
| Helmet styles | Easter eggs: five smithing templates hide in structure chests (ocean ruins and shipwrecks, trial chamber vaults, end cities, ancient cities, desert pyramids and jungle temples). At a smithing table, template + Power Armor Helmet + the object the template names gives that helmet its new look (name, icon and model change; it works the same). Helmet + iron block with no template gives the standard helmet back; 7 diamonds round a template and its object copy it. Rogues sometimes wear one. Design sec. 4c |
| Paint | Right-click the suit with any dye: its armour plates (shoulder pads, upper arms, chest, collar, belly plate, hips, thighs, the helmet's shell) take the colour and the dye is used up; another dye paints over it, a wet sponge washes it back to bare steel (the sponge stays wet). Only the look changes. A packed suit keeps its paint |
| Load ammo | Right-click the suit with iron nuggets: the stack goes into its hidden store (up to 640, config). The menu shows it (Bullets), a packed suit keeps it, a wreck drops it. The mind's chaingun uses it; yours too, after the nuggets in your own inventory |
| Rogue suits | Extremely rare (2% chance per in-game day, config): a rogue suit with a red visor turns up 48-64 blocks from a player. It needs no fuel or ammo and attacks any player or mob it sees: a chaingun rogue shoots, an EM rogue hurls (players too; a suit is too heavy to lift: it pins yours in place, you crawl at a quarter speed, then shoves it). It can't be boarded. Defeated, it drops only its tool; **struck by lightning** (a storm, a Channeling trident, a lightning rod) its mind reboots friendly with one coal's worth of fuel, and the first player to climb in claims it. It leaves after an in-game day once nobody is near. Your chaingun suits shoot rogues on sight. Test one: `/summon powerarmor:power_armor ~ ~ ~5 {Mind:"ROGUE",Inventory:{Items:[{Slot:1b,id:"powerarmor:chaingun",count:1}]}}` (or `powerarmor:em_projector`) |
| Lightning | A strike knocks a fuelled suit out: it convulses, slumps (you're stuck, the seal is off, so the fire the bolt leaves at your feet usually catches it) and restarts by itself after 5 s; the flames die as it powers up |

### Crafting the suit

Each part is crafted on its own, then torso, arms and legs are assembled like a body (at a crafting table):

| Item | Recipe |
|---|---|
| Power Armor Helmet | top row: iron block, redstone block, iron block; middle row: compass, glass, clock (they become the visor's compass and clock) |
| Power Armor Torso | iron blocks in the corners, copper blocks on the edges, a redstone block in the middle |
| Power Armor Arm (x2) | a row: copper block, piston, iron block (either way round) |
| Power Armor Leg (x2) | a column: copper block, piston, iron block (top to bottom) |
| Power Armor | arm, torso, arm across the top row; legs in the corners below |

The helmet isn't part of the assembly: put it on the finished suit (see *Helmet* above). A whole suit with its helmet
takes 12 iron blocks, 8 copper blocks, 2 redstone blocks, 4 pistons and glass (no lapis or furnaces since 2026-10-23). It comes out bare polished steel; paint it with dyes
(blue gives back the suit's old lapis blue).

The engines (swapped in the menu's engine slot; a new suit has none, so craft one before you fuel it):

| Engine | Recipe |
|---|---|
| Firebox | iron blocks in the corners, iron ingots on the edges, a blast furnace in the middle |
| Tidal Engine | iron blocks in the corners, dark prismarine on the edges, a Heart of the Sea in the middle |
| Star Reactor | iron blocks in the corners, diamonds on the edges, an end crystal in the middle (its Nether Star goes in the fuel slot) |

### Balance and config
Everything that costs fuel, plus damage, ranges and speeds, is in `config/powerarmor-common.toml` (in the
game folder, `run/config/` when developing) and in game under **Mods > Power Armor > Config**. Changes apply
at once. Your own visor colour is a separate file, `config/powerarmor-client.toml` (see *The visor and its HUD*). Fuel is counted in energy: walking burns 1 per tick, so 20 energy = 1 s of walking. Defaults (Phase 6
balance pass), with what one piece of coal (1600) buys:

| Use | Cost | Per coal |
|---|---|---|
| Walking / standing / sprinting (piloted or the machine mind) | 1 / 0.25 / 2 per tick | 80 s / 5:20 / 40 s |
| Jump | 20 | 80 jumps |
| Chaingun | 15 per bullet + 1 iron nugget, 1 per tick while spinning (4 hearts heavy, 1.5 barrage) | ~100 bullets |
| EM Projector | 1 per tick holding, 80 per throw | 80 s of holding, or 20 throws |
| Mining Drill | 20 per block (180 per 3x3) | ~9 faces (80 blocks) |
| Chainsaw | 15 per log | ~20 oaks (5 logs each) |
| Flamethrower | 6 per tick of flame (1.5 hearts per hit, then 5 s of burning) | ~13 s of flame |
| TNT Launcher | no fuel (config `energy_per_shot`): one TNT per round | - |
| Rail Cannon | 100 per shot + 1 iron ingot (20 hearts to everything on the beam) | 16 shots |
| Sealing | 20 per point of fire/frost damage kept out, 100 per harmful effect, +0.5 per tick of air under water | 80 s of burning |

The firebox burns what a furnace burns, as long as a furnace burns it: coal and charcoal 1600, blaze rod 2400, lava bucket 20000, coal block 16000, planks 300. Hover over any fuel for its walking time.

### Sounds
A powered suit is quiet: no engine loop. Now and then its backpack makes a soft sound (the firebox crackles, the tidal
pump bubbles under water, the star reactor hums), a little more often and louder as it works harder (walking, sprinting,
a tool running). The drill whirs, the chainsaw buzzes, the chaingun's motor whines, the flamethrower roars and the rail cannon hums while charging (subtitles included).
They are vanilla sounds re-pitched in `assets/powerarmor/sounds.json`: swap in your own `.ogg` files there.

### The visor and its HUD
With the helmet on you see the inside of the helmet from the cockpit (first person): dark steel pieces in the four
corners and the visor band in the middle of the top edge, like the pumpkin's view but only at the edges. While the suit
has power the glass along them catches a line of light, and the visor shows a HUD floating on the glass (no helmet, no
HUD: then vanilla's vehicle hearts show the hull). It's terminal green by default: pick your own colour under
**Mods > Power Armor > Config > Visor** (`visor_colour` in `config/powerarmor-client.toml`, `#RRGGBB`; only you see it).
- **Top left, the suit:** a blocky figure of the suit (one block per armour piece), **HULL** (its health, % and a bar)
  with a **shield** that's lit while the suit is sealed (running with the helmet on) and faded when not, and **FUEL**: how long it lasts at walking pace (∞ on a wet
  tidal engine or a star reactor; NO FUEL, also with no engine; FLOODED), a bar for how full the fuel slot is, and three
  **embers** for how fast it burns: one idling, two walking, three sprinting or with a hungry tool at work (the
  flamethrower), none when nothing burns.
- **Top right, the socket tool:** its symbol, one labelled bar and the ammo on a 3-digit counter (006; 999 means 999 or
  more; ∞ in creative; --- for a tool without ammo). The bar is the chaingun's **HEAT**, the rail cannon's **CHARGE**,
  the TNT launcher's **RELOAD**; the drill, chainsaw, EM projector and flamethrower show **COOL** (always ready, for now).
- **Top centre, compass and clock** (the helmet is crafted with both), as terminal text: a compass tape that slides a
  character (5°) at a time with a `^` under where you face, your heading in degrees (0 north, 90 east), the day and the
  time (`DAY 12  18:40`). Like the vanilla items they only work in the overworld: elsewhere the tape spins and they read
  `---` / `--:--`.
- The whole HUD turns **red** while something is critical: a quarter of the hull left, low fuel, the last rounds (16
  bullets, the last TNT or ingot), an overheated chaingun. With **no power** the glass goes dark: only the helmet's
  edge and a blinking red **NO FUEL** (or FLOODED) are left.
- While the HUD is up, vanilla's boss bars move down under the compass and the effect icons under the tool readout.

Fuel works like a furnace: the suit burns one item from the fuel slot at a time. It burns whenever
the suit is powered, piloted or not (the machine mind runs it): standing still uses a quarter of the walking rate, sprinting double, and each
jump a little extra. Under 30 seconds left the suit beeps and the exhaust coughs dark smoke. At zero the suit powers down and slumps; climb out, refuel it in the menu
and it stands back up (1.4 s). Fuel is needed in creative mode too. Suits from before this version
start empty: give them some coal.

**Chaingun** (crafted: iron ingots top and bottom rows; piston, iron block and dispenser in the middle). A cluster of
four barrels driven by the engine. **Tap** right-click for heavy single shots (4 hearts); **hold** it and the barrels
spin up over 2.5 s, firing faster and faster up to a barrage of 10 bullets a second (1.5 hearts each). Let go and they
coast down over 1.5 s; grab the trigger again before they stop and it picks up where it was. Each bullet uses fuel and
**one iron nugget** from your inventory (off hand first, like a bow's arrows; free in creative); the HUD shows how many
you carry and the barrels' heat. Bullets are hot tracers at 80 blocks/s that fly along your line of sight (whatever is in the crosshair is what gets hit;
they're drawn coming out of the gun and meet that line at the target). They ignore the half-second of immunity mobs
get after a hit (so every bullet of a barrage counts), push lightly instead of vanilla's knockback, and spark off
blocks without breaking them. **Overheat** (on by default, `overheat` in the config): 80 bullets without letting go and
the barrels glow white-hot and lock until they've cooled (3 s); the tips glow from a third of the way there. Kills
credit you ("shredded by"). It does nothing on foot. Old Plasma Cannons and Rivet Guns in your world become Chainguns.
With an empty socket or no fuel the trigger just clicks.

**EM Projector** (crafted: iron ingots in the corners, copper ingots top and bottom middle, redstone either
side of a lodestone). Grabs dropped items, mobs and players (players only where PvP is on) within 16 blocks; a rider is caught with their
mount. Other suits and the heavyweights in the entity tag `powerarmor:em_heavy` (bosses, iron golems, ravagers, wardens; a
datapack can add more) are too heavy to lift:
they fight the field, held in place and slowed to a crawl (a piloted suit moves at a quarter speed), dragged a little,
and letting go only shoves them. Never spectators, creative players or no-AI "statue" mobs.
Thrown mobs that hit a wall take damage ("slammed into a wall"). Socket only. A redstone electromagnet: four
copper-wound prongs round a redstone lens; the lens glows faintly while it's mounted in a powered suit and the prong
tips light up and spark while it holds something; the beam is redstone dust.

**Mining Drill** (crafted: a diamond on top, iron ingots + a diamond in the middle row, iron ingots + a
piston at the bottom). Diamond-tipped: it digs pickaxe and shovel blocks a diamond tool can harvest
(stone, ores, obsidian, dirt, sand, gravel, snow...), never bedrock/barriers, never wood (that's the
chainsaw's). Aim at a block within your reach (6 blocks inside the suit): the 3x3 around it in the plane of
the face you hit cracks together and breaks together, as slowly as its hardest block (stone 0.3 s, deepslate
0.6 s, obsidian 9.4 s), then a short pause before the next. Drops land where the blocks were (ores drop XP);
like vanilla, creative mode breaks without drops.

**Chainsaw** (crafted: iron ingots around shears and a piston). Cuts axe blocks (logs, planks, wooden
things) and leaves one at a time at diamond-axe speed. A cut log that belongs to a natural tree (logs
touching leaves that grew, not placed ones) brings down every connected log at or above the cut, top first,
3 logs per tick, 15 fuel each; leaves are left to decay as usual. Log cabins and other builds only lose the
block you cut. Both mining tools post Minecraft's block-break event for every block, so land-claim mods can
protect blocks from them.

**Flamethrower** (crafted: iron ingots, a bucket for the tank, copper, flint and steel, a blaze rod). Hold right-click
and it throws a stream of burning fuel, one gob a tick, about 7 blocks out: it arcs a little and takes a moment to get
there, so sweeping the nozzle sweeps the flame. Everything it washes over takes 1.5 hearts (about twice a second) and
burns for 5 s; fire-proof mobs (blazes, magma cubes, a fire resistance potion) don't care. Where it lands it melts ice
and snow, burns cobwebs away, lights campfires and candles, sets off TNT and now and then sets the block on fire (config
`ignites_blocks`; you must be allowed to build there, and claim mods see a block-place event). Raw food lying in the
stream cooks after about a second, the whole stack at once (what a smoker would make of it), and the stream sets no fire
next to dropped items. Under water it only bubbles. Runs on fuel alone: 6 per tick (a coal lasts about 13 s of flame).
Its pilot light glows blue whenever the suit is powered. With the machine mind it does the chaingun's job from close up
(follows and defends its owner, fires from 5.5 blocks, keeps a wider berth round friends) and never sets blocks on fire;
rogue suits sometimes carry one (and set fires if mobs may grief).

**TNT Launcher** (crafted: iron ingots, a dispenser, an iron block, gunpowder, copper). A revolver drum of TNT under a
fat barrel. Right-click lobs a block of primed TNT toward your crosshair (it arcs like an arrow) that blows up the moment it
hits a block or a mob, as real TNT: strength 4, breaking blocks unless the config says not to (`breaks_blocks`), and it
obeys the `tnt_explodes` game rule and land-claim mods. Hold to keep firing, one round each time it has reloaded (1 s,
config). The TNT comes from the suit's ammo slot, one block per shot (free in creative); with none left the trigger only
clicks. After each shot the drum turns a quarter turn to the next round. It's yours to fire only: the machine mind never
uses it and rogue suits never carry one.

**Rail Cannon** (crafted: iron ingots, copper ingots, an amethyst shard, an iron block). The chaingun's opposite: one
slow, critical shot instead of a barrage. Two steel prongs with a blue core between them, after DOOM's gauss cannon.
Hold right-click and it charges (1.5 s, config): the core glows brighter, a hum rises and a chime says it's full (the
HUD's bar turns white); it holds the charge as long as you hold the button. Let go at full charge and it fires an
instant beam along your crosshair, up to 64 blocks: every mob on the line takes 20 hearts (it goes through them all,
and through any hit immunity), and on the way it breaks what a sprinting suit smashes through (glass, panes, leaves,
ice, cobwebs; config `breaks_blocks`, claims can veto) until it meets anything solid. Let go early and it just winds
down: nothing is spent. A shot costs an iron ingot from the suit's ammo slot (free in creative) and 100 fuel, then it
cools for a second. With the machine mind it's a long-range guard: from home it shoots monsters it can see within 40
blocks of home (config `rail_guard_radius`) and whatever attacks it or its owner, never with a friend anywhere on the
beam (it goes on past the target), and its beam never breaks blocks. Rogue suits never carry one.

**Right hand**: shows your selected hotbar item and carries it forward (`hold_item`), reaching out each time
you use it (`use_item`). It's the suit's main hand, like yours: the tools sit on the left arm. In first person
the suit's gauntlet holds it at the lower right (cockpit viewmodel). It's a normal hand: left click attacks and mines
(at normal speed: riding doesn't count as "in the air" while the suit stands), right click uses the item. When the hand
has nothing to do, right click goes to the socket tool instead, exactly like vanilla tries your main hand and then a
shield in your off hand (your real off-hand item isn't used while piloting). A powered-down suit's hand can't do anything.

**Lights**: the firebox on the backpack (under the fuel hatch) glows through its grate while the engine runs (brighter under load,
dimmer when fuel runs low, embers dying out after shutdown), and the visor shows who is in control:
**amber** = a pilot (whatever they do), **green** = the friendly machine mind, **red** = a rogue mind (only rogues,
so red always means danger), **dark** = powered off; it stutters while starting up or shutting down and drops out
now and then on low fuel. Hits don't change it.
The chaingun's barrels glow with heat, the EM projector's redstone with its field and the rail cannon's core with its charge.

While inside: the suit takes hits instead of you, it has no fall damage, steps up full blocks,
and the camera pulls back in third person (F5).

Water (Fallout-style): the suit sinks and walks along the bottom at half speed. It never runs out
of air, but you do: once your head is under, your air bar drains and you drown in survival. Walk out
up a slope or stairs, or jump (weaker underwater).

## Developing

Minecraft 26.3 needs Java 25. It's installed with Homebrew (`brew install openjdk@25`) but not on the PATH by default.
Every terminal needs:

```sh
export JAVA_HOME=$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home
```

(add that line to `~/.zshrc` to make it permanent)

Worlds saved by the 1.21.1 version of the mod open in 26.3 (suits keep their fuel, tools, owner and home), but Minecraft
upgrades a world the first time it's opened and there's no way back: let it make the backup it offers.

| Command | What it does |
|---|---|
| `./gradlew runClient` | Starts Minecraft with the mod (offline dev account) |
| `./gradlew runGameTestServer` | Runs the automated in-game tests headless, prints pass/fail |
| `./gradlew build` | Builds the mod jar into `build/libs/` (put it in a real `mods/` folder, plus GeckoLib) |
| `node tools/export-model.mjs` | Re-exports model, animations and texture from the open Blockbench project |
| `./gradlew runAutopilot` | Dev tool: scripted test drive in a fresh flat world (pool, running out of fuel, chaingun range, right hand, EM projector, drill + chainsaw, tool items on foot), saves `run/screenshots/ap_*.png`, `run/autopilot_{water,fuel,cannon,em,mine}.csv`, quits |
| `./gradlew runAutopilotPose` | Dev tool (~30 s): the code-driven pose from fixed cameras (torso lean with the view pitch, tool raise, torso twist with the view yaw toward marker blocks); `run/screenshots/ap_pose_*.png`, `run/autopilot_pose.csv` |
| `./gradlew runAutopilotGait` | Dev tool (~45 s): foot slip and leg turning while walking, backing up, strafing, turning, sprinting; `run/autopilot_gait.csv`, `python3 tools/gait-report.py` |
| `./gradlew runAutopilotMech` | Dev tool: only the autopilot's last scene (~25 s): smashing through panes/leaves/glass/ice, a 9-block stomp on mobs, lightning, a punch, wading through lava + poison; `run/screenshots/ap_mech_*.png`, `run/autopilot_mech.csv`. A file `run/cleanzap` keeps the bolt's fire away (to see the knock-out animation) |
| `./gradlew runAutopilotTerrain` | Dev tool: drives a suit over real terrain in a **copy** of `run/saves/New World`, logs `run/autopilot_terrain.csv` (speed, height, ground contact, animation, camera) |
| `uv run --with pillow python3 tools/contact-sheet.py fp out.png` | Tiles autopilot screenshots (prefix `fp`, `tp`, `side`, `water`, `fuel`, `menu`) |
| `uv run --with pillow python3 tools/make-gui.py` | Rebuilds the suit menu background from vanilla's furnace screen |
| `uv run --with pillow python3 model/art/item_icons_art.py [--sheet out.png]` | Draws every item icon: hand-made 16 px pixel art (vanilla style), not rendered from the model; `--sheet` writes a review sheet |
| `python3 tools/extract-view-geo.py` | Rebuilds the cockpit viewmodel (`geckolib/models/view`) from the suit model (run after `export-model.mjs`) |
| `uv run --with pillow python3 tools/make-visor-textures.py` | Rebuilds the helmet edge pieces the pilot sees (`textures/gui/visor/`: corners, brow, their rim masks) |
| `uv run --with pillow python3 tools/make-glowmasks.py` | Rebuilds the glowmasks (`power_armor_glow_{fire,visor,heat,heat_barrel,em_core,em_arc}.png`) from the texture's reserved colours (run after `export-model.mjs`) |
| `uv run --with pillow python3 tools/make-paint-textures.py` | Rebuilds the painted textures (`textures/entity/paint/power_armor_<dye>.png`, one per dye) from the steel texture (run after `export-model.mjs`) |
| `./gradlew runAutopilotPaint` | Dev tool (~30 s): a suit right-clicked with dyes and a wet sponge; `run/screenshots/ap_paint_*.png` (in-game back view + front), `run/autopilot_paint.csv` (server/client paint, dyes left) |
| `./gradlew runAutopilotStyles` | Dev tool (~30 s): the helmet styles side by side (in-game back view + front, painted), the smithing table making one; `run/screenshots/ap_style_*.png`, `ap_menu_smithing.png`, `run/autopilot_style.csv` |
| `tools/view-tuner.sh start\|shoot\|stop` | Live-tunes the cockpit viewmodel's placement (`run/view/tuning.json`, shots `run/screenshots/ap_view_*.png`) |
| `python3 tools/terrain-report.py` | Summarizes `run/autopilot_terrain.csv` per leg (speed, air time, drops, step-ups, animations) |

## Where things live

```
src/main/java/dev/ajaczero/powerarmor/
  PowerArmor.java            entry point: registers everything
  registry/ModEntities.java  the entity type (hitbox size, pilot seat height)
  registry/ModItems.java     the item
  registry/ModMenus.java     the suit menu type; ModDataComponents.java: the loadout stored on a packed item
  entity/PowerArmorEntity.java  the suit as Minecraft sees it: vanilla hooks (riding, movement, water, jumping, damage,
                             death, saving), the synced data table, the slots, the tick that runs its parts:
    entity/SuitDriving.java  how it moves: the pilot's keys, walk/sprint speeds, stepping down ledges, sinking and walking
                             in water and lava, the charged jump, crawling in an EM field, the legs turning under the torso
    entity/SuitPower.java    the engine and its energy: burning fuel, flooding, free power, the lightning knock-out
    entity/SuitSocket.java   the arm socket's tool as the suit works it: trigger (pilot / mind), cooldown, synced spin/shots
    entity/SuitBrain.java    the machine mind's state: owner, home, aim, the job its tool gives it, rogues' gear and lifetime
    entity/SocketVisuals.java   client: the tool arm's raise and recoil, the installed tool's ToolEffects
    entity/SuitAnimator.java    client: measured speed, jumps/landings, step easing, which animation plays and how fast
    entity/SuitLights.java      client: fire, visor, engine glass, lightning sparks
  entity/SuitFuel.java       which items burn and for how long, per engine
  entity/FireboxFuel.java    asks a never-placed furnace how long it burns an item (the firebox's table)
  entity/SuitEngine.java     the engines (firebox / tidal / reactor); item/EngineItem.java: their items
  entity/ExhaustSmoke.java   client-side smoke from the back pipes
  menu/PowerArmorMenu.java   menu logic: socket, fuel, helmet + engine slots, shift-click, Pack up button
  item/SuitLoadout.java      fuel/tool/helmet/engine/half-burnt item carried by a packed suit item
  item/HelmetStyle.java      the helmet styles (design sec. 4c); SuitHelmetItem: name + flavour line, HelmetTemplateItem: the templates
  item/PowerTool.java        what a socket tool is: fuel per use, cooldown, model bone, use(); worked by an operator
                             (the pilot, or the suit itself while its mind drives: aim, ammo, credit); its mind job
                             (mindUse: entity/ai/ToolUse, and its own goals: mindGoals) and its looks (createEffects)
  item/ToolEffects.java      client: one suit's looks for its tool (moving parts, lights = ToolLight, shot effects);
                             one per tool: ChaingunEffects, TntLauncherEffects, EmProjectorEffects (+ the EM beam),
                             FlamethrowerEffects, RailCannonEffects (+ the rail beam, drawn by client/RailBeamRenderer)
  entity/SuitMind.java       friendly / rogue (synced: the visor colour)
  entity/ai/SuitGoals.java   the goals every machine mind has (vanilla goal system): walk home, look around, follow
                             the owner, pick a target; a rogue wanders
  entity/ai/ (tool jobs)     each tool's own goals, learned when it goes into the socket (PowerTool.mindGoals):
                             GunAttack (chaingun, flamethrower: shoot / punch, base AttackGoal), EmGuard + RogueEmAttack,
                             RailGuard, DrillWorker + SawWorker (base Worker)
  entity/ai/SuitTargets.java who the mind may shoot, what to shoot first, line-of-fire check
  entity/ai/SuitWork.java    where the drill and chainsaw minds may work (the hall, natural trees)
  entity/RogueSpawner.java   rogue suits turning up once in a while (saved day countdown, like the wandering trader)
  item/ChaingunItem.java     the first tool: spin-up, nugget ammo, overheat (continuous tool; also a GeckoLib item)
  item/EmProjectorItem.java  the second tool: grab / hold / collect / throw, slam damage (per-suit state in Beam)
  item/SocketAim.java        muzzle position, crosshair aim point, entity under the crosshair
  item/DrillItem.java        the third tool: 3x3 digging at diamond pickaxe/shovel speed
  item/ChainsawItem.java     the fourth tool: cuts wood, fells natural trees top-down
  item/RailCannonItem.java   charge, fire on release, the piercing beam (blocks it breaks, mobs it hits); the mind's job is
                             entity/ai/RailGuard; item/RailCannonEffects + client/RailBeamRenderer draw the beam
  item/ToolMining.java       shared block breaking for both (BreakEvent, drops, crack overlay, progress)
  entity/Bullet.java         the tracer bullet (flight, hit without immunity, sparks off blocks)
  network/ToolTriggerPayload.java  client -> server "attack pressed/released"
  item/PowerArmorItem.java   deploying / packing / repairing
  PilotProtection.java       redirects damage from the pilot to the suit (not "suffocating" against a wall)
  SuitSeal.java              sealed suit: fire/frost/effects kept out for fuel, air under water (tag damage_type/sealed_out)
  entity/SuitStomp.java      landing dust, stomp shock wave (damage type powerarmor:stomp)
  entity/SuitSmash.java      breaking leaves/panes/glass/ice while walking/sprinting (block tags suit_smashes_*)
  PilotHands.java            hand needs power, +1.5 reach, +4 melee damage
  PowerArmorConfig.java      balance knobs (config/powerarmor-common.toml)
  registry/ModSounds.java    engine + tool sound events (audio picked in assets/powerarmor/sounds.json)
  client/                    rendering only (never loaded on servers)
    PowerArmorModel.java     points GeckoLib at the asset files
    PowerArmorRenderer.java  copies the suit's state for the frame (SuitRenderData), aims the torso/head, raises the tool
                             arm, shows the installed tool; draws only the legs in first person (cockpit)
    SuitRenderData.java      what the renderer and its layers read from the suit each frame (GeckoLib DataTickets)
    PilotView.java           hides the pilot and vanilla hands, pulls the camera back
    CockpitHands.java        first-person viewmodel: socket tool on the left, gauntlet + held item on the right
    CockpitView*.java        its GeckoLib animatable/renderer (geckolib/models/view, cut from the suit by tools/extract-view-geo.py)
    PowerArmorScreen.java    draws the suit menu (preview, flame, status display, Pack up)
    SuitHud.java             the pilot's HUD (suit + tool readouts, compass + clock) + low-fuel beep; moves boss bars
                             and effect icons out of its way
    SuitVisor.java           the helmet's edge around the first-person view (pieces + the glass rim's light)
    VisorConfig.java         the player's own visor colour (CLIENT config, powerarmor-client.toml)
    ToolInput.java           right click the hand passes on -> socket tool (vanilla's off-hand turn)
    HeldItemLayer.java       draws the pilot's selected item in the suit's right hand (third person)
    SuitGlowLayer.java       the suit's lights + the tool's ToolLights (glowmasks, tinted per frame)
    BulletRenderer.java      the glowing tracer streak
    ToolRotors.java          turns the chaingun's barrels / the TNT drum, shakes the rail cannon (suit + cockpit viewmodel)
    ClientSetup.java         registers the renderer
    PowerArmorClient.java    client-only @Mod entry: the visor config + the in-game config screen
    SuitSounds.java          engine sounds now and then + tool loops (tickable sounds that follow the suit)
    FuelTooltip.java         "Power Armor fuel: 1:20 of walking" on every fuel
src/dev/java/dev/ajaczero/powerarmor/   development tools: compiled against the mod, NOT in the jar players get
  dev/PowerArmorDev.java     their @Mod entry point (dev runs load src/main + src/dev as one mod, see build.gradle)
  test/PowerArmorTests.java  GameTests (@SuitTest methods, registered by test/SuitTests.java)
  client/dev/AutoPilot.java  scripted screenshot test drive (only with -Dpowerarmor.autopilot); *Scene.java: its scenes,
                             TerrainPilot (real terrain), ShowcaseScene (the mod page's pictures)
  dev/MultiplayerScript.java + client/dev/MultiplayerScene.java   the scripted multiplayer check
src/dev/resources/data/powerarmor/structure/test_pad.nbt   floor used by the GameTests (9x11x9, floor on its second layer)
src/main/resources/
  assets/powerarmor/geckolib/models|animations/entity/, textures/entity/   exported from Blockbench
  assets/powerarmor/textures/gui/power_armor.png      suit menu background (tools/make-gui.py)
  assets/powerarmor/geckolib/models/view/power_armor_view.geo.json  cockpit viewmodel: tools + gauntlet cut out of the suit model (tools/extract-view-geo.py)
  assets/powerarmor/models/item/chaingun.json         a flat hand-held item, like a vanilla tool (item/handheld)
  assets/powerarmor/items/<item>.json                 which model each item shows (needed since 1.21.4)
  assets/powerarmor/textures/item/*.png               every item icon, 16 px pixel art drawn by model/art/item_icons_art.py
  data/powerarmor/damage_type/bullet.json             "shredded" damage type (+ minecraft is_projectile, no_knockback, bypasses_cooldown tags)
  data/powerarmor/damage_type/em_slam.json            "slammed into a wall" (EM throws)
  data/powerarmor/recipe/*.json                       crafting recipes (suit parts, suit + 4 tools)
```

## Notes

- Animation playback rates follow the real speed along the legs so the feet don't slide (backing up plays the walk in
  reverse, `walk_back`). The authored ground speeds were measured in Blockbench: walk 2.218, run 6.249 blocks/s; the game
  walks at 3.0 (the walk steps 1.35x faster) and sprints at 6.25. Tap sprint once: it keeps going while W is held.
- The walk and run keep the tool arm stabilized (IK on the arm), so it stays steady in the cockpit view.
- Cockpit feel: the view stays level (no camera bob); the FOV widens a little with speed, and the viewmodel sways with the
  gait under vanilla's View Bobbing option.
- Terrain: walking off ledges up to 2 blocks steps down instead of falling (keeps speed, no fall
  pose), and step-ups/downs are eased for the camera and the model over ~6 ticks.
- Saved suits: Minecraft stores attribute base values with each entity. `readAdditionalSaveData`
  resets them to the current tuning (`BASE_STATS`), so suits placed with older versions get new speeds.
  If you change `SuitDriving.WALK_BLOCKS_PER_SECOND` / `SPRINT_MULTIPLIER`, the rates follow automatically.
- Jumping: holding Space plays `jump_charge` (crouch); release plays `jump` (push-off) and the suit
  launches 2 ticks later, then `fall` loops; `land` plays after ~0.5 s in the air. Other players
  see the jump via a synced counter bumped by the server.
- Turning: legs (`yBodyRot`) follow the direction of travel; the torso (with arms and tool) twists
  up to 90 degrees and leans to face where the pilot looks (`PowerArmorRenderer.adjustModelBonesForRender`).
- First person: the eye sits at the visor (0.55 forward), the head/torso/hip shell is hidden and the
  suit is drawn with back-face culling, so no insides of cubes show.
- Animation sources (procedural specs) live in `model/anim/`.
- Power: `shutdown` (slump, hold) when the fuel runs out, `powered_off` (its last frame) for suits that were
  already off when they came into view, `startup` (1.4 s, movement blocked meanwhile) when fuel returns.
  The torso stops following the view while powered down. Spec: `model/anim/a_power.js`.
- Socket: the left arm ends in an elbow socket and the installed tool is the forearm, carried at the hip (rest pose:
  upper arm 18 deg forward, elbow 62 deg); `socket` (coupling plate) shows when empty, the installed
  tool's bone (`chaingun`, `em_projector`, `drill`, `chainsaw`) otherwise (`PowerArmorRenderer`). The EM push plays `tool_recoil` (0.5 s overlay, `a_tool_recoil.js`); the chaingun's barrels spin and its shots kick in code (`ToolRotors`, `PowerArmorRenderer`), and `tool_recoil`
  is triggered per shot; it must stay shorter than the cooldown because GeckoLib won't restart a running one.
- Mining tools: while a continuous tool runs, the suit syncs `TOOL_RUNNING` and a fourth controller (`tool`)
  loops the tool's own animation: `drill_run` spins the `drill_bit` 4 turns/s and buzzes the head,
  `chainsaw_run` shimmers the chain (`a_tools.js`). They key only the tool bones, so the arm keeps following
  walk/run and the aim.
- Right hand: `right_hand` grip bone (replaced the grenade prop) and a GeckoLib controller (`right_hand`) that owns the
  right arm while you hold something; `use_item` is triggered on every swing of the pilot's arm (`a_hand.js`).
- Arms swapped (2026-10-13): the hand used to be on the left and the tools on the right. The swap mirrored the
  whole model across x = 0 in Blockbench (geometry, rest poses, every keyframe, left/right names), so every
  animation stayed consistent (the gait now leads with the other foot). The specs in `model/anim/` are
  still written for the old rig; its `kit.json` makes the bake tools mirror them onto the new one.
- Unused animations so far: attack (3-round burst), idle_fidget (throw, stim and sneakwalk were removed 2026-10-18).
