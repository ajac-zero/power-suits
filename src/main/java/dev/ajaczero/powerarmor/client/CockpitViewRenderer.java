package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.PowerArmor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Draws one half of the cockpit viewmodel (CockpitView): the socket tool or the empty gauntlet. */
final class CockpitViewRenderer extends GeoObjectRenderer<CockpitView> {
    enum Part { TOOL, GAUNTLET }

    static final String[] TOOL_BONES = {"chaingun", "em_projector", "drill", "chainsaw"};

    Part part = Part.TOOL;
    /** Installed tool's bone (PowerTool.suitBone), or null for the bare socket. */
    @Nullable
    String installed;

    CockpitViewRenderer() {
        super(new Model());
        this.addRenderLayer(new HeatGlow(this));
    }

    @Override
    public void preRender(PoseStack poseStack, CockpitView view, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        boolean tool = this.part == Part.TOOL;
        model.getBone("view_tool").ifPresent(b -> { b.setHidden(!tool); b.setChildrenHidden(!tool); });
        model.getBone("view_gauntlet").ifPresent(b -> { b.setHidden(tool); b.setChildrenHidden(tool); });
        for (String name : TOOL_BONES) {
            boolean show = name.equals(this.installed);
            model.getBone(name).ifPresent(b -> { b.setHidden(!show); b.setChildrenHidden(!show); });
        }
        boolean bare = this.installed == null;
        model.getBone("socket").ifPresent(b -> { b.setHidden(!bare); b.setChildrenHidden(!bare); });
        super.preRender(poseStack, view, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }


    private static final class Model extends GeoModel<CockpitView> {
        private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "geo/view/power_armor_view.geo.json");
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor.png");
        private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "animations/entity/power_armor.animation.json");

        /** The chaingun's barrels turn with the suit's spin, like on the suit. */
        @Override
        public void setCustomAnimations(CockpitView view, long instanceId, software.bernie.geckolib.animation.AnimationState<CockpitView> state) {
            if (view.suit != null) {
                ChaingunParts.pose(getAnimationProcessor(), view.suit, state.getPartialTick());
            }
        }

        @Override
        public ResourceLocation getModelResource(CockpitView view) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(CockpitView view) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(CockpitView view) {
            return ANIMATIONS;
        }

        /** Both faces, like GeckoLib's default: in this render path the cubes wind the other way, so culling hid them all. */
        @Override
        public RenderType getRenderType(CockpitView view, ResourceLocation texture) {
            return RenderType.entityCutoutNoCull(texture);
        }
    }

    /** The chaingun's hot barrel tips, glowing like on the suit (SuitGlowLayer). */
    private static final class HeatGlow extends software.bernie.geckolib.renderer.layer.GeoRenderLayer<CockpitView> {
        private final CockpitViewRenderer renderer;

        HeatGlow(CockpitViewRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack poseStack, CockpitView view, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           com.mojang.blaze3d.vertex.VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float glow = view.suit == null || this.renderer.part != Part.TOOL ? 0F : SuitGlowLayer.heatGlow(view.suit.getToolHeat());
            if (glow <= 0.01F) {
                return;
            }
            int c = net.minecraft.util.Mth.clamp((int) (glow * 255F), 0, 255);
            RenderType type = RenderType.eyes(SuitGlowLayer.HEAT);
            getRenderer().reRender(model, poseStack, buffers, view, type, buffers.getBuffer(type), partialTick,
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    0xFF000000 | c << 16 | c << 8 | c);
        }
    }
}
