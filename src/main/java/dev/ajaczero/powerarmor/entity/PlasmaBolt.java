package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.PlasmaCannonItem;
import dev.ajaczero.powerarmor.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The plasma cannon's bolt: flies straight (no gravity) at 60 blocks/s, damages the first thing it hits,
 * never breaks blocks, fizzles after 1.5 s. Both sides move it every tick (so it looks smooth); only the
 * server decides hits and damage. The glowing ball is drawn by client/PlasmaBoltRenderer.
 */
public class PlasmaBolt extends Projectile {
    private static final double SPEED = 3.0;       // blocks per tick
    private static final int LIFETIME = 30;
    /** "powerarmor:plasma", defined in data/powerarmor/damage_type/plasma.json (death message, projectile tag). */
    public static final ResourceKey<DamageType> PLASMA = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "plasma"));
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 1.0F), 1.2F);

    public PlasmaBolt(EntityType<? extends PlasmaBolt> type, Level level) {
        super(type, level);
    }

    public PlasmaBolt(Level level, Entity owner, Vec3 from, Vec3 direction) {
        this(ModEntities.PLASMA_BOLT.get(), level);
        this.setOwner(owner);
        this.setPos(from);
        this.setDeltaMovement(direction.scale(SPEED));
        ProjectileUtil.rotateTowardsMovement(this, 1F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }
        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        if (this.level().isClientSide()) {
            // A streak of glowing dust along this tick's path, plus the odd spark.
            for (int i = 0; i < 6; i++) {
                Vec3 p = from.add(motion.scale(i / 6.0));
                this.level().addParticle(TRAIL, p.x, p.y, p.z, 0, 0, 0);
            }
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, motion.x * 0.02, motion.y * 0.02, motion.z * 0.02);
            }
        }
        this.setPos(from.add(motion));
        ProjectileUtil.rotateTowardsMovement(this, 1F);
        if (!this.level().isClientSide() && (this.tickCount > LIFETIME || this.isInWater())) {
            this.fizzle();
        }
    }

    /** Never hit the suit that fired us (the pilot is its passenger, so vanilla's same-vehicle rule covers it). */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof PowerArmorEntity suit && suit.hasPassenger(e -> e == this.getOwner()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        // Careful: despite the parameter names, vanilla passes (projectile, shooter), e.g. source(FIREBALL, fireball, thrower).
        DamageSource source = this.damageSources().source(PLASMA, this, this.getOwner());
        target.hurt(source, PlasmaCannonItem.DAMAGE);
        if (target instanceof LivingEntity living) {
            living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), 20)); // scorched for a second
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); // lets targets, bells etc. react; never breaks the block
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            this.impact(result.getLocation());
        }
    }

    private void impact(Vec3 at) {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.15, 0.15, 0.15, 0.4);
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(TRAIL, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.7F, 1.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
        this.discard();
    }

    private void fizzle() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.01);
        }
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // can't be punched back like a ghast fireball
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    /** Visible from far away (it's a bright light). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
