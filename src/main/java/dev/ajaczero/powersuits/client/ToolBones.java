package dev.ajaczero.powersuits.client;

import dev.ajaczero.powersuits.item.PowerTool;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Every power tool's bone in the suit model (and in the cockpit viewmodel): the renderers show the installed tool's bone
 * and hide the others. Collected from the item registry (every item that is a PowerTool names its bone), so a new tool
 * needs no list to be kept up to date here.
 */
final class ToolBones {
    private static List<String> all;

    /** All tool bones; worked out on first use (the registry is complete by the time anything is drawn). */
    static List<String> all() {
        if (all == null) {
            all = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof PowerTool)
                    .map(item -> ((PowerTool) item).suitBone()).distinct().toList();
        }
        return all;
    }

    private ToolBones() {}
}
