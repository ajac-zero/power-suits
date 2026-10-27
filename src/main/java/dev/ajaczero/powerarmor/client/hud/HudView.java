package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.HudAlert;
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
    /** Unlit segments at this fraction of the colour (an empty bar must read as empty); faint for secondary text. */
    public static final float DIM = 0.35F, FAINT = 0.65F;
    /** An alert's text, cut into its block: the visor colour this dark (near black, but of its hue). */
    public static final float CUT = 0.12F;

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

    /** The visor colour: values, lit segments. */
    public int main() {
        return 0xFF000000 | this.rgb;
    }

    /** Unlit (bar segments, empty chambers). */
    public int dim() {
        return 0xFF000000 | VisorConfig.dim(this.rgb, DIM);
    }

    /** Secondary: labels, a gauge at rest. */
    public int faint() {
        return 0xFF000000 | VisorConfig.dim(this.rgb, FAINT);
    }

    // ---- alerts ------------------------------------------------------------------------------------------------

    /**
     * One colour for the whole visor (user's pick, 2026-10-27): an alert doesn't change the colour, it inverts the
     * readout's label like an old terminal: a solid block of the visor colour with the text cut into it. A warning holds
     * the block steady, a critical one blinks it (every 5 ticks, with the plain label between). Orange and red were
     * tried first: an amber visor hid the orange warning, red and green are the pair colour-blind players mix up, and a
     * player's own colour may be either. Inverse video reads in any colour.
     */
    public static boolean inverse(HudAlert alert, boolean blink) {
        return alert == HudAlert.WARNING || alert == HudAlert.CRITICAL && blink;
    }

    /** A label in the visor colour ({@code rgb}, 0xRRGGBB) at this alert: plain, or inverted ({@link #inverse}). */
    public static void label(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int rgb, HudAlert alert,
                             boolean blink) {
        if (inverse(alert, blink)) {
            // a pixel clear of the glyphs all round: font.width counts the 1 px gap after the last glyph, capitals are
            // 7 px tall and the font's descenders reach the 8th row
            g.fill(x - 1, y - 1, x + font.width(text), y + 8, 0xFF000000 | rgb);
            g.text(font, text, x, y, 0xFF000000 | VisorConfig.dim(rgb, CUT), false);
        } else {
            g.text(font, text, x, y, 0xFF000000 | rgb, false);
        }
    }

    public void label(GuiGraphicsExtractor g, Component text, int x, int y, HudAlert alert) {
        label(g, this.font, text, x, y, this.rgb, alert, this.blink);
    }

    /** Critical when {@code critical}, else nothing to say. */
    public static HudAlert critical(boolean critical) {
        return critical ? HudAlert.CRITICAL : HudAlert.NONE;
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

    // ---- the top right corner: the ammo readout (chaingun, TNT launcher) ------------------------------------------

    /**
     * The corner block, two rows in two columns (user, 2026-10-27): values on the right (the big counter, a bar under
     * it as wide as the counter's three digits), their labels right-aligned to the left of them, so a longer label
     * (OVERHEAT) grows to the left instead of into the bar. Its rows from y 26 like the suit's readout on the left
     * (SuitHud: 4 px margin), 5 px in from the margin; the second row on the left's FUEL row.
     */
    public static final int CORNER_Y0 = 26;
    /** The bar's segments: 9 x 4 - 1 = 35 px, the counter's "000" at twice the font's size (34) and its trailing pixel. */
    private static final int CORNER_SEGMENTS = 9, VALUE_W = CORNER_SEGMENTS * 4 - 1, LABEL_GAP = 5;
    /** The block's width from the labels' left (the widest usual label, AMMO or HEAT, 23 px) to the values' right. */
    private static final int CORNER_W = VALUE_W + LABEL_GAP + 23, CORNER_MARGIN = 4, CORNER_INSET = 5;

    public int cornerRight() {
        return this.width - CORNER_MARGIN - CORNER_INSET;
    }

    public int cornerLeft() {
        return this.cornerRight() - CORNER_W;
    }

    /** Where the values column starts (the counter's and the bar's left edge). */
    public int valueLeft() {
        return this.cornerRight() - VALUE_W;
    }

    /** Where a label in the corner's label column starts: right-aligned to the values, so it grows to the left. */
    private int cornerLabelX(Component label) {
        return this.valueLeft() - LABEL_GAP - (this.font.width(label) - 1);
    }

    /** A label in the corner's label column, in this colour. */
    public void cornerLabel(GuiGraphicsExtractor g, Component label, int y, int colour) {
        g.text(this.font, label, this.cornerLabelX(label), y, colour, false);
    }

    /** A label in the corner's label column at this alert ({@link #label}). */
    public void cornerLabel(GuiGraphicsExtractor g, Component label, int y, HudAlert alert) {
        this.label(g, label, this.cornerLabelX(label), y, alert);
    }

    /** The first row: the count on the big counter, AMMO beside it on its baseline, inverted on the last rounds. */
    public void ammoRow(GuiGraphicsExtractor g, int count, boolean last) {
        this.counter(g, count, this.cornerRight(), CORNER_Y0 + 2, this.main());
        this.cornerLabel(g, Component.translatable("hud.powerarmor.ammo"), CORNER_Y0 + 9, critical(last));
    }

    /** The second row (the left's FUEL row): a bar under the counter, its label beside it (the alert's). */
    public void gaugeRow(GuiGraphicsExtractor g, Component label, float frac, HudAlert alert) {
        this.cornerLabel(g, label, CORNER_Y0 + 18, alert);
        bar(g, this.valueLeft(), CORNER_Y0 + 19, CORNER_SEGMENTS, 5, frac, this.main(), this.dim());
    }

    // ---- a flight on the world (the TNT round, a thrown mob) ----------------------------------------------------

    /** Screen pixels between the dots of a flight's line. */
    private static final int DOT_SPACING = 3;

    /**
     * A dotted line through points of the world (a flight's position tick by tick), a dot every few screen pixels so it
     * reads as one line however far away it runs, none within {@code clear} px of the crosshair's middle (0: none
     * skipped). Segments with an end behind the camera are left out. Returns the dots drawn.
     */
    public int dottedLine(GuiGraphicsExtractor g, java.util.List<Vec3> points, int colour, int clear) {
        int cx = this.crosshairX(), cy = this.crosshairY(), dots = 0;
        float carry = 0F;
        float[] a = points.isEmpty() ? null : this.project(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            float[] b = this.project(points.get(i));
            float dx = b == null || a == null ? 0F : b[0] - a[0], dy = b == null || a == null ? 0F : b[1] - a[1];
            float length = Mth.sqrt(dx * dx + dy * dy);
            if (a != null && b != null && length > 0.01F) {
                float t = carry;
                for (; t <= length; t += DOT_SPACING) {
                    int x = Math.round(a[0] + dx * t / length), y = Math.round(a[1] + dy * t / length);
                    boolean nearCrosshair = Math.abs(x - cx) <= clear && Math.abs(y - cy) <= clear;
                    if (!nearCrosshair && x >= 0 && y >= 0 && x < this.width && y < this.height) {
                        g.fill(x, y, x + 1, y + 1, colour);
                        dots++;
                    }
                }
                carry = t - length;
            }
            a = b;
        }
        return dots;
    }

    /** A diamond outline of half-size {@code r} round (x, y): where a flight ends. */
    public static void diamond(GuiGraphicsExtractor g, int x, int y, int r, int colour) {
        for (int k = 0; k <= r; k++) {
            g.fill(x - r + k, y - k, x - r + k + 1, y - k + 1, colour);
            g.fill(x - r + k, y + k, x - r + k + 1, y + k + 1, colour);
            g.fill(x + r - k, y - k, x + r - k + 1, y - k + 1, colour);
            g.fill(x + r - k, y + k, x + r - k + 1, y + k + 1, colour);
        }
    }

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

    /** A label centred on {@code x}, at this alert ({@link #label}). */
    public void centred(GuiGraphicsExtractor g, Component text, int x, int y, HudAlert alert) {
        this.label(g, text, x - (this.font.width(text) - 1) / 2, y, alert);
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
     * when none left. Critical on the last of them: inverted like a label ({@link #label}), the pips cut into a block.
     */
    public void pips(GuiGraphicsExtractor g, HudReadout r, int x, int y, int max) {
        HudAlert alert = critical(r.lowAmmo() || r.count() == 0);
        if (r.count() == HudReadout.ENDLESS) {
            this.centred(g, Component.translatable("hud.powerarmor.endless"), x, y - 2, alert);
            return;
        }
        if (r.count() <= 0) {
            this.centred(g, Component.translatable("hud.powerarmor.empty"), x, y - 2, alert);
            return;
        }
        int n = Math.min(r.count(), max);
        int x0 = x - (n * 4 - 1) / 2;
        int colour = this.main();
        if (inverse(alert, this.blink)) {
            g.fill(x0 - 1, y - 1, x0 + n * 4, y + 4, colour);
            colour = 0xFF000000 | VisorConfig.dim(this.rgb, CUT);
        }
        for (int i = 0; i < n; i++) {
            g.fill(x0 + i * 4, y, x0 + i * 4 + 3, y + 3, colour);
        }
        if (r.count() > max) {
            this.label(g, Component.literal("+" + (r.count() - max)), x0 + n * 4 + 2, y - 2, alert);
        }
    }
}
