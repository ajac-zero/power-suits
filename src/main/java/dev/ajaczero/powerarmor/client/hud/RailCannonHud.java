package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The rail cannon on the visor (user's pick A, 2026-10-27): it's a sniper's shot that only fires at full charge, so the
 * charge is at the crosshair. Four corner brackets wait wide and faint round it; as the charge builds they close in, and
 * at full charge they lock (a second, inner set) over READY. Under them, one pip per round left (red on the last).
 */
public final class RailCannonHud implements ToolHud {
    /** The brackets' distance from the crosshair's middle (GUI px): uncharged, and at full charge. The near one leaves
     *  the crosshair's own 7 px arms clear. */
    private static final int FAR = 22, NEAR = 7, ARM = 4;
    /** Rows under the crosshair: the charge (or READY), then the pips. */
    private static final int TEXT_Y = 28, PIPS_Y = 38, MAX_PIPS = 8;

    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        HudReadout r = v.readout;
        if (r == null) {
            return "";
        }
        float charge = Mth.clamp(r.bar(), 0F, 1F);
        boolean ready = charge >= 0.999F;
        int cx = v.crosshairX(), cy = v.crosshairY();
        int d = Math.round(Mth.lerp(charge, FAR, NEAR));
        HudView.brackets(g, cx - d, cy - d, cx + d, cy + d, ARM, charge > 0F ? v.main(false) : v.faint(false));
        if (ready) {
            // locked: a second set of short brackets just inside
            HudView.brackets(g, cx - d + 3, cy - d + 3, cx + d - 3, cy + d - 3, 2, v.main(false));
            v.centred(g, Component.translatable("hud.powerarmor.ready"), cx + 1, cy + TEXT_Y, v.main(false));
        } else if (charge > 0F) {
            v.centred(g, Component.translatable("hud.powerarmor.percent", Math.round(charge * 100)), cx + 1, cy + TEXT_Y, v.main(false));
        }
        v.pips(g, r, cx + 1, cy + PIPS_Y, MAX_PIPS);
        return String.format(Locale.ROOT, "charge=%.2f rounds=%d%s", charge, r.count(), ready ? " ready" : "");
    }
}
