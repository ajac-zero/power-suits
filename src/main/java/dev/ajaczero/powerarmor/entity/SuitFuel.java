package dev.ajaczero.powerarmor.entity;

import java.util.Map;
import net.minecraft.world.item.Item;
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
    /** Coal is the weakest fuel, blaze rods the strongest. Lava sits in between and leaves its bucket. */
    private static final Map<Item, Integer> ENERGY = Map.of(
            Items.COAL, 1600,       // 1:20 of walking
            Items.CHARCOAL, 1600,
            Items.LAVA_BUCKET, 3200, // 2:40
            Items.BLAZE_ROD, 4800);  // 4:00

    /** Energy one item of this stack gives, 0 if the suit can't burn it. */
    public static int energyOf(ItemStack stack) {
        return stack.isEmpty() ? 0 : ENERGY.getOrDefault(stack.getItem(), 0);
    }

    public static boolean isFuel(ItemStack stack) {
        return energyOf(stack) > 0;
    }

    private SuitFuel() {}
}
