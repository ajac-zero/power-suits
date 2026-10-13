package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.SocketAim;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Client: the EM beam, a twisting violet ribbon of particles from the projector's tip to what it holds. */
final class EmBeamEffects {
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(0.72F, 0.42F, 1.0F), 0.7F);

    static void tick(PowerArmorEntity suit) {
        Entity target = suit.getBeamTarget();
        if (target == null || !(suit.getControllingPassenger() instanceof Player pilot)) {
            return;
        }
        RandomSource random = suit.getRandom();
        Vec3 from = SocketAim.visualMuzzle.apply(suit, pilot);
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length < 0.1) {
            return;
        }
        Vec3 dir = line.scale(1 / length);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir);
        int steps = (int) (length * 3);
        // Dust particles linger ~1-2 s; any that end up next to the pilot's eye (view swinging) look like huge
        // blobs on the cockpit glass, so none are spawned that close.
        Vec3 eye = pilot.getEyePosition();
        float spin = suit.tickCount * 0.6F;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            // Two strands twisting around the line, thinner at both ends.
            double radius = 0.18 * Math.sin(Math.PI * t);
            for (int strand = 0; strand < 2; strand++) {
                double a = spin + t * 9 + strand * Math.PI;
                Vec3 p = from.add(line.scale(t)).add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
                if (p.distanceToSqr(eye) > 1.3 * 1.3) {
                    suit.level().addParticle(BEAM, p.x, p.y, p.z, 0, 0, 0);
                }
            }
        }
        // The held thing crackles.
        if (random.nextInt(2) == 0) {
            suit.level().addParticle(ParticleTypes.REVERSE_PORTAL, to.x + random.nextGaussian() * 0.3, to.y + random.nextGaussian() * 0.3,
                    to.z + random.nextGaussian() * 0.3, 0, 0, 0);
        }
        if (random.nextInt(4) == 0) {
            suit.level().addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z,
                    Mth.nextDouble(random, -0.1, 0.1), Mth.nextDouble(random, -0.1, 0.1), Mth.nextDouble(random, -0.1, 0.1));
        }
    }

    private EmBeamEffects() {}
}
