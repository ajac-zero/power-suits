# Power Armor: design spec

Status: agreed direction as of 2026-10-12. Items marked **OPEN** need the user's decision before
they are built. `docs/design-notes.md` keeps the history of how we got here.

## 1. Pillars
1. **A machine you climb into** (Fallout / Titanfall), not wearable armor. Heavy, tall (3 blocks), fast.
2. **Special-purpose, not a daily driver.** It burns fuel, shuts down when empty, and its strong
   abilities come from power tools that cost a lot of fuel.
3. **Readable feedback.** Exhaust smoke, sounds, a fuel gauge and animations tell the pilot what the
   suit is doing.

## 2. What exists today (shipped, tested)
| Area | Behaviour |
|---|---|
| Item | `power_armor` item deploys the suit (needs 3-block clearance). Durability bar = suit health; anvil + iron ingots repairs. Crafted from iron blocks, iron helmet/chestplate/boots, pistons. Packed items keep fuel, socket tool and the half-burnt item (`powerarmor:loadout` component; shown in the tooltip). |
| Entering | Right-click empty suit = climb in. Shift = climb out (behind/beside the suit). Sneak + right-click empty suit = suit menu. |
| Fuel + power (Phase 1) | Furnace-style fuel slot, drain by load, shutdown/startup animations + sounds, exhaust smoke by load, HUD gauge, low-fuel warning. See section 4. |
| Socket + plasma cannon (Phase 2) | `PowerTool` items in the socket; hold attack to use. Plasma cannon: bolt to the crosshair, 10 damage, 300 energy, 0.6 s cooldown, `plasma_shot` animation, 3D item (cut from the suit model), recipe. See section 3. |
| Left hand (Phase 3) | Holds the pilot's selected item (3D in third person, vanilla-style lower-left in first person); right-click uses it as normal; carry + reach animations; no attacking or mining; needs power; +1.5 reach. See section 3. |
| Suit menu (Phase 1) | Socket slot (power tools only), fuel slot, live suit preview, burn flame, hull/fuel/status display, Pack up button. See section 5. |
| Movement | Jog 5 blocks/s, sprint 10 (players: 4.3 / 5.6). Steps up 1 block, steps down up to 2 without falling; step changes eased for camera + model. Jump: hold Space (crouch wind-up, horse jump bar), release; launch 2 ticks later. |
| Turning | Legs face the direction of travel; torso + arms twist up to 90 deg and lean to face the pilot's view. |
| Camera | First person: eye at the visor, suit shell hidden, back faces culled, cockpit footfall bob, speed FOV. Third person: camera pulled back to 6.5. |
| Damage | Hits on the pilot go to the suit (except drowning, starvation, void//kill). Suit: 100 HP, armor 12, toughness 4, full knockback resistance, no fall damage. Death: animation, pilot ejected, drops iron. |
| Water | Fallout-style: sinks (~2.5 blocks/s), walks the bottom at half speed, suit never drowns, ignores currents. Pilot's air drains normally once their head is under. |
| Animations | idle, walk (jog), run, jump_charge, jump, fall, land, hit, death, shutdown, powered_off, startup (+ unused: attack, throw, stim, electrified, idle_fidget, sneakwalk). Walk/run hold the cannon steady (arm IK stabiliser). |
| Old saves | Suits loaded from older versions get current base stats (`readAdditionalSaveData`). |

## 3. Arms: one hand, one power socket
- **Left arm = a real hand.** It shows and uses the pilot's **currently selected hotbar item**
  (rendered in the suit's left hand). The **use** button (right-click) works as normal for that item:
  place blocks, eat, open doors, use buckets, throw potions...
  - The left hand does **not** break blocks: the attack button belongs to the socket tool.
  - **Decided 2026-10-12: no**, the left hand never attacks mobs, even with a sword (attack = socket tool).
  - Built (Phase 3): the item is drawn at the suit's `left_hand` bone; in the cockpit it's drawn like
    vanilla's off-hand item instead, because the arm can't reach into the view without blocking it.
    A powered-down suit's hand does nothing. Reach +1.5 blocks (block and entity interaction range).
- **Right arm = power socket.** Exactly one **power tool** item can be installed. Tools only work when
  installed in a powered suit (they do nothing on foot) and draw fuel per use.
  - The **attack** button (left-click, hold for continuous tools) uses the installed tool.
  - Empty socket: attack does nothing (maybe a dry "clunk" sound).
  - The suit is crafted/deployed with an **empty socket**.
- Built (Phase 2): the forearm block stays as the **socket housing**; a `socket` coupling plate (cyan pins)
  shows when the socket is empty. Each tool is a bone in the suit model (`cannon` for the plasma cannon),
  shown only while installed, so tools can have their own animated parts. The hand-held item model is
  cut from the same bones by `tools/extract-tool-geo.py`.
- Firing: the client cancels vanilla attack while piloting and sends "trigger down/up"; the server checks
  the sender is the pilot, the suit can operate, the tool's cooldown and fuel (lighting more fuel items if
  needed). Empty socket / no fuel: a dry click.

### Power tools
| Tool | Primary action (attack) | Fuel cost (initial guess) | Notes |
|---|---|---|---|
| **Plasma cannon** (built) | Fires a plasma bolt from the muzzle toward the crosshair (60 blocks/s, no gravity, ~90 block range): 10 damage, vanilla knockback, 1 s of fire, no block damage, fizzles in water. Cooldown 0.6 s while held. | 300 per shot (balance later) | Reuses the cannon art + `muzzle_flash`; single-shot `plasma_shot` overlay. Damage type `powerarmor:plasma` ("vaporized"). Recipe: iron ingots, blaze rod, diamond, glass. |
| **3x3 drill** | Hold to drill: breaks the targeted block plus the 8 around it in the plane facing the pilot. Respects harvest tier (diamond-equivalent?), never unbreakable blocks, drops items normally. | 60 per block | Fire `BlockEvent.BreakEvent` for every block so claim/protection mods can veto. |
| **Chainsaw** | Hold on a log: cuts it; when the bottom log of a tree is cut, fells the whole tree (connected logs, cap ~256). | 40 per log | "Natural tree" heuristic: connected logs with leaves attached, so log cabins aren't eaten. |
| **EM tool** (electromagnetic) | Hold = **pull** the targeted item entity / mob toward the suit; release / second press = **push** it away. | 2 per tick held, 200 per push | Replaces the earlier "gravity gun". **OPEN:** blocks too, or only items + mobs? **OPEN:** also usable on foot in a weaker form (it was originally a gun-like handheld)? Default: socket-only like the other tools. Never bosses or players (unless a PvP config allows). |

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
- Sounds: engine idle loop pitched by load (nice-to-have), power-down/up, tool sounds.

## 7. Balance knobs (all constants in code, later a config)
Fuel energy per item, drain per state, low-fuel threshold, tool costs, tool cooldowns, drill harvest tier, chainsaw cap, EM range
and strength, cannon damage.

## 8. Out of scope for now
Wearable armor, multiple sockets, upgrade trees, multiplayer permissions beyond "never grab players".
