package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.HudAlert;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client: the rail cannon's looks on one suit. Its core idles at a faint blue while it's fitted to a powered suit and
 * brightens with the charge (synced as the socket's spin); the energy runs along the core's rings toward the muzzle,
 * slowly while idle, racing as the charge builds; sparks jump between the prong tips as it charges; a shot flashes the
 * core white-hot, then it fades back.
 *
 * The shot itself is drawn by every client when the suit's synced shot counter moves (the server only sends where the
 * beam ended, SuitSocket.setRailBeamEnd): a straight bar of blue-white light from the muzzle this client
 * sees (the cockpit viewmodel's for its own pilot in first person, SocketAim.visualMuzzle) to the end, drawn as geometry
 * for a few ticks while it fades ({@link #BEAMS}, client/RailBeamRenderer: particles alone read as a dotted streak near
 * the gun, runAutopilotRail), with blue dust hanging along it a moment longer, sparks, and a burst where it stopped.
 * The server fired from the eye along the crosshair; from the muzzle the line is a hair off that, which nobody can see
 * over 64 blocks (the chaingun's bullets join their real path the same way).
 */
public final class RailCannonEffects implements ToolEffects {
    /** The core: small and near white (dust lives in proportion to its size: it fades first). */
    private static final DustParticleOptions CORE = new DustParticleOptions(0xD8FCFF, 0.55F);
    /** The glow round it: the core glass's blue, bigger, so it lingers. */
    private static final DustParticleOptions GLOW = new DustParticleOptions(0x2F9CFF, 1.1F);
    /** Dust next to the cockpit camera turns into big blobs on the glass: nothing that close (the EM beam's rule). */
    private static final double KEEP_CLEAR = 1.3;

    /** A beam being drawn: from, to, and the game tick it was fired (client/RailBeamRenderer fades and drops it). */
    public record Beam(Vec3 from, Vec3 to, long firedAt) {}

    /** Beams still on screen (client thread only). */
    public static final List<Beam> BEAMS = new ArrayList<>();
    /** How long a beam is drawn, ticks. */
    public static final int BEAM_TICKS = 8;

    /** Client: the core's glow, 0..1 (the charge, a flash after each shot), and how far the light running along it has
     *  gone (in rings; faster as it charges). */
    private float glow, flow, flowO;

    /** HUD: shots left (iron ingots in the ammo slot), low on the last one; the charge (it fires at full). */
    @Override
    public HudReadout hud(PowerArmorEntity suit, Player pilot) {
        int rounds = RailCannonItem.countRounds(suit, pilot);
        return new HudReadout(rounds, rounds >= 0 && rounds <= 1, suit.socket().getSpin(), HudAlert.NONE, false);
    }

    @Override
    public void tick(PowerArmorEntity suit) {
        float charge = suit.socket().getSpin();
        float target = !suit.isPowered() ? 0F : 0.15F + 0.85F * charge;
        this.glow = ToolEffects.ease(this.glow, target, 0.5F, 0.12F);
        this.flowO = this.flow;
        this.flow += 0.06F + 0.55F * charge;
        if (this.flow > 300F) { // keep it small (a multiple of 3 rings: the pattern repeats every 3)
            this.flow -= 300F;
            this.flowO -= 300F;
        }
        sparks(suit, charge);
    }

    @Override
    public void shot(PowerArmorEntity suit) {
        // The beam, drawn here by every client from the muzzle it sees; the core flashes white-hot.
        this.glow = 1F;
        LivingEntity operator = suit.getControllingPassenger() instanceof Player pilot ? pilot : suit;
        beam(suit, operator, suit.socket().getRailBeamEnd());
    }

    @Override
    public float shake(PowerArmorEntity suit) {
        return suit.socket().getSpin();
    }

    @Override
    public void lights(PowerArmorEntity suit, float time, float partial, float[] out) {
        float charge = suit.socket().getSpin();
        // The rings: a wave runs along the core, so each third brightens in turn after the one behind it. Dim and lazy
        // while idle, a bright fast pulse train while charging, all lit when it fires (the flash).
        float flow = Mth.lerp(partial, this.flowO, this.flow);
        float flash = Math.max(0F, this.glow - (0.15F + 0.85F * charge)); // what's left of a shot's flash: all rings lit
        for (int phase = 0; phase < 3; phase++) {
            float wave = Mth.cos((flow - phase) * Mth.TWO_PI / 3F) * 0.5F + 0.5F; // 1 when the crest is on this phase
            float crest = wave * wave * wave;                                    // a narrow crest, so it reads as moving rings
            out[ToolLight.RAIL_FLOW_0.ordinal() + phase] = Math.min(1F, this.glow * (0.3F + 0.7F * crest) + flash);
        }
        // the emitter: steady while it charges, a fast shimmer once it's full
        boolean full = charge >= 0.999F;
        out[ToolLight.RAIL_EMIT.ordinal()] = this.glow * (full ? 0.85F + 0.15F * Mth.sin(time * 2.7F + suit.getId()) : 1F);
    }

    /** The beam from the muzzle this client sees to where it ended: a bar of light (BEAMS) and particles. */
    private static void beam(PowerArmorEntity suit, LivingEntity operator, Vec3 end) {
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

    /** Client, every tick: while it charges, sparks jump between the prong tips (more and more as it fills; a steady
     *  crackle at full charge), like the gun can barely hold the charge in. Not right at the pilot's eye. */
    private static void sparks(PowerArmorEntity suit, float charge) {
        LivingEntity operator = suit.getControllingPassenger() instanceof LivingEntity p ? p : suit;
        if (charge < 0.1F || suit.getRandom().nextFloat() > 0.15F + 0.75F * charge * charge) {
            return;
        }
        RandomSource random = suit.getRandom();
        Vec3 tip = SocketAim.visualMuzzle.apply(suit, operator);
        Vec3 eye = operator.getEyePosition();
        if (tip.distanceTo(eye) < 1.4) {
            // the cockpit viewmodel's muzzle is under a block from the camera, where sparks turn into big stars on the
            // glass: crackle a little further out along the view instead (it still reads as at the muzzle)
            tip = tip.add(operator.getViewVector(1F).scale(1.4));
        }
        for (int i = 0; i < 1 + (charge >= 0.999F ? 2 : 0); i++) {
            Vec3 p = tip.add(random.nextGaussian() * 0.15, random.nextGaussian() * 0.15, random.nextGaussian() * 0.15);
            if (p.distanceToSqr(eye) > 0.9 * 0.9) {
                suit.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
                        random.nextGaussian() * 0.06, random.nextGaussian() * 0.06, random.nextGaussian() * 0.06);
            }
        }
    }
}
