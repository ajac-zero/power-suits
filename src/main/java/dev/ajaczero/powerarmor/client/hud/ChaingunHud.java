package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The chaingun on the visor (user's pick A, 2026-10-27): you hold the trigger and walk the stream onto the target, so
 * the view stays clear and the readout sits in the top right corner, mirroring the suit's on the left
 * ({@link HudView#ammoRow}, {@link HudView#gaugeRow}: one block, its rows on the left's rows): AMMO and the bullets
 * left on Fallout's big 3-digit counter, then the barrels' HEAT and its bar. The counter turns red on the last bullets;
 * the heat turns red and reads OVERHEAT while the barrels are locked.
 */
public final class ChaingunHud implements ToolHud {
    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        HudReadout r = v.readout;
        if (r == null) {
            return "";
        }
        v.ammoRow(g, r.count(), r.lowAmmo());
        boolean hot = r.barCritical();
        v.gaugeRow(g, Component.translatable(hot ? "hud.powerarmor.overheat" : "hud.powerarmor.heat"), r.bar(), hot);
        return String.format(Locale.ROOT, "ammo=%d heat=%.2f%s%s", r.count(), r.bar(), r.lowAmmo() ? " low" : "", hot ? " hot" : "");
    }
}
