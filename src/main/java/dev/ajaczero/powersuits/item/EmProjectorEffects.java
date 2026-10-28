package dev.ajaczero.powersuits.item;

import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client: the EM projector's looks on one suit. Its redstone lights: the lens and vents (core) idle at a faint glow while
 * mounted in a powered suit, brighten while the button is held (searching for something to grab) and run full while the
 * beam holds something; the prong tips only light up while it holds something (that's when they spark). Quick to light,
 * slower to fade.
 *
 * And the EM beam, a twisting ribbon of redstone dust from the projector's prongs to what it holds, red sparks
 * crackling between the prong tips and dust swirling round the held thing. The projector is a redstone electromagnet
 * (its lens and tips glow, SuitGlowLayer), so everything is redstone red: one strand vanilla's redstone red, the other a
 * hotter orange-red so the twist reads. (A dark red strand was tried first: thick dark-red dust round a pig read as blood.)
 */
final class EmProjectorEffects implements ToolEffects {
    private static final DustParticleOptions STRAND_RED = new DustParticleOptions(0xFF1A0D, 0.6F);
    private static final DustParticleOptions STRAND_HOT = new DustParticleOptions(0xFF6B38, 0.5F);
    /** Small and hot (orange-red): short-lived, because dust lives in proportion to its size. */
    private static final DustParticleOptions SPARK = new DustParticleOptions(0xFF7340, 0.35F);
    private static final DustParticleOptions SWIRL = new DustParticleOptions(0xFF331A, 0.4F);
    /** Dust particles linger ~1-2 s; any that end up next to the pilot's eye (view swinging) look like huge blobs
     *  on the cockpit glass, so none are spawned that close. The sparks are tiny and short-lived: they may come closer. */
    private static final double KEEP_CLEAR = 1.3, KEEP_CLEAR_SPARKS = 0.9;

    private float coreGlow, arcGlow;

    @Override
    public void tick(PowerSuitEntity suit) {
        boolean powered = suit.isPowered();
        boolean holding = powered && suit.socket().getBeamTarget() != null;
        float coreTarget = !powered ? 0F : holding ? 1F : suit.socket().isActive() ? 0.6F : 0.25F;
        this.coreGlow = ToolEffects.ease(this.coreGlow, coreTarget, 0.35F, 0.1F);
        this.arcGlow = ToolEffects.ease(this.arcGlow, holding ? 1F : 0F, 0.5F, 0.15F);
        this.beam(suit);
    }

    @Override
    public void lights(PowerSuitEntity suit, float time, float partial, float[] out) {
        // the core: a slow breathing pulse, and a fast uneven throb while the beam holds something
        float pulse = this.arcGlow > 0.5F ? 0.8F + 0.12F * Mth.sin(time * 1.7F) + 0.08F * Mth.sin(time * 4.3F)
                : 0.85F + 0.15F * Mth.sin(time * 0.25F);
        out[ToolLight.EM_CORE.ordinal()] = this.coreGlow * pulse;
        // the prong tips crackle (flicker at random-looking times)
        float crackle = Mth.sin(time * 5.1F) * Mth.sin(time * 2.9F + suit.getId()) > 0.5F ? 0.55F : 1F;
        out[ToolLight.EM_ARC.ordinal()] = this.arcGlow * crackle;
    }

    /** The beam's particles, from the muzzle this client sees to what it holds. */
    private void beam(PowerSuitEntity suit) {
        Entity target = suit.socket().getBeamTarget();
        LivingEntity operator = suit.toolOperator(); // the pilot, or the suit itself while its mind drives
        if (target == null || operator == null) {
            return;
        }
        RandomSource random = suit.getRandom();
        Vec3 from = SocketAim.visualMuzzle.apply(suit, operator);
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
        Vec3 eye = operator.getEyePosition();
        int steps = (int) (length * 2.5);
        float spin = suit.tickCount * 0.6F;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            // Two strands twisting around the line, thinner at both ends.
            double radius = 0.18 * Math.sin(Math.PI * t);
            for (int strand = 0; strand < 2; strand++) {
                double a = spin + t * 9 + strand * Math.PI;
                Vec3 p = from.add(line.scale(t)).add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
                if (p.distanceToSqr(eye) > KEEP_CLEAR * KEEP_CLEAR) {
                    suit.level().addParticle(strand == 0 ? STRAND_RED : STRAND_HOT, p.x, p.y, p.z, 0, 0, 0);
                }
            }
        }
        // Sparks jump between the prong tips (they sit ~0.2 blocks round the muzzle) and now and then along the beam.
        for (int i = 0; i < 2; i++) {
            Vec3 p = from.add(random.nextGaussian() * 0.12, random.nextGaussian() * 0.12, random.nextGaussian() * 0.12);
            if (p.distanceToSqr(eye) > KEEP_CLEAR_SPARKS * KEEP_CLEAR_SPARKS) {
                suit.level().addParticle(SPARK, p.x, p.y, p.z, 0, 0, 0);
            }
        }
        if (random.nextInt(3) == 0) {
            Vec3 p = from.add(line.scale(random.nextDouble()));
            if (p.distanceToSqr(eye) > KEEP_CLEAR_SPARKS * KEEP_CLEAR_SPARKS) {
                suit.level().addParticle(SPARK, p.x, p.y, p.z, 0, 0, 0);
            }
        }
        if (random.nextInt(5) == 0 && from.distanceToSqr(eye) > KEEP_CLEAR_SPARKS * KEEP_CLEAR_SPARKS) {
            // A bright crack now and then (vanilla's lightning-rod spark: the copper windings at work).
            suit.level().addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z,
                    Mth.nextDouble(random, -0.1, 0.1), Mth.nextDouble(random, -0.1, 0.1), Mth.nextDouble(random, -0.1, 0.1));
        }
        // Dust swirls round the held thing.
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.max(0.3, target.getBbWidth() * 0.7);
            suit.level().addParticle(SWIRL, to.x + Math.cos(a) * r, to.y + random.nextGaussian() * target.getBbHeight() * 0.3,
                    to.z + Math.sin(a) * r, 0, 0, 0);
        }
    }
}
