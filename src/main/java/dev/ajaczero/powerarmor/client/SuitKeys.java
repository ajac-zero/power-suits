package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.network.PowerSwitchPayload;
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
 *   in its menu. Unbound by default: every key is taken by something already, the player picks one.</li>
 * </ul>
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class SuitKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(PowerArmor.MODID, "power_armor"));
    public static final KeyMapping POWER_SWITCH = new KeyMapping("key.powerarmor.power_switch", InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    private SuitKeys() {
    }

    /** Mod bus, at startup: list our category and keys in the Controls screen. */
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(POWER_SWITCH);
    }

    /** Every client tick: hand each press to the server, if this player pilots a suit. */
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (POWER_SWITCH.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof PowerArmorEntity suit && suit.getControllingPassenger() == mc.player) {
                ClientPacketDistributor.sendToServer(new PowerSwitchPayload());
            }
        }
    }
}
