package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The picture half of the suit menu (client only). Slots and shift-click live in PowerArmorMenu;
 * this draws the background, a live preview of the suit, the burn flame, a status display and the
 * Pack up button. Numbers come from the suit's synced entity data, so they update while you watch.
 */
public class PowerArmorScreen extends AbstractContainerScreen<PowerArmorMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/gui/power_armor.png");
    private static final ResourceLocation LIT_PROGRESS = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final int FLAME_X = 73, FLAME_Y = 35;
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
                .bounds(this.leftPos + 94, this.topPos + 53, 76, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.powerarmor.pack_up.tooltip")))
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.renderEmptySlotHints(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        graphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight);
        PowerArmorEntity suit = this.menu.getSuit();
        if (suit == null) {
            return;
        }
        // Burning flame, shrinking as the current fuel item burns down (same sprite as the furnace).
        if (suit.getBurnLeft() > 0 && suit.getBurnTotal() > 0) {
            int h = Mth.ceil(Mth.clamp(suit.getBurnLeft() / (float) suit.getBurnTotal(), 0F, 1F) * 13F) + 1;
            graphics.blitSprite(LIT_PROGRESS, 14, 14, 0, 14 - h, x + FLAME_X, y + FLAME_Y + 14 - h, 14, h);
        }
        // The suit itself, turning to follow the mouse (like the player in the inventory screen).
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 8, y + 18, x + 60, y + 70, 15, 0.0F, mouseX, mouseY, suit);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        PowerArmorEntity suit = this.menu.getSuit();
        if (suit == null) {
            return;
        }
        int hull = Mth.ceil(suit.getHealth());
        int max = Mth.ceil(suit.getMaxHealth());
        int energy = suit.getTotalEnergy();
        int hullColour = hull < max / 4 ? RED : hull < max / 2 ? AMBER : GREEN;
        int fuelColour = energy == 0 ? RED : suit.isLowOnFuel() ? AMBER : GREEN;
        graphics.drawString(this.font, Component.translatable("gui.powerarmor.hull", hull, max), DISPLAY_X, DISPLAY_Y, hullColour, false);
        graphics.drawString(this.font, Component.translatable("gui.powerarmor.range", PowerArmorItem.formatWalkTime(energy)), DISPLAY_X, DISPLAY_Y + LINE, fuelColour, false);
        Component status = Component.translatable(suit.isPowered() ? "gui.powerarmor.online" : "gui.powerarmor.offline");
        graphics.drawString(this.font, status, DISPLAY_X, DISPLAY_Y + 2 * LINE, suit.isPowered() ? GREEN : RED, false);
        // The hidden nugget store (no slot: right-click the suit with nuggets to load it). Dim when empty.
        int nuggets = suit.getStoredNuggets();
        graphics.drawString(this.font, Component.translatable("gui.powerarmor.ammo", nuggets), DISPLAY_X, DISPLAY_Y + 3 * LINE,
                nuggets > 0 ? GREEN : DIM, false);
    }

    /** Explain the two special slots while they're empty. */
    private void renderEmptySlotHints(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot == null || this.hoveredSlot.hasItem() || !this.menu.getCarried().isEmpty()) {
            return;
        }
        int index = this.menu.slots.indexOf(this.hoveredSlot);
        String key = index == 0 ? "gui.powerarmor.socket_slot" : index == 1 ? "gui.powerarmor.fuel_slot" : null;
        if (key != null) {
            graphics.renderComponentTooltip(this.font, List.of(Component.translatable(key),
                    Component.translatable(key + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
