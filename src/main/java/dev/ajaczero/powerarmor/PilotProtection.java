package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Game events (not mod setup events). @EventBusSubscriber registers every static
 * @SubscribeEvent method in this class automatically.
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class PilotProtection {
    /** Damage aimed at a pilot hits the suit instead. */
    @SubscribeEvent
    public static void onPilotHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().getVehicle() instanceof PowerArmorEntity suit) || !suit.isAlive()) {
            return;
        }
        DamageSource source = event.getSource();
        // Things the suit can't stop: /kill, the void, hunger, drowning, and effects already inside the pilot. Poison and
        // wither tick as damage with no attacker (NeoForge gives poison its own type, neoforge:poison, in the tag
        // neoforge:is_poison; vanilla used "magic"): food poisoning from before climbing in, or an effect that got
        // through while the suit had no power. The seal keeps new effects out (SuitSeal); what's in is yours.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)
                || source.is(Tags.DamageTypes.IS_POISON) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.WITHER)) {
            return;
        }
        event.setCanceled(true);
        if (source.is(DamageTypes.IN_WALL) && !suit.isInWall()) {
            // The pilot's head sits forward in the visor and pokes out of the hitbox's face: with the suit pressed against a
            // wall, vanilla thought the pilot was suffocating. Only a suit that is itself stuck in blocks suffocates.
            return;
        }
        // Explosions already hit the suit by themselves, don't count them twice.
        if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
            suit.hurt(source, event.getAmount());
        }
    }

    private PilotProtection() {}
}
