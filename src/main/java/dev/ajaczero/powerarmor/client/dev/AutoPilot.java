package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import java.io.File;
import net.minecraft.core.BlockPos;
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
    /** "view" mode: stay in the cockpit and re-shoot the viewmodel whenever run/view/shoot appears (live tuning). */
    private static final boolean VIEW = "view".equals(System.getProperty("powerarmor.autopilot"));

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
        if (VIEW) {
            viewTuner(mc, tick++);
        } else {
            script(mc, tick++);
        }
        if (aimTarget != null) {
            aimAtPos(mc, aimTarget);
        }
        held.forEach(KeyMapping::setDown); // re-assert every tick (focus changes release keys)
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static void script(Minecraft mc, int t) {
        var o = mc.options;
        switch (t) {
            // GUI stays on in every scene: hiding it (F1) also skips the hand pass, i.e. the cockpit viewmodel.
            case 0 -> { o.hideGui = false; o.setCameraType(CameraType.FIRST_PERSON); }
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
            // ---- water: walk off a lip into a 6-deep pool, along the bottom, up stairs on the far side
            case 350 -> { mc.setCameraEntity(mc.player); releaseAll(); yaw = 0; pitch = 15; o.setCameraType(CameraType.THIRD_PERSON_BACK); buildPool(mc); }
            case 360 -> { openWaterLog(mc); hold(o.keyUp, true); }
            case 385, 400, 415, 430 -> shot(mc, "water_tp_" + t);
            case 440 -> { o.setCameraType(CameraType.FIRST_PERSON); o.hideGui = false; }
            case 450, 470 -> shot(mc, "water_fp_" + t);
            case 480 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 500, 520, 540, 560, 590 -> shot(mc, "water_tp_" + t);
            case 640 -> { releaseAll(); closeWaterLog(); survival(mc, false); }
            // ---- fuel: nearly empty tank, walk until the engine dies, refuel through the menu
            case 650 -> { o.hideGui = false; o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 0; pitch = 15; fuelScenarioSetup(mc); }
            case 660 -> { openFuelLog(mc); hold(o.keyUp, true); }
            case 675 -> shot(mc, "fuel_tp_low");
            case 690 -> o.setCameraType(CameraType.FIRST_PERSON);
            case 700 -> shot(mc, "fuel_fp_hud");
            case 712 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 720, 724 -> shot(mc, "fuel_tp_sputter_" + t);
            case 732 -> sideCamera(mc);
            // the engine dies at ~ t=738 (80 energy, walking burns 1 per tick); the slump plays for 1.6 s
            case 736, 742, 750, 760, 785 -> shot(mc, "fuel_side_shutdown_" + t);
            case 790 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); }
            case 795 -> shot(mc, "fuel_fp_out_of_fuel");
            case 800 -> { releaseAll(); hold(o.keyShift, true); }      // climb out
            case 805 -> hold(o.keyShift, false);
            case 815 -> openSuitMenu(mc);                             // sneak + right-click on the server side
            case 825 -> shot(mc, "menu_empty");
            case 830 -> refuel(mc);
            case 836 -> shot(mc, "menu_fueled");
            case 845 -> { mc.player.closeContainer(); sideCamera(mc); }
            case 850, 858, 866, 874, 890 -> shot(mc, "fuel_side_startup_" + t);
            // ---- rivet gun: climb back in, shoot three pigs 12 blocks ahead and a wall behind them
            case 900 -> { closeFuelLog(); mc.setCameraEntity(mc.player); yaw = 0; pitch = 9; cannonRangeSetup(mc); giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY); } // empty hand: right click goes to the tool
            case 910 -> { o.setCameraType(CameraType.FIRST_PERSON); openCannonLog(mc); }
            case 920 -> hold(o.keyUse, true);
            case 921, 922, 923, 924, 925, 926, 927, 928, 929, 930, 931, 932 -> shot(mc, "cannon_fp_" + t);
            case 940 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 12; }
            case 945, 946, 947, 949 -> shot(mc, "cannon_tp_" + t);
            case 958 -> { yaw = -12; sideCamera(mc); }
            case 969, 970, 971, 973, 976 -> shot(mc, "cannon_side_" + t);
            case 978 -> hold(o.keyUse, false);                    // let go: the tool arm lowers to its hip carry
            case 984 -> shot(mc, "cannon_side_lowered");
            case 985 -> releaseAll();
            // ---- cockpit while walking and looking down (cannon still installed): both arms and the feet in view,
            //      the tool must not clip into the upper arm
            case 986 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 55; hold(o.keyUp, true); }
            case 989, 990, 991, 992, 993 -> shot(mc, "fp_walkdown_" + t);
            case 994 -> releaseAll();
            // ---- right hand: hold and place stone bricks from inside, then eat a golden apple
            case 995 -> { closeCannonLog(); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON);
                          giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64)); yaw = 0; pitch = 40; }
            case 1003 -> shot(mc, "hand_fp_hold");
            case 1005, 1026, 1042 -> hold(o.keyUse, true);
            case 1006, 1007, 1008, 1009, 1010, 1011 -> shot(mc, "hand_fp_use_" + t);
            case 1016 -> { hold(o.keyUse, false); o.setCameraType(CameraType.THIRD_PERSON_BACK); pitch = 25; }
            case 1024 -> shot(mc, "hand_tp_hold");
            case 1027, 1028, 1029, 1031 -> shot(mc, "hand_tp_use_" + t);
            case 1034 -> { hold(o.keyUse, false); yaw = -90; pitch = 20;
                           giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH, 16)); }
            case 1038 -> frontCamera(mc);
            case 1040 -> shot(mc, "hand_front_hold");
            case 1043, 1044, 1045, 1047 -> shot(mc, "hand_front_use_" + t);
            case 1050 -> { hold(o.keyUse, false); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 10;
                           giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 4)); }
            case 1055 -> hold(o.keyUse, true);
            case 1062, 1075 -> shot(mc, "hand_fp_eat_" + t);
            case 1078 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 1085 -> shot(mc, "hand_tp_eat");
            case 1086 -> { releaseAll(); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 40;   // left click: the hand mines the bricks it placed
                           giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)); }
            case 1087 -> hold(o.keyAttack, true);
            case 1089, 1091 -> shot(mc, "hand_fp_mine_" + t);
            case 1092 -> releaseAll();
            // ---- EM projector: vacuum up dropped diamonds, then grab a pig, hold it and throw it
            case 1095 -> { giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY); emRangeSetup(mc); yaw = 180; pitch = 15; o.setCameraType(CameraType.THIRD_PERSON_BACK); openEmLog(mc); }
            case 1110 -> hold(o.keyUse, true);
            case 1113, 1118, 1124 -> shot(mc, "em_tp_items_" + t);
            case 1121 -> pitch = 10; // the next diamonds lie further out
            case 1128 -> o.setCameraType(CameraType.FIRST_PERSON);
            case 1132 -> shot(mc, "em_fp_items");
            case 1140 -> hold(o.keyUse, false);
            case 1146, 1149 -> aimAt(mc, emPig);
            case 1150 -> hold(o.keyUse, true);
            case 1156, 1164 -> shot(mc, "em_fp_pig_" + t);
            case 1166 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 1172 -> shot(mc, "em_tp_pig");
            case 1174 -> sideCamera(mc);
            case 1180 -> shot(mc, "em_side_pig");
            case 1182 -> pitch = -5;
            case 1186 -> hold(o.keyUse, false);
            case 1187, 1189, 1192, 1196 -> shot(mc, "em_side_throw_" + t);
            case 1205 -> { closeEmLog(); mc.setCameraEntity(mc.player); releaseAll(); }
            // ---- mining tools: drill a 3x3 into a rock face with ores, then fell a real oak with the chainsaw
            case 1210 -> { giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY); miningSetup(mc); yaw = 0; pitch = 20; o.setCameraType(CameraType.THIRD_PERSON_BACK); openMineLog(mc); }
            case 1225 -> aimTarget = Vec3.atCenterOf(mineBase.offset(0, 1, 4)).add(0, 0, -0.5); // re-aimed every tick (the eye moves as the suit turns)
            case 1228 -> hold(o.keyUse, true);
            case 1230, 1234, 1238, 1242 -> shot(mc, "drill_tp_" + t);
            case 1246 -> o.setCameraType(CameraType.FIRST_PERSON);
            case 1250, 1254, 1258 -> shot(mc, "drill_fp_" + t);
            case 1262 -> lookCamera(mc, Vec3.atBottomCenterOf(mineBase).add(-4.5, 2.0, 3.0), Vec3.atBottomCenterOf(mineBase).add(-0.5, 1.9, 1.8));
            case 1264, 1266, 1268 -> shot(mc, "drill_side_" + t);
            case 1272 -> { aimTarget = null; hold(o.keyUse, false); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 1278 -> shot(mc, "drill_tp_after");
            case 1280 -> treeSetup(mc);
            case 1284 -> aimTarget = Vec3.atCenterOf(treeBase).add(-0.5, 0, 0);
            case 1286 -> { hold(o.keyUse, true); o.setCameraType(CameraType.FIRST_PERSON); }
            case 1289 -> shot(mc, "saw_fp_" + t);
            case 1290 -> lookCamera(mc, Vec3.atBottomCenterOf(mineBase).add(-3.5, 3.0, -3.5), Vec3.atBottomCenterOf(mineBase).add(2.0, 1.6, 0.6)); // behind-left, clear of the rock and the canopy
            case 1291, 1293, 1294, 1295, 1296, 1297, 1298, 1300, 1304 -> shot(mc, "saw_side_" + t);
            case 1306 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); }
            case 1310 -> shot(mc, "saw_fp_" + t);
            case 1312 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 1324 -> { aimTarget = null; hold(o.keyUse, false); mc.setCameraEntity(mc.player); }
            case 1330 -> { shot(mc, "saw_tp_after"); closeMineLog(); }
            // ---- the cannon item on foot: hand (first / third person) and inventory icon
            case 1340 -> { o.setCameraType(CameraType.FIRST_PERSON); hold(o.keyShift, true); }
            case 1345 -> { hold(o.keyShift, false); giveCannon(mc); survival(mc, true); yaw = 120; pitch = 10; }
            case 1355 -> shot(mc, "item_fp");
            case 1357 -> o.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 1365 -> shot(mc, "item_tp_front");
            case 1367 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 1375 -> shot(mc, "item_tp_back");
            case 1377 -> { o.setCameraType(CameraType.FIRST_PERSON); mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }
            case 1385 -> shot(mc, "item_gui");
            case 1387 -> mc.setScreen(null);
            case 1390 -> mc.setScreen(new net.neoforged.neoforge.client.gui.ConfigurationScreen(
                    net.neoforged.fml.ModList.get().getModContainerById(PowerArmor.MODID).orElseThrow(), null));
            case 1396 -> shot(mc, "config");
            case 1398 -> mc.setScreen(null);
            // ---- lights at night (SuitGlowLayer): parked = green visor, piloted = amber, tool / hit = red, then the
            //      fuel runs out: the visor stutters and goes dark, the firebox dies down to embers
            case 1400 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); lightsSetup(mc); }
            case 1430 -> { yaw = 0; pitch = 0; boardNearestSuit(mc); } // (the client learns the new time once a second)
            case 1440 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); lightsCamera(mc); }
            case 1446 -> shot(mc, "lights_piloted");
            case 1448 -> hold(o.keyUse, true);                    // two rivets into the wall ahead
            case 1454 -> shot(mc, "lights_tool");
            case 1462 -> hold(o.keyUse, false);
            case 1468 -> shot(mc, "lights_amber_again");
            case 1470 -> onServer(mc, p -> { if (p.getVehicle() instanceof PowerArmorEntity s) s.hurt(p.serverLevel().damageSources().generic(), 1F); });
            case 1474 -> shot(mc, "lights_hit");
            case 1475 -> { Vec3 w = lightsPos; if (w != null) lookCamera(mc, w.add(-1.6, 2.4, 4.6), w.add(0, 2.5, 6.9)); }
            case 1477 -> shot(mc, "lights_rivets");                // red-hot rivets cooling in the wall
            case 1479 -> lightsCamera(mc);
            case 1480 -> hold(o.keyShift, true);                  // climb out: parked with power -> green
            case 1482 -> hold(o.keyShift, false);
            case 1494 -> shot(mc, "lights_parked");
            case 1496 -> boardNearestSuit(mc);                  // back in: a parked suit burns no fuel, so it could never run dry
            case 1500 -> onServer(mc, p -> p.serverLevel().getEntitiesOfClass(PowerArmorEntity.class, p.getBoundingBox().inflate(8)).forEach(s -> {
                              s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, net.minecraft.world.item.ItemStack.EMPTY); s.setBurn(1, 1600); })); // out of fuel
            case 1506, 1510, 1516 -> shot(mc, "lights_shutdown_" + t);
            case 1560 -> shot(mc, "lights_off");
            case 1562 -> mc.stop();
            default -> {}
        }
        if (t >= 360 && t < 640) {
            logWater(mc, t);
        }
        if (t >= 660 && t < 900) {
            logFuel(mc, t);
        }
        if (t >= 910 && t < 995) {
            logCannon(mc, t);
        }
        if (t >= 1095 && t < 1205) {
            logEm(mc, t);
        }
        if (t >= 1210 && t < 1330) {
            logMine(mc, t);
        }
    }

    private static void logSpeed(Minecraft mc, String what) {
        var suit = mc.player.getVehicle();
        double v = Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20.0;
        PowerArmor.LOGGER.info("[autopilot] {} speed {} blocks/s", what, String.format("%.2f", v));
    }

    private static java.io.BufferedWriter waterCsv;

    private static void openWaterLog(Minecraft mc) {
        try {
            waterCsv = java.nio.file.Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_water.csv"));
            waterCsv.write("tick,dz,y,speed,vy,onGround,inWater,eyeInWater,air,health,anim,suitHealth\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static double waterStartZ = Double.NaN;

    private static void logWater(Minecraft mc, int t) {
        if (!(mc.player.getVehicle() instanceof PowerArmorEntity suit) || waterCsv == null) {
            return;
        }
        if (Double.isNaN(waterStartZ)) {
            waterStartZ = suit.getZ();
        }
        try {
            waterCsv.write(String.format(java.util.Locale.ROOT, "%d,%.2f,%.3f,%.2f,%.2f,%b,%b,%b,%d,%.1f,%s,%.1f%n", t, suit.getZ() - waterStartZ,
                    suit.getY(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20, (suit.getY() - suit.yo) * 20,
                    suit.onGround(), suit.isInWater(), mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),
                    mc.player.getAirSupply(), mc.player.getHealth(), suit.getMovementAnimationName(), suit.getHealth()));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] water csv", e);
        }
    }

    private static void closeWaterLog() {
        try {
            if (waterCsv != null) {
                waterCsv.close();
            }
        } catch (IOException ignored) {
        }
    }

    // ---- fuel scenario helpers -------------------------------------------------------------

    private static PowerArmorEntity fuelSuit;
    private static java.io.BufferedWriter fuelCsv;

    /** Server: move to fresh flat ground 40 blocks east, empty the fuel slot and leave 3 s of walking. */
    private static void fuelScenarioSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (!(player.getVehicle() instanceof PowerArmorEntity suit)) {
                return;
            }
            fuelSuit = suit;
            player.stopRiding();
            suit.moveTo(suit.getX() + 40, suit.getY(), suit.getZ() - 30, 0, 0);
            player.startRiding(suit, true);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
            suit.setBurn(80, 1600);
        });
    }

    private static void openSuitMenu(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit != null) {
                player.setShiftKeyDown(true);
                fuelSuit.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                player.setShiftKeyDown(false);
            }
        });
    }

    private static void refuel(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit != null) {
                fuelSuit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 16));
            }
        });
    }

    // ---- cannon scenario helpers -----------------------------------------------------------

    private static final java.util.List<net.minecraft.world.entity.animal.Pig> pigs = new java.util.ArrayList<>();
    private static java.io.BufferedWriter cannonCsv;

    /** Server: back into the suit (facing +Z), three pigs 12 blocks ahead, a stone wall at 18. */
    private static void cannonRangeSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            player.startRiding(fuelSuit, true);
            fuelSuit.setYRot(0);
            var level = player.serverLevel();
            BlockPos base = fuelSuit.blockPosition();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy < 5; dy++) {
                    level.setBlock(base.offset(dx, dy, 18), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int i = -1; i <= 1; i++) {
                var pig = EntityType.PIG.create(level);
                pig.setNoAi(true);
                pig.moveTo(fuelSuit.getX() + i * 2.5, fuelSuit.getY(), fuelSuit.getZ() + 12, 180, 0);
                level.addFreshEntity(pig);
                pigs.add(pig);
            }
        });
    }

    private static net.minecraft.world.entity.animal.Pig emPig;
    private static java.io.BufferedWriter emCsv;

    /** Server: EM projector in the socket, 3 diamonds on the ground straight behind (the suit turns round),
     *  and a pig off to one side. */
    private static void emRangeSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                    new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.EM_PROJECTOR.get()));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            var level = player.serverLevel();
            for (int i = 0; i < 3; i++) {
                var item = new net.minecraft.world.entity.item.ItemEntity(level, fuelSuit.getX() + 0.3 * i, fuelSuit.getY() + 0.2,
                        fuelSuit.getZ() - 9 - 2 * i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                item.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(item);
            }
            emPig = EntityType.PIG.create(level);
            emPig.moveTo(fuelSuit.getX() + 4, fuelSuit.getY(), fuelSuit.getZ() - 8, 0, 0);
            level.addFreshEntity(emPig);
        });
    }

    /** Point the pilot's view at an entity's middle (it may have wandered since it was spawned). */
    private static void aimAt(Minecraft mc, net.minecraft.world.entity.Entity target) {
        if (target == null) {
            return;
        }
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0).subtract(mc.player.getEyePosition());
        yaw = (float) (net.minecraft.util.Mth.atan2(-to.x, to.z) * net.minecraft.util.Mth.RAD_TO_DEG);
        pitch = (float) (-net.minecraft.util.Mth.atan2(to.y, to.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
    }

    private static void openEmLog(Minecraft mc) {
        try {
            emCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_em.csv"));
            emCsv.write("tick,beamTarget,energy,diamonds,throws,pigDist,pigY,pigHealth,pigNoGravity\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void logEm(Minecraft mc, int t) {
        if (emCsv == null || fuelSuit == null) {
            return;
        }
        var target = fuelSuit.getBeamTarget();
        try {
            emCsv.write(String.format(java.util.Locale.ROOT, "%d,%s,%d,%d,%d,%.2f,%.2f,%.1f,%b%n", t,
                    target == null ? "-" : target.getType().toShortString(), fuelSuit.getTotalEnergy(),
                    mc.player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND), fuelSuit.getToolUses(),
                    emPig == null ? -1 : emPig.distanceTo(fuelSuit), emPig == null ? 0 : emPig.getY() - fuelSuit.getY(),
                    emPig == null ? 0 : emPig.getHealth(), emPig != null && emPig.isNoGravity()));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] em csv", e);
        }
    }

    private static void closeEmLog() {
        try {
            if (emCsv != null) {
                emCsv.close();
            }
        } catch (IOException ignored) {
        }
    }

    // ---- mining scenario helpers -------------------------------------------------------------

    private static BlockPos mineBase = BlockPos.ZERO, treeBase = BlockPos.ZERO;
    /** While set, the pilot keeps looking at this point. */
    private static Vec3 aimTarget;
    private static java.io.BufferedWriter mineCsv;

    /** Server: drill in the socket, survival (creative breaks drop nothing), a cleared work area with a
     *  5x4x3 rock face 4 blocks ahead (+Z, ores in the front layer, dirt and gravel on top). */
    private static void miningSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                    new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.DRILL.get()));
            var level = player.serverLevel();
            BlockPos base = fuelSuit.blockPosition();
            mineBase = base;
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-6, 0, -9), base.offset(8, 12, 9))) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 0, 4), base.offset(2, 3, 6))) {
                var block = p.getY() - base.getY() == 3 ? (p.getX() % 2 == 0 ? net.minecraft.world.level.block.Blocks.DIRT
                        : net.minecraft.world.level.block.Blocks.GRAVEL) : net.minecraft.world.level.block.Blocks.STONE;
                level.setBlock(p, block.defaultBlockState(), 3);
            }
            level.setBlock(base.offset(0, 1, 4), net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            level.setBlock(base.offset(-1, 0, 4), net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(base.offset(1, 2, 4), net.minecraft.world.level.block.Blocks.COAL_ORE.defaultBlockState(), 3);
            treeBase = base.offset(5, 0, 1);
        });
    }

    /** Server: chainsaw in the socket and an oak grown by the vanilla tree feature 5 blocks to the side (+X).
     *  Grown only now: its leaves next to the suit would block the third-person camera during the drill shots. */
    private static void treeSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                    new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINSAW.get()));
            var level = player.serverLevel();
            level.setBlock(treeBase.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getHolder(net.minecraft.data.worldgen.features.TreeFeatures.OAK)
                    .ifPresent(tree -> tree.value().place(level, level.getChunkSource().getGenerator(), level.random, treeBase));
        });
    }

    private static void aimAtPos(Minecraft mc, Vec3 at) {
        Vec3 to = at.subtract(mc.player.getEyePosition());
        yaw = (float) (net.minecraft.util.Mth.atan2(-to.x, to.z) * net.minecraft.util.Mth.RAD_TO_DEG);
        pitch = (float) (-net.minecraft.util.Mth.atan2(to.y, to.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
    }

    /** Where the lights scene's suit stands (it faces +Z). Set on the integrated server's thread, read on the client. */
    private static volatile Vec3 lightsPos;

    /** Night, and a fresh fuelled suit with the cannon, 60 blocks away from the other scenes on open ground; the player
     *  stands behind it (out of the camera's view). */
    private static void lightsSetup(Minecraft mc) {
        onServer(mc, player -> {
            var level = player.serverLevel();
            level.setDayTime(18000);
            int x = player.getBlockX() + 60, z = player.getBlockZ();
            Vec3 pos = new Vec3(x + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 8));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.RIVET_GUN.get()));
            suit.moveTo(pos.x, pos.y, pos.z, 0, 0);
            suit.setYBodyRot(0);
            level.addFreshEntity(suit);
            player.teleportTo(pos.x, pos.y, pos.z - 3);
            for (int dx = -3; dx <= 3; dx++) {     // a wall 7 blocks ahead of the suit to shoot rivets into
                for (int dy = 0; dy < 5; dy++) {
                    level.setBlock(BlockPos.containing(pos).offset(dx, dy, 7), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            lightsPos = pos;
        });
    }

    /** Camera 4.5 blocks in front of the lights suit, a little to its right, looking at the visor and firebox. */
    private static void lightsCamera(Minecraft mc) {
        Vec3 pos = lightsPos;
        if (pos != null) {
            lookCamera(mc, pos.add(-1.2, 2.3, 4.5), pos.add(0, 2.2, 0));
        }
    }

    /** Climb back into the nearest suit with an empty hand and the cannon back in its socket. */
    private static void boardNearestSuit(Minecraft mc) {
        onServer(mc, player -> {
            PowerArmorEntity suit = player.serverLevel().getEntitiesOfClass(PowerArmorEntity.class, player.getBoundingBox().inflate(8)).stream()
                    .min(java.util.Comparator.comparingDouble(s -> s.distanceToSqr(player))).orElse(null);
            if (suit != null) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 8)); // rivets (survival here), not in the hand
                suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                        new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.RIVET_GUN.get()));
                suit.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        });
    }

    /** A client-only camera at {@code pos} looking at {@code at}. */
    private static void lookCamera(Minecraft mc, Vec3 pos, Vec3 at) {
        Vec3 look = at.subtract(pos);
        float camYaw = (float) (net.minecraft.util.Mth.atan2(-look.x, look.z) * net.minecraft.util.Mth.RAD_TO_DEG);
        float camPitch = (float) (-net.minecraft.util.Mth.atan2(look.y, look.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
        sideCamera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        sideCamera.setPos(pos);
        sideCamera.xo = pos.x; sideCamera.yo = pos.y; sideCamera.zo = pos.z;
        sideCamera.setYRot(camYaw); sideCamera.yRotO = camYaw;
        sideCamera.setYHeadRot(camYaw); sideCamera.yHeadRotO = camYaw;
        sideCamera.setXRot(camPitch); sideCamera.xRotO = camPitch;
        mc.setCameraEntity(sideCamera);
    }

    private static void openMineLog(Minecraft mc) {
        try {
            mineCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_mine.csv"));
            mineCsv.write("tick,tool,uses,energy,faceLeft,logsLeft,drops,anim,crosshair,toolAnim,loops\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Client world: blocks left in the rock face and in the tree, dropped item stacks nearby. */
    private static void logMine(Minecraft mc, int t) {
        if (mineCsv == null || fuelSuit == null) {
            return;
        }
        int face = 0, logs = 0;
        for (BlockPos p : BlockPos.betweenClosed(mineBase.offset(-2, 0, 4), mineBase.offset(2, 3, 6))) {
            face += mc.level.getBlockState(p).isAir() ? 0 : 1;
        }
        for (BlockPos p : BlockPos.betweenClosed(treeBase.offset(-3, 0, -3), treeBase.offset(3, 9, 3))) {
            logs += mc.level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS) ? 1 : 0;
        }
        int drops = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(mineBase).inflate(12)).size();
        try {
            String aimed = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                    ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock()).getPath() : "-";
            String toolAnim = "-"; // what the client's GeckoLib "tool" controller plays (drill spin / chain)
            if (mc.player.getVehicle() instanceof PowerArmorEntity clientSuit) {
                var controller = clientSuit.getAnimatableInstanceCache().getManagerForId(clientSuit.getId()).getAnimationControllers().get("tool");
                if (controller != null && controller.getCurrentAnimation() != null) {
                    toolAnim = controller.getAnimationState() + ":" + controller.getCurrentAnimation().animation().name();
                }
            }
            mineCsv.write(String.format(java.util.Locale.ROOT, "%d,%s,%d,%d,%d,%d,%d,%s,%s,%s,%s%n", t,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(fuelSuit.getSocketStack().getItem()).getPath(),
                    fuelSuit.getToolUses(), fuelSuit.getTotalEnergy(), face, logs, drops,
                    mc.player.getVehicle() instanceof PowerArmorEntity s ? s.getMovementAnimationName() : "-", aimed, toolAnim, dev.ajaczero.powerarmor.client.SuitSounds.activeLoops()));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] mine csv", e);
        }
    }

    private static void closeMineLog() {
        try {
            if (mineCsv != null) {
                mineCsv.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static void giveHeld(Minecraft mc, net.minecraft.world.item.ItemStack stack) {
        onServer(mc, player -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack));
    }

    private static void giveCannon(Minecraft mc) {
        // Take the rivet gun out of the suit (the shot shows the bare socket coupling) and into the player's hand.
        mc.player.getInventory().selected = 0;
        onServer(mc, player -> {
            if (fuelSuit != null) {
                fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
            }
            player.getInventory().selected = 0; // the hand holds slot 0 (the EM scenes left slot 1 selected)
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.RIVET_GUN.get()));
            player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.EM_PROJECTOR.get()));
            player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.DRILL.get()));
            player.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINSAW.get()));
        });
    }

    private static void openCannonLog(Minecraft mc) {
        try {
            cannonCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_cannon.csv"));
            cannonCsv.write("tick,shots,energy,triggerHeld,pigHealth,anim\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Reads the server's suit (shots, trigger) and pigs: fine for a dev log in singleplayer. */
    private static void logCannon(Minecraft mc, int t) {
        if (cannonCsv == null || fuelSuit == null) {
            return;
        }
        String health = pigs.stream().map(p -> p.isAlive() ? String.format(java.util.Locale.ROOT, "%.0f", p.getHealth()) : "dead")
                .reduce((a, b) -> a + "/" + b).orElse("");
        try {
            cannonCsv.write(String.format(java.util.Locale.ROOT, "%d,%d,%d,%b,%s,%s%n", t, fuelSuit.getToolUses(), fuelSuit.getTotalEnergy(),
                    fuelSuit.isTriggerHeld(), health, mc.player.getVehicle() instanceof PowerArmorEntity s ? s.getMovementAnimationName() : "-"));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] cannon csv", e);
        }
    }

    private static void closeCannonLog() {
        try {
            if (cannonCsv != null) {
                cannonCsv.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
        IntegratedServer server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                action.accept(player);
            }
        });
    }

    private static void openFuelLog(Minecraft mc) {
        try {
            fuelCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_fuel.csv"));
            fuelCsv.write("tick,burnLeft,total,fuelItem,powered,canOperate,speed,anim,piloted\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Client view of the suit (synced data), one row per tick. */
    private static void logFuel(Minecraft mc, int t) {
        PowerArmorEntity suit = mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
        if (suit == null || fuelCsv == null) {
            return;
        }
        try {
            fuelCsv.write(String.format(java.util.Locale.ROOT, "%d,%d,%d,%s,%b,%b,%.2f,%s,%b%n", t, suit.getBurnLeft(), suit.getTotalEnergy(),
                    suit.getFuelStack().getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(suit.getFuelStack().getItem()).getPath(),
                    suit.isPowered(), suit.canOperate(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20, suit.getMovementAnimationName(),
                    suit.isPilotedBy(mc.player)));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] fuel csv", e);
        }
    }

    private static void closeFuelLog() {
        try {
            if (fuelCsv != null) {
                fuelCsv.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static void survival(Minecraft mc, boolean on) {
        IntegratedServer server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.setGameMode(on ? GameType.SURVIVAL : GameType.CREATIVE);
            }
        });
    }

    /** Pool in front of the suit (it faces +Z): 3 steps up onto a lip, 6 blocks of water, stairs out. */
    private static void buildPool(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !(player.getVehicle() instanceof PowerArmorEntity suit)) {
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            var level = player.serverLevel();
            BlockPos base = suit.blockPosition(); // g = base.y (the suit stands on top of base.y - 1)
            int g = base.getY();
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            var glass = net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState();
            var water = net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = 3; dz <= 17; dz++) {
                    for (int y = g - 3; y <= g + 2; y++) {
                        BlockPos p = base.offset(dx, 0, dz).atY(y);
                        boolean side = Math.abs(dx) == 4;
                        int top; // highest stone y in this column (stairs), or none
                        if (dz <= 5) {
                            top = g + (dz - 3);          // outside stairs up: tops at g+1, g+2, g+3
                        } else if (dz >= 12) {
                            top = g - 3 + (dz - 12);     // inside stairs out: g-3 ... g+2 at the far wall
                        } else {
                            top = Integer.MIN_VALUE;
                        }
                        if (y <= top) {
                            level.setBlock(p, stone, 3);
                        } else if (dz >= 6 && dz <= 17) {
                            level.setBlock(p, side ? glass : water, 3);
                        } else if (y < g) {
                            level.setBlock(p, stone, 3);
                        }
                    }
                }
            }
        });
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

    private static int viewStep = -1;
    private static long viewLastRequest;

    /**
     * Dev loop for tuning the cockpit viewmodel (CockpitHands) without restarting: edit run/view/tuning.json, touch
     * run/view/shoot, and screenshots ap_view_*.png appear (one per tool, bare socket, walking + looking down,
     * eating); run/view/done is written when the set is complete. Runs until the game is closed.
     */
    private static void viewTuner(Minecraft mc, int t) {
        var o = mc.options;
        java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("view");
        if (t == 0) {
            viewLastRequest = System.currentTimeMillis();
            try { // tools/view-tuner.sh stop uses this if the quit request is ever ignored
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Files.writeString(dir.resolve("pid"), Long.toString(ProcessHandle.current().pid()));
            } catch (java.io.IOException ignored) {
            }
        }
        // Never linger: quit on request, or after 10 minutes without a shot request.
        if (java.nio.file.Files.exists(dir.resolve("quit")) || System.currentTimeMillis() - viewLastRequest > 10 * 60_000) {
            try {
                java.nio.file.Files.deleteIfExists(dir.resolve("quit"));
                java.nio.file.Files.deleteIfExists(dir.resolve("pid"));
            } catch (java.io.IOException ignored) {
            }
            mc.stop();
            return;
        }
        if (viewStep < 0) {
            try {
                java.nio.file.Files.createDirectories(dir);
                if (!java.nio.file.Files.deleteIfExists(dir.resolve("shoot")) && t != 60) {
                    return;
                }
                viewLastRequest = System.currentTimeMillis();
                java.nio.file.Files.deleteIfExists(dir.resolve("done"));
            } catch (java.io.IOException e) {
                return;
            }
            viewStep = 0;
        }
        int s = viewStep++;
        switch (s) {
            // GUI on: hiding it (F1) also skips the hand pass, so the viewmodel wouldn't be drawn.
            case 0 -> { o.hideGui = false; o.setCameraType(CameraType.FIRST_PERSON); releaseAll(); yaw = 0; pitch = 10;
                        if (java.nio.file.Files.exists(dir.resolve("tuning.json"))) dev.ajaczero.powerarmor.client.CockpitHands.loadTuning(dir.resolve("tuning.json"));
                        socket(mc, dev.ajaczero.powerarmor.registry.ModItems.RIVET_GUN.get());
                        giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64)); }
            case 12 -> shot(mc, "view_cannon");
            case 13 -> socket(mc, dev.ajaczero.powerarmor.registry.ModItems.DRILL.get());
            case 25 -> shot(mc, "view_drill");
            case 26 -> socket(mc, dev.ajaczero.powerarmor.registry.ModItems.CHAINSAW.get());
            case 38 -> shot(mc, "view_chainsaw");
            case 39 -> socket(mc, dev.ajaczero.powerarmor.registry.ModItems.EM_PROJECTOR.get());
            case 51 -> shot(mc, "view_em");
            case 52 -> socket(mc, null);
            case 64 -> shot(mc, "view_empty");
            case 65 -> { socket(mc, dev.ajaczero.powerarmor.registry.ModItems.RIVET_GUN.get()); pitch = 55; hold(o.keyUp, true); }
            case 80 -> shot(mc, "view_walkdown");
            case 81 -> { releaseAll(); pitch = 10; giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 4)); }
            case 86 -> hold(o.keyUse, true);
            case 98 -> shot(mc, "view_eat");
            case 99 -> { releaseAll(); giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64)); }
            case 102 -> {
                viewStep = -1;
                try {
                    java.nio.file.Files.writeString(dir.resolve("done"), "ok");
                } catch (java.io.IOException ignored) {
                }
            }
            default -> {}
        }
    }

    /** Server: put this tool (or nothing) in the socket of the suit the player sits in. */
    private static void socket(Minecraft mc, @org.jetbrains.annotations.Nullable net.minecraft.world.item.Item tool) {
        onServer(mc, player -> {
            if (player.getVehicle() instanceof PowerArmorEntity suit) {
                suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                        tool == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(tool));
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "ap_" + name + ".png", mc.getMainRenderTarget(), msg -> {});
    }

    /** A client-only camera in front of the suit and to its right, the working hand's side (its torso faces the pilot's
     *  yaw), looking back at it. */
    private static void frontCamera(Minecraft mc) {
        var suit = mc.player.getVehicle();
        float yawRad = yaw * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-net.minecraft.util.Mth.sin(yawRad), 0, net.minecraft.util.Mth.cos(yawRad));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 pos = suit.position().add(forward.scale(4.5)).add(right.scale(2.5)).add(0, 0.6, 0);
        Vec3 look = suit.position().add(0, 1.7, 0).subtract(pos);
        float camYaw = (float) (net.minecraft.util.Mth.atan2(-look.x, look.z) * net.minecraft.util.Mth.RAD_TO_DEG);
        float camPitch = (float) (-net.minecraft.util.Mth.atan2(look.y, look.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
        sideCamera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        sideCamera.setPos(pos);
        sideCamera.xo = pos.x; sideCamera.yo = pos.y; sideCamera.zo = pos.z;
        sideCamera.setYRot(camYaw); sideCamera.yRotO = camYaw;
        sideCamera.setYHeadRot(camYaw); sideCamera.yHeadRotO = camYaw;
        sideCamera.setXRot(camPitch); sideCamera.xRotO = camPitch;
        mc.setCameraEntity(sideCamera);
    }

    /** A client-only camera 8 blocks to the side of the suit, looking at it. */
    private static void sideCamera(Minecraft mc) {
        var target = mc.player.getVehicle() != null ? mc.player.getVehicle()
                : mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(8)).stream().findFirst().orElseThrow();
        Vec3 suit = target.position();
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
            // A full stack of blaze rods (~4 hours of walking): enough for any scripted drive.
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 64));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.RIVET_GUN.get()));
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
