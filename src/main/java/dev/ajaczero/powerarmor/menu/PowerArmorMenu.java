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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's inventory screen, server side. A "menu" is the logic half of a GUI: which slots exist,
 * what may go in them and what shift-click does. It exists on both sides; the server's copy is the
 * real one and Minecraft keeps the client's copy in sync slot by slot. The picture is drawn by
 * {@code client/PowerArmorScreen}.
 */
public class PowerArmorMenu extends AbstractContainerMenu {
    // Two numberings, don't mix them up: the suit's own slots (PowerArmorEntity.FUEL_SLOT etc.: where an item lives in
    // the suit's inventory, and in saves) and the menu's slots (MENU_*: the order they were added here, which is what
    // clicks and shift-clicks talk about). The menu shows the suit's slots in this order, then the player's inventory.
    public static final int MENU_SOCKET = 0, MENU_FUEL = 1, MENU_HELMET = 2, MENU_ENGINE = 3, MENU_AMMO = 4;
    private static final int SUIT_SLOTS = 5;
    /** Menu slots of the player's inventory (27, top-left first) and hotbar (9). */
    public static final int INV_START = SUIT_SLOTS, HOTBAR_START = INV_START + 27, END = HOTBAR_START + 9;
    /** Where a shift-click from the player's side tries to put an item, first that takes it (the fuel slot last: it takes
     *  the most). */
    private static final int[] SHIFT_CLICK_ORDER = {MENU_SOCKET, MENU_HELMET, MENU_ENGINE, MENU_AMMO, MENU_FUEL};
    /** Button id sent by the "Pack up" button (vanilla's built-in menu button packet carries it). */
    public static final int BUTTON_PACK = 0;

    /** Slot positions in the 176x166 background (top-left of the 16x16 item area): a column ordered like the suit,
     *  helmet on top, the tool socket (arm), the engine (backpack) at the bottom with its fuel beside it
     *  (tools/make-gui.py draws the frames). The ammo sits beside the socket it feeds, on the left (the status display
     *  takes the right; the suit preview got 8 px narrower for it). */
    public static final int SLOT_X = 72, HELMET_Y = 18, SOCKET_Y = 36, ENGINE_Y = 54, FUEL_X = 90, FUEL_Y = 54, AMMO_X = 54;

    @Nullable
    private final PowerArmorEntity suit;

    public PowerArmorMenu(int containerId, Inventory playerInventory, Container suitInventory, @Nullable PowerArmorEntity suit) {
        super(ModMenus.POWER_ARMOR.get(), containerId);
        this.suit = suit;
        checkContainerSize(suitInventory, SUIT_SLOTS);
        suitInventory.startOpen(playerInventory.player);

        // Added in MENU_* order: the n-th addSlot is menu slot n.
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.SOCKET_SLOT, SLOT_X, SOCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof PowerTool;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Any engine's fuel, like a furnace's slot (what the installed engine can't use just sits there), and
                // what burnt fuel leaves (the empty bucket / glass bottle).
                return SuitFuel.fitsFuelSlot(stack);
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.HELMET_SLOT, SLOT_X, HELMET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.SUIT_HELMET.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.ENGINE_SLOT, SLOT_X, ENGINE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof EngineItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(suitInventory, PowerArmorEntity.AMMO_SLOT, AMMO_X, SOCKET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(PowerArmorEntity.AMMO);
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

    /** Shift-click: suit slots -> player; player -> socket (tools), helmet slot (a helmet), engine slot (an engine), ammo
     *  slot (TNT, iron ingots) or fuel slot (fuel). */
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
