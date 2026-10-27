# Entity inventory, menu, screen, item data, HUD (NeoForge 21.1)

Built for the Power Armor suit menu (fuel + tool socket, Pack up button) and fuel gauge. Files:
`menu/PowerArmorMenu.java`, `client/PowerArmorScreen.java`, `client/SuitHud.java` (was `FuelGauge`),
`item/SuitLoadout.java`, `registry/ModMenus.java`, `registry/ModDataComponents.java`.

## Entity inventory
- `SimpleContainer inventory = new SimpleContainer(n)` field; `inventory.addListener(c -> sync())` in the
  constructor to mirror slots into `EntityDataAccessor<ItemStack>` (`EntityDataSerializers.ITEM_STACK`) when
  clients need them (gauge, rendering). Only the server's container is real.
- Save with slot indices: `ContainerHelper.saveAllItems(new CompoundTag(), inventory.getItems(), registryAccess())`
  / `loadAllItems(tag, inventory.getItems(), registryAccess())`. (`SimpleContainer.fromTag` loses indices.)
- Refresh every synced copy wherever the server value changes (load, setters), not only in `tick()`.
- Furnace-style consumption: `stack.getCraftingRemainingItem()` (lava bucket -> bucket), `stack.shrink(1)`,
  then `setItem` / `setChanged()` so listeners fire.

## Menu (logic, both sides)
- Register: `DeferredRegister.create(Registries.MENU, MODID)` +
  `IMenuTypeExtension.create((id, inv, buf) -> ...)` so the server can send extra data (the entity id).
- Open on the server: `player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MyMenu(id, inv, container, this), name),
  buf -> buf.writeVarInt(getId()))`. Client factory: read the id, `level().getEntity(id)`, use a fresh
  `SimpleContainer(n)` (slot contents arrive by vanilla sync).
- `Slot` subclasses override `mayPlace` / `getMaxStackSize`. Write `quickMoveStack` explicitly (suit slots
  -> player; player -> matching suit slot; inventory <-> hotbar).
- `stillValid`: entity alive, not ridden, within 8 blocks.
- **Buttons without custom packets**: screen calls `minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)`,
  the server runs `menu.clickMenuButton(player, id)`.

## Screen (client)
- `AbstractContainerScreen<M>`; register in `RegisterMenuScreensEvent` (`event.register(type, Screen::new)`).
- Background: generate from a vanilla GUI with Pillow (`tools/make-gui.py` reads furnace.png from
  `build/moddev/artifacts/*client-extra*.jar`). Vanilla sprites: `container/furnace/lit_progress` via
  `blitSprite(id, 14, 14, 0, 14 - h, x, y + 14 - h, 14, h)`.
- Live entity preview: `InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0, mx, my, entity)`
  (scissors itself; scale 15 fits a 3-block mob in a 52 px box).
- Read live numbers from the entity's synced data in `renderLabels` (coordinates are relative to the panel).

## Item data component (1.21 replacement for item NBT)
- `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID)
  .registerComponentType("loadout", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC))`.
- Record with `ItemStack.OPTIONAL_CODEC.optionalFieldOf("x", ItemStack.EMPTY)` and
  `StreamCodec.composite(ItemStack.OPTIONAL_STREAM_CODEC, ...)`. Override `equals`/`hashCode` with
  `ItemStack.matches` / `hashItemAndComponents` (ItemStack has no equals).

## HUD layer
- `RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.HOTBAR, id, (graphics, delta) -> ...)`; skip when
  `options.hideGui` or not relevant. Hotbar right edge: `guiWidth()/2 + 91`. `renderItem` + `renderItemDecorations`
  draw an item with its count. Local-only sounds: `SimpleSoundInstance.forUI(sound, pitch, volume)`.
- *(26.3)* Hide a vanilla layer with `RenderGuiLayerEvent.Pre` + `setCanceled` on its id (`VanillaGuiLayers.VEHICLE_HEALTH`:
  the suit's 30 hearts). Tinted white masks: `blit(GUI_TEXTURED, tex, x, y, 0, 0, w, h, w, h, argb)`; colours carry alpha
  (fading). Bigger text: `g.pose()` is a `Matrix3x2fStack` (`pushMatrix`, `translate`, `scale`, `popMatrix`).
- *(26.3)* **Camera overlays (pumpkin-style):** `registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ...)` draws under the
  crosshair, hotbar and other HUD; check `options.getCameraType().isFirstPerson()` like vanilla. Anchor pieces to the
  corners in GUI pixels instead of stretching one picture (the pumpkin's 256x256 warps on wide windows). Mirror a texture
  with a negative source width: `blit(pipeline, tex, x, y, u = w, 0, w, h, srcWidth = -w, h, w, h, argb)`.
- *(26.3)* **Move a vanilla layer** (boss bars, effect icons) out of your HUD's way: in `RenderGuiLayerEvent.Pre`
  (priority LOWEST, skip if cancelled) `pose().pushMatrix()` + `translate(0, dy)` for that layer's id, `popMatrix()` in
  `Post`. Boss bars start at y 12 (name 9 above, 19 apart); effect icons at the top right, y 1 (good) and 27 (bad).
- *(26.3)* **World time:** `level.getOverworldClockTime()` (ticks; day = t / 24000 + 1, 06:00 at tick 0) replaced
  `getDayTime()`. The vanilla clock item only reads in the overworld (`context_dimension` select), the compass spins
  where the spawn isn't.
- *(26.3)* **Two config files** (CLIENT next to COMMON): `registerConfig(ModConfig.Type.CLIENT, SPEC)` in the client
  @Mod -> `<modid>-client.toml`; the config screen then lists both, labelled by
  `<modid>.configuration.section.<modid>.client.toml` (+ `.title`).
- A rate the server doesn't sync (the burn embers) can be measured on the client from a synced total tick by tick;
  clamp one-off jumps (refuels, a flooded firebox) or they swamp the smoothing for seconds.

## Particles
- Visual only: spawn on the client in the entity's tick (`level.addParticle`), from synced state. Put a
  fixed model point into the world with the yaw the bone follows (`forward = (-sin yaw, 0, cos yaw)`);
  symmetric pairs (two pipes) make GeckoLib's X mirroring irrelevant. `LARGE_SMOKE` is big: one every
  other tick per source is already a thick trail.

## Config files + in-game config screen (NeoForge 21.1)
- `ModConfigSpec.Builder`: `B.comment(..).translation(key).push("section")`, `defineInRange(name, def, min, max)`,
  `B.pop()`, `SPEC = B.build()`. Register in the `@Mod` constructor: `container.registerConfig(ModConfig.Type.COMMON, SPEC)`
  (needs a `ModContainer` constructor parameter). COMMON = one file for the game (`config/<modid>-common.toml`),
  written with comments on first launch, reloaded when the file changes; SERVER = per world, synced to clients.
- Read values with `.get()` at the moment of use (cached by NeoForge, refreshed on reload). Section labels need
  `.translation()` *before* `push`.
- Config screen: a second `@Mod(value = MODID, dist = Dist.CLIENT)` class whose constructor calls
  `container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`. Labels: the value's
  translation key, tooltips: `<key>.tooltip`, title `<modid>.configuration.title`. With one config file it opens
  the section directly.
- Tooltips on other mods'/vanilla items: `ItemTooltipEvent` (client, game bus), `event.getToolTip().add(...)`.

## Looping sounds
- `DeferredRegister<SoundEvent>` + `SoundEvent.createVariableRangeEvent(id)`; `assets/<modid>/sounds.json` maps each
  event to files or to other events (`{"name": "minecraft:entity.bee.loop_aggressive", "type": "event", "pitch": 0.75}`;
  pitch/volume multiply, final pitch is clamped to 0.5-2.0) plus a `subtitle` lang key.
- Client: `AbstractTickableSoundInstance` with `looping = true`, `delay = 0`, `canStartSilent() = true`; in `tick()`
  follow the entity (`x/y/z`), ease `volume`/`pitch`, `stop()` after fading out. Start them from a client tick
  handler that scans nearby entities and keeps one instance per entity id (drop stopped ones).
- Good vanilla loops to re-pitch: `entity.minecart.inside` / `entity.minecart.riding` (rumble / whir),
  `entity.bee.loop(_aggressive)` (buzz), `entity.guardian.attack` (whine), `item.elytra.flying` (wind).
