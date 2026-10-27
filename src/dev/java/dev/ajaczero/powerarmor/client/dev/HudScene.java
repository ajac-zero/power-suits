package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.client.SuitHud;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Autopilot scene for the pilot's HUD (client/SuitHud, 2026-10-26): every state it can show, from the cockpit, on flat
 * grass by day. A survival pilot (so ammo counts) in a suit with a firebox, coal, a helmet and a chaingun: idle (one
 * ember), walking (two), sprinting (three); a long burst until the barrels overheat (heat bar, red); down to the last
 * bullets (red); a low hull (red); low fuel (red, beeping); no fuel (faded, NO FUEL); then each tool in turn: the rail
 * cannon charging and ready, the TNT launcher reloading, the flamethrower (three embers), the drill, the EM projector,
 * the chainsaw (symbol only); a star reactor (endless fuel, no embers); and without the helmet (no HUD, vanilla's hearts).
 * Shots {@code ap_hud_*.png}; log run/autopilot_hud.csv: per tick what the HUD drew (hull, fuel bar and text, embers,
 * critical, powered, tool, count, bar) next to the server's own numbers. Runs alone with ./gradlew runAutopilotHud (~40 s).
 */
final class HudScene {
    private static volatile PowerArmorEntity suit;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        mc.gui.toastManager().clear(); // no recipe toasts over the HUD's corner
        switch (s) {
            case 0 -> { phase = "idle"; AutoPilot.releaseAll(); AutoPilot.aimTarget = null; setup(mc); }
            case 6 -> { mc.setCameraEntity(mc.player); o.setCameraType(CameraType.FIRST_PERSON); DevUtil.hideGui(mc, false);
                        AutoPilot.setView(0, 5); open(); }
            case 40 -> AutoPilot.shot(mc, "hud_idle");
            case 42 -> { phase = "walk"; AutoPilot.hold(o.keyUp, true); }
            case 70 -> AutoPilot.shot(mc, "hud_walk");
            case 72 -> { phase = "sprint"; AutoPilot.hold(o.keySprint, true); }
            case 100 -> AutoPilot.shot(mc, "hud_sprint");
            case 102 -> { phase = "stop"; AutoPilot.releaseAll(); mc.player.setSprinting(false); }
            // a long burst: the heat bar fills, the barrels overheat, the HUD goes red
            case 120 -> { phase = "burst"; AutoPilot.hold(o.keyUse, true); }
            case 140, 200 -> AutoPilot.shot(mc, "hud_chaingun_" + s);
            case 318 -> AutoPilot.shot(mc, "hud_chaingun_hot");  // 80 bullets in a row: overheated
            case 322 -> AutoPilot.hold(o.keyUse, false);
            // the last bullets (cooled down first)
            case 380 -> { phase = "last_bullets"; server(mc, (p, x) -> {
                              p.getInventory().clearContent();
                              p.getInventory().setItem(9, new ItemStack(Items.IRON_NUGGET, 10));
                              x.setStoredNuggets(0); }); }
            case 390 -> AutoPilot.shot(mc, "hud_last_bullets");
            case 392 -> server(mc, (p, x) -> p.getInventory().setItem(9, new ItemStack(Items.IRON_NUGGET, 64)));
            // a quarter of the hull left
            case 400 -> { phase = "hull_low"; server(mc, (p, x) -> x.setHealth(x.getMaxHealth() * 0.2F)); }
            case 410 -> AutoPilot.shot(mc, "hud_hull_low");
            case 412 -> server(mc, (p, x) -> x.setHealth(x.getMaxHealth()));
            // low fuel: the last few hundred energy in the burning item, nothing in the slot
            case 420 -> { phase = "fuel_low"; server(mc, (p, x) -> {
                              x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY);
                              x.power().setBurn(400, 1600); }); }
            case 432 -> AutoPilot.shot(mc, "hud_fuel_low");
            // out of fuel: the suit powers down, the HUD fades
            case 436 -> { phase = "off"; server(mc, (p, x) -> x.power().setBurn(0, 0)); }
            case 460 -> AutoPilot.shot(mc, "hud_off");
            case 462 -> { phase = "refuel"; server(mc, (p, x) -> x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 40))); }
            // the rail cannon: charging, then full (it fires on release)
            case 480 -> { phase = "rail"; tool(mc, ModItems.RAIL_CANNON.get(), new ItemStack(Items.IRON_INGOT, 6)); }
            case 492 -> AutoPilot.shot(mc, "hud_rail_idle");
            case 494 -> AutoPilot.hold(o.keyUse, true);
            case 512 -> AutoPilot.shot(mc, "hud_rail_charging");
            case 532 -> AutoPilot.shot(mc, "hud_rail_ready");
            case 534 -> AutoPilot.hold(o.keyUse, false);
            // the TNT launcher: a shot, the reload bar refilling
            case 550 -> { phase = "tnt"; tool(mc, ModItems.TNT_LAUNCHER.get(), new ItemStack(Items.TNT, 8)); AutoPilot.setView(0, -35); }
            case 562 -> AutoPilot.hold(o.keyUse, true);
            case 564 -> AutoPilot.hold(o.keyUse, false);
            case 570 -> AutoPilot.shot(mc, "hud_tnt_reload");
            case 600 -> AutoPilot.shot(mc, "hud_tnt_ready");
            // the flamethrower, burning hard
            case 610 -> { phase = "flame"; tool(mc, ModItems.FLAMETHROWER.get(), ItemStack.EMPTY); AutoPilot.setView(0, 10); }
            case 620 -> AutoPilot.hold(o.keyUse, true);
            case 646 -> AutoPilot.shot(mc, "hud_flame");
            case 648 -> AutoPilot.hold(o.keyUse, false);
            // the tools with only a symbol
            case 660 -> { phase = "drill"; tool(mc, ModItems.DRILL.get(), ItemStack.EMPTY); AutoPilot.setView(0, 5); }
            case 670 -> AutoPilot.shot(mc, "hud_drill");
            case 672 -> { phase = "em"; tool(mc, ModItems.EM_PROJECTOR.get(), ItemStack.EMPTY); }
            case 682 -> AutoPilot.shot(mc, "hud_em");
            case 684 -> { phase = "chainsaw"; tool(mc, ModItems.CHAINSAW.get(), ItemStack.EMPTY); }
            case 694 -> AutoPilot.shot(mc, "hud_chainsaw");
            // a star reactor: endless, nothing burns
            case 700 -> { phase = "reactor"; tool(mc, ModItems.CHAINGUN.get(), ItemStack.EMPTY);
                          server(mc, (p, x) -> {
                              x.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(ModItems.STAR_REACTOR.get()));
                              x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.NETHER_STAR)); }); }
            case 730 -> AutoPilot.shot(mc, "hud_reactor");
            // no helmet: no HUD (vanilla's vehicle hearts are back)
            case 734 -> { phase = "no_helmet"; server(mc, (p, x) -> x.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, ItemStack.EMPTY)); }
            case 744 -> AutoPilot.shot(mc, "hud_no_helmet");
            case 746 -> { close(); return false; }
            default -> {}
        }
        if (s >= 6) {
            log(mc, s);
        }
        return true;
    }

    /** Run on the server with the pilot and the scene's suit. */
    private static void server(Minecraft mc, java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, PowerArmorEntity> action) {
        AutoPilot.onServer(mc, p -> {
            PowerArmorEntity x = suit;
            if (x != null) {
                action.accept(p, x);
            }
        });
    }

    /** Swap the socket tool (and the ammo slot) on the server: the menu's job in the game. */
    private static void tool(Minecraft mc, Item tool, ItemStack ammo) {
        server(mc, (p, x) -> {
            x.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(tool));
            x.getInventory().setItem(PowerArmorEntity.AMMO_SLOT, ammo);
        });
    }

    /** Flat grass 120 blocks east by day; the suit (firebox, coal, helmet, chaingun) with a survival pilot inside, who
     *  carries two stacks of nuggets (in the inventory, not the hand: the hand would take the right click). */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            player.stopRiding();
            int x = player.getBlockX() + 120, z = player.getBlockZ();
            int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos b = new BlockPos(x, g, z);
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -6; dz <= 40; dz++) {
                    for (int y = g - 3; y <= g + 6; y++) {
                        level.setBlock(b.offset(dx, 0, dz).atY(y), y < g - 1 ? Blocks.DIRT.defaultBlockState()
                                : y == g - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(s);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 40));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
            s.snapTo(x + 0.5, g, z + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.brain().setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().setItem(9, new ItemStack(Items.IRON_NUGGET, 64));
            player.getInventory().setItem(10, new ItemStack(Items.IRON_NUGGET, 64));
            player.setGameMode(GameType.SURVIVAL); // ammo really counts down
            player.setShiftKeyDown(false);
            player.teleportTo(x + 0.5, g, z - 1.5);
            player.startRiding(s, true, true);
        });
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(FMLPaths.GAMEDIR.get().resolve("autopilot_hud.csv"));
            csv.write("tick,phase,shown,hull,fuel_bar,fuel_text,embers,critical,powered,tool,count,bar,server_health,server_energy,server_powered\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("hud csv", e);
        }
    }

    private static void log(Minecraft mc, int s) {
        PowerArmorEntity x = suit;
        BufferedWriter w = csv;
        if (x == null || w == null) {
            return;
        }
        String p = phase;
        SuitHud.Shown h = SuitHud.shown();
        String client = h == null ? "0,,,,,,,,," : String.format(Locale.ROOT, "1,%.2f,%.2f,%s,%d,%b,%b,%s,%d,%.2f", h.hull(), h.fuel(),
                h.fuelText().replace(',', ' '), h.embers(), h.critical(), h.powered(), h.tool(), h.count(), h.bar());
        Consumer<net.minecraft.server.level.ServerPlayer> write = player -> {
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%s,%.1f,%d,%b%n", s, p, client, x.getHealth(),
                        x.power().getTotalEnergy(), x.isPowered()));
            } catch (IOException ignored) {
                // closed at the end of the scene
            }
        };
        AutoPilot.onServer(mc, write);
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

    private HudScene() {}
}
