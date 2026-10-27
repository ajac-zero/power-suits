package dev.ajaczero.powerarmor.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One socket tool's own readout on the visor (2026-10-27, user: "each power tool could bring its own UI"): drawn
 * anywhere on the glass (the crosshair, a corner, over the world), in the visor's colour, red only for its own
 * warnings. A tool with nothing to say has none (the drill, the chainsaw, the flamethrower): the glass stays clear.
 * <p>
 * Client only. The tool's item is shared code, which a dedicated server loads too and which may never name the
 * client's drawing classes, so the drawing lives here and {@link ToolHuds} pairs it with the item at startup, the way
 * Minecraft pairs entity types with their renderers.
 */
@FunctionalInterface
public interface ToolHud {
    /**
     * Draws this frame's readout (inside the visor's scan while it warms up or shuts down: SuitHud clips it). Returns
     * what it showed in a few words, for the autopilot's log ("" when it drew nothing).
     */
    String render(GuiGraphicsExtractor g, HudView view);
}
