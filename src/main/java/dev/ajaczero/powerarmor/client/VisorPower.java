package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The visor's power-up and power-down, for both of its layers ({@link SuitVisor}'s metal, {@link SuitHud}'s readouts),
 * on one clock so they stay in step (2026-10-27). A scan from the middle of the screen (user: the effect comes from the
 * middle, and must sweep over the visor's elements, not just appear):
 * <ul>
 *   <li>Power on (fuel is back, or you climb into a running suit): a 1 px line across the middle of the screen widens
 *   from the centre (0.12 s), then splits into two lines that sweep up and down to the screen's edges (0.45 s). What
 *   lies between them is on: the readouts are there, the helmet's metal is see-through (only its lip); what they haven't
 *   reached yet is still off (no readouts, solid black metal).</li>
 *   <li>Power off (out of fuel, flooded): two lines come in from the top and bottom edges (0.35 s), switching off what
 *   they pass, meet in the middle, and the line left there shrinks to the centre (0.15 s), leaving the glass
 *   completely dark (the suit menu says why).</li>
 * </ul>
 * Timed on the wall clock (ms), not game ticks: a tick is 50 ms, a frame far less, and a sweep stepped per tick shows
 * its steps. Climbing into a suit that's already off shows it off at once (no shutdown to watch). Client-only state,
 * per world: entity ids start over in the next one.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class VisorPower {
    static final long LINE_MS = 120, SWEEP_OUT_MS = 450;
    static final long SWEEP_IN_MS = 350, SHRINK_MS = 150;

    /** The suit being watched (its entity id, -1 none), whether it was powered and since when (wall-clock ms). */
    private static int suitId = -1;
    private static boolean powered;
    private static long since;

    /**
     * Where the scan is: sweep = how much of the screen's height is on, from the middle out (0 = none, 1 = all);
     * line = how much of the middle line is drawn while nothing is on yet (from the centre out); lit = whether the scan
     * lines show at all.
     */
    public record Scan(float sweep, float line, boolean lit) {
        static final Scan ON = new Scan(1F, 0F, false);
        static final Scan OFF = new Scan(0F, 0F, false);

        /** Nothing on the glass any more: the powered-down HUD (dark, nothing drawn) takes over. */
        public boolean off() {
            return this.sweep <= 0F && !this.lit;
        }

        /** The band that's on, in GUI pixels: [top, bottom). */
        int top(int screenHeight) {
            return Math.round(screenHeight / 2F * (1F - this.sweep));
        }

        int bottom(int screenHeight) {
            return screenHeight - this.top(screenHeight);
        }

        /** The scan lines in the colour {@code rgb}: the middle line while nothing is on, else the band's two edges. */
        void drawLines(GuiGraphicsExtractor g, int rgb) {
            if (!this.lit) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight(), colour = 0xFF000000 | rgb;
            if (this.sweep <= 0F) {
                int half = Math.round(this.line * w / 2F);
                if (half > 0) {
                    g.fill(w / 2 - half, h / 2, w / 2 + half, h / 2 + 1, colour);
                }
                return;
            }
            int top = this.top(h), bottom = this.bottom(h);
            g.fill(0, top - 1, w, top, colour);
            g.fill(0, bottom, w, bottom + 1, colour);
        }
    }

    /** Follow the suit the player pilots; call every frame before asking (both layers do: the second call is a no-op). */
    static void watch(PowerArmorEntity suit) {
        boolean now = suit.isPowered();
        long t = Util.getMillis();
        if (suit.getId() != suitId) {
            suitId = suit.getId();
            powered = now;
            // a running suit warms up when you climb in; a dead one is simply dark (its shutdown is long over)
            since = now ? t : t - SWEEP_IN_MS - SHRINK_MS;
        } else if (now != powered) {
            powered = now;
            since = t;
        }
    }

    /** Out of the suit (or no helmet): the next suit starts fresh. */
    static void forget() {
        suitId = -1;
    }

    static Scan scan() {
        long t = Util.getMillis() - since;
        if (powered) {
            if (t < LINE_MS) {
                return new Scan(0F, Mth.smoothstep(t / (float) LINE_MS), true);
            }
            if (t < LINE_MS + SWEEP_OUT_MS) {
                return new Scan(Mth.smoothstep((t - LINE_MS) / (float) SWEEP_OUT_MS), 1F, true);
            }
            return Scan.ON;
        }
        if (t < SWEEP_IN_MS) {
            return new Scan(1F - Mth.smoothstep(t / (float) SWEEP_IN_MS), 1F, true);
        }
        if (t < SWEEP_IN_MS + SHRINK_MS) {
            return new Scan(0F, 1F - Mth.smoothstep((t - SWEEP_IN_MS) / (float) SHRINK_MS), true);
        }
        return Scan.OFF;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    private VisorPower() {}
}
