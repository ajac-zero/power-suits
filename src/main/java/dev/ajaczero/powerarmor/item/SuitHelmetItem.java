package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's helmet (its helmet slot: seals the suit, carries its mind). Its look is a data component, so one item has
 * every style (design sec. 4c): a smithing table gives a helmet a style (template + helmet + style object) or takes it
 * off again (helmet + iron block). The style changes the name ("Spartan Helmet") and adds a flavour line, nothing else.
 *
 * A data component is a typed piece of data stored on one ItemStack (like a sword's enchantments or a renamed item's
 * name): the item type stays powerarmor:suit_helmet, so the suit, the menu and the recipes need nothing new.
 */
public class SuitHelmetItem extends SuitPartItem {
    public SuitHelmetItem(Properties properties) {
        super(properties, "item.powerarmor.suit_helmet.tooltip");
    }

    /** The helmet's style, or null for the standard helmet. */
    public static @Nullable HelmetStyle style(ItemStack stack) {
        return stack.get(ModDataComponents.HELMET_STYLE.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        // A name typed in at an anvil still wins: vanilla draws the custom name over this one.
        HelmetStyle style = style(stack);
        return style == null ? super.getName(stack) : Component.translatable("item.powerarmor.suit_helmet." + style.getSerializedName());
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        super.addTooltip(stack, context, tooltip, flag);
        HelmetStyle style = style(stack);
        if (style != null) {
            // a line that hints at the helmet's inspiration, in the grey italics vanilla uses for flavour text
            tooltip.accept(Component.translatable("item.powerarmor.suit_helmet." + style.getSerializedName() + ".flavour")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
