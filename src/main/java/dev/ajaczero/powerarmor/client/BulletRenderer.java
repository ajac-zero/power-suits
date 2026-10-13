package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.Bullet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Draws a bullet as a tracer: a thin hot streak trailing behind it along its flight, full-bright and additive (the
 * "eyes" render type), a white-yellow core in an orange sheath. Drawn where Bullet.visualShift says (coming out of the
 * gun, joining the real path at the target) and pointing along that drawn path: the streak runs along local +X, turned
 * to point back the way the bullet came; it never reaches back past the muzzle.
 */
public class BulletRenderer extends EntityRenderer<Bullet> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/bullet.png");
    private static final float LENGTH = 3.0F;

    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Bullet bullet, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        Vec3 at = bullet.getPosition(partialTick);
        Vec3 shift = bullet.visualShift(at);
        poseStack.translate(shift.x, shift.y, shift.z);
        Vec3 dir = bullet.visualDirection(at);
        // Turn the streak's +X straight onto the way it came from. (A yaw/pitch pair borrowed from
        // ProjectileUtil.rotateTowardsMovement mirrored the east-west part: only due north/south and straight up
        // lined up, other shots trailed up to 180 degrees off, i.e. sideways.)
        poseStack.mulPose(new Quaternionf().rotationTo(1F, 0F, 0F, (float) -dir.x, (float) -dir.y, (float) -dir.z));
        float length = (float) Math.min(LENGTH, bullet.visualTravelled(at));
        if (length < 0.05F) {
            poseStack.popPose();
            return;
        }
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
