package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Who a friendly suit's mind may shoot, and when it must hold fire (design sec. 8: the chaingun job defends the owner
 * like a tamed wolf). Server side.
 */
public final class SuitTargets {
    /** How long after a hit the owner's (or the suit's) attacker stays a target, in ticks (vanilla forgets after 100). */
    private static final int PROVOKED_TICKS = 100;

    /**
     * May the suit attack this? Monsters always; anything else only when {@code provoked} (it attacked the owner or the
     * suit, or the owner attacked it), and never the owner, the owner's pets, villagers, golems or friendly suits.
     * Players only when provoked and the server allows PvP, and never in creative/spectator.
     */
    public static boolean canAttack(PowerArmorEntity suit, Entity entity, boolean provoked) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == suit || !living.canBeSeenAsEnemy()) {
            return false;
        }
        if (suit.getOwnerId() != null && suit.getOwnerId().equals(living.getUUID())) {
            return false;
        }
        if (living instanceof PowerArmorEntity other) {
            return other.isRogue() || provoked && other.getControllingPassenger() != null && canAttack(suit, other.getControllingPassenger(), true);
        }
        if (living.getVehicle() instanceof PowerArmorEntity) {
            return false; // a pilot: it's the suit that gets shot (above), never the person inside directly
        }
        if (living instanceof Player player) {
            return provoked && !player.isCreative() && pvpAllowed(suit);
        }
        if (living instanceof OwnableEntity pet && pet.getOwnerReference() != null
                && (pet.getOwnerReference().getUUID().equals(suit.getOwnerId()) || !provoked)) {
            return false; // the owner's wolves and cats, and nobody else's unless they started it
        }
        if (living instanceof AbstractVillager || living instanceof IronGolem || living instanceof SnowGolem) {
            return false;
        }
        return living instanceof Enemy || provoked;
    }

    /**
     * Who to shoot now, most urgent first: whoever just hurt the owner, whatever the owner just attacked, whoever just
     * hurt the suit, then the nearest monster the suit can see within the guard range of its owner (or of itself when the
     * owner isn't around). Null: nothing to do.
     */
    @Nullable
    public static LivingEntity pick(PowerArmorEntity suit) {
        Player owner = suit.getOwner();
        if (owner != null) {
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && canAttack(suit, attacker, true)) {
                return attacker;
            }
            LivingEntity victim = owner.getLastHurtMob();
            if (victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < PROVOKED_TICKS && canAttack(suit, victim, true)) {
                return victim;
            }
        }
        LivingEntity ownAttacker = suit.getLastHurtByMob();
        if (ownAttacker != null && suit.tickCount - suit.getLastHurtByMobTimestamp() < PROVOKED_TICKS && canAttack(suit, ownAttacker, true)) {
            return ownAttacker;
        }
        double range = PowerArmorConfig.GUARD_RANGE.get();
        Vec3 centre = owner != null && owner.distanceTo(suit) < 32 ? owner.position() : suit.position();
        return suit.level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(range, 8, range),
                        e -> (e instanceof Enemy || e instanceof PowerArmorEntity other && other.isRogue()) && canAttack(suit, e, false)
                                && e.distanceToSqr(centre) < range * range
                                && suit.getSensing().hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(suit))).orElse(null);
    }

    /** How far a rogue sees its victims, blocks. */
    public static final double ROGUE_SIGHT = 24;

    /**
     * A rogue's next victim (design sec. 8: it attacks any mob or player on sight): the nearest player it can see (a
     * player in a suit: the suit), else the nearest mob it can see. Never other rogues; creative and spectating players
     * can't be seen as enemies; mobs under water are left alone (bullets fizzle there).
     */
    @Nullable
    public static LivingEntity pickRogue(PowerArmorEntity suit) {
        AABB box = suit.getBoundingBox().inflate(ROGUE_SIGHT, 8, ROGUE_SIGHT);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : suit.level().getEntitiesOfClass(Player.class, box, p -> p.isAlive() && p.canBeSeenAsEnemy())) {
            LivingEntity victim = player.getVehicle() instanceof PowerArmorEntity ridden ? ridden : player;
            double d = victim.distanceToSqr(suit);
            if (d < bestDist && d < ROGUE_SIGHT * ROGUE_SIGHT && suit.getSensing().hasLineOfSight(victim)) {
                best = victim;
                bestDist = d;
            }
        }
        if (best != null) {
            return best;
        }
        return suit.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, e -> e != suit && e.isAlive()
                        && !(e instanceof PowerArmorEntity other && (other.isRogue() || other.isVehicle())) && !e.isUnderWater()
                        && e.distanceToSqr(suit) < ROGUE_SIGHT * ROGUE_SIGHT && suit.getSensing().hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(suit))).map(e -> (LivingEntity) e).orElse(null);
    }

    /** Was this target provoked (so non-monsters stay valid while the fight lasts)? */
    public static boolean isProvoked(PowerArmorEntity suit, LivingEntity target) {
        Player owner = suit.getOwner();
        if (owner != null && (owner.getLastHurtByMob() == target
                || owner.getLastHurtMob() == target && owner.tickCount - owner.getLastHurtMobTimestamp() < PROVOKED_TICKS)) {
            return true;
        }
        return suit.getLastHurtByMob() == target && suit.tickCount - suit.getLastHurtByMobTimestamp() < PROVOKED_TICKS;
    }

    /**
     * Would a burst from the suit's eye to {@code aim} hit someone it must not hurt first? Bullets stop at the first thing
     * they touch, so anything living in the way that isn't a valid target blocks the shot (the owner, a villager, a cow).
     * Boxes are grown a little for the barrage's spread.
     */
    public static boolean lineOfFireClear(PowerArmorEntity suit, LivingEntity target, Vec3 aim) {
        Vec3 eye = suit.getEyePosition();
        if (!(suit.level() instanceof ServerLevel level)) {
            return false;
        }
        for (Entity e : level.getEntities(suit, new AABB(eye, aim).inflate(1.0), e -> e != target && e instanceof LivingEntity && e.isAlive()
                && !e.isSpectator() && !(e instanceof PowerArmorEntity other && other.hasPassenger(p -> p == target)))) {
            if (canAttack(suit, e, false)) {
                continue; // another monster in front: hitting it is fine
            }
            AABB box = e.getBoundingBox().inflate(0.35);
            if (box.contains(eye) || box.clip(eye, aim).isPresent()) { // (clip misses a line that starts inside the box)
                return false;
            }
        }
        return true;
    }

    /** The flamethrower's version (Phase 11): the stream fans out to about a block and a half and burns what's round the
     *  target, so friends must be well clear of the line (a block) and not within 2.5 blocks of the target. */
    public static boolean flameClear(PowerArmorEntity suit, LivingEntity target, Vec3 aim) {
        Vec3 eye = suit.getEyePosition();
        if (!(suit.level() instanceof ServerLevel level)) {
            return false;
        }
        for (Entity e : level.getEntities(suit, new AABB(eye, aim).inflate(3.0), e -> e != target && e instanceof LivingEntity && e.isAlive()
                && !e.isSpectator() && !(e instanceof PowerArmorEntity other && other.hasPassenger(p -> p == target)))) {
            if (canAttack(suit, e, false)) {
                continue;
            }
            AABB box = e.getBoundingBox().inflate(1.0);
            if (box.contains(eye) || box.clip(eye, aim).isPresent() || e.distanceTo(target) < 2.5) {
                return false;
            }
        }
        return true;
    }

    private SuitTargets() {}

    /** Server: the world's pvp game rule (players may hurt each other). */
    public static boolean pvpAllowed(PowerArmorEntity suit) {
        return suit.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.PVP);
    }

    /** Mobs that fly instead of walking (ghasts, phantoms): they're never "on the ground", so the EM guard may grab
     *  them in the air. (1.21.1 had a FlyingMob class for these; 1.21.6 removed it.) */
    public static boolean isFlyer(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.monster.Ghast || e instanceof net.minecraft.world.entity.monster.Phantom;
    }
}
