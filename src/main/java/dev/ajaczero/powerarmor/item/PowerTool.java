package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * A power tool for the suit's right-arm socket (plasma cannon, EM projector, drill, chainsaw).
 * Only items implementing this fit the socket slot. They do nothing on foot.
 *
 * Two kinds, both run by the suit on the server while the pilot holds attack:
 *  - repeating (cannon): every time the tool is ready ({@link #cooldownTicks()} after the last use) and
 *    there's fuel for {@link #energyPerUse()}, the suit calls {@link #use};
 *  - continuous (EM projector, {@link #isContinuous()}): {@link #holdTick} every tick while held and
 *    {@link #release} when the button goes up or something interrupts (power, pilot gone, tool removed).
 *    These pay their own fuel through {@link PowerArmorEntity#tryUseEnergy}.
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

    /** Continuous tools are driven by holdTick / release instead of use. */
    default boolean isContinuous() {
        return false;
    }

    /** Server, continuous tools: every tick while attack is held and the suit can operate. */
    default void holdTick(PowerArmorEntity suit, Player pilot, ItemStack tool) {
    }

    /** Server, continuous tools: attack released ({@code interrupted} false), or the suit lost power, the
     *  pilot left or the tool was taken out ({@code interrupted} true, {@code pilot} may be null). */
    default void release(PowerArmorEntity suit, @Nullable Player pilot, boolean interrupted) {
    }

    /** Server: every tick while installed, held or not (e.g. to follow things the tool has thrown). */
    default void serverTick(PowerArmorEntity suit) {
    }

    /** Continuous tools: a looping animation of the tool's own bones (drill spin, chain) played while it runs,
     *  or null. It must only key the tool's bones: GeckoLib lets later controllers override whole bones. */
    @Nullable
    default RawAnimation runAnimation() {
        return null;
    }

    /** Continuous tools: a looping sound played (client side, see SuitSounds) while the tool runs, or null. */
    @Nullable
    default net.minecraft.sounds.SoundEvent runSound() {
        return null;
    }

    /** Server: keep the run animation going after the trigger is released (a tree still coming down). */
    default boolean keepsRunning(PowerArmorEntity suit) {
        return false;
    }
}
