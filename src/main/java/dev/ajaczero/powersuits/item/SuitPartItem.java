package dev.ajaczero.powersuits.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A piece of the suit (torso, arm, leg, helmet): crafted on its own from iron blocks, lapis blocks and vanilla parts.
 * Torso, arms and legs are assembled into the Power Armor at a crafting table (recipes in
 * data/power_suits/recipe/suit_*.json and power_suit.json); the helmet goes on the finished suit (its helmet slot):
 * it seals the suit and lets its machine mind run.
 */
public class SuitPartItem extends Item implements ModTooltip {
    private final String tooltipKey;

    public SuitPartItem(Properties properties, String tooltipKey) {
        super(properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        // What the part is for: the body parts share one line, the helmet has its own.
        tooltip.accept(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
