package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The picture half of the suit menu (client only). Slots and shift-click live in PowerArmorMenu;
 * this draws the background, a live preview of the suit, the burn flame, a status display and the
 * Pack up button. Numbers come from the suit's synced entity data, so they update while you watch.
 */
public class PowerArmorScreen extends AbstractContainerScreen<PowerArmorMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/power_armor.png");
    private static final Identifier LIT_PROGRESS = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final int FLAME_X = 109, FLAME_Y = 55; // beside the fuel slot
    private static final int DISPLAY_X = 98, DISPLAY_Y = 18, LINE = 8; // four lines in the status display
    private static final int GREEN = 0xFF7CFF6B, AMBER = 0xFFFFB13B, RED = 0xFFFF5555, DIM = 0xFF5E7A5A;

    public PowerArmorScreen(PowerArmorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // Buttons in container screens don't need a custom packet: vanilla sends the button id to the
        // server, which calls PowerArmorMenu.clickMenuButton.
        this.addRenderableWidget(Button.builder(Component.translatable("gui.powerarmor.pack_up"),
                        b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, PowerArmorMenu.BUTTON_PACK))
                .bounds(this.leftPos + 126, this.topPos + 54, 44, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.powerarmor.pack_up.tooltip")))
                .build());
    }

    // Since 1.21.6 screens don't draw directly: each frame they "extract" what to draw (a list of quads, text, items)
    // into GuiGraphicsExtractor, and Minecraft draws that list afterwards. Hence the extract* names.
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
        } else if (suit.power().getBurnLeft() > 0 && suit.power().getBurnTotal() > 0) {
            int h = Mth.ceil(Mth.clamp(suit.power().getBurnLeft() / (float) suit.power().getBurnTotal(), 0F, 1F) * 13F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS, 14, 14, 0, 14 - h, x + FLAME_X, y + FLAME_Y + 14 - h, 14, h);
        }
        // The suit itself, turning to follow the mouse (like the player in the inventory screen).
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + 8, y + 18, x + 52, y + 70, 15, 0.0F, mouseX, mouseY, suit);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
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
        // Say why a suit stands down: no engine, a flooded firebox, or (parked) no helmet: its mind needs the helmet.
        boolean warn = suit.getEngine() == null || suit.power().isFlooded() || !suit.hasHelmet();
        Component status = Component.translatable(suit.getEngine() == null ? "gui.powerarmor.no_engine"
                : suit.power().isFlooded() ? "gui.powerarmor.flooded"
                : !suit.hasHelmet() ? "gui.powerarmor.no_helmet"
                : suit.isPowered() ? "gui.powerarmor.online" : "gui.powerarmor.offline");
        graphics.text(this.font, status, DISPLAY_X, DISPLAY_Y + 2 * LINE, warn ? AMBER : suit.isPowered() ? GREEN : RED, false);
        // The hidden nugget store (no slot: right-click the suit with nuggets to load it). Dim when empty.
        int nuggets = suit.getStoredNuggets();
        graphics.text(this.font, Component.translatable("gui.powerarmor.ammo", nuggets), DISPLAY_X, DISPLAY_Y + 3 * LINE,
                nuggets > 0 ? GREEN : DIM, false);
    }

    /** Explain the suit's slots while they're empty. */
    private void renderEmptySlotHints(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot == null || this.hoveredSlot.hasItem() || !this.menu.getCarried().isEmpty()) {
            return;
        }
        int index = this.menu.slots.indexOf(this.hoveredSlot);
        String key = index == 0 ? "gui.powerarmor.socket_slot" : index == 1 ? "gui.powerarmor.fuel_slot"
                : index == 2 ? "gui.powerarmor.helmet_slot" : index == 3 ? "gui.powerarmor.engine_slot"
                : index == 4 ? "gui.powerarmor.ammo_slot" : null;
        if (key != null) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable(key),
                    Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
