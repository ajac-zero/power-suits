package dev.ajaczero.powerarmor.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side smoke from the two exhaust pipes on the suit's back. Particles are purely visual, so
 * they are spawned on each client from what it already knows (powered, speed, fuel left); the
 * server never hears about them.
 */
final class ExhaustSmoke {
    /** Pipe tops in the model, in blocks: 3.04 up (48.6 px), 0.53 behind the centre, 0.31 to each side. */
    private static final double PIPE_UP = 3.04, PIPE_BACK = 0.53, PIPE_SIDE = 0.31;

    /** Called every client tick. Smoke only while someone is driving: a parked suit's engine is off. */
    static void tick(PowerArmorEntity suit) {
        if (!suit.isAlive() || !suit.isVehicle() || !suit.canOperate() || suit.getEngine() != SuitEngine.FIREBOX) {
            return; // only the firebox has exhausts (design sec. 4b)
        }
        RandomSource random = suit.getRandom();
        boolean sprinting = suit.getControllingPassenger() instanceof Player pilot && pilot.isSprinting() && suit.getVisualSpeed() > 0.3;
        boolean moving = suit.getVisualSpeed() > 0.3;
        int interval = sprinting ? 1 : moving ? 2 : 7;
        if (suit.tickCount % interval != 0) {
            return;
        }
        // Sprinting: a big dark puff every other tick, small ones in between (a thick but not opaque trail).
        ParticleOptions type = sprinting && suit.tickCount % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
        double lift = sprinting ? 0.12 : 0.07;
        if (suit.isLowOnFuel()) {
            // Running on fumes: the engine coughs. Long gaps, then a dark puff.
            if (random.nextFloat() < 0.55F) {
                return;
            }
            type = ParticleTypes.LARGE_SMOKE;
            lift = 0.03;
        }
        puff(suit, type, 1, lift);
    }

    /** A thick black cough when the engine starts. */
    static void startupBurst(PowerArmorEntity suit) {
        if (suit.getEngine() != SuitEngine.FIREBOX) {
            return;
        }
        puff(suit, ParticleTypes.LARGE_SMOKE, 6, 0.15);
        puff(suit, ParticleTypes.SMOKE, 6, 0.2);
    }

    private static void puff(PowerArmorEntity suit, ParticleOptions type, int perPipe, double lift) {
        Level level = suit.level();
        RandomSource random = suit.getRandom();
        // The pipes are on the torso, which turns toward the pilot's view (yHeadRot) from the legs (yBodyRot).
        float yaw = Mth.rotLerp(suit.getAimBlend(), suit.yBodyRot, suit.getYHeadRot()) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        Vec3 base = suit.position().add(0, PIPE_UP + suit.getStepOffset(1F), 0).subtract(forward.scale(PIPE_BACK));
        Vec3 motion = suit.getDeltaMovement();
        for (int pipe = -1; pipe <= 1; pipe += 2) {
            Vec3 top = base.add(side.scale(pipe * PIPE_SIDE));
            for (int i = 0; i < perPipe; i++) {
                level.addParticle(type,
                        top.x + (random.nextDouble() - 0.5) * 0.1, top.y, top.z + (random.nextDouble() - 0.5) * 0.1,
                        motion.x * 0.3 - forward.x * 0.02, lift + random.nextDouble() * 0.04, motion.z * 0.3 - forward.z * 0.02);
            }
        }
    }

    private ExhaustSmoke() {}
}
