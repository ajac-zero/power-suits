package dev.ajaczero.powerarmor.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * The suit helmet's alternate looks (design sec. 4c): Easter eggs after famous armoured helmets, made at a smithing
 * table from a template found in the world, the helmet and a style object. Purely cosmetic: every style seals the
 * suit and carries its mind the same way. A helmet without the {@code powerarmor:helmet_style} component is the
 * standard one (so helmets made before the styles existed need nothing).
 *
 * Each style is a bone under the model's head (exported from the .bbmodel, drafted as proto_helm_*); the renderer
 * draws the one the worn helmet asks for instead of the standard {@code helmet} bone.
 */
public enum HelmetStyle implements StringRepresentable {
    /** After Subject Delta (BioShock 2): a riveted diving helmet, its glowing window behind three bars. */
    PROTECTOR("protector"),
    /** After the Master Chief (Halo): a band over a wide visor, a narrow mouthpiece. */
    SPARTAN("spartan"),
    /** After Emile's EVA helmet (Halo: Reach): a visor over the whole front, a steel band, red ear marks. */
    NOBLE("noble"),
    /** After Guy-Manuel's helmet (Daft Punk): one big dark visor in a gold frame, ear discs. */
    ROBOT("robot");

    /** Saved as its name ("spartan"): the item component in saves and in the recipes' JSON. */
    public static final Codec<HelmetStyle> CODEC = StringRepresentable.fromEnum(HelmetStyle::values);
    /** Sent to clients as its ordinal. */
    public static final StreamCodec<ByteBuf, HelmetStyle> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], HelmetStyle::ordinal);

    private final String name;

    HelmetStyle(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** The model bone holding this style's helmet. */
    public String bone() {
        return "helm_" + this.name;
    }

    /** The style for a synced ordinal (-1 = the standard helmet). */
    public static @Nullable HelmetStyle byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
