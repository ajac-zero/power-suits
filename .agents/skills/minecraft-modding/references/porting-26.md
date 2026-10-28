# Porting 1.21.1 -> 26.3 (NeoForge 26.3, GeckoLib 5) — what changed for this mod

Done 2026-10-14 on Power Suits (branch `port-26.3`). The other references were written on 1.21.1; where their
snippets use old names, this table maps them. Method: bump versions, `./gradlew compileJava` with
`-Xmaxerrs 5000`, fix by grepping the new sources (`build/moddev/artifacts/minecraft-patched-*-sources.jar`,
NeoForge `*-sources.jar` in the Gradle cache, GeckoLib from GitHub), then GameTests, then the autopilot CSVs
compared column by column against a run of the old version (a `git worktree` of `main` with Java 21).
The neoforged primers (`github.com/neoforged/.github/tree/main/primers/<version>`) explain each step.

## Build
- Java 25 (`openjdk@25`), Gradle 9.2, MDG 2.0.148 (copy `build.gradle` bits from `NeoForgeMDKs/MDK-26.3-ModDevGradle`).
- No Parchment: official Mojang names include parameters since 26.1. `neoforge.mods.toml` lost `modLoader` /
  `loaderVersion`.
- GeckoLib: `com.geckolib:geckolib-neoforge-26.3:5.5.7` (cloudsmith repo, group `com.geckolib`, packages `com.geckolib.*`),
  plus `interfaceInjectionData` on the same coordinates (makes `EntityRenderState` a `GeoRenderState` at compile time).

## Renames (mechanical)
| 1.21.1 | 26.3 |
|---|---|
| `ResourceLocation` | `Identifier` (`fromNamespaceAndPath` unchanged) |
| `InteractionResultHolder<ItemStack> use(...)` | `InteractionResult use(...)`; `sidedSuccess(..)` -> `SUCCESS` |
| `player.displayClientMessage(c, true)` | `player.sendOverlayMessage(c)` |
| `entity.hurt(src, f)` returning boolean / overriding `hurt` | `hurtServer(ServerLevel, src, f)` (`hurt` is final void) |
| `entity.hurtMarked = true` | `entity.syncVelocity = true` (`needsSync` is the old `hasImpulse`) |
| `moveTo(...)` | `snapTo(...)` (navigation's `moveTo` stays!) |
| `EntityType.PIG` etc. | `EntityTypes.PIG`; `create(level)` -> `create(level, EntitySpawnReason.X)` |
| entity classes | moved into subpackages: `animal.pig.Pig`, `animal.golem.IronGolem`, `npc.villager.AbstractVillager`, `monster.zombie.Husk`... |
| `FlyingMob` | gone (ghasts/phantoms are plain `Mob`s) |
| `level.random` | `level.getRandom()` |
| `GameRules.RULE_DOMOBSPAWNING`, `getBoolean` | `GameRules.SPAWN_MOBS`, `level.getGameRules().get(rule)` / `.set(rule, v, server)` (package `world.level.gamerules`) |
| `server.isPvpAllowed()` | `serverLevel.getGameRules().get(GameRules.PVP)` |
| `invulnerableTime` (hit immunity) | `LivingEntity.damageCooldownTime`; better: damage type tag `minecraft:bypasses_cooldown` |
| `knockback(s, x, z)` | `knockback(s, x, z, source, damage)` |
| `spawnAtLocation(stack)` | `spawnAtLocation(serverLevel, stack)` |
| `isControlledByLocalInstance()` | `isLocalInstanceAuthoritative()` |
| `tickHeadTurn(yaw, step)` returns float | `tickHeadTurn(yaw)` void |
| `customServerAiStep()` | `customServerAiStep(ServerLevel)` |
| `swing(hand)` (mob) | `swingForAttack(hand)`; players: `swing(hand, stack.getAttackAnimation(), false)` |
| `pilot.swinging`, `swingingArm` | `isSwinging()`, `getCurrentSwing().hand()` |
| `stack.getCraftingRemainingItem()` | `getCraftingRemainder()` -> `ItemStackTemplate` (`create()`) or null |
| `UseAnim` | `ItemUseAnimation` |
| `new DustParticleOptions(Vector3f, s)` | `new DustParticleOptions(0xRRGGBB, s)` |
| `sendParticles(player, type, force, x..)` | extra `alwaysShow` boolean after `force` |
| `ProjectileUtil.getEntityHitResult(level, e, a, b, box, pred)` | add the margin (`0.3F` was the old default) |
| `OwnableEntity.getOwnerUUID()` | `getOwnerReference().getUUID()` |
| `entity.getType().is(tag)` | `entity.is(tag)` |
| `BlockEvent.BreakEvent` | `level.block.BreakBlockEvent`; `onDestroyedByPlayer` takes the tool stack |
| `PlayerInteractEvent.EntityInteractSpecific` | merged into `EntityInteract` |
| `common.util.TriState` | `net.minecraft.util.TriState` |
| `ModConfig.Type.COMMON` | `LOCAL` (also `CLIENT`, `SYNCED` (old SERVER), `STARTUP`) |
| `isValidRepairItem` | `Item.Properties.repairable(item)` |
| `appendHoverText(.., List<Component>, flag)` | `(.., TooltipDisplay, Consumer<Component>, flag)` |
| `PacketDistributor.sendToServer` | `ClientPacketDistributor.sendToServer` |
| `mc.screen`, `mc.setScreen` | `mc.gui.screen()`, `mc.gui.setScreen` |
| `mc.options.hideGui` | `mc.gui.hud.isHidden()` / `toggle()` |
| `camera.getLookVector/getUpVector/getLeftVector/getPosition`, `getMainCamera` | `forwardVector/upVector/leftVector/position`, `mainCamera()` |
| `poseStack.mulPose(Axis.X.rotationDegrees(a))` | `poseStack.rotateDegrees(Axis.X, a)`; quaternions: `rotate(q)` |
| `RenderType.eyes/entityCutout/entityCutoutNoCull` | `RenderTypes.eyes/entityCutoutCull/entityCutout` (cull names flipped) |
| `LightTexture.FULL_BRIGHT` | `LightCoordsUtil.FULL_BRIGHT` |
| `GuiGraphics`, `drawString`, `renderItem`, `blit(tex, x, y, u, v, w, h)` | `GuiGraphicsExtractor`, `text`, `item`, `blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, 256, 256)` |
| screen `render`/`renderBg`/`renderLabels` | `extractRenderState`/`extractBackground`/`extractLabels` |
| `RenderBlockScreenEffectEvent` | `ExtractBlockScreenEffectEvent` |
| `registryOrThrow`, `getHolderOrThrow` | `lookupOrThrow`, `getOrThrow` |
| `Registries.CONFIGURED_FEATURE` | `Registries.FEATURE` (no ConfiguredFeature wrapper) |
| `setDayTime(t)` | `level.clockManager().setTotalTicks(dimensionType().defaultClock(), t)` |

## Bigger changes
- **Registration**: `DeferredRegister.createEntities` + `registerEntityType(name, factory, category, b -> b...)`;
  items via `registerItem(name, Ctor::new, props -> props...)` (properties carry the id).
- **Saving**: `readAdditionalSaveData(ValueInput)` / `addAdditionalSaveData(ValueOutput)`; containers via
  `ContainerHelper.load/saveAllItems(input.childOrEmpty("Inventory"), items)`; UUID / BlockPos with
  `read/storeNullable(name, UUIDUtil.CODEC / BlockPos.CODEC)` (same NBT as before). Saved data: `SavedDataType(id,
  ctor, codec)` + `computeIfAbsent(TYPE)`. `SimpleContainer` has no listeners: override `setChanged`.
- **Server-side passenger input**: `ServerPlayer.getLastClientInput()` (keys), `xxa/zza` stay 0 on the server.
- **Rendering** (vanilla 1.21.2+ render states, 1.21.9+ submit): `EntityRenderer<T, S>` with `createRenderState`,
  `extractRenderState(entity, state, partial)`, `submit(state, poseStack, SubmitNodeCollector, CameraRenderState)`;
  custom vertices via `collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> ...)`.
  `RenderLivingEvent` has no entity: tag render states with `RegisterRenderStateModifiersEvent` + a `ContextKey`.
- **GeckoLib 5**: see geckolib.md "GeckoLib 5".
- **GameTests**: registry entries; see testing.md "GameTests on 26.x".
- **Data**: items need `assets/<mod>/items/<item>.json` (`{"model": {"type": "minecraft:model", "model": "<mod>:item/<x>"}}`);
  recipe ingredients are strings (`"minecraft:iron_ingot"`, `"#minecraft:logs"`).
- **No `ItemStack` in static fields** (data components aren't bound when classes load).

## Behaviour differences measured after the port
- Ridden ground speed per movement-speed point 42.14 -> 43.15 blocks/s (+2.4%): re-tune `WALK_SPEED`.
- Free fall slightly faster; lightning damages ~0.5 s later. Smoke particles look the same.
