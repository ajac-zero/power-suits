package dev.ajaczero.powersuits.client.hud;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Which socket tool draws which visor HUD (client only), filled in once at startup by ClientSetup: like vanilla's table
 * of entity renderers, a client-side registry. The server never loads it, so the tools' items (shared code) stay free
 * of drawing code. A tool without an entry shows nothing on the visor.
 */
public final class ToolHuds {
    private static final Map<Item, ToolHud> HUDS = new IdentityHashMap<>();

    public static void register(Item tool, ToolHud hud) {
        HUDS.put(tool, hud);
    }

    /** The HUD of the tool {@code item}, or null for a tool with none. */
    @Nullable
    public static ToolHud get(Item item) {
        return HUDS.get(item);
    }

    private ToolHuds() {}
}
