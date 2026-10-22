package dev.ajaczero.powerarmor.menu;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.SuitFuel;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's inventory screen, server side. A "menu" is the logic half of a GUI: which slots exist,
 * what may go in them and what shift-click does. It exists on both sides; the server's copy is the
 * real one and Minecraft keeps the client's copy in sync slot by slot. The picture is drawn by
 * {@code client/PowerArmorScreen}.
 */
public class PowerArmorMenu extends AbstractContainerMenu {
    public static final int FUEL_SLOT = PowerArmorEntity.FUEL_SLOT;
    public static final int SOCKET_SLOT = PowerArmorEntity.SOCKET_SLOT;
    public static final int HELMET_SLOT = PowerArmorEntity.HELMET_SLOT;
    public static final int ENGINE_SLOT = PowerArmorEntity.ENGINE_SLOT;
    /** Menu slot order: socket (0), fuel (1), helmet (2), engine (3), then the player's inventory. */
    private static final int SUIT_SLOTS = 4;
    private static final int INV_START = SUIT_SLOTS, HOTBAR_START = INV_START + 27, END = HOTBAR_START + 9;
    /** Button id sent by the "Pack up" button (vanilla's built-in menu button packet carries it). */
    public static final int BUTTON_PACK = 0;

    /** Slot positions in the 176x166 background (top-left of the 16x16 item area): a column ordered like the suit,
     *  helmet on top, the tool socket (arm), the engine (backpack) at the bottom with its fuel beside it
     *  (tools/make-gui.py draws the frames). */
    public static final int SLOT_X = 72, HELMET_Y = 18, SOCKET_Y = 36, ENGINE_Y = 54, FUEL_X = 90, FUEL_Y = 54;

    @Nullable
    private final PowerArmorEntity suit;

    public PowerArmorMenu(int containerId, Inventory playerInventory, Container suitInventory, @Nullable PowerArmorEntity suit) {
        super(ModMenus.POWER_ARMOR.get(), containerId);
        this.suit = suit;
        checkContainerSize(suitInventory, SUIT_SLOTS);
        suitInventory.startOpen(playerInventory.player);

        this.addSlot(new Slot(suitInventory, SOCKET_SLOT, SLOT_X, SOCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof PowerTool;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, FUEL_SLOT, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Any engine's fuel, like a furnace's slot (what the installed engine can't use just sits there), and
                // what burnt fuel leaves (the empty bucket / glass bottle).
                return SuitFuel.fitsFuelSlot(stack);
            }
        });
        this.addSlot(new Slot(suitInventory, HELMET_SLOT, SLOT_X, HELMET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, ENGINE_SLOT, SLOT_X, ENGINE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof dev.ajaczero.powerarmor.item.EngineItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        // Player inventory and hotbar, same place as in a furnace.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client side: the server told us which suit (entity id). Slot contents arrive separately. */
    public static PowerArmorMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        PowerArmorEntity suit = playerInventory.player.level().getEntity(buf.readVarInt()) instanceof PowerArmorEntity s ? s : null;
        return new PowerArmorMenu(containerId, playerInventory, new SimpleContainer(SUIT_SLOTS), suit);
    }

    @Nullable
    public PowerArmorEntity getSuit() {
        return this.suit;
    }

    /** Only usable on a parked suit you're standing next to. */
    @Override
    public boolean stillValid(Player player) {
        return this.suit != null && this.suit.isAlive() && !this.suit.isVehicle() && player.distanceToSqr(this.suit) < 8 * 8;
    }

    /** Runs on the server when a screen button is clicked. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_PACK && this.suit != null && this.stillValid(player)) {
            return this.suit.packUp(player); // refused while the firebox is flooded
        }
        return false;
    }

    /** Shift-click: suit slots -> player; player -> socket (tools), helmet slot (a helmet), engine slot (an engine) or
     *  fuel slot (fuel). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SUIT_SLOTS) {
            if (!this.moveItemStackTo(stack, INV_START, END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (this.slots.get(0).mayPlace(stack)) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.slots.get(2).mayPlace(stack)) {
            if (!this.moveItemStackTo(stack, 2, 3, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.slots.get(3).mayPlace(stack)) {
            if (!this.moveItemStackTo(stack, 3, 4, false)) {
                return ItemStack.EMPTY;
            }
        } else if (SuitFuel.fitsFuelSlot(stack) && !stack.is(net.minecraft.world.item.Items.BUCKET)
                && !stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            if (!this.moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_START) {
            if (!this.moveItemStackTo(stack, HOTBAR_START, END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
