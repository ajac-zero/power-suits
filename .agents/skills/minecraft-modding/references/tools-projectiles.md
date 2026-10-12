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
