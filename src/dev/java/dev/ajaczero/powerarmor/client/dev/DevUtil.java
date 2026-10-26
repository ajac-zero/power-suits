package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Small helpers the dev scripts share (things 1.21.1 had as plain fields). */
final class DevUtil {
    /** F1: hide or show the HUD (the hand pass, i.e. the cockpit viewmodel, is skipped too while hidden). */
    static void hideGui(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    /** /time set: since 26.1 the time of day is the dimension's default clock. */
    static void setTime(ServerLevel level, long ticks) {
        level.dimensionType().defaultClock().ifPresent(clock -> level.clockManager().setTotalTicks(clock, ticks));
    }

    /** Suits come without an engine: give a scene's suit a firebox (if it has no engine yet) before fuelling it. */
    static void firebox(PowerArmorEntity suit) {
        if (suit.getInventory().getItem(PowerArmorEntity.ENGINE_SLOT).isEmpty()) {
            suit.getInventory().setItem(PowerArmorEntity.ENGINE_SLOT,
                    new ItemStack(ModItems.FIREBOX.get()));
        }
    }

    private DevUtil() {}
}
