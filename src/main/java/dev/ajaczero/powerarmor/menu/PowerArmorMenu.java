package dev.ajaczero.powerarmor.menu;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.SuitFuel;
import dev.ajaczero.powerarmor.item.EngineItem;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.registry.ModItems;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's inventory screen, server side. A "menu" is the logic half of a GUI: which slots exist,
 * what may go in them and what shift-click does. It exists on both sides; the server's copy is the
 * real one and Minecraft keeps the client's copy in sync slot by slot. The picture is drawn by
 * {@code client/PowerArmorScreen}.
 * <p>
 * Since 2026-10-27 (user) it's the player's inventory with the suit in it, laid out like vanilla's inventory screen:
 * the pilot opens it with the inventory key (E) from inside, like a horse's (PowerArmorEntity.openCustomInventoryScreen),
 * and sneak + right-click still opens it on a parked suit (under review). The suit's helmet, engine and fuel take the
 * armour column, its socket tool the slot opposite the engine between the Power and Pack up keys (the tool is the
 * suit's off hand: the swap-hands key moves it, see {@link #clicked}), a status screen the crafting grid's place.
 */
public class PowerArmorMenu extends AbstractContainerMenu {
    // Two numberings, don't mix them up: the suit's own slots (PowerArmorEntity.FUEL_SLOT etc.: where an item lives in
    // the suit's inventory, and in saves) and the menu's slots (MENU_*: the order they were added here, which is what
    // clicks and shift-clicks talk about). The menu shows the suit's slots in this order, then the player's inventory.
    public static final int MENU_SOCKET = 0, MENU_FUEL = 1, MENU_HELMET = 2, MENU_ENGINE = 3;
    private static final int SUIT_SLOTS = 4;
    /** Menu slots of the player's inventory (27, top-left first) and hotbar (9). */
    public static final int INV_START = SUIT_SLOTS, HOTBAR_START = INV_START + 27, END = HOTBAR_START + 9;
    /** Where a shift-click from the player's side tries to put an item, first that takes it (the fuel slot last: it takes
     *  the most). */
    private static final int[] SHIFT_CLICK_ORDER = {MENU_SOCKET, MENU_HELMET, MENU_ENGINE, MENU_FUEL};
    /** Button ids sent by the "Pack up" and power switch buttons (vanilla's built-in menu button packet carries them). */
    public static final int BUTTON_PACK = 0, BUTTON_POWER = 1;

    /** Slot positions in the 176x166 background (top-left of the 16x16 item area), vanilla's inventory screen's: the
     *  armour column (x 8) holds the helmet (head), the engine (the backpack, on the chest's row), then the burn flame
     *  and the fuel; the column right of the preview mirrors it (user, 2026-10-27): the Power key, the socket (opposite
     *  the engine), the Pack up key (opposite the fuel). tools/make-gui.py draws the frames. */
    public static final int COLUMN_X = 8, HELMET_Y = 8, ENGINE_Y = 26, FUEL_Y = 62, SOCKET_X = 77, SOCKET_Y = 26;
    /** Vanilla's empty-helmet-slot outline. */
    private static final Identifier EMPTY_HELMET = Identifier.withDefaultNamespace("container/slot/helmet");

    @Nullable
    private final PowerArmorEntity suit;

    public PowerArmorMenu(int containerId, Inventory playerInventory, Container suitInventory, @Nullable PowerArmorEntity suit) {
        super(ModMenus.POWER_ARMOR.get(), containerId);
        this.suit = suit;
        checkContainerSize(suitInventory, SUIT_SLOTS);
        suitInventory.startOpen(playerInventory.player);

        // Added in MENU_* order: the n-th addSlot is menu slot n.
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.SOCKET_SLOT, SOCKET_X, SOCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof PowerTool;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.FUEL_SLOT, COLUMN_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Any engine's fuel, like a furnace's slot (what the installed engine can't use just sits there), and
                // what burnt fuel leaves (the empty bucket / glass bottle).
                return SuitFuel.fitsFuelSlot(stack);
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.HELMET_SLOT, COLUMN_X, HELMET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.SUIT_HELMET.get());
            }

            @Override
            public Identifier getNoItemIcon() {
                return EMPTY_HELMET;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.ENGINE_SLOT, COLUMN_X, ENGINE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof EngineItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        // Player inventory and hotbar, where vanilla's inventory screen has them.
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

    /** Both sides: is this player inside the suit (its pilot)? Then there's no Pack up: climb out first. */
    public boolean isPiloting(Player player) {
        return this.suit != null && player.getVehicle() == this.suit;
    }

    /** Usable by the pilot from inside, or on a parked suit you're standing next to (not one somebody else is in). */
    @Override
    public boolean stillValid(Player player) {
        return this.suit != null && this.suit.isAlive()
                && (this.isPiloting(player) || !this.suit.isVehicle() && player.distanceToSqr(this.suit) < 8 * 8);
    }

    /** Runs on the server when a screen button is clicked. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.suit == null || !this.stillValid(player)) {
            return false;
        }
        if (id == BUTTON_PACK && !this.isPiloting(player)) {
            return this.suit.packUp(player); // refused while the firebox is flooded
        }
        if (id == BUTTON_POWER) {
            this.suit.flipPowerSwitch();
            return true;
        }
        return false;
    }

    /**
     * The swap-hands key (F) over a slot swaps that slot with the socket, not with the player's real off hand: in this
     * screen the socket is the off hand (vanilla's swap would move the player's hidden off-hand item instead). A tool
     * goes in, whatever was in the socket comes out; anything that isn't a power tool stays put. Runs on both sides (the
     * client predicts the click, the server's result is the real one).
     */
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (input == ContainerInput.SWAP && buttonNum == Inventory.SLOT_OFFHAND && slotIndex >= 0 && slotIndex < this.slots.size()) {
            Slot target = this.slots.get(slotIndex);
            Slot socket = this.slots.get(MENU_SOCKET);
            if (target != socket) {
                swap(target, socket, player);
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    /** Swap two slots' items if each may go in the other (an empty side is fine; nothing happens with both empty). */
    private static void swap(Slot a, Slot b, Player player) {
        ItemStack x = a.getItem(), y = b.getItem();
        if (x.isEmpty() && y.isEmpty() || !a.mayPickup(player) || !b.mayPickup(player)) {
            return;
        }
        boolean xFits = x.isEmpty() || b.mayPlace(x) && x.getCount() <= b.getMaxStackSize(x);
        boolean yFits = y.isEmpty() || a.mayPlace(y) && y.getCount() <= a.getMaxStackSize(y);
        if (xFits && yFits) {
            a.setByPlayer(y.copy());
            b.setByPlayer(x.copy());
        }
    }

    /** The suit's menu slot a shift-click from the player's side sends this item to, or -1 (then it hops between
     *  inventory and hotbar, as in vanilla). The empty bucket and glass bottle burnt fuel leaves behind may sit in the
     *  fuel slot but are never shift-clicked into it. */
    private int shiftClickTarget(ItemStack stack) {
        for (int target : SHIFT_CLICK_ORDER) {
            if (target == MENU_FUEL && (stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE))) {
                continue;
            }
            if (this.slots.get(target).mayPlace(stack)) {
                return target;
            }
        }
        return -1;
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
        } else {
            int target = this.shiftClickTarget(stack);
            if (target >= 0) {
                if (!this.moveItemStackTo(stack, target, target + 1, false)) {
                    return ItemStack.EMPTY; // that slot is full: nothing moves (like a furnace)
                }
            } else if (index < HOTBAR_START) {
                if (!this.moveItemStackTo(stack, HOTBAR_START, END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
