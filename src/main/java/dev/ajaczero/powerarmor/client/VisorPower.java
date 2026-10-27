package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The visor's power-up and power-down, for both of its layers ({@link SuitVisor}'s metal, {@link SuitHud}'s readouts),
 * on one clock so they stay in step (2026-10-27, after the user's "do the readouts, both the opening and the closing").
 * <ul>
 *   <li>Power on (fuel is back, or you climb into a running suit): the helmet's metal fades out over 0.6 s; the readouts
 *   start as a 1 px line across the middle of the screen that widens from the centre (0.12 s), then stretch up out of
 *   it to their places at the top (0.3 s), like an old TV's picture warming up.</li>
 *   <li>Power off (out of fuel, flooded): the metal is solid at once (the picture through it dies with the power); the
 *   readouts are pulled down into the line (0.25 s), the line shrinks to the centre (0.15 s), and only then does the red
 *   NO FUEL start blinking.</li>
 * </ul>
 * Timed on the wall clock (ms), not game ticks: a tick is 50 ms, a frame far less, and a fade stepped per tick shows its
 * steps. Climbing into a suit that's already off shows it off at once (no closing to watch). Client-only state, per
 * world: entity ids start over in the next one.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class VisorPower {
    static final long METAL_MS = 600;
    static final long LINE_MS = 120, OPEN_MS = 300;
    static final long CLOSE_MS = 250, SHRINK_MS = 150;

    /** The suit being watched (its entity id, -1 none), whether it was powered and since when (wall-clock ms). */
    private static int suitId = -1;
    private static boolean powered;
    private static long since;

    /** How far the readouts are open: band = how far the picture is stretched out of the screen's middle line (0 = all
     *  in the line, 1 = in place), line = how much of the line is drawn (from the centre out), lineAlpha = its brightness. */
    public record Readouts(float band, float line, float lineAlpha) {
        static final Readouts OPEN = new Readouts(1F, 0F, 0F);
        static final Readouts CLOSED = new Readouts(0F, 0F, 0F);

        /** Nothing on the glass any more: the powered-down HUD (the blinking NO FUEL) takes over. */
        public boolean closed() {
            return this.band <= 0F && this.lineAlpha <= 0F;
        }
    }

    /** Follow the suit the player pilots; call every frame before asking (both layers do: the second call is a no-op). */
    static void watch(PowerArmorEntity suit) {
        boolean now = suit.isPowered();
        long t = Util.getMillis();
        if (suit.getId() != suitId) {
            suitId = suit.getId();
            powered = now;
            // a running suit warms up when you climb in; a dead one is simply dark (its closing is long over)
            since = now ? t : t - CLOSE_MS - SHRINK_MS;
        } else if (now != powered) {
            powered = now;
            since = t;
        }
    }

    /** Out of the suit (or no helmet): the next suit starts fresh. */
    static void forget() {
        suitId = -1;
    }

    private static long elapsed() {
        return Util.getMillis() - since;
    }

    /** How solid the helmet's metal is: 1 without power, fading to 0 over the warm-up. */
    static float metal() {
        if (!powered) {
            return 1F;
        }
        return 1F - Mth.smoothstep(Mth.clamp(elapsed() / (float) METAL_MS, 0F, 1F));
    }

    static Readouts readouts() {
        long t = elapsed();
        if (powered) {
            if (t < LINE_MS) {
                return new Readouts(0F, Mth.smoothstep(t / (float) LINE_MS), 1F);
            }
            if (t < LINE_MS + OPEN_MS) {
                float band = Mth.smoothstep((t - LINE_MS) / (float) OPEN_MS);
                return new Readouts(band, 1F, 1F - band); // the line fades as the picture opens over it
            }
            return Readouts.OPEN;
        }
        if (t < CLOSE_MS) {
            float band = 1F - Mth.smoothstep(t / (float) CLOSE_MS);
            return new Readouts(band, 1F, 1F - band); // the line brightens as the picture squeezes into it
        }
        if (t < CLOSE_MS + SHRINK_MS) {
            return new Readouts(0F, 1F - Mth.smoothstep((t - CLOSE_MS) / (float) SHRINK_MS), 1F);
        }
        return Readouts.CLOSED;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    private VisorPower() {}
}
