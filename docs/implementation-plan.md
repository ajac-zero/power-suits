# Implementation plan

Read `docs/design.md` first (the spec) and `AGENTS.md` (how to work here). Each phase must end with:
build passes, all GameTests pass, an autopilot run looks right (screenshots / CSV), README + design
docs updated, a git commit, and a short explanation for the user (a first-time modder: teach the
concepts as you go).

Confirm the **OPEN** items in `docs/design.md` with the user before building the parts they affect.

## Phase 1: fuel, suit menu, shutdown, smoke, gauge (DONE 2026-10-12)
Built as specified in `docs/design.md` sections 4-6, with the user's decisions: furnace-style fuel
(item by item, coal shortest, blaze rod longest, no tank) and sneak + right-click for the menu.
Where things are: `SuitFuel` (fuel table), `PowerArmorEntity` (`tickFuel`, power flag + transitions,
inventory, save/load, `packUp`, animation states), `ExhaustSmoke`, `menu/PowerArmorMenu`,
`client/PowerArmorScreen` + `tools/make-gui.py`, `client/FuelGauge`, `item/SuitLoadout` +
`ModDataComponents`, `item/PowerTool` (empty marker). Animations `shutdown` / `powered_off` / `startup`
from `starcraft_marine_anim/a_power.js`. Tests: 9 new GameTests (17 total); autopilot fuel scenario
(`ap_fuel_*`, `ap_menu_*`, `run/autopilot_fuel.csv`).
Not done: hoppers into the fuel slot; exhaust pipe bone positions are computed from the entity transform
(`ExhaustSmoke.PIPE_*`), not tracked GeckoLib bones (ignores the torso lean, looks right in game).

## Phase 2: socket + plasma cannon (DONE 2026-10-12)
Built: `PowerTool` (energyPerUse, cooldownTicks, suitBone, use), `PlasmaCannonItem` (muzzle position from
measured bone offsets + torso lean, aim point by ray-tracing the pilot's view, GeckoLib item), `PlasmaBolt`
(+ `PlasmaBoltRenderer`, `powerarmor:plasma` damage type), `ToolTriggerPayload` + `client/ToolInput`
(attack intercepted, state sent on change), suit `tickTool` / `consumeEnergy`, renderer tool-bone
visibility, `socket` coupling in the model, `plasma_shot` animation (`a_plasma_shot.js`),
`tools/extract-tool-geo.py`, item display transforms, recipe. Tests: 7 new GameTests (24 total); autopilot
cannon range + item views (`ap_cannon_*`, `ap_item_*`, `run/autopilot_cannon.csv`).
Not done: muzzle position is computed from constants, not the animated bone (fine at the bolt's speed);
no glowmask (emissive) texture yet; fuel cost per shot to be balanced (user: later).
For the next tools: add the tool's bone to the suit model (child of `right_forearm`), its name to
`PowerArmorRenderer.TOOL_BONES` and `extract-tool-geo.py`, implement `PowerTool`, register the item + client
extension, add a `models/item/<tool>.json`.

## Phase 3: left hand holds and uses items
1. Render the pilot's main-hand item at the suit's left hand bone (`hand_l`) with the item layer.
2. Use (right-click) stays vanilla (the player uses their item). Block breaking and attacking with the
   hand are blocked while piloting (Phase 2's attack interception already routes attack to the tool;
   also cancel `PlayerInteractEvent.LeftClickBlock` / `AttackEntityEvent` server-side for pilots).
3. Left-arm use animation (overlay) when the pilot uses an item. Remove the grenade prop.
4. **Check reach**: the pilot's eye is inside the suit at the visor; block/entity reach from there
   should feel right (maybe `Attributes.BLOCK_INTERACTION_RANGE` bonus while piloting).

## Phase 4: EM tool (push/pull)
Hold attack: tractor beam on the targeted item entity / mob within ~16 blocks: pull it to a hold
point in front of the socket (server sets velocity each tick, disables gravity while held), fuel per
tick. Release / second press: push (launch along the view, damage on impact for mobs). Exclusions:
bosses (`Tags.EntityTypes.BOSSES`), players, entities with passengers. Beam particles client-side.
Resolve the OPEN items (blocks? on-foot version?) first.

## Phase 5: 3x3 drill and chainsaw
- **Drill**: hold attack; break progress per target block like mining (speed set by tool tier); on
  completion break the 3x3 in the hit face's plane (`hit.getDirection()`), each via
  `ServerPlayerGameMode.destroyBlock` or `level.destroyBlock` + `BlockEvent.BreakEvent` firing so
  protections work; skip unbreakable / harvest-tier-too-high blocks; fuel per block.
- **Chainsaw**: on cutting a log, if it's the bottom of a natural tree (logs connected upward with
  leaves present), BFS-collect connected logs (26-neighbourhood, cap 256) and break them over a few
  ticks (top-down looks good), fuel per log.
- Tests for both: count of broken blocks, drops present, protected/unbreakable blocks untouched,
  fuel consumed.

## Phase 6: polish
Sounds (engine loop pitched by load), recipes for every tool and fuel balance pass, a config file
(`ModConfigSpec`) for balance knobs, README + in-game tooltips.

## Art (Blockbench, `~/Developer/Models/starcraft_marine.bbmodel`)
Use the `blockbench-mcp` skill (`~/Developer/Models/.agents/skills/blockbench-mcp/`), checkpoint
before every change, save only when the user asks, re-export with `node tools/export-model.mjs`.
- Phase 1 (done): `shutdown`, `powered_off`, `startup` (`a_power.js`, planted feet IK).
- Phase 2 (done): `socket` coupling bone under `right_forearm`, `plasma_shot` overlay; the item model is
  extracted from the suit geo. Walk/run's stabiliser holds the forearm, so it steadies any tool.
- Phase 3: left-hand grip pose that holds an item; remove the grenade bone and update `throw` or retire it.
- Phase 4/5: models for the EM tool, drill, chainsaw (gun-like, sized for the socket) + tool-use
  overlays (recoil, drill vibration, chainsaw cut).
