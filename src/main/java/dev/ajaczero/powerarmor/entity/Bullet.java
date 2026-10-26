package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The chaingun's bullet: a hot tracer that flies straight at 80 blocks/s, hurts the first mob it hits and sparks off
 * blocks (chips, sparks, a wisp of smoke; it never breaks or sticks in them). Fizzles in water or after 1.5 s.
 *
 * <p>Bullets ignore the half-second of immunity a mob gets after being hurt (otherwise four of every five barrage
 * bullets would do nothing), and their damage type is in the no_knockback tag: vanilla's shove per hit would launch
 * mobs across the map at ten hits a second. Each bullet pushes by its own strength instead (heavy single shots more).
 *
 * <p>Where it flies vs where it's drawn: the bullet itself flies from the pilot's eye along the crosshair line, so what
 * the pilot sees in the crosshair is what it hits (from the gun's muzzle, off to the lower left, a fence or a block edge
 * could be in the way that the pilot can't see). Every client draws it coming out of the gun instead: from the muzzle
 * it sees (the cockpit viewmodel's for the pilot in first person, the suit's real muzzle for everyone else) along a
 * straight line that joins the real path at the target. See {@link #visualShift}.
 *
 * <p>Both sides move it every tick (smooth); only the server decides hits. Drawn by BulletRenderer.
 */
public class Bullet extends Projectile {
    public static final double SPEED = 4.0;        // blocks per tick
    private static final int LIFETIME = 30;
    /** "powerarmor:bullet", defined in data/powerarmor/damage_type/bullet.json (death message, projectile and
     *  no_knockback tags). */
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "bullet"));

    private float damage = 3F;
    private float knockback;

    public Bullet(EntityType<? extends Bullet> type, Level level) {
        super(type, level);
    }

    public Bullet(Level level, Entity owner, Vec3 from, Vec3 velocity, float damage, float knockback) {
        this(ModEntities.BULLET.get(), level);
        this.setOwner(owner);
        this.setPos(from);
        this.setDeltaMovement(velocity);
        this.damage = damage;
        this.knockback = knockback;
        ProjectileUtil.rotateTowardsMovement(this, 1F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public float getDamage() {
        return this.damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** Client: the drawn path. Set on the first client tick: where the real path starts (the pilot's eye) and goes,
     *  how far away its target is, and the gap from there to the muzzle this client draws it from. */
    private Vec3 pathStart, pathDir, gap = Vec3.ZERO;
    private float convergeDistance = 1F;
    private boolean pathKnown;

    /** Client: the first tick or the first frame, whichever comes first (the bullet is still at its spawn point). */
    private void learnPath() {
        if (this.pathKnown) {
            return;
        }
        this.pathKnown = true;
        this.pathStart = this.position();
        this.pathDir = this.getDeltaMovement().normalize();
        // Fired by a pilot (owner = the player in the suit) or by a suit's machine mind (owner = the suit itself).
        LivingEntity operator = this.getOwner() instanceof LivingEntity owner ? owner : null;
        PowerArmorEntity suit = operator instanceof PowerArmorEntity self ? self
                : operator != null && operator.getVehicle() instanceof PowerArmorEntity ridden ? ridden : null;
        if (suit != null) {
            this.gap = SocketAim.visualMuzzle.apply(suit, operator).subtract(this.pathStart);
            Vec3 target = SocketAim.aimPoint(suit, operator, 96);
            this.convergeDistance = (float) Math.max(2.0, target.distanceTo(this.pathStart));
        }
    }

    /** Client: how far the bullet has flown along its real path (at {@code position}). */
    private double travelled(Vec3 position) {
        this.learnPath();
        return position.distanceTo(this.pathStart);
    }

    /**
     * Client: offset from where the bullet really is (at {@code position}) to where it's drawn. The drawn bullet starts
     * at the muzzle (start + gap) and moves in a straight line to the real path's target, where the offset is gone:
     * drawn(s) = start + dir * s + gap * (1 - s / D) for flight distance s and target distance D.
     */
    public Vec3 visualShift(Vec3 position) {
        double s = this.travelled(position);
        return s >= this.convergeDistance ? Vec3.ZERO : this.gap.scale(1 - s / this.convergeDistance);
    }

    /** Client: the direction the drawn bullet moves in (the streak points along it). */
    public Vec3 visualDirection(Vec3 position) {
        if (this.travelled(position) >= this.convergeDistance) {
            return this.getDeltaMovement().normalize();
        }
        return this.pathDir.scale(this.convergeDistance).subtract(this.gap).normalize();
    }

    /** Client: how far the drawn bullet is from the muzzle it started at (the streak never reaches back past it). */
    public double visualTravelled(Vec3 position) {
        double s = this.travelled(position);
        if (s >= this.convergeDistance) {
            return Double.MAX_VALUE;
        }
        return s / this.convergeDistance * this.pathDir.scale(this.convergeDistance).subtract(this.gap).length();
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            this.learnPath();
        }
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
            if (this.isRemoved()) {
                return;
            }
        }
        this.setPos(this.position().add(this.getDeltaMovement()));
        ProjectileUtil.rotateTowardsMovement(this, 1F);
        if (!this.level().isClientSide() && (this.tickCount > LIFETIME || this.isInWater())) {
            if (this.isInWater() && this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), 3, 0.05, 0.05, 0.05, 0.02);
            }
            this.discard();
        }
    }

    /** Never hit the suit that fired us (the pilot is its passenger, or the suit itself is the owner). */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner()
                && !(target instanceof PowerArmorEntity suit && suit.hasPassenger(e -> e == this.getOwner()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = result.getEntity();
        // Every bullet counts (see the class comment): the bullet damage type is in minecraft:bypasses_cooldown, so
        // vanilla's half-second hit immunity doesn't swallow the rest of a burst.
        // Careful: despite the parameter names, vanilla passes (projectile, shooter), e.g. source(FIREBALL, fireball, thrower).
        DamageSource source = this.damageSources().source(BULLET, this, this.getOwner());
        if (target.hurtServer(level, source, this.damage) && target instanceof LivingEntity living && this.knockback > 0.05F) {
            Vec3 push = this.getDeltaMovement().multiply(1, 0, 1).normalize();
            living.knockback(0.45 * this.knockback, -push.x, -push.z, source, this.damage);
        }
        Vec3 at = result.getLocation();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.2);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); // lets targets, bells etc. react; never breaks the block
        if (this.level() instanceof ServerLevel level) {
            Vec3 at = result.getLocation();
            var state = level.getBlockState(result.getBlockPos());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 5, 0.08, 0.08, 0.08, 0.12);
            level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.04);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.01);
            level.playSound(null, at.x, at.y, at.z, state.getSoundType(level, result.getBlockPos(), this).getHitSound(),
                    SoundSource.PLAYERS, 0.6F, 1.3F + this.random.nextFloat() * 0.3F);
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putFloat("Knockback", this.knockback);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloatOr("Damage", 0F);
        this.knockback = tag.getFloatOr("Knockback", 0F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }
}
