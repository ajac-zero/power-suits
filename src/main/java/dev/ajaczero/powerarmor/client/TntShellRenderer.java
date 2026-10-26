package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ajaczero.powerarmor.entity.TntShell;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the TNT launcher's round as a half-size TNT block tumbling end over end, flashing white like primed TNT (the
 * same block model and flash as vanilla's TntRenderer), where TntShell.visualShift says: out of the launcher's muzzle.
 */
public class TntShellRenderer extends EntityRenderer<TntShell, TntShellRenderer.State> {
    /** This frame's round, copied from the entity when the frame is extracted (GeckoLib-style snapshots are vanilla's too
     *  since 1.21.2: the drawing happens later, from this state only). */
    public static class State extends EntityRenderState {
        final BlockModelRenderState block = new BlockModelRenderState();
        Vec3 shift = Vec3.ZERO;
        float age;
        float yaw;
    }

    private static final float SCALE = 0.5F;
    /** Degrees it tumbles per tick. */
    private static final float TUMBLE = 24F;
    private final BlockModelResolver blocks;

    public TntShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
        this.blocks = context.getBlockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TntShell shell, State state, float partialTick) {
        super.extractRenderState(shell, state, partialTick);
        state.shift = shell.visualShift(shell.getPosition(partialTick));
        state.age = shell.tickCount + partialTick;
        Vec3 v = shell.getDeltaMovement();
        state.yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z)); // its heading: it tumbles forward along it
        this.blocks.update(state.block, Blocks.TNT.defaultBlockState(), TntRenderer.BLOCK_DISPLAY_CONTEXT);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(state.shift.x, state.shift.y, state.shift.z);
        poseStack.rotateDegrees(Axis.YP, state.yaw);
        poseStack.rotateDegrees(Axis.XP, state.age * TUMBLE);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, -0.5F, -0.5F); // the block model's corner to its centre
        // flashes white every 4 ticks, like a lit TNT block (TntRenderer.isLit on a fuse counting down)
        boolean white = ((int) (state.age / 4F)) % 2 == 0;
        TntMinecartRenderer.submitWhiteSolidBlock(state.block, poseStack, collector, state.lightCoords, white, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
