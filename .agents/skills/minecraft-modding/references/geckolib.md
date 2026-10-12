# Blockbench model -> GeckoLib entity

## Export (Bedrock-format Blockbench project)
GeckoLib 4 reads Bedrock geometry (`format_version 1.12.0`) and Bedrock animation JSON, so use
Blockbench's own exporter through the MCP (`risky_eval`, read-only):
- geometry: `JSON.stringify(Codecs.bedrock.compile({raw: true}))`
- animations: `JSON.stringify(Animator.buildFile())`
- texture: `Texture.all[0].getDataURL()` (base64 PNG)
`../scripts/export_model.mjs` does all three over HTTP MCP and writes
`assets/<modid>/geo/entity/<name>.geo.json`, `animations/entity/<name>.animation.json`,
`textures/entity/<name>.png` (the `DefaultedEntityGeoModel` naming convention), renaming
`animation.<old>.*` to `animation.<name>.*`. Verify a re-export of an unchanged model is byte-identical
in content (JSON compare). Catmull-Rom keys and simple Molang (`math.sin(q.anim_time*…)`) work.
Item icon trick: an offscreen Blockbench view screenshot (`set_camera_angle`, png) has a transparent
background; crop to the bbox, resize to 32x32 LANCZOS, threshold alpha, add a dark 1 px outline.

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

## Flicker checklist
1. **Static z-fighting**: two cubes with coplanar, overlapping faces whose outward normals point the same
   way. Flush stacked cubes (opposite normals) are harmless. Scan with the Blockbench skill's
   `scripts/zfight.mjs` (rest pose, and `ANIMS=` for live animated poses); fix with `inflate` 0.1
   (box UVs unchanged; verify with `get_cube_uv`).
2. **Seeing inside cubes** (camera inside/near the model): cull back faces and/or hide the shell bones.
3. **Animation state toggling** every few ticks: log the animation name per tick; add hysteresis.
4. Blockbench previews render double-sided too, so offscreen views from the camera position reproduce
   what the game shows (set cube `visibility` false temporarily, restore, confirm `Project.saved`).
