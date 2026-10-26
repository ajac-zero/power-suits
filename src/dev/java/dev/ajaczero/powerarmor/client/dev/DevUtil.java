package dev.ajaczero.powerarmor.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;

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
    static void firebox(dev.ajaczero.powerarmor.entity.PowerArmorEntity suit) {
        if (suit.getInventory().getItem(dev.ajaczero.powerarmor.entity.PowerArmorEntity.ENGINE_SLOT).isEmpty()) {
            suit.getInventory().setItem(dev.ajaczero.powerarmor.entity.PowerArmorEntity.ENGINE_SLOT,
                    new net.minecraft.world.item.ItemStack(dev.ajaczero.powerarmor.registry.ModItems.FIREBOX.get()));
        }
    }

    private DevUtil() {}
}
