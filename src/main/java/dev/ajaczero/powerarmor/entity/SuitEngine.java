package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.EngineItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's engine (design sec. 4b, 2026-10-21): the backpack is a swappable power source. Which one is installed
 * decides what the fuel slot burns, where the suit runs and what its power costs.
 * <ul>
 * <li>{@link #FIREBOX}: the standard one, burns furnace fuels and floods (dies) when water covers it.</li>
 * <li>{@link #TIDAL}: free power while wet (backpack under water, or rain); on land it drinks water from the slot.</li>
 * <li>{@link #REACTOR}: a Nether Star in the fuel slot powers everything for free, forever.</li>
 * </ul>
 * The ordinal is synced to clients (PowerArmorEntity's ENGINE) and must not be reordered.
 */
public enum SuitEngine {
    FIREBOX,
    TIDAL,
    REACTOR;

    /** The engine an item in the engine slot provides, or null (empty slot / anything else). */
    public static @Nullable SuitEngine of(ItemStack stack) {
        return stack.getItem() instanceof EngineItem engine ? engine.engine() : null;
    }

    /** Synced id: -1 = no engine. */
    public static @Nullable SuitEngine byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
