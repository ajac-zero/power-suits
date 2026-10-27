package dev.ajaczero.powerarmor.network;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: "the pilot pressed the power switch key" (client/SuitKeys; H by default). A key press only exists on the pilot's client and the switch is the suit's (server) state, so the client
 * sends this message with nothing in it: its arrival is the whole news. The menu's switch needs no message of its own:
 * menu buttons travel in vanilla's button packet (PowerArmorMenu.clickMenuButton).
 */
public record PowerSwitchPayload() implements CustomPacketPayload {
    public static final Type<PowerSwitchPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(PowerArmor.MODID, "power_switch"));
    public static final StreamCodec<ByteBuf, PowerSwitchPayload> STREAM_CODEC = StreamCodec.unit(new PowerSwitchPayload());

    @Override
    public Type<PowerSwitchPayload> type() {
        return TYPE;
    }

    /** Mod-bus event: declare the message ("1": the protocol version, both sides must match). */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, PowerSwitchPayload::handle);
    }

    /** Server, main thread. Never trust the client: only the suit's pilot may flip it this way. */
    private static void handle(PowerSwitchPayload payload, IPayloadContext context) {
        if (context.player().getVehicle() instanceof PowerArmorEntity suit && suit.getControllingPassenger() == context.player()) {
            suit.flipPowerSwitch();
        }
    }
}
