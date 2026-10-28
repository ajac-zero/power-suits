package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.client.SuitHud;
import dev.ajaczero.powerarmor.client.SuitVisor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.TntLauncherItem;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Autopilot scene for the pilot's HUD (client/SuitHud, 2026-10-26): every state it can show, from the cockpit, on flat
 * grass by day. A survival pilot (so ammo counts) in a suit with a firebox, coal, a helmet and a chaingun: idle (one
 * ember, visor colour), walking (one), sprinting (two, orange); the charging rail cannon later (three, red); a long burst until the barrels overheat (the
 * chaingun's corner readout: HEAT orange from half, red in its last 15%, then OVERHEAT, staying red while it cools all
 * the way down: {@code ap_hud_chaingun_cooling}; the rest of the HUD stays in the visor colour); down to the last
 * bullets (the AMMO counter red); the hull under half (orange), then in its last 15% (red); low fuel (red, beeping); no fuel (the glass dark, nothing on it); then each tool in turn with its
 * own visor HUD (client/hud, 2026-10-27): the rail cannon's brackets closing in as it charges and locking at READY; the
 * TNT launcher's drum full, firing, empty and loading back chamber by chamber, its arc near and far, ending on a cow,
 * and a real round flying along it; the
 * flamethrower and the drill (nothing); the EM projector scanning on its own (two pigs and some ingots marked), aimed
 * at a pig (named), holding it (a box round it, its name and health, the arc it would be thrown along, the other pig
 * lit up in the way) and bowling it into the other pig ({@code ap_hud_em_bowl_5..9}); the chainsaw (nothing); a star reactor (endless fuel, no embers); boss bars
 * and effects moved out of the way; and without the helmet (no HUD, vanilla's hearts).
 * Shots {@code ap_hud_*.png}; log run/autopilot_hud.csv: per tick what the HUD drew (hull, fuel bar and text, embers,
 * alert (the suit's: none, warning, critical), powered, tool, tool_hud: what the tool's own readout said it drew, e.g. the TNT arc's
 * impact distance and flight ticks; hud_open / visor_sweep: how much of the screen the visor's scan had turned on as
 * the HUD and SuitVisor drew it, 0 .. 1: sweeping in after the fuel runs out ({@code ap_hud_close_*}), sweeping out
 * after the refuel ({@code ap_hud_warm_*})) next to the server's own numbers. Runs alone with ./gradlew
 * runAutopilotHud (~45 s).
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
            // cooling: the bar empties but stays red and reads OVERHEAT until it's empty (the gun is locked till then)
            case 370 -> AutoPilot.shot(mc, "hud_chaingun_cooling");
            // the last bullets (the barrels are cold again by the shot)
            case 380 -> { phase = "last_bullets"; server(mc, (p, x) -> {
                              p.getInventory().clearContent();
                              p.getInventory().setItem(9, new ItemStack(Items.IRON_NUGGET, 10)); }); }
            case 396 -> AutoPilot.shot(mc, "hud_last_bullets");
            case 397 -> server(mc, (p, x) -> p.getInventory().setItem(9, new ItemStack(Items.IRON_NUGGET, 64)));
            // under half the hull: a warning (orange); then its last 15%: critical (red)
            case 398 -> { phase = "hull_warn"; server(mc, (p, x) -> x.setHealth(x.getMaxHealth() * 0.4F)); }
            case 404 -> AutoPilot.shot(mc, "hud_hull_warn");
            case 405 -> { phase = "hull_low"; server(mc, (p, x) -> x.setHealth(x.getMaxHealth() * 0.1F)); }
            case 410 -> AutoPilot.shot(mc, "hud_hull_low");
            case 412 -> server(mc, (p, x) -> x.setHealth(x.getMaxHealth()));
            // low fuel: the last few hundred energy in the burning item, nothing in the slot
            case 420 -> { phase = "fuel_low"; server(mc, (p, x) -> {
                              x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY);
                              x.power().setBurn(400, 1600); }); }
            case 432 -> AutoPilot.shot(mc, "hud_fuel_low");
            // out of fuel: the suit powers down, the HUD fades
            case 436 -> { phase = "off"; server(mc, (p, x) -> x.power().setBurn(0, 0)); }
            // the visor shutting down (VisorPower): two lines sweep in from the top and bottom, turning the metal black
            // and wiping the readouts as they pass, meet in the middle and shrink away (0.5 s), then nothing (hud_open)
            case 439, 440, 441, 442, 443, 445 -> AutoPilot.shot(mc, "hud_close_" + (s - 436));
            case 460 -> AutoPilot.shot(mc, "hud_off");
                        case 468 -> { phase = "refuel"; server(mc, (p, x) -> x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 40))); }
            // the visor's warm-up: a line across the middle widens, splits and sweeps up and down, turning the metal
            // see-through and uncovering the readouts as it passes (0.57 s; hud_open / visor_sweep columns)
            case 471, 472, 473, 474, 475, 476, 477, 479 -> AutoPilot.shot(mc, "hud_warm_" + (s - 468));
            // the rail cannon: charging, then full (it fires on release)
            case 480 -> { phase = "rail"; tool(mc, ModItems.RAIL_CANNON.get(), new ItemStack(Items.IRON_INGOT, 6)); }
            case 492 -> AutoPilot.shot(mc, "hud_rail_idle");
            case 494 -> AutoPilot.hold(o.keyUse, true);
            case 512 -> AutoPilot.shot(mc, "hud_rail_charging");
            case 532 -> AutoPilot.shot(mc, "hud_rail_ready");
            case 534 -> AutoPilot.hold(o.keyUse, false);
            // the TNT launcher (TntLauncherHud): a full drum, the trigger held until it's empty (4 rounds, one every half
            // second), then the chambers loading back one by one from the ammo slot
            case 550 -> { phase = "tnt"; tool(mc, ModItems.TNT_LAUNCHER.get(), new ItemStack(Items.TNT, 8)); AutoPilot.setView(0, -35);
                          server(mc, (p, x) -> TntLauncherItem.setLoaded(x, x.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT), TntLauncherItem.DRUM)); }
            case 556 -> AutoPilot.shot(mc, "hud_tnt_ready");
            case 558 -> AutoPilot.hold(o.keyUse, true);
            case 574 -> AutoPilot.shot(mc, "hud_tnt_firing");
            case 592 -> { AutoPilot.hold(o.keyUse, false); AutoPilot.shot(mc, "hud_tnt_empty"); }
            case 606 -> AutoPilot.shot(mc, "hud_tnt_reload");
            // its arc from the muzzle: where a round would land, looking down a little (near), at our feet (red: inside
            // the blast) and lobbing it (far); then on a cow 14
            // blocks out (the arc ends on it); then a real round in flight along the arc it showed
            case 602 -> { phase = "tnt_arc"; AutoPilot.setView(0, 8); }
            case 610 -> AutoPilot.shot(mc, "hud_tnt_arc_near");
            case 611 -> AutoPilot.setView(0, 35); // close enough for its own blast: the arc turns red
            case 615 -> AutoPilot.shot(mc, "hud_tnt_danger");
            case 616 -> AutoPilot.setView(0, -8);
            case 620 -> AutoPilot.shot(mc, "hud_tnt_arc_far");
            case 622 -> { phase = "tnt_mob"; server(mc, (p, x) -> target = statue(p, EntityTypes.COW, x.position().add(0, 0, 14))); }
            case 624 -> AutoPilot.aimTarget = mc.player.getVehicle().position().add(0, 0.7, 14);
            case 634 -> AutoPilot.shot(mc, "hud_tnt_on_mob");
            case 636 -> { AutoPilot.aimTarget = null; AutoPilot.setView(0, -8); server(mc, (p, x) -> discardTarget()); }
            case 640 -> { phase = "tnt_flight"; AutoPilot.hold(o.keyUse, true); }
            case 642 -> AutoPilot.hold(o.keyUse, false);
            case 648 -> AutoPilot.shot(mc, "hud_tnt_flight");
            // the flamethrower, burning hard (no HUD of its own)
            case 670 -> { phase = "flame"; tool(mc, ModItems.FLAMETHROWER.get(), ItemStack.EMPTY); AutoPilot.setView(0, 10); }
            case 680 -> AutoPilot.hold(o.keyUse, true);
            case 706 -> AutoPilot.shot(mc, "hud_flame");
            case 708 -> AutoPilot.hold(o.keyUse, false);
            // the drill: nothing on the visor
            case 720 -> { phase = "drill"; tool(mc, ModItems.DRILL.get(), ItemStack.EMPTY); AutoPilot.setView(0, 5); }
            case 730 -> AutoPilot.shot(mc, "hud_drill");
            // the EM projector (EmProjectorHud): it scans on its own: two slowed pigs and a few dropped ingots get faint
            // brackets ("hud_em"); the one under the crosshair is named ("hud_em_aim"); held, its throw arc runs at the
            // other pig, which lights up (in the way: "hud_em_held"); let go, it bowls the other pig over ("hud_em_bowl")
            case 732 -> { phase = "em"; tool(mc, ModItems.EM_PROJECTOR.get(), ItemStack.EMPTY); AutoPilot.setView(0, 12);
                          server(mc, (p, x) -> {
                              // off to the side of where the flamethrower left fires
                              target = slowPig(p, x.position().add(-4, 0, 6));
                              second = slowPig(p, x.position().add(3, 0, 10));
                              ItemEntity ingots = new ItemEntity(p.level(), x.getX() + 1, x.getY(), x.getZ() + 5, new ItemStack(Items.IRON_INGOT, 3));
                              ingots.setPickUpDelay(32767);
                              p.level().addFreshEntity(ingots);
                              third = ingots; }); }
            case 742 -> AutoPilot.shot(mc, "hud_em");
            case 744 -> { phase = "em_aim"; Entity pig = target; if (pig != null) { AutoPilot.aimTarget = pig.position().add(0, 0.45, 0); } }
            case 752 -> AutoPilot.shot(mc, "hud_em_aim");
            case 754 -> { phase = "em_held"; AutoPilot.aimTarget = null; AutoPilot.hold(o.keyUse, true); }
            // caught: aim the throw at the other pig (the arc runs at it, and it lights up: it's in the way)
            case 772 -> { Entity pig = second; if (pig != null) { AutoPilot.aimTarget = pig.position().add(0, 2.2, 0); } } // above it: the throw drops
            case 782 -> AutoPilot.shot(mc, "hud_em_held");
            case 784 -> { phase = "em_bowl"; AutoPilot.aimTarget = null; AutoPilot.hold(o.keyUse, false); }
            case 789, 791, 793 -> AutoPilot.shot(mc, "hud_em_bowl_" + (s - 784));
            case 800 -> { AutoPilot.setView(0, 5); server(mc, (p, x) -> discardTarget()); }
            case 802 -> { phase = "chainsaw"; tool(mc, ModItems.CHAINSAW.get(), ItemStack.EMPTY); }
            case 812 -> AutoPilot.shot(mc, "hud_chainsaw");
            // a star reactor: endless, nothing burns
            case 820 -> { phase = "reactor"; tool(mc, ModItems.CHAINGUN.get(), ItemStack.EMPTY);
                          server(mc, (p, x) -> {
                              x.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(ModItems.STAR_REACTOR.get()));
                              x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.NETHER_STAR)); }); }
            case 850 -> AutoPilot.shot(mc, "hud_reactor");
            // two boss bars and two effects: vanilla draws them where the compass and the tool readout are; with the
            // HUD up they move down (boss bars under the compass, effect icons under the top right corner)
            case 854 -> { phase = "bosses"; server(mc, (p, x) -> {
                              command(p, "bossbar add powerarmor:hud_a \"Wither\"");
                              command(p, "bossbar set powerarmor:hud_a color purple");
                              command(p, "bossbar set powerarmor:hud_a value 70");
                              command(p, "bossbar add powerarmor:hud_b \"Ender Dragon\"");
                              command(p, "bossbar set powerarmor:hud_b color pink");
                              command(p, "bossbar set powerarmor:hud_b value 35");
                              command(p, "bossbar set powerarmor:hud_a players @a");
                              command(p, "bossbar set powerarmor:hud_b players @a");
                              p.addEffect(new MobEffectInstance(MobEffects.SPEED, 600));
                              p.addEffect(new MobEffectInstance(MobEffects.POISON, 600)); }); }
            case 864 -> AutoPilot.shot(mc, "hud_bosses");
            case 866 -> server(mc, (p, x) -> {
                              command(p, "bossbar remove powerarmor:hud_a");
                              command(p, "bossbar remove powerarmor:hud_b");
                              p.removeAllEffects(); });
            // no helmet: no HUD (vanilla's vehicle hearts are back)
            case 870 -> { phase = "no_helmet"; server(mc, (p, x) -> x.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, ItemStack.EMPTY)); }
            case 880 -> AutoPilot.shot(mc, "hud_no_helmet");
            case 882 -> { close(); return false; }
            default -> {}
        }
        if (s >= 6) {
            log(mc, s);
        }
        return true;
    }

    /** What the scene put out (the TNT arc's cow; the EM projector's pigs and ingots), to remove after. */
    private static volatile Entity target, second, third;

    /** A mob that stands still (no AI) on the ground at {@code at}: what the TNT arc lands on. */
    private static Entity statue(net.minecraft.server.level.ServerPlayer p, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(p.level(), EntitySpawnReason.COMMAND);
        mob.snapTo(at.x, at.y, at.z, 180, 0);
        mob.setNoAi(true);
        p.level().addFreshEntity(mob);
        return mob;
    }

    private static void discardTarget() {
        for (Entity t : new Entity[] {target, second, third}) {
            if (t != null) {
                t.discard();
            }
        }
        target = second = third = null;
    }

    /** A pig too slowed to walk off (so it waits to be picked up, or bowled over). */
    private static Pig slowPig(net.minecraft.server.level.ServerPlayer p, Vec3 at) {
        Pig pig = EntityTypes.PIG.create(p.level(), EntitySpawnReason.COMMAND);
        pig.snapTo(at.x, at.y, at.z, 0, 0);
        pig.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 10));
        p.level().addFreshEntity(pig);
        return pig;
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

    /** A command as the pilot would type it (op level from the dev world). */
    private static void command(net.minecraft.server.level.ServerPlayer p, String cmd) {
        p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput().withPermission(
                net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER), cmd);
    }

    /** Swap the socket tool on the server (the menu's job in the game) and give the pilot its ammo (inventory slot 10). */
    private static void tool(Minecraft mc, Item tool, ItemStack ammo) {
        server(mc, (p, x) -> {
            x.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(tool));
            p.getInventory().setItem(10, ammo);
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
            csv.write("tick,phase,shown,hull,fuel_bar,fuel_text,embers,alert,powered,tool,tool_hud,compass,hud_open,visor_sweep,server_health,server_energy,server_powered\n");
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
        String client = (h == null ? "0,,,,,,,,,," : String.format(Locale.ROOT, "1,%.2f,%.2f,%s,%d,%s,%b,%s,%s,%s,%.2f", h.hull(), h.fuel(),
                h.fuelText().replace(',', ' '), h.embers(), h.alert().name().toLowerCase(Locale.ROOT), h.powered(), h.tool(), h.toolHud().replace(',', ';'),
                h.compass().replace(',', ' '), h.open())) + String.format(Locale.ROOT, ",%.2f", SuitVisor.lastSweep());
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
