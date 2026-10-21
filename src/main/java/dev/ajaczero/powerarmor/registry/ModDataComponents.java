package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.SuitLoadout;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PowerArmor.MODID);

    /** Fuel + socket tool carried by a packed-up suit item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SuitLoadout>> LOADOUT =
            COMPONENTS.registerComponentType("loadout", b -> b.persistent(SuitLoadout.CODEC).networkSynchronized(SuitLoadout.STREAM_CODEC));

    /** A packed-up suit's paint (the dye on its plates), so it comes back painted. Not vanilla's "dye" component: an
     *  item carrying that one counts as a dye itself. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> PAINT =
            COMPONENTS.registerComponentType("paint", b -> b.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC));

    private ModDataComponents() {}
}
