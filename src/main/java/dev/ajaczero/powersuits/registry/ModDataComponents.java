package dev.ajaczero.powersuits.registry;

import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.item.HelmetStyle;
import dev.ajaczero.powersuits.item.SuitLoadout;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PowerSuits.MODID);

    /** Fuel + socket tool carried by a packed-up suit item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SuitLoadout>> LOADOUT =
            COMPONENTS.registerComponentType("loadout", b -> b.persistent(SuitLoadout.CODEC).networkSynchronized(SuitLoadout.STREAM_CODEC));

    /** A packed-up suit's paint (the dye on its plates), so it comes back painted. Not vanilla's "dye" component: an
     *  item carrying that one counts as a dye itself. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> PAINT =
            COMPONENTS.registerComponentType("paint", b -> b.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC));

    /** A suit helmet's look (design sec. 4c); none = the standard helmet. Set by the smithing recipes' results. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HelmetStyle>> HELMET_STYLE =
            COMPONENTS.registerComponentType("helmet_style", b -> b.persistent(HelmetStyle.CODEC).networkSynchronized(HelmetStyle.STREAM_CODEC));

    /** Rounds loaded in a TNT launcher's drum (0..4; none = empty), so a loaded launcher keeps them when it's taken out
     *  of the socket or the suit is packed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LOADED_ROUNDS =
            COMPONENTS.registerComponentType("loaded_rounds", b -> b.persistent(ExtraCodecs.intRange(0, 64)).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
