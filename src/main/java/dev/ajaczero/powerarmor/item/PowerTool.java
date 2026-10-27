package dev.ajaczero.powerarmor.item;

import com.geckolib.animation.RawAnimation;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.ai.ToolUse;
import java.util.List;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
 *
 * What the pilot sees of it on the visor: its own HUD, if it has anything to say (client/hud: one class per tool,
 * registered by item in ClientSetup; none for the drill, the chainsaw and the flamethrower), drawn from its effects'
 * {@link ToolEffects#hud} numbers (ammo, one gauge) and the suit's synced state.
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
    default SoundEvent runSound() {
        return null;
    }

    /** Client: volume and pitch of the run sound for this suit right now (the chaingun's whine follows its spin). */
    default float runSoundVolume(PowerArmorEntity suit) {
        return 1F;
    }

    default float runSoundPitch(PowerArmorEntity suit) {
        return 1F;
    }

    /** Client: a fresh object for this tool's looks on one suit (moving parts, lights, shot effects), made when the tool
     *  goes into a socket. Tools with nothing to show keep the default. */
    default ToolEffects createEffects() {
        return ToolEffects.NONE;
    }

    /** How the machine mind uses this tool, which picks its job (design sec. 8): a gunner follows its owner, the others
     *  stay at home (SuitBrain's job questions). */
    default ToolUse mindUse() {
        return ToolUse.NONE;
    }

    /**
     * Server: the machine mind's own goals for this tool (design sec. 8), a fresh set for each suit: the mind learns them
     * when the tool goes into the socket and forgets them when it comes out (SuitBrain.updateToolGoals; vanilla's
     * skeletons swap their bow and sword goals the same way). The goals every mind has (walk home, follow a gunner's
     * owner, pick targets, look around) are in SuitGoals. None (the default): the mind stands guard at home.
     */
    default List<Goal> mindGoals(PowerArmorEntity suit) {
        return List.of();
    }

    /** Server: keep the run animation going after the trigger is released (a tree still coming down). */
    default boolean keepsRunning(PowerArmorEntity suit) {
        return false;
    }
}
