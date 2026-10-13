package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * DEV TOOL, inert unless started with -Dpowerarmor.autopilot=terrain:<world> (./gradlew runAutopilotTerrain).
 * Copies the given save to "autopilot-terrain" (the original is never opened), loads the copy, climbs
 * into a suit and jogs / sprints in four directions over real terrain. Logs one CSV row per tick to
 * run/autopilot_terrain.csv and saves cockpit screenshots run/screenshots/ap_terrain_*.png.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class TerrainPilot {
    private static final String MODE = System.getProperty("powerarmor.autopilot", "");
    private static final boolean ENABLED = MODE.startsWith("terrain:");
    private static final String COPY = "autopilot-terrain";
    private static final int LEG_TICKS = 100; // per gait per direction

    private static boolean worldRequested, suitRequested;
    private static int tick = -1;
    private static float startYaw;
    private static BufferedWriter csv, frames;
    private static double lastX, lastY, lastZ;

    public static boolean enabled() {
        return ENABLED;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                openCopy(mc, MODE.substring("terrain:".length()));
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (tick < 0) {
            if (!(mc.player.getVehicle() instanceof PowerArmorEntity) && !suitRequested && mc.player.tickCount > 40) {
                suitRequested = true;
                boardSuit(mc);
            }
            if (mc.player.getVehicle() instanceof PowerArmorEntity && !fueled) {
                // The save may already have the player inside one of their own (unfuelled) suits.
                fueled = true;
                fuelVehicle(mc);
            }
            // Start once the suit has fuel and finished its 1.4 s startup.
            if (mc.player.getVehicle() instanceof PowerArmorEntity suit && suit.canOperate() && mc.player.tickCount > 60) {
                tick = 0;
                startYaw = mc.player.getYRot();
                lastX = suit.getX(); lastY = suit.getY(); lastZ = suit.getZ();
                openCsv(mc);
            }
            return;
        }
        int t = tick++;
        int leg = t / (2 * LEG_TICKS);
        if (leg >= 4) {
            finish(mc);
            return;
        }
        boolean sprint = (t % (2 * LEG_TICKS)) >= LEG_TICKS;
        float yaw = startYaw + 90 * leg;
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(10); mc.player.xRotO = 10;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false; // F1 would also skip the hand pass (cockpit viewmodel)
        set(mc.options.keyUp, true);
        set(mc.options.keySprint, sprint);
        if (!sprint && mc.player.isSprinting()) {
            mc.player.setSprinting(false); // vanilla keeps sprinting after the key is released
        }
        if (mc.player.getVehicle() instanceof PowerArmorEntity suit) {
            log(t, (sprint ? "sprint" : "jog") + leg, suit);
        }
        if (t % 25 == 0) {
            Screenshot.grab(mc.gameDirectory, "ap_terrain_" + t + ".png", mc.getMainRenderTarget(), msg -> {});
        }
    }

    /** Every render frame: how far the pilot's eye (the camera) sits above the drawn model, in blocks. From the
     *  cockpit, any change of this between frames is the suit's arm jerking up or down in view (it did, on every
     *  step). Uses the eye at this frame's partial tick: the Camera object still holds last frame's position here. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || frames == null || tick < 0 || mc.player == null || !(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        float partial = (float) event.getPartialTick();
        double model = Mth.lerp(partial, suit.yOld, suit.getY()) + suit.getStepOffset(partial);
        try {
            frames.write(String.format(Locale.ROOT, "%d,%.3f,%.4f,%.4f%n", tick, partial, mc.player.getEyePosition(partial).y - model, model));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[terrain] frames csv", e);
        }
    }

    private static void set(KeyMapping key, boolean down) {
        key.setDown(down);
    }

    private static void log(int t, String phase, PowerArmorEntity suit) {
        double dx = suit.getX() - lastX, dy = suit.getY() - lastY, dz = suit.getZ() - lastZ;
        lastX = suit.getX(); lastY = suit.getY(); lastZ = suit.getZ();
        try {
            csv.write(String.format(Locale.ROOT, "%d,%s,%.2f,%.3f,%.2f,%.2f,%.2f,%b,%d,%s,%.2f,%b,%.3f%n", t, phase,
                    suit.getX(), suit.getY(), suit.getZ(), Math.hypot(dx, dz) * 20, dy * 20, suit.onGround(),
                    suit.getAirTicks(), suit.getMovementAnimationName(), suit.getHipBob(), suit.horizontalCollision, Minecraft.getInstance().player.getEyeY()));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[terrain] csv", e);
        }
    }

    private static void openCsv(Minecraft mc) {
        try {
            csv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_terrain.csv"));
            csv.write("tick,phase,x,y,z,speed,vy,onGround,airTicks,anim,hipBob,hCollide,eyeY\n");
            frames = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_terrain_frames.csv"));
            frames.write("tick,partial,eyeAboveModel,modelY\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void finish(Minecraft mc) {
        try {
            csv.close();
            frames.close();
            frames = null;
        } catch (IOException ignored) {
        }
        set(mc.options.keyUp, false);
        set(mc.options.keySprint, false);
        mc.options.hideGui = false;
        PowerArmor.LOGGER.info("[terrain] done, {} ticks", tick);
        mc.stop();
    }

    private static void boardSuit(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }
            var level = player.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level);
            // A full stack of blaze rods (~4 hours of walking): enough for any scripted drive.
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 64));
            suit.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
            level.addFreshEntity(suit);
            player.startRiding(suit);
        });
    }

    private static boolean fueled;

    /** Server: fill the fuel slot of whatever suit the player sits in (only ever in the copied world). */
    private static void fuelVehicle(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && player.getVehicle() instanceof PowerArmorEntity suit) {
                suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 64));
            }
        });
    }

    /** Copy saves/<source> to saves/autopilot-terrain (fresh each run) and open the copy. */
    private static void openCopy(Minecraft mc, String source) {
        Path saves = mc.gameDirectory.toPath().resolve("saves");
        Path from = saves.resolve(source), to = saves.resolve(COPY);
        try {
            if (Files.exists(to)) {
                try (Stream<Path> walk = Files.walk(to)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
            try (Stream<Path> walk = Files.walk(from)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (p.getFileName().toString().equals("session.lock")) {
                        continue;
                    }
                    Path target = to.resolve(from.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("could not copy world " + source, e);
        }
        mc.createWorldOpenFlows().openWorld(COPY, () -> PowerArmor.LOGGER.error("[terrain] failed to open {}", COPY));
    }

    private TerrainPilot() {}
}
