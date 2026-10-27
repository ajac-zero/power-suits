package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.client.hud.HudView;
import dev.ajaczero.powerarmor.client.hud.ToolHud;
import dev.ajaczero.powerarmor.client.hud.ToolHuds;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * The pilot's HUD, projected on the helmet's visor (design sec. 6). Since 2026-10-27 it belongs to the visor
 * ({@link SuitVisor} draws the helmet's edge around the view): no boxes, the readouts float on the glass in the visor
 * colour (Mods > Power Armor > Config, {@link VisorConfig}; terminal green by default) in the top corners, with a
 * compass and a clock between them (the helmet is crafted with both).
 * <ul>
 *   <li>Top left, the suit: its figure (one block per armour piece, like Fallout's), HULL (health) with the seal shield,
 *   and FUEL (time left at walking pace, a bar for how full the fuel slot is) with three embers for the burn rate:
 *   1 idling, 2 walking, 3 sprinting or a hungry tool at work, dark when nothing burns (a free engine).</li>
 *   <li>The socket tool's own readout, wherever it wants it (2026-10-27: each tool brings its own, client/hud/ToolHuds):
 *   the chaingun's ammo and heat in the top right corner, the rail cannon's charge brackets round the crosshair, the
 *   TNT launcher's drum and the arc its round will fly, the EM projector's box round what it holds. Tools with nothing
 *   to say draw nothing.</li>
 *   <li>Top centre, under the helmet's brow, as terminal text: a compass tape (one character per 5 degrees) with a
 *   {@code ^} under the heading, the heading in degrees, the day and the time. Like the vanilla items they only work in
 *   the overworld: elsewhere the tape spins and the numbers read {@code ---}.</li>
 * </ul>
 * The suit's readouts and the compass turn red while the suit is critical (hull at a quarter, fuel low); a tool's
 * warnings (the last rounds, an overheated barrel) turn only that tool's readout red. Without power the projector is
 * dark: only a red blinking NO FUEL (or FLOODED) is left. The helmet carries the projector: no helmet, no HUD (vanilla's vehicle hearts then stand in for the hull, as on any mount). With the HUD up
 * those hearts are hidden, and vanilla's boss bars and effect icons move down out of its way.
 * <p>
 * It's a "GUI layer" (registered in ClientSetup), drawn every frame after the vanilla hotbar. Everything it shows comes
 * from the suit's synced data and the client's own world: the client never asks the server.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class SuitHud {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_hud");
    /** The suit figure, one mask per armour piece on one 15x26 canvas (tools/make-hud-icons.py), so each piece can take
     *  its own colour: today all follow the hull; with modular armour a critical piece will flash red on its own. */
    private static final Identifier[] FIGURE = {hud("suit_helmet"), hud("suit_chest"), hud("suit_arm_left"),
            hud("suit_arm_right"), hud("suit_leg_left"), hud("suit_leg_right")};
    private static final int FIGURE_W = 15, FIGURE_H = 26;
    private static final Identifier SEAL = hud("seal");
    private static final Identifier EMBER = hud("ember");
    private static final Identifier EMBER_LIT = hud("ember_lit");

    /** Warnings, whatever the visor colour. */
    private static final int RED = HudView.RED;
    /** Unlit segments (and the compass's dots) at this fraction of the colour: an empty bar must read as empty. */
    private static final float DIM = HudView.DIM, FAINT = HudView.FAINT;
    /** The hull is critical from a quarter left. */
    private static final float HULL_CRITICAL = 0.25F;
    /** Burn-rate embers: energy per tick at or above which 2 and 3 are lit (idle 0.25, walking 1, sprinting 2 by default;
     *  a flamethrower burns 6). Measured from the fuel actually going, so tools, jumps and air all count. */
    private static final float TWO_EMBERS = 0.6F, THREE_EMBERS = 1.5F;
    private static final int MAX_BURN_PER_TICK = 20;

    /** Placement (GUI pixels). Both readouts sit under the helmet's top corners ({@link SuitVisor}: 19 px deep at the
     *  side), 120 wide: the narrowest where the fuel row's longest text (FLOODED, 41 px) still clears the embers. TOP is
     *  the readouts' first row, below the compass's second (12 + 9 + 8). */
    private static final int MARGIN = 4, WIDTH = 120, TOP = 30;
    /** The compass: a tape of 31 characters in 6 px cells (fixed width, like a terminal), 5 degrees each; its row and
     *  the readout row under it. */
    private static final int CELL = 6, TAPE = 31, DEGREES_PER_CELL = 5, TAPE_Y = 12, READOUT_Y = 21;
    /** Vanilla's boss bars start at y 12 and its effect icons at y 1 (the top right): moved down this far under the
     *  compass and the top right corner (the chaingun's readout, client/hud/ChaingunHud) while the HUD is up (user's
     *  pick A, 2026-10-27: vanilla's own bars, moved). */
    private static final int BOSS_SHIFT = 30, EFFECTS_SHIFT = 62;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        PowerArmorEntity suit = shownFor(mc);
        if (suit == null) {
            shown = null;
            VisorPower.forget();
            return;
        }
        VisorPower.watch(suit);
        VisorPower.Scan scan = VisorPower.scan();
        Player pilot = mc.player;
        Font font = mc.font;
        boolean powered = suit.isPowered();
        boolean blink = (suit.tickCount / 5) % 2 == 0;
        int x0 = MARGIN, y0 = TOP - 4, x1 = x0 + WIDTH;

        var power = suit.power();
        boolean free = power.hasFreePower() && powered;
        int energy = power.getTotalEnergy();
        // Every word and sign on the HUD comes from the language file (hud.powerarmor.*), so a translation covers it all.
        // No engine reads NO FUEL too (user, 2026-10-27: players get it; the menu still says NO ENGINE).
        Component fuelText = free ? Component.translatable("hud.powerarmor.endless")
                : power.isFlooded() ? Component.translatable("gui.powerarmor.flooded")
                : energy > 0 ? Component.literal(PowerArmorItem.formatWalkTime(energy))
                : Component.translatable("gui.powerarmor.no_fuel");
        float hull = Mth.clamp(suit.getHealth() / suit.getMaxHealth(), 0F, 1F);
        if (!powered && scan.off()) {
            // The projector runs on the suit's power: with none, the glass is dark but for the reason, blinking red
            // (once the shutdown's scan is over: VisorPower).
            if (blink) {
                g.text(font, fuelText, x0 + 26, y0 + 4, colour(RED), false);
            }
            shown = new Shown(hull, power.fuelGauge(), blink ? fuelText.getString() : "", 0, true, false, "", "", "", 0F);
            return;
        }

        boolean hullCritical = hull <= HULL_CRITICAL;
        boolean fuelCritical = !free && (suit.getEngine() == null || power.isFlooded() || energy == 0 || power.isLowOnFuel());
        float fuel = power.fuelGauge();
        int embers = embers(suit);

        // the tool's warnings are its own readout's (ToolHud), not the whole HUD's
        boolean critical = hullCritical || fuelCritical;
        String toolId = suit.socket().tool() == null ? "" : BuiltInRegistries.ITEM.getKey(suit.getSocketStack().getItem()).getPath();
        int rgb = critical ? RED : VisorConfig.colour();
        int main = colour(rgb);
        int dim = colour(VisorConfig.dim(rgb, DIM));
        int faint = colour(VisorConfig.dim(rgb, FAINT));

        // Warming up or shutting down (VisorPower): the readouts show only where the scan has reached, the band around
        // the middle of the screen between its two lines, so the lines uncover them (or wipe them away) as they pass.
        int h = g.guiHeight(), bandTop = scan.top(h), bandBottom = scan.bottom(h);
        if (bandBottom <= bandTop) {
            scan.drawLines(g, rgb);
            shown = new Shown(hull, power.fuelGauge(), fuelText.getString(), 0, critical, powered, toolId, "", "", 0F);
            return;
        }
        boolean clipped = scan.sweep() < 1F;
        if (clipped) {
            g.enableScissor(0, bandTop, g.guiWidth(), bandBottom);
        }

        // ---- top left: the suit ---------------------------------------------------------------------------------
        for (Identifier piece : FIGURE) {
            g.blit(RenderPipelines.GUI_TEXTURED, piece, x0 + 5, y0 + 4, 0F, 0F, FIGURE_W, FIGURE_H, FIGURE_W, FIGURE_H, main);
        }
        int lx = x0 + 26, rx = x1 - 4, segments = (rx - lx + 1) / 4;
        Component hullLabel = Component.translatable("hud.powerarmor.hull");
        Component fuelLabel = Component.translatable("hud.powerarmor.fuel");
        // the seal shield and the embers start in one column, past the longer label
        int ix = lx + Math.max(font.width(hullLabel), font.width(fuelLabel)) + 4;
        g.text(font, hullLabel, lx, y0 + 4, main, false);
        // sealed (running with the helmet on: fire, frost, gas and water kept out) or faded like an unlit ember
        g.blit(RenderPipelines.GUI_TEXTURED, SEAL, ix, y0 + 4, 0F, 0F, 6, 7, 6, 7, suit.isSealed() ? main : dim);
        Component hullText = Component.translatable("hud.powerarmor.percent", Math.round(hull * 100));
        if (!(hullCritical && blink)) {
            g.text(font, hullText, rx - font.width(hullText), y0 + 4, main, false);
        }
        HudView.bar(g, lx, y0 + 13, segments, 3, hull, main, dim);
        g.text(font, fuelLabel, lx, y0 + 18, main, false);
        for (int i = 0; i < 3; i++) {
            boolean lit = i < embers;
            g.blit(RenderPipelines.GUI_TEXTURED, lit ? EMBER_LIT : EMBER, ix + i * 7, y0 + 19, 0F, 0F, 5, 6, 5, 6,
                    lit ? 0xFFFFFFFF : dim);
        }
        if (!(fuelCritical && blink)) {
            g.text(font, fuelText, rx - font.width(fuelText), y0 + 18, main, false);
        }
        HudView.bar(g, lx, y0 + 27, segments, 3, fuel, main, dim);

        // ---- top centre: compass and clock ------------------------------------------------------------------------
        String clock = compassAndClock(g, mc, font, main, faint, delta);

        // ---- the socket tool's own readout (client/hud), if it has one ----------------------------------------------
        String toolShown = "";
        ToolHud toolHud = suit.socket().tool() == null ? null : ToolHuds.get(suit.getSocketStack().getItem());
        if (toolHud != null) {
            toolShown = toolHud.render(g, new HudView(g, mc, suit, pilot, VisorConfig.colour(), delta.getGameTimeDeltaPartialTick(false)));
        }

        if (clipped) {
            g.disableScissor();
        }
        scan.drawLines(g, rgb);
        shown = new Shown(hull, fuel, fuelText.getString(), embers, critical, powered, toolId, toolShown, clock, scan.sweep());
    }

    /**
     * The compass tape and, under it, the heading, a {@code ^} and the clock, in fixed-width cells like a terminal. The
     * tape moves a whole cell (5 degrees) at a time. Compass headings count from north clockwise; Minecraft's yaw counts
     * from south (0 south, 90 west), hence the 180. Returns what the readout row says (for the autopilot's log).
     */
    private static String compassAndClock(GuiGraphicsExtractor g, Minecraft mc, Font font, int main, int faint,
                                          DeltaTracker delta) {
        // The suit's compass and clock work like the vanilla items they're made from: only in the overworld. Elsewhere
        // the tape spins (a little faster than the item's needle, steadily, so it reads as broken, not as turning).
        boolean overworld = mc.level.dimension() == Level.OVERWORLD;
        float heading = overworld ? Mth.positiveModulo(mc.player.getViewYRot(delta.getGameTimeDeltaPartialTick(false)) + 180F, 360F)
                : Mth.positiveModulo((mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 11F, 360F);
        int centre = Math.round(heading / DEGREES_PER_CELL) * DEGREES_PER_CELL;
        int x0 = g.guiWidth() / 2 - TAPE * CELL / 2;
        int mid = TAPE / 2;
        String[] marks = new String[TAPE];
        boolean[] letter = new boolean[TAPE];
        for (int i = 0; i < TAPE; i++) {
            int deg = Mth.positiveModulo(centre + (i - mid) * DEGREES_PER_CELL, 360);
            marks[i] = deg % 15 == 0 ? "|" : ".";
        }
        // the eight directions as letters ("NE" takes two cells), drawn over the marks
        for (int i = 0; i < TAPE; i++) {
            int deg = Mth.positiveModulo(centre + (i - mid) * DEGREES_PER_CELL, 360);
            if (deg % 45 == 0) {
                String name = Component.translatable("hud.powerarmor.compass." + deg / 45).getString();
                for (int k = 0; k < name.length() && i + k < TAPE; k++) {
                    marks[i + k] = String.valueOf(name.charAt(k));
                    letter[i + k] = true;
                }
            }
        }
        for (int i = 0; i < TAPE; i++) {
            cell(g, font, marks[i], x0 + i * CELL, TAPE_Y, letter[i] ? main : faint);
        }
        int xc = x0 + mid * CELL;
        cell(g, font, "^", xc, READOUT_Y, main);
        String direction = Component.translatable("hud.powerarmor.compass." + Math.round(heading / 45F) % 8).getString();
        String bearing = overworld ? String.format(Locale.ROOT, "%03d %s", Math.round(heading) % 360, direction)
                : Component.translatable("hud.powerarmor.compass.lost").getString();
        cells(g, font, bearing, xc - (bearing.length() + 2) * CELL, READOUT_Y, main);
        String clock;
        if (overworld) {
            // Day 1 starts at sunrise, 06:00 (tick 0), like the game's own clock.
            long t = mc.level.getOverworldClockTime();
            long ofDay = Math.floorMod(t + 6000L, 24000L);
            clock = Component.translatable("hud.powerarmor.clock", t / 24000L + 1,
                    String.format(Locale.ROOT, "%02d:%02d", ofDay / 1000, ofDay % 1000 * 60 / 1000)).getString();
        } else {
            clock = Component.translatable("hud.powerarmor.clock", "--", "--:--").getString();
        }
        cells(g, font, clock, xc + 3 * CELL, READOUT_Y, faint);
        return bearing + " " + clock;
    }

    /** A string in fixed 6 px cells, each character centred in its cell (Minecraft's font is proportional). */
    private static void cells(GuiGraphicsExtractor g, Font font, String s, int x, int y, int colour) {
        for (int i = 0; i < s.length(); i++) {
            cell(g, font, String.valueOf(s.charAt(i)), x + i * CELL, y, colour);
        }
    }

    private static void cell(GuiGraphicsExtractor g, Font font, String ch, int x, int y, int colour) {
        if (!ch.isBlank()) {
            // a glyph is drawn width-1 px wide plus a 1 px gap; centre the drawn part in the cell
            g.text(font, ch, x + (CELL - (font.width(ch) - 1)) / 2, y, colour, false);
        }
    }

    /** The suit this client's player pilots, if the HUD shows: in the suit, helmet on, the GUI not hidden (F1). */
    @Nullable
    static PowerArmorEntity shownFor(Minecraft mc) {
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()
                || !(mc.player.getVehicle() instanceof PowerArmorEntity suit) || suit.getControllingPassenger() != mc.player) {
            return null;
        }
        return suit.hasHelmet() ? suit : null;
    }

    /** With our HUD up, the hull is on it: vanilla's three rows of vehicle hearts go. Vanilla's boss bars (top centre)
     *  and effect icons (top right) would sit on the compass and the tool readout: they move down instead (the pose is
     *  shifted for their layer only and put back after it). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void layerPre(RenderGuiLayerEvent.Pre event) {
        if (event.isCanceled() || shownFor(Minecraft.getInstance()) == null) {
            return;
        }
        Identifier name = event.getName();
        if (name.equals(VanillaGuiLayers.VEHICLE_HEALTH)) {
            event.setCanceled(true);
        } else if (name.equals(VanillaGuiLayers.BOSS_OVERLAY) || name.equals(VanillaGuiLayers.EFFECTS)) {
            event.getGuiGraphics().pose().pushMatrix();
            event.getGuiGraphics().pose().translate(0F, name.equals(VanillaGuiLayers.BOSS_OVERLAY) ? BOSS_SHIFT : EFFECTS_SHIFT);
            shifted = name;
        }
    }

    @SubscribeEvent
    public static void layerPost(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(shifted)) {
            event.getGuiGraphics().pose().popMatrix();
            shifted = null;
        }
    }

    /** The layer whose pose layerPre shifted (popped in layerPost), or null. */
    @Nullable
    private static Identifier shifted;

    private static int colour(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static Identifier hud(String name) {
        return Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/hud/" + name + ".png");
    }

    // ---- The burn rate: measured from the fuel going down ---------------------------------------------------------

    /** The suit the rate is being measured for (its entity id; -1 none), the energy seen last tick and the smoothed
     *  drop per tick. Client-side state: cleared on leaving the world (ids repeat across worlds). */
    private static int rateSuit = -1;
    private static int lastEnergy;
    private static float rate;

    /** Every client tick: follow the energy of the suit this player pilots. */
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof PowerArmorEntity suit) || mc.isPaused()) {
            if (mc.player == null || !(mc.player.getVehicle() instanceof PowerArmorEntity)) {
                rateSuit = -1;
            }
            return;
        }
        int energy = suit.power().getTotalEnergy();
        if (suit.getId() != rateSuit) {
            rateSuit = suit.getId();
            rate = 0F;
        } else {
            // A refuel (energy going up) isn't burning: nothing burnt this tick. One big drop (a rail shot's 100, a flooded
            // firebox losing its fire) counts as one heavy tick, so it lights the embers for a moment, not for seconds.
            float burnt = Mth.clamp(lastEnergy - energy, 0, MAX_BURN_PER_TICK);
            rate += (burnt - rate) * 0.15F; // ~7 ticks to settle: a step or a single shot doesn't flicker it
        }
        lastEnergy = energy;
        // Warning beep every 2 seconds while running on the last few seconds of fuel (with or without the helmet: it's
        // the suit's own alarm, not the visor's).
        if (suit.isPowered() && suit.power().isLowOnFuel() && suit.tickCount % 40 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.7F, 0.5F));
        }
    }

    /** Lit embers, 0..3: none while the suit is off or burns nothing (a free engine). */
    private static int embers(PowerArmorEntity suit) {
        if (!suit.isPowered() || suit.power().hasFreePower() || suit.getId() != rateSuit) {
            return 0;
        }
        return rate >= THREE_EMBERS ? 3 : rate >= TWO_EMBERS ? 2 : 1;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        rateSuit = -1;
        rate = 0F;
        shown = null;
    }

    // ---- For the autopilot's logs ---------------------------------------------------------------------------------

    /** What the HUD drew last frame (dev/autopilot logs), or null when it wasn't shown: critical is the suit's (the
     *  tool's warnings are in toolHud, what its own readout said it drew: ToolHud.render); open: how much of the
     *  screen's height the visor's scan had turned on (VisorPower, 0 = none, 1 = all). */
    public record Shown(float hull, float fuel, String fuelText, int embers, boolean critical, boolean powered,
                        String tool, String toolHud, String compass, float open) {}

    @Nullable
    private static volatile Shown shown;

    @Nullable
    public static Shown shown() {
        return shown;
    }

    private SuitHud() {}
}
