package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The helmet seen from inside it (2026-10-27): like the pumpkin's overlay, but only at the edges of the view. Two dark
 * steel pieces in the top corners, two in the bottom corners and the visor band in the middle of the top edge, each on
 * its own (the user's sketches: corners that end at the sides instead of wrapping round to meet). While the suit has
 * power the visor shows the view through the metal (user, 2026-10-27, option B of the see-through mockups): only each
 * piece's 1 px steel lip is left, a faint outline of where the helmet is; without power the pieces are solid again, so
 * losing power closes the view in at once (the picture dies with the power).
 * <p>
 * The warm-up: when the visor gets power (fuel comes back, or you climb into a running suit) the metal doesn't vanish at
 * once, it fades out over {@link #WARM_UP_MS}, eased at both ends, so you see the visor take over. The readouts are
 * {@link SuitHud}'s.
 * <p>
 * A GUI layer drawn right after vanilla's camera overlays (the pumpkin's layer), so the crosshair, the hotbar and the
 * HUD draw over it. First person only, like the pumpkin. The pieces are in GUI pixels and keep their size on any window
 * (they're anchored to the corners, not stretched like the pumpkin's picture). Shapes and art:
 * tools/make-visor-textures.py.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class SuitVisor {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_visor");

    private static final Piece TOP = new Piece("corner_top", 101, 21);
    private static final Piece BOTTOM = new Piece("corner_bottom", 85, 30);
    private static final Piece BROW = new Piece("brow", 132, 8);
    /** The lip's opacity while powered, out of 255: faint but still there (user, 2026-10-27: 75% was jarring; 45%). */
    private static final int LIP_ALPHA = 115;
    /** How long the metal takes to fade out when the visor powers up. */
    private static final long WARM_UP_MS = 600;

    /** The warm-up's clock: which suit's visor is lit and since when (wall-clock ms, not game ticks, so it runs as
     *  smoothly as the frames do). -1 = not lit: the next powered frame starts a warm-up. Client only, per world. */
    private static int litSuit = -1;
    private static long litSince;
    /** How solid the metal was drawn last frame (1 = solid, 0 = only the lip; NaN = no visor), for the autopilot log. */
    private static volatile float lastMetal = Float.NaN;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        PowerArmorEntity suit = SuitHud.shownFor(mc);
        if (suit == null || !PowerArmorRenderer.isCockpitView(suit)) {
            litSuit = -1; // out of the cockpit view: the next look through the visor warms up again
            lastMetal = Float.NaN;
            return;
        }
        float metal = metal(suit);
        lastMetal = metal;
        int w = g.guiWidth(), h = g.guiHeight();
        TOP.draw(g, 0, 0, false, metal);
        TOP.draw(g, w - TOP.w, 0, true, metal);
        BOTTOM.draw(g, 0, h - BOTTOM.h, false, metal);
        BOTTOM.draw(g, w - BOTTOM.w, h - BOTTOM.h, true, metal);
        BROW.draw(g, w / 2 - BROW.w / 2, 0, false, metal);
    }

    /** How solid the metal is now: 1 without power, fading to 0 over the warm-up once the power is on. */
    private static float metal(PowerArmorEntity suit) {
        if (!suit.isPowered()) {
            litSuit = -1;
            return 1F;
        }
        long now = Util.getMillis();
        if (litSuit != suit.getId()) {
            litSuit = suit.getId();
            litSince = now;
        }
        float t = Mth.clamp((now - litSince) / (float) WARM_UP_MS, 0F, 1F);
        return 1F - Mth.smoothstep(t);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        litSuit = -1; // entity ids start over in the next world
        lastMetal = Float.NaN;
    }

    /** For the autopilot's log: how solid the visor's metal was last frame (1 solid, 0 see-through, NaN not shown). */
    public static float lastMetal() {
        return lastMetal;
    }

    /** One piece: its texture (slate + steel lip) and the lip alone, drawn mirrored for the right side. */
    private record Piece(Identifier texture, Identifier lip, int w, int h) {
        Piece(String name, int w, int h) {
            this(tex(name), tex(name + "_lip"), w, h);
        }

        /** metal: 1 = the solid piece, 0 = only its faint lip; in between (the warm-up) the solid piece fades out over
         *  the lip, so the lip goes from solid to faint with the rest instead of blinking. */
        void draw(GuiGraphicsExtractor g, int x, int y, boolean mirrored, float metal) {
            if (metal < 1F) {
                blit(g, this.lip, x, y, mirrored, ARGB.color(LIP_ALPHA, 0xFFFFFF));
            }
            if (metal > 0F) {
                blit(g, this.texture, x, y, mirrored, ARGB.color(Math.round(metal * 255), 0xFFFFFF));
            }
        }

        private void blit(GuiGraphicsExtractor g, Identifier t, int x, int y, boolean mirrored, int colour) {
            // mirrored: read the texture right to left (u from w back to 0)
            g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, mirrored ? this.w : 0F, 0F, this.w, this.h,
                    mirrored ? -this.w : this.w, this.h, this.w, this.h, colour);
        }
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/visor/" + name + ".png");
    }

    private SuitVisor() {}
}
