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
import net.minecraft.world.level.gamerules.GameRules;
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
    /** "mech" mode: skip straight to the suit-mechanics scene (MECH_START), for quick iterations. */
    private static final boolean MECH_ONLY = "mech".equals(System.getProperty("powerarmor.autopilot"));
    /** "pose" mode: only the pose check (PoseScene: torso lean, tool raise and twist from fixed cameras), ~30 s. */
    private static final boolean POSE_ONLY = "pose".equals(System.getProperty("powerarmor.autopilot"));
    /** "gait" mode: only the foot-slip / leg-turning drive (GaitScene), ~45 s. */
    private static final boolean GAIT_ONLY = "gait".equals(System.getProperty("powerarmor.autopilot"));
    /** "paint" mode: only the paint scene (PaintScene: dyes and a wet sponge on a suit), ~20 s. */
    private static final boolean PAINT_ONLY = "paint".equals(System.getProperty("powerarmor.autopilot"));
    /** "styles" mode: only the helmet styles scene (StyleScene: five helmets, paint, the smithing table), ~30 s. */
    private static final boolean STYLES_ONLY = "styles".equals(System.getProperty("powerarmor.autopilot"));
    /** "engine" mode: only the engine scene (EngineScene: flooded firebox, tidal engine, star reactor), ~35 s. */
    private static final boolean ENGINE_ONLY = "engine".equals(System.getProperty("powerarmor.autopilot"));
    private static final int MECH_START = 1712;
    /** A file run/cleanzap: no fire from the lightning scene's bolt, so the knock-out animation can be seen. */
    private static final boolean CLEAN_ZAP = Files.exists(Path.of("cleanzap"));

    private static boolean worldRequested;
    private static boolean suitRequested;
    private static int tick = -1;          // script time, starts once we sit in the suit
    private static final Map<KeyMapping, Boolean> held = new HashMap<>();
    private static float yaw, pitch;

    /** For the scene classes: the view the script holds the player to (applied every tick). */
    static void setView(float newYaw, float newPitch) {
        yaw = newYaw;
        pitch = newPitch;
    }
    private static ArmorStand sideCamera;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.gui.screen() instanceof TitleScreen) {
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
                tick = MECH_ONLY ? MECH_START : 0;
                yaw = 0;
                pitch = 0;
            }
            return;
        }
        if (VIEW) {
            viewTuner(mc, tick++);
        } else if (POSE_ONLY) {
            if (!PoseScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (GAIT_ONLY) {
            if (!GaitScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (PAINT_ONLY) {
            if (!PaintScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (ENGINE_ONLY) {
            if (!EngineScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (STYLES_ONLY) {
            if (!StyleScene.step(mc, tick++)) {
                mc.stop();
            }
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
            case 0 -> { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.FIRST_PERSON); }
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
            case 177 -> shot(mc, "tp_idle");
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
            case 440 -> { o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 450, 470 -> shot(mc, "water_fp_" + t);
            case 480 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 500, 520, 540, 560, 590 -> shot(mc, "water_tp_" + t);
            case 640 -> { releaseAll(); closeWaterLog(); survival(mc, false);
                          onServer(mc, p -> { if (p.getVehicle() instanceof PowerArmorEntity s) { // the firebox back in (see buildPool)
                              s.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.FIREBOX.get()));
                              s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 16)); } }); }
            // ---- fuel: nearly empty tank, walk until the engine dies, refuel through the menu
            case 650 -> { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 0; pitch = 15; fuelScenarioSetup(mc); }
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
            // ---- chaingun: climb back in, hold the trigger (spin-up to a barrage) at three pigs 12 blocks ahead and a wall
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
                           giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 4));
                           socket(mc, null); } // no tool to pass the click on to: the "sealed in" message shows
            case 1055 -> hold(o.keyUse, true);  // sealed in: eating is refused
            case 1062, 1075 -> shot(mc, "hand_fp_food_refused_" + t);
            case 1078 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 1085 -> shot(mc, "hand_tp_food_refused");
            case 1086 -> { releaseAll(); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 40;   // left click: the hand mines the bricks it placed
                           giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)); }
            case 1087 -> hold(o.keyAttack, true);
            case 1089, 1091 -> shot(mc, "hand_fp_mine_" + t);
            case 1092 -> releaseAll();
            // ---- EM projector: vacuum up dropped diamonds, then grab a pig, hold it and throw it
            case 1095 -> { giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY); emRangeSetup(mc); yaw = 180; pitch = 15; o.setCameraType(CameraType.THIRD_PERSON_BACK); openEmLog(mc); }
            case 1104 -> emCloseCamera(mc, false);   // close-up of the mounted projector: faint idle glow
            case 1107 -> shot(mc, "em_close_idle");
            case 1109 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false); }
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
            case 1167 -> emCloseCamera(mc, true);    // close-up while the beam holds the pig: core + tips lit, sparks
            case 1169 -> shot(mc, "em_close_pig");
            case 1170 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false); }
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
            case 1377 -> { o.setCameraType(CameraType.FIRST_PERSON); mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }
            case 1385 -> shot(mc, "item_gui");
            case 1387 -> mc.gui.setScreen(null);
            case 1390 -> mc.gui.setScreen(new net.neoforged.neoforge.client.gui.ConfigurationScreen(
                    net.neoforged.fml.ModList.get().getModContainerById(PowerArmor.MODID).orElseThrow(), null));
            case 1396 -> shot(mc, "config");
            case 1398 -> mc.gui.setScreen(null);
            // ---- lights at night (SuitGlowLayer): piloted = amber (also with the tool and when hit), empty = the machine
            //      mind's green, then the fuel runs out: the visor stutters and goes dark, the firebox dies down to embers
            case 1400 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); lightsSetup(mc); }
            case 1430 -> { yaw = 0; pitch = 0; boardNearestSuit(mc); } // (the client learns the new time once a second)
            case 1440 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); lightsCamera(mc); }
            case 1446 -> shot(mc, "lights_piloted");
            case 1448 -> hold(o.keyUse, true);                    // a long barrage into the wall ahead: tracers, hot barrels
            case 1454 -> shot(mc, "lights_tool");
            case 1500, 1560, 1596 -> shot(mc, "lights_barrage_" + t);
            case 1600 -> hold(o.keyUse, false);
            case 1608 -> shot(mc, "lights_hot");                   // barrels still glowing as they coast down
            case 1620 -> onServer(mc, p -> { if (p.getVehicle() instanceof PowerArmorEntity s) s.hurt(p.level().damageSources().generic(), 1F); });
            case 1624 -> shot(mc, "lights_hit");
            case 1630 -> hold(o.keyShift, true);                  // climb out: the machine mind takes over -> green
            case 1632 -> hold(o.keyShift, false);
            case 1644 -> shot(mc, "lights_parked");
            case 1646 -> boardNearestSuit(mc);                  // back in, to watch it run dry from the cockpit
            case 1650 -> onServer(mc, p -> p.level().getEntitiesOfClass(PowerArmorEntity.class, p.getBoundingBox().inflate(8)).forEach(s -> {
                              s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, net.minecraft.world.item.ItemStack.EMPTY); s.setBurn(1, 1600); })); // out of fuel
            case 1656, 1660, 1666 -> shot(mc, "lights_shutdown_" + t);
            case 1710 -> shot(mc, "lights_off");
            default -> {}
        }
        if (t >= MECH_START) {
            mechanics(mc, t - MECH_START);
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
            waterCsv.write("tick,dz,y,speed,vy,onGround,inWater,eyeInWater,air,health,anim,suitHealth,powered,engine\n");
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
            waterCsv.write(String.format(java.util.Locale.ROOT, "%d,%.2f,%.3f,%.2f,%.2f,%b,%b,%b,%d,%.1f,%s,%.1f,%b,%s%n", t, suit.getZ() - waterStartZ,
                    suit.getY(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20, (suit.getY() - suit.yo) * 20,
                    suit.onGround(), suit.isInWater(), mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),
                    mc.player.getAirSupply(), mc.player.getHealth(), suit.getMovementAnimationName(), suit.getHealth(), suit.isPowered(), suit.getEngine()));
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
            suit.snapTo(suit.getX() + 40, suit.getY(), suit.getZ() - 30, 0, 0);
            player.startRiding(suit, true, true);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
            suit.setBurn(80, 1600);
        });
    }

    private static void openSuitMenu(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit != null) {
                player.setShiftKeyDown(true);
                fuelSuit.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, fuelSuit.position());
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

    private static final java.util.List<net.minecraft.world.entity.animal.pig.Pig> pigs = new java.util.ArrayList<>();
    private static java.io.BufferedWriter cannonCsv;

    /** Server: back into the suit (facing +Z), three pigs 12 blocks ahead, a stone wall at 18. */
    private static void cannonRangeSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            player.startRiding(fuelSuit, true, true);
            fuelSuit.setYRot(0);
            var level = player.level();
            BlockPos base = fuelSuit.blockPosition();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy < 5; dy++) {
                    level.setBlock(base.offset(dx, dy, 18), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int i = -1; i <= 1; i++) {
                var pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                pig.setNoAi(true);
                pig.snapTo(fuelSuit.getX() + i * 2.5, fuelSuit.getY(), fuelSuit.getZ() + 12, 180, 0);
                level.addFreshEntity(pig);
                pigs.add(pig);
            }
        });
    }

    private static net.minecraft.world.entity.animal.pig.Pig emPig;
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
            var level = player.level();
            for (int i = 0; i < 3; i++) {
                var item = new net.minecraft.world.entity.item.ItemEntity(level, fuelSuit.getX() + 0.3 * i, fuelSuit.getY() + 0.2,
                        fuelSuit.getZ() - 9 - 2 * i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                item.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(item);
            }
            emPig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            emPig.snapTo(fuelSuit.getX() + 4, fuelSuit.getY(), fuelSuit.getZ() - 8, 0, 0);
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
    /** The player looks here every tick (null: the yaw/pitch fields). Package-private: the scene classes aim too. */
    static Vec3 aimTarget;
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
            var level = player.level();
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
            var level = player.level();
            level.setBlock(treeBase.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.registryAccess().lookupOrThrow(Registries.FEATURE)
                    .get(net.minecraft.data.worldgen.features.TreeFeatures.OAK)
                    .ifPresent(tree -> tree.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), treeBase));
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
            var level = player.level();
            DevUtil.setTime(level, 18000);
            int x = player.getBlockX() + 60, z = player.getBlockZ();
            Vec3 pos = new Vec3(x + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 8)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get()));
            suit.snapTo(pos.x, pos.y, pos.z, 0, 0);
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
            PowerArmorEntity suit = player.level().getEntitiesOfClass(PowerArmorEntity.class, player.getBoundingBox().inflate(8)).stream()
                    .min(java.util.Comparator.comparingDouble(s -> s.distanceToSqr(player))).orElse(null);
            if (suit != null) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.getInventory().setItem(7, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 64)); // bullets (survival here), not in the hand
                player.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 64));
                suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                        new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get()));
                suit.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, suit.position());
            }
        });
    }

    /** A client-only camera at {@code pos} looking at {@code at}. */
    static void lookCamera(Minecraft mc, Vec3 pos, Vec3 at) {
        Vec3 look = at.subtract(pos);
        float camYaw = (float) (net.minecraft.util.Mth.atan2(-look.x, look.z) * net.minecraft.util.Mth.RAD_TO_DEG);
        float camPitch = (float) (-net.minecraft.util.Mth.atan2(look.y, look.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
        sideCamera = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, mc.level);
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
                if (controller != null && controller.getCurrentAnimationPoint() != null) {
                    toolAnim = controller.getPlayState() + ":" + controller.getCurrentAnimationPoint().animation().name();
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
        mc.player.getInventory().setSelectedSlot(0);
        onServer(mc, player -> {
            if (fuelSuit != null) {
                fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, net.minecraft.world.item.ItemStack.EMPTY);
            }
            player.getInventory().setSelectedSlot(0); // the hand holds slot 0 (the EM scenes left slot 1 selected)
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get()));
            player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.EM_PROJECTOR.get()));
            player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.DRILL.get()));
            player.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINSAW.get()));
        });
    }

    private static void openCannonLog(Minecraft mc) {
        try {
            cannonCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_cannon.csv"));
            cannonCsv.write("tick,shots,energy,triggerHeld,pigHealth,anim,spin,heat\n");
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
            cannonCsv.write(String.format(java.util.Locale.ROOT, "%d,%d,%d,%b,%s,%s,%.2f,%.2f%n", t, fuelSuit.getToolUses(), fuelSuit.getTotalEnergy(),
                    fuelSuit.isTriggerHeld(), health, mc.player.getVehicle() instanceof PowerArmorEntity s ? s.getMovementAnimationName() : "-",
                    fuelSuit.getToolSpin(), fuelSuit.getToolHeat()));
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

    static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
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
            // Since the engines (2026-10-21) a firebox floods in water this deep: this scene is about moving through
            // water, so it drives a tidal engine (free under water) with a water bucket to climb the stairs out on.
            // The firebox goes back in at the end of the scene (engineBack).
            suit.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.TIDAL_ENGINE.get()));
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            var level = player.level();
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

    static void hold(KeyMapping key, boolean down) {
        held.put(key, down);
        key.setDown(down);
    }

    static void releaseAll() {
        held.replaceAll((k, v) -> false);
        held.forEach(KeyMapping::setDown);
        KeyMapping.releaseAll();
    }

    private static int viewStep = -1;
    private static long viewLastRequest;

    /**
     * Dev loop for tuning the cockpit viewmodel (CockpitHands) without restarting: edit run/view/tuning.json, touch
     * run/view/shoot, and screenshots ap_view_*.png appear (one per tool, bare socket, walking + looking down,
     * eating, the right hand with a sword / empty, and the same items on foot for comparison); run/view/done is written when the set is complete. Runs until the game is closed.
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
            case 0 -> { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.FIRST_PERSON); releaseAll(); yaw = 0; pitch = 10;
                        if (java.nio.file.Files.exists(dir.resolve("tuning.json"))) dev.ajaczero.powerarmor.client.CockpitHands.loadTuning(dir.resolve("tuning.json"));
                        socket(mc, dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get());
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
            case 65 -> { socket(mc, dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get()); pitch = 55; hold(o.keyUp, true); }
            case 80 -> shot(mc, "view_walkdown");
            case 81 -> { releaseAll(); pitch = 10; giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 4)); }
            case 98 -> shot(mc, "view_food"); // holding food (pilots can't eat: no use animation to check)
            // the right hand in the suit, then the same items on foot (vanilla's first-person hand) to compare;
            // wait out the attack cooldown after each swap: vanilla (and the gauntlet) keep the hand lowered until it recharges
            case 99 -> { releaseAll(); giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD)); }
            case 125 -> shot(mc, "view_sword");
            case 126 -> giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY);
            case 135 -> shot(mc, "view_hand");
            case 136 -> { onFoot(mc, true); giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64)); }
            case 150 -> shot(mc, "view_vanilla_bricks");
            case 151 -> giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            case 177 -> shot(mc, "view_vanilla_sword");
            case 178 -> giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY);
            case 190 -> shot(mc, "view_vanilla_hand");
            case 191 -> { onFoot(mc, false); giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64)); }
            // overheated chaingun (client-side heat: the server keeps sending 0 only when it changes, so it sticks)
            case 192 -> socket(mc, dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get());
            case 198 -> { if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.setToolSpin(0F, 1F); }
            case 202 -> shot(mc, "view_hot");
            case 203 -> { if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.setToolSpin(0F, 0.7F); }
            case 206 -> shot(mc, "view_warm");
            case 207 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.setToolSpin(0F, 1F); }
            case 214 -> shot(mc, "view_hot_tp");
            case 215 -> { o.setCameraType(CameraType.FIRST_PERSON); if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.setToolSpin(0F, 0F); }
            // tracers: a barrage diagonally up at the sky (yaw 45: the streaks must trail straight back along their flight)
            case 216 -> { giveHeld(mc, net.minecraft.world.item.ItemStack.EMPTY); yaw = 45; pitch = -25;
                          onServer(mc, p -> p.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 64))); }
            case 220 -> hold(o.keyUse, true);
            case 250, 252 -> shot(mc, "view_tracers_" + s);
            case 253 -> { sideCamera(mc); o.setCameraType(CameraType.THIRD_PERSON_BACK); } // (a "first person" camera entity would draw the pilot's cockpit tracers)
            case 262, 264 -> shot(mc, "view_tracers_tp_" + s);
            case 265 -> { releaseAll(); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 10;
                          giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64)); }
            case 285 -> {
                viewStep = -1;
                try {
                    java.nio.file.Files.writeString(dir.resolve("done"), "ok");
                } catch (java.io.IOException ignored) {
                }
            }
            default -> {}
        }
    }

    private static PowerArmorEntity parkedSuit;

    /** Server: climb out and stand 3 blocks in front of the suit facing away from it (yaw 0 looks +Z), or climb back in. */
    private static void onFoot(Minecraft mc, boolean out) {
        onServer(mc, player -> {
            if (out && player.getVehicle() instanceof PowerArmorEntity suit) {
                parkedSuit = suit;
                player.stopRiding();
                player.teleportTo(suit.getX(), suit.getY(), suit.getZ() + 3);
            } else if (!out && parkedSuit != null) {
                player.startRiding(parkedSuit, true, true);
            }
        });
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

    static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "ap_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
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
        sideCamera = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, mc.level);
        sideCamera.setPos(pos);
        sideCamera.xo = pos.x; sideCamera.yo = pos.y; sideCamera.zo = pos.z;
        sideCamera.setYRot(camYaw); sideCamera.yRotO = camYaw;
        sideCamera.setYHeadRot(camYaw); sideCamera.yHeadRotO = camYaw;
        sideCamera.setXRot(camPitch); sideCamera.xRotO = camPitch;
        mc.setCameraEntity(sideCamera);
    }

    /** A client-only camera 8 blocks to the side of the suit, looking at it. */
    /** A camera ~1.6 blocks out to the front-left of the tool arm, looking at the EM projector: raised (at the muzzle)
     *  or in its hip carry. */
    private static void emCloseCamera(Minecraft mc, boolean raised) {
        if (!(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        float yaw = mc.player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        Vec3 left = new Vec3(net.minecraft.util.Mth.cos(yaw), 0, net.minecraft.util.Mth.sin(yaw));
        Vec3 at = raised ? dev.ajaczero.powerarmor.item.SocketAim.muzzlePosition(suit, mc.player).subtract(forward.scale(0.3))
                : suit.position().add(0, 1.05, 0).add(left.scale(0.8)).add(forward.scale(0.3));
        // First person: in third person the game pulls the view 4 blocks back from the camera entity. The camera
        // entity (an armour stand) sees from its eye height, so stand it (and the aim point, to keep the direction)
        // that much lower. GUI hidden: the hand pass would draw the pilot's cockpit viewmodel over the close-up.
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 eye = at.add(left.scale(1.1)).add(forward.scale(1.1)).add(0, 0.35, 0);
        Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        lookCamera(mc, eye.subtract(drop), at.subtract(drop));
    }

    private static void sideCamera(Minecraft mc) {
        var target = mc.player.getVehicle() != null ? mc.player.getVehicle()
                : mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(8)).stream().findFirst().orElseThrow();
        Vec3 suit = target.position();
        sideCamera = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, mc.level);
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
            var level = player.level();
            DevUtil.setTime(level, 6000);
            level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            // A full stack of blaze rods (~4 hours of walking): enough for any scripted drive.
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 64)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get()));
            suit.snapTo(player.getX() + 0.5, player.getY(), player.getZ() + 0.5, 0, 0);
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
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.gui.screen());
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
        }
    }

    // ---- suit mechanics: smashing through blocks, stomp, lightning, lava + poison (the seal), a punch ----------------

    /** Server-side suit of this scene, and the scene's origin (the suit starts here facing +Z). */
    private static PowerArmorEntity mechSuit;
    private static volatile BlockPos mechBase;
    private static final java.util.List<net.minecraft.world.entity.LivingEntity> mechMobs = new java.util.ArrayList<>();
    private static java.io.BufferedWriter mechCsv;
    private static String mechPhase = "-";

    /** Scene cameras look from an armour stand, but vanilla still paints the player's own fire / in-wall overlays over
     *  them (it only checks "first person"): hide those while a scene camera is active. */
    @SubscribeEvent
    public static void sceneCameraOverlays(net.neoforged.neoforge.client.event.ExtractBlockScreenEffectEvent event) {
        if (ENABLED && Minecraft.getInstance().getCameraEntity() != Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    private static void mechanics(Minecraft mc, int m) {
        var o = mc.options;
        switch (m) {
            // smash: snow layers, a wall of panes, a leaf hedge, then glass + ice that only a sprint gets through
            case 0 -> { releaseAll(); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false);
                        yaw = 0; pitch = 15; mechSetup(mc); }
            case 10 -> { openMechLog(mc); mechPhase = "smash"; hold(o.keyUp, true); }
            case 22, 30, 38 -> shot(mc, "mech_smash_" + m);
            case 44 -> o.setCameraType(CameraType.FIRST_PERSON);   // through the panes from the cockpit
            case 48, 50, 52, 54, 56 -> shot(mc, "mech_smash_fp_" + m);
            case 58 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 62 -> shot(mc, "mech_smash_leaves");
            case 64 -> hold(o.keySprint, true);
            case 68, 72, 78 -> shot(mc, "mech_smash_sprint_" + m);
            case 90 -> { releaseAll(); mc.player.setSprinting(false); }
            case 96 -> { o.setCameraType(CameraType.FIRST_PERSON); }   // pressed against the stone wall: no suffocation, no in-wall overlay
            case 100 -> { shot(mc, "mech_smash_fp_wall"); o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            // stomp: lifted 9 blocks up over a ring of mobs, dropped
            case 104 -> { mechPhase = "stomp"; stompSetup(mc); }
            case 112 -> stompCamera(mc);
            case 114, 118, 122, 124, 126, 128, 130, 132, 136, 142, 150 -> shot(mc, "mech_stomp_" + m);
            // lightning: a real bolt on the suit; it convulses, slumps and restarts
            case 160 -> { mechPhase = "lightning"; onServer(mc, p -> { if (mechSuit != null) {
                              if (CLEAN_ZAP) p.level().getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, p.level().getServer());
                              var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                              bolt.snapTo(mechSuit.position());
                              p.level().addFreshEntity(bolt); } }); }
            case 161, 163, 165, 167 -> { if (CLEAN_ZAP) onServer(mc, p -> { if (mechSuit != null) { mechSuit.clearFire(); p.clearFire(); } }); }
            case 162, 166, 170, 174, 178, 182, 186, 190, 196, 202, 210, 220, 240, 255, 262, 266, 270, 276, 282, 292, 299 -> shot(mc, "mech_zap_" + m);
            case 230 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 232 -> shot(mc, "mech_zap_fp_out");
            case 236 -> stompCamera(mc);
            // a punch with the bare hand: 1 + the melee bonus (a cow has 10 health)
            case 300 -> { mechPhase = "punch"; mc.setCameraEntity(mc.player); DevUtil.hideGui(mc, false); o.setCameraType(CameraType.FIRST_PERSON); punchSetup(mc); }
            case 310 -> { var cow = nearestCow(mc); if (cow != null) { aimAt(mc, cow); } }
            case 320 -> { var cow = nearestCow(mc); if (cow != null) { mc.gameMode.attack(mc.player, cow); mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false); } }
            case 321 -> shot(mc, "mech_punch");
            case 322 -> { giveHeld(mc, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4));
                          onServer(mc, p -> p.getFoodData().setFoodLevel(10)); }   // hungry, bread in hand, no tool: refused
            case 323 -> hold(o.keyUse, true);
            case 328 -> { shot(mc, "mech_eat_refused"); hold(o.keyUse, false); }
            // the seal: wade through lava (hull damage, fire kept out for fuel), get poisoned (kept out)
            case 330 -> { mechPhase = "lava"; o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 0; pitch = 20; lavaSetup(mc); }
            case 336 -> hold(o.keyUp, true);
            case 350, 370, 390 -> shot(mc, "mech_lava_" + m);
            case 400 -> { releaseAll(); onServer(mc, p -> p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200))); }
            case 404 -> o.setCameraType(CameraType.FIRST_PERSON);
            case 410 -> shot(mc, "mech_lava_fp_burning");
            // the machine mind (Phase 8): at night, a fuelled suit with a drill, nobody inside. Pushed 6 blocks from its
            // home it walks back (green visor), then the mind aims at a rock wall and drills it (operator refactor)
            case 420 -> { closeMechLog(); mindSetup(mc); }
            case 425 -> openMindLog(mc);
            case 445 -> { mindPhase = "home"; onServer(mc, p -> { if (mindSuit != null) {         // (the client hears the night once a second)
                              mindSuit.teleportTo(mindBase.getX() + 6.5, mindBase.getY(), mindBase.getZ() + 0.5); } }); }
            case 446 -> mindCamera(mc);
            case 447, 452, 457, 462, 467, 475 -> shot(mc, "ai_home_" + (m - 420));
            case 485 -> { mindPhase = "drill"; onServer(mc, p -> { if (mindSuit != null) {
                              mindSuit.setAiAim(Vec3.atCenterOf(mindBase.offset(0, 1, 3)));
                              mindSuit.setAiTrigger(true); } }); }
            case 490, 496, 504, 512 -> shot(mc, "ai_drill_" + (m - 420));
            case 520 -> { mindPhase = "after"; onServer(mc, p -> { if (mindSuit != null) { mindSuit.setAiTrigger(false); mindSuit.setAiAim(null); } }); }
            case 522 -> mindVisorCamera(mc);
            case 532 -> shot(mc, "ai_visor");
            // chaingun job (step 2): load the hidden store by right-clicking with nuggets, then the suit follows its owner,
            // shoots the zombies that come for them, and punches the last one once the store is empty
            case 534 -> { mindPhase = "load"; mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false);
                          onServer(mc, p -> { if (mindSuit != null) {
                              mindSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.CHAINGUN.get()));
                              p.setGameMode(GameType.SURVIVAL);
                              p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 1200, 4));
                              for (int i = 0; i < 2; i++) {   // two right-clicks with a stack each: 128 in the store
                                  p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 64));
                                  mindSuit.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND, mindSuit.position());
                              }
                              p.setShiftKeyDown(true);          // sneak + right-click: the menu with its new Ammo line
                              mindSuit.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND, mindSuit.position());
                              p.setShiftKeyDown(false); } }); }
            case 542 -> shot(mc, "ai_menu_ammo");
            case 544 -> { mc.player.closeContainer(); mindPhase = "follow";
                          onServer(mc, p -> { if (mindBase != null) {
                              p.teleportTo(mindBase.getX() + 14.5, mindBase.getY(), mindBase.getZ() - 2.5); } }); }
            case 546 -> mindWideCamera(mc);
            case 552, 562, 572, 584 -> shot(mc, "ai_follow_" + (m - 420));
            case 590 -> { mindPhase = "defend"; onServer(mc, p -> {
                              p.level().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // peaceful removes monsters at once
                              for (int i = 0; i < 3; i++) {
                                  var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                                  zombie.snapTo(p.getX() + 3 + 2 * i, p.getY(), p.getZ() + 9 - i, 180, 0);
                                  zombie.setTarget(p);
                                  p.level().addFreshEntity(zombie);
                                  mindMobs.add(zombie);
                              } }); }
            case 593, 597, 601, 606, 612, 620, 630 -> shot(mc, "ai_defend_" + (m - 420));
            case 640 -> { mindPhase = "fists"; onServer(mc, p -> { if (mindSuit != null) {
                              mindSuit.setStoredNuggets(0);
                              var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                              zombie.snapTo(p.getX() + 4, p.getY(), p.getZ() + 6, 180, 0);
                              zombie.setTarget(p);
                              p.level().addFreshEntity(zombie);
                              mindMobs.add(zombie); } }); }
            case 650, 658, 666, 674, 684, 696 -> shot(mc, "ai_fists_" + (m - 420));
            // EM guard job (step 3): the suit goes home with an EM projector; zombies, a skeleton and a creeper wander in
            // round home: each one is caught, lifted overhead and flung ~24 blocks up (the creeper: away from home)
            case 700 -> { mindPhase = "em"; onServer(mc, p -> { if (mindSuit != null && mindBase != null) {
                              mindMobs.forEach(e -> e.discard());
                              mindMobs.clear();
                              p.setGameMode(GameType.CREATIVE);  // monsters ignore a creative player: they just wander near home
                              p.teleportTo(mindBase.getX() - 12.5, mindBase.getY(), mindBase.getZ() - 12.5);
                              mindSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                                      new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.EM_PROJECTOR.get())); } }); }
            case 702 -> mindGuardCamera(mc);
            case 740 -> onServer(mc, p -> { if (mindBase != null) {
                              var level = p.level();
                              EntityType<?>[] types = {net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.SKELETON, net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.CREEPER};
                              double[][] at = {{7, 2}, {-6, 5}, {3, -8}, {-5, -6}};
                              for (int i = 0; i < types.length; i++) {
                                  var mob = (net.minecraft.world.entity.Mob) types[i].create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                                  mob.snapTo(mindBase.getX() + 0.5 + at[i][0], mindBase.getY(), mindBase.getZ() + 0.5 + at[i][1], 0, 0);
                                  mob.setPersistenceRequired();
                                  level.addFreshEntity(mob);
                                  mindMobs.add(mob);
                              } } });
            case 776, 780, 786, 792, 800, 808, 816, 828, 840, 852, 864, 880, 900, 920 -> shot(mc, "ai_em_" + (m - 420));
            case 940 -> closeMindLog();
            default -> {}
        }
        // the drill and chainsaw workers (step 4), then the rogue suits (step 5): their own scene classes; then the run ends
        if (m >= 945 && workRunning && !WorkScene.step(mc, m - 945)) {
            workRunning = false;
            rogueStart = m + 1;
        }
        if (!workRunning && rogueRunning && m >= rogueStart && !RogueScene.step(mc, m - rogueStart)) {
            rogueRunning = false;
            helmetStart = m + 1;
        }
        if (!rogueRunning && helmetRunning && m >= helmetStart && !HelmetScene.step(mc, m - helmetStart)) {
            helmetRunning = false;
            paintStart = m + 1;
        }
        if (!helmetRunning && paintRunning && m >= paintStart && !PaintScene.step(mc, m - paintStart)) {
            paintRunning = false;
            engineStart = m + 1;
        }
        if (!paintRunning && m >= engineStart && !EngineScene.step(mc, m - engineStart)) {
            mc.stop();
        }
        if (m >= 10 && m < 420) {
            logMech(mc, m);
        }
        if (m >= 425 && m < 940) {
            logMind(mc, m);
        }
    }

    // ---- the machine mind scene -----------------------------------------------------------------------------------

    private static boolean workRunning = true;
    private static int rogueStart = Integer.MAX_VALUE;
    private static boolean rogueRunning = true;
    private static int helmetStart = Integer.MAX_VALUE;
    private static boolean helmetRunning = true;
    private static int paintStart = Integer.MAX_VALUE;
    private static boolean paintRunning = true;
    private static int engineStart = Integer.MAX_VALUE;
    private static PowerArmorEntity mindSuit;
    // Filled on the server thread, read by the client thread (logMind): a copy-on-write list (an ArrayList threw mid-iteration).
    private static final java.util.List<net.minecraft.world.entity.Mob> mindMobs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile BlockPos mindBase;
    private static java.io.BufferedWriter mindCsv;
    private static String mindPhase = "-";

    /** Server: night; the player gets out (in the lava pool) and stands 5 blocks off to the side; a fuelled suit with a drill
     *  stands at its home 40 blocks further east, a 3x3 stone wall 3 blocks ahead of it. */
    private static void mindSetup(Minecraft mc) {
        onServer(mc, player -> {
            var level = player.level();
            DevUtil.setTime(level, 18000);
            if (player.getVehicle() != null) {
                player.stopRiding();
            }
            player.clearFire();
            player.removeAllEffects();
            int x = (mechBase != null ? mechBase.getX() : player.getBlockX()) + 40, z = mechBase != null ? mechBase.getZ() : player.getBlockZ();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos base = new BlockPos(x, y, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(base.offset(dx, dy, 3), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
            }
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 16)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.DRILL.get()));
            suit.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            suit.setYBodyRot(0);
            suit.setYHeadRot(0);
            suit.setOwnerId(player.getUUID());
            suit.setHome(base);
            level.addFreshEntity(suit);
            player.teleportTo(x - 5.5, y, z - 3.5);
            mindSuit = suit;
            mindBase = base;
        });
    }

    /** Side view of the walk home: 7 blocks south-west of home, the start 6 blocks east of it in the frame. GUI hidden: with
     *  a scripted camera entity in first person the hand pass would draw the player's hand. */
    private static void mindCamera(Minecraft mc) {
        BlockPos b = mindBase;
        if (b != null) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            DevUtil.hideGui(mc, true);
            Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0); // lookCamera places the stand's feet
            lookCamera(mc, Vec3.atBottomCenterOf(b).add(3, 2.6, -6.5).subtract(drop), Vec3.atBottomCenterOf(b).add(3, 1.6, 1).subtract(drop));
        }
    }

    /** Wide side view of the follow / defend scene: from the south, 12 blocks out, centred between home and the owner. */
    private static void mindWideCamera(Minecraft mc) {
        BlockPos b = mindBase;
        if (b != null) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            DevUtil.hideGui(mc, true);
            Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
            lookCamera(mc, Vec3.atBottomCenterOf(b).add(9, 4.5, -14).subtract(drop), Vec3.atBottomCenterOf(b).add(11, 1.2, 1).subtract(drop));
        }
    }

    /** The EM guard scene: 20 blocks south of home and 8 up, looking at a point 9 above home, so the flung mobs stay in
     *  the frame. */
    private static void mindGuardCamera(Minecraft mc) {
        BlockPos b = mindBase;
        if (b != null) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            DevUtil.hideGui(mc, true);
            Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
            lookCamera(mc, Vec3.atBottomCenterOf(b).add(-3, 8, -24).subtract(drop), Vec3.atBottomCenterOf(b).add(0, 9, 0).subtract(drop));
        }
    }

    /** Close-up of the mind's suit from 3 blocks in front of its face (the client copy: where this client draws it). */
    private static void mindVisorCamera(Minecraft mc) {
        if (mindSuit != null && mc.level.getEntity(mindSuit.getId()) instanceof PowerArmorEntity suit) {
            float yaw = suit.getYHeadRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
            Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0); // lookCamera places the stand's feet
            Vec3 face = suit.position().add(0, 2.5, 0);
            lookCamera(mc, face.add(forward.scale(3.2)).add(forward.yRot(1.5708F).scale(0.8)).subtract(drop), face.subtract(drop));
        }
    }

    private static void openMindLog(Minecraft mc) {
        try {
            mindCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_mind.csv"));
            mindCsv.write("tick,phase,dxHome,dzHome,speed,driven,anim,headYaw,bodyYaw,xRot,toolActive,clientRaise,faceLeft,drops,energy,visorR,visorG,visorB,navDone,goals,ownerDist,target,store,shots,mobsAlive,mobHealth,beam,mobTopY,clientHead,clientBody,twist,drawnYaw,aimYaw,aimErr,mobs\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** One row per tick: the server's suit (position, mind, tool, fuel) and the client's copy (animation, arm, visor). */
    private static void logMind(Minecraft mc, int m) {
        PowerArmorEntity server = mindSuit;
        BlockPos b = mindBase;
        if (mindCsv == null || server == null || b == null || !(mc.level.getEntity(server.getId()) instanceof PowerArmorEntity client)) {
            return;
        }
        int faceLeft = 0;
        for (BlockPos p : BlockPos.betweenClosed(b.offset(-1, 0, 3), b.offset(1, 2, 3))) {
            faceLeft += mc.level.getBlockState(p).isAir() ? 0 : 1;
        }
        int drops = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(b).inflate(8)).size();
        float[] visor = client.getVisorColor();
        // What this client draws: the torso twists by (head - legs), clamped to +-90 (PowerArmorRenderer.aim), so the
        // torso faces drawnYaw. aimErr: how far that is from where the mind aims (its target point), degrees.
        float twist = net.minecraft.util.Mth.wrapDegrees(client.getYHeadRot() - client.yBodyRot);
        float drawnYaw = client.yBodyRot + net.minecraft.util.Mth.clamp(twist, -90F, 90F);
        Vec3 aim = server.getAiAim();
        float aimYaw = aim == null ? Float.NaN : (float) (net.minecraft.util.Mth.atan2(aim.z - server.getZ(), aim.x - server.getX()) * net.minecraft.util.Mth.RAD_TO_DEG) - 90F;
        float aimErr = aim == null ? Float.NaN : net.minecraft.util.Mth.wrapDegrees(aimYaw - drawnYaw);
        try {
            mindCsv.write(String.format(java.util.Locale.ROOT, "%d,%s,%.2f,%.2f,%.2f,%b,%s,%.1f,%.1f,%.1f,%b,%.2f,%d,%d,%d,%.2f,%.2f,%.2f,%b,%s,%.2f,%s,%d,%d,%d,%.1f,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%s%n",
                    m, mindPhase, server.getX() - (b.getX() + 0.5), server.getZ() - (b.getZ() + 0.5),
                    Math.hypot(server.getX() - server.xo, server.getZ() - server.zo) * 20, server.isAiDriven(), client.getMovementAnimationName(),
                    server.getYHeadRot(), server.yBodyRot, server.getXRot(), server.isToolActive(), client.getToolRaise(1F), faceLeft, drops,
                    server.getTotalEnergy(), visor[0], visor[1], visor[2], server.getNavigation().isDone(),
                    server.runningGoalNames(), server.getOwner() == null ? -1F : server.distanceTo(server.getOwner()),
                    server.getTarget() == null ? "-" : server.getTarget().getType().toShortString(), server.getStoredNuggets(), server.getToolUses(),
                    mindMobs.stream().filter(e -> e.isAlive()).count(), mindMobs.stream().filter(e -> e.isAlive()).mapToDouble(e -> e.getHealth()).sum(),
                    server.getBeamTarget() == null ? "-" : server.getBeamTarget().getType().toShortString(),
                    mindMobs.stream().filter(e -> e.isAlive()).mapToDouble(e -> e.getY() - b.getY()).max().orElse(0),
                    client.getYHeadRot(), client.yBodyRot, twist, drawnYaw, aimYaw, aimErr,
                    mindMobs.stream().map(e -> e.getType().toShortString() + ":" + (e.isAlive() ? String.format(java.util.Locale.ROOT, "%.0f@%.1f",
                                    e.getHealth(), Math.sqrt(e.distanceToSqr(Vec3.atBottomCenterOf(b)))) : "dead"))
                            .reduce((x, y) -> x + "/" + y).orElse("")));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] mind csv", e);
        }
    }

    private static void closeMindLog() {
        try {
            if (mindCsv != null) {
                mindCsv.close();
            }
        } catch (IOException ignored) {
        }
    }

    /** A fixed camera 7 blocks to the side of the stomp spot (30 blocks into the scene), looking at it. First person with
     *  the GUI hidden: a third-person camera would pull 4 blocks back, and the hand pass would draw the cockpit viewmodel. */
    private static void stompCamera(Minecraft mc) {
        BlockPos b = mechBase;
        if (b == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        lookCamera(mc, Vec3.atBottomCenterOf(b).add(8, 2.2, 30).subtract(drop), Vec3.atBottomCenterOf(b).add(0, 1.4, 30).subtract(drop));
    }

    private static net.minecraft.world.entity.animal.cow.Cow nearestCow(Minecraft mc) {
        return mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, mc.player.getBoundingBox().inflate(8)).stream()
                .min(Comparator.comparingDouble(c -> c.distanceToSqr(mc.player))).orElse(null);
    }

    /** Server: a fresh suit 120 blocks east of the start on flat ground, by day; the scene's blocks ahead (+Z). */
    private static void mechSetup(Minecraft mc) {
        onServer(mc, player -> {
            var level = player.level();
            DevUtil.setTime(level, 6000);
            player.setGameMode(GameType.SURVIVAL);
            if (player.getVehicle() != null) {
                player.stopRiding();
            }
            int x = player.getBlockX() + 120, z = player.getBlockZ();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos base = new BlockPos(x, y, z);
            mechBase = base;
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 32)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get()));
            suit.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            suit.setYBodyRot(0);
            level.addFreshEntity(suit);
            mechSuit = suit;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.teleportTo(x + 0.5, y, z - 1.5);
            player.startRiding(suit, true, true);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = 2; dz <= 4; dz++) {
                    level.setBlock(base.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState(), 3);
                }
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(base.offset(dx, dy, 6), net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState(), 3);
                }
                for (int dy = 0; dy <= 2; dy++) { // persistent leaves (like a hedge someone planted): they don't decay
                    level.setBlock(base.offset(dx, dy, 9), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 3);
                }
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(base.offset(dx, dy, 13), (dy < 2 ? net.minecraft.world.level.block.Blocks.GLASS
                            : net.minecraft.world.level.block.Blocks.ICE).defaultBlockState(), 3);
                }
                // a stone wall the suit can't get through, to end the run
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlock(base.offset(dx, dy, 20), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
        });
    }

    /** Server: lift the suit (and pilot) 9 blocks over a spot 30 blocks further on, with mobs around it: two pigs and a
     *  villager within the shock wave, a cow outside it. */
    private static void stompSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (mechSuit == null || mechBase == null) {
                return;
            }
            var level = player.level();
            BlockPos spot = mechBase.offset(0, 0, 30);
            Vec3 c = Vec3.atBottomCenterOf(spot);
            mechMobs.clear();
            Object[][] mobs = {{net.minecraft.world.entity.EntityTypes.PIG, 2.0, 0.0}, {net.minecraft.world.entity.EntityTypes.PIG, -1.5, 1.5}, {net.minecraft.world.entity.EntityTypes.VILLAGER, 0.0, -2.8}, {net.minecraft.world.entity.EntityTypes.COW, -6.0, 0.0}};
            for (Object[] mob : mobs) {
                var e = ((EntityType<?>) mob[0]).create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                e.snapTo(c.x + (double) mob[1], spot.getY(), c.z + (double) mob[2], 0, 0);
                level.addFreshEntity(e);
                mechMobs.add((net.minecraft.world.entity.LivingEntity) e);
            }
            player.stopRiding();
            mechSuit.snapTo(c.x, spot.getY() + 9, c.z, 0, 0);
            mechSuit.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(c.x, spot.getY() + 9, c.z);
            player.startRiding(mechSuit, true, true);
        });
    }

    /** Server: a 3x4 lava puddle (one deep) 2 blocks ahead of the suit, and a stone floor beyond to walk out onto. */
    private static void lavaSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (mechSuit == null) {
                return;
            }
            var level = player.level();
            BlockPos at = mechSuit.blockPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = 2; dz <= 5; dz++) {
                    level.setBlock(at.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), 3);
                }
            }
        });
    }

    /** Server: a cow 3 blocks to the side (10 health: one bare-handed punch at 1 + 4 takes half). */
    private static void punchSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (mechSuit == null) {
                return;
            }
            var level = player.level();
            var cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            cow.snapTo(mechSuit.getX() + 3, mechSuit.getY(), mechSuit.getZ() + 1, 90, 0); // off to the left, clear of the lava to come
            cow.setNoAi(true);
            level.addFreshEntity(cow);
            mechMobs.add(cow);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        });
    }

    private static void openMechLog(Minecraft mc) {
        try {
            mechCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_mech.csv"));
            mechCsv.write("tick,phase,dz,y,speed,onGround,sprint,anim,powered,stunned,canOperate,suitHealth,suitFire,pilotHealth,pilotFire,"
                    + "pilotPoison,air,energy,panes,leaves,glass,ice,snow,drops,victims,mobs,pilotFireTicks,clientPilotFire,suitLastDamage,pilotDamage,strength,eating,food,clientSuitFire,actionAnim\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** One row per tick: the client's suit (movement, animation), the server's suit and mobs (singleplayer: same JVM). */
    private static void logMech(Minecraft mc, int m) {
        if (mechCsv == null || mechSuit == null || mechBase == null || !(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        int panes = 0, leaves = 0, glass = 0, ice = 0, snow = 0;
        for (BlockPos p : BlockPos.betweenClosed(mechBase.offset(-2, 0, 0), mechBase.offset(2, 3, 14))) {
            var state = mc.level.getBlockState(p);
            panes += state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE) ? 1 : 0;
            leaves += state.is(net.minecraft.world.level.block.Blocks.OAK_LEAVES) ? 1 : 0;
            glass += state.is(net.minecraft.world.level.block.Blocks.GLASS) ? 1 : 0;
            ice += state.is(net.minecraft.world.level.block.Blocks.ICE) ? 1 : 0;
            snow += state.is(net.minecraft.world.level.block.Blocks.SNOW) ? 1 : 0;
        }
        int drops = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(mechBase).inflate(20)).size();
        String mobs = mechMobs.stream().map(e -> e.getType().toShortString() + ":" + (e.isAlive() ? String.format(java.util.Locale.ROOT, "%.1f", e.getHealth()) : "dead"))
                .reduce((a, b) -> a + "/" + b).orElse("");
        var server = mechSuit;
        var pilot = server.getControllingPassenger();
        try {
            var last = server.getLastDamageSource();
            mechCsv.write(String.format(java.util.Locale.ROOT, "%d,%s,%.2f,%.2f,%.2f,%b,%b,%s,%b,%b,%b,%.1f,%b,%.1f,%b,%b,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%b,%s,%.1f,%.2f,%b,%d,%b,%s%n",
                    m, mechPhase, suit.getZ() - mechBase.getZ(), suit.getY() - mechBase.getY(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20,
                    suit.onGround(), mc.player.isSprinting(), suit.getMovementAnimationName(), server.isPowered(), server.isStunned(), server.canOperate(),
                    server.getHealth(), server.isOnFire(), pilot == null ? -1F : pilot.getHealth(), pilot != null && pilot.isOnFire(),
                    pilot != null && pilot.hasEffect(net.minecraft.world.effect.MobEffects.POISON), pilot == null ? -1 : pilot.getAirSupply(),
                    server.getTotalEnergy(), panes, leaves, glass, ice, snow, drops, server.lastStompVictims, mobs,
                    pilot == null ? -1 : pilot.getRemainingFireTicks(), mc.player.isOnFire(), last == null ? "-" : last.getMsgId(),
                    pilot instanceof net.minecraft.world.entity.player.Player p ? p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : -1D, // -1D: an int -1 is boxed as Integer (%f crashed)
                    pilot instanceof net.minecraft.world.entity.player.Player p2 ? p2.getAttackStrengthScale(0.5F) : -1F,
                    mc.player.isUsingItem(), pilot instanceof net.minecraft.world.entity.player.Player p3 ? p3.getFoodData().getFoodLevel() : -1, suit.isOnFire(), actionAnim(suit)));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] mech csv", e);
        }
    }

    /** What the client's "action" controller (hit flinch, tool recoil) is playing, or "-". */
    private static String actionAnim(PowerArmorEntity suit) {
        var manager = suit.getAnimatableInstanceCache().getManagerForId(suit.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("action");
        if (controller == null || controller.getCurrentAnimationPoint() == null
                || controller.getPlayState() == com.geckolib.animation.object.PlayState.STOP) {
            return "-";
        }
        return controller.getCurrentAnimationPoint().animation().name().replace("animation.power_armor.", "");
    }

    private static void closeMechLog() {
        try {
            if (mechCsv != null) {
                mechCsv.close();
                mechCsv = null; // the game takes another tick or two to quit
            }
        } catch (IOException ignored) {
        }
    }

    private AutoPilot() {}
}
