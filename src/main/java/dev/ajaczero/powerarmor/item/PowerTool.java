package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animation.RawAnimation;

/**
 * A power tool for the suit's left-arm socket (chaingun, EM projector, drill, chainsaw, flamethrower, TNT launcher, rail
 * cannon).
 * Only items implementing this fit the socket slot. They do nothing on foot.
 *
 * Two kinds, both run by the suit on the server while its operator holds the trigger: the pilot (right click; see
 * ToolInput), or the suit itself while its machine mind drives it (PowerArmorEntity#toolOperator). The operator is
 * whoever aims (eye + view direction), pays the ammo and gets the credit; block breaking goes through
 * PowerArmorEntity#blockBreaker, a real or fake player.
 *  - repeating (the TNT launcher): every time the tool is ready ({@link #cooldownTicks()} after the last use) and
 *    there's fuel for {@link #energyPerUse()}, the suit calls {@link #use};
 *  - continuous (all the others, {@link #isContinuous()}: tools that spin up, charge or time their own shots, like the
 *    chaingun and the rail cannon, are continuous too): {@link #holdTick} every tick while held and
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

    /** How far this tool's tip sits behind the chaingun's muzzle along the tool, in model pixels (1/16 block): muzzle
     *  effects (the EM beam) start at the tip. The muzzle position was measured on the chaingun, the longest. */
    default float muzzleInsetPixels() {
        return 0F;
    }

    /** Server: one use (one shot, one block...). Return false if nothing happened (no fuel is charged). */
    boolean use(PowerArmorEntity suit, LivingEntity operator, ItemStack tool);

    /** How far the barrel's centre line sits in front of the socket's axis, model pixels toward the suit's front. The
     *  cockpit view starts this tool's shots there (in third person it's too small to see). */
    default float barrelForwardPixels() {
        return 0F;
    }

    /** Continuous tools are driven by holdTick / release instead of use. */
    default boolean isContinuous() {
        return false;
    }

    /** Server, continuous tools: every tick while the button is held and the suit can operate. */
    default void holdTick(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
    }

    /** Server, continuous tools: trigger released ({@code interrupted} false), or the suit lost power, the
     *  operator changed (pilot left or climbed in) or the tool was taken out ({@code interrupted} true,
     *  {@code operator} may be null). */
    default void release(PowerArmorEntity suit, @Nullable LivingEntity operator, boolean interrupted) {
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

    /** Client: volume and pitch of the run sound for this suit right now (the chaingun's whine follows its spin). */
    default float runSoundVolume(PowerArmorEntity suit) {
        return 1F;
    }

    default float runSoundPitch(PowerArmorEntity suit) {
        return 1F;
    }

    /** Server: keep the run animation going after the trigger is released (a tree still coming down). */
    default boolean keepsRunning(PowerArmorEntity suit) {
        return false;
    }
}
