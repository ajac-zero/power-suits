package dev.ajaczero.powersuits.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The firebox's fuel table is the furnace's: asks a plain furnace how long it would burn an item.
 *
 * Since 26.x a burn time isn't a number in code any more: fuel items carry a {@code cooking_fuel} data component whose
 * burn time usually names a "context int provider" from the data packs ({@code minecraft:cooking/time_coal}: 1600,
 * halved in a blast furnace or smoker). Working that out needs the furnace's loot context (which block is asking), so
 * rather than copy that, we keep a furnace block entity that never goes into a world and call its own
 * {@code getBurnDuration}. Data packs and mods that change furnace fuels change the suit's fuels with them.
 */
final class FireboxFuel {
    /** Ticks a furnace burns one of this item (0 = not a fuel). Exact on the server. On a client (tooltips, the packed
     *  suit's range) the numbers live on the server: singleplayer asks its built-in server, a client of a remote
     *  server only knows plain-number burn times and says 0 for the rest. The suit's own gauge doesn't need this on the
     *  client: the server syncs it ({@code PowerSuitEntity.FUEL_ENERGY}). */
    static int burnTime(ItemStack stack, @Nullable Level level) {
        var fuel = stack.get(DataComponents.COOKING_FUEL);
        if (fuel == null) {
            return 0;
        }
        ServerLevel server = level instanceof ServerLevel s ? s : integratedServerLevel();
        if (server == null) {
            return fuel.burnTime() instanceof ResolvableInt.Constant constant ? constant.value() : 0;
        }
        return probe().burnTime(server, stack);
    }

    private static @Nullable ServerLevel integratedServerLevel() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.overworld();
    }

    private static @Nullable Probe probe;

    /** Created on first use (block entities can't be built while the game is still registering things). */
    private static Probe probe() {
        if (probe == null) {
            probe = new Probe();
        }
        return probe;
    }

    /** A normal furnace (not a blast furnace or smoker, which burn fuel twice as fast), only used to ask. Its
     *  {@code getBurnDuration} is protected, so a subclass passes it on. It holds no state we touch, so one serves
     *  every suit. */
    private static final class Probe extends FurnaceBlockEntity {
        Probe() {
            super(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        }

        int burnTime(ServerLevel level, ItemStack stack) {
            return this.getBurnDuration(level, stack);
        }
    }

    private FireboxFuel() {}
}
