# Blockbench model -> GeckoLib entity

## Export (Bedrock-format Blockbench project)
GeckoLib (4 and 5) reads Bedrock geometry (`format_version 1.12.0`) and Bedrock animation JSON, so use
Blockbench's own exporter through the MCP (`risky_eval`, read-only):
- geometry: `JSON.stringify(Codecs.bedrock.compile({raw: true}))`
- animations: `JSON.stringify(Animator.buildFile())`
- texture: `Texture.all[0].getDataURL()` (base64 PNG)
`../scripts/export_model.mjs` does all three over HTTP MCP and writes (GeckoLib 4 layout; Power Suits'
`tools/export-model.mjs` writes the GeckoLib 5 one: `assets/<modid>/geckolib/models/entity/<name>.geo.json`,
`geckolib/animations/entity/<name>.animation.json`, model id `<modid>:entity/<name>`)
`assets/<modid>/geo/entity/<name>.geo.json`, `animations/entity/<name>.animation.json`,
`textures/entity/<name>.png` (the `DefaultedEntityGeoModel` naming convention), renaming
`animation.<old>.*` to `animation.<name>.*`. Verify a re-export of an unchanged model is byte-identical
in content (JSON compare). Catmull-Rom keys and simple Molang (`math.sin(q.anim_time*…)`) work.
Item icon trick: an offscreen Blockbench view screenshot (`set_camera_angle`, png) has a transparent
background; crop to the bbox, resize to 32x32 LANCZOS, threshold alpha, add a dark 1 px outline.

## GeckoLib 5 (Minecraft 1.21.2+, used by Power Suits on 26.3)
Rendering is split like vanilla's: **extract** (with the entity: copy what you need into the render state) then
**render** (only the render state; drawing is queued and runs later).
```java
public class MyEntity extends Mob implements GeoEntity {
  private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
  public void registerControllers(AnimatableManager.ControllerRegistrar c) {          // com.geckolib.animatable.manager
    c.add(new AnimationController<>("movement", 5, this::movement));                   // no `this` argument any more
    c.add(new AnimationController<MyEntity>("action", 2, t -> PlayState.STOP).triggerableAnim("hit", HIT));
  }
  private PlayState movement(AnimationTest<MyEntity> t) {                            // was AnimationState
    t.controller().setAnimationSpeed(rate());     // speed integrates per frame now: may change any time
    t.controller().setTransitionTicks(2);         // was transitionLength
    return t.setAndContinue(WALK);
  }
}
public class MyRenderer extends GeoEntityRenderer<MyEntity, LivingEntityRenderState> {
  static final DataTicket<Float> AIM = DataTicket.create("mymod_aim", Float.class);
  public MyRenderer(EntityRendererProvider.Context ctx) { super(ctx, new MyModel()); withRenderLayer(new MyLayer(this)); }
  @Override public void addRenderData(MyEntity e, Void v, LivingEntityRenderState s, float partial) {
    s.addGeckolibData(AIM, e.getAim(partial));                                       // extract
  }
  @Override public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots bones) {
    float aim = info.getOrDefaultGeckolibData(AIM, 0F);                             // render: state only
    bones.ifPresent("torso", b -> b.setRotY(b.getRotY() + info.renderState().yRot * Mth.DEG_TO_RAD));
    bones.ifPresent("body", b -> b.skipRender(true).skipChildrenRender(true));      // hide (per frame)
  }
}
```
- Bone snapshots are fresh every frame and hold the animation's offset from the bone's rest pose (the rest pose is
  added at render time), so adding to them never piles up. Unkeyed bones start at 0: set code-driven angles directly.
- `LivingEntityRenderState.yRot` = head yaw - body yaw (wrapped), `xRot` = pitch (replace `EntityModelData`).
- Layers: `GeoRenderLayer<T, Void, R>` with `addRenderData` (extract) and `submitRenderTask(info, collector)`;
  re-draw the model with another render type through `renderer.submitRenderTasks(info, collector.order(1), type)`
  (it reads tint / light / overlay from the state's `RENDER_COLOR` / `PACKED_LIGHT` / `PACKED_OVERLAY` tickets when
  called: set, submit, restore). Items on bones: `BlockAndItemGeoLayer` with `RenderData.item(bone, context,
  RenderUtil.createRenderStateForItem(stack, itemModelResolver, context, entity))`.
- Objects that aren't entities (Power Suits' cockpit viewmodel): `GeoObjectRenderer<T, Void, GeoRenderState>` and
  `performRenderPass(animatable, null, poseStack, collector, cameraState, light, partial)`; anything that differs
  between two passes in a frame must go into the render state (fields are read too late).
- Paths: `assets/<mod>/geckolib/models/<id>.geo.json`, `geckolib/animations/<id>.animation.json`; textures unchanged.
- Render types: `RenderTypes.entityCutout` (default, both faces) / `entityCutoutCull` / `eyes`.
- `getDeathMaxRotation(GeoRenderState)`; `withScale`; `shadowRadius` field as before.

## GeckoLib 4 (1.21.1) — kept for reference

## Entity side
```java
public class MyEntity extends Mob implements GeoEntity {
  private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
  static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.my.walk");
  public void registerControllers(AnimatableManager.ControllerRegistrar c) {
    c.add(new AnimationController<>(this, "movement", 5, this::movement)       // 5-tick blend
          .setAnimationSpeedHandler(e -> e.rate()));
    c.add(new AnimationController<>(this, "action", 2, s -> PlayState.STOP)
          .triggerableAnim("hit", HIT));                                       // server: triggerAnim("action","hit")
  }
  public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
```
- State handlers run on the client; return `state.setAndContinue(ANIM)` or `PlayState.STOP`
  (STOP = rest pose). Keep `RawAnimation`s static (same object = no restart).
- Chains: `thenPlay("jump").thenLoop("fall")`, holds: `thenPlayAndHold("death")`.
- `state.getController().transitionLength(n)` can shorten blends for snappy states (jump).
- Later controllers override earlier ones **per bone**: make one-shot actions upper-body overlays so
  legs keep walking underneath.
- **Speed handler multiplies the whole elapsed time** (`adjustTick = speed * (tick - offset)`): a smoothly
  varying rate makes the pose jump. Use constant rates per state (walk / run / underwater).
- Match animation to movement: measure the authored ground speed of each gait (planted foot travel /
  stance time, see the Blockbench skill's `scripts/speed.mjs`), then rate = in-game speed / authored speed.
  Rates above ~1.3 look frantic: author longer strides instead.
- Read the playing animation (for logs/logic):
  `cache.getManagerForId(getId()).getAnimationControllers().get("movement").getCurrentAnimation().animation().name()`.
- Server -> clients one-shots: `triggerAnim`. Other client-visible events: a synced counter in
  `SynchedEntityData` bumped by the server, compared on the client (initialise from the value when the
  entity first appears so an old event doesn't replay).

## Model / renderer side
```java
public class MyModel extends DefaultedEntityGeoModel<MyEntity> {
  public MyModel() { super(ResourceLocation.fromNamespaceAndPath(MODID, "my_entity")); }  // turnsHead=false
  @Override public void setCustomAnimations(MyEntity e, long id, AnimationState<MyEntity> s) {
    EntityModelData d = s.getData(DataTickets.ENTITY_MODEL_DATA);           // netHeadYaw, headPitch
    GeoBone torso = getAnimationProcessor().getBone("torso");
    torso.setRotY(torso.getRotY() + d.netHeadYaw() * Mth.DEG_TO_RAD);       // ADD to the keyframed value
  }
}
public class MyRenderer extends GeoEntityRenderer<MyEntity> { ... }        // register in EntityRenderersEvent.RegisterRenderers
```
- `setCustomAnimations` runs after keyframes each frame; add to bone values instead of replacing them
  (only for bones every playing animation sets, or values accumulate). The default head-turn sets, not adds.
- `netHeadYaw = yHeadRot - yBodyRot`: drive the legs with `yBodyRot` and the torso with the difference to
  get independent legs/torso aiming (rideable-entity.md).
- Hide parts: `bone.setHidden(true)` hides the bone's cubes and children; follow with
  `bone.setChildrenHidden(false)` to keep children (arms) visible. Do it every frame in `preRender`.
- Render type: default is `entityCutoutNoCull` (both faces). Override `getRenderType` to
  `RenderType.entityCutout(texture)` where back faces must be culled (first-person cockpit).
- Shift the whole model (e.g. eased steps): `poseStack.translate(...)` in `preRender` when `!isReRender`.
- `getDeathMaxRotation` -> 0 to stop the vanilla death tip-over when a death animation exists.
- `withScale`, `shadowRadius` for size; `getBone(name)` is an Optional on `BakedGeoModel`.
- Bone world positions (particles from pipes etc.): `bone.setTrackingMatrices(true)` then
  `bone.getWorldPosition()` after rendering.

- **Glowing parts with per-frame colour** (fire that flickers, a visor that changes colour): a `GeoRenderLayer` whose
  `render` calls `getRenderer().reRender(model, poseStack, buffers, animatable, RenderType.eyes(mask),
  buffers.getBuffer(type), partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb)` once per mask.
  `eyes` is additive and ignores light: black mask pixels add nothing, and scaling the colour dims the glow.
  GeckoLib's `AutoGlowingGeoLayer` does the same with a fixed colour and one `<texture>_glowmask.png`.
  Power Suits: `SuitGlowLayer`, masks generated from reserved texture colours by `tools/make-glowmasks.py`.

## Flicker checklist
1. **Static z-fighting**: two cubes with coplanar, overlapping faces whose outward normals point the same
   way. Flush stacked cubes (opposite normals) are harmless. Scan with the Blockbench skill's
   `scripts/zfight.mjs` (rest pose, and `ANIMS=` for live animated poses); fix with `inflate` 0.1
   (box UVs unchanged; verify with `get_cube_uv`).
2. **Seeing inside cubes** (camera inside/near the model): cull back faces and/or hide the shell bones.
3. **Animation state toggling** every few ticks: log the animation name per tick; add hysteresis.
4. Blockbench previews render double-sided too, so offscreen views from the camera position reproduce
   what the game shows (set cube `visibility` false temporarily, restore, confirm `Project.saved`).
