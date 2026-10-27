package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.HudAlert;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client: how the tool in one suit's socket looks while it works: its moving parts (the chaingun's barrels, the TNT
 * launcher's drum), its lights (heat, the EM projector's redstone, the pilot light, the rail cannon's core), and the
 * particles of its shots. Every client works this out by itself from what the server syncs (the suit's socket(): spin,
 * heat, shots, the EM catch, the rail beam's end); the server never hears about it.
 *
 * One object per suit and tool, made by {@link PowerTool#createEffects()} when the tool goes into the socket (the item
 * is shared by every stack, so per-suit state can't live on it), dropped when it comes out. The suit's SocketVisuals
 * owns it and calls it; the renderers read it. A tool with nothing to show uses {@link #NONE}.
 *
 * These run on the client only, but in shared code: only things that exist on both sides (particles, local sounds,
 * SocketAim.visualMuzzle), never the client's classes (see entity/ClientView).
 */
public interface ToolEffects {
    /** Nothing to show (the drill and chainsaw move by their run animations). */
    ToolEffects NONE = new ToolEffects() {};

    /** Client, every tick while this tool is in the socket. */
    default void tick(PowerArmorEntity suit) {
    }

    /** Client: the suit fired a shot this client hasn't seen yet (its synced shot counter moved). */
    default void shot(PowerArmorEntity suit) {
    }

    /** Client: the angle (degrees) of the tool's turning part this frame, for ToolRotors. */
    default float rotorAngle(float partial) {
        return 0F;
    }

    /** Client: how hard the tool shakes, 0..1 (the rail cannon fighting its charge, ToolRotors.railShake). */
    default float shake(PowerArmorEntity suit) {
        return 0F;
    }

    /** Client: this frame's brightness (0..1) of the tool's lights: write {@code out[light.ordinal()]} for each of the
     *  tool's own {@link ToolLight}s (the array arrives all zero: off). {@code time} is in ticks with the partial tick. */
    default void lights(PowerArmorEntity suit, float time, float partial, float[] out) {
    }

    /**
     * The numbers the pilot's HUD shows for this tool, worked out by the tool (client, every frame): a count (its ammo;
     * {@link #NO_COUNT} for none, {@link #ENDLESS} in creative) and whether it's down to the last ({@code lowAmmo}),
     * and one gauge 0..1 (heat, charge or reload), how urgent it is ({@link HudAlert}: orange from half, red in the
     * last 15%) and whether the tool is locked out by it ({@code barLocked}: an overheated chaingun, red until cooled).
     * How they're drawn is each tool's own visor HUD (client/hud/ToolHuds, 2026-10-27: no shared tool panel any more); a
     * warning colours only that readout, not the whole HUD. Here, in shared code, stays what the numbers are (the client's drawing classes
     * can't be named in shared code: a dedicated server would crash loading them).
     */
    record HudReadout(int count, boolean lowAmmo, float bar, HudAlert barAlert, boolean barLocked) {
        public static final int NO_COUNT = Integer.MIN_VALUE;
        public static final int ENDLESS = -1;

        public boolean hasCount() {
            return this.count != NO_COUNT;
        }
    }

    /** Client: this tool's numbers on its pilot's HUD this frame (the pilot is this client's own player), or null for a
     *  tool with none (the drill, the chainsaw, the flamethrower; the EM projector's HUD reads the socket itself). */
    @Nullable
    default HudReadout hud(PowerArmorEntity suit, Player pilot) {
        return null;
    }

    // ---- Helpers the tools share ---------------------------------------------------------------------------------

    /** A particle sink: (type, x, y, z, count, spread, speed). The server sends particles to players, a client spawns
     *  them itself: one muzzle burst method serves both. */
    interface Particles {
        void add(ParticleOptions type, double x, double y, double z, int count, double spread, double speed);
    }

    /** Where this client's own pilot sees the muzzle, and whether they look out of the cockpit (then it's the
     *  viewmodel's muzzle, under a block from the camera). */
    record LocalMuzzle(Vec3 at, boolean firstPerson) {}

    /**
     * Client: the muzzle as this suit's pilot sees it, if the pilot is the player of this client (null otherwise: the
     * server sends everyone else the burst). In first person the point is moved 1.4 blocks out along the view: particles
     * right in front of the camera turn into big blobs.
     */
    @Nullable
    static LocalMuzzle localMuzzle(PowerArmorEntity suit) {
        if (!(suit.getControllingPassenger() instanceof Player pilot) || !pilot.isLocalPlayer()) {
            return null;
        }
        Vec3 muzzle = SocketAim.visualMuzzle.apply(suit, pilot);
        boolean firstPerson = muzzle.distanceTo(pilot.getEyePosition()) < 1.4;
        if (firstPerson) {
            muzzle = muzzle.add(pilot.getViewVector(1F).scale(1.4));
        }
        return new LocalMuzzle(muzzle, firstPerson);
    }

    /** Client: a particle sink that spawns into the suit's level, spread and speed as gaussians. */
    static Particles clientParticles(PowerArmorEntity suit) {
        var random = suit.getRandom();
        return (type, x, y, z, n, spread, speed) -> {
            for (int i = 0; i < n; i++) {
                suit.level().addParticle(type, x + random.nextGaussian() * spread, y + random.nextGaussian() * spread,
                        z + random.nextGaussian() * spread, random.nextGaussian() * speed,
                        random.nextGaussian() * speed, random.nextGaussian() * speed);
            }
        };
    }

    /** Eases {@code value} toward {@code target}: by {@code up} of the gap per tick when rising, {@code down} when falling. */
    static float ease(float value, float target, float up, float down) {
        return value + (target - value) * (target > value ? up : down);
    }
}
