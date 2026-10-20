package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the working minds (Phase 8 step 4), run at the end of the mech scene (AutoPilot.mechanics):
 * <ul>
 *   <li>drill: a 3-high rock outcrop with coal and iron ore in front of home, a lava pocket and a hole in the floor; the
 *   suit clears its hall while two allays (one given coal, one raw iron) carry those drops to the player; the stone
 *   and everything else stays on the floor;</li>
 *   <li>chainsaw: three oaks round a second home; the suit fells them one by one.</li>
 * </ul>
 * Logs run/autopilot_work.csv (rock left, what the allays delivered, items on the ground, lava/hole walls intact).
 */
final class WorkScene {
    private static volatile BlockPos drillHome, sawHome;
    private static volatile PowerArmorEntity suit;
    private static final List<Allay> allays = new CopyOnWriteArrayList<>();
    private static BufferedWriter csv;
    private static String phase = "-";
    /** Rock outcrop, relative to the drill home: x -5..5, z 2..10, y 0..2 (the hall's height). */
    private static final int X0 = -5, X1 = 5, Z0 = 2, Z1 = 10;
    private static final BlockPos LAVA = new BlockPos(4, 1, 9), HOLE = new BlockPos(-3, -1, 6);

    /** Scene step {@code s} (ticks since the scene started). Returns false once it's over. */
    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        switch (s) {
            case 0 -> { phase = "drill"; setup(mc); }
            case 4 -> open(mc);
            case 6 -> camera(mc, drillHome, new Vec3(0, 13, -9), new Vec3(0, 0, 6));
            case 40, 100, 160, 220, 300, 380, 460, 540 -> AutoPilot.shot(mc, "work_drill_" + s);
            case 120 -> camera(mc, drillHome, new Vec3(-9, 4, 1), new Vec3(0, 1.5, 6)); // low, from the side: into the hall
            case 130, 200 -> AutoPilot.shot(mc, "work_drill_low_" + s);
            case 210 -> camera(mc, drillHome, new Vec3(0, 13, -9), new Vec3(0, 0, 6));
            case 560 -> { phase = "saw"; sawSetup(mc); }
            case 566 -> camera(mc, sawHome, new Vec3(-12, 7, -8), new Vec3(0, 3, 3));
            case 580, 610, 640, 670, 700, 740, 780 -> AutoPilot.shot(mc, "work_saw_" + s);
            case 800 -> { close(); return false; }
            default -> {}
        }
        if (s >= 5 && s < 800) {
            log(mc, s);
        }
        return true;
    }

    /** Server: 40 blocks east of the mind scene, by day. The rock, the suit (drill, home at the near edge), the player as
     *  its owner and the allays' friend (creative: the allays bring the drops to them), two allays with their item. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            int x = player.getBlockX() + 60, z = player.getBlockZ();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos home = new BlockPos(x, y, z);
            var random = level.getRandom();
            for (BlockPos p : BlockPos.betweenClosed(home.offset(X0, 0, Z0), home.offset(X1, 2, Z1))) {
                float r = random.nextFloat();
                level.setBlock(p, (r < 0.08F ? Blocks.COAL_ORE : r < 0.12F ? Blocks.IRON_ORE : r < 0.3F ? Blocks.DIRT : Blocks.STONE)
                        .defaultBlockState(), 3);
            }
            level.setBlock(home.offset(LAVA), Blocks.LAVA.defaultBlockState(), 3);
            level.setBlock(home.offset(HOLE), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(home.offset(HOLE).below(), Blocks.AIR.defaultBlockState(), 3);
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 16)); s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.DRILL.get()));
            s.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            s.setOwnerId(player.getUUID());
            s.setHome(home);
            level.addFreshEntity(s);
            player.teleportTo(x - 6.5, y, z - 3.5);
            allays.clear();
            for (Item item : new Item[] {Items.COAL, Items.RAW_IRON}) {
                Allay allay = net.minecraft.world.entity.EntityTypes.ALLAY.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                allay.snapTo(x - 4.5, y + 2, z - 2.5);
                allay.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item));
                allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, player.getUUID()); // what giving it the item does
                allay.setPersistenceRequired();
                level.addFreshEntity(allay);
                allays.add(allay);
            }
            suit = s;
            drillHome = home;
        });
    }

    /** Server: three oaks round a second home 30 blocks south; the suit is moved there with the chainsaw. */
    private static void sawSetup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            if (suit == null || drillHome == null) {
                return;
            }
            ServerLevel level = player.level();
            BlockPos home = drillHome.offset(0, 0, -30);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, home.getX(), home.getZ());
            home = new BlockPos(home.getX(), y, home.getZ());
            var oak = level.registryAccess().lookupOrThrow(Registries.FEATURE).get(TreeFeatures.OAK);
            for (int[] t : new int[][] {{-4, 6}, {3, 5}, {0, 10}}) {
                BlockPos base = home.offset(t[0], 0, t[1]);
                level.setBlock(base.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                BlockPos at = base;
                oak.ifPresent(tree -> tree.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), at));
            }
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINSAW.get()));
            suit.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            suit.setHome(home);
            player.teleportTo(home.getX() - 6.5, home.getY(), home.getZ() - 4.5);
            sawHome = home;
        });
    }

    private static void camera(Minecraft mc, BlockPos base, Vec3 from, Vec3 at) {
        if (base == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0); // lookCamera places the stand's feet
        AutoPilot.lookCamera(mc, Vec3.atBottomCenterOf(base).add(from).subtract(drop), Vec3.atBottomCenterOf(base).add(at).subtract(drop));
    }

    private static void open(Minecraft mc) {
        try {
            csv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_work.csv"));
            csv.write("tick,phase,x,z,goals,target,rockLeft,oresLeft,lavaWalls,holeColumn,ceilingDug,coalDelivered,ironDelivered,"
                    + "itemsOnGround,stacksOnGround,logsLeft,energy\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** One row per tick (server entities and blocks: singleplayer, same JVM). */
    private static void log(Minecraft mc, int s) {
        PowerArmorEntity server = suit;
        BlockPos home = drillHome;
        var player = mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        if (csv == null || server == null || home == null || player == null) {
            return;
        }
        ServerLevel level = player.level();
        int rock = 0, ores = 0, ceiling = 0;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(X0, 0, Z0), home.offset(X1, 2, Z1))) {
            var state = level.getBlockState(p);
            rock += state.isAir() || state.is(Blocks.LAVA) ? 0 : 1;
            ores += state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) ? 1 : 0;
        }
        for (BlockPos p : BlockPos.betweenClosed(home.offset(X0, 3, Z0), home.offset(X1, 3, Z1))) {
            ceiling += level.getBlockState(p).isAir() ? 0 : 1; // nothing was there: any block here would be wrong (0 = fine)
        }
        BlockPos lava = home.offset(LAVA);
        int lavaWalls = 0;
        for (var d : net.minecraft.core.Direction.values()) {
            lavaWalls += level.getBlockState(lava.relative(d)).isAir() ? 0 : 1;
        }
        int hole = 0;
        for (int dy = 0; dy < 3; dy++) {
            hole += level.getBlockState(home.offset(HOLE).above(1 + dy)).isAir() ? 0 : 1;
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(home).inflate(20));
        int onGround = items.stream().mapToInt(e -> e.getItem().getCount()).sum();
        int logs = 0;
        if (sawHome != null) {
            for (BlockPos p : BlockPos.betweenClosed(sawHome.offset(-8, 0, 0), sawHome.offset(8, 12, 14))) {
                logs += level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS) ? 1 : 0;
            }
        }
        try {
            csv.write(String.format(Locale.ROOT, "%d,%s,%.2f,%.2f,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n", s, phase,
                    server.getX() - home.getX() - 0.5, server.getZ() - home.getZ() - 0.5, server.runningGoalNames(),
                    server.getTarget() == null ? "-" : server.getTarget().getType().toShortString(), rock, ores, lavaWalls, hole, ceiling,
                    player.getInventory().countItem(Items.COAL), player.getInventory().countItem(Items.RAW_IRON), onGround, items.size(),
                    logs, server.getTotalEnergy()));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] work csv", e);
        }
    }

    private static void close() {
        try {
            if (csv != null) {
                csv.close();
            }
        } catch (IOException ignored) {
        }
    }

    private WorkScene() {}
}
