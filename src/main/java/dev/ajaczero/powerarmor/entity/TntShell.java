package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The TNT launcher's round (Phase 11, 2026-10-25): a primed block of TNT that flies in an arc and goes off the moment
 * it hits anything, a block or a mob (user: "explode on hit"). If it never hits anything (shot into the sky) it goes
 * off after {@link #FUSE} ticks, like the TNT it is.
 *
 * <p>The blast is real TNT: vanilla's explosion at strength 4, breaking blocks unless the config says not to. Like a
 * TNT block it obeys the {@code tnt_explodes} game rule (off: the round only fizzles), and protection mods see it as an
 * ordinary explosion (NeoForge's ExplosionEvent), so claims keep it out. The pilot (its owner) gets the credit: vanilla
 * blames a projectile's explosion on whoever fired it.
 *
 * <p>Like the chaingun's bullets it flies from the operator's eye along the crosshair (what you aim at is what it
 * flies at) and every client draws it coming out of the launcher's muzzle instead, joining the real path within
 * {@link #CONVERGE} blocks ({@link #visualShift}). Both sides move it every tick; only the server decides hits.
 * Drawn by TntShellRenderer (a small tumbling TNT block, flashing like primed TNT).
 */
public class TntShell extends Projectile {
    /** Launch speed, blocks per tick (30 blocks/s): slow enough to see it arc, fast enough to aim straight up close. */
    public static final double SPEED = 1.5;
    /** Gravity and air drag per tick: an arrow's (it drops about 2 blocks over 20 blocks). */
    private static final double GRAVITY = 0.05, DRAG = 0.99, WATER_DRAG = 0.8;
    /** Ticks until it goes off in the air if it hits nothing (5 s). */
    static final int FUSE = 100;
    /** Blocks over which the drawn round moves from the muzzle onto its real path. */
    private static final double CONVERGE = 4.0;

    private float power = 4F;
    private boolean breaksBlocks = true;

    public TntShell(EntityType<? extends TntShell> type, Level level) {
        super(type, level);
    }

    public TntShell(Level level, Entity owner, Vec3 from, Vec3 velocity, float power, boolean breaksBlocks) {
        this(ModEntities.TNT_SHELL.get(), level);
        this.setOwner(owner);
        this.setPos(from);
        this.setDeltaMovement(velocity);
        this.power = power;
        this.breaksBlocks = breaksBlocks;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    // ---- where it's drawn (client) -------------------------------------------------------------------

    /** Client: where the real path starts (the operator's eye) and the gap from there to the muzzle this client draws
     *  it from. Learnt on the first tick or frame, while the round is still at its spawn point. */
    private Vec3 pathStart, gap = Vec3.ZERO;
    private boolean pathKnown;

    private void learnPath() {
        if (this.pathKnown) {
            return;
        }
        this.pathKnown = true;
        this.pathStart = this.position();
        // Fired by a pilot: the owner is the player in the suit.
        if (this.getOwner() instanceof LivingEntity operator && operator.getVehicle() instanceof PowerArmorEntity suit) {
            this.gap = SocketAim.visualMuzzle.apply(suit, operator).subtract(this.pathStart);
        }
    }

    /** Client: offset from where the round really is to where it's drawn: the whole gap at the muzzle, none after
     *  {@link #CONVERGE} blocks of flight. */
    public Vec3 visualShift(Vec3 position) {
        this.learnPath();
        double s = position.distanceTo(this.pathStart);
        return s >= CONVERGE ? Vec3.ZERO : this.gap.scale(1 - s / CONVERGE);
    }

    // ---- flight -----------------------------------------------------------------------------------------

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
        double drag = this.isInWater() ? WATER_DRAG : DRAG;
        this.setDeltaMovement(this.getDeltaMovement().scale(drag).add(0, -GRAVITY, 0));
        if (this.level().isClientSide()) {
            // the lit fuse: a wisp of smoke trailing off it, like a primed TNT block's
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0, 0);
        } else if (this.tickCount >= FUSE) {
            this.explode(this.position());
        }
    }

    /** Never hit the suit that fired us (its pilot is the owner). */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner()
                && !(target instanceof PowerArmorEntity suit && suit.hasPassenger(e -> e == this.getOwner()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        this.explode(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); // targets, bells etc. react first
        // Go off just in front of the face it hit (an explosion centred inside the block would shield nothing on its side).
        Vec3 back = this.getDeltaMovement().normalize().scale(0.1);
        this.explode(result.getLocation().subtract(back));
    }

    /** Server: boom (once), where it hit. */
    private void explode(Vec3 at) {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        this.discard();
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) {
            // The world forbids TNT: a dud, like a TNT block that won't go off.
            level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.02);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }
        // Explosion.getDefaultDamageSource blames a projectile's owner: the pilot gets the kills.
        DamageSource source = Explosion.getDefaultDamageSource(level, this);
        level.explode(this, source, null, at.x, at.y, at.z, this.power, false,
                this.breaksBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    public float getPower() {
        return this.power;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Power", this.power);
        tag.putBoolean("BreaksBlocks", this.breaksBlocks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        this.power = tag.getFloatOr("Power", 4F);
        this.breaksBlocks = tag.getBooleanOr("BreaksBlocks", true);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    /** Gravity is applied in tick (an arrow's arc), not by vanilla's own. */
    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
