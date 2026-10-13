# Vehicle-mounted tools, input packets, projectiles, GeckoLib items (NeoForge 21.1)

Built for the Power Armor plasma cannon. Files: `item/PowerTool.java`, `item/PlasmaCannonItem.java`,
`entity/PlasmaBolt.java`, `network/ToolTriggerPayload.java`, `client/ToolInput.java`,
`client/PlasmaBoltRenderer.java`, `PowerArmorEntity.tickTool`, `tools/extract-tool-geo.py`.

## Hold-to-use input from a vehicle
- Client: `InputEvent.InteractionKeyMappingTriggered` -> `if (event.isAttack() && piloting) { setCanceled(true); setSwingHand(false); }`
  (stops punching / mining). In `ClientTickEvent.Post` send the button state **only when it changes**:
  `PacketDistributor.sendToServer(new Payload(down))`; send `false` when leaving the vehicle or opening a screen.
- Payload: `record P(boolean down) implements CustomPacketPayload`, `Type` with a ResourceLocation,
  `ByteBufCodecs.BOOL.map(P::new, P::down)`; register on the mod bus with
  `RegisterPayloadHandlersEvent -> event.registrar("1").playToServer(TYPE, CODEC, handler)` (handlers run on
  the main thread by default).
- Server: validate (`player == vehicle.getControllingPassenger()`), store `triggerHeld`, and run the tool in
  the vehicle's server tick with a cooldown. Gate on power/fuel there, not on the client.

## Aiming from an offset muzzle
- Muzzle = vehicle position + yaw-rotated offset (forward `(-sin y, 0, cos y)`, right `(-cos y, 0, -sin y)`),
  measured from the model bone in Blockbench (scene coords / 16; Blockbench's scene X is the model's right).
- Aim point = `level.clip(eye -> eye + view*range)` then `ProjectileUtil.getEntityHitResult(level, pilot, eye, blockEnd, box, filter)`
  excluding the vehicle and pilot. Direction = aimPoint - muzzle; if it doesn't point roughly along the view
  (dot < 0.5, target very close) fire along the view instead.

## Projectile
- `extends Projectile`; tick: `super.tick()`, `ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity)`,
  `EventHooks.onProjectileImpact` then `hitTargetOrDeflectSelf`, move with `setPos(pos + motion)`,
  `rotateTowardsMovement`. Runs on both sides (smooth); only the server applies damage/discards.
- `canHitEntity`: vanilla already skips entities in the owner's vehicle stack (`isPassengerOfSameVehicle`).
- Damage type as data: `data/<mod>/damage_type/x.json` (`message_id`, `scaling`, `exhaustion`), add it to
  `data/minecraft/tags/damage_type/is_projectile.json`, lang `death.attack.<message_id>[.player|.item]`.
  `damageSources().source(KEY, projectile, shooter)`: **projectile first** (parameter names lie).
- Entity type: small `sized`, `updateInterval(1)` for fast bolts. `isPickable`/`hurt` false so it can't be punched back.
- Renderer: camera-facing quad like `DragonFireballRenderer`, `RenderType.entityTranslucentEmissive`,
  `LightTexture.FULL_BRIGHT`; trail = client particles in `tick()` (`DustParticleOptions(color, size)`, `END_ROD`).
  Impact effects from the server with `ServerLevel.sendParticles` / `playSound(null, ...)`.

## Tool models in the vehicle model
- Keep each tool as a bone in the vehicle model and toggle `bone.setHidden(...)` in the renderer's
  `preRender` from a synced ItemStack; a bare "socket" bone shows when empty. Tools keep their own animated
  bones (muzzle flash) and the existing animations.
- Per-use animation: `triggerableAnim` on an overlay controller + `triggerAnim` from the server. GeckoLib does
  not restart a still-running triggered animation: keep it shorter than the cooldown.

## GeckoLib item from the same model
- Cut the bones out of the exported entity geo with a script (copy subtree + extra cubes, recentre the bounding
  box on the **origin**, one root bone). Same texture: override `getTextureResource` in a `DefaultedItemGeoModel`.
  An empty `animations/item/<name>.animation.json` keeps GeckoLib quiet.
- Item: `implements GeoItem` (cache + empty `registerControllers`). Renderer: register an `IClientItemExtensions`
  returning a `GeoItemRenderer` in `RegisterClientExtensionsEvent` (GeckoLib doesn't do it on NeoForge 21.1).
  Model JSON: `"parent": "builtin/entity"` + `display` transforms.
- Display rotations are applied X·Y·Z (Z first on the vector). Solve them instead of guessing: choose where the
  barrel and the top should point, build the rotation matrix, brute-force Euler angles in 5° steps
  (numpy). In hand / first person, -Z is forward; in GUI the camera looks along -Z. Verify with autopilot
  screenshots: GUI slot, first person, third person front.

## Held items in a vehicle's hand
- `BlockAndItemGeoLayer` subclass on the vehicle renderer: return the rider's main-hand stack for the grip
  bone, then in `renderStackForBone` copy vanilla `ItemInHandLayer`'s grip (`XP -90`, `YP 180`,
  translate `(-1/16, 0.125, 0)` at a bone placed at the grip, not the shoulder), scale for the bigger hand,
  `renderStatic(entity, stack, THIRD_PERSON_LEFT_HAND, true, ...)`. Skip it in the rider's own first-person view.
- First person: cancel `RenderHandEvent` and redraw the item with vanilla's first-person math mirrored
  (`applyItemArmTransform`, `applyItemArmAttackTransform`, swing translate, `applyEatTransform` are private:
  copy their few lines), `ItemInHandRenderer.renderItem(player, stack, FIRST_PERSON_LEFT_HAND, true, ...)`.
- Swing reaction: watch the rider's `swinging` rising edge on the client (other players' swings arrive as
  packets) and `triggerAnim` locally; hold pose = a controller added after the gait controller (it owns the
  bones it animates while playing). Return STOP when the vehicle is off so the slump pose wins.
- Rules for the rider: cancel `AttackEntityEvent`, `PlayerInteractEvent.LeftClickBlock`, `BlockEvent.BreakEvent`;
  `RightClickBlock/RightClickItem/EntityInteract(Specific)` when unpowered. Test with
  `CommonHooks.onLeftClickBlock/onRightClickBlock(...).isCanceled()` and `player.attack(mob)` in GameTests.
- Reach: `EntityMountEvent` (server) -> `addTransientModifier(new AttributeModifier(id, 1.5, ADD_VALUE))` on
  `BLOCK_INTERACTION_RANGE` / `ENTITY_INTERACTION_RANGE`, remove on dismount. Transient = not saved; re-added
  when the saved player re-mounts on load.

## Continuous tools (gravity-gun style)
- Give the tool interface a lifecycle: `holdTick` every tick while held, `release(interrupted)` when the trigger
  goes up or anything changes (power, rider, tool swapped, vehicle removed: override `remove()`), `serverTick`
  for follow-ups (thrown things). Keep per-vehicle state on the vehicle (items are singletons).
- Hold an entity: `setNoGravity(true)` (restore the original flag), steer with
  `setDeltaMovement(v*0.3 + clamp((hold-pos)*0.3, 1)*0.7)`, `hurtMarked = true` to sync, `fallDistance = 0`.
  No-AI mobs never move by velocity. Collect items with `ItemEntity.playerTouch(player)` (+ `setNoPickUpDelay`).
- Wall slam: remember the thrown mob's speed each tick; when `horizontalCollision` and last speed > threshold, hurt.
- Sync what the beam holds as an entity id in synced data; clients draw the beam with particles.

## Mining tools (3x3 drill, tree-felling chainsaw)
Files: `item/ToolMining.java`, `item/DrillItem.java`, `item/ChainsawItem.java`, `PilotHands.noBreaking`.
- Break blocks like vanilla `ServerPlayerGameMode.destroyBlock`, with a *virtual* tool stack (e.g.
  `new ItemStack(Items.DIAMOND_PICKAXE)`) instead of the hand item: post your own
  `new BlockEvent.BreakEvent(level, pos, state, player)` (protection mods veto here; `CommonHooks.fireBlockBreak`
  needs a ServerPlayer and pre-cancels on the hand item), then `block.playerWillDestroy` (door halves, piglins,
  break effect for *other* players), `state.onDestroyedByPlayer(level, pos, player, true, fluid)`, `block.destroy`,
  `Block.dropResources(state, level, pos, blockEntity, player, tool)` (loot table + XP via `BlockDropsEvent`;
  skip in creative like vanilla), and send `ClientboundLevelEventPacket(2001, pos, Block.getId(state), false)`
  to the player yourself (`levelEvent(player, ...)` excludes them).
- If you also cancel the rider's own BreakEvents (no mining by hand), exempt your tool's posts with a
  ThreadLocal flag set around `post()`, or the tool can never break anything.
- Harvest check without a real item: `state.is(BlockTags.MINEABLE_WITH_PICKAXE | _SHOVEL | _AXE)` and
  `!state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)`; `getDestroySpeed < 0` = unbreakable. Vanilla speed with the
  right tool: progress per tick = toolSpeed / hardness / 30 (diamond = 8: stone 6 ticks, obsidian 188).
- Crack overlay: `serverLevel.destroyBlockProgress(id, pos, stage 0..9)`, `-1` removes. Use **negative ids**
  (one per block): players' own ids are their entity ids and the packet is skipped for the player whose id
  matches.
- Natural trees: BFS over `BlockTags.LOGS` in the 26-neighbourhood (cap ~256), count touching leaves without
  `LeavesBlock.PERSISTENT` (player-placed leaves are persistent). Collect *before* breaking the cut log, then
  break the rest top-down from a queue in `serverTick` (a few per tick). Copy positions from
  `BlockPos.betweenClosed` (it reuses one mutable instance).
- Tool loop animations: sync a `TOOL_RUNNING` boolean and add a controller that plays the tool's loop. Key only
  the tool's own bones, or the controller overrides the arm the gait/aim animations move.
- GameTests: build the target wall inside the pad, aim the mock pilot (yaw/pitch from the eye), hold the
  trigger, assert blocks/drops (`ItemEntity` counts)/fuel; a static test-only `@SubscribeEvent` that cancels
  BreakEvents at one position stands in for a protection mod.
- Autopilot: keep tree canopies away from the third-person camera path (leaves pull the camera into the
  model); re-aim every tick while the vehicle turns (the eye position moves with it).
