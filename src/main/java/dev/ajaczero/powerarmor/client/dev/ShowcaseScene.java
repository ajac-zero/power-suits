package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
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
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    /** Set being shot: 0 = walk, 1 = chaingun at dusk. */
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
            switch (set) {
                // Places are pinned once a run found a good one (the seed is fixed, so they stay the same): run a new
                // set with a null spot to search (findSpot), then copy the place from run/showcase.csv.
                // a sunflower-plains hilltop: a mountain and the sea on the left, a village ahead
                case 0 -> moveTo(mc, "walk", Biomes.SUNFLOWER_PLAINS, 6000, new Spot(new BlockPos(739, 116, -659), 0, 0));
                // plains below a village, looking west into the sunset (yaw 90 looks -X)
                case 1 -> moveTo(mc, "dusk", Biomes.PLAINS, DUSK, new Spot(new BlockPos(595, 70, -812), 90, 0));
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
        int k = t - setStart - SETTLE; // ticks since the set's first shot could be taken
        switch (set) {
            case 0 -> walk(mc, k);
            case 1 -> dusk(mc, k);
            default -> {}
        }
        return true;
    }

    private static boolean done;

    /** A walk: the cockpit with the survival HUD (fuel gauge, ammo), then the in-game view from behind. */
    private static void walk(Minecraft mc, int k) {
        var o = mc.options;
        if (k == -SETTLE) { DevUtil.hideGui(mc, false); o.setCameraType(CameraType.FIRST_PERSON); yaw = setYaw - 25; pitch = 3; } // the mountain and the sea
        if (k == 41) yaw = setYaw;
        if (k == 0) walk = true;
        if (k >= 20 && k <= 40 && k % 5 == 0) shot(mc, "cockpit", k);
        if (k == 42) { o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); pitch = 12; }
        if (k >= 50 && k <= 86 && k % 4 == 0) shot(mc, "back_walk", k);
        if (k == 88) sprint = true;
        if (k >= 100 && k <= 116 && k % 2 == 0) shot(mc, "back_sprint", k);
        if (k == 118) done = true;
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
        if (k == 44) { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); pitch = 6; }
        if (k >= 48 && k <= 56 && k % 2 == 0) shot(mc, "dusk_back", k);
        if (k == 58) { o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); pitch = 2; }
        if (k >= 62 && k <= 72 && k % 2 == 0) shot(mc, "dusk_cockpit", k);
        if (k == 74) done = true;
    }

    /** A place for a set: where the suit stands and which way it looks (yaw 0 looks +Z). */
    private record Spot(BlockPos at, float yaw, double score) {}

    /**
     * Server: go to the pinned place, or find one in {@code biome} near spawn (open ground: a walkable dry stretch ahead, no trees between the
     * third-person camera and 32 blocks ahead, landscape rising on the horizon) and put the player in a fresh suit there
     * (firebox + coal, helmet, chaingun, nuggets, a lived-in hotbar). Sets {@code ready} when done.
     */
    private static void moveTo(Minecraft mc, String name, ResourceKey<Biome> biome, long time, @org.jetbrains.annotations.Nullable Spot pinned) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, time);
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            Spot spot = pinned != null ? pinned : findSpot(level, biome, player.blockPosition().atY(80), 0, 360);
            BlockPos at = spot.at();
            float yaw = spot.yaw();
            if (player.getVehicle() instanceof PowerArmorEntity old) {
                player.stopRiding();
                old.discard();
            }
            level.getChunk(at.getX() >> 4, at.getZ() >> 4); // generate it now: an unloaded chunk's height reads as the world's bottom
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            Vec3 pos = new Vec3(at.getX() + 0.5, ground, at.getZ() + 0.5);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(pos.x, pos.y, pos.z);
            PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(suit);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 48));
            suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
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
            String biomeName = level.getBiome(BlockPos.containing(pos)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
            log(String.format(Locale.ROOT, "%s,%s,%.1f,%.1f,%.1f,%.0f,%.1f", name, biomeName, pos.x, pos.y, pos.z, yaw, spot.score()));
            ready = true;
        });
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
