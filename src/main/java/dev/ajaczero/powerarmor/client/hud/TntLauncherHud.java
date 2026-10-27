package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.entity.TntShell;
import dev.ajaczero.powerarmor.item.TntLauncherItem;
import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The TNT launcher on the visor (2026-10-27): a lobbed round is hard to aim by the crosshair alone, so the visor shows
 * where it will go.
 * <ul>
 *   <li>The arc: a dotted line along the round's flight from the crosshair down to where it lands, worked out every
 *   frame with the round's own physics ({@link TntShell#predict}, from {@link TntLauncherItem#launch}: the same numbers
 *   the server fires with), and a diamond where it goes off with the distance to it. A round that would hit nothing goes
 *   off in the air when its fuse runs out: a small x there.</li>
 *   <li>The drum (user's pick A): a revolver drum seen from behind, right of the crosshair, its top chamber (the one
 *   under the barrel) filling while it reloads and lit when a round is ready; the rounds left beside it (x07), LOAD
 *   under them while it reloads. Red on the last round.</li>
 * </ul>
 */
public final class TntLauncherHud implements ToolHud {
    /** The drum's middle, right of the crosshair's, and the count beside it. */
    private static final int DRUM_X = 24, COUNT_X = 34;
    /** Arc dots this close to the crosshair's middle aren't drawn (they'd sit on it: the round leaves along the view). */
    private static final int CLEAR = 9;
    /** The landing diamond's half-size. */
    private static final int MARK = 4;

    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        HudReadout r = v.readout;
        if (r == null) {
            return "";
        }
        String arc = arc(g, v);
        int cx = v.crosshairX(), cy = v.crosshairY();
        boolean last = r.lowAmmo() || r.count() == 0;
        drum(g, v, cx + DRUM_X, cy, r.bar(), last);
        Component count = r.count() == HudReadout.ENDLESS ? Component.literal("x").append(Component.translatable("hud.powerarmor.endless"))
                : Component.literal(String.format(Locale.ROOT, "x%02d", Math.max(r.count(), 0)));
        g.text(v.font, count, cx + COUNT_X, cy - 3, v.main(last), false);
        if (r.bar() < 1F) {
            g.text(v.font, Component.translatable("hud.powerarmor.load"), cx + COUNT_X, cy + 6, v.faint(last), false);
        }
        return String.format(Locale.ROOT, "rounds=%d reload=%.2f %s", r.count(), r.bar(), arc);
    }

    /** The arc and where it ends; returns that for the log. */
    private static String arc(GuiGraphicsExtractor g, HudView v) {
        TntLauncherItem.Launch launch = TntLauncherItem.launch(v.suit, v.pilot, v.partial);
        TntShell.Flight flight = TntShell.predict(v.suit.level(), v.pilot, launch.from(), launch.velocity());
        int cx = v.crosshairX(), cy = v.crosshairY();
        int dot = v.faint(false), dots = 0;
        for (int i = 1; i < flight.path().size(); i++) {
            float[] p = v.project(flight.path().get(i));
            if (p == null) {
                continue;
            }
            int x = Math.round(p[0]), y = Math.round(p[1]);
            if (Math.abs(x - cx) <= CLEAR && Math.abs(y - cy) <= CLEAR) {
                continue;
            }
            g.fill(x, y, x + 1, y + 1, dot);
            dots++;
        }
        Vec3 end = flight.end();
        double distance = end.distanceTo(launch.from());
        float[] p = v.project(end);
        if (p != null) {
            int x = Math.round(p[0]), y = Math.round(p[1]);
            if (flight.hit()) {
                diamond(g, x, y, MARK, v.main(false));
                g.text(v.font, Component.translatable("hud.powerarmor.metres", Math.round(distance)), x + MARK + 3, y - 3, v.main(false), false);
            } else {
                // goes off in the air (the fuse): a small x
                int c = v.faint(false);
                for (int k = -2; k <= 2; k++) {
                    g.fill(x + k, y + k, x + k + 1, y + k + 1, c);
                    g.fill(x + k, y - k, x + k + 1, y - k + 1, c);
                }
            }
        }
        return String.format(Locale.ROOT, "arc=%d %s=%.1f ticks=%d%s", dots, flight.hit() ? "impact" : "airburst", distance,
                flight.ticks(), flight.entity() != null ? " on=" + BuiltInRegistries.ENTITY_TYPE.getKey(flight.entity().getType()).getPath() : "");
    }

    /** A diamond outline of half-size {@code r} round (x, y). */
    private static void diamond(GuiGraphicsExtractor g, int x, int y, int r, int colour) {
        for (int k = 0; k <= r; k++) {
            g.fill(x - r + k, y - k, x - r + k + 1, y - k + 1, colour);
            g.fill(x - r + k, y + k, x - r + k + 1, y + k + 1, colour);
            g.fill(x + r - k, y - k, x + r - k + 1, y - k + 1, colour);
            g.fill(x + r - k, y + k, x + r - k + 1, y + k + 1, colour);
        }
    }

    /**
     * The drum seen from behind: a hub and four 5x5 chambers round it. The top one is under the barrel: it fills from
     * the bottom while the launcher reloads ({@code reload} 0..1) and is lit when a round is ready; the other three are
     * rings (the drum's look: the rounds themselves are counted beside it).
     */
    private static void drum(GuiGraphicsExtractor g, HudView v, int x, int y, float reload, boolean warn) {
        int faint = v.faint(warn), lit = v.main(warn);
        g.fill(x - 1, y - 1, x + 2, y + 2, faint);
        int[][] chambers = {{0, -6}, {6, 0}, {0, 6}, {-6, 0}};
        for (int i = 0; i < chambers.length; i++) {
            int x0 = x + chambers[i][0] - 2, y0 = y + chambers[i][1] - 2;
            if (i == 0) {
                g.fill(x0, y0, x0 + 5, y0 + 5, v.dim(warn));
                int h = Math.round(5 * Math.min(1F, Math.max(0F, reload)));
                if (h > 0) {
                    g.fill(x0, y0 + 5 - h, x0 + 5, y0 + 5, lit);
                }
            } else {
                g.fill(x0, y0, x0 + 5, y0 + 1, faint);
                g.fill(x0, y0 + 4, x0 + 5, y0 + 5, faint);
                g.fill(x0, y0 + 1, x0 + 1, y0 + 4, faint);
                g.fill(x0 + 4, y0 + 1, x0 + 5, y0 + 4, faint);
            }
        }
    }
}
