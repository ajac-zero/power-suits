package dev.ajaczero.powerarmor.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.TooltipFlag;

/**
 * A helmet style's smithing template (design sec. 4c): found in chests of the structure its inspiration would come from
 * (loot modifiers in data/power_suits/loot_modifiers), used up at a smithing table with a suit helmet and the style's
 * object (recipes data/power_suits/recipe/helmet_*.json), copied like vanilla's trim templates (7 diamonds + the object).
 *
 * Vanilla's SmithingTemplateItem already draws the tooltip ("Applies to: ... Ingredients: ...") and the smithing
 * screen's hints for the empty slots; this only adds the style's flavour line under it.
 */
public class HelmetTemplateItem extends SmithingTemplateItem implements ModTooltip {
    private static final Identifier EMPTY_SLOT_HELMET = Identifier.withDefaultNamespace("container/slot/helmet");
    private final HelmetStyle style;

    public HelmetTemplateItem(Properties properties, HelmetStyle style) {
        super(Component.translatable("item.power_suits.helmet_template.applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable("item.power_suits." + style.getSerializedName() + "_template.ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable("item.power_suits.helmet_template.base_slot_description"),
                Component.translatable("item.power_suits." + style.getSerializedName() + "_template.additions_slot_description"),
                List.of(EMPTY_SLOT_HELMET), List.of(), properties);
        this.style = style;
    }

    public HelmetStyle style() {
        return this.style;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.power_suits.suit_helmet." + this.style.getSerializedName() + ".flavour")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
