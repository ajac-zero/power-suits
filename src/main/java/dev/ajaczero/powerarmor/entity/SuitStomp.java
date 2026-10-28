package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Landings: dust on every heavy landing (client), and the stomp: a big drop hurts everything around (server). */
public final class SuitStomp {
    /** "power_suits:stomp" (data/power_suits/damage_type/stomp.json): "was flattened by". */
    public static final ResourceKey<DamageType> STOMP = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "stomp"));
    /** The shock wave's reach (blocks from the suit's centre): grows with the drop, up to vanilla's mace ring (3.5). */
    private static final double MIN_RADIUS = 2.0, RADIUS_PER_BLOCK = 0.15, MAX_RADIUS = 3.5;

    /** Shock wave radius for a drop of this many blocks. */
    public static double radius(float fallDistance) {
        return Math.min(MAX_RADIUS, MIN_RADIUS + RADIUS_PER_BLOCK * fallDistance);
    }

    /**
     * Server: the suit came down from {@code fallDistance} blocks. Everything alive within the radius (mobs, animals,
     * villagers, other players; only the suit and its own pilot are spared) takes damage that grows with the drop, full
     * at the centre and half at the edge, and is thrown outward. Kills are credited to the pilot. Returns the number hit.
     */
    public static int stomp(PowerArmorEntity suit, ServerLevel level, float fallDistance) {
        float damage = (float) Math.min(PowerArmorConfig.STOMP_MAX_DAMAGE.get(), PowerArmorConfig.STOMP_DAMAGE_PER_BLOCK.get() * fallDistance);
        double radius = radius(fallDistance);
        Entity pilot = suit.getControllingPassenger();
        // (direct, causing): the suit lands on you, its pilot gets the credit. No pilot: the suit itself.
        DamageSource source = suit.damageSources().source(STOMP, suit, pilot != null ? pilot : suit);
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, suit.getBoundingBox().inflate(radius, 1.0, radius))) {
            if (target == suit || suit.hasPassenger(target) || !target.isAlive()) {
                continue;
            }
            Vec3 away = target.position().subtract(suit.position()).multiply(1, 0, 1);
            double distance = Math.max(0, away.length() - target.getBbWidth() / 2);
            if (distance > radius) {
                continue;
            }
            float falloff = (float) (1.0 - 0.5 * distance / radius);
            if (target.hurtServer(level, source, damage * falloff)) {
                hit++;
            }
            // Hurt already shoves the target away from the suit a little; the shock wave also throws it up.
            Vec3 push = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize().scale(0.4 * falloff);
            double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            target.push(push.x * resist, (0.25 + 0.02 * fallDistance) * falloff * resist, push.z * resist);
            target.syncVelocity = true; // send the new velocity to players' clients
        }
        // Vanilla's mace ground smash: a dust ring 3.5 blocks out and a pillar of debris (level event 2013, every client).
        level.levelEvent(2013, suit.getOnPos(), (int) Math.min(750, 150 + 40 * fallDistance));
        level.playSound(null, suit.getX(), suit.getY(), suit.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.NEUTRAL, 1.2F, 0.7F);
        return hit;
    }

    /** Client: a heavy landing (a jump or a drop) kicks up a ring of dust from the ground under the feet, more the longer
     *  the fall. At the feet only, over 2 blocks below the cockpit camera. */
    static void landingDust(PowerArmorEntity suit, int airTicks) {
        BlockPos below = suit.getOnPos();
        BlockState ground = suit.level().getBlockState(below);
        if (ground.isAir() || suit.isInWater()) {
            return;
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        int count = Mth.clamp(airTicks * 2, 16, 60);
        var random = suit.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = 0.6 + random.nextDouble() * 0.5;
            double cx = Math.cos(angle), cz = Math.sin(angle);
            double speed = 0.12 + random.nextDouble() * 0.12;
            suit.level().addParticle(dust, suit.getX() + cx * r, suit.getY() + 0.1, suit.getZ() + cz * r,
                    cx * speed, 0.05 + random.nextDouble() * 0.1, cz * speed);
        }
        for (int i = 0; i < count / 6; i++) { // a few puffs of smoke rolling out at ankle height
            double angle = random.nextDouble() * Mth.TWO_PI;
            suit.level().addParticle(ParticleTypes.POOF, suit.getX() + Math.cos(angle) * 0.8, suit.getY() + 0.15, suit.getZ() + Math.sin(angle) * 0.8,
                    Math.cos(angle) * 0.08, 0.01, Math.sin(angle) * 0.08);
        }
    }

    private SuitStomp() {}
}
