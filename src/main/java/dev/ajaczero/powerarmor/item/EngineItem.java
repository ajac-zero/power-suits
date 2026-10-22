package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.SuitEngine;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A suit engine (the backpack, design sec. 4b): goes in the suit's engine slot (menu). The item itself does nothing;
 * the suit reads which engine it is ({@link SuitEngine#of}) and runs by its rules.
 */
public class EngineItem extends Item {
    private final SuitEngine engine;

    public EngineItem(Properties properties, SuitEngine engine) {
        super(properties);
        this.engine = engine;
    }

    public SuitEngine engine() {
        return this.engine;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
            TooltipFlag flag) {
        // e.g. "item.powerarmor.tidal_engine.tooltip": what it runs on and where.
        tooltip.accept(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
