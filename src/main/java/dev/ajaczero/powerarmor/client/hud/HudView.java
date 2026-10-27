package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.client.VisorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link ToolHud} draws with, this frame: the suit and its pilot (this client's player), the visor's colours,
 * where the crosshair is, a way to find a point of the world on the screen, and the HUD's shared shapes (segmented
 * bars, corner brackets, the big counter), so every tool's readout looks like the rest of the visor.
 */
public final class HudView {
    /** Warnings, whatever the visor colour (SuitHud uses the same). */
    public static final int RED = 0xFF5555;
    /** Unlit segments at this fraction of the colour (an empty bar must read as empty); faint for secondary text. */
    public static final float DIM = 0.35F, FAINT = 0.65F;

    public final PowerArmorEntity suit;
    public final Player pilot;
    public final Font font;
    /** The visor's colour, 0xRRGGBB. */
    public final int rgb;
    /** The frame's partial tick (0 last tick .. 1 this one): for anything that follows the view or moves. */
    public final float partial;
    /** True half the time, switching every 5 ticks (like the HUD's other blinking warnings). */
    public final boolean blink;
    /** The tool's numbers (ToolEffects.hud), or null for a tool with none. */
    @Nullable
    public final HudReadout readout;
    private final Minecraft mc;
    private final int width, height;

    public HudView(GuiGraphicsExtractor g, Minecraft mc, PowerArmorEntity suit, Player pilot, int rgb, float partial) {
        this.mc = mc;
        this.suit = suit;
        this.pilot = pilot;
        this.font = mc.font;
        this.rgb = rgb;
        this.partial = partial;
        this.blink = (suit.tickCount / 5) % 2 == 0;
        this.readout = suit.socketVisuals().effects().hud(suit, pilot);
        this.width = g.guiWidth();
        this.height = g.guiHeight();
    }

    // ---- colours (ARGB, ready to draw) ----------------------------------------------------------------------------

    /** The visor colour, or red for a warning. */
    public int main(boolean warn) {
        return 0xFF000000 | (warn ? RED : this.rgb);
    }

    /** Unlit (bar segments, empty chambers). */
    public int dim(boolean warn) {
        return 0xFF000000 | VisorConfig.dim(warn ? RED : this.rgb, DIM);
    }

    /** Secondary: labels, a gauge at rest. */
    public int faint(boolean warn) {
        return 0xFF000000 | VisorConfig.dim(warn ? RED : this.rgb, FAINT);
    }

    // ---- where things are --------------------------------------------------------------------------------------

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** The middle pixel of vanilla's crosshair (a 15 px sprite at ((w - 15) / 2, (h - 15) / 2): Hud.extractCrosshair). */
    public int crosshairX() {
        return (this.width - 15) / 2 + 7;
    }

    public int crosshairY() {
        return (this.height - 15) / 2 + 7;
    }

    /**
     * Where a point of the world is on the screen, in GUI pixels (x, y), or null when it's behind the camera. Vanilla's
     * own projection (the locator bar's): the camera's view and lens of this frame, so it sits on the world as drawn.
     */
    @Nullable
    public float[] project(Vec3 world) {
        Vec3 ndc = this.mc.gameRenderer.projectPointToScreen(world);
        if (ndc.z > 1.0 || ndc.z < -1.0 || Double.isNaN(ndc.x)) {
            return null; // behind the camera (or on its plane)
        }
        return new float[] {(float) (ndc.x + 1) / 2F * this.width, (float) (1 - ndc.y) / 2F * this.height};
    }

    // ---- shapes ----------------------------------------------------------------------------------------------

    /** A segmented bar: {@code n} 3-pixel segments with 1-pixel gaps, the first {@code frac} of them lit. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int n, int height, float frac, int lit, int unlit) {
        int on = Math.round(n * Mth.clamp(frac, 0F, 1F));
        for (int i = 0; i < n; i++) {
            g.fill(x + i * 4, y, x + i * 4 + 3, y + height, i < on ? lit : unlit);
        }
    }

    /** Four corner brackets (arms {@code arm} px long) round the box from (x0, y0) to (x1, y1), corners included. */
    public static void brackets(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int arm, int colour) {
        g.fill(x0, y0, x0 + arm, y0 + 1, colour);
        g.fill(x0, y0, x0 + 1, y0 + arm, colour);
        g.fill(x1 - arm + 1, y0, x1 + 1, y0 + 1, colour);
        g.fill(x1, y0, x1 + 1, y0 + arm, colour);
        g.fill(x0, y1, x0 + arm, y1 + 1, colour);
        g.fill(x0, y1 - arm + 1, x0 + 1, y1 + 1, colour);
        g.fill(x1 - arm + 1, y1, x1 + 1, y1 + 1, colour);
        g.fill(x1, y1 - arm + 1, x1 + 1, y1 + 1, colour);
    }

    /** Text centred on {@code x}. */
    public void centred(GuiGraphicsExtractor g, Component text, int x, int y, int colour) {
        g.text(this.font, text, x - (this.font.width(text) - 1) / 2, y, colour, false);
    }

    /** Fallout's ammo counter: three digits at twice the font's size (006; 999 = "999 or more"; endless = the sign),
     *  ending at {@code right}. Returns its left edge. */
    public int counter(GuiGraphicsExtractor g, int count, int right, int y, int colour) {
        Component text = count == HudReadout.ENDLESS ? Component.translatable("hud.powerarmor.endless")
                : Component.literal(String.format(java.util.Locale.ROOT, "%03d", Math.min(Math.max(count, 0), 999)));
        int w = 2 * (this.font.width(text) - 1); // without the font's trailing gap
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(right - w, y);
        pose.scale(2F, 2F);
        g.text(this.font, text, 0, 0, colour, false);
        pose.popMatrix();
        return right - w;
    }

    /**
     * Rounds as pips centred on {@code x}: one 3x3 square each, up to {@code max}, then "+n"; "∞" when endless, EMPTY
     * when none left. Red when it's the last of them.
     */
    public void pips(GuiGraphicsExtractor g, HudReadout r, int x, int y, int max) {
        int colour = this.main(r.lowAmmo() || r.count() == 0);
        if (r.count() == HudReadout.ENDLESS) {
            this.centred(g, Component.translatable("hud.powerarmor.endless"), x, y - 2, colour);
            return;
        }
        if (r.count() <= 0) {
            this.centred(g, Component.translatable("hud.powerarmor.empty"), x, y - 2, colour);
            return;
        }
        int n = Math.min(r.count(), max);
        int x0 = x - (n * 4 - 1) / 2;
        for (int i = 0; i < n; i++) {
            g.fill(x0 + i * 4, y, x0 + i * 4 + 3, y + 3, colour);
        }
        if (r.count() > max) {
            g.text(this.font, Component.literal("+" + (r.count() - max)), x0 + n * 4 + 2, y - 2, colour, false);
        }
    }
}
