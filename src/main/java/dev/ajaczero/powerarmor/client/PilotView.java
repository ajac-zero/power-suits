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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Client-side tweaks while someone is inside a suit. */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class PilotView {
    /** Set on the render state of anyone sitting in a suit (ClientSetup registers PilotView::markPilot). */
    static final net.minecraft.util.context.ContextKey<Boolean> IN_SUIT =
            new net.minecraft.util.context.ContextKey<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(PowerArmor.MODID, "in_suit"));

    /** Extract: note whether this mob/player sits in a suit. The render event below only sees the render state, not the
     *  entity (Minecraft 1.21.2+ draws from snapshots), so the fact travels in the state. */
    static void markPilot(net.minecraft.world.entity.LivingEntity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        state.setRenderData(IN_SUIT, entity.getVehicle() instanceof PowerArmorEntity);
    }

    /** Don't draw players who are inside a suit (they'd poke through the armor). */
    @SubscribeEvent
    public static void hidePilot(RenderLivingEvent.Pre<?, ?, ?> event) {
        if (Boolean.TRUE.equals(event.getRenderState().getRenderData(IN_SUIT))) {
            event.setCanceled(true);
        }
    }

    /**
     * Vanilla paints the screen with a block's texture when your head is inside it, and with flames while you burn. The
     * pilot's head sits forward in the visor and grazes a wall the suit leans against (the suit itself isn't in it), and
     * a powered suit takes the pilot's fire on its own hull (SuitSeal): neither should cover the cockpit view. A
     * knocked-out or empty suit shows the flames again: no power, no seal.
     */
    @SubscribeEvent
    public static void cockpitOverlays(net.neoforged.neoforge.client.event.ExtractBlockScreenEffectEvent event) {
        if (!(event.getPlayer().getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        switch (event.getOverlayType()) {
            case BLOCK -> event.setCanceled(!suit.isInWall());
            case FIRE -> event.setCanceled(suit.isSealed()); // no helmet, no seal: the flames reach you
            default -> {}
        }
    }

    private static boolean toolDrawn;

    /**
     * The hand pass while piloting: the socket tool takes the off hand's place (vanilla's off-hand item isn't drawn), and
     * the main hand stays vanilla's: a held item is left to vanilla, an empty hand becomes the suit's gauntlet
     * (CockpitHands). The tool is drawn with whichever hand vanilla renders first this frame (with a bow or crossbow in
     * use vanilla renders only one hand).
     */
    @SubscribeEvent
    public static void cockpitHands(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof PowerArmorEntity suit)) {
            return;
        }
        boolean main = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND;
        if (main || !toolDrawn) {
            CockpitHands.renderTool(event, suit);
        }
        toolDrawn = main; // the off hand comes after the main hand in the same frame
        if (!main) {
            event.setCanceled(true);
        } else if (event.getItemStack().isEmpty()) {
            event.setCanceled(true);
            CockpitHands.renderGauntlet(event, suit);
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
            suit.animator().setClientJumpCharge(mc.options.keyJump.isDown() ? Math.max(0.01F, mc.player.getJumpRidingScale()) : 0F);
        }
    }

    // (No cockpit camera bob since 2026-10-20, user: "cinematic but it gets old very fast". It tipped the view's pitch
    // down with the hips on every footfall, up to 2.5 degrees. The viewmodel still sways with the gait, if vanilla's
    // View Bobbing is on: CockpitHands.bob.)

    /** Widen the view a little with speed, on top of vanilla's sprint FOV. */
    @SubscribeEvent
    public static void speedFov(ComputeFovModifierEvent event) {
        if (event.getPlayer().getVehicle() instanceof PowerArmorEntity suit) {
            float speed01 = (float) Mth.clamp(suit.animator().getVisualSpeed() / 10.0, 0.0, 1.0);
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F + 0.08F * speed01));
        }
    }

    private PilotView() {}
}
