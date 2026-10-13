package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.SuitFuel;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds "Power Armor fuel: 1:20 of walking" to the tooltip of every suit fuel (coal, charcoal, lava, blaze rods).
 *  ItemTooltipEvent lets a mod add lines to *other* items' tooltips, vanilla ones included. */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class FuelTooltip {
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        int energy = SuitFuel.energyOf(event.getItemStack());
        if (energy > 0) {
            event.getToolTip().add(Component.translatable("tooltip.powerarmor.fuel", PowerArmorItem.formatWalkTime(energy))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private FuelTooltip() {}
}
