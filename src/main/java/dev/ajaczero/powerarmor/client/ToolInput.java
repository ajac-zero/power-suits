package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.network.ToolTriggerPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * While piloting, the attack button (left click) belongs to the socket tool: vanilla's punch / mine is
 * cancelled, and pressing or releasing the button is sent to the server.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class ToolInput {
    private static boolean sentDown;

    /** No punching or block breaking (or arm swing) from inside the suit. */
    @SubscribeEvent
    public static void cancelVanillaAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Send a message only when the button state changes (not every tick). */
    @SubscribeEvent
    public static void trigger(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean piloting = mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity;
        boolean down = piloting && mc.screen == null && mc.options.keyAttack.isDown();
        if (down != sentDown && mc.getConnection() != null) {
            sentDown = down;
            PacketDistributor.sendToServer(new ToolTriggerPayload(down));
        }
    }

    private ToolInput() {}
}
