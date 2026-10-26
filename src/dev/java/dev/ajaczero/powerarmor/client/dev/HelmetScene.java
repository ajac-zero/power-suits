package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the helmet (2026-10-19), the last one of the mech run: a fuelled suit without its helmet stands
 * powered down; the player climbs in (it runs, unsealed, showing the stand-in face), climbs out (it powers down again:
 * no mind without the helmet; the pilot's head shows in the empty collar, ap_helmet_off_piloted(_back)), puts the helmet on by right-clicking (it boots, green visor: the mind drives), then opens
 * its menu (helmet slot). Shots ap_helmet_*.png, ap_menu_helmet.png; log run/autopilot_helmet.csv (server state each tick).
 */
final class HelmetScene {
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        switch (s) {
            case 0 -> { phase = "no_helmet"; setup(mc); }
            case 4 -> { open(); camera(mc); }
            case 30 -> AutoPilot.shot(mc, "helmet_off_empty");
            case 34 -> { phase = "piloted"; AutoPilot.onServer(mc, p -> {
                              if (suit != null) {
                                  p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                  suit.interact(p, InteractionHand.MAIN_HAND, suit.position());
                                  p.setYRot(180); // look north: the suit (and its empty collar) faces the camera
                                  p.setYHeadRot(180);
                              } }); }
            case 36 -> { AutoPilot.setView(180, 0); camera(mc); }
            case 76 -> AutoPilot.shot(mc, "helmet_off_piloted");
            case 77 -> { mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); AutoPilot.setView(180, 15); }
            case 81 -> AutoPilot.shot(mc, "helmet_off_piloted_back"); // the pilot's own head in the collar (PilotHeadLayer), from behind
            case 84 -> { phase = "climbed_out"; AutoPilot.onServer(mc, p -> {
                              p.stopRiding();
                              if (suit != null) {
                                  p.teleportTo(suit.getX() - 3, suit.getY(), suit.getZ() - 3);
                              }
                          }); }
            case 86 -> camera(mc);
            case 130 -> AutoPilot.shot(mc, "helmet_off_left");
            case 134 -> { phase = "helmet_on"; AutoPilot.onServer(mc, p -> {
                              if (suit != null) {
                                  p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SUIT_HELMET.get()));
                                  suit.interact(p, InteractionHand.MAIN_HAND, suit.position());
                              } }); }
            case 136 -> camera(mc);
            case 190 -> AutoPilot.shot(mc, "helmet_on_mind");
            case 196 -> { phase = "menu"; mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false);
                          AutoPilot.onServer(mc, p -> {
                              if (suit != null) {
                                  suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY); // park it: no mind walking off
                                  p.setShiftKeyDown(true);
                                  suit.interact(p, InteractionHand.MAIN_HAND, suit.position());
                                  p.setShiftKeyDown(false);
                              } }); }
            case 206 -> AutoPilot.shot(mc, "menu_helmet");
            case 210 -> { mc.player.closeContainer(); close(); return false; }
            default -> {}
        }
        if (s >= 4 && s < 210) {
            log(mc, s);
        }
        return true;
    }

    /** Flat ground 100 blocks south, daytime; a fuelled suit without its helmet facing the player. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            int x = player.getBlockX(), z = player.getBlockZ() + 100;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            base = new BlockPos(x, y, z);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(x + 0.5, y, z - 3.5);
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            DevUtil.firebox(s); s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4)); // fuel, but no helmet
            s.snapTo(x + 0.5, y, z + 0.5, 180, 0); // facing north, toward the player and the camera
            s.setYBodyRot(180);
            s.setYHeadRot(180);
            s.brain().setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
        });
    }

    /** Front three-quarter view of the suit's head and shoulders, from the ground (scripted camera). */
    private static void camera(Minecraft mc) {
        BlockPos b = base;
        if (b == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 at = Vec3.atBottomCenterOf(b);
        // the camera is an armor stand's eye: place the stand that much lower (as RogueScene does)
        Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, at.add(2.4, 2.5, -4.2).subtract(drop), at.add(0, 2.0, 0).subtract(drop));
    }

    private static void open() {
        try {
            var file = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_helmet.csv");
            csv = Files.newBufferedWriter(file);
            csv.write("tick,phase,has_helmet,powered,sealed,ai_driven,piloted,burn_left,fuel_items\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("helmet csv", e);
        }
    }

    private static void log(Minecraft mc, int s) {
        PowerArmorEntity x = suit;
        BufferedWriter w = csv;
        if (x == null || w == null) {
            return;
        }
        String p = phase;
        AutoPilot.onServer(mc, player -> {
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%b,%b,%b,%b,%b,%d,%d%n", s, p, x.hasHelmet(), x.isPowered(), x.isSealed(),
                        x.isAiDriven(), x.isVehicle(), x.power().getBurnLeft(), x.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount()));
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

    private HelmetScene() {}
}
