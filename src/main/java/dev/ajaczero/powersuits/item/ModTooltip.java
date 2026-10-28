package dev.ajaczero.powersuits.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * An item with tooltip lines of its own (what it does, its numbers, where it goes).
 *
 * Vanilla deprecated {@code Item.appendHoverText}: Mojang wants tooltips to come from data components. NeoForge's way
 * for lines that belong to the item is a "tooltip appender": PowerSuits registers one (RegisterTooltipAppendersEvent)
 * that hands every ModTooltip item its turn, at the same place in the tooltip where appendHoverText's lines went.
 */
public interface ModTooltip {
    void addTooltip(ItemStack stack, Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag);
}
