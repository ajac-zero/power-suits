package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * What the mind's fighting goals share (GunAttack, RogueEmAttack): they steer the feet and the head (MOVE + LOOK), run
 * every tick, walk up to the target when there's no shot, and fall back on the fists when the tool can't do anything
 * (out of bullets, a mob too heavy for the beam). Each subclass decides when to use its tool.
 */
public abstract class AttackGoal extends Goal {
    protected final PowerArmorEntity suit;
    private int repath;
    private int punchCooldown;

    protected AttackGoal(PowerArmorEntity suit) {
        this.suit = suit;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repath = 0;
    }

    /** Call every tick: counts down the fists' cooldown. */
    protected void tickCooldowns() {
        if (this.punchCooldown > 0) {
            this.punchCooldown--;
        }
    }

    /** Walk toward the target (re-planning the path twice a second), sprinting while it's far. */
    protected void approach(LivingEntity target, double farSpeed) {
        if (--this.repath <= 0) {
            this.repath = 10;
            this.suit.getNavigation().moveTo(target, this.suit.distanceTo(target) > 12 ? farSpeed : 1.0);
        }
    }

    /** The fists (1 + the melee bonus, like a pilot's bare hand): walk up, then a blow a second. */
    protected void fists(LivingEntity target, boolean sees, double farSpeed) {
        this.suit.socket().setAiTrigger(false);
        if (!sees || !SuitTargets.inPunchReach(this.suit, target)) {
            this.approach(target, farSpeed);
            return;
        }
        this.suit.getNavigation().stop();
        if (this.punchCooldown == 0) {
            this.suit.brain().punch(target);
            this.punchCooldown = 20;
        }
    }

    @Override
    public void stop() {
        this.suit.socket().setAiTrigger(false);
        this.suit.brain().setAim(null);
        this.suit.getNavigation().stop();
    }
}
