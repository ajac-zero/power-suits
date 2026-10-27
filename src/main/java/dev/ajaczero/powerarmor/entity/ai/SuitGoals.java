package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.SuitDriving;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The suit's machine mind, built from vanilla's goal system: a mob holds a list of goals ("walk home", "look at that
 * player"), each with a priority and the controls it needs (MOVE, LOOK...). Twice a second the goal selector starts
 * the most important goals that want to run ({@code canUse}) and whose controls are free; running goals tick every
 * tick until {@code canContinueToUse} says stop. Lower priority number = more important.
 *
 * Every goal here also checks {@link PowerArmorEntity#isAiDriven()}: the mind only acts while the suit is powered and
 * nobody is inside (the pilot drives otherwise; unpowered it's a parked machine).
 *
 * Two kinds of goals:
 *  - the ones every mind has, here: walk home (ReturnHome), follow the owner (FollowOwner) and pick who to defend them
 *    from (DefendOwner) while the tool makes it a gunner, a rogue's target picking (RogueTarget) and wandering
 *    (RogueWander), and looking around;
 *  - the tool's own job: each power tool lists its goals (PowerTool.mindGoals), which the mind learns when the tool goes
 *    into the socket and forgets when it comes out (SuitBrain.updateToolGoals). Chaingun and flamethrower: GunAttack (aim,
 *    fire, punch when dry); EM projector: EmGuard (guard home) and RogueEmAttack; rail cannon: RailGuard (a sentry at
 *    home); drill: DrillWorker (dig a hall); chainsaw: SawWorker (fell trees). A tool with none (the TNT launcher) leaves
 *    the mind standing guard at home. Adding a tool's job never touches this class.
 * A rogue mind has no owner and no home: it wanders and attacks anything it sees with its chaingun or EM projector.
 * A mob has two goal lists: {@code targetSelector} decides who to fight (setTarget), {@code goalSelector} what to do.
 */
public final class SuitGoals {
    /**
     * Ticks an idle mind waits after a search for a target came up empty (DefendOwner, RogueTarget, EmGuard, RailGuard).
     * A search looks at every living thing in a wide box and checks line of sight to each; vanilla's own target goals
     * search about twice a second, not every tick, and so do these. A search that finds something is acted on at once.
     */
    static final int SEARCH_INTERVAL = 10;

    /** Priority of a tool's own goals (PowerTool.mindGoals): above following the owner and walking home, so a job in
     *  progress keeps the feet; target picking is a separate list (targetSelector). */
    public static final int TOOL_JOB = 2;

    /** How far from home the idle suit may drift (pushed, knocked back) before it walks back, in blocks. */
    static final double HOME_SLACK = 2.5;

    /** The goals every mind has. Vanilla builds a mob's goals once, in its constructor (registerGoals). */
    public static void register(PowerArmorEntity suit, GoalSelector goals, GoalSelector targets) {
        targets.addGoal(1, new DefendOwner(suit));
        targets.addGoal(1, new RogueTarget(suit));
        // (priority TOOL_JOB: the socket tool's goals, added and removed by SuitBrain.updateToolGoals)
        goals.addGoal(4, new FollowOwner(suit));
        goals.addGoal(5, new ReturnHome(suit));
        goals.addGoal(7, new RogueWander(suit));
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
            return this.suit.tickCount >= this.retryAt && this.suit.isAiDriven() && !this.suit.brain().followsOwner() && this.suit.onGround()
                    && this.suit.brain().getHome() != null && this.suit.brain().isAwayFromHome(HOME_SLACK);
        }

        @Override
        public void start() {
            BlockPos home = this.suit.brain().getHome();
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
            BlockPos home = this.suit.brain().getHome();
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
            return this.suit.isAiDriven() && !this.suit.brain().followsOwner() && this.suit.brain().getHome() != null && this.ticks < 600
                    && (!this.suit.getNavigation().isDone() || this.suit.brain().isAwayFromHome(ARRIVED));
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
        /** Tick of the next search for a target (SEARCH_INTERVAL after one came up empty). */
        private int nextSearch;

        DefendOwner(PowerArmorEntity suit) {
            super(suit, false); // we check sight ourselves (a target behind a wall keeps the suit walking toward it)
            this.suit = suit;
        }

        @Override
        public boolean canUse() {
            if (!this.suit.isAiDriven() || !this.suit.brain().followsOwner() || this.suit.tickCount < this.nextSearch) {
                return false;
            }
            this.targetMob = SuitTargets.pick(this.suit);
            if (this.targetMob == null) {
                this.nextSearch = this.suit.tickCount + SEARCH_INTERVAL;
            }
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
            if (!this.suit.isAiDriven() || !this.suit.brain().followsOwner() || target == null || target.distanceToSqr(this.suit) > 40 * 40
                    || !SuitTargets.canAttack(this.suit, target, SuitTargets.isProvoked(this.suit, target) || this.provokedAtStart(target))) {
                return false;
            }
            return true;
        }

        /** A target that was provoked stays fair game for the rest of the fight (a wolf that bit the owner once). */
        private boolean provokedAtStart(LivingEntity target) {
            return target == this.targetMob && !(target instanceof Player);
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

    /** Target selector, rogue mind: the nearest player it sees, else the nearest mob (SuitTargets.pickRogue); re-picks
     *  every second, so a player who comes into view takes over from a cow. */
    static class RogueTarget extends TargetGoal {
        private final PowerArmorEntity suit;
        private int recheck;
        /** Tick of the next search for a target (SEARCH_INTERVAL after one came up empty). */
        private int nextSearch;

        RogueTarget(PowerArmorEntity suit) {
            super(suit, false);
            this.suit = suit;
        }

        @Override
        public boolean canUse() {
            if (!this.suit.isAiDriven() || !this.suit.isRogue() || this.suit.tickCount < this.nextSearch) {
                return false;
            }
            this.targetMob = SuitTargets.pickRogue(this.suit);
            if (this.targetMob == null) {
                this.nextSearch = this.suit.tickCount + SEARCH_INTERVAL;
            }
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
            return this.suit.isAiDriven() && this.suit.isRogue() && target != null && target.isAlive() && target.canBeSeenAsEnemy()
                    && target.distanceToSqr(this.suit) < 32 * 32;
        }

        @Override
        public void tick() {
            if (--this.recheck <= 0) {
                this.recheck = 20;
                LivingEntity better = SuitTargets.pickRogue(this.suit);
                if (better != null && better != this.suit.getTarget() && !(this.suit.getTarget() instanceof Player)) {
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

    /** Rogue mind with nothing to attack: roams, a walk to a random spot up to 12 blocks away every few seconds. */
    static class RogueWander extends Goal {
        private final PowerArmorEntity suit;

        RogueWander(PowerArmorEntity suit) {
            this.suit = suit;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.suit.isAiDriven() || !this.suit.isRogue() || this.suit.getTarget() != null || this.suit.getRandom().nextInt(60) != 0) {
                return false;
            }
            var random = this.suit.getRandom();
            int x = this.suit.getBlockX() + random.nextInt(25) - 12, z = this.suit.getBlockZ() + random.nextInt(25) - 12;
            int y = this.suit.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return Math.abs(y - this.suit.getBlockY()) < 6 && this.suit.getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return this.suit.isAiDriven() && this.suit.isRogue() && this.suit.getTarget() == null && !this.suit.getNavigation().isDone();
        }

        @Override
        public void stop() {
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
            Player owner = this.suit.brain().getOwner();
            if (!this.suit.isAiDriven() || !this.suit.brain().followsOwner() || owner == null || owner.isSpectator()) {
                return false;
            }
            double d = owner.distanceTo(this.suit);
            return d > START && d < GIVE_UP;
        }

        @Override
        public boolean canContinueToUse() {
            Player owner = this.suit.brain().getOwner();
            if (!this.suit.isAiDriven() || !this.suit.brain().followsOwner() || owner == null || owner.isSpectator()) {
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
            Player owner = this.suit.brain().getOwner();
            if (owner != null && --this.repath <= 0) {
                this.repath = 10;
                this.suit.getNavigation().moveTo(owner, owner.distanceTo(this.suit) > SPRINT ? SuitDriving.SPRINT_MULTIPLIER : 1.0);
            }
        }

        @Override
        public void stop() {
            this.suit.getNavigation().stop();
        }
    }

    private SuitGoals() {}
}
