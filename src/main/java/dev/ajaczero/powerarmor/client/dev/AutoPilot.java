package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * DEV TOOL, inert unless started with -Dpowerarmor.autopilot (./gradlew runAutopilot).
 * Creates a flat creative world, climbs into a suit, drives a scripted sequence (walk, sprint, look
 * around, jump), saves screenshots to run/screenshots/ap_*.png, then quits. Lets us check visuals
 * (cockpit view, torso aiming, jump timing) without a human at the keyboard.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class AutoPilot {
    private static final boolean ENABLED = System.getProperty("powerarmor.autopilot") != null && !TerrainPilot.enabled();
    private static final String WORLD_PREFIX = "autopilot-";

    private static boolean worldRequested;
    private static boolean suitRequested;
    private static int tick = -1;          // script time, starts once we sit in the suit
    private static final Map<KeyMapping, Boolean> held = new HashMap<>();
    private static float yaw, pitch;
    private static ArmorStand sideCamera;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                createWorld(mc);
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (tick < 0) {
            if (!suitRequested && mc.player.tickCount > 40) {
                suitRequested = true;
                boardSuit(mc);
            }
            if (mc.player.getVehicle() instanceof PowerArmorEntity) {
                tick = 0;
                yaw = 0;
                pitch = 0;
            }
            return;
        }
        script(mc, tick++);
        held.forEach(KeyMapping::setDown); // re-assert every tick (focus changes release keys)
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static void script(Minecraft mc, int t) {
        var o = mc.options;
        switch (t) {
            case 0 -> { o.hideGui = true; o.setCameraType(CameraType.FIRST_PERSON); }
            case 10 -> shot(mc, "fp_idle");
            case 15 -> hold(o.keyUp, true);
            case 40, 41, 42, 43 -> { shot(mc, "fp_walk_" + t); logSpeed(mc, "walk"); }
            case 55 -> hold(o.keySprint, true);
            case 75, 76, 77 -> { shot(mc, "fp_run_" + t); logSpeed(mc, "run"); }
            case 85 -> releaseAll();
            case 90 -> yaw = 60;
            case 92 -> shot(mc, "fp_look_right_now");
            case 110 -> shot(mc, "fp_look_right_later");
            case 115 -> pitch = 35;
            case 120 -> shot(mc, "fp_look_down");
            case 122 -> pitch = -30;
            case 127 -> shot(mc, "fp_look_up");
            case 130 -> { pitch = 10; yaw = 0; o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 140 -> shot(mc, "tp_idle");
            case 142 -> yaw = 70;
            case 144 -> shot(mc, "tp_twist_now");
            case 170 -> shot(mc, "tp_twist_settled");
            case 175 -> { yaw = 0; hold(o.keyUp, true); }
            case 200 -> shot(mc, "tp_walk");
            case 215 -> { hold(o.keyUp, false); hold(o.keyRight, true); }
            case 235 -> shot(mc, "tp_strafe_right");
            case 240 -> { releaseAll(); hold(o.keyUp, true); hold(o.keySprint, true); }
            case 262 -> shot(mc, "tp_run");
            case 270 -> releaseAll();
            case 280 -> sideCamera(mc);
            case 285 -> hold(o.keyJump, true);
            case 288, 294 -> shot(mc, "side_charge_" + t);
            case 298 -> hold(o.keyJump, false);
            case 299, 300, 301, 302, 304, 306, 310, 316, 324, 334, 342 -> shot(mc, "side_jump_" + t);
            case 350 -> { mc.setCameraEntity(mc.player); o.hideGui = false; }
            case 355 -> mc.stop();
            default -> {}
        }
    }

    private static void logSpeed(Minecraft mc, String what) {
        var suit = mc.player.getVehicle();
        double v = Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20.0;
        PowerArmor.LOGGER.info("[autopilot] {} speed {} blocks/s", what, String.format("%.2f", v));
    }

    private static void hold(KeyMapping key, boolean down) {
        held.put(key, down);
        key.setDown(down);
    }

    private static void releaseAll() {
        held.replaceAll((k, v) -> false);
        held.forEach(KeyMapping::setDown);
        KeyMapping.releaseAll();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "ap_" + name + ".png", mc.getMainRenderTarget(), msg -> {});
    }

    /** A client-only camera 8 blocks to the side of the suit, looking at it. */
    private static void sideCamera(Minecraft mc) {
        Vec3 suit = mc.player.getVehicle().position();
        sideCamera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        Vec3 pos = suit.add(7, -0.4, 0);
        sideCamera.setPos(pos);
        sideCamera.xo = pos.x; sideCamera.yo = pos.y; sideCamera.zo = pos.z;
        sideCamera.setYRot(90); sideCamera.yRotO = 90; // look toward -X
        sideCamera.setYHeadRot(90); sideCamera.yHeadRotO = 90; // the camera follows the head yaw
        sideCamera.setXRot(0); sideCamera.xRotO = 0;
        mc.setCameraEntity(sideCamera);
    }

    private static void boardSuit(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }
            var level = player.serverLevel();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level);
            suit.moveTo(player.getX() + 0.5, player.getY(), player.getZ() + 0.5, 0, 0);
            level.addFreshEntity(suit);
            player.startRiding(suit);
        });
    }

    private static void createWorld(Minecraft mc) {
        Path saves = mc.gameDirectory.toPath().resolve("saves");
        try (Stream<Path> old = Files.exists(saves) ? Files.list(saves) : Stream.empty()) {
            old.filter(p -> p.getFileName().toString().startsWith(WORLD_PREFIX)).forEach(AutoPilot::deleteTree);
        } catch (IOException ignored) {
        }
        String name = WORLD_PREFIX + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
        }
    }

    private AutoPilot() {}
}
