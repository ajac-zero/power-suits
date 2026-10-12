package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A power tool for the suit's right-arm socket (plasma cannon, later drill, chainsaw, EM tool).
 * Only items implementing this fit the socket slot. They do nothing on foot.
 *
 * The suit runs the tool on the server while the pilot holds attack: every time the tool is ready
 * ({@link #cooldownTicks()} after the last use) and there's enough fuel, it calls {@link #use}.
 */
public interface PowerTool {
    /** Fuel energy per use (walking burns 1 per tick, so 300 = 15 s of walking). */
    int energyPerUse();

    /** Ticks between uses while the trigger is held. */
    int cooldownTicks();

    /** Name of this tool's bone in the suit model. The renderer shows it only while this tool is installed. */
    String suitBone();

    /** Server: one use (one shot, one block...). Return false if nothing happened (no fuel is charged). */
    boolean use(PowerArmorEntity suit, Player pilot, ItemStack tool);
}
