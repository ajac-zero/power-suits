package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.Rivet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a rivet as two little boxes, a shank and a head, pointing along its flight like vanilla's arrow (after the two
 * rotations below, -X is forward: ProjectileUtil.rotateTowardsMovement stores yaw as atan2(z, x) + 90, not vanilla
 * arrows' atan2(x, z); the shank goes into the block it sticks in, the head stays out). Steel, lit by the
 * world; plus the same boxes again in the additive "eyes" render type as its red-hot glow, which fades over 3 s once
 * it has stuck (hot metal cooling down).
 */
public class RivetRenderer extends EntityRenderer<Rivet> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/rivet.png");
    private static final int COOL_TICKS = 60;

    public RivetRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Rivet rivet, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        Vec3 shift = rivet.visualOffset(rivet.tickCount + partialTick); // own shots start at the cockpit viewmodel's muzzle
        poseStack.translate(shift.x, shift.y, shift.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, rivet.yRotO, rivet.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, rivet.xRotO, rivet.getXRot())));
        float heat = rivet.isStuck() ? Mth.clamp(1F - (rivet.getStuckTicks() + partialTick) / COOL_TICKS, 0F, 1F) : 1F;
        // Steel, a little darker while it glows so the glow reads as hot metal.
        int steel = grey(0.62F - 0.2F * heat);
        drawRivet(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), steel, light);
        if (heat > 0.01F) {
            int glow = 0xFF000000 | channel(1F * heat) << 16 | channel(0.42F * heat * heat) << 8 | channel(0.08F * heat * heat);
            poseStack.scale(1.04F, 1.04F, 1.04F);
            drawRivet(poseStack, buffers.getBuffer(RenderType.eyes(TEXTURE)), glow, LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
        super.render(rivet, yaw, partialTick, poseStack, buffers, light);
    }

    /** Shank 0.22 long forward (-X: into the block), head 0.05 behind it (out of the block). */
    private static void drawRivet(PoseStack poseStack, VertexConsumer out, int colour, int light) {
        box(poseStack.last(), out, -0.22F, -0.035F, -0.035F, 0F, 0.035F, 0.035F, colour, light);
        box(poseStack.last(), out, 0F, -0.075F, -0.075F, 0.05F, 0.075F, 0.075F, colour, light);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int colour, int light) {
        quad(pose, out, colour, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quad(pose, out, colour, light, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(pose, out, colour, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, out, colour, light, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(pose, out, colour, light, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(pose, out, colour, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int colour, int light, float nx, float ny, float nz, float... v) {
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            out.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(colour).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }

    private static int grey(float v) {
        int c = channel(v);
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    private static int channel(float v) {
        return Mth.clamp((int) (v * 255F), 0, 255);
    }

    @Override
    public ResourceLocation getTextureLocation(Rivet rivet) {
        return TEXTURE;
    }
}
