package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
 * background, a live preview of the suit, the burn flame, a status display, the power switch and (from outside) the
 * Pack up button. Numbers come from the suit's synced entity data, so they update while you watch.
 * <p>
 * Since 2026-10-27 (user) it looks like vanilla's inventory screen with the suit in it (tools/make-gui.py): the
 * suit's slots down the armour column and in the off-hand slot, the suit in the preview window, the status display and
 * buttons where the crafting grid was. Inside the suit this is your inventory (E).
 */
public class PowerArmorScreen extends AbstractContainerScreen<PowerArmorMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/power_armor.png");
    private static final Identifier LIT_PROGRESS = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    /** The flame between the engine and fuel slots; the preview window (vanilla's player box); the status display
     *  (three lines) and the two buttons under it. */
    private static final int FLAME_X = 9, FLAME_Y = 45;
    private static final int PREVIEW_X0 = 26, PREVIEW_Y0 = 8, PREVIEW_X1 = 75, PREVIEW_Y1 = 78, PREVIEW_SCALE = 18;
    private static final int DISPLAY_X = 100, DISPLAY_Y = 20, LINE = 8;
    private static final int BUTTON_X = 97, POWER_Y = 48, PACK_Y = 64, BUTTON_W = 72, BUTTON_H = 15;
    private static final int GREEN = 0xFF7CFF6B, AMBER = 0xFFFFB13B, RED = 0xFFFF5555;

    private Button power;

    public PowerArmorScreen(PowerArmorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelX = 97; // where vanilla's inventory screen says "Crafting"
    }

    @Override
    protected void init() {
        super.init();
        // Buttons in container screens don't need a custom packet: vanilla sends the button id to the
        // server, which calls PowerArmorMenu.clickMenuButton.
        this.power = this.addRenderableWidget(Button.builder(powerLabel(this.menu.getSuit()),
                        b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, PowerArmorMenu.BUTTON_POWER))
                .bounds(this.leftPos + BUTTON_X, this.topPos + POWER_Y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("gui.powerarmor.power.tooltip")))
                .build());
        if (!this.menu.isPiloting(this.minecraft.player)) {
            // Packing up needs you outside: from inside there's no button (climb out first).
            this.addRenderableWidget(Button.builder(Component.translatable("gui.powerarmor.pack_up"),
                            b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, PowerArmorMenu.BUTTON_PACK))
                    .bounds(this.leftPos + BUTTON_X, this.topPos + PACK_Y, BUTTON_W, BUTTON_H)
                    .tooltip(Tooltip.create(Component.translatable("gui.powerarmor.pack_up.tooltip")))
                    .build());
        }
    }

    /** The switch's caption shows its state, like vanilla's option buttons ("Power: ON"). */
    private static Component powerLabel(PowerArmorEntity suit) {
        boolean on = suit == null || suit.power().isSwitchedOn();
        return Component.translatable("gui.powerarmor.power", Component.translatable(on ? "gui.powerarmor.power.on" : "gui.powerarmor.power.off"));
    }

    // Since 1.21.6 screens don't draw directly: each frame they "extract" what to draw (a list of quads, text, items)
    // into GuiGraphicsExtractor, and Minecraft draws that list afterwards. Hence the extract* names.
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.power.setMessage(powerLabel(this.menu.getSuit())); // the switch flips on the server: follow the synced state
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

    /** The suit's name over the status display (vanilla's "Crafting" spot); no "Inventory" label (vanilla's inventory
     *  screen has none either: the grid starts right under the preview). */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        PowerArmorEntity suit = this.menu.getSuit();
        if (suit == null) {
            return;
        }
        int hull = Mth.ceil(suit.getHealth());
        int max = Mth.ceil(suit.getMaxHealth());
        int energy = suit.power().getTotalEnergy();
        int hullColour = hull < max / 4 ? RED : hull < max / 2 ? AMBER : GREEN;
        int fuelColour = energy == 0 ? RED : suit.power().isLowOnFuel() ? AMBER : GREEN;
        graphics.text(this.font, Component.translatable("gui.powerarmor.hull", hull, max), DISPLAY_X, DISPLAY_Y, hullColour, false);
        // A wet tidal engine or a star reactor burns nothing: its range is endless.
        Component range = suit.power().hasFreePower() ? Component.translatable("gui.powerarmor.range_free")
                : Component.translatable("gui.powerarmor.range", PowerArmorItem.formatWalkTime(energy));
        graphics.text(this.font, range, DISPLAY_X, DISPLAY_Y + LINE, suit.power().hasFreePower() ? GREEN : fuelColour, false);
        // Say why a suit stands down: switched off, no engine, a flooded firebox, (parked) no helmet: its mind needs
        // the helmet, or nothing left to burn. This is the only place that says it: the visor goes completely dark
        // without power (SuitHud, user 2026-10-27), so a pilot checks here. NO POWER is left for the rest (lightning).
        boolean off = !suit.power().isSwitchedOn();
        boolean warn = off || suit.getEngine() == null || suit.power().isFlooded() || !suit.hasHelmet();
        Component status = Component.translatable(off ? "gui.powerarmor.switched_off"
                : suit.getEngine() == null ? "gui.powerarmor.no_engine"
                : suit.power().isFlooded() ? "gui.powerarmor.flooded"
                : !suit.hasHelmet() && !suit.isVehicle() ? "gui.powerarmor.no_helmet"
                : suit.isPowered() ? "gui.powerarmor.online"
                : energy == 0 && !suit.power().hasFreePower() ? "gui.powerarmor.no_fuel" : "gui.powerarmor.offline");
        graphics.text(this.font, status, DISPLAY_X, DISPLAY_Y + 2 * LINE, warn ? AMBER : suit.isPowered() ? GREEN : RED, false);
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
