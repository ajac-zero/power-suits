package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.util.Mth;
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

    /** No floating first-person hand while piloting; the selected item is drawn on the left instead
     *  (the suit's left hand holds it; the cannon is on the right). */
    @SubscribeEvent
    public static void hideHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof PowerArmorEntity) {
            event.setCanceled(true);
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                FirstPersonLeftHand.render(event, player);
            }
        }
    }

    /** Pull the third-person camera back so the whole 3-block suit fits on screen. */
    @SubscribeEvent
    public static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof PowerArmorEntity) {
            event.setDistance(Math.max(event.getDistance(), 6.5F));
        }
    }

    /** Tell our suit how far the jump bar is charged while space is held, so it can crouch. */
    @SubscribeEvent
    public static void jumpCharge(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity suit) {
            suit.setClientJumpCharge(mc.options.keyJump.isDown() ? Math.max(0.01F, mc.player.getJumpRidingScale()) : 0F);
        }
    }

    /** Cockpit stomp: dip the view with the mech's hips on every footfall (first person only). */
    @SubscribeEvent
    public static void cockpitBob(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity suit && mc.options.getCameraType().isFirstPerson()) {
            // hipBob is in model pixels; ~0.6 degrees per pixel of drop, capped.
            event.setPitch(event.getPitch() - Mth.clamp(suit.getHipBob() * 0.6F, -2.5F, 2.5F));
        }
    }

    /** Widen the view a little with speed, on top of vanilla's sprint FOV. */
    @SubscribeEvent
    public static void speedFov(ComputeFovModifierEvent event) {
        if (event.getPlayer().getVehicle() instanceof PowerArmorEntity suit) {
            float speed01 = (float) Mth.clamp(suit.getVisualSpeed() / 10.0, 0.0, 1.0);
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F + 0.08F * speed01));
        }
    }

    private PilotView() {}
}
