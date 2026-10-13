package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the suit burns, and for how long. Works like a furnace: the fuel slot holds a stack, the
 * engine takes one item at a time and burns it down, then takes the next.
 *
 * Values are "energy units". Walking burns 1 unit per tick (20 per second), so 1600 = 80 s of walking.
 * Standing still burns a quarter of that, sprinting double (see {@link PowerArmorEntity}).
 */
public final class SuitFuel {
    /** Energy one item of this stack gives, 0 if the suit can't burn it. Coal is the weakest fuel (default
     *  1:20 of walking), blaze rods the strongest (4:00); lava (2:40) sits in between and leaves its bucket.
     *  The amounts come from the config (PowerArmorConfig), so they can be tuned without rebuilding. */
    public static int energyOf(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return PowerArmorConfig.COAL_ENERGY.get();
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return PowerArmorConfig.LAVA_ENERGY.get();
        }
        if (stack.is(Items.BLAZE_ROD)) {
            return PowerArmorConfig.BLAZE_ENERGY.get();
        }
        return 0;
    }

    public static boolean isFuel(ItemStack stack) {
        return energyOf(stack) > 0;
    }

    private SuitFuel() {}
}
