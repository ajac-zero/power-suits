package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the code-driven pose (2026-10-20, ./gradlew runAutopilotPose, ~30 s): the torso lean with the view
 * pitch, the tool raise and the torso twist with the view yaw, each from a fixed camera, so a sign mistake is obvious.
 * Looking down must lean the suit forward (it leaned back before: GeckoLib 5 bone rotations have Blockbench's sign).
 * Side shots ap_pose_side_*.png (the suit faces +Z = the left of the picture); top shots ap_pose_top_*.png (the suit's
 * legs face the top of the picture; a gold block lies along the view at yaw +40, a redstone block at yaw -40: the
 * torso and the raised tool must face the block of the shot). Log run/autopilot_pose.csv (client view and leg yaw).
 */
final class PoseScene {
    private static BufferedWriter csv;
    private static Vec3 home;

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        switch (s) {
            case 0 -> setup(mc);
            case 6 -> side(mc);
            case 30 -> shoot(mc, "side_level");
            case 32 -> AutoPilot.setView(0, 40);
            case 50 -> shoot(mc, "side_down40");
            case 52 -> AutoPilot.setView(0, -30);
            case 70 -> shoot(mc, "side_up30");
            case 72 -> { AutoPilot.setView(0, 0); AutoPilot.hold(o.keyUse, true); } // the tool arm raises to aim
            case 92 -> shoot(mc, "side_raised");
            case 94 -> AutoPilot.setView(0, 30);
            case 110 -> shoot(mc, "side_raised_down30");
            case 112 -> { AutoPilot.hold(o.keyUse, false); AutoPilot.setView(40, 0); top(mc); }
            case 140 -> shoot(mc, "top_yaw40");
            case 142 -> AutoPilot.hold(o.keyUse, true);
            case 160 -> shoot(mc, "top_yaw40_raised");
            case 162 -> { AutoPilot.hold(o.keyUse, false); AutoPilot.setView(-40, 0); }
            case 190 -> shoot(mc, "top_yawm40");
            case 192 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); AutoPilot.setView(0, 15); }
            case 215 -> shoot(mc, "tp_down15");
            case 220 -> { close(); return false; }
            default -> {}
        }
        return true;
    }

    /** Empty hand (right click goes to the tool), a marker block along each test yaw, the log. */
    private static void setup(Minecraft mc) {
        AutoPilot.setView(0, 0);
        home = mc.player.getVehicle() != null ? mc.player.getVehicle().position() : mc.player.position();
        AutoPilot.onServer(mc, p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var level = p.level();
            for (int yaw : new int[] {40, -40}) {
                double r = yaw * Mth.DEG_TO_RAD;
                BlockPos at = BlockPos.containing(home.x - Mth.sin((float) r) * 5, home.y, home.z + Mth.cos((float) r) * 5);
                level.setBlockAndUpdate(at, yaw > 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        });
        try {
            csv = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_pose.csv"));
            csv.write("shot,viewYaw,viewPitch,suitYaw,suitPitch,bodyYaw,twist,toolRaise\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("pose csv", e);
        }
    }

    /** From the suit's left side (+X), level, so forward/back lean shows as a tilt left/right in the picture. */
    private static void side(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 eyeDrop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, home.add(6.5, 1.5, 0).subtract(eyeDrop), home.add(0, 1.5, 0).subtract(eyeDrop));
    }

    /** Straight down from above (camera yaw 0: the top of the picture is +Z, where the legs face). */
    private static void top(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        Vec3 eyeDrop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, home.add(0, 9, 0).subtract(eyeDrop), home.add(0, 0, 0.001).subtract(eyeDrop));
    }

    private static void shoot(Minecraft mc, String name) {
        AutoPilot.shot(mc, "pose_" + name);
        if (csv != null && mc.player.getVehicle() instanceof PowerArmorEntity suit) {
            try {
                csv.write(String.format(Locale.ROOT, "%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f%n", name, mc.player.getYRot(), mc.player.getXRot(),
                        suit.getYRot(), suit.getXRot(), suit.yBodyRot, Mth.wrapDegrees(suit.getYRot() - suit.yBodyRot), suit.getToolRaise(1F)));
            } catch (IOException ignored) {
                // closed
            }
        }
    }

    private static void close() {
        try {
            if (csv != null) {
                csv.close();
            }
        } catch (IOException ignored) {
            // nothing to do
        }
        csv = null;
    }

    private PoseScene() {}
}
