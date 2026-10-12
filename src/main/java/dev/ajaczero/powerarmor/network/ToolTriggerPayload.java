package dev.ajaczero.powerarmor.network;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: "the pilot pressed / released attack". Mouse buttons only exist on the client,
 * but tools act on the world, which only the server may change. So the client sends this tiny message
 * (one boolean) and the server's suit runs the tool while it's held.
 */
public record ToolTriggerPayload(boolean down) implements CustomPacketPayload {
    public static final Type<ToolTriggerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "tool_trigger"));
    public static final StreamCodec<ByteBuf, ToolTriggerPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ToolTriggerPayload::new, ToolTriggerPayload::down);

    @Override
    public Type<ToolTriggerPayload> type() {
        return TYPE;
    }

    /** Mod-bus event: declare our messages. The "1" is a protocol version (both sides must match). */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, ToolTriggerPayload::handle);
    }

    /** Server, main thread. Never trust the client: the suit checks that this player is its pilot. */
    private static void handle(ToolTriggerPayload payload, IPayloadContext context) {
        if (context.player().getVehicle() instanceof PowerArmorEntity suit) {
            suit.setTriggerHeld(context.player(), payload.down());
        }
    }
}
