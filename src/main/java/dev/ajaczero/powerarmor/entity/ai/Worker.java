package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared by the block jobs (DrillWorker, SawWorker): pick a block to work on (SuitWork), walk until it's in reach and
 * the tool would hit it (or something else it may work on), aim and hold the trigger until it's gone, then the next one.
 * Blocks it can't get at (no path, something in the way it mustn't touch) are skipped for a minute. With nothing left
 * to do the goal ends and ReturnHome walks it back; it looks for new work every 2 seconds.
 */
public abstract class Worker extends Goal {
    /** From the eye to the target's centre: the tools reach 6 (4.5 + the suit's 1.5); keep a margin. */
    private static final double REACH = 4.6;
    /** Give up on a block after this long (walking + working), and skip it for a minute. */
    private static final int GIVE_UP_TICKS = 200, SKIP_FOR = 1200;
    protected final PowerArmorEntity suit;
    private final Set<BlockPos> skip = new HashSet<>();
    @Nullable
    protected BlockPos target;
    private int ticks, repath, nextScan, forgetSkipsAt;

    protected Worker(PowerArmorEntity suit) {
        this.suit = suit;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** The mind has this job right now (a friendly mind with the right tool). */
    protected abstract boolean hasJob();

    @Nullable
    protected abstract BlockPos findTarget(Set<BlockPos> skip);

    /** Still something to do at the target? */
    protected abstract boolean stillWork(BlockPos target);

    /** May the tool work on this block, if the aim hits it first? */
    protected abstract boolean mayWorkOn(BlockPos pos);

    /** Wait (trigger up) without picking the next target, e.g. while a felled tree comes down. */
    protected boolean busy() {
        return false;
    }

    @Override
    public boolean canUse() {
        if (!this.suit.isAiDriven() || !this.hasJob() || this.suit.brain().getHome() == null || this.suit.tickCount < this.nextScan) {
            return false;
        }
        if (!SuitWork.workAreaLoaded(this.suit)) {
            this.nextScan = this.suit.tickCount + 40; // home at the edge of the loaded world: wait, don't load it
            return false;
        }
        if (this.suit.tickCount >= this.forgetSkipsAt) {
            this.skip.clear();
            this.forgetSkipsAt = this.suit.tickCount + SKIP_FOR;
        }
        this.target = this.findTarget(this.skip);
        if (this.target == null) {
            this.nextScan = this.suit.tickCount + 40;
        }
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.suit.isAiDriven() && this.hasJob();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.repath = 0;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        if (this.busy()) {
            this.suit.socket().setAiTrigger(false);
            return;
        }
        if (!this.stillWork(this.target)) {
            this.next();
            return;
        }
        if (++this.ticks > GIVE_UP_TICKS) {
            this.skip.add(this.target); // can't get at it: try the others first
            this.next();
            return;
        }
        Vec3 aim = Vec3.atCenterOf(this.target);
        BlockPos first = SuitWork.firstBlockToward(this.suit, aim);
        boolean canHit = first != null && (first.equals(this.target) || this.mayWorkOn(first));
        if (this.suit.getEyePosition().distanceTo(aim) <= REACH && canHit) {
            this.suit.getNavigation().stop();
            this.suit.brain().setAim(aim);
            this.suit.socket().setAiTrigger(true);
            return;
        }
        this.suit.socket().setAiTrigger(false);
        this.suit.brain().setAim(null);
        if (--this.repath <= 0) {
            this.repath = 20;
            // Toward the block itself: the path ends next to it (the block's own spot is solid).
            this.suit.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
        }
    }

    private void next() {
        this.suit.socket().setAiTrigger(false);
        this.ticks = 0;
        this.repath = 0;
        this.target = this.findTarget(this.skip);
        if (this.target == null) {
            this.nextScan = this.suit.tickCount + 40;
        }
    }

    @Override
    public void stop() {
        this.suit.socket().setAiTrigger(false);
        this.suit.brain().setAim(null);
        this.suit.getNavigation().stop();
        this.target = null;
    }
}
