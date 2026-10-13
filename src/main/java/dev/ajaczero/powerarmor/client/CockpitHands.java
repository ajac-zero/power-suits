package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.item.SocketAim;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.RenderUtil;

/**
 * What the pilot sees of the suit's arms, drawn in the hand pass like vanilla's first-person hands. The suit's own
 * upper body is hidden in the cockpit (only the legs are drawn), because looking at its arms from inside never lined
 * up with the view: the arm raised a hand where your hand isn't, the tool hid behind its own base, pads clipped.
 * Instead: the socket tool low on the left (CockpitView, animated like the suit's tool), and on the right your main
 * hand exactly as vanilla draws it, except that the suit's gauntlet replaces the player's arm: a held item is vanilla's
 * own first-person item (same place, swing, eat / bow / shield / crossbow motions; no arm, as in vanilla), and an
 * empty hand is the gauntlet in vanilla's empty-arm pose (PilotView decides which).
 *
 * <p>Positions are in view space (blocks; x right, y up, -z forward), like vanilla's hand transforms.
 */
public final class CockpitHands {
    private static final CockpitViewRenderer RENDERER = new CockpitViewRenderer();

    // Placement rules (tools/viewmodel-fit.py checks and solves them; the hand pass always uses a 70 degree FOV):
    //  - the tool's cut end (its socket face) stays below the bottom edge of the screen, so it reaches in from the corner
    //    instead of floating;
    //  - the raised tool's axis points at the crosshair 10 blocks out (`viewmodel-fit.py aim`), so its barrels line up
    //    with where the shots go (the first poses pointed 20-40 degrees up and the tracers looked off);
    //  - the corners: the hotbar covers the middle of the bottom edge.
    // Not final: the dev view tuner (autopilot "view" mode) reloads them from run/view/tuning.json.
    // Socket tool at rest (lowered: its axis points just below the crosshair, 16 blocks out) and raised (aiming).
    static Vector3f toolPos = new Vector3f(-0.58F, -0.57F, -0.19F);
    static float toolYaw = -2.1F, toolPitch = 88.4F, toolRoll = 15F, toolScale = 1.0F;
    static Vector3f toolRaisedPos = new Vector3f(-0.55F, -0.54F, -0.19F);
    static float toolRaisedYaw = -3.2F, toolRaisedPitch = 93.1F;
    // Gauntlet: stands in for the player's arm, so it only shows when vanilla would show the arm (an empty hand; with an item
    // vanilla draws just the item, and so do we). It's laid along the player's right arm in vanilla's own empty-hand pose
    // (ItemInHandRenderer.renderPlayerArm): its fingertips where the player's fist is, a little bulkier than the arm.
    static float gauntletScale = 0.7F;
    /** The chaingun's muzzle (barrel tips) in the view model (pixels below the socket; the muzzle bone's pivot). */
    private static final float MUZZLE_PX = -15F;
    private static final float RECOIL_TICKS = 6F;

    private static boolean wasShooting;
    private static double shotAt = -100;

    /** The socket tool, low on the left (the off hand's place). */
    static void renderTool(RenderHandEvent event, PowerArmorEntity suit) {
        PoseStack pose = event.getPoseStack();
        CockpitView view = CockpitView.INSTANCE;
        view.suit = suit;
        pollShot(suit, view);
        RENDERER.installed = suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.suitBone() : null;
        pose.pushPose();
        toolTransform(pose, suit, event.getPartialTick(), suit.getToolRaise(event.getPartialTick()));
        drawPart(pose, event, CockpitViewRenderer.Part.TOOL);
        pose.popPose();
    }

    /** The gauntlet where vanilla draws the empty right hand, with vanilla's swing and equip motion. */
    static void renderGauntlet(RenderHandEvent event, PowerArmorEntity suit) {
        PoseStack pose = event.getPoseStack();
        CockpitView.INSTANCE.suit = suit;
        pose.pushPose();
        gauntletTransform(pose, event.getSwingProgress(), event.getEquipProgress());
        drawPart(pose, event, CockpitViewRenderer.Part.GAUNTLET);
        pose.popPose();
    }

    private static void drawPart(PoseStack pose, RenderHandEvent event, CockpitViewRenderer.Part part) {
        RENDERER.part = part;
        pose.translate(-0.5F, -0.51F, -0.5F); // GeoObjectRenderer centres objects in a block; undo that
        RENDERER.render(pose, CockpitView.INSTANCE, event.getMultiBufferSource(), null, null, event.getPackedLight(), event.getPartialTick());
    }

    /** Tool: low on the left, raised toward the middle while used, bobbing with the gait, kicking back and up when
     *  it fires. */
    private static void toolTransform(PoseStack pose, PowerArmorEntity suit, float partial, float raise) {
        float kick = Math.max(recoil(partial), suit.getRecoil(partial)); // EM push / chaingun shots
        Vector3f p = new Vector3f(toolPos).lerp(toolRaisedPos, raise);
        pose.translate(p.x, p.y + bob(suit), p.z + kick * 0.10F);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(raise, toolYaw, toolRaisedYaw)));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(raise, toolPitch, toolRaisedPitch) + kick * 8F));
        pose.mulPose(Axis.YP.rotationDegrees(toolRoll));
        pose.scale(toolScale, toolScale, toolScale);
    }

    /** Same as toolTransform, as a matrix (no kick), to find the muzzle. */
    private static Matrix4f toolMatrix(PowerArmorEntity suit, float raise) {
        Vector3f p = new Vector3f(toolPos).lerp(toolRaisedPos, raise);
        return new Matrix4f()
                .translate(p.x, p.y + bob(suit), p.z)
                .rotateY(Mth.lerp(raise, toolYaw, toolRaisedYaw) * Mth.DEG_TO_RAD)
                .rotateX(Mth.lerp(raise, toolPitch, toolRaisedPitch) * Mth.DEG_TO_RAD)
                .rotateY(toolRoll * Mth.DEG_TO_RAD)
                .scale(toolScale);
    }

    /** Gait bob (the suit's hip drop, model pixels) plus a slow breathing drift. */
    private static float bob(PowerArmorEntity suit) {
        return suit.getHipBob() * 0.008F + Mth.sin((float) RenderUtil.getCurrentTick() * 0.07F) * 0.004F;
    }

    /** Vanilla's right-arm transform (ItemInHandRenderer.renderPlayerArm + PlayerModel's right arm), then the gauntlet
     *  laid along that arm. */
    private static void gauntletTransform(PoseStack pose, float swing, float equip) {
        float sq = Mth.sqrt(swing);
        pose.translate(-0.3F * Mth.sin(sq * Mth.PI) + 0.64F, 0.4F * Mth.sin(sq * Mth.TWO_PI) - 0.6F - equip * 0.6F,
                -0.4F * Mth.sin(swing * Mth.PI) - 0.72F);
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(sq * Mth.PI) * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(swing * swing * Mth.PI) * -20.0F));
        pose.translate(-1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-135.0F));
        pose.translate(5.6F, 0.0F, 0.0F);
        // PlayerModel's right arm (wide): pivot (-5, 2, 0) px, idle sway zRot 0.1 rad (AnimationUtils.bobModelPart, age 0).
        // Its box runs x -3..1, y -2..10 (model Y runs down the arm, toward the fist at y 10).
        pose.translate(-5F / 16F, 2F / 16F, 0F);
        pose.mulPose(Axis.ZP.rotation(0.1F));
        // Into the gauntlet's space: entity models are turned 180 degrees about Z into the world (Y up), GeckoLib models
        // aren't, so the gauntlet (hanging -Y, fingertips at y -17, centred on x 0.5) turns 180 about Z; then its
        // fingertips go where the player's fist ends (-1, 10, 0).
        float k = gauntletScale;
        pose.translate((-1F + 0.5F * k) / 16F, (10F - 17F * k) / 16F, 0F);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(k, k, k);
    }

    /** The suit plays tool_recoil when the EM projector pushes; start the kick here too. (Chaingun shots kick through
     *  the suit's synced shot counter, PowerArmorEntity.getRecoil.) */
    private static void pollShot(PowerArmorEntity suit, CockpitView view) {
        AnimationController<?> action = suit.getAnimatableInstanceCache().getManagerForId(suit.getId()).getAnimationControllers().get("action");
        RawAnimation triggered = action == null ? null : action.getTriggeredAnimation();
        boolean shooting = action != null && action.isPlayingTriggeredAnimation() && triggered != null
                && triggered.getAnimationStages().get(0).animationName().endsWith("tool_recoil");
        if (shooting && !wasShooting) {
            shotAt = RenderUtil.getCurrentTick();
        }
        wasShooting = shooting;
    }

    /** 1 right after a shot, easing back to 0. */
    private static float recoil(float partial) {
        double age = RenderUtil.getCurrentTick() - shotAt;
        if (age < 0 || age > RECOIL_TICKS) {
            return 0F;
        }
        float t = (float) (age / RECOIL_TICKS);
        return t < 0.15F ? t / 0.15F : (1 - t) / 0.85F * (1 - t);
    }

    /**
     * Where the tool's muzzle appears in the world for the local pilot in first person: bolts and the EM beam start
     * there on their screen. The hand pass uses a fixed 70 degree FOV, so the view-space point is rescaled to the
     * world FOV to land on the same spot of the screen.
     */
    static Vec3 visualMuzzle(PowerArmorEntity suit, Player pilot) {
        Minecraft mc = Minecraft.getInstance();
        if (pilot != mc.player || !mc.options.getCameraType().isFirstPerson()) {
            return SocketAim.muzzlePosition(suit, pilot);
        }
        Vector3f p = toolMatrix(suit, suit.getToolRaise(1F)).transformPosition(new Vector3f(0, MUZZLE_PX / 16F, 0));
        double k = Math.tan(Math.toRadians(mc.options.fov().get()) / 2) / Math.tan(Math.toRadians(70) / 2);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f look = camera.getLookVector(), up = camera.getUpVector(), left = camera.getLeftVector();
        double x = p.x * k, y = p.y * k, z = p.z;
        return camera.getPosition()
                .add(-left.x * x, -left.y * x, -left.z * x)
                .add(up.x * y, up.y * y, up.z * y)
                .add(-look.x * z, -look.y * z, -look.z * z);
    }

    /** Dev: reload the placement from a JSON file (keys as the fields above; positions as [x, y, z]). */
    public static void loadTuning(java.nio.file.Path file) {
        try (var reader = java.nio.file.Files.newBufferedReader(file)) {
            com.google.gson.JsonObject j = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            java.util.function.BiFunction<String, Float, Float> f = (k, d) -> j.has(k) ? j.get(k).getAsFloat() : d;
            java.util.function.Function<String, Vector3f> v = k -> {
                var a = j.getAsJsonArray(k);
                return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
            };
            if (j.has("toolPos")) toolPos = v.apply("toolPos");
            if (j.has("toolRaisedPos")) toolRaisedPos = v.apply("toolRaisedPos");
            toolRaisedYaw = f.apply("toolRaisedYaw", toolRaisedYaw); toolRaisedPitch = f.apply("toolRaisedPitch", toolRaisedPitch);
            toolYaw = f.apply("toolYaw", toolYaw); toolPitch = f.apply("toolPitch", toolPitch);
            toolRoll = f.apply("toolRoll", toolRoll); toolScale = f.apply("toolScale", toolScale);
            gauntletScale = f.apply("gauntletScale", gauntletScale);
        } catch (Exception e) {
            dev.ajaczero.powerarmor.PowerArmor.LOGGER.warn("[view] can't read {}: {}", file, e.toString());
        }
    }

    private CockpitHands() {}
}
