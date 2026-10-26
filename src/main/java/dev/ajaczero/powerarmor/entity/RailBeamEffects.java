package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.SocketAim;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client: the rail cannon's shot, drawn by every client when the suit's synced shot counter moves (the server only sends
 * where the beam ended, PowerArmorEntity.setRailBeamEnd): a straight bar of blue-white light from the muzzle this client
 * sees (the cockpit viewmodel's for its own pilot in first person, SocketAim.visualMuzzle) to the end, drawn as geometry
 * for a few ticks while it fades ({@link #BEAMS}, client/RailBeamRenderer: particles alone read as a dotted streak near
 * the gun, runAutopilotRail), with blue dust hanging along it a moment longer, sparks, and a burst where it stopped.
 * The server fired from the eye along the crosshair; from the muzzle the line is a hair off that, which nobody can see
 * over 64 blocks (the chaingun's bullets join their real path the same way).
 */
public final class RailBeamEffects {
    /** The core: small and near white (dust lives in proportion to its size: it fades first). */
    private static final DustParticleOptions CORE = new DustParticleOptions(0xD8FCFF, 0.55F);
    /** The glow round it: the core glass's blue, bigger, so it lingers. */
    private static final DustParticleOptions GLOW = new DustParticleOptions(0x2F9CFF, 1.1F);
    /** Dust next to the cockpit camera turns into big blobs on the glass: nothing that close (the EM beam's rule). */
    private static final double KEEP_CLEAR = 1.3;

    /** A beam being drawn: from, to, and the game tick it was fired (client/RailBeamRenderer fades and drops it). */
    public record Beam(Vec3 from, Vec3 to, long firedAt) {}

    /** Beams still on screen (client thread only). */
    public static final java.util.List<Beam> BEAMS = new java.util.ArrayList<>();
    /** How long a beam is drawn, ticks. */
    public static final int BEAM_TICKS = 8;

    static void shot(PowerArmorEntity suit, LivingEntity operator, Vec3 end) {
        Level level = suit.level();
        RandomSource random = suit.getRandom();
        Vec3 from = SocketAim.visualMuzzle.apply(suit, operator);
        Vec3 line = end.subtract(from);
        double length = line.length();
        if (length < 0.1) {
            return;
        }
        Vec3 dir = line.scale(1 / length);
        Vec3 eye = operator.getEyePosition();
        BEAMS.removeIf(b -> b.firedAt() < level.getGameTime() - BEAM_TICKS);
        BEAMS.add(new Beam(from, end, level.getGameTime()));
        for (double d = 0; d <= length; d += 0.35) {
            Vec3 p = from.add(dir.scale(d));
            if (p.distanceToSqr(eye) < KEEP_CLEAR * KEEP_CLEAR) {
                continue;
            }
            if (random.nextInt(3) == 0) { // a little dust that hangs where the beam was after the bar has faded
                level.addParticle(random.nextBoolean() ? CORE : GLOW, p.x + random.nextGaussian() * 0.05, p.y + random.nextGaussian() * 0.05,
                        p.z + random.nextGaussian() * 0.05, 0, 0, 0);
            }
            if (random.nextInt(8) == 0) {
                // vanilla's lightning-rod spark: the charge crackling off the beam
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
                        random.nextGaussian() * 0.08, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08);
            }
        }
        // Where it stopped: a spray of sparks and a few glowing motes drifting off.
        for (int i = 0; i < 12; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                    random.nextGaussian() * 0.25 - dir.x * 0.2, random.nextGaussian() * 0.25 - dir.y * 0.2, random.nextGaussian() * 0.25 - dir.z * 0.2);
        }
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.END_ROD, end.x, end.y, end.z,
                    random.nextGaussian() * 0.05, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05);
        }
        // (no firework flash at the muzzle: a block-sized blue square over the suit from behind, runAutopilotRail)
    }

    private RailBeamEffects() {}
}
