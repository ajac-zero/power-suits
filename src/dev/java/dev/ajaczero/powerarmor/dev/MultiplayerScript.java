package dev.ajaczero.powerarmor.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * DEV TOOL, inert unless the dedicated server starts with -Dpower_suits.mpscript=true (./gradlew runServerMp).
 * The server half of the multiplayer check (clients: client/dev/MultiplayerScene). Once players "Driver" and "Watcher"
 * are both in, it directs a scripted story and logs the server's truth to run/mp_server.csv each tick:
 * board (red suit) -> paint blue -> fuel pulled (suit dies) -> fuel back -> driver climbs out.
 * Everything the suit shows the other player (paint, power, passenger) must reach the watcher's client; the watcher
 * logs what it sees to run/mp-watch/mp_client.csv; compare the two side by side (there is no report script yet).
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class MultiplayerScript {
    private static final boolean ENABLED = Boolean.getBoolean("power_suits.mpscript");
    private static int tick = -1, settle;
    private static PowerArmorEntity suit;
    private static BufferedWriter csv;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        var server = event.getServer();
        ServerPlayer driver = find(server, "Driver"), watcher = find(server, "Watcher");
        if (tick < 0) {
            if (driver == null || watcher == null || ++settle < 100) {
                return; // let both clients finish loading terrain
            }
            tick = 0;
            try {
                csv = Files.newBufferedWriter(Path.of("mp_server.csv"));
                csv.write("tick,phase,suitX,suitZ,paint,canOperate,passenger,driverRiding\n");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (driver == null || watcher == null) {
            return;
        }
        ServerLevel level = driver.level();
        int t = tick++;
        String phase = switch (t / 100) { case 0 -> "board"; case 1 -> "drive_red"; case 2 -> "blue"; case 3 -> "nofuel"; case 4 -> "refuel"; default -> "out"; };
        if (t == 0) {
            level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            suit.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT, new ItemStack(ModItems.FIREBOX.get()));
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 64));
            suit.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.setPaint(DyeColor.RED);
            suit.snapTo(driver.getX(), driver.getY(), driver.getZ(), driver.getYRot(), 0);
            level.addFreshEntity(suit);
            driver.startRiding(suit);
            // The watcher stands 8 blocks to the suit's right, looking at its start point.
            watcher.connection.teleport(driver.getX() + 8, driver.getY(), driver.getZ(), 90, 5);
        }
        if (suit != null) {
            if (t == 200) { suit.setPaint(DyeColor.BLUE); }
            if (t == 300) { suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY); }
            if (t == 400) { suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 64)); }
            if (t == 500) { driver.stopRiding(); }
            try {
                csv.write(String.format(Locale.ROOT, "%d,%s,%.2f,%.2f,%s,%b,%b,%b%n", t, phase, suit.getX(), suit.getZ(),
                        suit.getPaint(), suit.canOperate(), suit.getControllingPassenger() != null, driver.getVehicle() == suit));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (t == 600) {
            try { csv.close(); } catch (IOException ignored) { }
            PowerArmor.LOGGER.info("[mp] script finished, server stopping");
            server.halt(false);
        }
    }

    private static ServerPlayer find(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name);
    }

    private MultiplayerScript() {}
}
