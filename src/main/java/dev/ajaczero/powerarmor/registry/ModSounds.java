package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our own sound events. A sound event is just a name ("powerarmor:suit.engine"); which audio it plays, how
 * high-pitched and with what subtitle is set in assets/powerarmor/sounds.json. Ours currently point at vanilla
 * sounds (re-pitched), so swapping in real recordings later only means adding .ogg files and editing that
 * JSON, no code. Resource packs can override them the same way.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PowerArmor.MODID);

    /** Engine loop while piloted and powered; pitch and volume follow the load (client: SuitSounds). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE = register("suit.engine");
    /** Tool loops while the tool runs. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL = register("tool.drill");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINSAW = register("tool.chainsaw");
    /** The chaingun's motor whine while its barrels spin; the pitch follows the spin (ChaingunItem.runSoundPitch). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINGUN = register("tool.chaingun");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, name)));
    }

    private ModSounds() {}
}
