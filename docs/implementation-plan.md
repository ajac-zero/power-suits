# Implementation plan

Read `docs/design.md` first (the spec) and `AGENTS.md` (how to work here). Each phase must end with:
build passes, all GameTests pass, an autopilot run looks right (screenshots / CSV), README + design
docs updated, a git commit, and a short explanation for the user (a first-time modder: teach the
concepts as you go).

Confirm the **OPEN** items in `docs/design.md` with the user before building the parts they affect.

## Phase 1: fuel tank, suit menu, shutdown, smoke, gauge
1. **Suit storage** in `PowerArmorEntity`: `fuel` (int units), a 2-slot `SimpleContainer`
   (0 = fuel, 1 = socket). Save/load in `addAdditionalSaveData` / `readAdditionalSaveData`
   (1.21 item saving needs the registry provider: `ContainerHelper.saveAllItems(tag, items, registryAccess())`).
   Sync to clients with `EntityDataAccessor`s: `FUEL` (int), `SOCKET_ITEM` (ItemStack serializer, for
   rendering), `POWERED` (boolean).
2. **Fuel logic (server + controlling client)**: drain per tick by state (idle/moving/sprinting),
   refill from the fuel slot (`stack.getBurnTime(RecipeType.SMELTING)` in NeoForge 1.21.1; keep the
   container item, e.g. empty bucket). Movement code runs on the pilot's client (client-authoritative
   riding), so the client must also refuse to move when unpowered: gate `getRiddenInput`, jumping and
   `travel` on the synced `POWERED` flag.
3. **Shutdown/startup**: transitions fire sounds and (client) `shutdown` / `startup` animations
   (new Blockbench animations, see Art below). While shut down: zero input, no jump, no tools,
   `isPickable` unchanged, pilot can dismount.
4. **Menu**: `MenuType` registered with `IMenuTypeExtension.create((id, inv, buf) -> ...)`
   (buf carries the suit entity id); `PowerArmorMenu extends AbstractContainerMenu` with fuel slot,
   socket slot (accepts only items tagged/implementing `PowerTool`), player inventory, `ContainerData`
   for fuel/capacity; `PowerArmorScreen extends AbstractContainerScreen` (gauge, Pack up button ->
   custom payload to server). Register the screen in `RegisterMenuScreensEvent`. `stillValid` = suit
   alive, empty, within 8 blocks.
5. **Interaction change**: right-click = climb in; sneak + right-click = open menu (pending the OPEN
   gesture decision); packing moves to the button. Packing writes fuel + socket item into the item
   (`DataComponents.CUSTOM_DATA` or a small custom data component) and deploying restores them.
6. **Exhaust particles** (client): every tick, for each pipe top, compute its world position (GeckoLib:
   `bone.setTrackingMatrices(true)` once, then `bone.getWorldPosition()` after render, or compute from
   the entity transform + bone pivots) and spawn `SMOKE` / `CAMPFIRE_COSY_SMOKE` / `LARGE_SMOKE`
   by load; dark sputter when fuel < 10%.
7. **HUD gauge**: register a GUI layer (`RegisterGuiLayersEvent`) drawn only while piloting.
8. **Tests**: fuel item -> units (incl. bucket returned), drain over N ticks, shutdown blocks travel input,
   save/load keeps fuel + socket item, pack/deploy keeps them, menu slot accepts only tools.
   Autopilot: add a "run out of fuel" scenario (low tank, walk until shutdown, screenshot the slump +
   no smoke), log fuel per tick.

## Phase 2: socket + plasma cannon (first power tool)
1. `PowerTool` interface (or abstract item class): fuel cost, cooldown, `use(suit, pilot, hit)` on
   the server, animation names, model id. Items do nothing on foot (tooltip says "Install in a Power
   Armor socket").
2. **Input**: while piloting, intercept the attack key on the client
   (`InputEvent.InteractionKeyMappingTriggered` for attack: cancel vanilla, no arm swing) and send a
   custom payload (`RegisterPayloadHandlersEvent`, `CustomPacketPayload`) "tool start/stop".
   Server validates (pilot is controlling passenger, suit powered, cooldown, fuel) and runs the tool.
3. **Plasma cannon**: projectile entity (or hitscan + particles) from the muzzle, damage + knockback,
   fuel 300/shot, cooldown ~12 ticks; trigger the `attack` animation (has recoil + `muzzle_flash`)
   on the suit's "action" controller via `triggerAnim`.
4. **Rendering**: tool model attached to the socket bone with GeckoLib's `BlockAndItemGeoLayer`
   (or a custom `GeoRenderLayer`) using the synced `SOCKET_ITEM`; tools are GeckoLib items
   (`GeoItem`) or plain item models.
5. **Tests**: firing consumes fuel and respects cooldown; no fire when unpowered / empty socket;
   projectile damages a mob; tool does nothing on foot.

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
- Phase 1: `shutdown` (slump to a kneel/sag, hold) and `startup` animations (planted feet IK, see
  `ANIMATION.md`); confirm exhaust pipe top positions.
- Phase 2: split the cannon (`cannon`, `muzzle_flash` bones and the cannon cubes on `right_forearm`)
  into its own model file for the plasma-cannon item; add a socket coupling on `right_forearm`;
  re-check `walk`/`run` (they keep the cannon stabilised: with no cannon the stabiliser should now
  stabilise the socket/tool, see `stabilize()` in `starcraft_marine_anim/a_walk.js`).
- Phase 3: left-hand grip pose that holds an item; remove the grenade bone and update `throw` or retire it.
- Phase 4/5: models for the EM tool, drill, chainsaw (gun-like, sized for the socket) + tool-use
  overlays (recoil, drill vibration, chainsaw cut).
