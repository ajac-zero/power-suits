# Power Armor: design spec

Status: agreed direction as of 2026-10-12. Items marked **OPEN** need the user's decision before
they are built. `docs/design-notes.md` keeps the history of how we got here.

## 0. Identity (decided 2026-10-13)
**A coal-fired, steam-and-diesel machine, not sci-fi.** Inspirations, not targets: Fallout's power armor and
BioShock's Big Daddies (heavy plates, a glowing visor, a drill, a rivet gun); don't replicate either. Everything it does is powered by the firebox burning your fuel; the glow comes
from fire, hot metal, redstone and sparks, never from futuristic energy. Keep the blue paint for now.
- **Material palette: blue paint, grey steel, dark iron; light only from fire and glow.** No small accents in a new
  material (brass trim on the visor and firebox was tried 2026-10-13 and dropped: it read as stuck on).
- Built 2026-10-13 (step 1): firebox, glowmask layer (`SuitGlowLayer`), visor states.
- Chest: the blue core becomes a **firebox** (grated window, orange glow, flickers with load, fades with fuel, dark
  when off). Glow is drawn from glowmask textures (full-bright, tinted per frame).
- Visor: **the whole visor glows and shows the suit's state** (the helmet's own steel is its frame): green = parked with power (free to climb in), amber =
  piloted, red = tool in use or just hit, dark = powered off, flickering while starting up, shutting down or low on fuel.
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
- Later (not now): dyeable paint like leather armor.

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
| Item | `power_armor` item deploys the suit (needs 3-block clearance). Durability bar = suit health; anvil + iron ingots repairs. Crafted from iron blocks, iron helmet/chestplate/boots, pistons. Packed items keep fuel, socket tool and the half-burnt item (`powerarmor:loadout` component; shown in the tooltip). |
| Entering | Right-click empty suit = climb in. Shift = climb out (behind/beside the suit). Sneak + right-click empty suit = suit menu. |
| Fuel + power (Phase 1) | Furnace-style fuel slot, drain by load, shutdown/startup animations + sounds, exhaust smoke by load, HUD gauge, low-fuel warning. See section 4. |
| Socket + chaingun (Phase 2; plasma cannon, then rivet gun, chaingun since 2026-10-13) | `PowerTool` items in the socket; hold right-click to use (attack until 2026-10-13). Chaingun: tap = heavy shots, hold = spin-up to a 10/s barrage, 1 iron nugget + fuel per bullet, bullets ignore hit immunity, optional overheat, flat item (icon rendered from the suit model), recipe. See section 3. |
| Left hand (Phase 3) | Holds the pilot's selected item (3D in third person, vanilla-style lower-left in first person); right-click uses it as normal; carry + reach animations; no attacking or mining; needs power; +1.5 reach. See section 3. |
| EM projector (Phase 4) | Hold to grab items / mobs (16 blocks) and hold them floating; items are collected; release throws, tap shoves; wall slam damage. Socket only. See section 3. |
| Drill + chainsaw (Phase 5) | Diamond-tipped 3x3 drill (pickaxe + shovel blocks, diamond tier) and a chainsaw (axe blocks + leaves, fells natural trees above the cut). BreakEvent per block, vanilla drops, crack overlay, tool-bone loop animations. See section 3. |
| Suit menu (Phase 1) | Socket slot (power tools only), fuel slot, live suit preview, burn flame, hull/fuel/status display, Pack up button. See section 5. |
| Movement | Jog 5 blocks/s, sprint 10 (players: 4.3 / 5.6). Steps up 1 block, steps down up to 2 without falling; step changes eased for camera + model. Jump: hold Space (crouch wind-up, horse jump bar), release; launch 2 ticks later. |
| Turning | Legs face the direction of travel; torso + arms twist up to 90 deg and lean to face the pilot's view. |
| Camera | First person: eye at the visor, only the suit's legs drawn, arms = cockpit viewmodel (tool left, gauntlet + item right), cockpit footfall bob, speed FOV. Third person: camera pulled back to 6.5. |
| Damage | Hits on the pilot go to the suit (except drowning, starvation, void//kill). Suit: 100 HP, armor 12, toughness 4, full knockback resistance, no fall damage. Death: animation, pilot ejected, drops iron. |
| Water | Fallout-style: sinks (~2.5 blocks/s), walks the bottom at half speed, suit never drowns, ignores currents. Pilot's air drains normally once their head is under. |
| Animations | idle, walk (jog), run, jump_charge, jump, fall, land, hit, death, shutdown, powered_off, startup, tool_recoil, hold_item/use_item, drill_run, chainsaw_run (+ unused: attack, throw, stim, electrified, idle_fidget, sneakwalk). Walk/run hold the cannon steady (arm IK stabiliser). |
| Old saves | Suits loaded from older versions get current base stats (`readAdditionalSaveData`). |

## 3. Arms: one hand, one power socket
- **Decided 2026-10-13: hand right, tools left** (swapped from the original layout: "the hand should represent
  flexibility", and it matches vanilla's main hand). Done by mirroring the whole suit (model, animations, code,
  cockpit viewmodel), so third person and the cockpit agree.
- **Right arm = a real hand.** It shows and uses the pilot's **currently selected hotbar item**
  (rendered in the suit's right hand). The **use** button (right-click) works as normal for that item:
  place blocks, eat, open doors, use buckets, throw potions...
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
  The item is flat like a vanilla tool; its icon is rendered from the same bones (plus the socket ring), cut out by
  `tools/extract-tool-geo.py` and drawn by `tools/make-item-icons.py`.
- Firing: the client cancels vanilla attack while piloting and sends "trigger down/up"; the server checks
  the sender is the pilot, the suit can operate, the tool's cooldown and fuel (lighting more fuel items if
  needed). Empty socket / no fuel: a dry click.

### Power tools
| Tool | Primary action (attack) | Fuel cost (initial guess) | Notes |
|---|---|---|---|
| **Chaingun** (built 2026-10-13; replaced the rivet gun, which replaced the plasma cannon) | Tap: one heavy shot (8 damage, first shot at once, then every 0.6 s at most). Hold: the barrels spin up over 2.5 s, the interval falls from 12 to 2 ticks (10 bullets/s), damage from 8 to 3; release: they coast down over 1.5 s (re-press resumes). Tracer bullets at 80 blocks/s, no drop, spread up to 0.4 deg at full spin (1.2 missed small mobs), fired from the pilot's eye along the crosshair line (from the muzzle, things the pilot couldn't see blocked shots) and drawn from the gun's muzzle along a straight line that joins the real path at the target (`Bullet.visualShift`), ignore the half-second hit immunity, light own knockback (damage type in `no_knockback`), spark off blocks, fizzle in water. Ammo: 1 iron nugget per bullet (off hand first; creative free); without: dry click. Overheat (config, on): 80 bullets in a row lock the gun until cooled (3 s, after a gap longer than the slow interval). | 15 per bullet + 1 per tick while spinning | Parts: motor housing, barrel cluster (`chaingun_barrels`: 2 clamp plates, 4 barrels, axle) turned in code from the synced spin (`ChaingunParts`); barrel tips glow with the heat (`power_armor_glow_heat.png`); recoil from the synced shot counter. Continuous tool (`holdTick`/`serverTick`). Motor whine pitch follows the spin. Damage type `powerarmor:bullet` ("shredded"). Recipe: iron ingots, piston, iron block, dispenser. |
| **3x3 drill** (built) | Hold on a block within reach (6 in the suit): the block + the 8 around it in the plane of the hit face crack together and break together, at diamond pickaxe/shovel speed (8) for the hardest of them, then a 4-tick pause. **Decided 2026-10-12:** diamond tier (obsidian yes, bedrock/unbreakable no), **pickaxe + shovel blocks** (axe blocks are the chainsaw's), model with a visible diamond tip. Drops via the block's loot table for a diamond pickaxe (shovel for shovel blocks), XP from ores; none in creative (vanilla). | 20 per block (Phase 6; was 60) | `BlockEvent.BreakEvent` for every block so claim/protection mods can veto (`ToolMining`, exempt from the pilot's no-mining rule). `drill_run` loop: bit spins 4 turns/s. Recipe: diamonds, iron, piston. |
| **Chainsaw** (built) | Hold on an axe block (logs, planks, wooden things) or leaves: cuts it at diamond-axe speed. A cut log of a natural tree fells every connected log (26-neighbourhood, cap 256) **at or above the cut**, top first, 3 per tick; it keeps falling after release while the suit has power + fuel. Leaves are left to decay normally. | 15 per log (Phase 6; was 40) | "Natural tree" = connected logs touching >= 4 non-persistent leaves, so log cabins only lose the cut block. `chainsaw_run` loop (chain shimmer). Recipe: iron, shears, piston. |
| **EM projector** (built) | Hold on a dropped item / mob (within 16 blocks): pulled in and held 3.5 blocks in front of the projector; items that arrive go into the pilot's inventory (vacuum: keep holding to grab the next). Release: launch along the view (mobs 32 blocks/s, items 24). Tap: shove. Thrown mobs hitting a wall take (speed - 0.5) x 12 damage. Drops (no throw) when power/pilot/tool is lost. | 1 per tick while holding, 80 per throw (Phase 6; was 2 / 200) | **Decided 2026-10-12:** items + mobs only (no blocks), socket only (no on-foot version). Never players, bosses, other suits, no-AI mobs, riders/ridden, volume > 6 blocks^3. Recipe: iron, copper, redstone, lodestone. |

## 4. Fuel and shutdown (built in Phase 1)
- **Decided 2026-10-12: works like a furnace**, no big tank. The fuel slot holds a stack; the engine burns
  one item at a time and takes the next when it's used up. Better fuel = longer per item.
- **Fuels (energy per item; walking burns 1 per tick, so 1600 = 80 s):** coal / charcoal 1600 (1:20),
  lava bucket 3200 (2:40, the empty bucket stays in the slot), blaze rod 4800 (4:00). Nothing else
  (planks, coal blocks...) burns, so blaze rods stay the best fuel. Table: `SuitFuel`.
- **Drain per tick (piloted only):** idle 0.25, moving 1.0, sprinting 2.0, +20 per jump, plus tool costs
  per use (Phase 2+). A parked suit burns nothing and keeps its half-burnt item. Also in creative mode.
- **Shutdown at 0** (nothing burning, slot empty): power-down sound, `shutdown` slump (hold), no movement /
  jump / tools, torso stops aiming, smoke stops, action-bar hint. The pilot can climb out. Fuel in the slot
  restarts it: power-up sound, black smoke cough, `startup` animation; movement unlocks after 1.4 s.
  A parked suit without fuel stays slumped (`powered_off`).
- **Low fuel (< 600 energy = 30 s of walking, counting the slot):** gauge flashes, beep every 2 s, dark
  sputtering smoke.
- Hoppers into the fuel slot: nice-to-have, not built.

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
- Sounds (built, Phase 6): engine loop while piloted + powered, pitch/volume by load; drill and chainsaw loops while
  running; power-down/up, shots, EM hum and grinding one-shots. All in `sounds.json` (re-pitched vanilla for now).

## 7. Balance knobs (built, Phase 6: `config/powerarmor-common.toml`, in-game config screen)
Fuel energy per item, drain per state, jump cost, low-fuel threshold, tool costs, chaingun intervals, spin-up, damage, overheat, EM range + slam
damage, drill pause, chainsaw cap + felling speed. Not configurable (decided design): drill harvest tier (diamond).
Default costs per coal (1600): 80 s walking, ~100 chaingun bullets, 80 s of EM holding or 20 throws, ~9 drill faces,
~20 felled oaks.

## 8. Out of scope for now
Wearable armor, multiple sockets, upgrade trees, multiplayer permissions beyond "never grab players".
