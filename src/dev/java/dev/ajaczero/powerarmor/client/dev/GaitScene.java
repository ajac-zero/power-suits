package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Autopilot scene for foot slip and leg turning (2026-10-20, ./gradlew runAutopilotGait, ~45 s): the pilot walks
 * forward, backs up, strafes, walks diagonally, turns on the spot, looks around while walking, sprints and turns while
 * sprinting, taps the sprint key once and holds W (vanilla keeps a sprint going while W is held), on flat ground. Every client tick goes to run/autopilot_gait.csv: what the legs play (animation, rate),
 * where they face, where the pilot looks and how the suit really moves. The animations were authored for a ground
 * speed along the legs (walk 2.218, run 6.249 blocks/s x rate), so "slip" is how far the real velocity is from that:
 * a planted foot slides at that speed. Summarize with tools/gait-report.py. Shots ap_gait_*.png (third person).
 */
final class GaitScene {
    private static BufferedWriter csv;
    private static String phase = "-";
    private static float sweepBase;

    // phase name, first tick; each phase holds its keys until the next one starts
    private static final Object[][] PHASES = {
            {"stand", 0}, {"walk_fwd", 20}, {"back", 80}, {"strafe_r", 140}, {"strafe_l", 200}, {"diag", 260},
            {"settle", 320}, {"turn_spot", 340}, {"settle2", 420}, {"walk_look", 440}, {"sprint", 540},
            {"sprint_turn", 600}, {"sprint_tap", 660}, {"stop", 760}, {"end", 800}};

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        if (s == 0) {
            open();
            AutoPilot.setView(0, 15);
            mc.setCameraEntity(mc.player);
            o.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        for (Object[] p : PHASES) {
            if ((int) p[1] == s) {
                start(mc, (String) p[0]);
            }
        }
        // view scripts inside phases
        switch (phase) {
            case "turn_spot" -> AutoPilot.setView(s < 380 ? 120 : 0, 15);   // snap 120 right, then back
            case "walk_look" -> AutoPilot.setView(sweepBase + 60 * Mth.sin((s - 440) * Mth.TWO_PI / 50F), 15); // look about
            case "sprint_turn" -> AutoPilot.setView(Math.min(90, (s - 600) * 4.5F), 15); // a 90 degree turn in 20 ticks
            default -> {}
        }
        if (s == 663) {
            o.keySprint.setDown(false); // sprint_tap: Ctrl tapped for 3 ticks, W held on: the suit must keep sprinting
            AutoPilot.hold(o.keySprint, false);
        }
        if (s == 100 || s == 160 || s == 280 || s == 350 || s == 470 || s == 610) {
            AutoPilot.shot(mc, "gait_" + phase + "_" + s);
        }
        log(mc, s);
        if (phase.equals("end")) {
            close();
            return false;
        }
        return true;
    }

    private static void start(Minecraft mc, String name) {
        var o = mc.options;
        AutoPilot.releaseAll();
        mc.player.setSprinting(false); // vanilla keeps sprinting after the key is released
        phase = name;
        switch (name) {
            case "walk_fwd", "walk_look" -> AutoPilot.hold(o.keyUp, true);
            case "back" -> AutoPilot.hold(o.keyDown, true);
            case "strafe_r" -> AutoPilot.hold(o.keyRight, true);
            case "strafe_l" -> AutoPilot.hold(o.keyLeft, true);
            case "diag" -> { AutoPilot.hold(o.keyUp, true); AutoPilot.hold(o.keyRight, true); }
            case "sprint", "sprint_turn", "sprint_tap" -> { AutoPilot.hold(o.keyUp, true); AutoPilot.hold(o.keySprint, true); }
            default -> {}
        }
        if (name.equals("walk_look")) {
            sweepBase = 0;
        }
        if (name.equals("sprint") || name.equals("sprint_tap")) {
            AutoPilot.setView(0, 15);
        }
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(FMLPaths.GAMEDIR.get().resolve("autopilot_gait.csv"));
            csv.write("tick,phase,keys,anim,rate,realigning,viewYaw,bodyYaw,twist,legTurn,speed,fwd,lat,expected,slip\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("gait csv", e);
        }
    }

    private static float lastBody = Float.NaN;

    private static void log(Minecraft mc, int s) {
        if (csv == null || !(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        var o = mc.options;
        String keys = (down(o.keyUp) ? "W" : "") + (down(o.keyDown) ? "S" : "") + (down(o.keyLeft) ? "A" : "")
                + (down(o.keyRight) ? "D" : "") + (down(o.keySprint) ? "+" : "");
        float body = suit.yBodyRot;
        float legTurn = Float.isNaN(lastBody) ? 0F : Mth.wrapDegrees(body - lastBody);
        lastBody = body;
        // velocity this tick, blocks/s, split along the legs (forward = where the boots point) and across them
        Vec3 v = new Vec3(suit.getX() - suit.xo, 0, suit.getZ() - suit.zo).scale(20);
        float b = body * Mth.DEG_TO_RAD;
        Vec3 fwdDir = new Vec3(-Mth.sin(b), 0, Mth.cos(b));
        Vec3 rightDir = new Vec3(-Mth.cos(b), 0, -Mth.sin(b));
        double fwd = v.dot(fwdDir), lat = v.dot(rightDir);
        String anim = suit.animator().getMovementAnimationName();
        double rate = suit.animator().getMovementAnimationRate();
        double expected = suit.animator().getSteppedSpeed(); // the planted foot pushes the suit along the legs at this speed
        double slip = Math.hypot(fwd - expected, lat);
        try {
            csv.write(String.format(Locale.ROOT, "%d,%s,%s,%s,%.3f,%b,%.1f,%.1f,%.1f,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f%n", s, phase,
                    keys.isEmpty() ? "-" : keys, anim, rate, suit.isRealigning(), mc.player.getYRot(), body,
                    Mth.wrapDegrees(mc.player.getYRot() - body), legTurn, v.length(), fwd, lat, expected, slip));
        } catch (IOException ignored) {
            // closed
        }
    }

    private static boolean down(KeyMapping k) {
        return k.isDown();
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

    private GaitScene() {}
}
