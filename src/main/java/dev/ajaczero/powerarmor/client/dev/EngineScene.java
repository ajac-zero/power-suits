package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.SuitEngine;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the engines (Phase 9, design sec. 4b): the player pilots a firebox suit up a few steps and into a
 * glass-walled pool 4 deep: water covers the backpack, the firebox floods (the suit dies) and won't pack up. Its engine is
 * swapped for a tidal engine (in the game the pilot would climb out and use the menu): it runs free under water, walks
 * the bottom, climbs the stairs out and dies on the dry landing after its grace. A water bucket in the fuel slot runs it
 * on land; then a star reactor with a Nether Star sprints for free. Last, the menu with its engine slot.
 * Shots {@code ap_engine_*.png}, {@code ap_menu_engine.png}; log run/autopilot_engine.csv (server state each tick, and
 * the client's view of the synced flags). Runs alone with ./gradlew runAutopilotEngine (~35 s) and at the end of
 * runAutopilotMech.
 */
final class EngineScene {
    /** Pool layout along +Z from the suit's start (dz 0): steps up dz 2-5 (tops g..g+3), water dz 6-17 (blocks g..g+3,
     *  4 deep, glass sides), stairs out dz 14-17, then a dry landing at the lip's height to dz 50. */
    private static final int WATER_FROM = 6, WATER_TO = 17, STAIRS_OUT = 14, LANDING_TO = 50, DEPTH = 4;
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static volatile boolean packRefused;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        switch (s) {
            case 0 -> { phase = "firebox"; AutoPilot.releaseAll(); AutoPilot.aimTarget = null; setup(mc); }
            case 4 -> { if (!(mc.player.getVehicle() instanceof PowerArmorEntity)) { PowerArmor.LOGGER.warn("[autopilot] engine scene: not in the suit"); }
                        mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false);
                        AutoPilot.setView(0, 15); open(); }
            case 20 -> AutoPilot.shot(mc, "engine_fire_start");
            case 22 -> AutoPilot.hold(o.keyUp, true);
            case 60, 66, 72 -> AutoPilot.shot(mc, "engine_fire_into_water_" + s);
            case 90 -> AutoPilot.releaseAll();
            case 94 -> sideCamera(mc);
            case 100 -> AutoPilot.shot(mc, "engine_fire_flooded_side");
            case 102 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 106 -> AutoPilot.shot(mc, "engine_fire_flooded_fp");
            case 108 -> AutoPilot.onServer(mc, p -> { if (suit != null) { packRefused = !suit.packUp(p); } });
            case 112 -> AutoPilot.shot(mc, "engine_fire_pack_refused");
            case 115 -> { phase = "tidal_wet"; engine(mc, ModItems.TIDAL_ENGINE.get(), null); }
            case 150 -> AutoPilot.shot(mc, "engine_tidal_fp");
            case 152 -> sideCamera(mc);
            case 156 -> AutoPilot.shot(mc, "engine_tidal_side");
            case 158 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false); }
            case 160 -> AutoPilot.hold(o.keyUp, true);
            case 200, 260, 300 -> AutoPilot.shot(mc, "engine_tidal_walk_" + s);
            case 330, 345 -> AutoPilot.shot(mc, "engine_tidal_dry_" + s);
            case 360 -> { phase = "tidal_dry"; AutoPilot.releaseAll(); }
            case 362 -> sideCamera(mc);
            case 366 -> AutoPilot.shot(mc, "engine_tidal_dry_side");
            case 368 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 372 -> AutoPilot.shot(mc, "engine_tidal_dry_fp");
            case 375 -> { phase = "tidal_bucket"; engine(mc, null, new ItemStack(Items.WATER_BUCKET)); }
            case 410 -> AutoPilot.shot(mc, "engine_tidal_bucket_fp");
            case 412 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); AutoPilot.hold(o.keyUp, true); }
            case 450 -> AutoPilot.shot(mc, "engine_tidal_bucket_walk");
            case 470 -> AutoPilot.releaseAll();
            case 475 -> { phase = "reactor"; engine(mc, ModItems.STAR_REACTOR.get(), new ItemStack(Items.NETHER_STAR)); }
            case 510 -> { o.setCameraType(CameraType.FIRST_PERSON); }
            case 512 -> AutoPilot.shot(mc, "engine_reactor_fp");
            case 515 -> { o.setCameraType(CameraType.THIRD_PERSON_BACK); AutoPilot.hold(o.keyUp, true); AutoPilot.hold(o.keySprint, true); }
            case 560 -> AutoPilot.shot(mc, "engine_reactor_sprint");
            case 575 -> { AutoPilot.releaseAll(); mc.player.setSprinting(false); } // vanilla keeps sprinting after the key
            case 585 -> { phase = "menu"; o.setCameraType(CameraType.FIRST_PERSON);
                          AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              if (x != null) {
                                  p.stopRiding();
                                  p.teleportTo(x.getX() - 2, x.getY(), x.getZ() - 2);
                                  p.setShiftKeyDown(true);
                                  x.interact(p, InteractionHand.MAIN_HAND, x.position());
                                  p.setShiftKeyDown(false);
                              } }); }
            case 596 -> AutoPilot.shot(mc, "menu_engine");
            case 600 -> { mc.player.closeContainer(); close(); return false; }
            default -> {}
        }
        if (s >= 4 && s < 600) {
            log(mc, s);
        }
        return true;
    }

    /** Flat ground 100 blocks north, daytime: the pool, and a firebox suit (coal + helmet) at its start facing +Z with
     *  the player inside. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            player.stopRiding();
            int x = player.getBlockX(), z = player.getBlockZ() - 100;
            int g = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos b = new BlockPos(x, g, z);
            base = b;
            buildPool(level, b);
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 8)); // it comes with a firebox
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.snapTo(x + 0.5, g, z + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // creative: a flooded suit loses its seal and the pilot's air (the earlier scenes leave the player in survival)
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.setShiftKeyDown(false);
            player.teleportTo(x + 0.5, g, z - 1.5);
            player.startRiding(s, true, true); // forced, as the fuel scene does: a plain startRiding can be refused here
        });
    }

    private static void buildPool(ServerLevel level, BlockPos b) {
        int g = b.getY();
        var stone = Blocks.STONE.defaultBlockState();
        var glass = Blocks.GLASS.defaultBlockState();
        var water = Blocks.WATER.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= LANDING_TO; dz++) {
                for (int y = g; y <= g + 8; y++) {
                    BlockPos p = b.offset(dx, 0, dz).atY(y);
                    boolean side = Math.abs(dx) == 4;
                    int top; // highest stone in this column
                    if (dz >= 2 && dz <= 5) {
                        top = g + (dz - 2);                      // steps up: tops g .. g+3 (the lip)
                    } else if (dz >= STAIRS_OUT && dz <= WATER_TO) {
                        top = g + (dz - STAIRS_OUT);             // stairs out of the water, up to the lip's height
                    } else if (dz > WATER_TO) {
                        top = g + DEPTH - 1;                     // the dry landing
                    } else {
                        top = Integer.MIN_VALUE;
                    }
                    boolean inPool = dz >= WATER_FROM && dz <= WATER_TO && y < g + DEPTH;
                    level.setBlock(p, y <= top ? stone : inPool ? (side ? glass : water) : air, 3);
                }
            }
        }
    }

    /** Swap the engine and/or the fuel slot on the server (the menu's job in the game; a null leaves it as it is). */
    private static void engine(Minecraft mc, net.minecraft.world.item.Item engine, ItemStack fuel) {
        AutoPilot.onServer(mc, p -> {
            PowerArmorEntity x = suit;
            if (x == null) {
                return;
            }
            if (engine != null) {
                x.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(engine));
            }
            if (fuel != null) {
                x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, fuel);
            }
        });
    }

    /** A still camera 6 blocks to the suit's side and 3 behind, outside the pool's glass, a little above backpack height. A scripted camera stops
     *  the client sending the pilot's moves, so it's only used while the suit stands still. */
    private static void sideCamera(Minecraft mc) {
        if (!(mc.level != null && suit != null && mc.level.getEntity(suit.getId()) instanceof PowerArmorEntity c)) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        Vec3 at = c.position().add(0, 2.0, 0);
        AutoPilot.lookCamera(mc, at.add(6, 1.2, 3).subtract(drop), at.subtract(drop));
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_engine.csv"));
            csv.write("tick,phase,engine,powered,flooded,engine_under_water,free,burn_left,fuel,fuel_count,dz,dy,pack_refused,"
                    + "client_powered,client_free,client_flooded,piloted,client_riding,dx,yaw,screen\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("engine csv", e);
        }
    }

    private static void log(Minecraft mc, int s) {
        PowerArmorEntity x = suit;
        BufferedWriter w = csv;
        BlockPos b = base;
        if (x == null || w == null || b == null) {
            return;
        }
        String p = phase;
        var client = mc.level == null ? null : mc.level.getEntity(x.getId()) instanceof PowerArmorEntity c ? c : null;
        boolean cPowered = client != null && client.isPowered(), cFree = client != null && client.hasFreePower();
        boolean cFlooded = client != null && client.isFlooded();
        boolean riding = client != null && mc.player.getVehicle() == client;
        float yaw = mc.player.getYRot();
        String screen = mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName();
        AutoPilot.onServer(mc, player -> {
            try {
                ItemStack fuel = x.getInventory().getItem(PowerArmorEntity.FUEL_SLOT);
                SuitEngine engine = x.getEngine();
                w.write(String.format(Locale.ROOT, "%d,%s,%s,%b,%b,%b,%b,%.1f,%s,%d,%.2f,%.2f,%b,%b,%b,%b,%b,%b,%.2f,%.0f,%s%n", s, p,
                        engine == null ? "-" : engine.name().toLowerCase(Locale.ROOT), x.isPowered(), x.isFlooded(), x.isEngineUnderWater(),
                        x.hasFreePower(), x.getBurnLeftExact(),
                        fuel.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(fuel.getItem()).getPath(),
                        fuel.getCount(), x.getZ() - b.getZ() - 0.5, x.getY() - b.getY(), packRefused, cPowered, cFree, cFlooded,
                        x.isVehicle(), riding, x.getX() - b.getX() - 0.5, yaw, screen));
            } catch (IOException ignored) {
                // the file was closed at the end of the scene
            }
        });
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

    private EngineScene() {}
}
