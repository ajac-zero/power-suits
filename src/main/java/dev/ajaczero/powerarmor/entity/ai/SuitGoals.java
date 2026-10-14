package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
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
 */
public final class SuitGoals {
    /** How far from home the idle suit may drift (pushed, knocked back) before it walks back, in blocks. */
    static final double HOME_SLACK = 2.5;

    public static void register(PowerArmorEntity suit, GoalSelector goals) {
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
            return this.suit.tickCount >= this.retryAt && this.suit.isAiDriven() && this.suit.onGround()
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
            return this.suit.isAiDriven() && this.suit.getHome() != null && this.ticks < 600
                    && (!this.suit.getNavigation().isDone() || this.suit.isAwayFromHome(ARRIVED));
        }

        @Override
        public void stop() {
            this.suit.getNavigation().stop();
            this.suit.setZza(0F); // the move control may still have a last step in it
        }
    }

    private SuitGoals() {}
}
