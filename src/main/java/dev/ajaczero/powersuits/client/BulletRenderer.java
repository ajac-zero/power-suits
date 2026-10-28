package dev.ajaczero.powersuits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.Bullet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Draws a bullet as a tracer: a thin hot streak trailing behind it along its flight, full-bright and additive (the
 * "eyes" render type), a white-yellow core in an orange sheath. Drawn where Bullet.visualShift says (coming out of the
 * gun, joining the real path at the target) and pointing along that drawn path: the streak runs along local +X, turned
 * to point back the way the bullet came; it never reaches back past the muzzle.
 */
public class BulletRenderer extends EntityRenderer<Bullet, BulletRenderer.State> {
    /** This frame's tracer, worked out from the bullet when the frame is extracted. */
    public static class State extends EntityRenderState {
        Vec3 shift = Vec3.ZERO;
        Vec3 direction = new Vec3(1, 0, 0);
        float length;
    }

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PowerSuits.MODID, "textures/entity/bullet.png");
    private static final float LENGTH = 3.0F;

    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Bullet bullet, State state, float partialTick) {
        super.extractRenderState(bullet, state, partialTick);
        Vec3 at = bullet.getPosition(partialTick);
        state.shift = bullet.visualShift(at);
        state.direction = bullet.visualDirection(at);
        state.length = (float) Math.min(LENGTH, bullet.visualTravelled(at));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.length < 0.05F) {
            return;
        }
        poseStack.pushPose();
        Vec3 shift = state.shift;
        poseStack.translate(shift.x, shift.y, shift.z);
        Vec3 dir = state.direction;
        // Turn the streak's +X straight onto the way it came from. (A yaw/pitch pair borrowed from
        // ProjectileUtil.rotateTowardsMovement mirrored the east-west part: only due north/south and straight up
        // lined up, other shots trailed up to 180 degrees off, i.e. sideways.)
        poseStack.rotate(new Quaternionf().rotationTo(1F, 0F, 0F, (float) -dir.x, (float) -dir.y, (float) -dir.z));
        float length = state.length;
        collector.submitCustomGeometry(poseStack, RenderTypes.eyes(TEXTURE), (pose, out) -> {
            box(pose, out, 0F, -0.045F, -0.045F, length, 0.045F, 0.045F, 0xFFFF7A1E);        // orange sheath
            box(pose, out, -0.02F, -0.02F, -0.02F, length * 0.6F, 0.02F, 0.02F, 0xFFFFE8A0); // hot core
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
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
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        }
    }
}
