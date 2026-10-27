package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The chaingun on the visor (user's pick A, 2026-10-27): you hold the trigger and walk the stream onto the target, so
 * the view stays clear and the readout sits in the top right corner, mirroring the suit's on the left: AMMO and the
 * bullets left on Fallout's big 3-digit counter (on the HULL row), the barrel HEAT under it as a bar (on the FUEL
 * row). The counter turns red on the last bullets; the heat turns red and reads OVERHEAT while the barrels are locked.
 */
public final class ChaingunHud implements ToolHud {
    /** Where the readout sits: like the suit's on the left (SuitHud: MARGIN 4, its rows from y 26), 47 px wide (the
     *  heat bar's 12 segments), 5 px in from the margin. */
    private static final int MARGIN = 4, INSET = 5, Y0 = 26, SEGMENTS = 12;

    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        HudReadout r = v.readout;
        if (r == null) {
            return "";
        }
        int right = v.width() - MARGIN - INSET;
        int left = right - (SEGMENTS * 4 - 1);
        // AMMO and the counter on the HULL row (the label sits on the counter's baseline)
        int ammo = v.main(r.lowAmmo());
        int counterLeft = v.counter(g, r.count(), right, Y0 + 4, ammo);
        Component label = Component.translatable("hud.powerarmor.ammo");
        g.text(v.font, label, counterLeft - 4 - v.font.width(label), Y0 + 11, ammo, false);
        // the heat on the FUEL row, its bar under it like the fuel's
        boolean hot = r.barCritical();
        int heat = v.main(hot);
        g.text(v.font, Component.translatable(hot ? "hud.powerarmor.overheat" : "hud.powerarmor.heat"), left, Y0 + 20, heat, false);
        HudView.bar(g, left, Y0 + 29, SEGMENTS, 3, r.bar(), heat, v.dim(hot));
        return String.format(Locale.ROOT, "ammo=%d heat=%.2f%s%s", r.count(), r.bar(), r.lowAmmo() ? " low" : "", hot ? " hot" : "");
    }
}
