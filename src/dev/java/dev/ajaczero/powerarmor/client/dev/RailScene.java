package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.RailCannonItem;
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
 * Autopilot scene for the rail cannon (Phase 11): a suit with the cannon and 6 iron ingots in its ammo slot on flat grass
 * by day, facing +Z, a survival pilot inside. In front: a glass wall 6 blocks out, two husks (no AI) one behind the other
 * at 9 and 12, a cobblestone wall at 16. The pilot lets go early once (nothing fires), charges up from behind (the core
 * brightening, the HUD's charge bar) and fires through the glass and both husks, fires again from a still side camera and
 * once from the cockpit at the wall, holds the trigger with no ingots (no charge, the HUD's red 0), then climbs out: the
 * machine mind guards home and snipes a husk off to the side. Last, the menu with the ammo slot.
 * Shots {@code ap_rail_*.png}, {@code ap_menu_rail.png}; log run/autopilot_rail.csv (tool uses, ammo on the server and
 * the client's copy, the charge on both sides, husks alive, glass and wall blocks left, the last shot's hits and broken
 * blocks, the client's recoil, raise and core glow, the mind's goals).
 * Runs alone with ./gradlew runAutopilotRail (~35 s).
 */
final class RailScene {
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static BufferedWriter csv;
    private static String phase = "-";
    private static final int GLASS_Z = 6, WALL_Z = 16;

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        BlockPos b = base;
        switch (s) {
            case 0 -> { phase = "idle"; AutoPilot.releaseAll(); AutoPilot.aimTarget = null; setup(mc); }
            case 6 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, false);
                        AutoPilot.setView(0, 10); open(); }
            case 24 -> AutoPilot.shot(mc, "rail_idle_back");
            case 26 -> sideCamera(mc);
            case 30 -> AutoPilot.shot(mc, "rail_idle_side");
            case 32 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false); }
            case 36 -> AutoPilot.shot(mc, "rail_idle_fp");                       // the viewmodel, the HUD's 6 ingots
            // let go early: it charges, winds down, nothing fires
            case 40 -> { phase = "early"; aim(b, 0, 1.0, 12); }
            case 44 -> AutoPilot.hold(o.keyUse, true);
            case 56 -> AutoPilot.shot(mc, "rail_early_fp");                      // half charged: the bar half full
            case 58 -> AutoPilot.hold(o.keyUse, false);
            // charge and fire from behind: through the glass and both husks
            case 72 -> { phase = "charge"; o.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 76 -> AutoPilot.hold(o.keyUse, true);
            case 86, 96 -> AutoPilot.shot(mc, "rail_charge_back_" + s);
            case 110 -> AutoPilot.shot(mc, "rail_charged_back");
            case 114 -> { phase = "fire"; AutoPilot.hold(o.keyUse, false); }
            case 115, 116, 117, 119, 122, 128 -> AutoPilot.shot(mc, "rail_fire_back_" + s);
            // again, from a still side camera (aimed at the wall through the hole)
            case 140 -> { phase = "side"; sideCamera(mc); aim(b, 0.5, 1.6, WALL_Z); AutoPilot.hold(o.keyUse, true); }
            case 176 -> AutoPilot.hold(o.keyUse, false);
            case 177, 178, 180, 184 -> AutoPilot.shot(mc, "rail_fire_side_" + s);
            // from the cockpit
            case 196 -> { phase = "cockpit"; mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON);
                          DevUtil.hideGui(mc, false); aim(b, -1.5, 2.0, WALL_Z); }
            case 200 -> AutoPilot.hold(o.keyUse, true);
            case 212, 220 -> AutoPilot.shot(mc, "rail_charging_fp_" + s);
            case 228 -> AutoPilot.shot(mc, "rail_charged_fp");
            case 234 -> AutoPilot.hold(o.keyUse, false);
            case 235, 236, 238, 242 -> AutoPilot.shot(mc, "rail_fire_fp_" + s);
            // no ingots: it doesn't charge, the HUD shows a red 0
            case 250 -> { phase = "empty"; AutoPilot.onServer(mc, p -> { PowerArmorEntity x = suit; if (x != null) {
                              x.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, ItemStack.EMPTY); } }); }
            case 256 -> AutoPilot.hold(o.keyUse, true);
            case 276 -> AutoPilot.shot(mc, "rail_empty_fp");
            case 280 -> AutoPilot.hold(o.keyUse, false);
            // up close while it charges: the core's light running toward the muzzle, the shake, the sparks
            case 288 -> { phase = "close"; AutoPilot.onServer(mc, p -> { PowerArmorEntity x = suit; if (x != null) {
                              x.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, new ItemStack(Items.IRON_INGOT, 4)); } });
                          aim(b, 0.5, 1.6, WALL_Z); closeCamera(mc); }
            case 292 -> AutoPilot.hold(o.keyUse, true);
            case 318 -> frontCamera(mc);   // the rest from in front: down the slot, the energy and the emitter in the band
            case 340 -> AutoPilot.hold(o.keyUse, false);
            // the machine mind: the pilot climbs out (the suit's home is where it stands), ingots go back in, a husk
            // comes in off to the left: the mind charges, aims and fires
            case 360 -> { phase = "mind"; AutoPilot.aimTarget = null; AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              BlockPos bb = base;
                              if (x == null || bb == null) {
                                  return;
                              }
                              p.stopRiding();
                              p.teleportTo(x.getX() + 1.5, x.getY(), x.getZ() - 3);
                              x.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, new ItemStack(Items.IRON_INGOT, 4));
                              husk(p.level(), bb.getX() - 9 + 0.5, bb.getY(), bb.getZ() + 12.5); // (clear of the glass wall)
                          }); }
            case 364 -> mindCamera(mc);
            case 380, 390, 400, 410, 415, 420, 425, 430, 440, 450 -> AutoPilot.shot(mc, "rail_mind_" + s);
            // the menu, with the ammo slot
            case 466 -> { phase = "menu"; mc.setCameraEntity(mc.player); DevUtil.hideGui(mc, false);
                          AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              if (x != null) {
                                  x.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, new ItemStack(Items.IRON_INGOT, 12));
                                  p.teleportTo(x.getX() - 2, x.getY(), x.getZ() - 2);
                                  p.setShiftKeyDown(true);
                                  x.interact(p, InteractionHand.MAIN_HAND, x.position());
                                  p.setShiftKeyDown(false);
                              } }); }
            case 478 -> AutoPilot.shot(mc, "menu_rail");
            case 482 -> { mc.player.closeContainer(); close(); return false; }
            default -> {}
        }
        if (s >= 290 && s <= 346 && s % 2 == 0) {
            AutoPilot.shot(mc, "rail_close_" + s);
        }
        if (s >= 6 && s < 482) {
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

    private static void husk(ServerLevel level, double x, double y, double z) {
        Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        husk.snapTo(x, y, z, 180, 0);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        level.addFreshEntity(husk);
    }

    /** Flat grass 120 blocks east by day; the suit (firebox, coal, helmet, rail cannon, 6 ingots) with the player inside,
     *  the glass, the husks and the wall in front of it. */
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
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -6; dz <= 22; dz++) {
                    for (int y = g - 4; y <= g + 8; y++) {
                        level.setBlock(b.offset(dx, 0, dz).atY(y), y < g - 1 ? Blocks.DIRT.defaultBlockState()
                                : y == g - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = 0; y < 4; y++) {
                    level.setBlock(b.offset(dx, y, GLASS_Z), Blocks.GLASS.defaultBlockState(), 3);
                    level.setBlock(b.offset(dx, y, WALL_Z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
            husk(level, x + 0.5, g, z + 9.5);
            husk(level, x + 0.5, g, z + 12.5);
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(s);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 16));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.RAIL_CANNON.get()));
            s.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, new ItemStack(Items.IRON_INGOT, 6));
            s.snapTo(x + 0.5, g, z + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.brain().setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // the hand would take the right click
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // the ingots really run out
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
        Vec3 at = c.position().add(0, 1.6, 6);
        AutoPilot.lookCamera(mc, at.add(11, 2.5, -3).subtract(drop), at.subtract(drop));
    }

    /** Close to the raised cannon on the suit's tool side, a little behind and above it: the core between the prongs. */
    private static void closeCamera(Minecraft mc) {
        PowerArmorEntity c = clientSuit(mc);
        if (c == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        Vec3 gun = c.position().add(0.7, 2.15, 1.3);
        AutoPilot.lookCamera(mc, gun.add(0.5, 1.7, -0.7).subtract(drop), gun.add(-0.1, 0, 0.5).subtract(drop));
    }

    /** In front of the raised cannon, a little above and to its outer side, looking back down its slot. */
    private static void frontCamera(Minecraft mc) {
        PowerArmorEntity c = clientSuit(mc);
        if (c == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, c.position().add(0.95, 2.25, 3.1).subtract(drop), c.position().add(0.75, 1.75, 1.2).subtract(drop));
    }

    /** Behind the suit and to its right, looking past it toward the husk off to the left. */
    private static void mindCamera(Minecraft mc) {
        PowerArmorEntity c = clientSuit(mc);
        if (c == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        Vec3 at = c.position().add(-3.5, 1.8, 6);
        AutoPilot.lookCamera(mc, c.position().add(5, 4.5, -7).subtract(drop), at.subtract(drop));
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_rail.csv"));
            csv.write("tick,phase,tool_uses,ammo,client_ammo,charge,client_charge,husks,glass,wall,last_hits,last_broken,last_len,"
                    + "client_recoil,client_raise,client_glow,goals\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("rail csv", e);
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
        float clientCharge = client == null ? -1F : client.socket().getSpin();
        float recoil = client == null ? 0F : client.socketVisuals().recoil(0F);
        float raise = client == null ? 0F : client.socketVisuals().raise(0F);
        float glow = client == null ? 0F : client.socketVisuals().light(dev.ajaczero.powerarmor.item.ToolLight.RAIL_EMIT, client.tickCount);
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            AABB area = new AABB(b).inflate(24);
            int husks = (int) level.getEntitiesOfClass(Husk.class, area).stream().filter(h -> h.isAlive()).count();
            int glass = 0, wall = 0;
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-3, 0, GLASS_Z), b.offset(3, 3, GLASS_Z))) {
                glass += level.getBlockState(q).is(Blocks.GLASS) ? 1 : 0;
            }
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-3, 0, WALL_Z), b.offset(3, 3, WALL_Z))) {
                wall += level.getBlockState(q).is(Blocks.COBBLESTONE) ? 1 : 0;
            }
            RailCannonItem.Shot shot = RailCannonItem.lastShot(x);
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%.2f,%.2f,%d,%d,%d,%d,%d,%.1f,%.2f,%.2f,%.2f,%s%n", s, p, x.socket().getUses(),
                        x.getInventory().getItem(PowerArmorEntity.AMMO_SLOT).getCount(), clientAmmo, x.socket().getSpin(), clientCharge,
                        husks, glass, wall, shot == null ? -1 : shot.hit().size(), shot == null ? -1 : shot.blocksBroken(),
                        shot == null ? -1D : shot.from().distanceTo(shot.to()), recoil, raise, glow, x.brain().runningGoalNames()));
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

    private RailScene() {}
}
