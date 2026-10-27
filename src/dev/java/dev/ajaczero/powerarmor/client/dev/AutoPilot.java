package dev.ajaczero.powerarmor.client.dev;

import com.geckolib.animation.object.PlayState;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.client.CockpitHands;
import dev.ajaczero.powerarmor.client.SuitSounds;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockScreenEffectEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jetbrains.annotations.Nullable;

/**
 * DEV TOOL, inert unless started with -Dpowerarmor.autopilot (./gradlew runAutopilot).
 * Creates a flat creative world, climbs into a suit, drives a scripted sequence (walk, sprint, look
 * around, jump), saves screenshots to run/screenshots/ap_*.png, then quits. Lets us check visuals
 * (cockpit view, torso aiming, jump timing) without a human at the keyboard.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class AutoPilot {
    private static final boolean ENABLED = System.getProperty("powerarmor.autopilot") != null && !TerrainPilot.enabled() && !ShowcaseScene.enabled();
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
    /** "flame" mode: only the flamethrower scene (FlameScene: pigs, a plank wall, beef, ice, a campfire), ~30 s. */
    private static final boolean FLAME_ONLY = "flame".equals(System.getProperty("powerarmor.autopilot"));
    /** "tnt" mode: only the TNT launcher scene (TntScene: a wall, husks, the drum, the ammo slot), ~35 s. */
    private static final boolean TNT_ONLY = "tnt".equals(System.getProperty("powerarmor.autopilot"));
    /** "rail" mode: only the rail cannon scene (RailScene: glass, husks in a line, a wall, the mind's guard, the ammo slot), ~35 s. */
    private static final boolean RAIL_ONLY = "rail".equals(System.getProperty("powerarmor.autopilot"));
    /** "hud" mode: only the pilot HUD scene (HudScene: every state the HUD shows, each tool, no helmet), ~35 s. */
    private static final boolean HUD_ONLY = "hud".equals(System.getProperty("powerarmor.autopilot"));
    // The main script's sections, each starting where the one before ends (its length is the difference). Case labels
    // in script() count from their section's start, so a step added to one section only changes that section's length
    // here; every later section (and the mech scenes, MECH_START) moves along by itself. Screenshot names keep the
    // absolute tick (t).
    private static final int BASICS = 0;                    // walk, sprint, look round, jump (cockpit and third person)
    private static final int WATER = BASICS + 350;
    private static final int FUEL = WATER + 300;
    private static final int CHAINGUN = FUEL + 250;
    private static final int COCKPIT_LOOK = CHAINGUN + 86;
    private static final int RIGHT_HAND = COCKPIT_LOOK + 9;
    private static final int EM = RIGHT_HAND + 100;
    private static final int MINING = EM + 115;
    private static final int ITEM_ON_FOOT = MINING + 130;
    private static final int LIGHTS = ITEM_ON_FOOT + 60;
    private static final int MECH_START = LIGHTS + 312;   // then mechanics(): smashing, stomp, lightning, the minds, ...
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
        } else if (FLAME_ONLY) {
            if (!FlameScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (TNT_ONLY) {
            if (!TntScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (RAIL_ONLY) {
            if (!RailScene.step(mc, tick++)) {
                mc.stop();
            }
        } else if (HUD_ONLY) {
            if (!HudScene.step(mc, tick++)) {
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
            case BASICS -> { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.FIRST_PERSON); }
            case BASICS + 10 -> shot(mc, "fp_idle");
            case BASICS + 15 -> hold(o.keyUp, true);
            case BASICS + 40, BASICS + 41, BASICS + 42, BASICS + 43 -> { shot(mc, "fp_walk_" + t); logSpeed(mc, "walk"); }
            case BASICS + 55 -> hold(o.keySprint, true);
            case BASICS + 75, BASICS + 76, BASICS + 77 -> { shot(mc, "fp_run_" + t); logSpeed(mc, "run"); }
            case BASICS + 85 -> releaseAll();
            case BASICS + 90 -> yaw = 60;
            case BASICS + 92 -> shot(mc, "fp_look_right_now");
            case BASICS + 110 -> shot(mc, "fp_look_right_later");
            case BASICS + 115 -> pitch = 35;
            case BASICS + 120 -> shot(mc, "fp_look_down");
            case BASICS + 122 -> pitch = -30;
            case BASICS + 127 -> shot(mc, "fp_look_up");
            case BASICS + 130 -> { pitch = 10; yaw = 0; o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case BASICS + 177 -> shot(mc, "tp_idle");
            case BASICS + 142 -> yaw = 70;
            case BASICS + 144 -> shot(mc, "tp_twist_now");
            case BASICS + 170 -> shot(mc, "tp_twist_settled");
            case BASICS + 175 -> { yaw = 0; hold(o.keyUp, true); }
            case BASICS + 200 -> shot(mc, "tp_walk");
            case BASICS + 215 -> { hold(o.keyUp, false); hold(o.keyRight, true); }
            case BASICS + 235 -> shot(mc, "tp_strafe_right");
            case BASICS + 240 -> { releaseAll(); hold(o.keyUp, true); hold(o.keySprint, true); }
            case BASICS + 262 -> shot(mc, "tp_run");
            case BASICS + 270 -> releaseAll();
            case BASICS + 280 -> sideCamera(mc);
            case BASICS + 285 -> hold(o.keyJump, true);
            case BASICS + 288, BASICS + 294 -> shot(mc, "side_charge_" + t);
            case BASICS + 298 -> hold(o.keyJump, false);
            case BASICS + 299, BASICS + 300, BASICS + 301, BASICS + 302, BASICS + 304, BASICS + 306, BASICS + 310, BASICS + 316, BASICS + 324, BASICS + 334, BASICS + 342 -> shot(mc, "side_jump_" + t);
            // ---- water: walk off a lip into a 6-deep pool, along the bottom, up stairs on the far side
            case WATER -> { mc.setCameraEntity(mc.player); releaseAll(); yaw = 0; pitch = 15; o.setCameraType(CameraType.THIRD_PERSON_BACK); buildPool(mc); }
            case WATER + 10 -> { openWaterLog(mc); hold(o.keyUp, true); }
            case WATER + 35, WATER + 50, WATER + 65, WATER + 80 -> shot(mc, "water_tp_" + t);
            case WATER + 90 -> { o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case WATER + 100, WATER + 120 -> shot(mc, "water_fp_" + t);
            case WATER + 130 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case WATER + 150, WATER + 170, WATER + 190, WATER + 210, WATER + 240 -> shot(mc, "water_tp_" + t);
            case WATER + 290 -> { releaseAll(); closeWaterLog(); survival(mc, false);
                          onServer(mc, p -> { if (p.getVehicle() instanceof PowerArmorEntity s) { // the firebox back in (see buildPool)
                              s.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(ModItems.FIREBOX.get()));
                              DevUtil.firebox(s); s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 16)); } }); }
            // ---- fuel: nearly empty tank, walk until the engine dies, refuel through the menu
            case FUEL -> { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 0; pitch = 15; fuelScenarioSetup(mc); }
            case FUEL + 10 -> { openFuelLog(mc); hold(o.keyUp, true); }
            case FUEL + 25 -> shot(mc, "fuel_tp_low");
            case FUEL + 40 -> o.setCameraType(CameraType.FIRST_PERSON);
            case FUEL + 50 -> shot(mc, "fuel_fp_hud");
            case FUEL + 62 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case FUEL + 70, FUEL + 74 -> shot(mc, "fuel_tp_sputter_" + t);
            case FUEL + 82 -> sideCamera(mc);
            // the engine dies at ~ t=738 (80 energy, walking burns 1 per tick); the slump plays for 1.6 s
            case FUEL + 86, FUEL + 92, FUEL + 100, FUEL + 110, FUEL + 135 -> shot(mc, "fuel_side_shutdown_" + t);
            case FUEL + 140 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); }
            case FUEL + 145 -> shot(mc, "fuel_fp_out_of_fuel");
            case FUEL + 150 -> { releaseAll(); hold(o.keyShift, true); }      // climb out
            case FUEL + 155 -> hold(o.keyShift, false);
            case FUEL + 165 -> openSuitMenu(mc);                             // sneak + right-click on the server side
            case FUEL + 175 -> shot(mc, "menu_empty");
            case FUEL + 180 -> refuel(mc);
            case FUEL + 186 -> shot(mc, "menu_fueled");
            case FUEL + 195 -> { mc.player.closeContainer(); sideCamera(mc); }
            case FUEL + 200, FUEL + 208, FUEL + 216, FUEL + 224, FUEL + 240 -> shot(mc, "fuel_side_startup_" + t);
            // ---- chaingun: climb back in, hold the trigger (spin-up to a barrage) at three pigs 12 blocks ahead and a wall
            case CHAINGUN -> { closeFuelLog(); mc.setCameraEntity(mc.player); yaw = 0; pitch = 9; cannonRangeSetup(mc); giveHeld(mc, ItemStack.EMPTY); } // empty hand: right click goes to the tool
            case CHAINGUN + 10 -> { o.setCameraType(CameraType.FIRST_PERSON); openCannonLog(mc); }
            case CHAINGUN + 20 -> hold(o.keyUse, true);
            case CHAINGUN + 21, CHAINGUN + 22, CHAINGUN + 23, CHAINGUN + 24, CHAINGUN + 25, CHAINGUN + 26, CHAINGUN + 27, CHAINGUN + 28, CHAINGUN + 29, CHAINGUN + 30, CHAINGUN + 31, CHAINGUN + 32 -> shot(mc, "cannon_fp_" + t);
            case CHAINGUN + 40 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 12; }
            case CHAINGUN + 45, CHAINGUN + 46, CHAINGUN + 47, CHAINGUN + 49 -> shot(mc, "cannon_tp_" + t);
            case CHAINGUN + 58 -> { yaw = -12; sideCamera(mc); }
            case CHAINGUN + 69, CHAINGUN + 70, CHAINGUN + 71, CHAINGUN + 73, CHAINGUN + 76 -> shot(mc, "cannon_side_" + t);
            case CHAINGUN + 78 -> hold(o.keyUse, false);                    // let go: the tool arm lowers to its hip carry
            case CHAINGUN + 84 -> shot(mc, "cannon_side_lowered");
            case CHAINGUN + 85 -> releaseAll();
            // ---- cockpit while walking and looking down (cannon still installed): both arms and the feet in view,
            //      the tool must not clip into the upper arm
            case COCKPIT_LOOK -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 55; hold(o.keyUp, true); }
            case COCKPIT_LOOK + 3, COCKPIT_LOOK + 4, COCKPIT_LOOK + 5, COCKPIT_LOOK + 6, COCKPIT_LOOK + 7 -> shot(mc, "fp_walkdown_" + t);
            case COCKPIT_LOOK + 8 -> releaseAll();
            // ---- right hand: hold and place stone bricks from inside, then eat a golden apple
            case RIGHT_HAND -> { closeCannonLog(); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON);
                          giveHeld(mc, new ItemStack(Items.STONE_BRICKS, 64)); yaw = 0; pitch = 40; }
            case RIGHT_HAND + 8 -> shot(mc, "hand_fp_hold");
            case RIGHT_HAND + 10, RIGHT_HAND + 31, RIGHT_HAND + 47 -> hold(o.keyUse, true);
            case RIGHT_HAND + 11, RIGHT_HAND + 12, RIGHT_HAND + 13, RIGHT_HAND + 14, RIGHT_HAND + 15, RIGHT_HAND + 16 -> shot(mc, "hand_fp_use_" + t);
            case RIGHT_HAND + 21 -> { hold(o.keyUse, false); o.setCameraType(CameraType.THIRD_PERSON_BACK); pitch = 25; }
            case RIGHT_HAND + 29 -> shot(mc, "hand_tp_hold");
            case RIGHT_HAND + 32, RIGHT_HAND + 33, RIGHT_HAND + 34, RIGHT_HAND + 36 -> shot(mc, "hand_tp_use_" + t);
            case RIGHT_HAND + 39 -> { hold(o.keyUse, false); yaw = -90; pitch = 20;
                           giveHeld(mc, new ItemStack(Items.TORCH, 16)); }
            case RIGHT_HAND + 43 -> frontCamera(mc);
            case RIGHT_HAND + 45 -> shot(mc, "hand_front_hold");
            case RIGHT_HAND + 48, RIGHT_HAND + 49, RIGHT_HAND + 50, RIGHT_HAND + 52 -> shot(mc, "hand_front_use_" + t);
            case RIGHT_HAND + 55 -> { hold(o.keyUse, false); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 10;
                           giveHeld(mc, new ItemStack(Items.GOLDEN_APPLE, 4));
                           socket(mc, null); } // no tool to pass the click on to: the "sealed in" message shows
            case RIGHT_HAND + 60 -> hold(o.keyUse, true);  // sealed in: eating is refused
            case RIGHT_HAND + 67, RIGHT_HAND + 80 -> shot(mc, "hand_fp_food_refused_" + t);
            case RIGHT_HAND + 83 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case RIGHT_HAND + 90 -> shot(mc, "hand_tp_food_refused");
            case RIGHT_HAND + 91 -> { releaseAll(); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 40;   // left click: the hand mines the bricks it placed
                           giveHeld(mc, new ItemStack(Items.DIAMOND_PICKAXE)); }
            case RIGHT_HAND + 92 -> hold(o.keyAttack, true);
            case RIGHT_HAND + 94, RIGHT_HAND + 96 -> shot(mc, "hand_fp_mine_" + t);
            case RIGHT_HAND + 97 -> releaseAll();
            // ---- EM projector: vacuum up dropped diamonds, then grab a pig, hold it and throw it
            case EM -> { giveHeld(mc, ItemStack.EMPTY); emRangeSetup(mc); yaw = 180; pitch = 15; o.setCameraType(CameraType.THIRD_PERSON_BACK); openEmLog(mc); }
            case EM + 9 -> emCloseCamera(mc, false);   // close-up of the mounted projector: faint idle glow
            case EM + 12 -> shot(mc, "em_close_idle");
            case EM + 14 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false); }
            case EM + 15 -> hold(o.keyUse, true);
            case EM + 18, EM + 23, EM + 29 -> shot(mc, "em_tp_items_" + t);
            case EM + 26 -> pitch = 10; // the next diamonds lie further out
            case EM + 33 -> o.setCameraType(CameraType.FIRST_PERSON);
            case EM + 37 -> shot(mc, "em_fp_items");
            case EM + 45 -> hold(o.keyUse, false);
            case EM + 51, EM + 54 -> aimAt(mc, emPig);
            case EM + 55 -> hold(o.keyUse, true);
            case EM + 61, EM + 69 -> shot(mc, "em_fp_pig_" + t);
            case EM + 71 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case EM + 72 -> emCloseCamera(mc, true);    // close-up while the beam holds the pig: core + tips lit, sparks
            case EM + 74 -> shot(mc, "em_close_pig");
            case EM + 75 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false); }
            case EM + 77 -> shot(mc, "em_tp_pig");
            case EM + 79 -> sideCamera(mc);
            case EM + 85 -> shot(mc, "em_side_pig");
            case EM + 87 -> pitch = -5;
            case EM + 91 -> hold(o.keyUse, false);
            case EM + 92, EM + 94, EM + 97, EM + 101 -> shot(mc, "em_side_throw_" + t);
            case EM + 110 -> { closeEmLog(); mc.setCameraEntity(mc.player); releaseAll(); }
            // ---- mining tools: drill a 3x3 into a rock face with ores, then fell a real oak with the chainsaw
            case MINING -> { giveHeld(mc, ItemStack.EMPTY); miningSetup(mc); yaw = 0; pitch = 20; o.setCameraType(CameraType.THIRD_PERSON_BACK); openMineLog(mc); }
            case MINING + 15 -> aimTarget = Vec3.atCenterOf(mineBase.offset(0, 1, 4)).add(0, 0, -0.5); // re-aimed every tick (the eye moves as the suit turns)
            case MINING + 18 -> hold(o.keyUse, true);
            case MINING + 20, MINING + 24, MINING + 28, MINING + 32 -> shot(mc, "drill_tp_" + t);
            case MINING + 36 -> o.setCameraType(CameraType.FIRST_PERSON);
            case MINING + 40, MINING + 44, MINING + 48 -> shot(mc, "drill_fp_" + t);
            case MINING + 52 -> lookCamera(mc, Vec3.atBottomCenterOf(mineBase).add(-4.5, 2.0, 3.0), Vec3.atBottomCenterOf(mineBase).add(-0.5, 1.9, 1.8));
            case MINING + 54, MINING + 56, MINING + 58 -> shot(mc, "drill_side_" + t);
            case MINING + 62 -> { aimTarget = null; hold(o.keyUse, false); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case MINING + 68 -> shot(mc, "drill_tp_after");
            case MINING + 70 -> treeSetup(mc);
            case MINING + 74 -> aimTarget = Vec3.atCenterOf(treeBase).add(-0.5, 0, 0);
            case MINING + 76 -> { hold(o.keyUse, true); o.setCameraType(CameraType.FIRST_PERSON); }
            case MINING + 79 -> shot(mc, "saw_fp_" + t);
            case MINING + 80 -> lookCamera(mc, Vec3.atBottomCenterOf(mineBase).add(-3.5, 3.0, -3.5), Vec3.atBottomCenterOf(mineBase).add(2.0, 1.6, 0.6)); // behind-left, clear of the rock and the canopy
            case MINING + 81, MINING + 83, MINING + 84, MINING + 85, MINING + 86, MINING + 87, MINING + 88, MINING + 90, MINING + 94 -> shot(mc, "saw_side_" + t);
            case MINING + 96 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); }
            case MINING + 100 -> shot(mc, "saw_fp_" + t);
            case MINING + 102 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case MINING + 114 -> { aimTarget = null; hold(o.keyUse, false); mc.setCameraEntity(mc.player); }
            case MINING + 120 -> { shot(mc, "saw_tp_after"); closeMineLog(); }
            // ---- the cannon item on foot: hand (first / third person) and inventory icon
            case ITEM_ON_FOOT -> { o.setCameraType(CameraType.FIRST_PERSON); hold(o.keyShift, true); }
            case ITEM_ON_FOOT + 5 -> { hold(o.keyShift, false); giveCannon(mc); survival(mc, true); yaw = 120; pitch = 10; }
            case ITEM_ON_FOOT + 15 -> shot(mc, "item_fp");
            case ITEM_ON_FOOT + 17 -> o.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case ITEM_ON_FOOT + 25 -> shot(mc, "item_tp_front");
            case ITEM_ON_FOOT + 27 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case ITEM_ON_FOOT + 35 -> shot(mc, "item_tp_back");
            case ITEM_ON_FOOT + 37 -> { o.setCameraType(CameraType.FIRST_PERSON); mc.gui.setScreen(new InventoryScreen(mc.player)); }
            case ITEM_ON_FOOT + 45 -> shot(mc, "item_gui");
            case ITEM_ON_FOOT + 47 -> mc.gui.setScreen(null);
            case ITEM_ON_FOOT + 50 -> mc.gui.setScreen(new ConfigurationScreen(
                    ModList.get().getModContainerById(PowerArmor.MODID).orElseThrow(), null));
            case ITEM_ON_FOOT + 56 -> shot(mc, "config");
            case ITEM_ON_FOOT + 58 -> mc.gui.setScreen(null);
            // ---- lights at night (SuitGlowLayer): piloted = amber (also with the tool and when hit), empty = the machine
            //      mind's green, then the fuel runs out: the visor stutters and goes dark, the firebox dies down to embers
            case LIGHTS -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); lightsSetup(mc); }
            case LIGHTS + 30 -> { yaw = 0; pitch = 0; boardNearestSuit(mc); } // (the client learns the new time once a second)
            case LIGHTS + 40 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); lightsCamera(mc); }
            case LIGHTS + 46 -> shot(mc, "lights_piloted");
            case LIGHTS + 48 -> hold(o.keyUse, true);                    // a long barrage into the wall ahead: tracers, hot barrels
            case LIGHTS + 54 -> shot(mc, "lights_tool");
            case LIGHTS + 100, LIGHTS + 160, LIGHTS + 196 -> shot(mc, "lights_barrage_" + t);
            case LIGHTS + 200 -> hold(o.keyUse, false);
            case LIGHTS + 208 -> shot(mc, "lights_hot");                   // barrels still glowing as they coast down
            case LIGHTS + 220 -> onServer(mc, p -> { if (p.getVehicle() instanceof PowerArmorEntity s) s.hurtServer(p.level(), p.level().damageSources().generic(), 1F); });
            case LIGHTS + 224 -> shot(mc, "lights_hit");
            case LIGHTS + 230 -> hold(o.keyShift, true);                  // climb out: the machine mind takes over -> green
            case LIGHTS + 232 -> hold(o.keyShift, false);
            case LIGHTS + 244 -> shot(mc, "lights_parked");
            case LIGHTS + 246 -> boardNearestSuit(mc);                  // back in, to watch it run dry from the cockpit
            case LIGHTS + 250 -> onServer(mc, p -> p.level().getEntitiesOfClass(PowerArmorEntity.class, p.getBoundingBox().inflate(8)).forEach(s -> {
                              s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY); s.power().setBurn(1, 1600); })); // out of fuel
            case LIGHTS + 256, LIGHTS + 260, LIGHTS + 266 -> shot(mc, "lights_shutdown_" + t);
            case LIGHTS + 310 -> shot(mc, "lights_off");
            default -> {}
        }
        if (t >= MECH_START) {
            mechanics(mc, t - MECH_START);
        }
        if (t >= WATER + 10 && t < WATER + 290) {
            logWater(mc, t);
        }
        if (t >= FUEL + 10 && t < FUEL + 250) {
            logFuel(mc, t);
        }
        if (t >= CHAINGUN + 10 && t < CHAINGUN + 95) {
            logCannon(mc, t);
        }
        if (t >= EM && t < EM + 110) {
            logEm(mc, t);
        }
        if (t >= MINING && t < MINING + 120) {
            logMine(mc, t);
        }
    }

    private static void logSpeed(Minecraft mc, String what) {
        var suit = mc.player.getVehicle();
        double v = Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20.0;
        PowerArmor.LOGGER.info("[autopilot] {} speed {} blocks/s", what, String.format("%.2f", v));
    }

    private static BufferedWriter waterCsv;

    private static void openWaterLog(Minecraft mc) {
        try {
            waterCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_water.csv"));
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
            waterCsv.write(String.format(Locale.ROOT, "%d,%.2f,%.3f,%.2f,%.2f,%b,%b,%b,%d,%.1f,%s,%.1f,%b,%s%n", t, suit.getZ() - waterStartZ,
                    suit.getY(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20, (suit.getY() - suit.yo) * 20,
                    suit.onGround(), suit.isInWater(), mc.player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()),
                    mc.player.getAirSupply(), mc.player.getHealth(), suit.animator().getMovementAnimationName(), suit.getHealth(), suit.isPowered(), suit.getEngine()));
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
    private static BufferedWriter fuelCsv;

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
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY);
            suit.power().setBurn(80, 1600);
        });
    }

    private static void openSuitMenu(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit != null) {
                player.setShiftKeyDown(true);
                fuelSuit.interact(player, InteractionHand.MAIN_HAND, fuelSuit.position());
                player.setShiftKeyDown(false);
            }
        });
    }

    private static void refuel(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit != null) {
                DevUtil.firebox(fuelSuit); fuelSuit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 16));
            }
        });
    }

    // ---- cannon scenario helpers -----------------------------------------------------------

    private static final List<Pig> pigs = new ArrayList<>();
    private static BufferedWriter cannonCsv;

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
                    level.setBlock(base.offset(dx, dy, 18), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int i = -1; i <= 1; i++) {
                var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                pig.setNoAi(true);
                pig.snapTo(fuelSuit.getX() + i * 2.5, fuelSuit.getY(), fuelSuit.getZ() + 12, 180, 0);
                level.addFreshEntity(pig);
                pigs.add(pig);
            }
        });
    }

    private static Pig emPig;
    private static BufferedWriter emCsv;

    /** Server: EM projector in the socket, 3 diamonds on the ground straight behind (the suit turns round),
     *  and a pig off to one side. */
    private static void emRangeSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                    new ItemStack(ModItems.EM_PROJECTOR.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var level = player.level();
            for (int i = 0; i < 3; i++) {
                var item = new ItemEntity(level, fuelSuit.getX() + 0.3 * i, fuelSuit.getY() + 0.2,
                        fuelSuit.getZ() - 9 - 2 * i, new ItemStack(Items.DIAMOND));
                item.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(item);
            }
            emPig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
            emPig.snapTo(fuelSuit.getX() + 4, fuelSuit.getY(), fuelSuit.getZ() - 8, 0, 0);
            level.addFreshEntity(emPig);
        });
    }

    /** Point the pilot's view at an entity's middle (it may have wandered since it was spawned). */
    private static void aimAt(Minecraft mc, Entity target) {
        if (target == null) {
            return;
        }
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0).subtract(mc.player.getEyePosition());
        yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        pitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
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
        var target = fuelSuit.socket().getBeamTarget();
        try {
            emCsv.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%.2f,%.2f,%.1f,%b%n", t,
                    target == null ? "-" : target.getType().toShortString(), fuelSuit.power().getTotalEnergy(),
                    mc.player.getInventory().countItem(Items.DIAMOND), fuelSuit.socket().getUses(),
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
    /** The player looks here every tick (null: the yaw/pitch fields). Package-private: the scene classes aim too. */
    static Vec3 aimTarget;
    private static BufferedWriter mineCsv;

    /** Server: drill in the socket, survival (creative breaks drop nothing), a cleared work area with a
     *  5x4x3 rock face 4 blocks ahead (+Z, ores in the front layer, dirt and gravel on top). */
    private static void miningSetup(Minecraft mc) {
        onServer(mc, player -> {
            if (fuelSuit == null) {
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                    new ItemStack(ModItems.DRILL.get()));
            var level = player.level();
            BlockPos base = fuelSuit.blockPosition();
            mineBase = base;
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-6, 0, -9), base.offset(8, 12, 9))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 0, 4), base.offset(2, 3, 6))) {
                var block = p.getY() - base.getY() == 3 ? (p.getX() % 2 == 0 ? Blocks.DIRT
                        : Blocks.GRAVEL) : Blocks.STONE;
                level.setBlock(p, block.defaultBlockState(), 3);
            }
            level.setBlock(base.offset(0, 1, 4), Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            level.setBlock(base.offset(-1, 0, 4), Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(base.offset(1, 2, 4), Blocks.COAL_ORE.defaultBlockState(), 3);
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
                    new ItemStack(ModItems.CHAINSAW.get()));
            var level = player.level();
            level.setBlock(treeBase.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.registryAccess().lookupOrThrow(Registries.FEATURE)
                    .get(TreeFeatures.OAK)
                    .ifPresent(tree -> tree.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), treeBase));
        });
    }

    private static void aimAtPos(Minecraft mc, Vec3 at) {
        Vec3 to = at.subtract(mc.player.getEyePosition());
        yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        pitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
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
            Vec3 pos = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(suit); suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 8)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
            suit.snapTo(pos.x, pos.y, pos.z, 0, 0);
            suit.setYBodyRot(0);
            level.addFreshEntity(suit);
            player.teleportTo(pos.x, pos.y, pos.z - 3);
            for (int dx = -3; dx <= 3; dx++) {     // a wall 7 blocks ahead of the suit to shoot rivets into
                for (int dy = 0; dy < 5; dy++) {
                    level.setBlock(BlockPos.containing(pos).offset(dx, dy, 7), Blocks.STONE_BRICKS.defaultBlockState(), 3);
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
                    .min(Comparator.comparingDouble(s -> s.distanceToSqr(player))).orElse(null);
            if (suit != null) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.getInventory().setItem(7, new ItemStack(Items.IRON_NUGGET, 64)); // bullets (survival here), not in the hand
                player.getInventory().setItem(8, new ItemStack(Items.IRON_NUGGET, 64));
                suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                        new ItemStack(ModItems.CHAINGUN.get()));
                suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
            }
        });
    }

    /** A client-only camera at {@code pos} looking at {@code at}. */
    static void lookCamera(Minecraft mc, Vec3 pos, Vec3 at) {
        Vec3 look = at.subtract(pos);
        float camYaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float camPitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        sideCamera = new ArmorStand(EntityTypes.ARMOR_STAND, mc.level);
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
            logs += mc.level.getBlockState(p).is(BlockTags.LOGS) ? 1 : 0;
        }
        int drops = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(mineBase).inflate(12)).size();
        try {
            String aimed = mc.hitResult instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS
                    ? BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock()).getPath() : "-";
            String toolAnim = "-"; // what the client's GeckoLib "tool" controller plays (drill spin / chain)
            if (mc.player.getVehicle() instanceof PowerArmorEntity clientSuit) {
                var controller = clientSuit.getAnimatableInstanceCache().getManagerForId(clientSuit.getId()).getAnimationControllers().get("tool");
                if (controller != null && controller.getCurrentAnimationPoint() != null) {
                    toolAnim = controller.getPlayState() + ":" + controller.getCurrentAnimationPoint().animation().name();
                }
            }
            mineCsv.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%d,%d,%s,%s,%s,%s%n", t,
                    BuiltInRegistries.ITEM.getKey(fuelSuit.getSocketStack().getItem()).getPath(),
                    fuelSuit.socket().getUses(), fuelSuit.power().getTotalEnergy(), face, logs, drops,
                    mc.player.getVehicle() instanceof PowerArmorEntity s ? s.animator().getMovementAnimationName() : "-", aimed, toolAnim, SuitSounds.activeLoops()));
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

    private static void giveHeld(Minecraft mc, ItemStack stack) {
        onServer(mc, player -> player.setItemInHand(InteractionHand.MAIN_HAND, stack));
    }

    private static void giveCannon(Minecraft mc) {
        // Take the rivet gun out of the suit (the shot shows the bare socket coupling) and into the player's hand.
        mc.player.getInventory().setSelectedSlot(0);
        onServer(mc, player -> {
            if (fuelSuit != null) {
                fuelSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, ItemStack.EMPTY);
            }
            player.getInventory().setSelectedSlot(0); // the hand holds slot 0 (the EM scenes left slot 1 selected)
            player.getInventory().setItem(0, new ItemStack(ModItems.CHAINGUN.get()));
            player.getInventory().setItem(1, new ItemStack(ModItems.EM_PROJECTOR.get()));
            player.getInventory().setItem(2, new ItemStack(ModItems.DRILL.get()));
            player.getInventory().setItem(3, new ItemStack(ModItems.CHAINSAW.get()));
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
        String health = pigs.stream().map(p -> p.isAlive() ? String.format(Locale.ROOT, "%.0f", p.getHealth()) : "dead")
                .reduce((a, b) -> a + "/" + b).orElse("");
        try {
            cannonCsv.write(String.format(Locale.ROOT, "%d,%d,%d,%b,%s,%s,%.2f,%.2f%n", t, fuelSuit.socket().getUses(), fuelSuit.power().getTotalEnergy(),
                    fuelSuit.socket().isTriggerHeld(), health, mc.player.getVehicle() instanceof PowerArmorEntity s ? s.animator().getMovementAnimationName() : "-",
                    fuelSuit.socket().getSpin(), fuelSuit.socket().getHeat()));
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

    static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
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
            fuelCsv.write(String.format(Locale.ROOT, "%d,%d,%d,%s,%b,%b,%.2f,%s,%b%n", t, suit.power().getBurnLeft(), suit.power().getTotalEnergy(),
                    suit.getFuelStack().getCount() + "x" + BuiltInRegistries.ITEM.getKey(suit.getFuelStack().getItem()).getPath(),
                    suit.isPowered(), suit.canOperate(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20, suit.animator().getMovementAnimationName(),
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
            suit.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(ModItems.TIDAL_ENGINE.get()));
            DevUtil.firebox(suit); suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.WATER_BUCKET));
            var level = player.level();
            BlockPos base = suit.blockPosition(); // g = base.y (the suit stands on top of base.y - 1)
            int g = base.getY();
            var stone = Blocks.STONE.defaultBlockState();
            var glass = Blocks.GLASS.defaultBlockState();
            var water = Blocks.WATER.defaultBlockState();
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
        Path dir = mc.gameDirectory.toPath().resolve("view");
        if (t == 0) {
            viewLastRequest = System.currentTimeMillis();
            try { // tools/view-tuner.sh stop uses this if the quit request is ever ignored
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("pid"), Long.toString(ProcessHandle.current().pid()));
            } catch (IOException ignored) {
            }
        }
        // Never linger: quit on request, or after 10 minutes without a shot request.
        if (Files.exists(dir.resolve("quit")) || System.currentTimeMillis() - viewLastRequest > 10 * 60_000) {
            try {
                Files.deleteIfExists(dir.resolve("quit"));
                Files.deleteIfExists(dir.resolve("pid"));
            } catch (IOException ignored) {
            }
            mc.stop();
            return;
        }
        if (viewStep < 0) {
            try {
                Files.createDirectories(dir);
                if (!Files.deleteIfExists(dir.resolve("shoot")) && t != 60) {
                    return;
                }
                viewLastRequest = System.currentTimeMillis();
                Files.deleteIfExists(dir.resolve("done"));
            } catch (IOException e) {
                return;
            }
            viewStep = 0;
        }
        int s = viewStep++;
        switch (s) {
            // GUI on: hiding it (F1) also skips the hand pass, so the viewmodel wouldn't be drawn.
            case 0 -> { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.FIRST_PERSON); releaseAll(); yaw = 0; pitch = 10;
                        if (Files.exists(dir.resolve("tuning.json"))) CockpitHands.loadTuning(dir.resolve("tuning.json"));
                        socket(mc, ModItems.CHAINGUN.get());
                        giveHeld(mc, new ItemStack(Items.STONE_BRICKS, 64)); }
            case 12 -> shot(mc, "view_cannon");
            case 13 -> socket(mc, ModItems.DRILL.get());
            case 25 -> shot(mc, "view_drill");
            case 26 -> socket(mc, ModItems.CHAINSAW.get());
            case 38 -> shot(mc, "view_chainsaw");
            case 39 -> socket(mc, ModItems.EM_PROJECTOR.get());
            case 51 -> shot(mc, "view_em");
            case 52 -> socket(mc, null);
            case 64 -> shot(mc, "view_empty");
            case 65 -> { socket(mc, ModItems.CHAINGUN.get()); pitch = 55; hold(o.keyUp, true); }
            case 80 -> shot(mc, "view_walkdown");
            case 81 -> { releaseAll(); pitch = 10; giveHeld(mc, new ItemStack(Items.GOLDEN_APPLE, 4)); }
            case 98 -> shot(mc, "view_food"); // holding food (pilots can't eat: no use animation to check)
            // the right hand in the suit, then the same items on foot (vanilla's first-person hand) to compare;
            // wait out the attack cooldown after each swap: vanilla (and the gauntlet) keep the hand lowered until it recharges
            case 99 -> { releaseAll(); giveHeld(mc, new ItemStack(Items.IRON_SWORD)); }
            case 125 -> shot(mc, "view_sword");
            case 126 -> giveHeld(mc, ItemStack.EMPTY);
            case 135 -> shot(mc, "view_hand");
            case 136 -> { onFoot(mc, true); giveHeld(mc, new ItemStack(Items.STONE_BRICKS, 64)); }
            case 150 -> shot(mc, "view_vanilla_bricks");
            case 151 -> giveHeld(mc, new ItemStack(Items.IRON_SWORD));
            case 177 -> shot(mc, "view_vanilla_sword");
            case 178 -> giveHeld(mc, ItemStack.EMPTY);
            case 190 -> shot(mc, "view_vanilla_hand");
            case 191 -> { onFoot(mc, false); giveHeld(mc, new ItemStack(Items.STONE_BRICKS, 64)); }
            // overheated chaingun (client-side heat: the server keeps sending 0 only when it changes, so it sticks)
            case 192 -> socket(mc, ModItems.CHAINGUN.get());
            case 198 -> { if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.socket().setSpin(0F, 1F); }
            case 202 -> shot(mc, "view_hot");
            case 203 -> { if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.socket().setSpin(0F, 0.7F); }
            case 206 -> shot(mc, "view_warm");
            case 207 -> { o.setCameraType(CameraType.THIRD_PERSON_FRONT); if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.socket().setSpin(0F, 1F); }
            case 214 -> shot(mc, "view_hot_tp");
            case 215 -> { o.setCameraType(CameraType.FIRST_PERSON); if (mc.player.getVehicle() instanceof PowerArmorEntity suit) suit.socket().setSpin(0F, 0F); }
            // tracers: a barrage diagonally up at the sky (yaw 45: the streaks must trail straight back along their flight)
            case 216 -> { giveHeld(mc, ItemStack.EMPTY); yaw = 45; pitch = -25;
                          onServer(mc, p -> p.getInventory().setItem(8, new ItemStack(Items.IRON_NUGGET, 64))); }
            case 220 -> hold(o.keyUse, true);
            case 250, 252 -> shot(mc, "view_tracers_" + s);
            case 253 -> { sideCamera(mc); o.setCameraType(CameraType.THIRD_PERSON_BACK); } // (a "first person" camera entity would draw the pilot's cockpit tracers)
            case 262, 264 -> shot(mc, "view_tracers_tp_" + s);
            case 265 -> { releaseAll(); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); yaw = 0; pitch = 10;
                          giveHeld(mc, new ItemStack(Items.STONE_BRICKS, 64)); }
            // the Phase 11 tools at rest
            case 280 -> socket(mc, ModItems.FLAMETHROWER.get());
            case 292 -> shot(mc, "view_flame");
            case 293 -> socket(mc, ModItems.TNT_LAUNCHER.get());
            case 305 -> shot(mc, "view_tnt");
            case 306 -> socket(mc, ModItems.RAIL_CANNON.get());
            case 318 -> shot(mc, "view_rail");
            case 320 -> {
                viewStep = -1;
                try {
                    Files.writeString(dir.resolve("done"), "ok");
                } catch (IOException ignored) {
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
    private static void socket(Minecraft mc, @Nullable Item tool) {
        onServer(mc, player -> {
            if (player.getVehicle() instanceof PowerArmorEntity suit) {
                suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                        tool == null ? ItemStack.EMPTY : new ItemStack(tool));
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
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 pos = suit.position().add(forward.scale(4.5)).add(right.scale(2.5)).add(0, 0.6, 0);
        Vec3 look = suit.position().add(0, 1.7, 0).subtract(pos);
        float camYaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float camPitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        sideCamera = new ArmorStand(EntityTypes.ARMOR_STAND, mc.level);
        sideCamera.setPos(pos);
        sideCamera.xo = pos.x; sideCamera.yo = pos.y; sideCamera.zo = pos.z;
        sideCamera.setYRot(camYaw); sideCamera.yRotO = camYaw;
        sideCamera.setYHeadRot(camYaw); sideCamera.yHeadRotO = camYaw;
        sideCamera.setXRot(camPitch); sideCamera.xRotO = camPitch;
        mc.setCameraEntity(sideCamera);
    }

    /** A camera ~1.6 blocks out to the front-left of the tool arm, looking at the EM projector: raised (at the muzzle)
     *  or in its hip carry. */
    private static void emCloseCamera(Minecraft mc, boolean raised) {
        if (!(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 at = raised ? SocketAim.muzzlePosition(suit, mc.player).subtract(forward.scale(0.3))
                : suit.position().add(0, 1.05, 0).add(left.scale(0.8)).add(forward.scale(0.3));
        // First person: in third person the game pulls the view 4 blocks back from the camera entity. The camera
        // entity (an armour stand) sees from its eye height, so stand it (and the aim point, to keep the direction)
        // that much lower. GUI hidden: the hand pass would draw the pilot's cockpit viewmodel over the close-up.
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 eye = at.add(left.scale(1.1)).add(forward.scale(1.1)).add(0, 0.35, 0);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        lookCamera(mc, eye.subtract(drop), at.subtract(drop));
    }

    /** A client-only camera 8 blocks to the side of the suit, looking at it. */
    private static void sideCamera(Minecraft mc) {
        var target = mc.player.getVehicle() != null ? mc.player.getVehicle()
                : mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(8)).stream().findFirst().orElseThrow();
        Vec3 suit = target.position();
        sideCamera = new ArmorStand(EntityTypes.ARMOR_STAND, mc.level);
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
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            // A full stack of blaze rods (~4 hours of walking): enough for any scripted drive.
            DevUtil.firebox(suit); suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 64)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
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
    /** Filled on the server thread (the scene spawns them), read every tick by logMech on the client thread: a plain
     *  ArrayList threw ConcurrentModificationException now and then (crashes 10-13, 10-19, 10-23). */
    private static final List<LivingEntity> mechMobs = new CopyOnWriteArrayList<>();
    private static BufferedWriter mechCsv;
    private static String mechPhase = "-";

    /** Scene cameras look from an armour stand, but vanilla still paints the player's own fire / in-wall overlays over
     *  them (it only checks "first person"): hide those while a scene camera is active. */
    @SubscribeEvent
    public static void sceneCameraOverlays(ExtractBlockScreenEffectEvent event) {
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
                              var bolt = EntityTypes.LIGHTNING_BOLT.create(p.level(), EntitySpawnReason.COMMAND);
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
            case 320 -> { var cow = nearestCow(mc); if (cow != null) { mc.gameMode.attack(mc.player, cow); mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false); } }
            case 321 -> shot(mc, "mech_punch");
            case 322 -> { giveHeld(mc, new ItemStack(Items.BREAD, 4));
                          onServer(mc, p -> p.getFoodData().setFoodLevel(10)); }   // hungry, bread in hand, no tool: refused
            case 323 -> hold(o.keyUse, true);
            case 328 -> { shot(mc, "mech_eat_refused"); hold(o.keyUse, false); }
            // the seal: wade through lava (hull damage, fire kept out for fuel), get poisoned (kept out)
            case 330 -> { mechPhase = "lava"; o.setCameraType(CameraType.THIRD_PERSON_BACK); yaw = 0; pitch = 20; lavaSetup(mc); }
            case 336 -> hold(o.keyUp, true);
            case 350, 370, 390 -> shot(mc, "mech_lava_" + m);
            case 400 -> { releaseAll(); onServer(mc, p -> p.addEffect(new MobEffectInstance(MobEffects.POISON, 200))); }
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
                              mindSuit.brain().setAim(Vec3.atCenterOf(mindBase.offset(0, 1, 3)));
                              mindSuit.socket().setAiTrigger(true); } }); }
            case 490, 496, 504, 512 -> shot(mc, "ai_drill_" + (m - 420));
            case 520 -> { mindPhase = "after"; onServer(mc, p -> { if (mindSuit != null) { mindSuit.socket().setAiTrigger(false); mindSuit.brain().setAim(null); } }); }
            case 522 -> mindVisorCamera(mc);
            case 532 -> shot(mc, "ai_visor");
            // chaingun job (step 2): the suit's menu, then the suit follows its owner and shoots the zombies that come for
            // them (the mind's bullets never run out since 2026-10-27), and a last one that comes later
            case 534 -> { mindPhase = "load"; mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false);
                          onServer(mc, p -> { if (mindSuit != null) {
                              mindSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
                              p.setGameMode(GameType.SURVIVAL);
                              p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 4));
                              p.setShiftKeyDown(true);          // sneak + right-click: the menu
                              mindSuit.interact(p, InteractionHand.MAIN_HAND, mindSuit.position());
                              p.setShiftKeyDown(false); } }); }
            case 542 -> shot(mc, "ai_menu_ammo");
            case 544 -> { mc.player.closeContainer(); mindPhase = "follow";
                          onServer(mc, p -> { if (mindBase != null) {
                              p.teleportTo(mindBase.getX() + 14.5, mindBase.getY(), mindBase.getZ() - 2.5); } }); }
            case 546 -> mindWideCamera(mc);
            case 552, 562, 572, 584 -> shot(mc, "ai_follow_" + (m - 420));
            case 590 -> { mindPhase = "defend"; onServer(mc, p -> {
                              p.level().getServer().setDifficulty(Difficulty.NORMAL, true); // peaceful removes monsters at once
                              for (int i = 0; i < 3; i++) {
                                  var zombie = EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND);
                                  zombie.snapTo(p.getX() + 3 + 2 * i, p.getY(), p.getZ() + 9 - i, 180, 0);
                                  zombie.setTarget(p);
                                  p.level().addFreshEntity(zombie);
                                  mindMobs.add(zombie);
                              } }); }
            case 593, 597, 601, 606, 612, 620, 630 -> shot(mc, "ai_defend_" + (m - 420));
            case 640 -> { mindPhase = "late"; onServer(mc, p -> { if (mindSuit != null) {
                              var zombie = EntityTypes.ZOMBIE.create(p.level(), EntitySpawnReason.COMMAND);
                              zombie.snapTo(p.getX() + 4, p.getY(), p.getZ() + 6, 180, 0);
                              zombie.setTarget(p);
                              p.level().addFreshEntity(zombie);
                              mindMobs.add(zombie); } }); }
            case 650, 658, 666, 674, 684, 696 -> shot(mc, "ai_late_" + (m - 420));
            // EM guard job (step 3): the suit goes home with an EM projector; zombies, a skeleton and a creeper wander in
            // round home: each one is caught, lifted overhead and flung ~24 blocks up (the creeper: away from home)
            case 700 -> { mindPhase = "em"; onServer(mc, p -> { if (mindSuit != null && mindBase != null) {
                              mindMobs.forEach(e -> e.discard());
                              mindMobs.clear();
                              p.setGameMode(GameType.CREATIVE);  // monsters ignore a creative player: they just wander near home
                              p.teleportTo(mindBase.getX() - 12.5, mindBase.getY(), mindBase.getZ() - 12.5);
                              mindSuit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT,
                                      new ItemStack(ModItems.EM_PROJECTOR.get())); } }); }
            case 702 -> mindGuardCamera(mc);
            case 740 -> onServer(mc, p -> { if (mindBase != null) {
                              var level = p.level();
                              EntityType<?>[] types = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.CREEPER};
                              double[][] at = {{7, 2}, {-6, 5}, {3, -8}, {-5, -6}};
                              for (int i = 0; i < types.length; i++) {
                                  var mob = (Mob) types[i].create(level, EntitySpawnReason.COMMAND);
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
    private static final List<Mob> mindMobs = new CopyOnWriteArrayList<>();
    private static volatile BlockPos mindBase;
    private static BufferedWriter mindCsv;
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
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos base = new BlockPos(x, y, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(base.offset(dx, dy, 3), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(suit); suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 16)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.DRILL.get()));
            suit.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            suit.setYBodyRot(0);
            suit.setYHeadRot(0);
            suit.brain().setOwnerId(player.getUUID());
            suit.brain().setHome(base);
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
            Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0); // lookCamera places the stand's feet
            lookCamera(mc, Vec3.atBottomCenterOf(b).add(3, 2.6, -6.5).subtract(drop), Vec3.atBottomCenterOf(b).add(3, 1.6, 1).subtract(drop));
        }
    }

    /** Wide side view of the follow / defend scene: from the south, 12 blocks out, centred between home and the owner. */
    private static void mindWideCamera(Minecraft mc) {
        BlockPos b = mindBase;
        if (b != null) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            DevUtil.hideGui(mc, true);
            Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
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
            Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
            lookCamera(mc, Vec3.atBottomCenterOf(b).add(-3, 8, -24).subtract(drop), Vec3.atBottomCenterOf(b).add(0, 9, 0).subtract(drop));
        }
    }

    /** Close-up of the mind's suit from 3 blocks in front of its face (the client copy: where this client draws it). */
    private static void mindVisorCamera(Minecraft mc) {
        if (mindSuit != null && mc.level.getEntity(mindSuit.getId()) instanceof PowerArmorEntity suit) {
            float yaw = suit.getYHeadRot() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0); // lookCamera places the stand's feet
            Vec3 face = suit.position().add(0, 2.5, 0);
            lookCamera(mc, face.add(forward.scale(3.2)).add(forward.yRot(1.5708F).scale(0.8)).subtract(drop), face.subtract(drop));
        }
    }

    private static void openMindLog(Minecraft mc) {
        try {
            mindCsv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_mind.csv"));
            mindCsv.write("tick,phase,dxHome,dzHome,speed,driven,anim,headYaw,bodyYaw,xRot,toolActive,clientRaise,faceLeft,drops,energy,visorR,visorG,visorB,navDone,goals,ownerDist,target,shots,mobsAlive,mobHealth,beam,mobTopY,clientHead,clientBody,twist,drawnYaw,aimYaw,aimErr,mobs\n");
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
        int drops = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(b).inflate(8)).size();
        float[] visor = client.lights().getVisorColor();
        // What this client draws: the torso twists by (head - legs), clamped to +-90 (PowerArmorRenderer.aim), so the
        // torso faces drawnYaw. aimErr: how far that is from where the mind aims (its target point), degrees.
        float twist = Mth.wrapDegrees(client.getYHeadRot() - client.yBodyRot);
        float drawnYaw = client.yBodyRot + Mth.clamp(twist, -90F, 90F);
        Vec3 aim = server.brain().getAim();
        float aimYaw = aim == null ? Float.NaN : (float) (Mth.atan2(aim.z - server.getZ(), aim.x - server.getX()) * Mth.RAD_TO_DEG) - 90F;
        float aimErr = aim == null ? Float.NaN : Mth.wrapDegrees(aimYaw - drawnYaw);
        try {
            mindCsv.write(String.format(Locale.ROOT, "%d,%s,%.2f,%.2f,%.2f,%b,%s,%.1f,%.1f,%.1f,%b,%.2f,%d,%d,%d,%.2f,%.2f,%.2f,%b,%s,%.2f,%s,%d,%d,%.1f,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%s%n",
                    m, mindPhase, server.getX() - (b.getX() + 0.5), server.getZ() - (b.getZ() + 0.5),
                    Math.hypot(server.getX() - server.xo, server.getZ() - server.zo) * 20, server.isAiDriven(), client.animator().getMovementAnimationName(),
                    server.getYHeadRot(), server.yBodyRot, server.getXRot(), server.socket().isActive(), client.socketVisuals().raise(1F), faceLeft, drops,
                    server.power().getTotalEnergy(), visor[0], visor[1], visor[2], server.getNavigation().isDone(),
                    server.brain().runningGoalNames(), server.brain().getOwner() == null ? -1F : server.distanceTo(server.brain().getOwner()),
                    server.getTarget() == null ? "-" : server.getTarget().getType().toShortString(), server.socket().getUses(),
                    mindMobs.stream().filter(e -> e.isAlive()).count(), mindMobs.stream().filter(e -> e.isAlive()).mapToDouble(e -> e.getHealth()).sum(),
                    server.socket().getBeamTarget() == null ? "-" : server.socket().getBeamTarget().getType().toShortString(),
                    mindMobs.stream().filter(e -> e.isAlive()).mapToDouble(e -> e.getY() - b.getY()).max().orElse(0),
                    client.getYHeadRot(), client.yBodyRot, twist, drawnYaw, aimYaw, aimErr,
                    mindMobs.stream().map(e -> e.getType().toShortString() + ":" + (e.isAlive() ? String.format(Locale.ROOT, "%.0f@%.1f",
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
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        lookCamera(mc, Vec3.atBottomCenterOf(b).add(8, 2.2, 30).subtract(drop), Vec3.atBottomCenterOf(b).add(0, 1.4, 30).subtract(drop));
    }

    private static Cow nearestCow(Minecraft mc) {
        return mc.level.getEntitiesOfClass(Cow.class, mc.player.getBoundingBox().inflate(8)).stream()
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
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos base = new BlockPos(x, y, z);
            mechBase = base;
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(suit); suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 32)); suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            suit.setYBodyRot(0);
            level.addFreshEntity(suit);
            mechSuit = suit;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.teleportTo(x + 0.5, y, z - 1.5);
            player.startRiding(suit, true, true);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = 2; dz <= 4; dz++) {
                    level.setBlock(base.offset(dx, 0, dz), Blocks.SNOW.defaultBlockState(), 3);
                }
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(base.offset(dx, dy, 6), Blocks.GLASS_PANE.defaultBlockState(), 3);
                }
                for (int dy = 0; dy <= 2; dy++) { // persistent leaves (like a hedge someone planted): they don't decay
                    level.setBlock(base.offset(dx, dy, 9), Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(LeavesBlock.PERSISTENT, true), 3);
                }
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(base.offset(dx, dy, 13), (dy < 2 ? Blocks.GLASS
                            : Blocks.ICE).defaultBlockState(), 3);
                }
                // a stone wall the suit can't get through, to end the run
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlock(base.offset(dx, dy, 20), Blocks.STONE_BRICKS.defaultBlockState(), 3);
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
            Object[][] mobs = {{EntityTypes.PIG, 2.0, 0.0}, {EntityTypes.PIG, -1.5, 1.5}, {EntityTypes.VILLAGER, 0.0, -2.8}, {EntityTypes.COW, -6.0, 0.0}};
            for (Object[] mob : mobs) {
                var e = ((EntityType<?>) mob[0]).create(level, EntitySpawnReason.COMMAND);
                e.snapTo(c.x + (double) mob[1], spot.getY(), c.z + (double) mob[2], 0, 0);
                level.addFreshEntity(e);
                mechMobs.add((LivingEntity) e);
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
                    level.setBlock(at.offset(dx, -1, dz), Blocks.LAVA.defaultBlockState(), 3);
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
            var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            cow.snapTo(mechSuit.getX() + 3, mechSuit.getY(), mechSuit.getZ() + 1, 90, 0); // off to the left, clear of the lava to come
            cow.setNoAi(true);
            level.addFreshEntity(cow);
            mechMobs.add(cow);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
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
            panes += state.is(Blocks.GLASS_PANE) ? 1 : 0;
            leaves += state.is(Blocks.OAK_LEAVES) ? 1 : 0;
            glass += state.is(Blocks.GLASS) ? 1 : 0;
            ice += state.is(Blocks.ICE) ? 1 : 0;
            snow += state.is(Blocks.SNOW) ? 1 : 0;
        }
        int drops = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(mechBase).inflate(20)).size();
        String mobs = mechMobs.stream().map(e -> e.getType().toShortString() + ":" + (e.isAlive() ? String.format(Locale.ROOT, "%.1f", e.getHealth()) : "dead"))
                .reduce((a, b) -> a + "/" + b).orElse("");
        var server = mechSuit;
        var pilot = server.getControllingPassenger();
        try {
            var last = server.getLastDamageSource();
            mechCsv.write(String.format(Locale.ROOT, "%d,%s,%.2f,%.2f,%.2f,%b,%b,%s,%b,%b,%b,%.1f,%b,%.1f,%b,%b,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%b,%s,%.1f,%.2f,%b,%d,%b,%s%n",
                    m, mechPhase, suit.getZ() - mechBase.getZ(), suit.getY() - mechBase.getY(), Math.hypot(suit.getX() - suit.xo, suit.getZ() - suit.zo) * 20,
                    suit.onGround(), mc.player.isSprinting(), suit.animator().getMovementAnimationName(), server.isPowered(), server.isStunned(), server.canOperate(),
                    server.getHealth(), server.isOnFire(), pilot == null ? -1F : pilot.getHealth(), pilot != null && pilot.isOnFire(),
                    pilot != null && pilot.hasEffect(MobEffects.POISON), pilot == null ? -1 : pilot.getAirSupply(),
                    server.power().getTotalEnergy(), panes, leaves, glass, ice, snow, drops, server.lastStompVictims, mobs,
                    pilot == null ? -1 : pilot.getRemainingFireTicks(), mc.player.isOnFire(), last == null ? "-" : last.getMsgId(),
                    pilot instanceof Player p ? p.getAttributeValue(Attributes.ATTACK_DAMAGE) : -1D, // -1D: an int -1 is boxed as Integer (%f crashed)
                    pilot instanceof Player p2 ? p2.getAttackStrengthScale(0.5F) : -1F,
                    mc.player.isUsingItem(), pilot instanceof Player p3 ? p3.getFoodData().getFoodLevel() : -1, suit.isOnFire(), actionAnim(suit)));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] mech csv", e);
        }
    }

    /** What the client's "action" controller (hit flinch, tool recoil) is playing, or "-". */
    private static String actionAnim(PowerArmorEntity suit) {
        var manager = suit.getAnimatableInstanceCache().getManagerForId(suit.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("action");
        if (controller == null || controller.getCurrentAnimationPoint() == null
                || controller.getPlayState() == PlayState.STOP) {
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
