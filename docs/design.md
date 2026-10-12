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
| Item | `power_armor` item deploys the suit (needs 3-block clearance). Durability bar = suit health; anvil + iron ingots repairs. Crafted from iron blocks, iron helmet/chestplate/boots, pistons. |
| Entering | Right-click empty suit = climb in. Shift = climb out (behind/beside the suit). Sneak + right-click empty suit = pack it into the item (keeps damage + name). **Changes in Phase 1.** |
| Movement | Jog 5 blocks/s, sprint 10 (players: 4.3 / 5.6). Steps up 1 block, steps down up to 2 without falling; step changes eased for camera + model. Jump: hold Space (crouch wind-up, horse jump bar), release; launch 2 ticks later. |
| Turning | Legs face the direction of travel; torso + arms twist up to 90 deg and lean to face the pilot's view. |
| Camera | First person: eye at the visor, suit shell hidden, back faces culled, cockpit footfall bob, speed FOV. Third person: camera pulled back to 6.5. |
| Damage | Hits on the pilot go to the suit (except drowning, starvation, void//kill). Suit: 100 HP, armor 12, toughness 4, full knockback resistance, no fall damage. Death: animation, pilot ejected, drops iron. |
| Water | Fallout-style: sinks (~2.5 blocks/s), walks the bottom at half speed, suit never drowns, ignores currents. Pilot's air drains normally once their head is under. |
| Animations | idle, walk (jog), run, jump_charge, jump, fall, land, hit, death (+ unused: attack, throw, stim, electrified, idle_fidget, sneakwalk). Walk/run hold the cannon steady (arm IK stabiliser). |
| Old saves | Suits loaded from older versions get current base stats (`readAdditionalSaveData`). |

## 3. Arms: one hand, one power socket
- **Left arm = a real hand.** It shows and uses the pilot's **currently selected hotbar item**
  (rendered in the suit's left hand). The **use** button (right-click) works as normal for that item:
  place blocks, eat, open doors, use buckets, throw potions...
  - The left hand does **not** break blocks: the attack button belongs to the socket tool.
  - **OPEN:** may the left hand attack mobs with a held sword? (Default: no, attack = socket tool.)
- **Right arm = power socket.** Exactly one **power tool** item can be installed. Tools only work when
  installed in a powered suit (they do nothing on foot) and draw fuel per use.
  - The **attack** button (left-click, hold for continuous tools) uses the installed tool.
  - Empty socket: attack does nothing (maybe a dry "clunk" sound).
  - The suit is crafted/deployed with an **empty socket**.
- The model's built-in cannon is removed from the suit; the right forearm ends in a **socket
  coupling**. Each tool is its own small model attached to the socket bone.

### Power tools
| Tool | Primary action (attack) | Fuel cost (initial guess) | Notes |
|---|---|---|---|
| **Plasma cannon** | Fires a plasma bolt (projectile, impact damage + small knockback, no block damage). Cooldown ~0.6 s. | 300 per shot | Reuses the existing cannon art, `attack` animation and `muzzle_flash` bone. First tool to build. |
| **3x3 drill** | Hold to drill: breaks the targeted block plus the 8 around it in the plane facing the pilot. Respects harvest tier (diamond-equivalent?), never unbreakable blocks, drops items normally. | 60 per block | Fire `BlockEvent.BreakEvent` for every block so claim/protection mods can veto. |
| **Chainsaw** | Hold on a log: cuts it; when the bottom log of a tree is cut, fells the whole tree (connected logs, cap ~256). | 40 per log | "Natural tree" heuristic: connected logs with leaves attached, so log cabins aren't eaten. |
| **EM tool** (electromagnetic) | Hold = **pull** the targeted item entity / mob toward the suit; release / second press = **push** it away. | 2 per tick held, 200 per push | Replaces the earlier "gravity gun". **OPEN:** blocks too, or only items + mobs? **OPEN:** also usable on foot in a weaker form (it was originally a gun-like handheld)? Default: socket-only like the other tools. Never bosses or players (unless a PvP config allows). |

## 4. Fuel tank and shutdown
- **Energy unit = furnace burn ticks.** Any furnace fuel works: coal/charcoal 1600, blaze rod 2400,
  coal block 16000, lava bucket 20000 (returns the empty bucket). Better fuel = more energy per item,
  which is how coal/blaze/lava "tiers" show up.
- **Tank capacity 18,000 units** so a full tank is ~15 minutes of walking (see drain table). One lava
  bucket fills it. **OPEN:** the user mentioned "fuel tank tier (coal/lava/blaze rod)": this could
  instead mean upgradeable tanks (bigger capacity) or fuel-specific tanks. Default: one tank, fuel
  quality = burn time.
- **Drain per tick (piloted):** idle 0.25, moving 1.0, sprinting 2.0, airborne/jumping as moving, plus
  tool costs per use. A parked empty suit drains nothing.
- **Refuelling:** put fuel in the suit menu's fuel slot; items are consumed into the tank whenever
  there's room for a whole item (like a furnace). Hoppers into the fuel slot: nice-to-have.
- **Shutdown at 0:** the suit freezes (no movement, no jump, no tools), plays a power-down sound and a
  slumped `shutdown` pose, smoke stops. The pilot can still climb out. Adding fuel restarts it
  (`startup` animation + sound).
- **Low fuel (< 10%)**: gauge flashes, smoke turns dark and sputters, warning beep.

## 5. Suit menu (inventory UI)
- Opened on a **parked** (empty) suit. Tools and fuel can only be changed there: the pilot has to get
  out, which makes the loadout a decision.
- Contents: **fuel slot**, **tank gauge**, **socket slot** (accepts power tools only), suit health,
  a **Pack up** button (replaces sneak + right-click packing), and the player inventory below.
- Contents persist on the entity (NBT) and are kept when packed into the item (custom data on the
  item stack), so packing never deletes tools or fuel.
- **OPEN (gesture):** the user asked for "right-click like a chest", but right-click is how you climb
  in. Recommended: follow the vanilla horse/llama convention: **sneak + right-click = menu**,
  right-click = climb in. Alternative: right-click = menu with an "Enter" button.

## 6. Feedback
- **Exhaust smoke** from the two back pipes (`exhaust_r`, `exhaust_l` bones, tops at ~y 48 px):
  idle = occasional light puffs; walking = steady; sprinting / tool use = heavy; low fuel = dark,
  sputtering; shutdown = none. Client-side particles at the pipe tops' world positions (they follow
  the animated torso).
- **HUD gauge** while piloting: fuel bar (and maybe the tool's name/cooldown) near the hotbar.
- Sounds: engine idle loop pitched by load (nice-to-have), power-down/up, tool sounds.

## 7. Balance knobs (all constants in code, later a config)
Tank capacity, drain per state, tool costs, tool cooldowns, drill harvest tier, chainsaw cap, EM range
and strength, cannon damage.

## 8. Out of scope for now
Wearable armor, multiple sockets, upgrade trees, multiplayer permissions beyond "never grab players".
