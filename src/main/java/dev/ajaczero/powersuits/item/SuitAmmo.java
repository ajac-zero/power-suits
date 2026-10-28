package dev.ajaczero.powersuits.item;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Where the socket tools' ammo comes from (user, 2026-10-27): a pilot pays from their own inventory, like a bow's arrows
 * (the off hand first, then the rest; free in creative), and the machine mind's tools (and a rogue's) never run out. The
 * suit has no ammo of its own any more: the ammo slot and the hidden nugget store are gone (what old suits still hold
 * comes back out as items: PowerSuitEntity.returnOldAmmo).
 * <p>
 * Each tool says what a round is ({@code fits}): an iron nugget for the chaingun, TNT for the TNT launcher, an iron
 * ingot for the rail cannon.
 */
public final class SuitAmmo {
    /** What {@link #count} returns for a pilot who never runs out (creative). */
    public static final int ENDLESS = -1;

    private SuitAmmo() {
    }

    /** Server: use up one round for this operator. A pilot pays from their inventory (false if they carry none); the
     *  machine mind (any operator that isn't a player) fires for free. */
    public static boolean take(@Nullable LivingEntity operator, Predicate<ItemStack> fits) {
        if (!(operator instanceof Player player)) {
            return true;
        }
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && fits.test(offhand)) {
            offhand.shrink(1);
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && fits.test(stack)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Both sides: does this operator have a round to fire (always, for the machine mind)? */
    public static boolean has(@Nullable LivingEntity operator, Predicate<ItemStack> fits) {
        return !(operator instanceof Player player) || count(player, fits) != 0;
    }

    /** Both sides: the rounds this pilot carries, or {@link #ENDLESS} in creative. A client knows its own player's
     *  inventory, so the HUD can count without asking the server. */
    public static int count(Player player, Predicate<ItemStack> fits) {
        if (player.hasInfiniteMaterials()) {
            return ENDLESS;
        }
        Inventory inventory = player.getInventory();
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && fits.test(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }
}
