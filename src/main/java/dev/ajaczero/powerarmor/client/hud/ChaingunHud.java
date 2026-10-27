package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The chaingun on the visor (user's pick A, 2026-10-27): you hold the trigger and walk the stream onto the target, so
 * the view stays clear and the readout sits in the top right corner, mirroring the suit's on the left
 * ({@link HudView#ammoRow}, {@link HudView#gaugeRow}: values in a right column, labels right-aligned beside them):
 * the bullets left on Fallout's big 3-digit counter with AMMO beside it, then the barrels' heat as a bar under the
 * counter with HEAT beside it. Alerts invert the label ({@link HudView#label}): AMMO blinks on the last bullets; HEAT
 * holds inverted from half full and blinks in its last 15%; an overheat locks the barrels until they're cold, and the
 * label reads OVERHEAT, blinking, all the way down while they cool (user, 2026-10-27: so it's clear the gun won't fire
 * until the bar is empty).
 */
public final class ChaingunHud implements ToolHud {
    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        HudReadout r = v.readout;
        if (r == null) {
            return "";
        }
        v.ammoRow(g, r.count(), r.lowAmmo());
        boolean locked = r.barLocked();
        v.gaugeRow(g, Component.translatable(locked ? "hud.powerarmor.overheat" : "hud.powerarmor.heat"), r.bar(), r.barAlert());
        return String.format(Locale.ROOT, "ammo=%d heat=%.2f %s%s%s", r.count(), r.bar(), r.barAlert().name().toLowerCase(Locale.ROOT),
                r.lowAmmo() ? " low" : "", locked ? " overheat" : "");
    }
}
