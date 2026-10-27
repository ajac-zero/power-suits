package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.item.ToolEffects.HudReadout;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * The pilot's HUD, projected on the helmet's visor (design sec. 6, 2026-10-26, after players called the old fuel gauge
 * bland; laid out after Fallout's power armour HUD): flat amber readouts in two corner panels.
 * <ul>
 *   <li>Left, the suit: its silhouette, HULL (health) and FUEL (time left at walking pace, a bar for how full the
 *   fuel slot is) with three embers for the burn rate: 1 idling, 2 walking, 3 sprinting or a hungry tool at work, dark
 *   when nothing burns (a free engine, or off).</li>
 *   <li>Right, the socket tool: its symbol, one labelled bar (heat, charge, reload) and its ammo on a 3-digit counter,
 *   each tool's own ({@link dev.ajaczero.powerarmor.item.ToolEffects#hud}).</li>
 * </ul>
 * Amber normally; the whole HUD turns red while anything is critical (hull at a quarter, fuel low, the last rounds, an
 * overheated barrel), and fades while the suit has no power. The helmet carries the projector: no helmet, no HUD
 * (vanilla's vehicle hearts then stand in for the hull, as on any mount). With the HUD up those hearts are hidden.
 * <p>
 * It's a "GUI layer" (registered in ClientSetup), drawn every frame after the vanilla hotbar. Everything it shows comes
 * from the suit's synced data: the client never asks the server.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class SuitHud {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_hud");
    private static final Identifier SUIT = hud("suit");
    private static final Identifier EMBER = hud("ember");
    private static final Identifier EMBER_LIT = hud("ember_lit");

    /** The readouts' colour, and their unlit segments about a third as bright (an empty bar must read as empty). */
    private static final int AMBER = 0xFFB13B, AMBER_DIM = 0x5A3C14;
    private static final int RED = 0xFF5555, RED_DIM = 0x5A1E1E;
    /** The panels' dark backing (alpha 110 of 255): the readouts stay legible over a bright sky. */
    private static final int BACKING_ALPHA = 110;
    /** Without power the HUD fades to this much of its brightness (the projector runs on the suit's power). */
    private static final float UNPOWERED = 0.4F;
    /** The hull is critical from a quarter left. */
    private static final float HULL_CRITICAL = 0.25F;
    /** Burn-rate embers: energy per tick at or above which 2 and 3 are lit (idle 0.25, walking 1, sprinting 2 by default;
     *  a flamethrower burns 6). Measured from the fuel actually going, so tools, jumps and air all count. */
    private static final float TWO_EMBERS = 0.6F, THREE_EMBERS = 1.5F;
    private static final int MAX_BURN_PER_TICK = 20;

    /** Panel size and placement (GUI pixels): both panels sit in the bottom corners, the hotbar between them. */
    private static final int MARGIN = 4, PANEL_H = 31, SUIT_W = 128, TOOL_W = 112, TOOL_BAR_SEGMENTS = 10;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        PowerArmorEntity suit = shownFor(mc);
        if (suit == null) {
            shown = null;
            return;
        }
        Player pilot = mc.player;
        Font font = mc.font;
        boolean powered = suit.isPowered();
        boolean blink = (suit.tickCount / 5) % 2 == 0;

        float hull = Mth.clamp(suit.getHealth() / suit.getMaxHealth(), 0F, 1F);
        boolean hullCritical = hull <= HULL_CRITICAL;

        var power = suit.power();
        boolean free = power.hasFreePower() && powered;
        int energy = power.getTotalEnergy();
        Component fuelText = free ? Component.literal("\u221e") // endless
                : suit.getEngine() == null ? Component.translatable("gui.powerarmor.no_engine")
                : power.isFlooded() ? Component.translatable("gui.powerarmor.flooded")
                : energy > 0 ? Component.literal(PowerArmorItem.formatWalkTime(energy))
                : Component.translatable("gui.powerarmor.no_fuel");
        boolean fuelCritical = !free && (suit.getEngine() == null || power.isFlooded() || energy == 0 || power.isLowOnFuel());
        float fuel = power.fuelGauge();
        int embers = embers(suit);

        ItemStack toolStack = suit.getSocketStack();
        HudReadout tool = suit.socket().tool() == null ? null : suit.socketVisuals().effects().hud(suit, pilot);

        boolean critical = hullCritical || fuelCritical || (tool != null && tool.critical());
        float fade = powered ? 1F : UNPOWERED;
        int main = colour(critical ? RED : AMBER, fade);
        int dim = colour(critical ? RED_DIM : AMBER_DIM, fade);
        int backing = ((int) (BACKING_ALPHA * fade)) << 24;

        // ---- left: the suit -------------------------------------------------------------------------------------
        int h = g.guiHeight();
        int x0 = MARGIN, y0 = h - MARGIN - 1 - PANEL_H, x1 = x0 + SUIT_W;
        panel(g, x0, y0, x1, y0 + PANEL_H, backing, main);
        g.blit(RenderPipelines.GUI_TEXTURED, SUIT, x0 + 5, y0 + 5, 0F, 0F, 16, 22, 16, 22, main);
        int lx = x0 + 26, rx = x1 - 4, segments = (rx - lx + 1) / 4;
        g.text(font, Component.translatable("hud.powerarmor.hull"), lx, y0 + 4, main, false);
        String hullText = Math.round(hull * 100) + "%";
        if (!(hullCritical && blink)) {
            g.text(font, hullText, rx - font.width(hullText), y0 + 4, main, false);
        }
        bar(g, lx, y0 + 13, segments, 3, hull, main, dim);
        Component fuelLabel = Component.translatable("hud.powerarmor.fuel");
        g.text(font, fuelLabel, lx, y0 + 18, main, false);
        int ex = lx + font.width(fuelLabel) + 4;
        for (int i = 0; i < 3; i++) {
            boolean lit = i < embers;
            g.blit(RenderPipelines.GUI_TEXTURED, lit ? EMBER_LIT : EMBER, ex + i * 7, y0 + 19, 0F, 0F, 5, 6, 5, 6,
                    lit ? colour(0xFFFFFF, fade) : dim);
        }
        if (!(fuelCritical && blink && powered)) {
            g.text(font, fuelText, rx - font.width(fuelText), y0 + 18, main, false);
        }
        bar(g, lx, y0 + 27, segments, 3, fuel, main, dim);

        // ---- right: the socket tool -------------------------------------------------------------------------------
        // Three columns on the panel's middle line (2026-10-27, user's pick B): the symbol, the bar's label over the
        // bar, and the ammo as a fixed 3-digit counter at twice the font's size, like Fallout's (006; 999 means "999 or
        // more"; a dim --- for a tool without ammo), so the panel never changes size.
        if (tool != null) {
            int tx1 = g.guiWidth() - MARGIN, tx0 = tx1 - TOOL_W;
            panel(g, tx0, y0, tx1, y0 + PANEL_H, backing, main);
            g.blit(RenderPipelines.GUI_TEXTURED, symbol(toolStack), tx0 + 5, y0 + 8, 0F, 0F, 16, 16, 16, 16, main);
            g.text(font, Component.translatable(tool.barLabel()), tx0 + 26, y0 + 8, main, false);
            bar(g, tx0 + 26, y0 + 19, TOOL_BAR_SEGMENTS, 5, tool.bar(), main, dim);
            String count = !tool.hasCount() ? "---" : tool.count() < 0 ? "\u221e" : String.format(Locale.ROOT, "%03d", Math.min(tool.count(), 999));
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(tx1 - 5 - 2 * font.width(count) + 2, y0 + 9); // +2: the font's trailing gap, doubled
            pose.scale(2F, 2F);
            g.text(font, count, 0, 0, tool.hasCount() ? main : dim, false);
            pose.popMatrix();
        }
        shown = new Shown(hull, fuel, fuelText.getString(), embers, critical, powered,
                tool == null ? "" : BuiltInRegistries.ITEM.getKey(toolStack.getItem()).getPath(),
                tool == null ? HudReadout.NO_COUNT : tool.count(), tool == null ? 0F : tool.bar());
    }

    /** The suit this client's player pilots, if the HUD shows: in the suit, helmet on, the GUI not hidden (F1). */
    @Nullable
    private static PowerArmorEntity shownFor(Minecraft mc) {
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()
                || !(mc.player.getVehicle() instanceof PowerArmorEntity suit) || suit.getControllingPassenger() != mc.player) {
            return null;
        }
        return suit.hasHelmet() ? suit : null;
    }

    /** With our HUD up, the hull is on it: vanilla's three rows of vehicle hearts go. */
    @SubscribeEvent
    public static void hideVehicleHearts(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.VEHICLE_HEALTH) && shownFor(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    /** A panel: the dark backing and an L at each corner. */
    private static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int backing, int colour) {
        g.fill(x0, y0, x1, y1, backing);
        int l = 5;
        g.fill(x0, y0, x0 + l, y0 + 1, colour);
        g.fill(x0, y0, x0 + 1, y0 + l, colour);
        g.fill(x1 - l, y0, x1, y0 + 1, colour);
        g.fill(x1 - 1, y0, x1, y0 + l, colour);
        g.fill(x0, y1 - 1, x0 + l, y1, colour);
        g.fill(x0, y1 - l, x0 + 1, y1, colour);
        g.fill(x1 - l, y1 - 1, x1, y1, colour);
        g.fill(x1 - 1, y1 - l, x1, y1, colour);
    }

    /** A segmented bar: {@code n} 3-pixel segments with 1-pixel gaps, the first {@code frac} of them lit. */
    private static void bar(GuiGraphicsExtractor g, int x, int y, int n, int height, float frac, int lit, int unlit) {
        int on = Math.round(n * Mth.clamp(frac, 0F, 1F));
        for (int i = 0; i < n; i++) {
            g.fill(x + i * 4, y, x + i * 4 + 3, y + height, i < on ? lit : unlit);
        }
    }

    private static int colour(int rgb, float alpha) {
        return ((int) (255 * alpha) << 24) | rgb;
    }

    private static Identifier hud(String name) {
        return Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/hud/" + name + ".png");
    }

    /** A tool's symbol, by its item id (tools/make-hud-icons.py draws them): textures/gui/hud/tool/&lt;id&gt;.png. */
    private static Identifier symbol(ItemStack tool) {
        Identifier id = BuiltInRegistries.ITEM.getKey(tool.getItem());
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/gui/hud/tool/" + id.getPath() + ".png");
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

    /** What the HUD drew last frame (dev/autopilot logs), or null when it wasn't shown. */
    public record Shown(float hull, float fuel, String fuelText, int embers, boolean critical, boolean powered,
                        String tool, int count, float bar) {}

    @Nullable
    private static volatile Shown shown;

    @Nullable
    public static Shown shown() {
        return shown;
    }

    private SuitHud() {}
}
