package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The helmet seen from inside it (2026-10-27): like the pumpkin's overlay, but only at the edges of the view. Two dark
 * steel pieces in the top corners, two in the bottom corners and the visor band in the middle of the top edge, each on
 * its own (the user's sketches: corners that end at the sides instead of wrapping round to meet). While the suit has
 * power the visor shows the view through the metal (user, 2026-10-27, option B of the see-through mockups): only each
 * piece's 1 px steel lip is left, a faint outline of where the helmet is; without power the pieces are solid black
 * again, like the pumpkin's overlay. Between the two the visor scans ({@link VisorPower}): lines sweeping out from the
 * middle of the screen turn the metal see-through as they pass, lines sweeping in from the edges turn it black again.
 * The readouts are {@link SuitHud}'s.
 * <p>
 * A GUI layer drawn right after vanilla's camera overlays (the pumpkin's layer), so the crosshair, the hotbar and the
 * HUD draw over it. First person only, like the pumpkin. The pieces are in GUI pixels and keep their size on any window
 * (they're anchored to the corners, not stretched like the pumpkin's picture). Shapes and art:
 * tools/make-visor-textures.py.
 */
public final class SuitVisor {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_visor");

    private static final Piece TOP = new Piece("corner_top", 101, 21);
    private static final Piece BOTTOM = new Piece("corner_bottom", 85, 30);
    private static final Piece BROW = new Piece("brow", 132, 8);
    /** The lip's opacity while powered, out of 255: faint but still there (user, 2026-10-27: 75% was jarring; 45%). */
    private static final int LIP_ALPHA = 115;
    /** How much of the screen's height the scan had turned on last frame (1 = all: the metal see-through; 0 = none:
     *  solid; NaN = no visor), for the autopilot's log. */
    private static volatile float lastSweep = Float.NaN;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        PowerArmorEntity suit = SuitHud.shownFor(mc);
        if (suit == null || !PowerArmorRenderer.isCockpitView(suit)) {
            lastSweep = Float.NaN;
            return;
        }
        VisorPower.watch(suit);
        VisorPower.Scan scan = VisorPower.scan();
        lastSweep = scan.sweep();
        int w = g.guiWidth(), h = g.guiHeight();
        int top = scan.top(h), bottom = scan.bottom(h);
        // what the scan has reached shows only the lips; above and below it the metal is still solid black
        if (bottom > top) {
            g.enableScissor(0, top, w, bottom);
            drawAll(g, w, h, true);
            g.disableScissor();
        }
        if (top > 0) {
            g.enableScissor(0, 0, w, top);
            drawAll(g, w, h, false);
            g.disableScissor();
            g.enableScissor(0, bottom, w, h);
            drawAll(g, w, h, false);
            g.disableScissor();
        }
    }

    private static void drawAll(GuiGraphicsExtractor g, int w, int h, boolean seeThrough) {
        TOP.draw(g, 0, 0, false, seeThrough);
        TOP.draw(g, w - TOP.w, 0, true, seeThrough);
        BOTTOM.draw(g, 0, h - BOTTOM.h, false, seeThrough);
        BOTTOM.draw(g, w - BOTTOM.w, h - BOTTOM.h, true, seeThrough);
        BROW.draw(g, w / 2 - BROW.w / 2, 0, false, seeThrough);
    }

    /** For the autopilot's log: how much of the screen the visor's scan had turned on last frame (see lastSweep). */
    public static float lastSweep() {
        return lastSweep;
    }

    /** One piece: its texture (slate + steel lip) and the lip alone, drawn mirrored for the right side. */
    private record Piece(Identifier texture, Identifier lip, int w, int h) {
        Piece(String name, int w, int h) {
            this(tex(name), tex(name + "_lip"), w, h);
        }

        /** seeThrough: only the faint lip (the visor shows the view through the metal); else the solid black piece. */
        void draw(GuiGraphicsExtractor g, int x, int y, boolean mirrored, boolean seeThrough) {
            if (seeThrough) {
                blit(g, this.lip, x, y, mirrored, ARGB.color(LIP_ALPHA, 0xFFFFFF));
            } else {
                blit(g, this.texture, x, y, mirrored, 0xFFFFFFFF);
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
