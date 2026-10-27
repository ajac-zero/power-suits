package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.TntShell;
import dev.ajaczero.powerarmor.entity.Trajectory;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.item.TntLauncherItem;
import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The TNT launcher on the visor (2026-10-27): a lobbed round is hard to aim by the crosshair alone, so the visor shows
 * where it will go.
 * <ul>
 *   <li>The arc: a dotted line along the round's flight, from the launcher's muzzle down to a diamond where it goes off,
 *   with the distance to it. It's the round as it's drawn: the real flight leaves from the eye along the crosshair
 *   ({@link TntShell#predict}, from {@link TntLauncherItem#launch}: the same numbers the server fires with) and every
 *   client draws the round coming out of the muzzle and joining that path within a few blocks ({@link TntShell#shift});
 *   the arc does the same (user, 2026-10-27: from the gun, not the crosshair), starting just clear of the launcher's
 *   model. Worked out every frame, so it moves with
 *   the aim. Where the blast would reach the suit (an explosion's reach is twice its strength) the arc and the diamond
 *   turn red. A round that would hit nothing goes off in the air when its fuse runs out: a small x there.</li>
 *   <li>The drum, in the top right corner like the chaingun's ammo (user, 2026-10-27): a revolver drum seen from behind,
 *   one chamber per round loaded (filled, from the top clockwise), the next empty one filling up while it's being
 *   loaded; AMMO and the rounds in reserve (the ammo slot) on the big counter beside it, LOAD under them while it
 *   loads. Red on the last round.</li>
 * </ul>
 */
public final class TntLauncherHud implements ToolHud {
    /** Arc dots this close to the crosshair's middle aren't drawn (they'd crowd it). */
    private static final int CLEAR = 7;
    /** The arc starts this far (blocks) out of the muzzle, clear of the launcher's own model (user, 2026-10-27: the line
     *  ran over the barrel in the cockpit view). */
    private static final double CLEAR_OF_GUN = 0.3;
    /** The landing diamond's half-size. */
    private static final int MARK = 4;
    /** The drum: chamber size, the chambers' distance from the middle, the hub's size, and its gap left of the corner
     *  block (its middle on the block's middle). */
    private static final int CHAMBER = 7, SPREAD = 9, HUB = 5, DRUM_GAP = 8;

    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        HudReadout r = v.readout;
        if (r == null) {
            return "";
        }
        String arc = arc(g, v);
        boolean last = r.lowAmmo();
        v.ammoRow(g, r.count(), last);
        int loaded = TntLauncherItem.loaded(v.suit.getSocketStack());
        if (r.bar() > 0F) {
            v.cornerLabel(g, Component.translatable("hud.powerarmor.load"), HudView.CORNER_Y0 + 18, v.faint(last));
        }
        drum(g, v, v.cornerLeft() - DRUM_GAP - (2 * SPREAD + CHAMBER) / 2, HudView.CORNER_Y0 + 16, loaded, r.bar(), last);
        return String.format(Locale.ROOT, "drum=%d reserve=%d loading=%.2f %s", loaded, r.count(), r.bar(), arc);
    }

    /** The arc and where it ends; returns that for the log. */
    private static String arc(GuiGraphicsExtractor g, HudView v) {
        TntLauncherItem.Launch launch = TntLauncherItem.launch(v.suit, v.pilot, v.partial);
        Trajectory flight = TntShell.predict(v.suit.level(), v.pilot, launch.from(), launch.velocity());
        // the round as drawn: out of the muzzle, onto the real path within a few blocks
        Vec3 gap = SocketAim.visualMuzzle.apply(v.suit, v.pilot).subtract(launch.from());
        List<Vec3> drawn = new ArrayList<>(flight.path().size() + 1);
        for (Vec3 p : flight.path()) {
            drawn.add(p.add(TntShell.shift(p, launch.from(), gap)));
        }
        Vec3 end = flight.end();
        drawn.add(end.add(TntShell.shift(end, launch.from(), gap)));
        drawn = from(drawn, CLEAR_OF_GUN);
        // inside the blast: vanilla's explosion reaches twice its strength, measured to the suit's feet
        double blast = 2 * PowerArmorConfig.TNT_LAUNCHER_POWER.get();
        boolean danger = end.distanceTo(v.suit.position()) < blast;
        int dots = v.dottedLine(g, drawn, v.faint(danger), CLEAR);
        double distance = end.distanceTo(launch.from());
        float[] p = v.project(end);
        if (p != null) {
            int x = Math.round(p[0]), y = Math.round(p[1]);
            if (flight.hit()) {
                HudView.diamond(g, x, y, MARK, v.main(danger));
                g.text(v.font, Component.translatable("hud.powerarmor.metres", Math.round(distance)), x + MARK + 3, y - 3, v.main(danger), false);
            } else {
                // goes off in the air (the fuse): a small x
                int c = v.faint(danger);
                for (int k = -2; k <= 2; k++) {
                    g.fill(x + k, y + k, x + k + 1, y + k + 1, c);
                    g.fill(x + k, y - k, x + k + 1, y - k + 1, c);
                }
            }
        }
        return String.format(Locale.ROOT, "arc=%d %s=%.1f ticks=%d%s%s", dots, flight.hit() ? "impact" : "airburst", distance,
                flight.ticks(), danger ? " danger" : "",
                flight.entity() != null ? " on=" + BuiltInRegistries.ENTITY_TYPE.getKey(flight.entity().getType()).getPath() : "");
    }

    /** The line from {@code distance} blocks along it on (a point cut into the segment it falls in). */
    private static List<Vec3> from(List<Vec3> line, double distance) {
        double run = 0;
        for (int i = 1; i < line.size(); i++) {
            Vec3 a = line.get(i - 1), b = line.get(i);
            double step = a.distanceTo(b);
            if (run + step > distance) {
                List<Vec3> out = new ArrayList<>(line.size() - i + 1);
                out.add(a.lerp(b, (distance - run) / step));
                out.addAll(line.subList(i, line.size()));
                return out;
            }
            run += step;
        }
        return List.of(); // the whole flight is shorter than that: nothing to draw
    }

    /**
     * The drum seen from behind, centred on (x, y): a hub and four chambers round it, one per round. The loaded ones are
     * filled, from the top (under the barrel: the next to fire) clockwise; the first empty one fills from the bottom while
     * it's being loaded ({@code loading} 0..1); the rest are rings.
     */
    private static void drum(GuiGraphicsExtractor g, HudView v, int x, int y, int rounds, float loading, boolean warn) {
        int lit = v.main(warn), faint = v.faint(warn);
        g.fill(x - HUB / 2, y - HUB / 2, x - HUB / 2 + HUB, y - HUB / 2 + HUB, faint);
        int[][] chambers = {{0, -SPREAD}, {SPREAD, 0}, {0, SPREAD}, {-SPREAD, 0}};
        for (int i = 0; i < chambers.length; i++) {
            int x0 = x + chambers[i][0] - CHAMBER / 2, y0 = y + chambers[i][1] - CHAMBER / 2;
            boolean loaded = i < rounds;
            if (i == rounds && loading > 0F) {
                g.fill(x0, y0, x0 + CHAMBER, y0 + CHAMBER, v.dim(warn));
                int h = Math.round(CHAMBER * Math.min(1F, loading));
                if (h > 0) {
                    g.fill(x0, y0 + CHAMBER - h, x0 + CHAMBER, y0 + CHAMBER, lit);
                }
            } else if (loaded) {
                g.fill(x0, y0, x0 + CHAMBER, y0 + CHAMBER, lit);
            } else {
                g.fill(x0, y0, x0 + CHAMBER, y0 + 1, faint);
                g.fill(x0, y0 + CHAMBER - 1, x0 + CHAMBER, y0 + CHAMBER, faint);
                g.fill(x0, y0 + 1, x0 + 1, y0 + CHAMBER - 1, faint);
                g.fill(x0 + CHAMBER - 1, y0 + 1, x0 + CHAMBER, y0 + CHAMBER - 1, faint);
            }
        }
    }
}
