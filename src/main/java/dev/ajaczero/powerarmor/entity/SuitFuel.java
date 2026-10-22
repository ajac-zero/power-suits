package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import org.jetbrains.annotations.Nullable;

/**
 * What the suit's engine burns, and for how long. Works like a furnace: the fuel slot holds a stack, the
 * engine takes one item at a time and burns it down, then takes the next.
 *
 * Values are "energy units". Walking burns 1 unit per tick (20 per second), so 1600 = 80 s of walking.
 * Standing still burns a quarter of that, sprinting double (see {@link PowerArmorEntity}).
 *
 * Since the engines (design sec. 4b) each engine has its own fuels: the firebox burns coal, lava and blaze rods, the
 * tidal engine drinks water on land, and the star reactor doesn't burn anything (its Nether Star powers it forever:
 * {@link #powersForever}). The fuel slot takes any engine's fuel, like a furnace's slot; what the installed engine
 * can't use just sits there.
 */
public final class SuitFuel {
    /** Energy one item of this stack gives the given engine, 0 if that engine can't burn it (or there's no engine).
     *  Coal is the firebox's weakest fuel (default 1:20 of walking), blaze rods the strongest (4:00); lava (2:40) sits in
     *  between and leaves its bucket. The amounts come from the config (PowerArmorConfig), so they can be tuned without
     *  rebuilding. */
    public static int energyOf(@Nullable SuitEngine engine, ItemStack stack) {
        if (engine == SuitEngine.FIREBOX) {
            if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
                return PowerArmorConfig.COAL_ENERGY.get();
            }
            if (stack.is(Items.LAVA_BUCKET)) {
                return PowerArmorConfig.LAVA_ENERGY.get();
            }
            if (stack.is(Items.BLAZE_ROD)) {
                return PowerArmorConfig.BLAZE_ENERGY.get();
            }
        } else if (engine == SuitEngine.TIDAL) {
            if (stack.is(Items.WATER_BUCKET)) {
                return PowerArmorConfig.WATER_BUCKET_ENERGY.get();
            }
            if (isWaterBottle(stack)) {
                return PowerArmorConfig.WATER_BOTTLE_ENERGY.get();
            }
        }
        return 0;
    }

    /** The firebox's fuels (the original table; the tooltips and the hint text describe these). */
    public static int energyOf(ItemStack stack) {
        return energyOf(SuitEngine.FIREBOX, stack);
    }

    public static boolean isFuel(@Nullable SuitEngine engine, ItemStack stack) {
        return energyOf(engine, stack) > 0;
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
