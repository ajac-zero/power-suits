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
        var stack = event.getItemStack();
        int energy = SuitFuel.energyOf(stack);
        if (energy > 0) {
            event.getToolTip().add(Component.translatable("tooltip.powerarmor.fuel", PowerArmorItem.formatWalkTime(energy))
                    .withStyle(ChatFormatting.GOLD));
        }
        int water = SuitFuel.energyOf(dev.ajaczero.powerarmor.entity.SuitEngine.TIDAL, stack); // a tidal engine on land
        if (water > 0) {
            event.getToolTip().add(Component.translatable("tooltip.powerarmor.tidal_fuel", PowerArmorItem.formatWalkTime(water))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (SuitFuel.powersForever(dev.ajaczero.powerarmor.entity.SuitEngine.REACTOR, stack)) {
            event.getToolTip().add(Component.translatable("tooltip.powerarmor.reactor_fuel").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private FuelTooltip() {}
}
