package dev.ajaczero.powerarmor.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a packed-up suit carries: its fuel slot, its socket tool and the half-burnt fuel item.
 * Stored on the item stack as a "data component" (1.21's replacement for item NBT). A component
 * needs a Codec (how to save it to disk) and a StreamCodec (how to send it over the network).
 */
public record SuitLoadout(ItemStack fuel, ItemStack socket, int burnLeft, int burnTotal) {
    public static final SuitLoadout EMPTY = new SuitLoadout(ItemStack.EMPTY, ItemStack.EMPTY, 0, 0);

    public static final Codec<SuitLoadout> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("fuel", ItemStack.EMPTY).forGetter(SuitLoadout::fuel),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("socket", ItemStack.EMPTY).forGetter(SuitLoadout::socket),
            Codec.INT.optionalFieldOf("burn_left", 0).forGetter(SuitLoadout::burnLeft),
            Codec.INT.optionalFieldOf("burn_total", 0).forGetter(SuitLoadout::burnTotal)
    ).apply(i, SuitLoadout::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SuitLoadout> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, SuitLoadout::fuel,
            ItemStack.OPTIONAL_STREAM_CODEC, SuitLoadout::socket,
            ByteBufCodecs.VAR_INT, SuitLoadout::burnLeft,
            ByteBufCodecs.VAR_INT, SuitLoadout::burnTotal,
            SuitLoadout::new);

    // ItemStack has no equals(), but components are compared to decide whether two stacks are the same.
    @Override
    public boolean equals(Object o) {
        return o instanceof SuitLoadout other && ItemStack.matches(this.fuel, other.fuel)
                && ItemStack.matches(this.socket, other.socket) && this.burnLeft == other.burnLeft && this.burnTotal == other.burnTotal;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ItemStack.hashItemAndComponents(this.fuel) + ItemStack.hashItemAndComponents(this.socket)) + this.burnLeft;
    }
}
