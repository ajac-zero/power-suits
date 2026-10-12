package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        // Things the suit can't stop: /kill, the void, hunger, drowning.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)) {
            return;
        }
        event.setCanceled(true);
        // Explosions already hit the suit by themselves, don't count them twice.
        if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
            suit.hurt(source, event.getAmount());
        }
    }

    private PilotProtection() {}
}
