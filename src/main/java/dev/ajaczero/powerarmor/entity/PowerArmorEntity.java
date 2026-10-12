package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.PowerArmorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The power armor suit. It is a living entity (it has health and armor and can die) that a player
 * can ride. While ridden, the player steers it like a horse, and the player's model is hidden.
 *
 * Minecraft runs every entity twice: on the server (the real game state) and on each client
 * (a copy used for rendering). {@code level().isClientSide()} tells you which one you are in.
 */
public class PowerArmorEntity extends Mob implements GeoEntity, PlayerRideableJumping {
    public static final int MAX_HEALTH = 100;

    // ---- Movement tuning -----------------------------------------------------------------
    // Attribute speed -> blocks per second on flat ground is about x42.3 for ridden entities
    // (0.98 input x friction 0.546 terminal velocity x 20 ticks).
    private static final double BLOCKS_PER_SECOND_PER_SPEED = 42.3;
    private static final double WALK_SPEED = 0.0837;      // ~3.54 blocks/s (a player walks at 4.3)
    private static final float SPRINT_MULTIPLIER = 2.41F; // ~8.5 blocks/s (a player sprints at 5.6)

    // The ground speed each Blockbench animation was authored for (blocks/s, measured from the
    // planted foot). Playing them at movementSpeed / authoredSpeed keeps the feet from sliding.
    private static final double WALK_ANIM_AUTHORED = 3.54;
    private static final double RUN_ANIM_AUTHORED = 8.52;
    private static final double WALK_ANIM_RATE = WALK_SPEED * BLOCKS_PER_SECOND_PER_SPEED / WALK_ANIM_AUTHORED;
    private static final double RUN_ANIM_RATE = WALK_SPEED * SPRINT_MULTIPLIER * BLOCKS_PER_SECOND_PER_SPEED / RUN_ANIM_AUTHORED;

    // ---- Airborne tuning (client-side animation only) ----------------------------------------
    /** Ticks in the air before switching to the fall pose (stepping off one block shouldn't). */
    private static final int FALL_POSE_AFTER_TICKS = 4;
    /** Ticks in the air needed to play the heavy landing (a jump, or a drop of ~2+ blocks). */
    private static final int LAND_AFTER_TICKS = 10;
    /** Length of the land animation (0.6 s), and how much of it to keep when walking away. */
    private static final int LAND_TICKS = 12;
    private static final int LAND_TICKS_WHILE_MOVING = 5;

    // ---- Jumping ---------------------------------------------------------------------------
    /** Delay between releasing jump and leaving the ground, so the push-off in the jump animation
     *  (crouch -> legs extend at 0.1 s) happens while the feet are still on the ground. */
    private static final int LAUNCH_DELAY_TICKS = 2;
    /** How long the jump animation is forced after release, covering the delay + takeoff. */
    private static final int JUMP_ANIM_HOLD_TICKS = 6;

    // ---- Turning (legs vs torso) ---------------------------------------------------------------
    /** The torso can twist this far from the legs before the legs are dragged along. */
    private static final float MAX_TWIST = 90F;
    /** Standing still, the legs shuffle round once the view is this far off... */
    private static final float IDLE_REALIGN_START = 50F;
    /** ...and stop once they're this close. */
    private static final float IDLE_REALIGN_END = 8F;
    /** Leg turn rates, degrees per tick. */
    private static final float LEG_TURN_MOVING = 18F;
    private static final float LEG_TURN_IDLE = 7F;

    /** Synced to every client: bumped by the server each time the pilot releases jump, so other
     *  players see the jump animation start at the same moment as the pilot does. */
    private static final EntityDataAccessor<Integer> JUMP_COUNTER =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);

    /** Ticks to stay around after dying so the death animation (2.68 s) can finish. */
    private static final int DEATH_TICKS = 60;

    // ---- Animations (names from the exported Blockbench animation file) --------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.power_armor.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.power_armor.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.power_armor.run");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.power_armor.death");
    private static final RawAnimation HIT = RawAnimation.begin().thenPlay("animation.power_armor.hit");
    // jump plays once (push-off + tuck) and flows straight into the airborne loop
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlay("animation.power_armor.jump").thenLoop("animation.power_armor.fall");
    private static final RawAnimation FALL = RawAnimation.begin().thenLoop("animation.power_armor.fall");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlay("animation.power_armor.land");
    private static final RawAnimation JUMP_CHARGE = RawAnimation.begin().thenPlayAndHold("animation.power_armor.jump_charge");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /** Pending jump strength 0..1, set by the jump bar when the pilot releases space. */
    private float pendingJump;
    /** Ticks left before a pending jump actually launches. */
    private int launchDelay;
    /** Legs are turning to catch up with the torso while standing still. */
    private boolean realigning;
    /** Client only: smoothed horizontal speed in blocks per second, used to pick walk/run/idle. */
    private double visualSpeed;
    /** Client only: airborne bookkeeping for the jump / fall / land animations. */
    private int airTicks;
    private boolean jumped;
    private int landTicks;
    /** Client only: jump bar charge of the local pilot while holding space (set by PilotView). */
    private float clientJumpCharge;
    /** Client only: forces the jump animation for a few ticks after release. */
    private int jumpAnimTicks;
    private int lastJumpCounter;

    public PowerArmorEntity(EntityType<? extends PowerArmorEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(JUMP_COUNTER, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.STEP_HEIGHT, 1.1); // walks up full blocks without jumping
    }

    // =========================================================================================
    // Getting in and out
    // =========================================================================================

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isVehicle() || this.isDeadOrDying()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            if (player.isSecondaryUseActive()) {
                // Sneak + right-click: pack the empty suit back into an item (keeps its damage).
                ItemStack stack = PowerArmorItem.fromSuit(this);
                if (!player.addItem(stack)) {
                    this.spawnAtLocation(stack);
                }
                this.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.8F);
                this.discard();
            } else {
                player.startRiding(this);
                this.playSound(SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.7F);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    /** The first player passenger drives the suit. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    /** When the pilot gets out, put them behind the suit (or beside it) instead of on its head. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (float angle : new float[] {180F, 90F, -90F, 0F}) {
            Vec3 offset = getCollisionHorizontalEscapeVector(this.getBbWidth(), passenger.getBbWidth(), this.getYRot() + angle);
            Vec3 spot = findDismountSpot(this.getX() + offset.x, this.getZ() + offset.z, passenger);
            if (spot != null) {
                return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Nullable
    private Vec3 findDismountSpot(double x, double z, LivingEntity passenger) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, this.getBoundingBox().minY, z);
        double maxY = this.getBoundingBox().minY + 1.5;
        for (Pose pose : passenger.getDismountPoses()) {
            pos.setY((int) Math.floor(this.getBoundingBox().minY));
            for (; pos.getY() < maxY; pos.move(Direction.UP)) {
                double floor = this.level().getBlockFloorHeight(pos);
                if (DismountHelper.isBlockFloorValid(floor)) {
                    Vec3 spot = new Vec3(x, pos.getY() + floor, z);
                    AABB box = passenger.getLocalBoundsForPose(pose).move(spot);
                    if (DismountHelper.canDismountTo(this.level(), passenger, box)) {
                        passenger.setPose(pose);
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    // =========================================================================================
    // Driving: vanilla calls these while a player controls the entity (same hooks horses use)
    // =========================================================================================

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float strafe = player.xxa * 0.5F;  // A/D
        float forward = player.zza;        // W/S
        if (forward < 0) {
            forward *= 0.5F;               // backing up is slow
        }
        return new Vec3(strafe, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float speed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return player.isSprinting() ? speed * SPRINT_MULTIPLIER : speed;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        // yRot/xRot/yHeadRot = where the pilot looks. WASD moves relative to yRot, and the model's
        // torso aims along it. yBodyRot (the legs) is handled separately in tickHeadTurn().
        this.setRot(player.getYRot(), player.getXRot());
        this.yHeadRot = this.getYRot();

        if (this.isControlledByLocalInstance() && this.pendingJump > 0) {
            if (this.launchDelay > 0) {
                this.launchDelay--;
            } else {
                if (this.onGround()) {
                    Vec3 motion = this.getDeltaMovement();
                    this.setDeltaMovement(motion.x, 0.42 + 0.3 * this.pendingJump, motion.z); // up to ~2.3 blocks
                    this.hasImpulse = true;
                    this.playSound(SoundEvents.PISTON_EXTEND, 0.8F, 0.6F);
                }
                this.pendingJump = 0;
            }
        }
    }

    /**
     * Vanilla calls this every tick (server and clients) to turn the body. For a piloted suit we
     * turn the legs ourselves: toward the direction of travel while moving (or away from it when
     * backing up), and in small catch-up shuffles when standing still. The renderer then twists
     * the torso by (look - legs), so the upper body always faces where the pilot looks.
     */
    @Override
    protected float tickHeadTurn(float movementYaw, float animStep) {
        if (!this.isVehicle()) {
            this.realigning = false;
            return super.tickHeadTurn(movementYaw, animStep);
        }
        float look = this.getYHeadRot();
        double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
        boolean moving = dx * dx + dz * dz > 0.0004; // > 0.4 blocks/s
        float target = this.yBodyRot;
        float step = LEG_TURN_IDLE;
        if (moving) {
            float heading = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            if (Math.abs(Mth.wrapDegrees(heading - look)) > 100F) {
                heading += 180F; // backing up: legs keep facing forward
            }
            target = heading;
            step = LEG_TURN_MOVING;
            this.realigning = false;
        } else {
            float twist = Math.abs(Mth.wrapDegrees(look - this.yBodyRot));
            if (twist > IDLE_REALIGN_START) {
                this.realigning = true;
            } else if (twist < IDLE_REALIGN_END) {
                this.realigning = false;
            }
            if (this.realigning) {
                target = look;
            }
        }
        this.yBodyRot = Mth.approachDegrees(this.yBodyRot, target, step);
        // Never let the torso twist past its limit: drag the legs along instead.
        float twist = Mth.wrapDegrees(look - this.yBodyRot);
        if (Math.abs(twist) > MAX_TWIST) {
            this.yBodyRot = look - Math.signum(twist) * MAX_TWIST;
        }
        return animStep;
    }

    /** Client: true while the legs are shuffling round to face the view (plays a step cycle). */
    public boolean isRealigning() {
        return this.realigning;
    }

    /** Lets the pilot sprint while inside (vanilla blocks sprinting on most vehicles). */
    @Override
    public boolean canSprint() {
        return true;
    }

    // Jumping uses the horse jump bar: hold space to charge (the suit crouches), release to jump.
    // Called on the pilot's client when space is released.
    @Override
    public void onPlayerJump(int jumpPower) {
        if (jumpPower > 0 && this.onGround()) {
            this.pendingJump = jumpPower >= 90 ? 1.0F : 0.4F + 0.6F * jumpPower / 90.0F;
            this.launchDelay = LAUNCH_DELAY_TICKS;
            this.jumpAnimTicks = JUMP_ANIM_HOLD_TICKS;
        }
    }

    @Override
    public boolean canJump() {
        return this.isAlive();
    }

    /** No charging while airborne or while a launch is already pending. */
    @Override
    public int getJumpCooldown() {
        return this.onGround() && this.pendingJump == 0 ? 0 : 1;
    }

    /** Called on the server when the pilot releases jump: tell all other clients. */
    @Override
    public void handleStartJump(int jumpPower) {
        this.entityData.set(JUMP_COUNTER, this.entityData.get(JUMP_COUNTER) + 1);
    }

    /** Client: the local pilot's jump bar charge (0 = not charging). */
    public void setClientJumpCharge(float charge) {
        this.clientJumpCharge = charge;
    }

    @Override
    public void handleStopJump() {}

    // =========================================================================================
    // Toughness, damage, death
    // =========================================================================================

    /** Nobody can click the suit while it's occupied. Otherwise the pilot's camera, which sits
     *  inside the suit's hitbox, would always "look at" the suit and couldn't mine blocks.
     *  Hits meant for the suit land on the pilot instead and get redirected (PilotProtection). */
    @Override
    public boolean isPickable() {
        return !this.isVehicle() && super.isPickable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !this.level().isClientSide() && this.isAlive()) {
            this.triggerAnim("action", "hit"); // play the flinch on every client
        }
        return damaged;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance > 3) {
            this.playSound(SoundEvents.ANVIL_LAND, 0.6F, 0.5F); // heavy landing, no damage
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false; // a ton of steel doesn't get shoved around
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.ejectPassengers();
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60); // poof particles
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new ItemStack(Items.IRON_INGOT, 4 + this.random.nextInt(5)));
    }

    // A placed suit stays forever (mobs normally despawn when far from players).
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // =========================================================================================
    // Sounds
    // =========================================================================================

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.8F, 0.9F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    // =========================================================================================
    // Animation (GeckoLib). This only runs on the client.
    // =========================================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // Measure how fast we actually move (blocks/s) and smooth it to avoid flicker
            // between idle and walk.
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            double speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
            this.visualSpeed += (speed - this.visualSpeed) * 0.4;
            tickAirborneAnimationState();
        }
    }

    private void tickAirborneAnimationState() {
        if (this.landTicks > 0) {
            this.landTicks--;
        }
        if (this.jumpAnimTicks > 0) {
            this.jumpAnimTicks--;
        }
        // Another player's suit jumped (the pilot's own client already started it in onPlayerJump).
        int counter = this.entityData.get(JUMP_COUNTER);
        if (this.tickCount < 5) {
            this.lastJumpCounter = counter; // just came into view: don't replay an old jump
        }
        if (counter != this.lastJumpCounter) {
            this.lastJumpCounter = counter;
            if (!this.isControlledByLocalInstance()) {
                this.jumpAnimTicks = JUMP_ANIM_HOLD_TICKS;
            }
        }
        if (!this.isVehicle()) {
            this.clientJumpCharge = 0;
        }
        if (!this.onGround()) {
            if (this.airTicks == 0) {
                // Just left the ground. Rising fast means a jump (stepping up a block keeps us on the
                // ground, so it never gets here).
                this.jumped = this.getY() - this.yo > 0.15;
            }
            this.airTicks++;
        } else {
            if (this.airTicks >= LAND_AFTER_TICKS) {
                this.landTicks = LAND_TICKS;
            }
            this.airTicks = 0;
            this.jumped = false;
        }
    }

    private boolean isRunning() {
        return this.getControllingPassenger() instanceof Player pilot && pilot.isSprinting();
    }

    private boolean isWalking() {
        return this.visualSpeed > 0.3;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Controller 1: full-body locomotion. 5 ticks of blending between animations.
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnimation)
                .setAnimationSpeedHandler(suit -> suit.movementAnimationRate()));
        // Controller 2: one-shot upper-body reactions, started from the server with triggerAnim().
        // It is added after "movement", so for the bones it animates it wins; legs keep walking.
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("hit", HIT));
    }

    private PlayState movementAnimation(AnimationState<PowerArmorEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (!this.isVehicle()) {
            return PlayState.STOP; // empty suit: powered down, frozen in its rest pose
        }
        // Jump states blend fast (the charge crouch ends exactly where the jump starts).
        boolean jumpState = this.jumpAnimTicks > 0 || this.jumped || (this.clientJumpCharge > 0 && this.onGround());
        state.getController().transitionLength(jumpState ? 2 : 5);
        if (this.jumpAnimTicks > 0 || this.jumped) {
            return state.setAndContinue(JUMP);
        }
        if (this.clientJumpCharge > 0 && this.onGround()) {
            return state.setAndContinue(JUMP_CHARGE);
        }
        if (this.airTicks >= FALL_POSE_AFTER_TICKS) {
            return state.setAndContinue(FALL);
        }
        boolean landing = this.landTicks > 0 && !(this.isWalking() && this.landTicks <= LAND_TICKS - LAND_TICKS_WHILE_MOVING);
        if (landing) {
            return state.setAndContinue(LAND);
        }
        if (this.isWalking() || this.realigning) {
            return state.setAndContinue(this.isRunning() ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    /** Fixed rate per gait. GeckoLib scales the whole elapsed time by this, so changing it
     *  smoothly mid-loop would make the animation jump; constant values avoid that. */
    private double movementAnimationRate() {
        if (this.isDeadOrDying() || !this.isVehicle() || this.jumped || this.jumpAnimTicks > 0
                || this.airTicks >= FALL_POSE_AFTER_TICKS || this.landTicks > 0) {
            return 1.0;
        }
        if (!this.isWalking()) {
            return this.realigning ? 0.6 : 1.0; // turning on the spot: a slower step cycle
        }
        return this.isRunning() ? RUN_ANIM_RATE : WALK_ANIM_RATE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    /** Used by the renderer to know whether this suit is carrying the given entity. */
    public boolean isPilotedBy(@Nullable Entity entity) {
        return entity != null && this.hasPassenger(entity);
    }
}
