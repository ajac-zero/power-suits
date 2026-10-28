package dev.ajaczero.powersuits.client;

import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.SuitEngine;
import dev.ajaczero.powersuits.entity.SuitFuel;
import dev.ajaczero.powersuits.item.PowerSuitItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds "Power Armor fuel: 1:20 of walking" to the tooltip of every suit fuel (anything a furnace burns, water for the
 *  tidal engine, the Nether Star).
 *  ItemTooltipEvent lets a mod add lines to *other* items' tooltips, vanilla ones included. */
@EventBusSubscriber(modid = PowerSuits.MODID, value = Dist.CLIENT)
public final class FuelTooltip {
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var level = Minecraft.getInstance().level;
        int energy = SuitFuel.energyOf(SuitEngine.FIREBOX, stack, level);
        if (energy > 0) {
            event.getToolTip().add(Component.translatable("tooltip.power_suits.fuel", PowerSuitItem.formatWalkTime(energy))
                    .withStyle(ChatFormatting.GOLD));
        }
        int water = SuitFuel.energyOf(SuitEngine.TIDAL, stack, level); // a tidal engine on land
        if (water > 0) {
            event.getToolTip().add(Component.translatable("tooltip.power_suits.tidal_fuel", PowerSuitItem.formatWalkTime(water))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (SuitFuel.powersForever(SuitEngine.REACTOR, stack)) {
            event.getToolTip().add(Component.translatable("tooltip.power_suits.reactor_fuel").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private FuelTooltip() {}
}
