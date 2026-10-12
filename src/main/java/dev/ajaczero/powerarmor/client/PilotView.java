package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Client-side tweaks while someone is inside a suit. */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class PilotView {
    /** Don't draw players who are inside a suit (they'd poke through the armor). */
    @SubscribeEvent
    public static void hidePilot(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity().getVehicle() instanceof PowerArmorEntity) {
            event.setCanceled(true);
        }
    }

    /** No floating first-person hand while piloting. */
    @SubscribeEvent
    public static void hideHand(RenderHandEvent event) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof PowerArmorEntity) {
            event.setCanceled(true);
        }
    }

    /** Pull the third-person camera back so the whole 3-block suit fits on screen. */
    @SubscribeEvent
    public static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof PowerArmorEntity) {
            event.setDistance(Math.max(event.getDistance(), 6.5F));
        }
    }

    private PilotView() {}
}
