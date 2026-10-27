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
 * power, the edge of the glass along each piece catches the visor's light: a 1 px line in the visor colour
 * ({@link VisorConfig}); without power it's just the helmet. The readouts are {@link SuitHud}'s.
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
    /** The rim's light, out of 255: bright but not solid, so it reads as light on glass. */
    private static final int RIM_ALPHA = 200;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        PowerArmorEntity suit = SuitHud.shownFor(mc);
        if (suit == null || !PowerArmorRenderer.isCockpitView(suit)) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        int rim = suit.isPowered() ? ARGB.color(RIM_ALPHA, VisorConfig.colour()) : 0;
        TOP.draw(g, 0, 0, false, rim);
        TOP.draw(g, w - TOP.w, 0, true, rim);
        BOTTOM.draw(g, 0, h - BOTTOM.h, false, rim);
        BOTTOM.draw(g, w - BOTTOM.w, h - BOTTOM.h, true, rim);
        BROW.draw(g, w / 2 - BROW.w / 2, 0, false, rim);
    }

    /** One piece: its texture (slate + steel lip) and its rim mask (white, tinted), drawn mirrored for the right side. */
    private record Piece(Identifier texture, Identifier rimMask, int w, int h) {
        Piece(String name, int w, int h) {
            this(tex(name), tex(name + "_rim"), w, h);
        }

        void draw(GuiGraphicsExtractor g, int x, int y, boolean mirrored, int rim) {
            blit(g, this.texture, x, y, mirrored, 0xFFFFFFFF);
            if (rim != 0) {
                blit(g, this.rimMask, x, y, mirrored, rim);
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
