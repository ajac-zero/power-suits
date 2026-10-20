package dev.ajaczero.powerarmor.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A piece of the suit (torso, arm, leg, helmet): crafted on its own from iron blocks, lapis blocks and vanilla parts,
 * then four pieces + the helmet are assembled into the Power Armor at a crafting table (recipes in
 * data/powerarmor/recipe/suit_*.json and power_armor.json). For now they're only ingredients; the helmet will become
 * removable later (it seals the suit).
 */
public class SuitPartItem extends Item {
    public SuitPartItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
            TooltipFlag flag) {
        // One shared line: every part says what it's for ("item.powerarmor.suit_part.tooltip").
        tooltip.accept(Component.translatable("item.powerarmor.suit_part.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
