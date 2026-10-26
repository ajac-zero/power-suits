package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.TntShell;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the TNT launcher (Phase 11): a suit with the launcher and 8 TNT in its ammo slot on flat grass by
 * day, facing +Z, a survival pilot inside (so the TNT really runs out). In front: a cobblestone wall 13 blocks out, and
 * three husks (no AI) 8 out and 6 to the side (clear of the shots at the wall). The pilot taps a round into the wall (third person, from behind; then a still side camera),
 * films the drum's quarter turn up close, taps one at the husks (cockpit), holds the trigger for a volley (one round per
 * reload), empties the drum (dry click, the HUD's red 0), and opens the menu with its ammo slot. Shots
 * {@code ap_tnt_*.png}, {@code ap_menu_ammo.png}; log run/autopilot_tnt.csv (tool uses, ammo on the server and the
 * client's synced copy, rounds in the air, husks alive, wall blocks left, the client's drum angle and recoil).
 * Runs alone with ./gradlew runAutopilotTnt (~35 s).
 */
final class TntScene {
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static BufferedWriter csv;
    private static String phase = "-";
    private static final int WALL_Z = 13;

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        BlockPos b = base;
        switch (s) {
            case 0 -> { phase = "idle"; AutoPilot.releaseAll(); AutoPilot.aimTarget = null; setup(mc); }
            case 6 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false);
                        AutoPilot.setView(0, 10); open(); }
            case 24 -> AutoPilot.shot(mc, "tnt_idle_back");
            case 26 -> sideCamera(mc);
            case 30 -> AutoPilot.shot(mc, "tnt_idle_side");
            case 32 -> drumCamera(mc);
            case 36 -> AutoPilot.shot(mc, "tnt_idle_drum");
            case 38 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 42 -> AutoPilot.shot(mc, "tnt_idle_fp");                      // the viewmodel, the HUD's 8 TNT
            // a tap at the wall, from behind: the round in flight, the blast
            case 44 -> { phase = "wall"; o.setCameraType(CameraType.THIRD_PERSON_BACK); aim(b, 0, 1.5, WALL_Z); }
            case 50 -> AutoPilot.hold(o.keyUse, true);
            case 52 -> AutoPilot.hold(o.keyUse, false);
            case 51, 53, 55, 57, 59, 61, 63 -> AutoPilot.shot(mc, "tnt_wall_back_" + s);
            // a second tap, from a still side camera
            case 70 -> sideCamera(mc);
            case 74 -> AutoPilot.hold(o.keyUse, true);
            case 76 -> AutoPilot.hold(o.keyUse, false);
            case 75, 77, 79, 81, 83, 85 -> AutoPilot.shot(mc, "tnt_wall_side_" + s);
            // the drum's quarter turn after a shot, up close, every tick
            case 96 -> drumCamera(mc);
            case 100 -> AutoPilot.hold(o.keyUse, true);
            case 102 -> AutoPilot.hold(o.keyUse, false);
            case 99, 101, 103, 104, 105, 106, 107, 108, 110 -> AutoPilot.shot(mc, "tnt_drum_" + s);
            // the husks, from the cockpit
            case 116 -> { phase = "husks"; mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON);
                          DevUtil.hideGui(mc, false); aim(b, 6, 1.0, 8); }
            case 122 -> AutoPilot.hold(o.keyUse, true);
            case 124 -> AutoPilot.hold(o.keyUse, false);
            case 123, 125, 127, 129, 131, 133, 135 -> AutoPilot.shot(mc, "tnt_husks_fp_" + s);
            // hold: a volley, one round per reload, into what's left of the wall
            case 140 -> { phase = "volley"; aim(b, 0, 1.0, WALL_Z); o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 144 -> AutoPilot.hold(o.keyUse, true);
            case 170 -> AutoPilot.shot(mc, "tnt_volley_back");
            case 186 -> AutoPilot.hold(o.keyUse, false);
            // the last rounds, then an empty drum: dry clicks and a red 0
            case 196 -> { phase = "empty"; o.setCameraType(CameraType.FIRST_PERSON); AutoPilot.hold(o.keyUse, true); }
            case 256 -> AutoPilot.shot(mc, "tnt_empty_fp");
            case 258 -> AutoPilot.hold(o.keyUse, false);
            case 262 -> { phase = "after"; AutoPilot.aimTarget = null; sideCamera(mc); }
            case 268 -> AutoPilot.shot(mc, "tnt_after_side");
            // the menu, with the ammo slot
            case 272 -> { phase = "menu"; mc.setCameraEntity(mc.player); DevUtil.hideGui(mc, false);
                          AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              if (x != null) {
                                  x.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, new ItemStack(Items.TNT, 12));
                                  p.stopRiding();
                                  p.teleportTo(x.getX() - 2, x.getY(), x.getZ() - 2);
                                  p.setShiftKeyDown(true);
                                  x.interact(p, InteractionHand.MAIN_HAND, x.position());
                                  p.setShiftKeyDown(false);
                              } }); }
            case 284 -> AutoPilot.shot(mc, "menu_ammo");
            case 288 -> { mc.player.closeContainer(); close(); return false; }
            default -> {}
        }
        if (s >= 6 && s < 288) {
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

    /** Flat grass 120 blocks east by day; the suit (firebox, coal, helmet, launcher, 8 TNT) with the player inside, the
     *  husks and the wall in front of it. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            // monsters vanish at once in the autopilot's peaceful world: the husks need a difficulty first
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            DevUtil.setTime(level, 6000);
            player.stopRiding();
            int x = player.getBlockX() + 120, z = player.getBlockZ();
            int g = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos b = new BlockPos(x, g, z);
            base = b;
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -6; dz <= 20; dz++) {
                    for (int y = g - 4; y <= g + 8; y++) {
                        level.setBlock(b.offset(dx, 0, dz).atY(y), y < g - 1 ? Blocks.DIRT.defaultBlockState()
                                : y == g - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = 0; y < 4; y++) {
                    level.setBlock(b.offset(dx, y, WALL_Z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
            for (int i = -1; i <= 1; i++) {
                Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                husk.snapTo(x + 6.5 + i * 1.6, g, z + 8.5, 180, 0); // off to the side: out of the line of fire to the wall
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                level.addFreshEntity(husk);
            }
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(s);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 16));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.TNT_LAUNCHER.get()));
            s.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, new ItemStack(Items.TNT, 8));
            s.snapTo(x + 0.5, g, z + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // the hand would take the right click
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // the TNT really runs out
            player.setShiftKeyDown(false);
            player.teleportTo(x + 0.5, g, z - 1.5);
            player.startRiding(s, true, true);
        });
    }

    private static PowerArmorEntity clientSuit(Minecraft mc) {
        return mc.level != null && suit != null && mc.level.getEntity(suit.getId()) instanceof PowerArmorEntity c ? c : null;
    }

    /** A still camera to the suit's tool side (+X) and a little ahead, looking across the line of fire. */
    private static void sideCamera(Minecraft mc) {
        PowerArmorEntity c = clientSuit(mc);
        if (c == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        Vec3 at = c.position().add(0, 1.6, 5);
        AutoPilot.lookCamera(mc, at.add(8, 1.5, -1.5).subtract(drop), at.subtract(drop));
    }

    /** Close to the launcher on the suit's tool side, a little behind the drum and above it: its side (the octagon's
     *  notches turning) without the muzzle smoke in the way. */
    private static void drumCamera(Minecraft mc) {
        PowerArmorEntity c = clientSuit(mc);
        if (c == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        Vec3 drum = c.position().add(0.85, 1.75, 0.75);
        AutoPilot.lookCamera(mc, drum.add(1.6, 0.6, -0.5).subtract(drop), drum.subtract(drop));
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_tnt.csv"));
            csv.write("tick,phase,tool_uses,ammo,client_ammo,shells,husks,wall_blocks,client_drum,client_recoil,client_raise\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("tnt csv", e);
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
        PowerArmorEntity client = clientSuit(mc);
        int clientAmmo = client == null ? -1 : client.getAmmoStack().getCount();
        float drum = client == null ? 0F : client.getBarrelAngle(0F);
        float recoil = client == null ? 0F : client.getRecoil(0F);
        float raise = client == null ? 0F : client.getToolRaise(0F);
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            AABB area = new AABB(b).inflate(24);
            int shells = level.getEntitiesOfClass(TntShell.class, area).size();
            int husks = level.getEntitiesOfClass(Husk.class, area).size();
            int wall = 0;
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-3, 0, WALL_Z), b.offset(3, 3, WALL_Z))) {
                if (level.getBlockState(q).is(Blocks.COBBLESTONE)) {
                    wall++;
                }
            }
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%d,%d,%d,%.1f,%.2f,%.2f%n", s, p, x.socket().getUses(),
                        x.getInventory().getItem(PowerArmorEntity.AMMO_SLOT).getCount(), clientAmmo, shells, husks, wall, drum, recoil, raise));
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

    private TntScene() {}
}
