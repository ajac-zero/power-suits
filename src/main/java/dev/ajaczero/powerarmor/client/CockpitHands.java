package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.item.SocketAim;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
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
 * Instead: the socket tool low on the left (CockpitView, animated like the suit's tool) and the suit's gauntlet
 * holding the selected item on the right (your main hand), with vanilla's swing / use / eat motions.
 *
 * <p>Positions are in view space (blocks; x right, y up, -z forward), like vanilla's hand transforms.
 */
public final class CockpitHands {
    private static final CockpitViewRenderer RENDERER = new CockpitViewRenderer();
    private static final int RIGHT = 1; // vanilla's arm side factor for the right (main) hand

    // Placement rules (tools/viewmodel-fit.py checks and solves them; the hand pass always uses a 70 degree FOV):
    //  - each part's cut end (the tool's socket face, the gauntlet's wrist) stays below the bottom edge of the screen, so
    //    tool and arm reach in from the corners instead of floating;
    //  - the raised tool's axis points at the crosshair 10 blocks out (`viewmodel-fit.py aim`), so its barrels line up
    //    with where the shots go (the first poses pointed 20-40 degrees up and the tracers looked off);
    //  - the corners: the hotbar covers the middle of the bottom edge.
    // Not final: the dev view tuner (autopilot "view" mode) reloads them from run/view/tuning.json.
    // Socket tool at rest (lowered: its axis points just below the crosshair, 16 blocks out) and raised (aiming).
    static Vector3f toolPos = new Vector3f(-0.58F, -0.57F, -0.19F);
    static float toolYaw = -2.1F, toolPitch = 88.4F, toolRoll = 15F, toolScale = 1.0F;
    static Vector3f toolRaisedPos = new Vector3f(-0.55F, -0.54F, -0.19F);
    static float toolRaisedYaw = -3.2F, toolRaisedPitch = 93.1F;
    // Gauntlet: where the elbow sits (the forearm hangs -Y from it in the model), how it's turned, size. It points forward,
    // a little up (at a spot 8 blocks out, 2 up), so the held item stays in view.
    static Vector3f gauntletPos = new Vector3f(0.55F, -0.54F, -0.20F);
    static float gauntletYaw = 4.0F, gauntletPitch = 108.0F, gauntletRoll = -10F, gauntletScale = 0.7F;
    // Eating / drinking: the elbow moves here and the forearm turns in and up, so the food sits low in the middle.
    private static final Vector3f EAT_POS = new Vector3f(0.26F, -0.53F, -0.26F);
    private static final float EAT_YAW = 30F, EAT_PITCH = 148F;
    /** The chaingun's muzzle (barrel tips) in the view model (pixels below the socket; the muzzle bone's pivot). */
    private static final float MUZZLE_PX = -15F;
    private static final float RECOIL_TICKS = 6F;

    private static boolean wasShooting;
    private static double shotAt = -100;

    static void render(RenderHandEvent event, LocalPlayer player, PowerArmorEntity suit) {
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();
        CockpitView view = CockpitView.INSTANCE;
        view.suit = suit;
        pollShot(suit, view);
        RENDERER.installed = suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.suitBone() : null;

        pose.pushPose();
        toolTransform(pose, suit, partial, suit.getToolRaise(partial));
        drawPart(pose, event, CockpitViewRenderer.Part.TOOL, ItemStack.EMPTY);
        pose.popPose();

        pose.pushPose();
        ItemStack held = player.getMainHandItem();
        gauntletTransform(pose, player, held, event.getSwingProgress(), event.getEquipProgress(), partial, suit);
        drawPart(pose, event, CockpitViewRenderer.Part.GAUNTLET, held);
        pose.popPose();
    }

    private static void drawPart(PoseStack pose, RenderHandEvent event, CockpitViewRenderer.Part part, ItemStack held) {
        RENDERER.part = part;
        RENDERER.held = held;
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

    /** Gauntlet: vanilla's first-person swing (ItemInHandRenderer's right hand), and an eat/drink motion
     *  that brings the gauntlet to the bottom middle of the view (vanilla's eat transform assumes its own hand offsets
     *  and swung the gauntlet out of view). */
    private static void gauntletTransform(PoseStack pose, Player player, ItemStack held, float swing, float equip, float partial,
                                          PowerArmorEntity suit) {
        boolean eating = player.isUsingItem() && player.getUseItemRemainingTicks() > 0
                && (held.getUseAnimation() == UseAnim.EAT || held.getUseAnimation() == UseAnim.DRINK);
        float bob = bob(suit);
        float toMouth = 0F, chew = 0F;
        if (eating) {
            float left = player.getUseItemRemainingTicks() - partial + 1.0F;
            float done = left / held.getUseDuration(player);
            toMouth = Mth.clamp((1.0F - done) * 6F, 0F, 1F);                           // raise it over the first ~1/6
            chew = done < 0.8F ? Mth.abs(Mth.cos(left / 4.0F * Mth.PI)) * 0.06F : 0F;   // vanilla's chewing bob
        } else {
            float sq = Mth.sqrt(swing);
            pose.translate(RIGHT * -0.4F * Mth.sin(sq * Mth.PI), 0.2F * Mth.sin(sq * Mth.TWO_PI), -0.2F * Mth.sin(swing * Mth.PI));
        }
        pose.translate(Mth.lerp(toMouth, gauntletPos.x, EAT_POS.x), Mth.lerp(toMouth, gauntletPos.y, EAT_POS.y) + bob + chew - equip * 0.6F,
                Mth.lerp(toMouth, gauntletPos.z, EAT_POS.z));
        if (!eating) {
            float f = Mth.sin(swing * swing * Mth.PI);
            float f1 = Mth.sin(Mth.sqrt(swing) * Mth.PI);
            pose.mulPose(Axis.YP.rotationDegrees(RIGHT * (45.0F + f * -20.0F)));
            pose.mulPose(Axis.ZP.rotationDegrees(RIGHT * f1 * -20.0F));
            pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
            pose.mulPose(Axis.YP.rotationDegrees(RIGHT * -45.0F));
        }
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(toMouth, gauntletYaw, EAT_YAW)));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(toMouth, gauntletPitch, EAT_PITCH)));
        pose.mulPose(Axis.YP.rotationDegrees(gauntletRoll));
        pose.scale(gauntletScale, gauntletScale, gauntletScale);
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
            if (j.has("gauntletPos")) gauntletPos = v.apply("gauntletPos");
            if (j.has("toolRaisedPos")) toolRaisedPos = v.apply("toolRaisedPos");
            toolRaisedYaw = f.apply("toolRaisedYaw", toolRaisedYaw); toolRaisedPitch = f.apply("toolRaisedPitch", toolRaisedPitch);
            toolYaw = f.apply("toolYaw", toolYaw); toolPitch = f.apply("toolPitch", toolPitch);
            toolRoll = f.apply("toolRoll", toolRoll); toolScale = f.apply("toolScale", toolScale);
            gauntletYaw = f.apply("gauntletYaw", gauntletYaw); gauntletPitch = f.apply("gauntletPitch", gauntletPitch);
            gauntletRoll = f.apply("gauntletRoll", gauntletRoll); gauntletScale = f.apply("gauntletScale", gauntletScale);
        } catch (Exception e) {
            dev.ajaczero.powerarmor.PowerArmor.LOGGER.warn("[view] can't read {}: {}", file, e.toString());
        }
    }

    private CockpitHands() {}
}
