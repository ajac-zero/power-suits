package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PlasmaBolt;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the bolt as a glowing ball: one square that always faces the camera (a "billboard"), lit at full
 * brightness, like the ender dragon's fireball. The trail is particles spawned by PlasmaBolt itself.
 */
public class PlasmaBoltRenderer extends EntityRenderer<PlasmaBolt> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/plasma_bolt.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    public PlasmaBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(PlasmaBolt bolt, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(PlasmaBolt bolt, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        Vec3 shift = bolt.visualOffset(bolt.tickCount + partialTick); // own shots start at the cockpit viewmodel's muzzle
        poseStack.translate(shift.x, shift.y, shift.z);
        float pulse = 0.9F + 0.15F * (float) Math.sin((bolt.tickCount + partialTick) * 1.7F);
        poseStack.scale(0.8F * pulse, 0.8F * pulse, 0.8F * pulse);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        vertex(consumer, pose, 0F, 0F, 0, 1);
        vertex(consumer, pose, 1F, 0F, 1, 1);
        vertex(consumer, pose, 1F, 1F, 1, 0);
        vertex(consumer, pose, 0F, 1F, 0, 0);
        poseStack.popPose();
        super.render(bolt, yaw, partialTick, poseStack, buffers, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(PlasmaBolt bolt) {
        return TEXTURE;
    }
}
