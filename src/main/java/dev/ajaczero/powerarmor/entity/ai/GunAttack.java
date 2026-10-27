package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The gunner's job (chaingun, flamethrower; their items add it, see PowerTool.mindGoals): aim the head at the target
 * (DefendOwner or RogueTarget picked it) and hold the trigger while the shot is clear (never through, or with a flame
 * near, the owner, a villager, a pet...); walk closer while it can't see the target or something friendly is in the way.
 * Out of ammo, it walks up and punches instead. A rogue fires at will and never runs dry.
 */
public class GunAttack extends AttackGoal {
    /**
     * How one tool fights as a gun.
     * @param range       fires from up to this far, blocks
     * @param flame       the shot fans out (the flamethrower): keep a wide berth round friends (SuitTargets.flameClear)
     *                    instead of a clear line (lineOfFireClear)
     * @param hasAmmo     something to shoot (a friendly mind's chaingun needs bullets in the hidden store)
     * @param mayFire     the tool can fire right now (not overheated)
     */
    public record Gun(double range, boolean flame, Predicate<PowerArmorEntity> hasAmmo, Predicate<PowerArmorEntity> mayFire) {}

    private final Gun gun;

    public GunAttack(PowerArmorEntity suit, Gun gun) {
        super(suit);
        this.gun = gun;
    }

    @Override
    public boolean canUse() {
        return this.suit.isAiDriven() && this.suit.brain().hasGun() && this.suit.getTarget() != null && this.suit.getTarget().isAlive();
    }

    @Override
    public void tick() {
        LivingEntity target = this.suit.getTarget();
        if (target == null) {
            return;
        }
        this.tickCooldowns();
        Vec3 aim = target.getBoundingBox().getCenter();
        this.suit.brain().setAim(aim);
        boolean sees = this.suit.getSensing().hasLineOfSight(target);
        if (!this.suit.isRogue() && !this.gun.hasAmmo().test(this.suit)) {
            this.fists(target, sees, 1.6);
            return;
        }
        boolean inRange = this.suit.distanceTo(target) < this.gun.range();
        // A rogue fires at will; a friendly mind never through (or, with a flame, near) someone it must not hurt.
        boolean clear = sees && inRange && (this.suit.isRogue()
                || (this.gun.flame() ? SuitTargets.flameClear(this.suit, target, aim) : SuitTargets.lineOfFireClear(this.suit, target, aim)));
        this.suit.socket().setAiTrigger(clear && this.gun.mayFire().test(this.suit));
        if (clear) {
            this.suit.getNavigation().stop(); // plant the feet and fire
        } else {
            // Get a clear shot: closer, round the friend in the way. (A short range like the flamethrower's is fine:
            // moveTo stops at the target, and inRange plants the feet on the way.)
            this.approach(target, 1.6);
        }
    }
}
