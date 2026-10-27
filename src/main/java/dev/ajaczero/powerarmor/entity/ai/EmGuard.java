package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * EM projector job (the projector adds it, see PowerTool.mindGoals): guard the area round home. Pick the monster nearest
 * home (within the guard radius, in the projector's range and in sight), aim at it and hold the trigger until the beam
 * has it; then aim straight up (a creeper: up and away from home) so the beam lifts it overhead, and let go: the
 * projector throws along the aim, hard enough that the fall kills (EM_GUARD_LAUNCH). Only LOOK: the feet stay with
 * ReturnHome, so it fights from home.
 */
public class EmGuard extends Goal {
    /** Ticks to hold the aim up before letting go: the beam pulls the monster overhead. */
    private static final int LIFT_TICKS = 12;
    /** Give up on a monster the beam hasn't caught after this long (out of sight, behind something). */
    private static final int SEEK_TICKS = 60;
    private final PowerArmorEntity suit;
    /** Tick of the next search for a target (SEARCH_INTERVAL after one came up empty). */
    private int nextSearch;
    @Nullable
    private LivingEntity target;
    private boolean lifting, thrown;
    private int ticks;

    public EmGuard(PowerArmorEntity suit) {
        this.suit = suit;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.suit.isAiDriven() || !this.suit.brain().guardsHome() || this.suit.brain().getHome() == null || !this.suit.socket().isReady()
                || this.suit.socket().getBeamTarget() != null || this.suit.tickCount < this.nextSearch) {
            return false;
        }
        this.target = this.pick();
        if (this.target == null) {
            this.nextSearch = this.suit.tickCount + SuitGoals.SEARCH_INTERVAL;
        }
        return this.target != null;
    }

    /** The monster nearest home that the beam could catch from here. */
    @Nullable
    private LivingEntity pick() {
        BlockPos home = this.suit.brain().getHome();
        if (home == null) {
            return null;
        }
        Vec3 centre = Vec3.atBottomCenterOf(home);
        double radius = PowerArmorConfig.EM_GUARD_RADIUS.get();
        double reach = PowerArmorConfig.EM_RANGE.get();
        return this.suit.level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius, 8, radius),
                        // standing (or swimming, or a flyer): one it threw is left alone until it lands (catching it
                        // mid-fall reset its fall and the zombie survived the 30-block drop)
                        e -> e instanceof Enemy && e.distanceToSqr(centre) < radius * radius
                                && (e.onGround() || e.isInLiquid() || SuitTargets.isFlyer(e))
                                && e.distanceTo(this.suit) < reach - 1 && EmProjectorItem.canGrab(e)
                                && !EmProjectorItem.isHeavy(e) // can't be lifted: no fling
                                && SuitTargets.canAttack(this.suit, e, false) && this.suit.getSensing().hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(centre))).orElse(null);
    }

    @Override
    public void start() {
        this.suit.setTarget(this.target);
        this.lifting = false;
        this.thrown = false;
        this.ticks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.thrown || this.target == null || !this.target.isAlive() || !this.suit.isAiDriven() || !this.suit.brain().guardsHome()) {
            return false;
        }
        return this.lifting ? this.suit.socket().getBeamTarget() == this.target : this.ticks < SEEK_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        this.ticks++;
        if (!this.lifting) {
            this.suit.brain().setAim(this.target.getBoundingBox().getCenter());
            this.suit.socket().setAiTrigger(true);
            if (this.suit.socket().getBeamTarget() == this.target) {
                this.lifting = true; // caught: now up with it
                this.ticks = 0;
            }
            return;
        }
        this.suit.brain().setAim(this.liftAim());
        if (this.ticks >= LIFT_TICKS) {
            this.suit.socket().setAiTrigger(false); // let go: the projector throws it along the aim (this tick, after the goals)
            this.thrown = true;
        }
    }

    /** Where to point while lifting: straight up, leaning a little away from home so the monster comes down outside
     *  (not on the suit); a creeper is thrown up and away instead: it must not burst at home. */
    private Vec3 liftAim() {
        BlockPos home = this.suit.brain().getHome();
        Vec3 eye = this.suit.getEyePosition();
        Vec3 out = this.target.position().subtract(home == null ? this.suit.position() : Vec3.atBottomCenterOf(home)).multiply(1, 0, 1);
        out = out.lengthSqr() < 1.0E-4 ? this.suit.getLookAngle().multiply(1, 0, 1) : out;
        out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : out.normalize();
        return this.target instanceof Creeper ? eye.add(out.scale(10)).add(0, 4.5, 0)
                : eye.add(out.scale(0.6)).add(0, 10, 0);
    }

    @Override
    public void stop() {
        this.suit.socket().setAiTrigger(false);
        this.suit.brain().setAim(null);
        this.suit.setTarget(null);
        this.target = null;
    }
}
