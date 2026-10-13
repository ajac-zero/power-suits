package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * The suit's lights: the firebox fire and the visor, drawn on top of the model at full brightness so they glow
 * in the dark. Like GeckoLib's AutoGlowingGeoLayer, the whole model is drawn again with a glowmask texture (black
 * everywhere except the glowing pixels, made by tools/make-glowmasks.py) in vanilla's "eyes" render type (the one
 * spider and enderman eyes use): it ignores light and adds its colour on top. Unlike AutoGlowingGeoLayer, each mask
 * is tinted every frame, so the fire flickers and fades and the visor changes colour (PowerArmorEntity.tickLights).
 */
public class SuitGlowLayer extends GeoRenderLayer<PowerArmorEntity> {
    private static final ResourceLocation FIRE = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_fire.png");
    private static final ResourceLocation VISOR = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_visor.png");

    public SuitGlowLayer(GeoRenderer<PowerArmorEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, PowerArmorEntity suit, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (PowerArmorRenderer.isCockpitView(suit)) {
            return; // the upper body isn't drawn from inside
        }
        float time = (float) RenderUtil.getCurrentTick() + partialTick;
        // Coals: a slow uneven flicker (two sines that never line up), deeper when the engine works hard.
        float fire = suit.getFireGlow() * (0.86F + 0.08F * Mth.sin(time * 0.37F + suit.getId()) + 0.06F * Mth.sin(time * 1.31F));
        draw(poseStack, suit, model, buffers, partialTick, FIRE, fire, 1F, 1F, 1F);
        float[] visor = suit.getVisorColor();
        float on = suit.getVisorFlicker(time);
        draw(poseStack, suit, model, buffers, partialTick, VISOR, on, visor[0], visor[1], visor[2]);
    }

    private void draw(PoseStack poseStack, PowerArmorEntity suit, BakedGeoModel model, MultiBufferSource buffers, float partialTick,
                      ResourceLocation mask, float brightness, float r, float g, float b) {
        if (brightness <= 0.01F) {
            return;
        }
        // Additive: scaling the colour down dims the glow (black adds nothing).
        int colour = 0xFF000000 | channel(r * brightness) << 16 | channel(g * brightness) << 8 | channel(b * brightness);
        RenderType type = RenderType.eyes(mask);
        getRenderer().reRender(model, poseStack, buffers, suit, type, buffers.getBuffer(type), partialTick,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
    }

    private static int channel(float v) {
        return Mth.clamp((int) (v * 255F), 0, 255);
    }
}
