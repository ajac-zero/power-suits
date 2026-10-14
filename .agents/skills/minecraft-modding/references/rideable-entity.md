# Rideable / enterable entities (vehicles, mechs)

All of this is in `src/main/java/dev/ajaczero/powerarmor/entity/PowerArmorEntity.java` with comments.

## Riding: reuse the horse hooks (LivingEntity / Mob, 1.21.1)
| Hook | Use |
|---|---|
| `mobInteract(player, hand)` | right-click: `player.startRiding(this)` on the server; return `sidedSuccess` |
| `getControllingPassenger()` | return the first passenger if it is a `Player` |
| `getRiddenInput(player, vec)` | `player.xxa` (strafe), `player.zza` (forward); scale backwards/strafe |
| `getRiddenSpeed(player)` | attribute speed, times a sprint multiplier if `player.isSprinting()` |
| `tickRidden(player, vec)` | `setRot(player.getYRot(), player.getXRot())`, head yaw, jumps |
| `canSprint()` | `true`, otherwise `LocalPlayer` won't sprint while riding |
| `PlayerRideableJumping` | horse jump bar: `onPlayerJump(power)` runs on the pilot's client at release; `handleStartJump` on the server; `getJumpCooldown()!=0` blocks charging |
| `getDismountLocationForPassenger` | default is the top of the hitbox: search behind/beside with `getCollisionHorizontalEscapeVector` + `DismountHelper` like `AbstractHorse` |
- **Movement is client-authoritative**: `travelRidden` calls `travel` only where
  `isControlledByLocalInstance()` (26.x: `isLocalInstanceAuthoritative()`) (pilot's client; the server for an empty mob). Logic in `travel` /
  `tickRidden` runs there; sync anything the server must enforce.
- Ground speed: measured **42.14 blocks/s per point of movement speed** for a ridden mob on normal
  ground on 1.21.1, **43.15 on 26.3**. Calibrate with an autopilot log after every Minecraft update; don't trust the derivation.
- 26.x: the server no longer gets a passenger's `xxa` / `zza`; read `ServerPlayer.getLastClientInput()` (keys) there.
- Air control is speed x 0.1 by default, so jumps/falls bleed speed.
- Shift dismounts (vanilla), so don't plan a "sneak" gait for the pilot.

## Pilot and camera
- Seat height: `EntityType.Builder.passengerAttachments(new Vec3(0, y, z))`. Player vehicle offset is
  0.6 and eye height 1.62, so eye = attachment.y + 1.02. `z` moves the seat forward (rotated by yaw).
- **Crosshair trap**: if the pilot's eye is inside the vehicle's hitbox, `ProjectileUtil.getEntityHitResult`
  always returns the vehicle (can't mine or use blocks). `isPickable()` -> `!isVehicle()`; hits aimed at
  the vehicle then land on the pilot and get redirected (below).
- Hide the pilot: cancel `RenderLivingEvent.Pre` for entities riding the vehicle; cancel `RenderHandEvent`.
- Third-person distance: `CalculateDetachedCameraDistanceEvent.setDistance`.
- Camera angles (footfall bob): `ViewportEvent.ComputeCameraAngles` (pitch/roll). Derive the bob from the
  playing gait's hip bone relative to that gait's known mean, eased; zero for other states.
- FOV: `ComputeFovModifierEvent.setNewFovModifier`.
- Client tick hooks: `ClientTickEvent.Post` (e.g. read `LocalPlayer.getJumpRidingScale()` +
  `options.keyJump.isDown()` to drive a crouch wind-up animation while charging).
- Legs vs torso: keep `yRot`/`yHeadRot` = look (WASD stays view-relative) and drive `yBodyRot` (legs)
  yourself by overriding `tickHeadTurn` (runs on every side): face travel direction while moving
  (flip when backing up), idle catch-up past ~50 deg, clamp twist to 90. The model twists the torso by
  `netHeadYaw`.

## Damage and safety
- Redirect pilot damage: `LivingIncomingDamageEvent` on the game bus: if the victim rides the vehicle,
  cancel and `vehicle.hurt(source, amount)`. Let through `BYPASSES_INVULNERABILITY`, starvation, drowning;
  skip explosions (they hit the vehicle too).
- `causeFallDamage` -> false (also protects passengers). `isPushable` false, knockback resistance 1.
- Death: `die()` -> `ejectPassengers()`; override `tickDeath` to linger for the death animation, then
  `broadcastEntityEvent(this, 60)` + `remove(KILLED)`; extra drops in `dropCustomDeathLoot`.
- Persistence: `requiresCustomPersistence()` true, `removeWhenFarAway` false.

## Item <-> entity
- Deploy: `Item.useOn` on the server: `type.create(level)`, `moveTo(clicked face, yaw facing player)`,
  refuse if `!level.noCollision(entity, entity.getBoundingBox())`, `addFreshEntity`, `stack.consume(1, player)`.
- Durability bar = vehicle health (`Item.Properties().durability(maxHealth)`, `isValidRepairItem`) is an
  intuitive way to carry damage through pack/deploy; carry the name via `DataComponents.CUSTOM_NAME`.

## Terrain feel (measure with the terrain autopilot)
- Step-up: `Attributes.STEP_HEIGHT` (1.1 for full blocks).
- Step-down / ground snap: override `travel`; after `super.travel`, if we were on the ground, aren't
  jumping and are now airborne, and `!level.noCollision(this, box.expandTowards(0, -D, 0))`, then
  `move(MoverType.SELF, (0, -D, 0))`. D = 2.1 for a 3-block mech kept fall/land states off slopes.
- Eased steps: on the client, detect on-ground y jumps, accumulate `stepOffset -= dy`, decay x0.65 per
  tick; apply to the pilot via `getPassengerAttachmentPoint` (+offset) and to the model with a
  `poseStack.translate` in the renderer (lerp with partial ticks).
- Airborne animation states need hysteresis: fall pose only after ~6 ticks in the air, landing only after ~10.

## Water (Fallout-style heavy suit)
Vanilla's water branch in `LivingEntity.travel` runs before NeoForge's `moveInFluid` hook for water, so
override `travel`: when `isInWater() && isControlledByLocalInstance()` do your own: `moveRelative`
(reduced speed, less control while not on ground), `move`, then sink (`vy = (vy - 0.025) * 0.85`), XZ
drag 0.546, and vanilla's "climb out at the edge" boost when `horizontalCollision` and free above.
Vehicle can't drown: override `canDrownInFluidType` (NeoForge). Ignore currents: `isPushedByFluid()` false.
The pilot's air drains by vanilla rules (eye position); the HUD shows bubbles above vehicle hearts.

## Saved data migration
Entities save attribute **base values**: changing defaults in `createAttributes()` does not affect
entities already in worlds. Keep tuning in one map and re-apply it in `readAdditionalSaveData`; add a
GameTest that saves an entity with old values, loads it, and checks the current values.
