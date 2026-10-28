package dev.ajaczero.powersuits.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.network.PowerSwitchPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The suit's own keys (Options > Controls > Key Binds, under "Power Armor"). A key binding is client-only: the game
 * remembers the key a player picked in options.txt and counts presses; what a press does on the world is the server's
 * business, so we send it a message (PowerSwitchPayload).
 * <ul>
 *   <li><b>Power switch</b> (user, 2026-10-27): flips the suit's power on and off while you pilot it, like the switch
 *   in its menu. H by default (user, 2026-10-27): one of the letters vanilla 26.3 leaves free (P is social
 *   interactions, O the friends screen; H, I, J, K, M, R, U, Y, Z are unbound).</li>
 *   <li><b>Night vision</b> (user, 2026-10-27): the visor's night vision on or off (VisorVision), N by default (free
 *   in 26.3 too). Purely the client's business: no message, the choice is saved in this player's own
 *   power_suits-client.toml. Only in a suit with a helmet (the visor does it), so N does nothing on foot.</li>
 * </ul>
 */
@EventBusSubscriber(modid = PowerSuits.MODID, value = Dist.CLIENT)
public final class SuitKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(PowerSuits.MODID, "power_suit"));
    public static final KeyMapping POWER_SWITCH = new KeyMapping("key.power_suits.power_switch", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping NIGHT_VISION = new KeyMapping("key.power_suits.night_vision", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N, CATEGORY);

    private SuitKeys() {
    }

    /** Mod bus, at startup: list our category and keys in the Controls screen. */
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(POWER_SWITCH);
        event.register(NIGHT_VISION);
    }

    /** Every client tick: hand each power switch press to the server, if this player pilots a suit; flip the night
     *  vision here. */
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (POWER_SWITCH.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof PowerSuitEntity suit && suit.getControllingPassenger() == mc.player) {
                ClientPacketDistributor.sendToServer(new PowerSwitchPayload());
            }
        }
        while (NIGHT_VISION.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof PowerSuitEntity suit && suit.getControllingPassenger() == mc.player
                    && suit.hasHelmet()) {
                VisorVision.toggle();
            }
        }
    }
}
