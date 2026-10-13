package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.RivetGunItem;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The rivet gun's red-hot rivet. It flies fast (60 blocks/s) with a slight drop (the gun aims the arc so it still
 * lands on the crosshair, see RivetGunItem), hurts the first mob it hits (and is lost), and sticks in blocks: there it
 * cools down, and after 10 s, or when its block is broken, it drops back out as the iron nugget it was made from. A
 * player on foot can also pull it out by walking into it. Rivets fired in creative mode don't drop anything.
 *
 * <p>Both sides move it every tick (smooth); only the server decides hits, damage and drops. Drawn by RivetRenderer.
 */
public class Rivet extends Projectile {
    public static final double SPEED = 3.0;        // blocks per tick
    public static final double GRAVITY = 0.02;     // blocks per tick, per tick (arrows: 0.05)
    private static final int FLIGHT_TICKS = 100;   // gone if it hasn't hit anything by then
    public static final int STUCK_TICKS = 200;     // 10 s in a block, then it drops out
    /** "powerarmor:rivet", defined in data/powerarmor/damage_type/rivet.json (death message, projectile tag). */
    public static final ResourceKey<DamageType> RIVET = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "rivet"));
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.12F), 0.55F);
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(Rivet.class, EntityDataSerializers.BOOLEAN);

    private int stuckTicks;
    private BlockPos stuckIn;
    private boolean dropsNugget = true;

    public Rivet(EntityType<? extends Rivet> type, Level level) {
        super(type, level);
    }

    public Rivet(Level level, Entity owner, Vec3 from, Vec3 velocity, boolean dropsNugget) {
        this(ModEntities.RIVET.get(), level);
        this.setOwner(owner);
        this.setPos(from);
        this.setDeltaMovement(velocity);
        this.dropsNugget = dropsNugget;
        ProjectileUtil.rotateTowardsMovement(this, 1F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STUCK, false);
    }

    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    /** Both sides: ticks since it stuck (the renderer cools it down over the first 3 s). */
    public int getStuckTicks() {
        return this.stuckTicks;
    }

    /** Ticks over which a rivet drawn from the pilot's on-screen muzzle merges into its real path. */
    private static final float CONVERGE_TICKS = 4F;

    /** Client: draw offset for the pilot's own shots in first person: the cockpit viewmodel's muzzle isn't where the
     *  suit's muzzle is, so the drawn rivet starts at the one on screen and merges into its real path. Zero for
     *  everyone else, once stuck, and once it has converged. */
    public Vec3 visualOffset(float age) {
        if (age >= CONVERGE_TICKS || this.isStuck() || !(this.getOwner() instanceof Player pilot)
                || !(pilot.getVehicle() instanceof PowerArmorEntity suit)) {
            return Vec3.ZERO;
        }
        Vec3 gap = SocketAim.visualMuzzle.apply(suit, pilot).subtract(SocketAim.muzzlePosition(suit, pilot));
        return gap.scale(1 - age / CONVERGE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isStuck()) {
            this.tickStuck();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
            if (this.isStuck() || this.isRemoved()) {
                return;
            }
        }
        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        if (this.level().isClientSide()) {
            this.trail(from, motion);
        }
        this.setPos(from.add(motion));
        if (this.isInWater()) {
            this.setDeltaMovement(motion.scale(0.6)); // quenched: slows right down and sinks
            if (this.tickCount % 4 == 0) {
                this.level().addParticle(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), 0, 0.05, 0);
            }
        } else {
            this.setDeltaMovement(motion.x, motion.y - GRAVITY, motion.z);
        }
        ProjectileUtil.rotateTowardsMovement(this, 1F);
        if (!this.level().isClientSide() && this.tickCount > FLIGHT_TICKS) {
            this.discard();
        }
    }

    /** Client: a thin streak of hot sparks along this tick's path. Not right in front of the pilot's eyes (dust lingers
     *  for a second and turns into big blobs next to the camera). */
    private void trail(Vec3 from, Vec3 motion) {
        Vec3 shift = this.visualOffset(this.tickCount);
        Entity owner = this.getOwner();
        for (int i = 0; i < 3; i++) {
            Vec3 p = from.add(motion.scale(i / 3.0)).add(shift);
            if (owner != null && owner.getEyePosition().distanceToSqr(p) < 1.6 * 1.6) {
                continue;
            }
            this.level().addParticle(TRAIL, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    private void tickStuck() {
        this.stuckTicks++;
        if (this.level().isClientSide()) {
            return;
        }
        boolean loose = this.stuckIn != null && this.level().getBlockState(this.stuckIn).isAir();
        if (loose || this.stuckTicks >= STUCK_TICKS) {
            this.dropOut();
        }
    }

    /** Server: fall out of the block as an iron nugget (nothing for creative-mode rivets). */
    private void dropOut() {
        if (this.dropsNugget && this.level() instanceof ServerLevel level) {
            ItemEntity item = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.IRON_NUGGET));
            item.setDeltaMovement(0, 0.1, 0);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        this.discard();
    }

    /** A player on foot walking into a stuck rivet pulls it out (like an arrow). */
    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide() && this.isStuck() && this.dropsNugget && player.getVehicle() == null
                && player.getInventory().add(new ItemStack(Items.IRON_NUGGET))) {
            player.take(this, 1);
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
        Vec3 motion = this.getDeltaMovement();
        // Careful: despite the parameter names, vanilla passes (projectile, shooter), e.g. source(FIREBALL, fireball, thrower).
        DamageSource source = this.damageSources().source(RIVET, this, this.getOwner());
        if (target.hurt(source, RivetGunItem.damage()) && target instanceof LivingEntity living) {
            Vec3 push = motion.multiply(1, 0, 1).normalize();
            living.knockback(0.5, -push.x, -push.z);
        }
        Vec3 at = result.getLocation();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.25F, 2.0F);
        this.discard(); // a rivet that hit a mob is lost
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); // lets targets, bells etc. react; never breaks the block
        // Stop at the surface: the renderer draws the shank forward of this point (into the block) and the head behind.
        Vec3 dir = this.getDeltaMovement().normalize();
        this.setPos(result.getLocation().subtract(dir.scale(0.02)));
        this.setDeltaMovement(Vec3.ZERO);
        this.stuckIn = result.getBlockPos();
        this.entityData.set(STUCK, true);
        if (this.level() instanceof ServerLevel level) {
            Vec3 at = result.getLocation();
            var state = level.getBlockState(result.getBlockPos());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.15);
            level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.03);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.3F, 1.9F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Stuck", this.isStuck());
        tag.putInt("StuckTicks", this.stuckTicks);
        tag.putBoolean("DropsNugget", this.dropsNugget);
        if (this.stuckIn != null) {
            tag.put("StuckIn", NbtUtils.writeBlockPos(this.stuckIn));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(STUCK, tag.getBoolean("Stuck"));
        this.stuckTicks = tag.getInt("StuckTicks");
        this.dropsNugget = !tag.contains("DropsNugget") || tag.getBoolean("DropsNugget");
        this.stuckIn = NbtUtils.readBlockPos(tag, "StuckIn").orElse(null);
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
        return true; // gravity is applied in tick() (vanilla's would double it)
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }
}
