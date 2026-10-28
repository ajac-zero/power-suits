package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.client.PowerArmorScreen;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Autopilot scene for the suit's inventory screen (user, 2026-10-27): the player's inventory with the suit in it.
 * A survival pilot sits in a fuelled suit with the chaingun in its socket, a drill and a chainsaw on the hotbar and
 * ammo and coal in the inventory.
 * <ol>
 *   <li>The inventory key from inside (what vanilla does for E when the vehicle has its own screen:
 *   {@code sendOpenInventory}): the suit's menu, no Pack up ({@code ap_menu_inside}).</li>
 *   <li>F over the drill on the hotbar: the drill goes into the socket, the chaingun comes out ({@code ap_menu_swapped}).</li>
 *   <li>The Power button: switched off, the status reads POWER OFF ({@code ap_menu_off}); closed, the visor is completely
 *   dark, nothing on the glass ({@code ap_menu_hud_off}).</li>
 *   <li>The power switch key (H): on again, the visor warms up ({@code ap_menu_hud_on}).</li>
 *   <li>F outside the screen with the chainsaw selected: the chainsaw goes into the socket ({@code ap_menu_f_back},
 *   the in-game back view).</li>
 *   <li>Out of the suit, sneak + right-click: the same screen from outside, with Pack up ({@code ap_menu_outside}).</li>
 *   <li>Still open, the fuel taken out: the status reads NO FUEL ({@code ap_menu_no_fuel}); the engine taken out: NO
 *   ENGINE ({@code ap_menu_no_engine}). The menu is the only place that says why a suit is dark (SuitHud draws nothing).</li>
 * </ol>
 * Log run/autopilot_menu.csv: per tick the screen open on the client, the server's and the client's socket tool, the
 * switch and power on both sides, the pilot's hand and whether vanilla treats the inventory key as the vehicle's.
 * Runs alone with ./gradlew runAutopilotMenu (~26 s) and at the end of runAutopilotMech.
 */
final class MenuScene {
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        switch (s) {
            case 0 -> { setup(mc); open(); }
            case 40 -> { phase = "inside"; mc.options.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false);
                         // what Minecraft.handleKeybinds does for the inventory key
                         if (mc.gameMode.isServerControlledInventory()) {
                             mc.player.sendOpenInventory();
                         } }
            case 50 -> AutoPilot.shot(mc, "menu_inside");
            // F over the drill (hotbar slot 1): the screen's own swap-hands click
            case 54 -> { phase = "swap"; click(mc, PowerArmorMenu.HOTBAR_START + 1, Inventory.SLOT_OFFHAND, ContainerInput.SWAP); }
            case 60 -> AutoPilot.shot(mc, "menu_swapped");
            case 64 -> { phase = "off"; if (mc.player.containerMenu instanceof PowerArmorMenu m) {
                             mc.gameMode.handleInventoryButtonClick(m.containerId, PowerArmorMenu.BUTTON_POWER);
                         } }
            case 74 -> AutoPilot.shot(mc, "menu_off");
            case 78 -> mc.player.closeContainer();
            case 110 -> AutoPilot.shot(mc, "menu_hud_off");
            // the power switch key: a press of H, through the key binding itself (SuitKeys sends the message)
            case 114 -> { phase = "on"; KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_H)); }
            case 160 -> AutoPilot.shot(mc, "menu_hud_on");
            // F outside the screen, the chainsaw selected: vanilla's swap packet, which the suit takes over (PilotHands)
            case 164 -> { phase = "f_key"; mc.player.getInventory().setSelectedSlot(2); // (the client tells the server next tick)
                          AutoPilot.onServer(mc, p -> p.getInventory().setSelectedSlot(2)); }
            case 166 -> { mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                  BlockPos.ZERO, Direction.DOWN)); }
            case 170 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); DevUtil.hideGui(mc, true); }
            case 180 -> AutoPilot.shot(mc, "menu_f_back");
            // out of the suit: sneak + right-click opens the same screen, with Pack up
            case 184 -> { phase = "outside"; mc.options.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false);
                          AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              if (x == null) {
                                  return;
                              }
                              p.stopRiding();
                              p.teleportTo(x.getX() - 2, x.getY(), x.getZ() - 2);
                              p.setShiftKeyDown(true);
                              x.interact(p, InteractionHand.MAIN_HAND, x.position());
                              p.setShiftKeyDown(false);
                          }); }
            case 196 -> AutoPilot.shot(mc, "menu_outside");
            // the menu is the only place that says why a suit is dark (the visor shows nothing): out of fuel, then no engine
            case 200 -> { phase = "no_fuel"; AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              if (x != null) {
                                  x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY);
                                  x.power().setBurn(0, 0);
                              }
                          }); }
            case 210 -> AutoPilot.shot(mc, "menu_no_fuel");
            case 214 -> { phase = "no_engine"; AutoPilot.onServer(mc, p -> {
                              PowerArmorEntity x = suit;
                              if (x != null) {
                                  x.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, ItemStack.EMPTY);
                              }
                          }); }
            case 224 -> AutoPilot.shot(mc, "menu_no_engine");
            case 228 -> { mc.player.closeContainer(); close(); return false; }
            default -> {}
        }
        if (s >= 2) {
            log(mc, s);
        }
        return true;
    }

    private static void click(Minecraft mc, int slot, int button, ContainerInput input) {
        if (mc.player.containerMenu instanceof PowerArmorMenu m) {
            mc.gameMode.handleContainerInput(m.containerId, slot, button, input, mc.player);
        }
    }

    /** Flat ground 140 blocks east by day; the suit (firebox, coal, helmet, chaingun) with a survival pilot carrying a
     *  drill and a chainsaw on the hotbar (slot 0, the selected one, empty) and nuggets and coal in the inventory. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            player.stopRiding();
            int x = player.getBlockX() + 140, z = player.getBlockZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            base = new BlockPos(x, y, z);
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(s);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 16));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
            s.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.brain().setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setGameMode(GameType.SURVIVAL);
            Inventory inv = player.getInventory();
            inv.clearContent();
            inv.setSelectedSlot(0);
            inv.setItem(1, new ItemStack(ModItems.DRILL.get()));
            inv.setItem(2, new ItemStack(ModItems.CHAINSAW.get()));
            inv.setItem(9, new ItemStack(Items.IRON_NUGGET, 64));
            inv.setItem(10, new ItemStack(Items.TNT, 12));
            inv.setItem(11, new ItemStack(Items.COAL, 32));
            player.setShiftKeyDown(false);
            player.teleportTo(x + 0.5, y, z - 1.5);
            player.startRiding(s, true, true);
        });
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(FMLPaths.GAMEDIR.get().resolve("autopilot_menu.csv"));
            csv.write("tick,phase,screen,vehicle_inventory,server_socket,client_socket,server_on,client_on,server_powered,client_powered,hand\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("menu csv", e);
        }
    }

    private static void log(Minecraft mc, int s) {
        PowerArmorEntity x = suit;
        BufferedWriter w = csv;
        if (x == null || w == null) {
            return;
        }
        String p = phase;
        PowerArmorEntity client = mc.level == null ? null : mc.level.getEntity(x.getId()) instanceof PowerArmorEntity c ? c : null;
        var open = mc.gui.screen();
        String screen = open == null ? "-" : open instanceof PowerArmorScreen ? "suit" : open.getClass().getSimpleName();
        boolean vehicleInventory = mc.gameMode.isServerControlledInventory();
        String clientSocket = client == null ? "?" : id(client.getSocketStack());
        boolean clientOn = client != null && client.power().isSwitchedOn();
        boolean clientPowered = client != null && client.isPowered();
        AutoPilot.onServer(mc, player -> {
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%s,%b,%s,%s,%b,%b,%b,%b,%s%n", s, p, screen, vehicleInventory,
                        id(x.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT)), clientSocket, x.power().isSwitchedOn(), clientOn,
                        x.isPowered(), clientPowered, id(player.getMainHandItem())));
            } catch (IOException ignored) {
                // closed at the end of the scene
            }
        });
    }

    private static String id(ItemStack stack) {
        return stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
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
}
