package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Rogue mind with an EM projector (the projector adds it, with EmGuard for a friendly mind): catch the target in the
 * beam (a player too: the rogue's beam takes players), lift it and hurl it away with a pilot's strength (32 blocks/s, a
 * little upward): the landing and anything it slams into hurt. What the beam can't hold (a piloted suit, a big mob) gets
 * walked up to and punched.
 */
public class RogueEmAttack extends AttackGoal {
    private static final int LIFT_TICKS = 8;
    private static final double REACH = 13;
    private int lift;

    public RogueEmAttack(PowerArmorEntity suit) {
        super(suit);
    }

    @Override
    public boolean canUse() {
        return this.suit.isAiDriven() && this.suit.isRogue() && this.suit.brain().toolUse() == ToolUse.EM && this.suit.getTarget() != null
                && this.suit.getTarget().isAlive();
    }

    @Override
    public void start() {
        super.start();
        this.lift = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.suit.getTarget();
        if (target == null) {
            return;
        }
        this.tickCooldowns();
        boolean sees = this.suit.getSensing().hasLineOfSight(target);
        if (this.suit.socket().getBeamTarget() == target) {
            // Caught: swing it up and away (from the suit toward where it was), then let go: the projector throws it.
            this.suit.getNavigation().stop();
            Vec3 away = target.position().subtract(this.suit.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? this.suit.getLookAngle().multiply(1, 0, 1) : away;
            this.suit.brain().setAim(this.suit.getEyePosition().add(away.normalize().scale(10)).add(0, 4, 0));
            // Something heavy (a suit, a boss) can't be lifted: pin it down for a couple of seconds before the shove.
            this.suit.socket().setAiTrigger(++this.lift < (EmProjectorItem.isHeavy(target) ? 40 : LIFT_TICKS));
            return;
        }
        this.lift = 0;
        this.suit.brain().setAim(target.getBoundingBox().getCenter());
        if (!EmProjectorItem.canHold(this.suit, target)) {
            this.fists(target, sees, 1.0); // too heavy for the beam
            return;
        }
        // Only from the ground (or water): re-catching a thrown one mid-flight would juggle it and spare it the landing.
        boolean landed = target.onGround() || target.isInLiquid() || SuitTargets.isFlyer(target);
        boolean inReach = sees && landed && this.suit.distanceTo(target) < REACH;
        this.suit.socket().setAiTrigger(inReach && this.suit.socket().isReady());
        if (inReach) {
            this.suit.getNavigation().stop();
        } else {
            this.approach(target, 1.0);
        }
    }
}
