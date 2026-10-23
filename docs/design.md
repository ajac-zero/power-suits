# Power Armor: design spec

Status: agreed direction as of 2026-10-12. Items marked **OPEN** need the user's decision before
they are built. `docs/design-notes.md` keeps the history of how we got here.

## 0. Identity (decided 2026-10-13)
**A coal-fired, steam-and-diesel machine, not sci-fi.** Inspirations, not targets: Fallout's power armor and
BioShock's Big Daddies (heavy plates, a glowing visor, a drill, a rivet gun); don't replicate either. Everything it does is powered by the firebox burning your fuel; the glow comes
from fire, hot metal, redstone and sparks, never from futuristic energy.
- **Material palette: polished steel, dark iron / slate, paint from dyes; light only from fire and glow.** (Until
  2026-10-20 the armour was painted blue; now it is bare steel and the player paints its plates, see *Paint*.) No small accents in a new
  material (brass trim on the visor and firebox was tried 2026-10-13 and dropped: it read as stuck on).
- Built 2026-10-13 (step 1): firebox, glowmask layer (`SuitGlowLayer`), visor states.
- Chest: the blue core becomes a **firebox** (grated window, orange glow, flickers with load, fades with fuel, dark
  when off). Glow is drawn from glowmask textures (full-bright, tinted per frame).
- Visor: **the whole visor glows and shows the suit's state** (the helmet's own steel is its frame): green = parked with power (free to climb in), amber =
  piloted, red = tool in use or just hit, dark = powered off, flickering while starting up, shutting down or low on fuel.
  **Changes with the suit AI (sec. 8):** amber = piloted, always (no red for tool use or hits), green = friendly AI,
  red = rogue AI only, dark = off; no flash or flicker on a hit.
- Plasma cannon -> **rivet gun** (built, then replaced by the chaingun below): fires red-hot rivets using **iron nuggets from the pilot's inventory** (like a bow
  uses arrows; creative: free) plus fuel for steam pressure. Rivets **stick in blocks** (not mobs) for a while and
  drop back as nuggets. Item id `rivet_gun`, with `plasma_cannon` registered as an alias so existing items survive.
  Raising it opens its steam vent; per shot the piston slams, the ammo drum turns, steam puffs; no muzzle-flash block.
  Built 2026-10-13 (step 2).
- **Rivet gun -> chaingun (decided 2026-10-13, after trying the rivet gun):** the user wasn't moved by the rivet gun. The
  chaingun spins up: tapping fires single heavy shots (like the rivet gun), holding speeds the barrel cluster up to a
  barrage. One iron nugget per bullet. Bullets ignore the half-second hit immunity. Optional overheat (config). Model:
  compact like the EM projector / drill (motor housing + short spinning cluster of barrels), no long thin barrel, no
  extra accessories (sight, magazine, drum, gauge). Name: Chaingun (pairs with Chainsaw). Built 2026-10-13 (step 2).
- EM projector -> **redstone electromagnet** (built 2026-10-13, step 3): the user kept the four prongs; the copper (it's in
  the recipe) is wound round each prong like an electromagnet's iron cores instead of two big rings round all four (too
  thick), the core no longer sticks out between the prongs but is a redstone lens set into the housing, the prong tips
  are redstone (like redstone torch heads). Prongs + windings halved after the first in-game look (it was the biggest
  tool), a pixel of steel left showing between copper and tip, then given the same 7x6x7 blue forearm block as the
  other tools (without it the projector looked like a head without an arm), then the prongs 2 px longer again (3 px of
  bare steel between copper and tip): 13 px long, the others 15-16. Glow (`em_core` lens + vents: faint while mounted and powered, brighter while used, throbbing while
  holding; `em_arc` tips: only while holding, crackling), redstone-dust beam (red + orange-red strands; a dark-red
  strand read as blood round a pig), red sparks between the tips. No ammo.
- **Paint (built 2026-10-20, user):** the suit is bare polished steel (`model/art/steel_art.py`: the old blue's shades
  mapped onto a steel ramp with cool shadows and warm glints). Right-click it with a dye to paint its plates (shoulder
  pads, upper arms, chest + breastplate, hip hoods, thighs: "two-tone"; since 2026-10-20 also the helmet's shell, the
  collar and the abdomen plate); the visor's band and grill, gauntlets, shins + boots (one piece with the feet),
  backpack and the ribbed waist stay steel. A new dye paints over the old one (the dye is used up, not
  in creative; the same colour again does nothing); a wet sponge washes it off (the sponge stays wet). Texture only:
  one texture per dye (`tools/make-paint-textures.py`: the steel ramp's 8 shades become the dye's 8 on the plates);
  blue is the old lapis blue exactly. Saved with the suit (`Paint`), kept by a packed suit (`powerarmor:paint`
  component, shown in the tooltip). Rogues can't be painted (they don't let anyone near).

## 1. Pillars
1. **A machine you climb into** (Fallout / Titanfall), not wearable armor. Heavy, tall (3 blocks), fast.
2. **Special-purpose, not a daily driver.** It burns fuel, shuts down when empty, and its strong
   abilities come from power tools that cost a lot of fuel.
3. **Readable feedback.** Exhaust smoke, sounds, a fuel gauge and animations tell the pilot what the
   suit is doing.

## 2. What exists today (shipped, tested)
| Area | Behaviour |
|---|---|
| Config + sounds (Phase 6) | Balance file + in-game config screen; engine/drill/chainsaw sound loops; fuel tooltips. See sections 6-7. |
| Item | `power_armor` item deploys the suit (needs 3-block clearance). Durability bar = suit health; anvil + iron ingots repairs. Crafted from parts (2026-10-19, user: more expensive, no ingredients besides the parts; 2026-10-23: no lapis or furnaces in the suit, copper and redstone instead): helmet (4 iron blocks, redstone block, glass), torso (4 iron + 4 copper blocks round a redstone block), arm (copper block, piston, iron block in a row) and leg (the same in a column), each arm/leg crafted twice; torso, arms and legs are assembled like a body (arm-torso-arm, legs below): 12 iron blocks, 8 copper blocks, 2 redstone blocks in all with the helmet. Hand-drawn 16 px icons (`model/art/item_icons_art.py`, 2026-10-23; rendered from the .bbmodel before). **Helmet slot** (2026-10-19, user): the helmet goes in the suit's third slot (menu, or right-click the suit with it); it only seals the suit and lets the machine mind run. Without it a pilot still drives the suit, unsealed (fire, effects, water reach them; they can eat), and an empty suit stands powered down (no mind, no idle burn). A packed suit keeps it; a wreck drops it with the fuel and tool; rogues always wear one (put on if missing), drop only their tool, keep it after a lightning reboot. No helmet = empty collar (the pilot's own head isn't drawn yet: open). Packed items keep fuel, socket tool and the half-burnt item (`powerarmor:loadout` component; shown in the tooltip). |
| Entering | Right-click empty suit = climb in. Shift = climb out (behind/beside the suit). Sneak + right-click empty suit = suit menu. |
| Fuel + power (Phase 1) | Furnace-style fuel slot, drain by load, shutdown/startup animations + sounds, exhaust smoke by load, HUD gauge, low-fuel warning. See section 4. |
| Socket + chaingun (Phase 2; plasma cannon, then rivet gun, chaingun since 2026-10-13) | `PowerTool` items in the socket; hold right-click to use (attack until 2026-10-13). Chaingun: tap = heavy shots, hold = spin-up to a 10/s barrage, 1 iron nugget + fuel per bullet, bullets ignore hit immunity, optional overheat, flat item (icon rendered from the suit model), recipe. See section 3. |
| Left hand (Phase 3) | Holds the pilot's selected item (3D in third person, vanilla-style lower-left in first person); right-click uses it as normal; carry + reach animations; no attacking or mining; needs power; +1.5 reach. See section 3. |
| EM projector (Phase 4) | Hold to grab items / mobs / players (16 blocks; heavy things are pinned instead) and hold them floating; items are collected; release throws, tap shoves; wall slam damage. Socket only. See section 3. |
| Drill + chainsaw (Phase 5) | Diamond-tipped 3x3 drill (pickaxe + shovel blocks, diamond tier) and a chainsaw (axe blocks + leaves, fells natural trees above the cut). BreakEvent per block, vanilla drops, crack overlay, tool-bone loop animations. See section 3. |
| Suit menu (Phase 1) | Socket slot (power tools only), fuel slot, live suit preview, burn flame, hull/fuel/status display, Pack up button. See section 5. |
| Movement | Walk 3.0 blocks/s, sprint 6.25 (players: 4.3 / 5.6). Walk 3.0 since 2026-10-20 (user: 2.22, the walk animation's own speed, was too slow to get anywhere; the steps follow the real speed, so any speed keeps the feet planted). A tap of the sprint key keeps the sprint going while W is held (vanilla), backing up is half speed, strafing full. (2026-10-19: the animations' own speeds, walk 2.22; before that walk 2.6; until 2026-10-14 a 5 blocks/s jog and a 10 sprint, see Animations). Steps up 1 block, steps down up to 2 without falling; step changes eased for camera + model. Jump: hold Space (crouch wind-up, horse jump bar), release; launch 2 ticks later. |
| Turning | Legs face the direction of travel; torso + arms twist up to 90 deg and lean to face the pilot's view. |
| Camera | First person: eye at the visor, only the suit's legs drawn, arms = cockpit viewmodel (tool left, gauntlet + item right), speed FOV (no camera bob: removed 2026-10-20, user; the viewmodel sways with the gait under vanilla's View Bobbing). Third person: camera pulled back to 6.5. |
| Damage | Hits on the pilot go to the suit (except drowning, starvation, void//kill; "suffocating" because the pilot's head pokes out of the hitbox against a wall is ignored). Suit: 100 HP, armor 12, toughness 4, full knockback resistance, no fall damage. Death: animation, pilot ejected, drops iron. |
| Sealed suit (2026-10-13) | While powered, damage in the tag `powerarmor:sealed_out` (fire, campfire, magma, frost, cactus, berry bush, magic, wither) costs fuel instead of hull (20 per point, once per half second), harmful effects on pilot or suit are refused for 100 fuel (good ones pass), the pilot breathes under water (+0.5/tick) and doesn't burn or freeze. A powered suit never stays alight (fire put out at the start and end of every tick, both sides; not fireImmune, which would make lava and fire free); in lava it burns, and the flames die as it leaves. Lava still hurts the hull. No power (empty or knocked out) = no seal, burns like any mob. |
| Effects inside (2026-10-13) | Poison (`neoforge:is_poison`), wither and magic ticks on the pilot hurt the pilot, not the suit, and cost no fuel: food poisoning from before climbing in, or an effect that got in while unpowered. |
| Strength (2026-10-13) | Pilot's ATTACK_DAMAGE +4 (config): every melee hit, armed or not. Bows, thrown tridents and socket tools unchanged. |
| Stomp (2026-10-13) | Landing from >= 3.5 blocks: everything alive within 2-3.5 blocks (grows with the drop) except the suit and its pilot takes 2/block (max 30, half at the edge), credited to the pilot (`powerarmor:stomp`), thrown outward + up; mace ground-smash dust + sound. Every heavy landing kicks up a dust ring. |
| Smashing (2026-10-13) | While piloted, powered and steering: blocks in `powerarmor:suit_smashes_walking` (leaves, panes, cobweb, snow layer, berry bush, azalea, dripleaf, bamboo) break in the hitbox + 0.6 ahead + under the feet; sprinting adds `suit_smashes_sprinting` (glass, ice) 1.1 ahead. No drops, BreakEvent via ToolMining. Config switch. |
| Lightning (2026-10-13) | A weakness: a strike on a suit with fuel knocks it out for 100 ticks (electrified once + shutdown slump, sparks), no seal meanwhile, then startup. No boost. The bolt itself doesn't ignite a running suit; the fire it leaves at the suit's feet catches the knocked-out one. A suit that is off (empty, knocked out, standing up) doesn't flinch when hit (`reactsToHits`): the hit animation would cut into the convulsion, slump and startup. |
| Water | Fallout-style: sinks (~2.5 blocks/s), walks the bottom at half speed, suit never drowns, ignores currents. Since the engines (sec. 4b) a firebox floods once water covers its backpack (2.2 blocks up): only a tidal engine or a star reactor runs under deeper water. Powered: the pilot breathes the suit's air; unpowered: their air drains once their head is under. Lava: same bottom-walking at 35% speed (vanilla lava swimming crawled at 0.4 blocks/s). |
| Animations | Final legs (2026-10-15, legs v10): a solid lower leg down to the boot's sole strip, the ankle hinged at the lower leg's front bottom edge, the boot's hidden heel shaped around that hinge so the lower leg rocks over it without a gap. Walk v6: a slow heavy stride (1.0 s cycle, 22 px stance, 2.22 blocks/s authored), a knee dip and a footfall jolt at each heel strike. Run v8: a driving run (0.7 s stride, foot planted 32% of it, 6.25 blocks/s): the suit sinks into each planted leg and shoves off with the trailing leg thrown back; both arms pump a short heavy swing (the tool arm too: its third-person pose never aims). Rejected on the way: short quick steps, a bounding run ("hopping"), a knee-style ankle band. Every other leg animation still has keys from the earlier legs and needs re-baking before export. idle, walk, run, jump_charge, jump, fall, land, hit, death, shutdown, powered_off, startup, electrified (lightning), tool_recoil, hold_item/use_item, drill_run, chainsaw_run (+ unused: attack, idle_fidget; throw, stim and sneakwalk removed 2026-10-18). |
| Old saves | Suits loaded from older versions get current base stats (`readAdditionalSaveData`). |

## 3. Arms: one hand, one power socket
- **Decided 2026-10-13: hand right, tools left** (swapped from the original layout: "the hand should represent
  flexibility", and it matches vanilla's main hand). Done by mirroring the whole suit (model, animations, code,
  cockpit viewmodel), so third person and the cockpit agree.
- **Right arm = a real hand.** It shows and uses the pilot's **currently selected hotbar item**
  (rendered in the suit's right hand). The **use** button (right-click) works as normal for that item:
  place blocks, open doors, use buckets, throw potions...
  - **Decided 2026-10-13: no eating or drinking inside** (sealed in): food, potions, milk, honey, stew and cake are
    refused ("Sealed in: climb out to eat or drink" when no tool takes the click). A refused click passes on (FAIL), so
    with food in hand right click runs the socket tool, like with a sword. Splash/lingering potions still work.
  - **Decided 2026-10-13: the hand is completely normal**: left click attacks mobs and mines blocks (at normal
    speed), right click uses the item. (Until then attack belonged to the socket tool and the hand could neither
    attack nor mine, decided 2026-10-12.)
  - Built (Phase 3): the item is drawn at the suit's `right_hand` bone (`left_hand` until the 2026-10-13 swap). In the cockpit the suit's upper body
    isn't drawn (only the legs); a first-person viewmodel shows the gauntlet holding the item and the socket
    tool, like vanilla's hands (decided 2026-10-13: lining the external model up with the pilot's view never
    worked: hands raised where yours aren't, tools hidden behind their base, pads clipping).
    A powered-down suit's hand does nothing. Reach +1.5 blocks (block and entity interaction range).
- **Left arm = power socket.** Exactly one **power tool** item can be installed. Tools only work when
  installed in a powered suit (they do nothing on foot) and draw fuel per use.
  - **Decided 2026-10-13: the tool works like a shield in the off hand.** Right click goes to the hand first
    (vanilla order: the block you click, then the held item); if the hand has nothing to do, the press goes to the
    tool, which runs while the button is held (`ToolInput`). The player's real off-hand item isn't used while
    piloting. While the tool is used its arm raises from the hip carry to aim (`PowerArmorModel.raiseTool`, synced
    `TOOL_ACTIVE`, 3 ticks up / 5 down); the cockpit viewmodel raises it toward the middle too.
  - Empty socket: the trigger just clicks (dispenser sound).
  - The suit is crafted/deployed with an **empty socket**.
- Built (Phase 2, reworked later): the left arm ends at the elbow in a steel socket ring, and **each tool is the
  whole forearm** (16-19 px from the elbow, like the right forearm + hand). A `socket` coupling plate (cyan pins)
  shows when the socket is empty. Each tool is a bone in the suit model (`chaingun` for the chaingun) pivoting
  at the socket, shown only while installed, so tools can have their own animated parts. In first person
  `PowerArmorModel` raises the arm, swings it in a little and bends the tool up at the socket so you see its side.
  The item is flat like a vanilla tool, with a hand-drawn 16 px icon (`model/art/item_icons_art.py`, 2026-10-23; it used
  to be rendered from the tool's bones).
- Firing: the client cancels vanilla attack while piloting and sends "trigger down/up"; the server checks
  the sender is the pilot, the suit can operate, the tool's cooldown and fuel (lighting more fuel items if
  needed). Empty socket / no fuel: a dry click.

### Power tools
| Tool | Primary action (attack) | Fuel cost (initial guess) | Notes |
|---|---|---|---|
| **Chaingun** (built 2026-10-13; replaced the rivet gun, which replaced the plasma cannon) | Tap: one heavy shot (8 damage, first shot at once, then every 0.6 s at most). Hold: the barrels spin up over 2.5 s, the interval falls from 12 to 2 ticks (10 bullets/s), damage from 8 to 3; release: they coast down over 1.5 s (re-press resumes). Tracer bullets at 80 blocks/s, no drop, spread up to 0.4 deg at full spin (1.2 missed small mobs), fired from the pilot's eye along the crosshair line (from the muzzle, things the pilot couldn't see blocked shots) and drawn from the gun's muzzle along a straight line that joins the real path at the target (`Bullet.visualShift`), ignore the half-second hit immunity, light own knockback (damage type in `no_knockback`), spark off blocks, fizzle in water. Ammo: 1 iron nugget per bullet (off hand first; creative free); without: dry click. Overheat (config, on): 80 bullets in a row lock the gun until cooled (3 s, after a gap longer than the slow interval). | 15 per bullet + 1 per tick while spinning | Parts: motor housing, barrel cluster (`chaingun_barrels`: 2 clamp plates, 4 barrels, axle) turned in code from the synced spin (`ChaingunParts`); barrel tips glow with the heat (`power_armor_glow_heat.png`); recoil from the synced shot counter. Continuous tool (`holdTick`/`serverTick`). Motor whine pitch follows the spin. Damage type `powerarmor:bullet` ("shredded"). Recipe: iron ingots, piston, iron block, dispenser. |
| **3x3 drill** (built) | Hold on a block within reach (6 in the suit): the block + the 8 around it in the plane of the hit face crack together and break together, at diamond pickaxe/shovel speed (8) for the hardest of them, then a 4-tick pause. **Decided 2026-10-12:** diamond tier (obsidian yes, bedrock/unbreakable no), **pickaxe + shovel blocks** (axe blocks are the chainsaw's), model with a visible diamond tip. Drops via the block's loot table for a diamond pickaxe (shovel for shovel blocks), XP from ores; none in creative (vanilla). | 20 per block (Phase 6; was 60) | `BlockEvent.BreakEvent` for every block so claim/protection mods can veto (`ToolMining`, exempt from the pilot's no-mining rule). `drill_run` loop: bit spins 4 turns/s. Recipe: diamonds, iron, piston. |
| **Chainsaw** (built) | Hold on an axe block (logs, planks, wooden things) or leaves: cuts it at diamond-axe speed. A cut log of a natural tree fells every connected log (26-neighbourhood, cap 256) **at or above the cut**, top first, 3 per tick; it keeps falling after release while the suit has power + fuel. Leaves are left to decay normally. | 15 per log (Phase 6; was 40) | "Natural tree" = connected logs touching >= 4 non-persistent leaves, so log cabins only lose the cut block. `chainsaw_run` loop (chain shimmer). Recipe: iron, shears, piston. |
| **EM projector** (built) | Hold on a dropped item / mob (within 16 blocks): pulled in and held 3.5 blocks in front of the projector; items that arrive go into the pilot's inventory (vacuum: keep holding to grab the next). Release: launch along the view (mobs 32 blocks/s, items 24). Tap: shove. Thrown mobs hitting a wall take (speed - 0.5) x 12 damage. Drops (no throw) when power/pilot/tool is lost. | 1 per tick while holding, 80 per throw (Phase 6; was 2 / 200) | **Decided 2026-10-12:** items + mobs only (no blocks), socket only (no on-foot version). **Changed 2026-10-13:** players too (pilots only where the server allows PvP; rogues always); a rider is caught with their mount. Only other suits and the entity tag `powerarmor:em_heavy` (bosses via `#c:bosses`, iron golems, ravagers, wardens; a tag rather than the old size rule, which let golems and wardens through) resist: **heavy**, they aren't lifted but held in place, their motion damped to a fifth and dragged at most 1.2 blocks/s, a piloted or AI suit crawls at a quarter speed (`FIELD_HELD`, synced: the pilot's client moves a piloted suit), letting go shoves them (a quarter of a throw). Never spectators, creative players, no-AI mobs. Recipe: iron, copper, redstone, lodestone. |

## 4. Fuel and shutdown (built in Phase 1)
- **Decided 2026-10-12: works like a furnace**, no big tank. The fuel slot holds a stack; the engine burns
  one item at a time and takes the next when it's used up. Better fuel = longer per item.
- **Fuels (energy per item; walking burns 1 per tick, so 1600 = 80 s):** since 2026-10-23 the firebox burns
  **whatever a furnace burns, for as many ticks as a furnace burns it**: coal / charcoal 1600 (1:20), blaze rod 2400
  (2:00), lava bucket 20000 (16:40, the empty bucket stays in the slot), coal block 16000, planks 300, sticks 100...
  Asked of a real (never placed) furnace block entity (`FireboxFuel`), so data packs and mods that change furnace
  fuels change the suit's. Until then it had its own table (coal 1600, lava 3200, blaze 4800, nothing else).
- **Drain per tick:** idle 0.25, moving 1.0, sprinting 2.0, +20 per jump, plus tool costs
  per use (Phase 2+). Also in creative mode. **Since the machine mind (sec. 8, 2026-10-13) a powered suit always
  runs**, piloted or not (the mind's load is judged from how fast it moves); to park a suit, take the fuel out.
- **Shutdown at 0** (nothing burning, slot empty): power-down sound, `shutdown` slump (hold), no movement /
  jump / tools, torso stops aiming, smoke stops, action-bar hint. The pilot can climb out. Fuel in the slot
  restarts it: power-up sound, black smoke cough, `startup` animation; movement unlocks after 1.4 s.
  A parked suit without fuel stays slumped (`powered_off`).
- **Low fuel (< 600 energy = 30 s of walking, counting the slot):** gauge flashes, beep every 2 s, dark
  sputtering smoke.
- Hoppers into the fuel slot: nice-to-have, not built.
- Since the engines (section 4b) this section describes the **firebox**, the standard engine.

## 4b. Engines (decided 2026-10-21, user; built 2026-10-21: mechanics, then the backpacks)
Why: a furnace that keeps burning under water made no sense. The backpack becomes the suit's **engine**, and
engines can be swapped. Each engine is a full redesign of the backpack: only the firebox has the firebox and the
exhausts; the other two are their own models (simple boxes, detail in the texture, as the user prefers).
- **Engine slot**: a fourth slot in the suit menu. Engine items: **Firebox**, **Tidal engine**, **Star reactor**.
  **A new suit comes without an engine** (2026-10-23, user; until then it came with a firebox): craft one and put it
  in. Suits saved (or packed) before the engines load with the firebox they had built in; rogues get a firebox (not
  loot, kept after a lightning reboot). No engine = no power (slumped, like a suit without fuel). A packed suit keeps its engine; a wreck drops it.
- **The fuel slot holds what the engine runs on.** Like a furnace's, it takes any engine's fuel; what the installed
  engine can't use just sits there. Swapping the engine is allowed any time; the half-burnt item is lost with it.

  | Engine | Recipe (2026-10-21; "somewhat expensive") | Fuel slot | Runs | Costs |
  |---|---|---|---|---|
  | Firebox (standard, most common) | 4 iron blocks, 4 iron ingots, a blast furnace (user, 2026-10-23: the one furnace kept when the suit's recipes dropped theirs) | furnace fuels (section 4) | anywhere, rain too; **floods** when water covers it | as section 4 |
  | Tidal engine | 4 iron blocks, 4 dark prismarine, the **Heart of the Sea** | water: bucket (the bucket stays) or bottle, a short run each, to get back into the water | **free while wet**: its backpack under water, or standing in rain; on land it drinks the water in the slot | nothing while wet |
  | Star reactor (endgame, like an arc reactor) | 4 iron blocks, 4 diamonds, an end crystal | 1 **Nether Star** (never used up) | forever, anywhere | nothing: movement and tools are free; the chaingun still needs iron nuggets |

- **Under water** means the engine is covered: the backpack's middle (2.2 blocks above the suit's feet) is below the
  surface. Wading through shallower water changes nothing.
- **Flooded firebox**: the fire dies (hiss, steam), the half-burnt item is lost, the suit shuts down (slump) and
  loses its seal (the pilot's air drains). Fuel in the slot can't relight it while the backpack is under water.
  **No pack-up while flooded.** Rescues: drain the water (sponges, blocks) or swap in a tidal engine under water.
- A tidal engine leaving the water keeps running for a moment (1 s grace) before it drinks from the slot or stops,
  so a shoreline doesn't flicker it on and off.
- Lightning still knocks out every engine (the suit keeps its one weakness). Rogues run on nothing and never flood.
- **Each engine is its own backpack** (2026-10-21, user's picks after drafts; only the installed one is drawn):
  firebox = today's pack (painted firebox, two exhausts: orange glow, smoke); tidal = twin dark iron diving tanks with
  steel straps and valves, a pump between them with the Heart of the Sea glowing teal in a porthole, **bubbles** from
  the valves while it runs with its backpack under water (not in rain); star reactor = a netherite housing (vent grille,
  a round glowing core) between two energy canisters with glass strips showing the energy (violet -> white, slow
  pulse) and a **faint aura**: ender particles (the purple portal specks of endermen) sinking into the glass (2026-10-23; before: translucent violet wisps; trails of sparks were too much).
  Gauge: flame (firebox and tidal on land), endless while running free.
- The machine mind: firebox suits keep out of water; a tidal suit's mind only runs while it has power.

## 4c. Helmet styles (decided + built 2026-10-22, user)
Alternate looks for the helmet, as **Easter eggs** after famous armoured helmets (first meant to come with the dyes;
user: make them widely available, and the smithing table's templates make them feel like a find). Names and lines
**allude** to the originals, never name them (other studios' trademarks).
- **One helmet item, its style a data component** (`powerarmor:helmet_style`; none = the standard helmet). Purely
  cosmetic: every style seals the suit and carries its mind the same way. The name follows the style ("Spartan
  Helmet", an anvil name still wins) and the tooltip adds an italic flavour line; the icon follows it too.
- **Smithing table**: template + Power Armor Helmet + the style's object = the styled helmet (template and object used
  up, like vanilla's trims; other components kept). A styled helmet can be restyled. **Helmet + iron block, no
  template** = the standard helmet back. Templates **copy** like vanilla's: 7 diamonds round the template and the
  style's object = 2 templates.

  | Style (draft) | After | Template found in (about 1 chest in 10) | Object | Flavour line |
  |---|---|---|---|---|
  | Protector (F) | Subject Delta, BioShock 2 | ocean ruins (small + big), shipwreck treasure | turtle shell | "Would you kindly keep her safe?" |
  | Spartan (G) | the Master Chief, Halo | trial chamber vaults, ominous too (1 in 8) | totem of undying | "Wake me when you need me." |
  | Noble (H) | Emile's EVA helmet, Halo: Reach | end city treasure | phantom membrane | "I'm ready! How 'bout you?" |
  | Robot (K) | Guy-Manuel's helmet, Daft Punk | ancient city | any music disc (`#c:music_discs`) | "One more time." |
  | Hunter (U, 2026-10-22) | Samus's helmet as in Smash Bros. Ultimate | desert pyramids, jungle temples (the "Chozo ruins") | slime ball (a Metroid) | "See you next mission." |

- Templates are named "<Style> Helmet Design" (vanilla's template tooltip: applies to the helmet, ingredient the object)
  and carry the flavour line too. Loot comes from NeoForge loot modifiers (`neoforge:add_table`).
- The model: each style is a whole helmet of its own under the head bone (`helm_protector`, `helm_spartan`,
  `helm_noble`, `helm_robot`, `helm_hunter`; drafted as hidden `proto_helm_*`, still hidden in Blockbench); the renderer draws the
  worn one instead of `helmet`. Their visors glow like the standard one; their shells take the paint, with the
  protector's window bars, the spartan's top band and the robot's frame (brown = brass, yellow = gold).
- Rogues: one in four wears a random style (only a look; their helmet is never loot).
- The Hunter (2026-10-22, user, after rounds on a Metroid Prime version and a red-orange one: steel like the others, after
  Ultimate): one shield visor wrapping the sides, a crest, a silver grille, corrugated silver hoses in straight runs
  (jaw, side, up into a port: angled or stepped hoses were "too much" / "too jagged"). Its shell and crest take the paint
  (red dye = Samus), grille and hoses stay silver.
- Left for later (user): a black one (Death Trooper?).

## 5. Suit menu (built in Phase 1)
- **Sneak + right-click a parked suit** (decided 2026-10-12, the horse/llama convention). Tools and fuel can
  only be changed there: the pilot has to get out, which makes the loadout a decision. Closes if
  someone climbs in or you walk > 8 blocks away.
- Contents: socket slot (accepts `PowerTool` items only, 1), fuel slot (suit fuels + empty buckets),
  burn flame, live suit preview, hull / fuel time / ONLINE-NO POWER display, **Pack up** button, player
  inventory. Shift-click sends fuel to the fuel slot and tools to the socket.
- Packing keeps fuel, tool and the half-burnt item on the item stack; deploying restores them. A destroyed
  suit drops its fuel and tool.

## 6. Feedback
- **Exhaust smoke** from the two back pipes (`exhaust_r`, `exhaust_l` bones, tops at ~y 48 px):
  idle = occasional light puffs; walking = steady; sprinting / tool use = heavy; low fuel = dark,
  sputtering; shutdown = none. Client-side particles at the pipe tops' world positions (they follow
  the animated torso).
- **HUD gauge** while piloting (built): burn flame, fuel slot item + count, time left at walking pace, right of
  the hotbar. Phase 2 may add the tool's name/cooldown.
- **Lights** (built 2026-10-13): firebox glow (load, low fuel, embers after shutdown) and visor state colours,
  see section 0.
- Sounds (built, Phase 6; engine reworked 2026-10-21): no engine loop (user: a constant rumble drives you mad); a
  powered suit's backpack makes a faint sound every ~7 s at rest, ~3 s flat out (firebox crackle, tidal bubbles only
  under water, reactor hum); drill, chainsaw and chaingun loops while running; power-down/up, shots, EM hum and grinding one-shots. All in `sounds.json` (re-pitched vanilla for now).

## 7. Balance knobs (built, Phase 6: `config/powerarmor-common.toml`, in-game config screen)
Fuel energy per item, drain per state, jump cost, low-fuel threshold, tool costs, chaingun intervals, spin-up, damage, overheat, EM range + slam
damage, drill pause, chainsaw cap + felling speed; suit: sealing costs, air cost, melee bonus, stomp threshold/damage/cap, lightning
knock-out length, smashing on/off. A light source was considered and dropped (2026-10-13): vanilla has no moving lights, and
the usual trick (invisible light blocks following the suit) was judged not worth its hacks. Not configurable (decided design): drill harvest tier (diamond).
Default costs per coal (1600): 80 s walking, ~100 chaingun bullets, 80 s of EM holding or 20 throws, ~9 drill faces,
~20 felled oaks.

## 8. Suit AI (decided 2026-10-13; built: step 1 mind, owner, home, visor, tool operator, idle job; step 2 nugget store + chaingun job; step 3 EM guard job; step 4 drill + chainsaw workers; step 5 rogue suits)
An empty suit is a mob, like a horse: a machine mind drives it whenever nobody else does.

- **Who**: every *powered* suit without a pilot is AI-driven. To park a suit, take its fuel out (or run it dry):
  unpowered = slumped, no AI. Player-owned suits never go rogue.
- **Owner** (decided): the last pilot (a suit deployed but never piloted: whoever deployed it). A rebooted rogue has
  no owner until someone climbs in: **whoever climbs in claims it**.
- **Home**: the block where the pilot got out (deployed: where it was placed). Jobs are anchored there; radius in config.
- **Visor**: green friendly, red rogue, amber piloted, dark off (sec. 0).
- **Job = the socket tool**:
  | Tool | Job |
  |---|---|
  | none | Idle at home: stands, looks around, walks back if pushed away. |
  | Drill (built) | Mines **horizontally** at the home level within the radius (`work_radius` 12): clears an open hall 3 blocks high (the suit's height), ring by ring out from home (`SuitWork.mayDig`: in the hall, drillable, not next to a liquid, floor under the column; the drill itself refuses anything else for the mind). Never digs down or raises the ceiling, never steps down (path max fall 0) and leaves a wall next to lava/water and columns over holes. Drops stay on the floor as vanilla items. |
  | Chainsaw (built) | Fells natural trees whose trunk stands within the radius (logs with natural leaves; never cabins), nearest first: cuts the lowest log, waits for the tree to come down, next; then goes back home. Drops stay where they fall. |
  | Chaingun (built) | Follows the owner like a tamed wolf (walks when over 7 blocks away, sprints past 16, stops at 4; no teleport: a ton of steel) and defends them: shoots what attacks the owner (or the suit), what the owner attacks and monsters within 16 blocks of the owner (config `guard_range`). Players only if they started it and PvP is on; never villagers, golems, the owner's pets or friendly suits; rogue suits always. Never fires with the owner, other players, villagers, pets or suits in the line of fire. Ammo: the suit's hidden nugget store (below); empty: punches (+4 melee bonus). |
  | EM projector (built) | Guards an area around home (`em_guard_radius` 12): grabs the monster nearest home, lifts it overhead and flings it straight up (leaning a little outward, so it comes down beside the suit) at `em_guard_launch` 44 blocks/s (~30 blocks high in the open; a pilot's throws are 32), so the fall kills it; creepers are thrown up and away from home instead. Only monsters standing (or swimming, or flyers): one it threw is left alone until it lands (catching it mid-fall reset the fall). Fights from home: the feet stay with ReturnHome. Never players, pets, villagers, suits. |
- **Collecting the drops = allays** (vanilla 1.21.1, decided 2026-10-13; copper golems don't exist before 1.21.9 and
  only sort chests anyway). An allay picks up dropped items matching the one it holds and brings them to its player or
  to a note block it heard in the last 30 s. So the player decides what to keep: hand allays a diamond, coal, iron...,
  play a note block next to a hopper at home; the stone and dirt nobody wants despawn after 5 minutes. Allays only see
  items in **line of sight** within 32 blocks across / 16 up-down, which is why the drill clears an open hall around
  home instead of branch tunnels (drops around corners would be invisible), and why the default radius stays
  well under 32.
- **Hidden nugget store** (decided 2026-10-13: no ammo slot for a single tool): right-clicking a suit with iron
  nuggets in hand loads the whole stack into it instead of climbing in (like feeding a horse). The count is saved with
  the suit, kept when packed up and dropped as nuggets when the suit is destroyed; the menu shows it as text only.
  A pilot's chaingun also uses it, after the nuggets in their own inventory (decided 2026-10-13).
- **Rogue suits** (built 2026-10-13; `RogueSpawner`, goals `RogueTarget`, `ChaingunAttack`, `RogueEmAttack`, `RogueWander`;
  chance `rogue_spawn_chance` 0.02/day, `rogue_lifetime` 24000; the EM rogue's beam takes players too and throws with a
  pilot's strength, a piloted suit gets punched; friendly chaingun minds treat rogues like monsters) (extremely rare, natural only): spawned like the wandering trader (one roll per in-game day, tiny
  chance, far from a random player in the overworld, one at a time; chance in config). **They leave after a while**
  (decided 2026-10-13), like the trader: after about an in-game day (config) a rogue vanishes once no player is near
  enough to watch it go. Red visor, a chaingun or an EM
  projector, **unlimited fuel and ammo**, attacks any mob or player it sees. Defeated: **drops only its tool** (not
  the suit). Struck by lightning (storm, Channeling trident, lightning rod): the existing lightning knock-out, then it
  restarts as a friendly suit with no owner (the first pilot claims it).
- **Engineering notes** (step 1, built): `PowerArmorEntity` extends `Mob`, so the mind is vanilla's goal system
  (`SuitGoals`; every goal also checks `isAiDriven()`, and `updateControlFlags` hands the goals' controls to the mind
  only while it drives). The tools take an *operator* (`PowerArmorEntity.toolOperator()`): the pilot, or the suit
  itself while its mind drives; it aims (eye + view: for a mob, its head), pays the ammo and owns the bullets/kills.
  Without a pilot, blocks are broken as a FakePlayer with the owner's UUID (`blockBreaker`), so claim mods veto as
  for the owner. The mind aims with `setAiAim` (snaps the head onto a point, after the goals and before vanilla's look
  control) and fires with `setAiTrigger`. Vanilla mobs move at speed squared (MoveControl passes the speed as the
  forward input); `travel` normalises the mind's input so speed modifier 1.0 = the pilot's walk (3.0 blocks/s).

## 9. Out of scope for now
Wearable armor, multiple sockets, upgrade trees, multiplayer permissions beyond the server's PvP setting.
