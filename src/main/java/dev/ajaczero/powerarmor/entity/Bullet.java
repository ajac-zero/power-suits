package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The chaingun's bullet: a hot tracer that flies straight at 80 blocks/s, hurts the first mob it hits and sparks off
 * blocks (chips, sparks, a wisp of smoke; it never breaks or sticks in them). Fizzles in water or after 1.5 s.
 *
 * <p>Bullets ignore the half-second of immunity a mob gets after being hurt (otherwise four of every five barrage
 * bullets would do nothing), and their damage type is in the no_knockback tag: vanilla's shove per hit would launch
 * mobs across the map at ten hits a second. Each bullet pushes by its own strength instead (heavy single shots more).
 *
 * <p>Both sides move it every tick (smooth); only the server decides hits. Drawn by BulletRenderer.
 */
public class Bullet extends Projectile {
    public static final double SPEED = 4.0;        // blocks per tick
    private static final int LIFETIME = 30;
    /** "powerarmor:bullet", defined in data/powerarmor/damage_type/bullet.json (death message, projectile and
     *  no_knockback tags). */
    public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "bullet"));

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

    /** Ticks over which a bullet drawn from the pilot's on-screen muzzle merges into its real path. */
    private static final float CONVERGE_TICKS = 3F;

    /** Client: draw offset for the pilot's own shots in first person: the cockpit viewmodel's muzzle isn't where the
     *  suit's muzzle is, so the drawn tracer starts at the one on screen and merges into its real path. Zero for
     *  everyone else and once it has converged. */
    public Vec3 visualOffset(float age) {
        if (age >= CONVERGE_TICKS || !(this.getOwner() instanceof Player pilot) || !(pilot.getVehicle() instanceof PowerArmorEntity suit)) {
            return Vec3.ZERO;
        }
        Vec3 gap = SocketAim.visualMuzzle.apply(suit, pilot).subtract(SocketAim.muzzlePosition(suit, pilot));
        return gap.scale(1 - age / CONVERGE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
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

    /** Never hit the suit that fired us (the pilot is its passenger). */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof PowerArmorEntity suit && suit.hasPassenger(e -> e == this.getOwner()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = result.getEntity();
        target.invulnerableTime = 0; // every bullet counts (see the class comment)
        // Careful: despite the parameter names, vanilla passes (projectile, shooter), e.g. source(FIREBALL, fireball, thrower).
        DamageSource source = this.damageSources().source(BULLET, this, this.getOwner());
        if (target.hurt(source, this.damage) && target instanceof LivingEntity living && this.knockback > 0.05F) {
            Vec3 push = this.getDeltaMovement().multiply(1, 0, 1).normalize();
            living.knockback(0.45 * this.knockback, -push.x, -push.z);
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
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putFloat("Knockback", this.knockback);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        this.knockback = tag.getFloat("Knockback");
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
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
