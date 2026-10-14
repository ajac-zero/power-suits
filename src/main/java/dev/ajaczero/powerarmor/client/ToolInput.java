package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.network.ToolTriggerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * While piloting, the socket tool works like a shield in the off hand: the use button (right click) goes to the
 * suit's hand first, exactly as vanilla does it (open the door, place the block, throw the potion...), and only when the
 * hand has nothing to do does the press go to the socket tool, which then runs for as long as the button is held.
 * Attack (left click) is never touched: the hand punches, mines and swings its weapon as normal.
 *
 * <p>How: vanilla's {@code startUseItem} tries the main hand, then the off hand, and NeoForge fires
 * InteractionKeyMappingTriggered before each. Reaching the off hand means the main hand passed: that's the tool's
 * turn (the suit has no off hand, so the player's real off-hand item isn't used while piloting). Pressing and
 * releasing the tool is sent to the server; it runs the tool.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class ToolInput {
    /** The current press of the use button went to the tool. */
    private static boolean toolHeld;
    private static boolean sentDown;

    @SubscribeEvent
    public static void useButton(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !(mc.player.getVehicle() instanceof PowerArmorEntity)) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            toolHeld = true;                  // the hand passed: the tool takes this press
        }
        if (toolHeld) {
            // Also swallows vanilla's repeats (every 4 ticks while held), so a drill doesn't start placing the blocks
            // in your hand when the crosshair moves onto a block face.
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Release, and send a message only when the tool's state changes (not every tick). */
    @SubscribeEvent
    public static void trigger(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean piloting = mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity;
        if (!piloting || mc.gui.screen() != null || !mc.options.keyUse.isDown()) {
            toolHeld = false;
        }
        if (toolHeld != sentDown && mc.getConnection() != null) {
            sentDown = toolHeld;
            ClientPacketDistributor.sendToServer(new ToolTriggerPayload(toolHeld));
        }
    }

    private ToolInput() {}
}
