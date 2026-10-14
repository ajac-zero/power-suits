package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.EnumSet;
import dev.ajaczero.powerarmor.item.ChaingunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;

/**
 * The suit's machine mind, built from vanilla's goal system: a mob holds a list of goals ("walk home", "look at that
 * player"), each with a priority and the controls it needs (MOVE, LOOK...). Twice a second the goal selector starts
 * the most important goals that want to run ({@code canUse}) and whose controls are free; running goals tick every
 * tick until {@code canContinueToUse} says stop. Lower priority number = more important.
 *
 * Every goal here also checks {@link PowerArmorEntity#isAiDriven()}: the mind only acts while the suit is powered and
 * nobody is inside (the pilot drives otherwise; unpowered it's a parked machine).
 *
 * The job comes from the socket tool (design sec. 8): none (and tools whose job isn't built yet) = stand guard at home;
 * chaingun = follow the owner and defend them (a target goal picks who, an attack goal aims, fires and moves).
 * A mob has two goal lists: {@code targetSelector} decides who to fight (setTarget), {@code goalSelector} what to do.
 */
public final class SuitGoals {
    /** How far from home the idle suit may drift (pushed, knocked back) before it walks back, in blocks. */
    static final double HOME_SLACK = 2.5;

    public static void register(PowerArmorEntity suit, GoalSelector goals, GoalSelector targets) {
        targets.addGoal(1, new DefendOwner(suit));
        goals.addGoal(2, new ChaingunAttack(suit));
        goals.addGoal(4, new FollowOwner(suit));
        goals.addGoal(5, new ReturnHome(suit));
        goals.addGoal(8, new LookAtPlayerGoal(suit, Player.class, 8F) {
            @Override
            public boolean canUse() {
                return suit.isAiDriven() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return suit.isAiDriven() && super.canContinueToUse();
            }
        });
        goals.addGoal(9, new RandomLookAroundGoal(suit) {
            @Override
            public boolean canUse() {
                return suit.isAiDriven() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return suit.isAiDriven() && super.canContinueToUse();
            }
        });
    }

    /** Walk back to the home block (where the pilot got out) when the suit is more than {@link #HOME_SLACK} away. */
    static class ReturnHome extends Goal {
        /** Close enough to call it home (blocks, horizontally). */
        private static final double ARRIVED = 0.4;
        private final PowerArmorEntity suit;
        /** Game tick before which it doesn't try again after no path was found (e.g. home walled in). */
        private int retryAt;
        private int ticks;

        ReturnHome(PowerArmorEntity suit) {
            this.suit = suit;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            // On the ground: vanilla's pathfinder finds nothing for a mob in the air (falling, just spawned).
            return this.suit.tickCount >= this.retryAt && this.suit.isAiDriven() && !this.suit.followsOwner() && this.suit.onGround()
                    && this.suit.getHome() != null && this.suit.isAwayFromHome(HOME_SLACK);
        }

        @Override
        public void start() {
            BlockPos home = this.suit.getHome();
            this.ticks = 0;
            if (home == null || !this.suit.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0)) {
                this.retryAt = this.suit.tickCount + 40; // no way there right now: look again in 2 seconds
            }
        }

        /** The path ends within a block or so of home (vanilla's paths stop "close enough", and a 2-block-wide mob's
         *  nodes are offset): walk the last stretch straight onto the block, so home stays home. */
        @Override
        public void tick() {
            this.ticks++;
            BlockPos home = this.suit.getHome();
            if (home != null && this.suit.getNavigation().isDone()) {
                this.suit.getMoveControl().setWantedPosition(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.6);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.suit.isAiDriven() && !this.suit.followsOwner() && this.suit.getHome() != null && this.ticks < 600
                    && (!this.suit.getNavigation().isDone() || this.suit.isAwayFromHome(ARRIVED));
        }

        @Override
        public void stop() {
            this.suit.getNavigation().stop();
            this.suit.setZza(0F); // the move control may still have a last step in it
        }
    }

    /** Target selector, chaingun job: pick who to shoot (SuitTargets.pick) and keep re-checking every second, so a new
     *  attack on the owner takes over from a monster further away. */
    static class DefendOwner extends TargetGoal {
        private final PowerArmorEntity suit;
        private int recheck;

        DefendOwner(PowerArmorEntity suit) {
            super(suit, false); // we check sight ourselves (a target behind a wall keeps the suit walking toward it)
            this.suit = suit;
        }

        @Override
        public boolean canUse() {
            if (!this.suit.isAiDriven() || !this.suit.followsOwner()) {
                return false;
            }
            this.targetMob = SuitTargets.pick(this.suit);
            return this.targetMob != null;
        }

        @Override
        public void start() {
            this.suit.setTarget(this.targetMob);
            this.recheck = 20;
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.suit.getTarget();
            if (!this.suit.isAiDriven() || !this.suit.followsOwner() || target == null || target.distanceToSqr(this.suit) > 40 * 40
                    || !SuitTargets.canAttack(this.suit, target, SuitTargets.isProvoked(this.suit, target) || this.provokedAtStart(target))) {
                return false;
            }
            return true;
        }

        /** A target that was provoked stays fair game for the rest of the fight (a wolf that bit the owner once). */
        private boolean provokedAtStart(LivingEntity target) {
            return target == this.targetMob && !(target instanceof net.minecraft.world.entity.player.Player);
        }

        @Override
        public void tick() {
            if (--this.recheck <= 0) {
                this.recheck = 20;
                LivingEntity better = SuitTargets.pick(this.suit);
                if (better != null && better != this.suit.getTarget() && SuitTargets.isProvoked(this.suit, better)) {
                    this.targetMob = better;
                    this.suit.setTarget(better);
                }
            }
        }

        @Override
        public void stop() {
            this.suit.setTarget(null);
            super.stop();
        }
    }

    /**
     * Chaingun job: aim the head at the target and hold the trigger while the line of fire is clear (never through the
     * owner, a villager, a pet...); walk closer while it can't see the target or something friendly is in the way. Out of
     * nuggets (the hidden store is empty), it walks up and punches instead.
     */
    static class ChaingunAttack extends Goal {
        /** Fire from up to this far (bullets fly 80 blocks/s; past ~30 the spread misses small mobs). */
        private static final double FIRE_RANGE = 28;
        private static final double PUNCH_REACH = 1.4; // from the suit's box to the target's box, blocks
        private final PowerArmorEntity suit;
        private int repath;
        private int punchCooldown;

        ChaingunAttack(PowerArmorEntity suit) {
            this.suit = suit;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.suit.isAiDriven() && this.suit.followsOwner() && this.suit.getTarget() != null && this.suit.getTarget().isAlive();
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

        @Override
        public void tick() {
            LivingEntity target = this.suit.getTarget();
            if (target == null) {
                return;
            }
            Vec3 aim = target.getBoundingBox().getCenter();
            this.suit.setAiAim(aim);
            boolean sees = this.suit.getSensing().hasLineOfSight(target);
            if (this.punchCooldown > 0) {
                this.punchCooldown--;
            }
            if (this.suit.hasAmmo()) {
                boolean inRange = this.suit.distanceTo(target) < FIRE_RANGE;
                boolean clear = sees && inRange && SuitTargets.lineOfFireClear(this.suit, target, aim);
                this.suit.setAiTrigger(clear && !ChaingunItem.isOverheated(this.suit));
                if (clear) {
                    this.suit.getNavigation().stop(); // plant the feet and fire
                } else {
                    this.approach(target); // get a clear shot: closer, round the friend in the way
                }
                return;
            }
            // No bullets: the fists (1 + the melee bonus, like a pilot's bare hand).
            this.suit.setAiTrigger(false);
            double gap = Math.sqrt(this.suit.getBoundingBox().distanceToSqr(target.getBoundingBox().getCenter()))
                    - Math.max(target.getBbWidth(), target.getBbHeight()) / 2;
            if (gap > PUNCH_REACH || !sees) {
                this.approach(target);
            } else {
                this.suit.getNavigation().stop();
                if (this.punchCooldown == 0) {
                    this.suit.punch(target);
                    this.punchCooldown = 20;
                }
            }
        }

        private void approach(LivingEntity target) {
            if (--this.repath <= 0) {
                this.repath = 10;
                this.suit.getNavigation().moveTo(target, this.suit.distanceTo(target) > 12 ? 1.6 : 1.0);
            }
        }

        @Override
        public void stop() {
            this.suit.setAiTrigger(false);
            this.suit.setAiAim(null);
            this.suit.getNavigation().stop();
        }
    }

    /** Chaingun job: keep up with the owner like a tamed wolf: walk when they're over 7 blocks away, sprint past 16, stop
     *  at 4. No teleporting (it's a ton of steel): an owner who flies off leaves it standing where it was. */
    static class FollowOwner extends Goal {
        private static final double START = 7, STOP = 4, SPRINT = 16, GIVE_UP = 64;
        private final PowerArmorEntity suit;
        private int repath;

        FollowOwner(PowerArmorEntity suit) {
            this.suit = suit;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            net.minecraft.world.entity.player.Player owner = this.suit.getOwner();
            if (!this.suit.isAiDriven() || !this.suit.followsOwner() || owner == null || owner.isSpectator()) {
                return false;
            }
            double d = owner.distanceTo(this.suit);
            return d > START && d < GIVE_UP;
        }

        @Override
        public boolean canContinueToUse() {
            net.minecraft.world.entity.player.Player owner = this.suit.getOwner();
            if (!this.suit.isAiDriven() || !this.suit.followsOwner() || owner == null || owner.isSpectator()) {
                return false;
            }
            double d = owner.distanceTo(this.suit);
            return d > STOP && d < GIVE_UP;
        }

        @Override
        public void start() {
            this.repath = 0;
        }

        @Override
        public void tick() {
            net.minecraft.world.entity.player.Player owner = this.suit.getOwner();
            if (owner != null && --this.repath <= 0) {
                this.repath = 10;
                this.suit.getNavigation().moveTo(owner, owner.distanceTo(this.suit) > SPRINT ? 2.0 : 1.0);
            }
        }

        @Override
        public void stop() {
            this.suit.getNavigation().stop();
        }
    }

    private SuitGoals() {}
}
