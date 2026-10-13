package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.Bullet;
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
 * Draws a bullet as a tracer: a thin hot streak trailing behind it along its flight, full-bright and additive (the
 * "eyes" render type), a white-yellow core in an orange sheath. Oriented like vanilla's arrow: after the two rotations
 * below, -X is forward (ProjectileUtil.rotateTowardsMovement stores yaw as atan2(z, x) + 90), so the streak runs along
 * +X behind the bullet. The first tick it's short, so it doesn't stick out of the muzzle backwards.
 */
public class BulletRenderer extends EntityRenderer<Bullet> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/bullet.png");
    private static final float LENGTH = 1.1F;

    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Bullet bullet, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        Vec3 shift = bullet.visualOffset(bullet.tickCount + partialTick); // own shots start at the cockpit viewmodel's muzzle
        poseStack.translate(shift.x, shift.y, shift.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, bullet.yRotO, bullet.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, bullet.xRotO, bullet.getXRot())));
        float length = LENGTH * Mth.clamp((bullet.tickCount + partialTick) / 1.5F, 0.15F, 1F);
        VertexConsumer out = buffers.getBuffer(RenderType.eyes(TEXTURE));
        box(poseStack.last(), out, 0F, -0.045F, -0.045F, length, 0.045F, 0.045F, 0xFFFF7A1E);        // orange sheath
        box(poseStack.last(), out, -0.02F, -0.02F, -0.02F, length * 0.6F, 0.02F, 0.02F, 0xFFFFE8A0); // hot core
        poseStack.popPose();
        super.render(bullet, yaw, partialTick, poseStack, buffers, light);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int colour) {
        quad(pose, out, colour, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quad(pose, out, colour, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(pose, out, colour, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, out, colour, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(pose, out, colour, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(pose, out, colour, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int colour, float nx, float ny, float nz, float... v) {
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            out.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(colour).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Bullet bullet) {
        return TEXTURE;
    }
}
