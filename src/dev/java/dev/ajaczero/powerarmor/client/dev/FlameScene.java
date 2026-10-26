package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.FlameJet;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the flamethrower (Phase 11): a suit with the flamethrower on flat ground at dusk, facing +Z. In
 * front of it: three pigs (no AI) 5 blocks out, an oak plank wall 9 out, raw beef on the ground, a block of ice and an
 * unlit campfire. The pilot burns the pigs (cockpit, third person from behind, still side camera), sets the wall on fire,
 * cooks the beef, lights the campfire and melts the ice. Shots {@code ap_flame_*.png}; log run/autopilot_flame.csv (tool
 * uses, fuel, gobs in the air, pigs burning, fire blocks, the beef, ice, campfire, the client's pilot light).
 * Runs alone with ./gradlew runAutopilotFlame (~30 s).
 */
final class FlameScene {
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static volatile ItemEntity beef;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        BlockPos b = base;
        switch (s) {
            case 0 -> { phase = "idle"; AutoPilot.releaseAll(); AutoPilot.aimTarget = null; setup(mc); }
            case 6 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false);
                        AutoPilot.setView(0, 10); open(); }
            case 24 -> AutoPilot.shot(mc, "flame_idle_back");
            case 26 -> sideCamera(mc, 4.5);
            case 30 -> AutoPilot.shot(mc, "flame_idle_side");                     // the pilot light, lit blue
            case 32 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 36 -> AutoPilot.shot(mc, "flame_idle_fp");
            // the pigs
            case 40 -> { phase = "pigs"; aim(b, 0, 0.8, 5.5); }
            case 44 -> AutoPilot.hold(o.keyUse, true);
            case 50, 56, 64 -> AutoPilot.shot(mc, "flame_pigs_fp_" + s);
            case 66 -> o.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 72, 78 -> AutoPilot.shot(mc, "flame_pigs_back_" + s);
            case 80 -> sideCamera(mc, 6);
            case 84, 88, 92 -> AutoPilot.shot(mc, "flame_pigs_side_" + s);
            case 96 -> { AutoPilot.hold(o.keyUse, false); mc.setCameraEntity(mc.player); DevUtil.hideGui(mc, false); }
            case 106 -> AutoPilot.shot(mc, "flame_pigs_after");                  // burning pigs
            // the wall
            case 110 -> { phase = "wall"; aim(b, 0, 1.5, 9); }
            case 114 -> AutoPilot.hold(o.keyUse, true);
            case 140 -> AutoPilot.shot(mc, "flame_wall_back");
            case 142 -> o.setCameraType(CameraType.FIRST_PERSON);
            case 150 -> AutoPilot.shot(mc, "flame_wall_fp");
            case 170 -> AutoPilot.hold(o.keyUse, false);
            case 172 -> sideCamera(mc, 8);
            case 200 -> AutoPilot.shot(mc, "flame_wall_burning");
            case 202 -> { mc.setCameraEntity(mc.player); DevUtil.hideGui(mc, false); }
            // the beef, then the ice and the campfire
            case 206 -> { phase = "beef"; aim(b, -2, 0.1, 4); }
            case 210 -> AutoPilot.hold(o.keyUse, true);
            case 230 -> AutoPilot.shot(mc, "flame_beef_fp");
            case 260 -> AutoPilot.hold(o.keyUse, false);
            // the campfire before the ice: the melted ice's water would put it out (a waterlogged campfire can't be lit)
            case 264 -> { phase = "campfire"; aim(b, 3, 0.3, 3); }
            case 266 -> AutoPilot.hold(o.keyUse, true);
            case 282 -> { phase = "ice"; aim(b, 2, 0.5, 4); }
            case 296 -> AutoPilot.hold(o.keyUse, false);
            case 300 -> { phase = "done"; AutoPilot.aimTarget = null; AutoPilot.setView(0, 30); }
            case 306 -> AutoPilot.shot(mc, "flame_after_fp");
            case 308 -> sideCamera(mc, 7);
            case 314 -> AutoPilot.shot(mc, "flame_after_side");
            case 316 -> { mc.setCameraEntity(mc.player); DevUtil.hideGui(mc, false); close(); return false; }
            default -> {}
        }
        if (s >= 6 && s < 316) {
            log(mc, s);
        }
        return true;
    }

    /** Look at a point given relative to the scene's base (the suit's block). */
    private static void aim(BlockPos b, double dx, double dy, double dz) {
        if (b != null) {
            AutoPilot.aimTarget = new Vec3(b.getX() + 0.5 + dx, b.getY() + dy, b.getZ() + 0.5 + dz);
        }
    }

    /** Flat grass 120 blocks east at dusk; the suit (firebox, coal, helmet, flamethrower) with the player inside, and the
     *  targets in front of it. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 12600);
            player.stopRiding();
            int x = player.getBlockX() + 120, z = player.getBlockZ();
            int g = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos b = new BlockPos(x, g, z);
            base = b;
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -6; dz <= 16; dz++) {
                    for (int y = g - 1; y <= g + 6; y++) {
                        level.setBlock(b.offset(dx, 0, dz).atY(y), y == g - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int y = 0; y < 3; y++) {
                    level.setBlock(b.offset(dx, y, 9), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
            level.setBlock(b.offset(2, 0, 4), Blocks.ICE.defaultBlockState(), 3);
            level.setBlock(b.offset(3, 0, 3), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
            for (int i = -1; i <= 1; i++) {
                Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                pig.snapTo(x + 0.5 + i * 1.3, g, z + 5.5, 180, 0);
                pig.setNoAi(true);
                level.addFreshEntity(pig);
            }
            ItemEntity meat = new ItemEntity(level, x + 0.5 - 2, g + 0.1, z + 0.5 + 4, new ItemStack(Items.BEEF, 4));
            meat.setDeltaMovement(Vec3.ZERO);
            meat.setNeverPickUp();
            level.addFreshEntity(meat);
            beef = meat;
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(s);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 16));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.FLAMETHROWER.get()));
            s.snapTo(x + 0.5, g, z + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.brain().setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // the hand would take the right click
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.setShiftKeyDown(false);
            player.teleportTo(x + 0.5, g, z - 1.5);
            player.startRiding(s, true, true);
        });
    }

    /** A still camera to the suit's left (its tool side) and a little ahead, looking across the stream. */
    private static void sideCamera(Minecraft mc, double ahead) {
        if (!(mc.level != null && suit != null && mc.level.getEntity(suit.getId()) instanceof PowerArmorEntity c)) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        Vec3 at = c.position().add(0, 1.6, ahead * 0.5);
        AutoPilot.lookCamera(mc, at.add(7.5, 1.5, ahead * 0.2 - 1).subtract(drop), at.subtract(drop));
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_flame.csv"));
            csv.write("tick,phase,tool_uses,energy,jets,pigs_burning,pig_health,fire_blocks,beef,ice,campfire_lit,"
                    + "client_running,client_pilot_glow\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("flame csv", e);
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
        boolean running = client != null && client.socket().isRunning();
        float glow = client == null ? 0F : client.socketVisuals().light(dev.ajaczero.powerarmor.item.ToolLight.PILOT, 0F);
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            AABB area = new AABB(b).inflate(10);
            var pigs = level.getEntitiesOfClass(Pig.class, area);
            int burning = (int) pigs.stream().filter(e -> e.isOnFire()).count();
            float health = (float) pigs.stream().mapToDouble(e -> e.getHealth()).sum();
            int jets = level.getEntitiesOfClass(FlameJet.class, area).size();
            int fire = 0;
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-8, 0, -6), b.offset(8, 5, 16))) {
                if (level.getBlockState(q).is(BlockTags.FIRE)) {
                    fire++;
                }
            }
            ItemEntity meat = beef;
            String beefState = meat == null ? "-" : !meat.isAlive() ? "gone"
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(meat.getItem().getItem()).getPath() + "x" + meat.getItem().getCount();
            boolean ice = level.getBlockState(b.offset(2, 0, 4)).is(Blocks.ICE);
            var camp = level.getBlockState(b.offset(3, 0, 3));
            boolean lit = camp.hasProperty(CampfireBlock.LIT) && camp.getValue(CampfireBlock.LIT);
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%d,%.1f,%d,%s,%b,%b,%b,%.2f%n", s, p, x.socket().getUses(), x.power().getTotalEnergy(),
                        jets, burning, health, fire, beefState, ice, lit, running, glow));
            } catch (IOException ignored) {
                // closed at the end of the scene
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

    private FlameScene() {}
}
