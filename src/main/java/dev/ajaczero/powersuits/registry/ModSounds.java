package dev.ajaczero.powersuits.registry;

import dev.ajaczero.powersuits.PowerSuits;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our own sound events. A sound event is just a name ("power_suits:suit.engine"); which audio it plays, how
 * high-pitched and with what subtitle is set in assets/power_suits/sounds.json. Ours currently point at vanilla
 * sounds (re-pitched), so swapping in real recordings later only means adding .ogg files and editing that
 * JSON, no code. Resource packs can override them the same way.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PowerSuits.MODID);

    /**
     * The engines' faint, occasional sounds while powered (client: SuitSounds): no loop (user: the old minecart rumble
     * that played all the time "drove me mad"). Each engine gets a sound its backpack can explain: the firebox's fire
     * crackles, the tidal pump bubbles (only under water), the star reactor hums now and then.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> FIREBOX = register("suit.engine.firebox");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDAL = register("suit.engine.tidal");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR = register("suit.engine.reactor");
    /** Tool loops while the tool runs. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRILL = register("tool.drill");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINSAW = register("tool.chainsaw");
    /** The chaingun's motor whine while its barrels spin; the pitch follows the spin (ChaingunItem.runSoundPitch). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAINGUN = register("tool.chaingun");
    /** The flamethrower's roar while it sprays. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAMETHROWER = register("tool.flamethrower");
    /** The rail cannon's hum while it charges; the pitch rises with the charge (RailCannonItem.runSoundPitch). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL_CANNON = register("tool.rail_cannon");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(PowerSuits.MODID, name)));
    }

    private ModSounds() {}
}
