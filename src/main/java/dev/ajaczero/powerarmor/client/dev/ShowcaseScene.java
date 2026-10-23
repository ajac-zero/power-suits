package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ChainsawItem;
import dev.ajaczero.powerarmor.item.HelmetStyle;
import dev.ajaczero.powerarmor.registry.ModDataComponents;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * DEV TOOL, inert unless started with -Dpowerarmor.autopilot=showcase (./gradlew runShowcase): gameplay screenshots for
 * the mod's gallery (Modrinth). Unlike the autopilot's test shots these are meant to be pretty: a fresh world with real
 * terrain (fixed seed, so every run finds the same places), a window of 960x540 (1920x1080 pixels on a Retina screen),
 * survival HUD where the shot shows the game as played. Each shot is taken over several ticks
 * ({@code run/screenshots/showcase_<shot>_<tick>.png}) so we can pick the frame with the best stride / muzzle flash;
 * {@code run/showcase.csv} lists where each set was found (biome, position, view yaw).
 *
 * <p>Sets so far (prototype, 2026-10-23): a walk over sunflower plains (cockpit with HUD, then the in-game view from behind) and the
 * chaingun at dusk against a few monsters on plains.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class ShowcaseScene {
    private static final boolean ENABLED = "showcase".equals(System.getProperty("powerarmor.autopilot"));
    private static final String WORLD = "showcase";
    private static final long SEED = 20261023L;
    /** Ticks between moving to a new set and its first shot: the new chunks must generate, light and render. */
    private static final int SETTLE = 140;

    private static boolean worldRequested;
    private static int tick = -1;
    private static float yaw, pitch;
    private static boolean walk, sprint, fire;
    private static volatile Vec3 setPos;
    private static volatile float setYaw;
    private static BufferedWriter csv;
    private static int renderDistance = -1;
    private static int fov = 70;

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
            if (!worldRequested && mc.gui.screen() instanceof TitleScreen) {
                worldRequested = true;
                createWorld(mc);
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (tick < 0) {
            if (mc.player.tickCount > 40) {
                tick = 0;
                renderDistance = mc.options.renderDistance().get();
                fov = mc.options.fov().get();
                mc.options.renderDistance().set(16); // further vistas (put back at the end: options.txt is shared)
                open(mc);
            }
            return;
        }
        if (!script(mc, tick++)) {
            finish(mc);
            return;
        }
        var o = mc.options;
        o.keyUp.setDown(walk);
        o.keySprint.setDown(sprint);
        o.keyUse.setDown(fire);
        if (!sprint && mc.player.isSprinting()) {
            mc.player.setSprinting(false); // vanilla keeps sprinting after the key is released
        }
        Vec3 target = aim;
        if (target != null) {
            Vec3 to = target.subtract(mc.player.getEyePosition());
            yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
            pitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        }
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    /** A file run/showcase-only with set numbers ("3 4"): shoot only those (quicker iterations on new sets). */
    private static final java.util.Set<Integer> ONLY = readOnly();

    private static java.util.Set<Integer> readOnly() {
        Path file = Path.of("showcase-only");
        try {
            return Files.exists(file) ? java.util.Arrays.stream(Files.readString(file).trim().split("[\\s,]+"))
                    .map(Integer::parseInt).collect(java.util.stream.Collectors.toSet()) : null;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    /** Where the pilot aims (set by the server when a set starts; the view turns to it every tick), or null. */
    private static volatile Vec3 aim;

    /** Set being shot: 0 = walk, 1 = chaingun at dusk, 2 = coral reef, 3 = drill into rock, 4 = chainsaw on a tree, 5 = EM projector. */
    private static int set = -1;
    /** Client tick at which the current set's place was ready (-1: the server is still looking). */
    private static int setStart = -1;
    private static volatile boolean ready;

    /** One tick of the shot list; false when it's over. */
    private static boolean script(Minecraft mc, int t) {
        var o = mc.options;
        // no advancement / recipe toasts or chat lines in the pictures (the hotbar's items unlock a few)
        mc.gui.toastManager().clear();
        mc.gui.hud.getChat().clearMessages(false);
        if (set < 0 || (setStart >= 0 && done)) {
            set++;
            done = false;
            setStart = -1;
            ready = false;
            walk = sprint = fire = false;
            mc.setCameraEntity(mc.player);
            aim = null;
            while (ONLY != null && set < 10 && !ONLY.contains(set)) {
                set++; // (iterating on a few sets: run/showcase-only)
            }
            switch (set) {
                // Places are pinned once a run found a good one (the seed is fixed, so they stay the same): run a new
                // set with a null spot to search (findSpot), then copy the place from run/showcase.csv.
                // a sunflower-plains hilltop: a mountain and the sea on the left, a village ahead
                case 0 -> moveTo(mc, "walk", Biomes.SUNFLOWER_PLAINS, 6000, pin(739, 116, -659, 0),
                        new Loadout(DyeColor.BLUE, ModItems.FIREBOX.get(), ModItems.CHAINGUN.get()));
                // plains below a village, looking west into the sunset (yaw 90 looks -X)
                case 1 -> moveTo(mc, "dusk", Biomes.PLAINS, DUSK, pin(595, 70, -812, 90),
                        new Loadout(DyeColor.RED, ModItems.FIREBOX.get(), ModItems.CHAINGUN.get()));
                // a coral reef: the tidal engine runs free under water
                case 2 -> moveTo(mc, "reef", Biomes.WARM_OCEAN, 6000, pin(1618, 52, -236, 90),
                        new Loadout(DyeColor.YELLOW, ModItems.TIDAL_ENGINE.get(), ModItems.DRILL.get()));
                // the drill into a rock face, filmed from outside (a mountain near the walk's hilltop)
                case 3 -> moveTo(mc, "mine", Biomes.PLAINS, 6000, pin(531, 40, -667, 270, new BlockPos(533, 41, -667)), // a stone ravine (found by findWall)
                        new Loadout(DyeColor.ORANGE, ModItems.FIREBOX.get(), ModItems.DRILL.get(), HelmetStyle.PROTECTOR));
                // the chainsaw felling a tree in a forest, filmed from outside
                case 4 -> moveTo(mc, "tree", Biomes.PLAINS, 6000, l -> findTree(l, Biomes.PLAINS, new BlockPos(739, 80, -659)),
                        new Loadout(DyeColor.GREEN, ModItems.FIREBOX.get(), ModItems.CHAINSAW.get(), HelmetStyle.SPARTAN));
                // the EM projector lifting a creeper, below the dusk set's village by day: purple (Samus's Gravity Suit)
                // with the Hunter helmet and the star reactor
                case 5 -> moveTo(mc, "em", Biomes.PLAINS, 5000, pin(595, 70, -812, 90),
                        new Loadout(DyeColor.PURPLE, ModItems.STAR_REACTOR.get(), ModItems.EM_PROJECTOR.get(), HelmetStyle.HUNTER));
                default -> { return false; }
            }
            return true;
        }
        if (setStart < 0) {
            if (!ready) {
                return true;
            }
            setStart = t; // and on: this tick is the set's k = -SETTLE (its camera and view are set up now)
        }
        int settle = set == 2 ? WATER_SETTLE : SETTLE;
        int k = t - setStart - settle; // ticks since the set's first shot could be taken
        switch (set) {
            case 0 -> walk(mc, k);
            case 1 -> dusk(mc, k);
            case 2 -> reef(mc, k);
            case 3 -> mine(mc, k);
            case 4 -> tree(mc, k);
            case 5 -> em(mc, k);
            default -> {}
        }
        return true;
    }

    private static boolean done;

    /** A walk: the game's front view (F5 twice) coming at us, the cockpit with the survival HUD (fuel gauge, ammo), then the in-game view from behind. */
    private static void walk(Minecraft mc, int k) {
        var o = mc.options;
        // F5's front view turns the pilot's view round: looking up puts its camera higher, looking down at the suit
        if (k == -SETTLE) { o.setCameraType(CameraType.THIRD_PERSON_FRONT); DevUtil.hideGui(mc, true); yaw = setYaw; pitch = -15; }
        if (k == 0) walk = true;
        if (k >= 8 && k <= 28 && k % 4 == 0) shot(mc, "front_walk", k);
        // a still camera standing in the suit's path, as a friend would: the suit walks up to it. (A scripted camera
        // stops the client sending the pilot's moves to the server; the client still walks the suit, for a few seconds.)
        if (k == 30) pathCamera(mc, 8.0, 2.3);
        if (k >= 34 && k <= 50 && k % 4 == 0) shot(mc, "front_close", k);
        if (k == 52) { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); yaw = setYaw - 25; pitch = 3; } // the mountain and the sea
        if (k >= 62 && k <= 78 && k % 4 == 0) shot(mc, "cockpit", k);
        if (k == 80) { yaw = setYaw; o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); pitch = 12; }
        if (k >= 86 && k <= 114 && k % 4 == 0) shot(mc, "back_walk", k);
        if (k == 116) done = true;
    }

    /** Sunset (the sun on the horizon at ~12000; vanilla nights are near black in pictures). Zombies wear helmets: they'd burn now. */
    private static final long DUSK = 12400;

    /** The chaingun at dusk: monsters come in, the suit fires; over its tool shoulder, from the side, the in-game view, the cockpit. */
    private static void dusk(Minecraft mc, int k) {
        var o = mc.options;
        if (k == -SETTLE) { yaw = setYaw; pitch = 2; }
        if (k == -10) monsters(mc);
        if (k == 0) { fire = true; shoulderCamera(mc); }
        if (k >= 8 && k <= 24 && k % 2 == 0) shot(mc, "dusk_shoulder", k);
        if (k == 26) sideCamera(mc);
        if (k >= 28 && k <= 42 && k % 2 == 0) shot(mc, "dusk_side", k);
        if (k == 44) frontCamera(mc);
        if (k >= 46 && k <= 60 && k % 2 == 0) shot(mc, "dusk_front", k);
        if (k == 62) { o.fov().set(fov); mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); pitch = 6; }
        if (k >= 66 && k <= 74 && k % 2 == 0) shot(mc, "dusk_back", k);
        if (k == 76) { o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); pitch = 2; }
        if (k >= 80 && k <= 90 && k % 2 == 0) shot(mc, "dusk_cockpit", k);
        if (k == 92) done = true;
    }

    /**
     * Ticks on the reef before the first shot: vanilla clears the underwater fog over the 30 s after the eyes go under
     * (LocalPlayer's water vision), so the shots wait for the view a player has after half a minute down there.
     */
    private static final int WATER_SETTLE = 640;

    /** A walk over the reef: the game's front view, the in-game view from behind (the tidal pack bubbling), the cockpit. */
    private static void reef(Minecraft mc, int k) {
        var o = mc.options;
        if (k == -WATER_SETTLE) { o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); yaw = setYaw; pitch = 8; }
        if (k == -20) reseat(mc); // the suit drifts a little while the view clears (runs differed): back to the pinned spot
        // F5's front view (a still camera in the coral hid the suit or froze it against a block)
        if (k == -2) o.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (k == 0) walk = true;
        if (k >= 6 && k <= 26 && k % 4 == 0) shot(mc, "reef_front", k);
        if (k == 30) o.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (k >= 36 && k <= 56 && k % 4 == 0) shot(mc, "reef_back", k);
        // then stop and look back the way it came (the coral it walked into fills the view ahead)
        if (k == 58) { walk = false; o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); yaw = setYaw + 150; pitch = 5; }
        if (k >= 70 && k <= 82 && k % 4 == 0) shot(mc, "reef_cockpit", k);
        if (k == 84) done = true;
    }

    /**
     * The drill into a rock face: first from the cockpit (survival HUD, the drill's viewmodel, the face cracking), then
     * from outside: behind it to its tool side, from the side, from further back and up.
     */
    private static void mine(Minecraft mc, int k) {
        var o = mc.options;
        if (k == -SETTLE) { o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); aim = target; }
        if (k == 0) fire = true;
        if (k >= 3 && k <= 39 && k % 3 == 0) shot(mc, "mine_cockpit", k);
        // the aim goes to the server with the player's moves, which stop while a scripted camera is in use: it was
        // set while the player was the camera, as was the trigger (a click from a scripted camera goes to what it sees)
        if (k == 42) toolCamera(mc, -3.5, 3.2, 2.9);
        if (k >= 48 && k <= 72 && k % 3 == 0) shot(mc, "mine_back", k);
        if (k == 74) toolCamera(mc, -0.5, 5.0, 2.5);
        if (k >= 78 && k <= 96 && k % 3 == 0) shot(mc, "mine_side", k);
        if (k == 98) toolCamera(mc, -7.0, 3.5, 5.0, 1.2);
        if (k >= 102 && k <= 120 && k % 3 == 0) shot(mc, "mine_wide", k);
        if (k == 122) { fire = false; aim = null; done = true; }
    }

    /** The chainsaw on a tree, from outside: from its tool side and a little ahead (helmet and saw in view), the tree as it comes down. */
    private static void tree(Minecraft mc, int k) {
        var o = mc.options;
        if (k == -SETTLE) { o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); aim = target; }
        if (k == -6) fire = true; // (before the scripted camera: see mine())
        if (k == 0) toolCamera(mc, TREE_CAM[0], TREE_CAM[1], TREE_CAM[2], 2.6);
        if (k >= 2 && k <= 40 && k % 2 == 0) shot(mc, "tree_side", k);
        if (k == 62) { fire = false; aim = null; done = true; }
    }

    /**
     * The EM projector on a creeper: it comes in, the beam catches it, the pilot looks up and it rises into the air.
     * The grab and the look up happen while the player is the camera (their aim reaches the server only then), then
     * still cameras behind and in front (on the hand's side, then the projector's).
     */
    private static void em(Minecraft mc, int k) {
        var o = mc.options;
        if (k == -SETTLE) { o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); yaw = setYaw; pitch = 10; }
        if (k == -20) creeper(mc);
        if (k == -14) aim = target;
        if (k == -6) fire = true;
        if (k == 6) { aim = null; yaw = setYaw; pitch = -22; } // caught: look up, the creeper rises 3.5 blocks out along the view
        // the in-game view from behind dropped into the grass when the pilot looked up: a still camera behind instead
        if (k == 14) toolCamera(mc, -4.5, -2.5, 3.0, 3.5);
        if (k >= 18 && k <= 27 && k % 3 == 0) shot(mc, "em_back", k);
        // in front, on the hand's side: the raised projector arm hid the helmet from its own side
        if (k == 30) toolCamera(mc, 6.5, -4.0, 1.0, 3.0);
        if (k >= 34 && k <= 58 && k % 3 == 0) shot(mc, "em_front", k);
        if (k == 60) toolCamera(mc, 6.5, 4.0, 1.0, 3.0);
        if (k >= 64 && k <= 82 && k % 3 == 0) shot(mc, "em_front_tool", k);
        if (k == 84) { fire = false; done = true; }
    }

    /** Server: a creeper 6 blocks ahead of the suit, facing it; {@code target} is its middle (the beam aims there). */
    private static void creeper(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            Vec3 p = setPos.add(forward().scale(6));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
            if (EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND) instanceof Mob mob) {
                mob.snapTo(p.x, y, p.z, setYaw + 180, 0);
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
                target = new Vec3(p.x, y + 0.85, p.z);
            }
        });
    }

    /**
     * A still camera {@code back} blocks along the suit's facing (negative: behind) and {@code side} to its tool side
     * (its left; negative: right), {@code up} blocks up, looking at the point between the suit and its target at
     * {@code lookUp} blocks up.
     */
    private static void toolCamera(Minecraft mc, double along, double side, double up, double lookUp) {
        Vec3 forward = forward(), left = new Vec3(forward.z, 0, -forward.x);
        Vec3 t = target != null ? target : setPos.add(forward.scale(3));
        Vec3 mid = setPos.add(t).scale(0.5);
        camera(mc, setPos.add(forward.scale(along)).add(left.scale(side)).add(0, up, 0), new Vec3(mid.x, setPos.y + lookUp, mid.z));
    }

    private static void toolCamera(Minecraft mc, double along, double side, double up) {
        toolCamera(mc, along, side, up, 1.8);
    }

    /**
     * Real (generated) chunks on an 8-block grid round {@code origin}: a rock face for the drill. Flat dry ground
     * where the suit stands and 8 blocks behind it, a face of stone 2-3 blocks ahead at least 4 blocks high (3 wide,
     * mostly stone), open ground to the suit's tool side for the camera. Score: the face's stone + ore count.
     */
    private static Spot findWall(ServerLevel level, BlockPos origin) {
        Heights h = new Heights(level);
        java.util.List<Spot> rough = new java.util.ArrayList<>();
        for (int gx = -256; gx <= 256; gx += 4) {
            for (int gz = -256; gz <= 256; gz += 4) {
                int x = origin.getX() + gx, z = origin.getZ() + gz;
                int h0 = h.surface(x, z);
                for (int i = 0; i < 8; i++) {
                    float yaw = i * 45F;
                    double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
                    int face = Math.max(h.surface(x + (int) Math.round(dx * 3), z + (int) Math.round(dz * 3)),
                            h.surface(x + (int) Math.round(dx * 4), z + (int) Math.round(dz * 4)));
                    if (face - h0 < 4 || h.wet(x, z)) {
                        continue;
                    }
                    boolean flat = true;
                    for (int d = -3; d <= 1 && flat; d += 1) {
                        int px = x + (int) Math.round(dx * d), pz = z + (int) Math.round(dz * d);
                        flat = Math.abs(h.surface(px, pz) - h0) <= 1 && !h.wet(px, pz);
                    }
                    if (flat) {
                        rough.add(new Spot(new BlockPos(x, h0, z), yaw, face - h0));
                    }
                }
            }
        }
        rough.sort(Comparator.comparingDouble(s -> -s.score()));
        Spot best = null;
        for (int i = 0; i < Math.min(150, rough.size()); i++) {
            Spot s = checkWall(level, rough.get(i));
            if (s != null && (best == null || s.score() > best.score())) {
                best = s;
            }
        }
        PowerArmor.LOGGER.info("[showcase] wall: {} rough candidates, picked {} (rejected: ground {}, camera {}, not rock {}, no face {})",
                rough.size(), best, REJECT[0], REJECT[1], REJECT[2], REJECT[3]);
        return best != null ? best : new Spot(origin, 0, 0);
    }

    private static final int[] REJECT = new int[4];

    private static Spot checkWall(ServerLevel level, Spot rough) {
        double dx = -Mth.sin(rough.yaw() * Mth.DEG_TO_RAD), dz = Mth.cos(rough.yaw() * Mth.DEG_TO_RAD);
        int x0 = rough.at().getX(), z0 = rough.at().getZ();
        level.getChunk(x0 >> 4, z0 >> 4);
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, z0);
        for (int d = -3; d <= 1; d++) {
            for (int w = -1; w <= 1; w++) {
                int x = x0 + (int) Math.round(dx * d - dz * w), z = z0 + (int) Math.round(dz * d + dx * w);
                level.getChunk(x >> 4, z >> 4);
                if (Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - g) > 1
                        || !level.getFluidState(new BlockPos(x, g - 1, z)).isEmpty()) {
                    REJECT[0]++;
                    return null; // the suit stands on level dry ground
                }
            }
        }
        // the cameras (see mine()) and a block round them must be in the open
        double[][] cams = {{-3.5, 3.2, 2.9}, {-0.5, 5.0, 2.5}};
        for (double[] c : cams) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    // the tool side is the suit's left: (dz, -dx) for a facing (dx, dz)
                    int x = (int) Math.floor(x0 + 0.5 + dx * c[0] + dz * c[1]) + ox, z = (int) Math.floor(z0 + 0.5 + dz * c[0] - dx * c[1]) + oz;
                    level.getChunk(x >> 4, z >> 4);
                    if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > g + c[2] - 0.5) {
                        REJECT[1]++;
                        return null;
                    }
                }
            }
        }
        // the face: the first solid block along the view at chest height, 2-4 blocks ahead
        for (int d = 2; d <= 4; d++) {
            int stone = 0, ore = 0;
            boolean solid = true;
            for (int w = -1; w <= 1 && solid; w++) {
                for (int y = 0; y < 4 && solid; y++) {
                    BlockPos p = new BlockPos(x0 + (int) Math.round(dx * d - dz * w), g + y, z0 + (int) Math.round(dz * d + dx * w));
                    var state = level.getBlockState(p);
                    solid = !state.isAir() && state.getFluidState().isEmpty();
                    stone += state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) ? 1 : 0;
                    ore += state.is(net.minecraft.tags.BlockTags.ORES) ? 1 : 0;
                }
            }
            if (!solid) {
                continue;
            }
            if (stone + ore < 8) {
                REJECT[2]++;
                return null; // dirt or gravel, not a rock face
            }
            BlockPos target = new BlockPos(x0 + (int) Math.round(dx * d), g + 1, z0 + (int) Math.round(dz * d));
            return new Spot(rough.at().atY(g), rough.yaw(), stone + 3 * ore, target);
        }
        REJECT[3]++;
        return null;
    }

    /**
     * Real chunks round the nearest {@code biome}: a lone tree for the chainsaw. Every trunk within 96 blocks, the suit
     * 3 blocks from it on one of 8 sides: level dry ground with no trunks from 4 behind the suit to the tree, an open
     * stretch 4-7 blocks to its tool side for the camera, no other tree within 10 blocks. Score: the tree's size.
     */
    private static Spot findTree(ServerLevel level, ResourceKey<Biome> biome, BlockPos origin) {
        var found = level.findClosestBiome3d(hb -> hb.is(biome), origin, 6000, 32, 64);
        BlockPos centre = found == null ? origin : found.getFirst();
        Spot best = null;
        int trunks = 0;
        for (int gx = -160; gx <= 160; gx++) {
            for (int gz = -160; gz <= 160; gz++) {
                int x = centre.getX() + gx, z = centre.getZ() + gz;
                level.getChunk(x >> 4, z >> 4);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos p = new BlockPos(x, top, z);
                if (!level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) {
                    continue;
                }
                while (level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LOGS)) {
                    p = p.below();
                }
                if (!level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
                    continue; // a branch, not the trunk's foot
                }
                trunks++;
                for (int i = 0; i < 8; i++) {
                    float yaw = i * 45F;
                    double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
                    // 4 blocks back: an oak's canopy hangs 2-3 blocks out and hid the suit's helmet at 3
                    int x0 = p.getX() - (int) Math.round(dx * 4), z0 = p.getZ() - (int) Math.round(dz * 4);
                    int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
                    if (Math.abs(g - p.getY()) > 1) {
                        continue;
                    }
                    Spot s = checkTree(level, x0, z0, g, yaw, dx, dz, p);
                    if (s != null && (best == null || s.score() > best.score())) {
                        best = s;
                    }
                }
            }
        }
        PowerArmor.LOGGER.info("[showcase] tree: {} trunks, picked {}", trunks, best);
        return best != null ? best : new Spot(centre, 0, 0);
    }

    private static Spot checkTree(ServerLevel level, int x0, int z0, int g, float yaw, double dx, double dz, BlockPos trunk) {
        // level dry ground with no trunks from 4 behind the suit to the tree (2 either side; canopies may hang over),
        // and the camera's stretch 4-9 blocks to the tool side clear of trunks and low leaves
        for (int dd = -4; dd <= 2; dd++) {
            for (int w = -2; w <= 7; w++) {
                boolean cam = w >= 4;
                if (!cam && w > 2 || cam && dd < -1) {
                    continue;
                }
                int x = x0 + (int) Math.round(dx * dd + dz * w), z = z0 + (int) Math.round(dz * dd - dx * w); // w: to the suit's left (tool side)
                level.getChunk(x >> 4, z >> 4);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                boolean isTrunk = x == trunk.getX() && z == trunk.getZ();
                if (!isTrunk && (Math.abs(ground - g) > (cam ? 2 : 1) || !level.getFluidState(new BlockPos(x, ground - 1, z)).isEmpty()
                        || cam && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > g + 3)) {
                    return null;
                }
            }
        }
        java.util.List<BlockPos> logs = ChainsawItem.treeAbove(level, trunk);
        if (logs.size() < 4) {
            return null; // not a natural tree (or a stump)
        }
        // a lone tree: no other trunk within 10 blocks (the tree reads on its own, nothing between it and the camera)
        java.util.Set<BlockPos> own = new java.util.HashSet<>(logs);
        own.add(trunk);
        for (BlockPos p : BlockPos.betweenClosed(trunk.offset(-10, 0, -10), trunk.offset(10, 2, 10))) {
            if (!own.contains(p) && level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) {
                return null;
            }
        }
        // nothing over the suit's head (the canopy hid the helmet)
        for (BlockPos p : BlockPos.betweenClosed(x0 - 1, g, z0 - 1, x0 + 1, g + 4, z0 + 1)) {
            if (!level.getBlockState(p).isAir() && !level.getBlockState(p).canBeReplaced()) {
                return null;
            }
        }
        // the camera's line of sight (see tree()): air all the way from it to the suit and the tree (tall grass hid both)
        Vec3 suit = new Vec3(x0 + 0.5, g, z0 + 0.5), forward = new Vec3(dx, 0, dz), left = new Vec3(dz, 0, -dx);
        Vec3 eye = suit.add(forward.scale(TREE_CAM[0])).add(left.scale(TREE_CAM[1])).add(0, TREE_CAM[2], 0);
        for (Vec3 to : new Vec3[] {suit.add(0, 1.0, 0), suit.add(0, 2.4, 0), Vec3.atBottomCenterOf(trunk).add(0, 1.0, 0)}) {
            for (int i = 0; i <= 10; i++) {
                BlockPos p = BlockPos.containing(eye.lerp(to, i / 10.0 * 0.85));
                if (!level.getBlockState(p).isAir() && !own.contains(p)) {
                    return null;
                }
            }
        }
        // aim at the second log: a ray at the foot of the trunk hits the tall grass first (plants have an outline)
        return new Spot(new BlockPos(x0, g, z0), yaw, logs.size(), trunk.above());
    }

    /** The tree camera: along the suit's facing, to its tool side, up (see toolCamera). */
    private static final double[] TREE_CAM = {3.0, 5.0, 2.4};

    /** A place for a set: where the suit stands and which way it looks (yaw 0 looks +Z). */
    private record Spot(BlockPos at, float yaw, double score, @org.jetbrains.annotations.Nullable BlockPos target) {
        Spot(BlockPos at, float yaw, double score) {
            this(at, yaw, score, null);
        }
    }

    /** The block a set's tool works on (the rock face, the trunk), or null. */
    private static volatile Vec3 target;

    /**
     * Server: go to the pinned place, or find one in {@code biome} near spawn (open ground: a walkable dry stretch ahead, no trees between the
     * third-person camera and 32 blocks ahead, landscape rising on the horizon) and put the player in a fresh suit there
     * (firebox + coal, helmet, chaingun, nuggets, a lived-in hotbar). Sets {@code ready} when done.
     */
    /** What a set's suit wears: its paint, engine and tool. */
    private record Loadout(DyeColor paint, Item engine, Item tool, @org.jetbrains.annotations.Nullable HelmetStyle helmet) {
        Loadout(DyeColor paint, Item engine, Item tool) {
            this(paint, engine, tool, null);
        }
    }

    private static java.util.function.Function<ServerLevel, Spot> pin(int x, int y, int z, float yaw) {
        return level -> new Spot(new BlockPos(x, y, z), yaw, 0);
    }

    private static java.util.function.Function<ServerLevel, Spot> pin(int x, int y, int z, float yaw, BlockPos target) {
        return level -> new Spot(new BlockPos(x, y, z), yaw, 0, target);
    }

    private static void moveTo(Minecraft mc, String name, ResourceKey<Biome> biome, long time, java.util.function.Function<ServerLevel, Spot> where, Loadout loadout) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, time);
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            boolean water = biome.identifier().getPath().contains("ocean");
            Spot spot = where.apply(level);
            BlockPos at = spot.at();
            float yaw = spot.yaw();
            if (player.getVehicle() instanceof PowerArmorEntity old) {
                player.stopRiding();
                old.discard();
            }
            level.getChunk(at.getX() >> 4, at.getZ() >> 4); // generate it now: an unloaded chunk's height reads as the world's bottom
            // (the sea floor under water: the other heightmaps stop at the water's surface)
            int ground = level.getHeight(water ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            Vec3 pos = new Vec3(at.getX() + 0.5, ground, at.getZ() + 0.5);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(pos.x, pos.y, pos.z);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            suit.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(loadout.engine()));
            // the star reactor runs on a Nether Star in the fuel slot (never used up); the others burn coal
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, loadout.engine() == ModItems.STAR_REACTOR.get()
                    ? new ItemStack(Items.NETHER_STAR) : new ItemStack(Items.COAL, 48));
            ItemStack helmet = new ItemStack(ModItems.SUIT_HELMET.get());
            if (loadout.helmet() != null) {
                helmet.set(ModDataComponents.HELMET_STYLE.get(), loadout.helmet());
            }
            suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, helmet);
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(loadout.tool()));
            suit.setPaint(loadout.paint());
            suit.setStoredNuggets(128);
            suit.snapTo(pos.x, pos.y, pos.z, yaw, 0);
            suit.setYBodyRot(yaw);
            suit.setYHeadRot(yaw);
            level.addFreshEntity(suit);
            player.startRiding(suit, true, true);
            // a lived-in hotbar: the selected slot stays empty (right click goes to the tool)
            var inv = player.getInventory();
            inv.clearContent();
            inv.setSelectedSlot(0);
            inv.setItem(1, new ItemStack(Items.IRON_PICKAXE));
            inv.setItem(2, new ItemStack(Items.TORCH, 37));
            inv.setItem(3, new ItemStack(Items.COBBLESTONE, 52));
            inv.setItem(4, new ItemStack(Items.OAK_LOG, 18));
            inv.setItem(6, new ItemStack(Items.COAL, 21));
            inv.setItem(7, new ItemStack(Items.IRON_NUGGET, 64));
            inv.setItem(8, new ItemStack(Items.BREAD, 9));
            setPos = pos;
            setYaw = yaw;
            // aim a little off the block's centre: looking diagonally at the centre the ray hits the block's edge, the
            // face it hits flips from tick to tick and the saw starts over every time (the tree never fell)
            Vec3 side = new Vec3(Mth.cos(yaw * Mth.DEG_TO_RAD), 0, Mth.sin(yaw * Mth.DEG_TO_RAD)).scale(0.3);
            target = spot.target() == null ? null : Vec3.atCenterOf(spot.target()).add(side);
            String biomeName = level.getBiome(BlockPos.containing(pos)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
            log(String.format(Locale.ROOT, "%s,%s,%.1f,%.1f,%.1f,%.0f,%.1f", name, biomeName, pos.x, pos.y, pos.z, yaw, spot.score()));
            ready = true;
        });
    }

    /**
     * A coral reef in a warm ocean: on a grid of real (generated) columns round the biome's nearest spot, the sea floor
     * 6-12 blocks deep with the most coral within 6 blocks, looking along the flattest stretch of floor with the most
     * coral ahead (the suit walks there; its eye and the cameras must stay under water).
     */
    private static Spot findReef(ServerLevel level, BlockPos origin) {
        var found = level.findClosestBiome3d(h -> h.is(Biomes.WARM_OCEAN), origin, 6000, 32, 64);
        BlockPos centre = found == null ? origin : found.getFirst();
        Spot best = null;
        for (int gx = -64; gx <= 64; gx += 8) {
            for (int gz = -64; gz <= 64; gz += 8) {
                int x = centre.getX() + gx, z = centre.getZ() + gz;
                int depth = depth(level, x, z);
                if (depth < 6 || depth > 12) {
                    continue;
                }
                int floor = floor(level, x, z);
                for (int i = 0; i < 8; i++) {
                    float yaw = i * 45F;
                    double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
                    boolean flat = true;
                    for (int d = -7; d <= 12 && flat; d++) {
                        int px = x + (int) Math.round(dx * d), pz = z + (int) Math.round(dz * d);
                        flat = Math.abs(floor(level, px, pz) - floor) <= 2 && depth(level, px, pz) >= 5;
                    }
                    if (!flat) {
                        continue;
                    }
                    int coral = coral(level, new BlockPos(x + (int) Math.round(dx * 8), floor, z + (int) Math.round(dz * 8)), 7);
                    if (best == null || coral > best.score()) {
                        best = new Spot(new BlockPos(x, floor, z), yaw, coral);
                    }
                }
            }
        }
        PowerArmor.LOGGER.info("[showcase] reef: picked {}", best);
        return best != null ? best : new Spot(centre, 0, 0);
    }

    private static int floor(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    /** Water depth over the sea floor (0 on land). */
    private static int depth(ServerLevel level, int x, int z) {
        int floor = floor(level, x, z);
        return level.getFluidState(new BlockPos(x, floor, z)).isEmpty() ? 0 : level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - floor;
    }

    /** Coral blocks and plants within {@code r} blocks (and 4 up) of {@code at}. */
    private static int coral(ServerLevel level, BlockPos at, int r) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 4, r))) {
            var state = level.getBlockState(p);
            if (state.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS) || state.is(net.minecraft.tags.BlockTags.CORALS)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Two passes: the world generator's height estimate (cheap, no chunks) ranks points on a 16-block grid round the
     * biome's nearest spot (one view direction per point, the best), then the best few are checked in real (generated)
     * chunks for trees in the corridor the suit walks and the camera looks along.
     */
    private static Spot findSpot(ServerLevel level, ResourceKey<Biome> biome, BlockPos origin, float minYaw, float maxYaw) {
        var found = level.findClosestBiome3d(h -> h.is(biome), origin, 6000, 16, 32);
        BlockPos centre = found == null ? origin : found.getFirst();
        Heights heights = new Heights(level);
        java.util.List<Spot> spots = new java.util.ArrayList<>();
        for (int gx = -192; gx <= 192; gx += 16) {
            for (int gz = -192; gz <= 192; gz += 16) {
                int x = centre.getX() + gx, z = centre.getZ() + gz;
                int y = heights.surface(x, z);
                if (!level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).is(biome)) {
                    continue;
                }
                BlockPos p = new BlockPos(x, y, z);
                Spot bestHere = null; // one view per point: the tree check below then tries many places, not one place's yaws
                for (float yaw = minYaw; yaw < maxYaw + 0.1F && yaw < 360; yaw += 15F) {
                    double score = viewScore(heights, p, yaw);
                    if (score > -500 && (bestHere == null || score > bestHere.score())) {
                        bestHere = new Spot(p, yaw, score);
                    }
                }
                if (bestHere != null) {
                    spots.add(bestHere);
                }
            }
        }
        spots.sort(Comparator.comparingDouble(s -> -s.score()));
        Spot best = null;
        int bestTrees = Integer.MAX_VALUE;
        for (int i = 0; i < Math.min(25, spots.size()); i++) {
            int trees = trees(level, spots.get(i));
            if (trees >= 0 && trees < bestTrees) {
                bestTrees = trees;
                best = spots.get(i);
            }
            if (trees == 0) {
                break;
            }
        }
        PowerArmor.LOGGER.info("[showcase] {}: {} candidate places, picked {} ({} tree columns)", biome.identifier(), spots.size(), best, bestTrees);
        return best != null ? best : new Spot(centre, minYaw, 0);
    }

    /** The generator's surface / sea-floor estimate per column (no chunks generated), cached at 2-block resolution. */
    private static final class Heights {
        private final ServerLevel level;
        private final net.minecraft.world.level.chunk.ChunkGenerator gen;
        private final net.minecraft.world.level.levelgen.RandomState random;
        private final java.util.Map<Long, int[]> cache = new java.util.HashMap<>();

        Heights(ServerLevel level) {
            this.level = level;
            this.gen = level.getChunkSource().getGenerator();
            this.random = level.getChunkSource().randomState();
        }

        private int[] column(int x, int z) {
            int qx = x >> 1 << 1, qz = z >> 1 << 1;
            return cache.computeIfAbsent(BlockPos.asLong(qx, 0, qz), k -> new int[] {
                    gen.getBaseHeight(qx, qz, Heightmap.Types.WORLD_SURFACE_WG, level, random),
                    gen.getBaseHeight(qx, qz, Heightmap.Types.OCEAN_FLOOR_WG, level, random)});
        }

        int surface(int x, int z) {
            return column(x, z)[0];
        }

        boolean wet(int x, int z) {
            int[] c = column(x, z);
            return c[0] != c[1];
        }
    }

    /**
     * The same checks as {@link #viewScore} on the real (generated) chunks, which the generator's estimate doesn't know:
     * caves and ravines cut into the surface, lakes, trees. -1: unusable; else the columns topped by leaves or logs in
     * the corridor from 10 blocks behind the suit to 32 ahead (fewer is better).
     */
    private static int trees(ServerLevel level, Spot spot) {
        double dx = -Mth.sin(spot.yaw() * Mth.DEG_TO_RAD), dz = Mth.cos(spot.yaw() * Mth.DEG_TO_RAD);
        int x0 = spot.at().getX(), z0 = spot.at().getZ();
        level.getChunk(x0 >> 4, z0 >> 4);
        int h0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        int count = 0;
        for (int d = -10; d <= 90; d++) {
            for (int w = d <= 32 ? -6 : 0; w <= (d <= 32 ? 12 : 0); w++) {
                int x = x0 + (int) Math.round(dx * d - dz * w), z = z0 + (int) Math.round(dz * d + dx * w);
                level.getChunk(x >> 4, z >> 4);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (d > 32) {
                    if (ground > h0 + 4 + (d - 30) * 0.25) {
                        return -1; // a wall in the view
                    }
                    continue;
                }
                BlockPos below = new BlockPos(x, ground - 1, z);
                if (!level.getFluidState(below).isEmpty() || Math.abs(ground - h0) > 3) {
                    return -1; // water, a hole (a cave or ravine reaching the surface) or a hill
                }
                var top = level.getBlockState(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z));
                if (top.is(net.minecraft.tags.BlockTags.LEAVES) || top.is(net.minecraft.tags.BlockTags.LOGS)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * How good the view along {@code yaw} from {@code at} is (below -500: unusable). Usable: dry, gentle ground from 10
     * blocks behind (the third-person camera) to 30 ahead, 6 blocks either side (the suit walks, monsters come in, the
     * side camera stands 11 to the right), and nothing rising like a wall in the next 60 blocks (an open view). Score: the
     * highest ground 100-300 blocks out above the start (mountains on the horizon).
     */
    private static double viewScore(Heights h, BlockPos at, float yaw) {
        double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int h0 = at.getY();
        int prev = h0;
        for (int d = -10; d <= 30; d += 2) {
            for (int w = -6; w <= 12; w += 3) {
                int x = at.getX() + (int) Math.round(dx * d - dz * w), z = at.getZ() + (int) Math.round(dz * d + dx * w);
                if (h.wet(x, z) || Math.abs(h.surface(x, z) - h0) > 3) {
                    return -1000; // water, a hill or a hole
                }
            }
            int x = at.getX() + (int) Math.round(dx * d), z = at.getZ() + (int) Math.round(dz * d);
            int surface = h.surface(x, z);
            if (d > -10 && Math.abs(surface - prev) > 1.5) {
                return -1000; // a step the suit would have to jump
            }
            prev = surface;
        }
        for (int d = 32; d <= 90; d += 4) {
            int x = at.getX() + (int) Math.round(dx * d), z = at.getZ() + (int) Math.round(dz * d);
            if (h.surface(x, z) > h0 + 4 + (d - 30) * 0.25) {
                return -1000; // a wall in the view
            }
        }
        int high = Integer.MIN_VALUE;
        for (int d = 100; d <= 300; d += 10) {
            int x = at.getX() + (int) Math.round(dx * d), z = at.getZ() + (int) Math.round(dz * d);
            high = Math.max(high, h.surface(x, z));
        }
        return high - h0;
    }

    /** Server: four zombies, a skeleton and a creeper spread 11-17 blocks ahead of the suit, coming for the pilot. */
    private static void monsters(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            Vec3 pos = setPos;
            float yaw = setYaw;
            Vec3 forward = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            EntityType<?>[] types = {EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.CREEPER, EntityTypes.ZOMBIE};
            double[] side = {-5, -2.5, 0.5, 2.5, 4.5, 7};
            double[] ahead = {12, 15, 17, 11, 14, 16};
            Item[] helmets = {Items.IRON_HELMET, Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.GOLDEN_HELMET, Items.IRON_HELMET, Items.LEATHER_HELMET};
            for (int i = 0; i < types.length; i++) {
                Vec3 p = pos.add(forward.scale(ahead[i])).add(right.scale(side[i]));
                level.getChunk(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
                if (types[i].create(level, EntitySpawnReason.COMMAND) instanceof Mob mob) {
                    mob.snapTo(p.x, y, p.z, yaw + 180, 0);
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
                    if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                        zombie.setBaby(false); // finalizeSpawn may roll a baby: too small to read at this distance
                    }
                    mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(helmets[i])); // no burning before dark
                    mob.setPersistenceRequired();
                    level.addFreshEntity(mob);
                }
            }
        });
    }

    /** Over the suit's tool shoulder (its left), from behind and above: the tracers run away into the monsters. */
    private static void shoulderCamera(Minecraft mc) {
        Vec3 forward = forward(), left = new Vec3(forward.z, 0, -forward.x);
        camera(mc, setPos.add(forward.scale(-4.5)).add(left.scale(2.6)).add(0, 3.4, 0), setPos.add(forward.scale(9)).add(0, 1.2, 0));
    }

    /**
     * The monsters' view: behind their line, looking back at the suit through them with a long lens (FOV 40: the suit
     * stays big 17 blocks away), the tracers coming at us. Put back to the player's FOV after the shots.
     */
    private static void frontCamera(Minecraft mc) {
        Vec3 forward = forward(), left = new Vec3(forward.z, 0, -forward.x);
        mc.options.fov().set(40);
        camera(mc, setPos.add(forward.scale(17)).add(left.scale(1.5)).add(0, 2.4, 0), setPos.add(0, 1.8, 0));
    }

    /** Server: climb out, put the suit back on the set's spot and climb in again. */
    private static void reseat(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            if (player.getVehicle() instanceof PowerArmorEntity suit) {
                player.stopRiding();
                suit.snapTo(setPos.x, setPos.y, setPos.z, setYaw, 0);
                suit.setYBodyRot(setYaw);
                suit.setYHeadRot(setYaw);
                suit.setDeltaMovement(Vec3.ZERO);
                player.startRiding(suit, true, true);
            }
        });
    }

    /** A still camera {@code ahead} blocks in front of where the suit is now (the client's copy), a little to its tool side, {@code up} blocks up. */
    private static void pathCamera(Minecraft mc, double ahead, double up) {
        if (!(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        Vec3 forward = forward(), left = new Vec3(forward.z, 0, -forward.x);
        Vec3 at = suit.position();
        camera(mc, at.add(forward.scale(ahead)).add(left.scale(1.2)).add(0, up, 0), at.add(forward.scale(ahead * 0.45)).add(0, 1.7, 0));
    }

    /** From the side (the suit's right), far enough out to have the suit on the left and the monsters on the right. */
    private static void sideCamera(Minecraft mc) {
        Vec3 forward = forward(), right = new Vec3(-forward.z, 0, forward.x);
        camera(mc, setPos.add(forward.scale(3)).add(right.scale(11)).add(0, 2.6, 0), setPos.add(forward.scale(7)).add(0, 1.4, 0));
    }

    private static Vec3 forward() {
        return new Vec3(-Mth.sin(setYaw * Mth.DEG_TO_RAD), 0, Mth.cos(setYaw * Mth.DEG_TO_RAD));
    }

    /** A scripted camera with its eye at {@code eye}, looking at {@code at}; GUI hidden (the hand pass would draw the pilot's viewmodel). */
    private static void camera(Minecraft mc, Vec3 eye, Vec3 at) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0); // lookCamera places the stand's feet
        AutoPilot.lookCamera(mc, eye.subtract(drop), at.subtract(drop));
    }

    private static void shot(Minecraft mc, String name, int t) {
        Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "showcase_%s_%03d.png", name, t), mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
    }

    /** A fresh normal (not flat) world with a fixed seed, survival, time and weather frozen, no natural spawns. */
    private static void createWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
                mc.gui.screen());
    }

    private static void open(Minecraft mc) {
        try {
            csv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("showcase.csv"));
            csv.write("set,biome,x,y,z,yaw,viewScore\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("showcase csv", e);
        }
        AutoPilot.onServer(mc, player -> {
            var server = player.level().getServer();
            var rules = player.level().getGameRules();
            rules.set(GameRules.ADVANCE_TIME, false, server);
            rules.set(GameRules.ADVANCE_WEATHER, false, server);
            rules.set(GameRules.SPAWN_MOBS, false, server);
            rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
        });
    }

    private static void log(String line) {
        try {
            if (csv != null) {
                csv.write(line + "\n");
                csv.flush();
            }
        } catch (IOException e) {
            PowerArmor.LOGGER.error("showcase csv", e);
        }
    }

    private static void finish(Minecraft mc) {
        var o = mc.options;
        o.keyUp.setDown(false);
        o.keySprint.setDown(false);
        o.keyUse.setDown(false);
        mc.setCameraEntity(mc.player);
        DevUtil.hideGui(mc, false);
        if (renderDistance > 0) {
            o.renderDistance().set(renderDistance);
        }
        o.fov().set(fov);
        try {
            if (csv != null) {
                csv.close();
            }
        } catch (IOException ignored) {
        }
        PowerArmor.LOGGER.info("[showcase] done");
        mc.stop();
    }

    private ShowcaseScene() {}
}
