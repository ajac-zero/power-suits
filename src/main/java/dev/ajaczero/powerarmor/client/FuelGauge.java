package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.item.ChaingunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The fuel gauge next to the hotbar while you pilot a suit: a flame that shrinks as the current item
 * burns, the fuel slot's item and count, how long the fuel lasts at walking pace, and with the chaingun installed
 * how many bullets (iron nuggets) you carry and the barrels' heat.
 * It's a "GUI layer" (registered in ClientSetup), drawn every frame on top of the vanilla hotbar.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class FuelGauge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "fuel_gauge");
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/power_armor.png");
    private static final ResourceLocation LIT_PROGRESS = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 91 + 8;  // just right of the hotbar
        int y = graphics.guiHeight() - 19;
        boolean low = suit.isLowOnFuel();
        boolean blink = low && (suit.tickCount / 5) % 2 == 0;

        // Grey (unlit) flame from our menu texture, then the lit part on top.
        graphics.blit(BACKGROUND, x, y, 73, 35, 14, 14);
        if (suit.getBurnLeft() > 0 && suit.getBurnTotal() > 0 && !blink) {
            int h = Mth.ceil(Mth.clamp(suit.getBurnLeft() / (float) suit.getBurnTotal(), 0F, 1F) * 13F) + 1;
            graphics.blitSprite(LIT_PROGRESS, 14, 14, 0, 14 - h, x, y + 14 - h, 14, h);
        }
        ItemStack fuel = suit.getFuelStack();
        if (!fuel.isEmpty()) {
            graphics.renderItem(fuel, x + 16, y - 1);
            graphics.renderItemDecorations(mc.font, fuel, x + 16, y - 1);
        }
        int energy = suit.getTotalEnergy();
        Component text = energy > 0 ? Component.literal(PowerArmorItem.formatWalkTime(energy))
                : Component.translatable("gui.powerarmor.no_fuel");
        int colour = energy == 0 ? 0xFFFF5555 : low ? (blink ? 0xFFFF5555 : 0xFFFFB13B) : 0xFFFFFFFF;
        graphics.drawString(mc.font, text, x + 36, y + 3, colour, true);
        if (suit.getSocketStack().getItem() instanceof ChaingunItem) {
            // Bullets left (iron nuggets carried), just above the fuel: red when there are none.
            int bullets = mc.player.hasInfiniteMaterials() ? -1 : ChaingunItem.countBullets(mc.player);
            graphics.renderItem(BULLET_ICON, x + 16, y - 18);
            Component count = Component.literal(bullets < 0 ? "\u221e" : Integer.toString(bullets));
            graphics.drawString(mc.font, count, x + 36, y - 14, bullets == 0 ? 0xFFFF5555 : 0xFFFFFFFF, true);
            // Barrel heat (with overheat on): a thin bar under the count, orange to red, blinking when overheated.
            float heat = suit.getToolHeat();
            if (heat > 0.01F && !(heat >= 0.99F && (suit.tickCount / 4) % 2 == 0)) {
                int w = Math.round(24 * heat);
                graphics.fill(x + 36, y - 4, x + 60, y - 2, 0x80000000);
                graphics.fill(x + 36, y - 4, x + 36 + w, y - 2, heat > 0.75F ? 0xFFFF3B1E : 0xFFFF9A2E);
            }
        }
    }

    private static final ItemStack BULLET_ICON = new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET);

    /** Warning beep every 2 seconds while running on the last few seconds of fuel. */
    @SubscribeEvent
    public static void lowFuelBeep(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity suit && !mc.isPaused()
                && suit.isPowered() && suit.isLowOnFuel() && suit.tickCount % 40 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.7F, 0.5F));
        }
    }

    private FuelGauge() {}
}
