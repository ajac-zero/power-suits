package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.HudAlert;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.client.hud.HudView;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The picture half of the suit menu (client only). Slots and shift-click live in PowerArmorMenu; this draws the
 * background, a live preview of the suit, the burn flame, the status screen and the Power and Pack up keys. Numbers
 * come from the suit's synced entity data, so they update while you watch.
 * <p>
 * Since 2026-10-27 (user) it looks like vanilla's inventory screen with the suit in it (tools/make-gui.py): the suit's
 * helmet, engine and fuel down the armour column; opposite them, right of the preview, the Power key, the socket slot
 * and the Pack up key ({@link SuitKey}, slot-sized); a terminal screen where the crafting grid was. Inside the suit this
 * is your inventory (E).
 * <p>
 * The screen reads like the visor (user's pick T2, 2026-10-27), in the visor's colour: the suit's name, HULL and FUEL
 * with the HUD's segmented bars, and the status line. It's the one place that says why a suit is dark (the visor shows
 * nothing without power): NO FUEL, NO ENGINE, FLOODED (critical: inverted, blinking), POWER OFF, NO HELMET (inverted,
 * steady), NO POWER (lightning), or ONLINE. Alerts look like the HUD's ({@link HudView#label}).
 */
public class PowerArmorScreen extends AbstractContainerScreen<PowerArmorMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/power_armor.png");
    private static final Identifier LIT_PROGRESS = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier POWER_SYMBOL = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "container/power_armor/power");
    private static final Identifier PACK_SYMBOL = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "container/power_armor/pack");
    /** The flame between the engine and fuel slots; the preview window (vanilla's player box). */
    private static final int FLAME_X = 9, FLAME_Y = 45;
    private static final int PREVIEW_X0 = 26, PREVIEW_Y0 = 8, PREVIEW_X1 = 75, PREVIEW_Y1 = 78, PREVIEW_SCALE = 18;
    /** The keys, in the column right of the preview: Power opposite the helmet, Pack up opposite the fuel. */
    private static final int KEY_X = 76, POWER_Y = 7, PACK_Y = 61;
    /** The status screen's text area (the screen itself is x 97..169, y 7..79 in the background): left and right edges
     *  (16 bar segments wide), the rows. */
    private static final int TEXT_X0 = 101, TEXT_X1 = 165, TITLE_Y = 11, RULE1_Y = 20, HULL_Y = 22, HULL_BAR_Y = 31,
            FUEL_Y = 37, FUEL_BAR_Y = 46, RULE2_Y = 54, STATUS_Y = 58;
    private static final int SEGMENTS = (TEXT_X1 - TEXT_X0 + 1) / 4;
    /** The suit's name and the rules, as fractions of the visor colour: the name a caption, the rules faint. */
    private static final float TITLE = 0.55F, RULE = 0.3F;

    private SuitKey power;
    private boolean powerShown;

    public PowerArmorScreen(PowerArmorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // Buttons in container screens don't need a custom packet: vanilla sends the button id to the
        // server, which calls PowerArmorMenu.clickMenuButton.
        this.power = this.addRenderableWidget(new SuitKey(this.leftPos + KEY_X, this.topPos + POWER_Y, POWER_SYMBOL,
                powerName(true), () -> switchedOn(this.menu.getSuit()),
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, PowerArmorMenu.BUTTON_POWER)));
        this.powerShown = !this.power.isDown();
        SuitKey pack = this.addRenderableWidget(new SuitKey(this.leftPos + KEY_X, this.topPos + PACK_Y, PACK_SYMBOL,
                Component.translatable("gui.powerarmor.pack_up"), null,
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, PowerArmorMenu.BUTTON_PACK)));
        // Packing up needs you outside: from inside the key is there but greyed out, and says why.
        boolean outside = !this.menu.isPiloting(this.minecraft.player);
        pack.active = outside;
        pack.setTooltip(tooltip(Component.translatable("gui.powerarmor.pack_up"),
                Component.translatable(outside ? "gui.powerarmor.pack_up.tooltip" : "gui.powerarmor.pack_up.inside")));
    }

    private static boolean switchedOn(PowerArmorEntity suit) {
        return suit == null || suit.power().isSwitchedOn();
    }

    /** The key's name says its state, for the tooltip and the narrator ("Power: ON"). */
    private static Component powerName(boolean on) {
        return Component.translatable("gui.powerarmor.power", Component.translatable(on ? "gui.powerarmor.power.on" : "gui.powerarmor.power.off"));
    }

    /** A key's tooltip: its name, then what it does in grey (a key has a symbol, no caption). */
    private static Tooltip tooltip(Component name, Component hint) {
        return Tooltip.create(Component.empty().append(name).append("\n").append(hint.copy().withStyle(ChatFormatting.GRAY)));
    }

    // Since 1.21.6 screens don't draw directly: each frame they "extract" what to draw (a list of quads, text, items)
    // into GuiGraphicsExtractor, and Minecraft draws that list afterwards. Hence the extract* names.
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // the switch flips on the server (the key, H, a running-dry suit doesn't touch it): name the key by the synced state
        boolean on = this.power.isDown();
        if (on != this.powerShown) {
            this.powerShown = on;
            this.power.setMessage(powerName(on));
            this.power.setTooltip(tooltip(powerName(on), Component.translatable("gui.powerarmor.power.tooltip")));
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick); // background, slots, labels, item tooltips
        this.renderEmptySlotHints(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos, y = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0F, 0F, this.imageWidth, this.imageHeight, 256, 256);
        PowerArmorEntity suit = this.menu.getSuit();
        if (suit == null) {
            return;
        }
        // Burning flame, shrinking as the current fuel item burns down (same sprite as the furnace).
        if (suit.power().hasFreePower() && suit.isPowered()) {
            // a wet tidal engine or a star reactor: burning nothing, never running down
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS, 14, 14, 0, 0, x + FLAME_X, y + FLAME_Y, 14, 14);
        } else if (suit.isPowered() && suit.power().getBurnLeft() > 0 && suit.power().getBurnTotal() > 0) {
            int h = Mth.ceil(Mth.clamp(suit.power().getBurnLeft() / (float) suit.power().getBurnTotal(), 0F, 1F) * 13F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS, 14, 14, 0, 14 - h, x + FLAME_X, y + FLAME_Y + 14 - h, 14, h);
        }
        // The suit itself, turning to follow the mouse (like the player in the inventory screen). The whole of it, also
        // from inside: in first person the renderer would leave out all but the legs (the cockpit view).
        PowerArmorRenderer.inventoryPreview = true;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + PREVIEW_X0, y + PREVIEW_Y0, x + PREVIEW_X1, y + PREVIEW_Y1,
                    PREVIEW_SCALE, 0.0625F, mouseX, mouseY, suit);
        } finally {
            PowerArmorRenderer.inventoryPreview = false;
        }
    }

    /** The status screen (labels are drawn relative to the background's corner). No vanilla title or "Inventory"
     *  label: the suit's name is the screen's first line, and vanilla's inventory screen has no "Inventory" either. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        PowerArmorEntity suit = this.menu.getSuit();
        if (suit == null) {
            return;
        }
        int rgb = VisorConfig.colour();
        int main = 0xFF000000 | rgb;
        int unlit = 0xFF000000 | VisorConfig.dim(rgb, HudView.DIM);
        int rule = 0xFF000000 | VisorConfig.dim(rgb, RULE);
        boolean blink = (suit.tickCount / 5) % 2 == 0;
        var power = suit.power();

        // the suit's name (a renamed suit shows its own), in capitals like the rest of the terminal
        String name = this.font.plainSubstrByWidth(this.title.getString().toUpperCase(Locale.ROOT), TEXT_X1 - TEXT_X0);
        graphics.text(this.font, name, TEXT_X0, TITLE_Y, 0xFF000000 | VisorConfig.dim(rgb, TITLE), false);
        graphics.fill(TEXT_X0, RULE1_Y, TEXT_X1, RULE1_Y + 1, rule);

        // HULL: like the HUD's (a warning from half, critical in the last 15%)
        float hull = Mth.clamp(suit.getHealth() / suit.getMaxHealth(), 0F, 1F);
        HudView.label(graphics, this.font, Component.translatable("hud.powerarmor.hull"), TEXT_X0, HULL_Y, rgb, HudAlert.ofLeft(hull), blink);
        right(graphics, Component.translatable("hud.powerarmor.percent", Math.round(hull * 100)), HULL_Y, main);
        HudView.bar(graphics, TEXT_X0, HULL_BAR_Y, SEGMENTS, 3, hull, main, unlit);

        // FUEL: time left at walking pace (endless on a wet tidal engine or a star reactor), the bar how full the slot is
        int energy = power.getTotalEnergy();
        boolean free = power.hasFreePower();
        boolean fuelCritical = !free && (suit.getEngine() == null || power.isFlooded() || energy == 0 || power.isLowOnFuel());
        HudView.label(graphics, this.font, Component.translatable("hud.powerarmor.fuel"), TEXT_X0, FUEL_Y, rgb, HudView.critical(fuelCritical), blink);
        right(graphics, free ? Component.translatable("hud.powerarmor.endless") : Component.literal(PowerArmorItem.formatWalkTime(energy)), FUEL_Y, main);
        HudView.bar(graphics, TEXT_X0, FUEL_BAR_Y, SEGMENTS, 3, power.fuelGauge(), main, unlit);
        graphics.fill(TEXT_X0, RULE2_Y, TEXT_X1, RULE2_Y + 1, rule);

        // Why a suit stands down. Something to fix (no fuel, no engine, flooded, lightning) blinks; a choice (switched
        // off, a parked suit without its helmet: its mind needs the helmet) holds steady.
        boolean off = !power.isSwitchedOn();
        String status;
        HudAlert alert;
        if (off) {
            status = "gui.powerarmor.switched_off"; alert = HudAlert.WARNING;
        } else if (suit.getEngine() == null) {
            status = "gui.powerarmor.no_engine"; alert = HudAlert.CRITICAL;
        } else if (power.isFlooded()) {
            status = "gui.powerarmor.flooded"; alert = HudAlert.CRITICAL;
        } else if (!suit.hasHelmet() && !suit.isVehicle()) {
            status = "gui.powerarmor.no_helmet"; alert = HudAlert.WARNING;
        } else if (suit.isPowered()) {
            status = "gui.powerarmor.online"; alert = HudAlert.NONE;
        } else if (energy == 0 && !free) {
            status = "gui.powerarmor.no_fuel"; alert = HudAlert.CRITICAL;
        } else {
            status = "gui.powerarmor.offline"; alert = HudAlert.CRITICAL;
        }
        // an inverted label needs a pixel of block round it: nudge it in off the left edge
        int x = alert == HudAlert.NONE ? TEXT_X0 : TEXT_X0 + 1;
        HudView.label(graphics, this.font, Component.translatable(status), x, STATUS_Y, rgb, alert, blink);
    }

    /** Text flush with the right end of the bars (font.width counts the gap after the last glyph). */
    private void right(GuiGraphicsExtractor graphics, Component text, int y, int colour) {
        graphics.text(this.font, text, TEXT_X1 + 1 - this.font.width(text), y, colour, false);
    }

    /** Explain the suit's slots while they're empty. */
    private void renderEmptySlotHints(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot == null || this.hoveredSlot.hasItem() || !this.menu.getCarried().isEmpty()) {
            return;
        }
        int index = this.menu.slots.indexOf(this.hoveredSlot);
        String key = index == PowerArmorMenu.MENU_SOCKET ? "gui.powerarmor.socket_slot"
                : index == PowerArmorMenu.MENU_FUEL ? "gui.powerarmor.fuel_slot"
                : index == PowerArmorMenu.MENU_HELMET ? "gui.powerarmor.helmet_slot"
                : index == PowerArmorMenu.MENU_ENGINE ? "gui.powerarmor.engine_slot" : null;
        if (key != null) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable(key),
                    Component.translatable(key + ".hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
