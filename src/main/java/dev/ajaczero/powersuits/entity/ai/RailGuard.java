package dev.ajaczero.powersuits.entity.ai;

import dev.ajaczero.powersuits.PowerSuitsConfig;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.item.RailCannonItem;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rail cannon job (Phase 11, user: "a long-range guard"; the cannon adds it, see PowerTool.mindGoals): a sentry at home.
 * Pick what to shoot within the rail guard radius of home (whatever just hurt the owner or the suit first, then the
 * monster nearest home) that the suit can see and the beam can reach, aim the head at it and hold the trigger until the
 * cannon is charged; then let go (it fires on release) once the head points at the target and nobody it must not hurt is
 * anywhere on the beam (SuitTargets.beamClear: the beam goes on past the target). Only LOOK: ReturnHome keeps the feet at
 * home. A shot it can't take within a few seconds is dropped without firing (RailCannonItem.disarm). Never breaks blocks.
 */
public class RailGuard extends Goal {
    /** Give up on a target after this long (out of sight, friends in the way). */
    private static final int GIVE_UP_TICKS = 120;
    /** Fire only with the head within this many degrees of the target (the beam is a hand wide). */
    private static final double MAX_AIM_ERROR = 2.0;
    private final PowerSuitEntity suit;
    /** Tick of the next search for a target (SEARCH_INTERVAL after one came up empty). */
    private int nextSearch;
    @Nullable
    private LivingEntity target;
    private int ticks;
    private boolean fired;

    public RailGuard(PowerSuitEntity suit) {
        this.suit = suit;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Nullable
    private RailCannonItem cannon() {
        return this.suit.getSocketStack().getItem() instanceof RailCannonItem c ? c : null;
    }

    @Override
    public boolean canUse() {
        var cannon = this.cannon();
        if (cannon == null || !this.suit.isAiDriven() || !this.suit.brain().snipesFromHome() || this.suit.brain().getHome() == null
                || RailCannonItem.isCoolingDown(this.suit) || !cannon.canPay(this.suit, this.suit) || this.suit.tickCount < this.nextSearch) {
            return false;
        }
        this.target = this.pick();
        if (this.target == null) {
            this.nextSearch = this.suit.tickCount + SuitGoals.SEARCH_INTERVAL;
        }
        return this.target != null;
    }

    /** Provoked attackers first (the owner's, then the suit's own), else the monster nearest home. */
    @Nullable
    private LivingEntity pick() {
        BlockPos home = this.suit.brain().getHome();
        if (home == null) {
            return null;
        }
        Vec3 centre = Vec3.atBottomCenterOf(home);
        double radius = PowerSuitsConfig.RAIL_GUARD_RADIUS.get();
        double reach = PowerSuitsConfig.RAIL_RANGE.get() - 1;
        Predicate<LivingEntity> shootable = e -> e.distanceToSqr(centre) < radius * radius
                && e.distanceTo(this.suit) < reach && this.suit.getSensing().hasLineOfSight(e);
        Player owner = this.suit.brain().getOwner();
        for (LivingEntity provoked : new LivingEntity[] {owner == null ? null : owner.getLastHurtByMob(), this.suit.getLastHurtByMob()}) {
            if (provoked != null && SuitTargets.isProvoked(this.suit, provoked) && SuitTargets.canAttack(this.suit, provoked, true)
                    && shootable.test(provoked)) {
                return provoked;
            }
        }
        return this.suit.level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius, 16, radius),
                        e -> (e instanceof Enemy || e instanceof PowerSuitEntity other && other.isRogue())
                                && SuitTargets.canAttack(this.suit, e, false) && shootable.test(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(centre))).orElse(null);
    }

    @Override
    public void start() {
        this.suit.setTarget(this.target);
        this.ticks = 0;
        this.fired = false;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.fired && this.target != null && this.target.isAlive() && this.ticks < GIVE_UP_TICKS
                && this.suit.isAiDriven() && this.suit.brain().snipesFromHome() && this.cannon() != null;
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
        Vec3 aim = this.target.getBoundingBox().getCenter();
        this.suit.brain().setAim(aim);
        this.suit.socket().setAiTrigger(true); // charge (it holds a full charge as long as the trigger stays down)
        if (!RailCannonItem.isCharged(this.suit) || !this.suit.getSensing().hasLineOfSight(this.target)) {
            return;
        }
        Vec3 eye = this.suit.getEyePosition();
        Vec3 view = this.suit.getViewVector(1F);
        Vec3 want = aim.subtract(eye).normalize();
        double error = Math.toDegrees(Math.acos(Mth.clamp(view.dot(want), -1, 1)));
        if (error <= MAX_AIM_ERROR && SuitTargets.beamClear(this.suit, view, PowerSuitsConfig.RAIL_RANGE.get())) {
            this.suit.socket().setAiTrigger(false); // let go: it fires this tick, after the goals (SuitSocket.tick)
            this.fired = true;
        }
    }

    @Override
    public void stop() {
        if (!this.fired) {
            RailCannonItem.disarm(this.suit); // let go without firing
        }
        this.suit.socket().setAiTrigger(false);
        this.suit.brain().setAim(null);
        this.suit.setTarget(null);
        this.target = null;
    }
}
