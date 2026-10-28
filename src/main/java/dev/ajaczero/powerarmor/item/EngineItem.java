package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.SuitEngine;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A suit engine (the backpack, design sec. 4b): goes in the suit's engine slot (menu). The item itself does nothing;
 * the suit reads which engine it is ({@link SuitEngine#of}) and runs by its rules.
 */
public class EngineItem extends Item implements ModTooltip {
    private final SuitEngine engine;

    public EngineItem(Properties properties, SuitEngine engine) {
        super(properties);
        this.engine = engine;
    }

    public SuitEngine engine() {
        return this.engine;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        // e.g. "item.power_suits.tidal_engine.tooltip": what it runs on and where.
        tooltip.accept(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
