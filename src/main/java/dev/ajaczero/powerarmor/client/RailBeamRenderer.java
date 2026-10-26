package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.RailBeamEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;

/**
 * Draws the rail cannon's beams (RailBeamEffects.BEAMS) as bars of light: a white core (full-bright and additive, the
 * "eyes" render type like the chaingun's tracers) inside a see-through blue sheath (full-bright translucent: added light
 * alone hardly showed against a day sky, runAutopilotRail), fading out over a few ticks and thinning as they fade. Beams aren't entities, so they go through NeoForge's custom geometry hooks: when the frame is
 * extracted the beams still alive are copied (with their brightness and the camera position) into the level's render
 * state, and drawn from there when custom geometry is submitted.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class RailBeamRenderer {
    private record Bar(Vec3 from, Vec3 to, float fade) {}

    private static final ContextKey<List<Bar>> BARS = new ContextKey<>(Identifier.fromNamespaceAndPath(PowerArmor.MODID, "rail_beams"));
    private static final ContextKey<Vec3> CAMERA = new ContextKey<>(Identifier.fromNamespaceAndPath(PowerArmor.MODID, "rail_beam_camera"));
    /** A plain white texture (the tracers'): the colour comes from the vertices. */
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/bullet.png");

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        if (RailBeamEffects.BEAMS.isEmpty()) {
            return;
        }
        float now = event.getLevel().getGameTime() + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Bar> bars = new ArrayList<>();
        // A beam from the future (fired at a later game time than now) belongs to another world: a world joined after
        // leaving one has its own clock. Without the second test it was drawn at full brightness until the clock caught up.
        RailBeamEffects.BEAMS.removeIf(b -> now - b.firedAt() > RailBeamEffects.BEAM_TICKS || now < b.firedAt() - 1);
        for (RailBeamEffects.Beam b : RailBeamEffects.BEAMS) {
            float t = Mth.clamp((now - b.firedAt()) / RailBeamEffects.BEAM_TICKS, 0F, 1F);
            bars.add(new Bar(b.from(), b.to(), 1F - t * t)); // bright for most of its life, then gone
        }
        event.getRenderState().setRenderData(BARS, bars);
        event.getRenderState().setRenderData(CAMERA, event.getCamera().position());
    }

    /** Leaving a world: its beams go with it. */
    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RailBeamEffects.BEAMS.clear();
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        List<Bar> bars = event.getLevelRenderState().getRenderData(BARS);
        Vec3 camera = event.getLevelRenderState().getRenderData(CAMERA);
        if (bars == null || camera == null || Minecraft.getInstance().level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        for (Bar bar : bars) {
            Vec3 line = bar.to().subtract(bar.from());
            float length = (float) line.length();
            if (length < 0.1F || bar.fade() < 0.02F) {
                continue;
            }
            Vec3 dir = line.scale(1 / length);
            poseStack.pushPose();
            poseStack.translate(bar.from().x - camera.x, bar.from().y - camera.y, bar.from().z - camera.z);
            poseStack.rotate(new Quaternionf().rotationTo(1F, 0F, 0F, (float) dir.x, (float) dir.y, (float) dir.z));
            float f = bar.fade();
            float sheath = 0.04F + 0.05F * f, core = 0.015F + 0.02F * f;
            // The sheath is see-through blue (it has to show against a bright sky, where added light barely does); the
            // core inside it adds white light, so it glows at night and burns white in the middle by day.
            int sheathColour = (int) (200 * f) << 24 | 0x2F9CFF, coreColour = colour(0xE6FBFF, f);
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE), (pose, out) ->
                    box(pose, out, 0F, -sheath, -sheath, length, sheath, sheath, sheathColour));
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.eyes(TEXTURE), (pose, out) ->
                    box(pose, out, 0F, -core, -core, length, core, core, coreColour));
            poseStack.popPose();
        }
    }

    /** The colour scaled by the fade (additive: darker = dimmer). */
    private static int colour(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void box(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int colour) {
        quad(pose, out, colour, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, out, colour, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(pose, out, colour, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(pose, out, colour, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, out, colour, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int colour, float nx, float ny, float nz, float... v) {
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            out.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(colour).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        }
    }

    private RailBeamRenderer() {}
}
