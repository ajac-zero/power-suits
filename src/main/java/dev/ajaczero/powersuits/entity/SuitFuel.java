package dev.ajaczero.powersuits.entity;

import dev.ajaczero.powersuits.PowerSuitsConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What the suit's engine burns, and for how long. Works like a furnace: the fuel slot holds a stack, the
 * engine takes one item at a time and burns it down, then takes the next.
 *
 * Values are "energy units". Walking burns 1 unit per tick (20 per second), so 1600 = 80 s of walking.
 * Standing still burns a quarter of that, sprinting double (see {@link PowerSuitEntity}).
 *
 * Since the engines (design sec. 4b) each engine has its own fuels: the firebox burns whatever a furnace burns, the
 * tidal engine drinks water on land, and the star reactor doesn't burn anything (its Nether Star powers it forever:
 * {@link #powersForever}). The fuel slot takes any engine's fuel, like a furnace's slot; what the installed engine
 * can't use just sits there.
 */
public final class SuitFuel {
    /** Energy one item of this stack gives the given engine, 0 if that engine can't burn it (or there's no engine).
     *  The firebox burns like a furnace: anything a furnace takes, for as many ticks as a furnace burns it (coal 1600 =
     *  1:20 of walking, a blaze rod 2400, a lava bucket 20000, planks 300, a stick 100...), see {@link FireboxFuel}.
     *  Since 26.x those burn times are data (the item's {@code cooking_fuel} component points at a number defined in
     *  the server's data packs), so the exact value needs a server; {@code level} may be a client level, see
     *  {@link FireboxFuel#burnTime}. The tidal engine's amounts come from the config (PowerSuitsConfig). */
    public static int energyOf(@Nullable SuitEngine engine, ItemStack stack, @Nullable Level level) {
        if (engine == SuitEngine.FIREBOX) {
            return FireboxFuel.burnTime(stack, level);
        } else if (engine == SuitEngine.TIDAL) {
            if (stack.is(Items.WATER_BUCKET)) {
                return PowerSuitsConfig.WATER_BUCKET_ENERGY.get();
            }
            if (isWaterBottle(stack)) {
                return PowerSuitsConfig.WATER_BOTTLE_ENERGY.get();
            }
        }
        return 0;
    }

    /** Can this engine burn the stack? Needs no server: for the firebox it's vanilla's furnace-slot test (the item has a
     *  burn time at all), so the menu agrees on both sides. */
    public static boolean isFuel(@Nullable SuitEngine engine, ItemStack stack) {
        if (engine == SuitEngine.FIREBOX) {
            return stack.has(DataComponents.COOKING_FUEL);
        }
        return energyOf(engine, stack, null) > 0;
    }

    /** Does this stack in the fuel slot power the engine without ever running out (a Nether Star in a reactor)? */
    public static boolean powersForever(@Nullable SuitEngine engine, ItemStack stack) {
        return engine == SuitEngine.REACTOR && stack.is(Items.NETHER_STAR);
    }

    /** May this go in the fuel slot: fuel for any engine, or what a burnt one leaves (empty bucket / glass bottle). */
    public static boolean fitsFuelSlot(ItemStack stack) {
        for (SuitEngine engine : SuitEngine.values()) {
            if (isFuel(engine, stack) || powersForever(engine, stack)) {
                return true;
            }
        }
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    /** A water bottle is a potion item holding plain water (vanilla has no separate item for it). */
    public static boolean isWaterBottle(ItemStack stack) {
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    private SuitFuel() {}
}
