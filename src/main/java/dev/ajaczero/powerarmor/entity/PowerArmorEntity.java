package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.item.SuitLoadout;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import java.util.Map;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.neoforged.neoforge.fluids.FluidType;
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
    // Attribute speed -> blocks per second on flat ground for ridden entities. Measured in game with
    // ./gradlew runAutopilot (speed 0.1158 gave 4.88 blocks/s), so x42.14.
    private static final double BLOCKS_PER_SECOND_PER_SPEED = 42.14;
    private static final double WALK_SPEED = 0.11865;     // ~5.0 blocks/s jog (a player walks at 4.3)
    private static final float SPRINT_MULTIPLIER = 2.0F;  // ~10 blocks/s (a player sprints at 5.6)

    // The ground speed each Blockbench animation was authored for (blocks/s, measured from the
    // planted foot). Playing them at movementSpeed / authoredSpeed keeps the feet from sliding.
    private static final double WALK_ANIM_AUTHORED = 5.0;
    private static final double RUN_ANIM_AUTHORED = 9.99;
    private static final double WALK_ANIM_RATE = WALK_SPEED * BLOCKS_PER_SECOND_PER_SPEED / WALK_ANIM_AUTHORED;
    private static final double RUN_ANIM_RATE = WALK_SPEED * SPRINT_MULTIPLIER * BLOCKS_PER_SECOND_PER_SPEED / RUN_ANIM_AUTHORED;

    /** Average hip height (model px) of the jog / run cycles: yHip mean in a_walk.js / a_run.js. */
    private static final float WALK_HIP_MEAN = -3.6F;
    private static final float RUN_HIP_MEAN = -4.0F;

    // ---- Terrain ----------------------------------------------------------------------------
    /** Walking off a ledge up to this high keeps the suit planted (steps down instead of falling).
     *  2 blocks: a 3-block-tall mech steps down mountain terraces; the step easing smooths it. */
    private static final double STEP_DOWN = 2.1;
    /** Each tick the eased step offset keeps this fraction (0.65 -> ~6 ticks to settle). */
    private static final float STEP_EASE = 0.65F;

    // ---- Water (Fallout-style: the suit sinks and walks along the bottom) ----------------------
    /** Speed on the bottom relative to dry land. */
    private static final float UNDERWATER_SPEED = 0.5F;
    /** Steering while sinking / rising, relative to UNDERWATER_SPEED. */
    private static final float UNDERWATER_AIR_CONTROL = 0.3F;
    /** Downward acceleration per tick and the vertical drag: terminal sink ~2.8 blocks/s. */
    private static final double SINK_ACCEL = 0.025;
    private static final double WATER_DRAG_Y = 0.85;
    /** Horizontal drag per tick on the bottom (same as dry ground, so it feels like walking). */
    private static final double WATER_DRAG_XZ = 0.546;
    /** Jumps underwater are pushing off against water. */
    private static final double UNDERWATER_JUMP_SCALE = 0.6;

    // ---- Airborne tuning (client-side animation only) ----------------------------------------
    /** Ticks in the air before switching to the fall pose (stepping off one block shouldn't). */
    private static final int FALL_POSE_AFTER_TICKS = 6;
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

    // ---- Fuel (works like a furnace, see SuitFuel) ---------------------------------------------
    public static final int FUEL_SLOT = 0, SOCKET_SLOT = 1;
    /** Energy burnt per tick while piloted. Walking = 1, so SuitFuel values read as ticks of walking. */
    private static final float DRAIN_IDLE = 0.25F, DRAIN_MOVING = 1.0F, DRAIN_SPRINT = 2.0F;
    /** Extra energy for each jump (the legs' pistons). */
    private static final int JUMP_ENERGY = 20;
    /** Below this much energy left (30 s of walking) the suit warns: dark sputtering smoke, flashing gauge. */
    public static final int LOW_FUEL = 600;
    /** Length of the startup animation (1.4 s): the suit can't move until it has stood up. */
    private static final int STARTUP_TICKS = 28;

    /** True while the engine has energy. Movement, jumping and (later) tools need it. */
    private static final EntityDataAccessor<Boolean> POWERED =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Energy left in the item that is burning now, and what that item started with (for the gauge). */
    private static final EntityDataAccessor<Integer> BURN_LEFT =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURN_TOTAL =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** Entity id of whatever the EM projector holds (-1: nothing), so every client can draw the beam. */
    private static final EntityDataAccessor<Integer> BEAM_TARGET =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** True while a continuous socket tool runs (drill spinning, chainsaw cutting), for its animation. */
    private static final EntityDataAccessor<Boolean> TOOL_RUNNING =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Copies of the two slots, so clients can show the fuel left and (Phase 2) draw the socket tool. */
    private static final EntityDataAccessor<ItemStack> FUEL_ITEM =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> SOCKET_ITEM =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.ITEM_STACK);

    /** The suit's own two slots: fuel and the right-arm socket. Real contents only exist on the server. */
    private final SimpleContainer inventory = new SimpleContainer(2);
    /** Server: energy left in the burning item (fractional, idle drains 0.25 per tick). */
    private float burnLeft;
    private int burnTotal;
    /** Both sides: power transitions seen, for animations, sounds and smoke. */
    private boolean seenPower;
    private boolean lastPowered;
    /** The suit was already off when we first saw it: hold the slumped pose instead of playing the slump. */
    private boolean wasOffWhenLoaded;
    private int startupTicks;
    /** Server: the pilot is holding attack (sent by the client, see ToolTriggerPayload). */
    private boolean triggerHeld;
    /** Server: ticks until the socket tool may be used again. */
    private int toolCooldown;
    /** Server: how many times the socket tool has been used (tests and dev logs). */
    private int toolUses;
    /** Server: the continuous tool currently held down (to release it when anything changes). */
    @Nullable
    private PowerTool activeContinuousTool;
    /** Server: a tool's per-suit state (items are shared by every stack, so state can't live on the item). */
    @Nullable
    private Object toolData;
    /** Client: the pilot's arm was mid-swing last tick (to catch the start of each swing). */
    private boolean pilotWasSwinging;
    /** Client: 0..1, how much the torso follows the pilot's view (eases out on shutdown, in after startup). */
    private float aimBlend;

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
    // Out of fuel: slump and hold. powered_off is the last frame of shutdown, for suits that were already off.
    private static final RawAnimation SHUTDOWN = RawAnimation.begin().thenPlayAndHold("animation.power_armor.shutdown");
    private static final RawAnimation POWERED_OFF = RawAnimation.begin().thenPlayAndHold("animation.power_armor.powered_off");
    private static final RawAnimation STARTUP = RawAnimation.begin().thenPlay("animation.power_armor.startup");
    private static final RawAnimation PLASMA_SHOT = RawAnimation.begin().thenPlay("animation.power_armor.plasma_shot");
    // Left hand (a_hand.js): carry pose while the pilot holds something, quick reach on every use.
    private static final RawAnimation HOLD_ITEM = RawAnimation.begin().thenLoop("animation.power_armor.hold_item");
    private static final RawAnimation USE_ITEM = RawAnimation.begin().thenPlay("animation.power_armor.use_item");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /** Pending jump strength 0..1, set by the jump bar when the pilot releases space. */
    private float pendingJump;
    /** Ticks left before a pending jump actually launches. */
    private int launchDelay;
    /** Legs are turning to catch up with the torso while standing still. */
    private boolean realigning;
    /** Client only: smoothed horizontal speed in blocks per second, used to pick walk/run/idle. */
    private double visualSpeed;
    /** Client only: vertical offset (blocks) that eases the camera and model over step-ups/downs. */
    private float stepOffset, stepOffsetO;
    private boolean wasOnGroundForSteps;
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
        // Whenever the slots change on the server, copy them into the synced data for clients.
        this.inventory.addListener(container -> this.syncSlots());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(JUMP_COUNTER, 0);
        builder.define(POWERED, false);
        builder.define(BURN_LEFT, 0);
        builder.define(BURN_TOTAL, 0);
        builder.define(FUEL_ITEM, ItemStack.EMPTY);
        builder.define(BEAM_TARGET, -1);
        builder.define(TOOL_RUNNING, false);
        builder.define(SOCKET_ITEM, ItemStack.EMPTY);
    }

    /** Base stats. The single source of truth: also re-applied to suits loaded from old saves. */
    private static final Map<Holder<Attribute>, Double> BASE_STATS = Map.of(
            Attributes.MAX_HEALTH, (double) MAX_HEALTH,
            Attributes.ARMOR, 12.0,
            Attributes.ARMOR_TOUGHNESS, 4.0,
            Attributes.KNOCKBACK_RESISTANCE, 1.0,
            Attributes.MOVEMENT_SPEED, WALK_SPEED,
            Attributes.STEP_HEIGHT, 1.1); // walks up full blocks without jumping

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        BASE_STATS.forEach(builder::add);
        return builder;
    }

    /**
     * Minecraft saves an entity's attribute base values with the entity. A suit placed with an
     * older version of the mod would keep its old speed forever, so after loading we reset the
     * base values to the current tuning (modifiers from effects etc. are kept).
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        BASE_STATS.forEach((attribute, value) -> {
            AttributeInstance instance = this.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        });
        this.inventory.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound("Inventory"), this.inventory.getItems(), this.registryAccess());
        this.burnLeft = tag.getFloat("BurnLeft");
        this.burnTotal = tag.getInt("BurnTotal");
        this.syncSlots();
        this.syncBurn();
        this.entityData.set(POWERED, this.hasEnergy());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Slot numbers are saved with each item, so an empty fuel slot doesn't shift the socket tool into it.
        tag.put("Inventory", ContainerHelper.saveAllItems(new CompoundTag(), this.inventory.getItems(), this.registryAccess()));
        tag.putFloat("BurnLeft", this.burnLeft);
        tag.putInt("BurnTotal", this.burnTotal);
    }

    // =========================================================================================
    // Fuel, power, inventory
    // =========================================================================================

    /** Server: one tick of the engine. Only a piloted suit burns fuel; a parked one is switched off. */
    private void tickFuel() {
        Player pilot = this.getControllingPassenger() instanceof Player p ? p : null;
        if (pilot != null) {
            if (this.burnLeft <= 0) {
                this.igniteNextItem();
            }
            if (this.burnLeft > 0) {
                this.burnLeft = Math.max(0, this.burnLeft - drainPerTick(pilot));
                if (this.burnLeft <= 0) {
                    this.igniteNextItem(); // hand over to the next item without a gap, like a furnace
                }
            }
        }
        this.setPowered(this.hasEnergy());
        this.syncBurn();
    }

    private void syncBurn() {
        this.entityData.set(BURN_LEFT, Mth.ceil(this.burnLeft));
        this.entityData.set(BURN_TOTAL, this.burnTotal);
    }

    /** Load on the engine. The server learns the pilot's WASD from the input packet vanilla sends for
     *  every passenger (that's how a boat knows you're paddling). */
    private static float drainPerTick(Player pilot) {
        boolean moving = pilot.zza != 0 || pilot.xxa != 0;
        if (!moving) {
            return DRAIN_IDLE;
        }
        return pilot.isSprinting() ? DRAIN_SPRINT : DRAIN_MOVING;
    }

    /** Takes one item from the fuel slot and starts burning it. Lava leaves its bucket behind. */
    private void igniteNextItem() {
        ItemStack stack = this.inventory.getItem(FUEL_SLOT);
        int energy = SuitFuel.energyOf(stack);
        if (energy <= 0) {
            return;
        }
        ItemStack remainder = stack.getCraftingRemainingItem();
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.inventory.setItem(FUEL_SLOT, remainder);
        } else {
            this.inventory.setChanged();
            if (!remainder.isEmpty()) {
                this.spawnAtLocation(remainder);
            }
        }
        this.burnLeft += energy;
        this.burnTotal = energy;
    }

    /** Energy available right now: the burning item, or an item waiting in the slot. */
    private boolean hasEnergy() {
        return this.burnLeft > 0 || SuitFuel.isFuel(this.inventory.getItem(FUEL_SLOT));
    }

    private void setPowered(boolean powered) {
        if (powered == this.entityData.get(POWERED)) {
            return;
        }
        this.entityData.set(POWERED, powered);
        if (powered) {
            this.playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
            this.playSound(SoundEvents.PISTON_EXTEND, 0.8F, 0.5F);
        } else {
            this.playSound(SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.6F);
            this.playSound(SoundEvents.IRON_GOLEM_DAMAGE, 0.6F, 0.5F);
            if (this.getControllingPassenger() instanceof Player pilot) {
                pilot.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.powerarmor.out_of_fuel"), true);
            }
        }
    }

    private void syncSlots() {
        if (!this.level().isClientSide()) {
            this.entityData.set(FUEL_ITEM, this.inventory.getItem(FUEL_SLOT).copy());
            this.entityData.set(SOCKET_ITEM, this.inventory.getItem(SOCKET_SLOT).copy());
        }
    }

    /** Both sides: the engine has energy (synced from the server). */
    public boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    /** Powered and finished standing up: allowed to move, jump and use tools. */
    public boolean canOperate() {
        return this.isPowered() && this.startupTicks == 0;
    }

    /** Both sides: energy in the burning item (rounded up). */
    public int getBurnLeft() {
        return this.entityData.get(BURN_LEFT);
    }

    public int getBurnTotal() {
        return this.entityData.get(BURN_TOTAL);
    }

    /** Both sides: the fuel slot's contents (a synced copy on clients). */
    public ItemStack getFuelStack() {
        return this.entityData.get(FUEL_ITEM);
    }

    public ItemStack getSocketStack() {
        return this.entityData.get(SOCKET_ITEM);
    }

    /** Both sides: all energy on board, burning item + everything in the fuel slot. */
    public int getTotalEnergy() {
        ItemStack fuel = this.getFuelStack();
        return this.getBurnLeft() + fuel.getCount() * SuitFuel.energyOf(fuel);
    }

    public boolean isLowOnFuel() {
        return this.getTotalEnergy() < LOW_FUEL;
    }

    // ---- Socket tool ---------------------------------------------------------------------------

    /** Server: the pilot pressed or released attack. Anyone else (or a forged packet) is ignored. */
    public void setTriggerHeld(Player who, boolean down) {
        if (who != this.getControllingPassenger()) {
            return;
        }
        if (down && !this.triggerHeld && !(this.getSocketTool() != null && this.canOperate())) {
            // Nothing in the socket, or no power: the trigger just clicks.
            this.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F);
        }
        this.triggerHeld = down;
    }

    /** Server: run the socket tool. Repeating tools fire each time they're ready while the trigger is held;
     *  continuous tools get a tick while held and a release when the trigger goes up or anything interrupts. */
    private void tickTool() {
        if (this.toolCooldown > 0) {
            this.toolCooldown--;
        }
        PowerTool tool = this.getSocketTool();
        Player pilot = this.getControllingPassenger() instanceof Player p ? p : null;
        if (tool != null) {
            tool.serverTick(this);
        }
        boolean active = pilot != null && this.triggerHeld && tool != null && this.canOperate();
        this.entityData.set(TOOL_RUNNING, tool != null && tool.isContinuous() && (active || tool.keepsRunning(this)));
        if (this.activeContinuousTool != null && (this.activeContinuousTool != tool || !active)) {
            boolean interrupted = this.activeContinuousTool != tool || pilot == null || !this.canOperate();
            this.activeContinuousTool.release(this, pilot, interrupted);
            this.activeContinuousTool = null;
        }
        if (pilot == null) {
            this.triggerHeld = false; // the pilot left with the button down
            return;
        }
        if (!active) {
            return;
        }
        if (tool.isContinuous()) {
            this.activeContinuousTool = tool;
            tool.holdTick(this, pilot, this.inventory.getItem(SOCKET_SLOT));
            return;
        }
        if (this.toolCooldown > 0) {
            return;
        }
        int cost = tool.energyPerUse();
        if (!this.hasEnergyFor(cost)) {
            this.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F); // not enough fuel for a shot
            this.toolCooldown = tool.cooldownTicks();
            return;
        }
        if (tool.use(this, pilot, this.inventory.getItem(SOCKET_SLOT))) {
            this.consumeEnergy(cost);
            this.toolCooldown = tool.cooldownTicks();
            this.toolUses++;
        }
    }

    /** Server: a tool's state for this suit, created on first use. */
    @SuppressWarnings("unchecked")
    public <T> T toolData(Class<T> type, java.util.function.Supplier<T> create) {
        if (!type.isInstance(this.toolData)) {
            this.toolData = create.get();
        }
        return (T) this.toolData;
    }

    /** Server: take energy for a tool if there's enough (burning item + fuel slot). */
    public boolean tryUseEnergy(int amount) {
        if (!this.hasEnergyFor(amount)) {
            return false;
        }
        this.consumeEnergy(amount);
        return true;
    }

    public boolean isToolReady() {
        return this.toolCooldown == 0;
    }

    public void setToolCooldown(int ticks) {
        this.toolCooldown = ticks;
    }

    public void countToolUse() {
        this.toolUses++;
    }

    /** Server: what the EM beam holds (synced for the beam effect). */
    public void setBeamTarget(@Nullable Entity target) {
        this.entityData.set(BEAM_TARGET, target == null ? -1 : target.getId());
    }

    /** Both sides: the entity in the EM beam, if any. */
    @Nullable
    public Entity getBeamTarget() {
        int id = this.entityData.get(BEAM_TARGET);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Unloaded, packed or destroyed with something in the beam: let it go, or it would float forever. */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && this.activeContinuousTool != null) {
            this.activeContinuousTool.release(this, null, true);
            this.activeContinuousTool = null;
        }
        super.remove(reason);
    }

    @Nullable
    private PowerTool getSocketTool() {
        return this.inventory.getItem(SOCKET_SLOT).getItem() instanceof PowerTool tool ? tool : null;
    }

    /** Server: is there this much energy on board (burning item + fuel slot)? */
    public boolean hasEnergyFor(int amount) {
        ItemStack fuel = this.inventory.getItem(FUEL_SLOT);
        return this.burnLeft + (float) fuel.getCount() * SuitFuel.energyOf(fuel) >= amount;
    }

    /** Server: take energy, lighting more fuel items if the burning one hasn't got enough left. */
    private void consumeEnergy(int amount) {
        while (this.burnLeft < amount && SuitFuel.isFuel(this.inventory.getItem(FUEL_SLOT))) {
            this.igniteNextItem();
        }
        this.burnLeft = Math.max(0, this.burnLeft - amount);
        this.syncBurn();
    }

    public int getToolUses() {
        return this.toolUses;
    }

    public boolean isTriggerHeld() {
        return this.triggerHeld;
    }

    /** Server: the real slots (used by the menu and tests). */
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    /** Server/tests: set the burning item's energy directly. */
    public void setBurn(float left, int total) {
        this.burnLeft = left;
        this.burnTotal = total;
        this.syncBurn();
    }

    public float getBurnLeftExact() {
        return this.burnLeft;
    }

    /** What a packed suit item keeps: both slots and the half-burnt item. */
    public SuitLoadout getLoadout() {
        return new SuitLoadout(this.inventory.getItem(FUEL_SLOT).copy(), this.inventory.getItem(SOCKET_SLOT).copy(),
                Mth.ceil(this.burnLeft), this.burnTotal);
    }

    public void applyLoadout(SuitLoadout loadout) {
        this.inventory.setItem(FUEL_SLOT, loadout.fuel().copy());
        this.inventory.setItem(SOCKET_SLOT, loadout.socket().copy());
        this.burnLeft = loadout.burnLeft();
        this.burnTotal = loadout.burnTotal();
        this.syncBurn();
        this.entityData.set(POWERED, this.hasEnergy());
    }

    /** Server: turn this parked suit back into an item (with its fuel and tool) for the player. */
    public void packUp(Player player) {
        ItemStack stack = PowerArmorItem.fromSuit(this);
        this.inventory.clearContent(); // they're inside the item now; don't drop them too
        if (!player.addItem(stack)) {
            this.spawnAtLocation(stack);
        }
        this.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.8F);
        this.discard();
    }

    /** Both sides: watch the synced power flag change, to start the slump / stand-up animations. */
    private void tickPowerTransitions() {
        boolean powered = this.isPowered();
        if (!this.seenPower) {
            // First tick we know about this suit (just spawned, loaded, or came into view).
            this.seenPower = true;
            this.lastPowered = powered;
            this.wasOffWhenLoaded = !powered;
        } else if (powered != this.lastPowered) {
            this.lastPowered = powered;
            this.wasOffWhenLoaded = false;
            this.startupTicks = powered ? STARTUP_TICKS : 0;
            if (powered && this.level().isClientSide()) {
                ExhaustSmoke.startupBurst(this);
            }
        }
        if (this.startupTicks > 0) {
            this.startupTicks--;
        }
        if (!this.canOperate()) {
            this.pendingJump = 0;
        }
    }

    /** Client: 0..1 weight of the torso following the pilot's view. */
    public float getAimBlend() {
        return this.aimBlend;
    }

    /** Sneak + right-click on a parked suit: open its inventory. Server only. */
    private void openMenu(Player player) {
        player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> new PowerArmorMenu(id, playerInventory, this.inventory, this),
                this.getDisplayName()), buf -> buf.writeVarInt(this.getId()));
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
                // Sneak + right-click: open the suit's menu (fuel, socket, pack up), like a horse's inventory.
                this.openMenu(player);
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
        if (!this.canOperate()) {
            return Vec3.ZERO; // out of fuel (or still booting): the legs don't move
        }
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
        if (!this.canOperate()) {
            return; // powered down: the torso stays put while the pilot looks around the cockpit
        }
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
                    double launch = (0.42 + 0.3 * this.pendingJump) * (this.isInWater() ? UNDERWATER_JUMP_SCALE : 1.0);
                    this.setDeltaMovement(motion.x, launch, motion.z); // up to ~2.3 blocks on land
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

    /** Client/dev: ticks spent airborne (animation bookkeeping). */
    public int getAirTicks() {
        return this.airTicks;
    }

    /** Client/dev: name of the animation the movement controller is playing, or "-". */
    public String getMovementAnimationName() {
        var manager = this.animationCache.getManagerForId(this.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("movement");
        var current = controller == null ? null : controller.getCurrentAnimation();
        return current == null ? "-" : current.animation().name().replace("animation.power_armor.", "");
    }

    /** Client: smoothed ground speed in blocks/s (for camera effects). */
    public double getVisualSpeed() {
        return this.visualSpeed;
    }

    /** Client: how far the animated hips are below/above the gait's average this frame (model px). */
    private float hipBob;

    /** Called by the model every frame with the root bone's animated height. Only the jog and run
     *  cycles bob the camera, measured against their own average hip height (from the specs). */
    public void setHipHeight(float y) {
        String anim = this.getMovementAnimationName();
        float target = switch (anim) {
            case "walk" -> y - WALK_HIP_MEAN;
            case "run" -> y - RUN_HIP_MEAN;
            default -> 0F;
        };
        this.hipBob += (target - this.hipBob) * 0.35F; // eases in/out when the gait changes
    }

    public float getHipBob() {
        return this.hipBob;
    }

    /** Client: true while the legs are shuffling round to face the view (plays a step cycle). */
    public boolean isRealigning() {
        return this.realigning;
    }

    /**
     * Movement for the controlling client. After vanilla moves us, if we just walked off a small
     * ledge (and aren't jumping), drop straight onto the ground below: stepping down instead of
     * falling keeps full speed and avoids a fall pose on every block of a slope.
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isInWater() && this.isControlledByLocalInstance()) {
            this.travelUnderwater(travelVector);
            return;
        }
        boolean wasOnGround = this.onGround();
        super.travel(travelVector);
        if (this.isVehicle() && this.isControlledByLocalInstance() && wasOnGround && !this.onGround()
                && this.getDeltaMovement().y <= 0 && this.pendingJump == 0) {
            AABB below = this.getBoundingBox().expandTowards(0, -STEP_DOWN, 0);
            if (!this.level().noCollision(this, below)) {
                this.move(MoverType.SELF, new Vec3(0, -STEP_DOWN, 0)); // stops at the ground
                this.setDeltaMovement(this.getDeltaMovement().multiply(1, 0, 1));
            }
        }
    }

    /**
     * Vanilla makes living things float and paddle in water. The suit is a ton of steel instead:
     * no buoyancy, it sinks to the bottom and walks there at reduced speed. Runs on whichever side
     * controls the suit (the pilot's client, or the server for an empty suit).
     */
    private void travelUnderwater(Vec3 input) {
        float speed = this.getSpeed() * UNDERWATER_SPEED * (this.onGround() ? 1.0F : UNDERWATER_AIR_CONTROL);
        this.moveRelative(speed, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 v = this.getDeltaMovement();
        double vy = (v.y - SINK_ACCEL) * WATER_DRAG_Y;
        // Vanilla's "climb out at the edge" help: pushing into a bank near the surface lifts us over.
        if (this.horizontalCollision && this.isFree(v.x, v.y + 0.6 - this.getY() + this.yo, v.z)) {
            vy = 0.3;
        }
        this.setDeltaMovement(v.x * WATER_DRAG_XZ, vy, v.z * WATER_DRAG_XZ);
        this.calculateEntityAnimation(false);
    }

    /** The suit itself is a sealed machine and never runs out of air (its pilot can, though). */
    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    /** Too heavy for currents and bubble columns to push around. */
    @Override
    public boolean isPushedByFluid() {
        return false;
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
        return this.isAlive() && this.canOperate();
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
        this.burnLeft = Math.max(0, this.burnLeft - JUMP_ENERGY);
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
        for (ItemStack stack : this.inventory.removeAllItems()) {
            this.spawnAtLocation(stack); // the fuel and the socket tool survive the wreck
        }
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
        if (!this.level().isClientSide() && this.isAlive()) {
            this.tickFuel();
            this.tickTool();
        }
        this.tickPowerTransitions();
        if (this.level().isClientSide()) {
            float aimTarget = this.isVehicle() && this.canOperate() ? 1F : 0F;
            this.aimBlend += (aimTarget - this.aimBlend) * 0.2F;
            ExhaustSmoke.tick(this);
            EmBeamEffects.tick(this);
            this.tickLeftHandSwing();
            // Measure how fast we actually move (blocks/s) and smooth it to avoid flicker
            // between idle and walk.
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            double speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
            this.visualSpeed += (speed - this.visualSpeed) * 0.4;
            tickStepSmoothing();
            tickAirborneAnimationState();
        }
    }

    /** Step-ups (and snapped step-downs) move us a whole block in one tick. Absorb that jump into an
     *  offset that eases back to zero, used for the pilot's seat and the rendered model. */
    private void tickStepSmoothing() {
        this.stepOffsetO = this.stepOffset;
        double dy = this.getY() - this.yo;
        if (this.wasOnGroundForSteps && this.onGround() && Math.abs(dy) > 0.2 && Math.abs(dy) <= STEP_DOWN + 0.1) {
            this.stepOffset -= (float) dy;
            this.stepOffsetO -= (float) dy;
        }
        this.wasOnGroundForSteps = this.onGround();
        this.stepOffset = Mth.clamp(this.stepOffset * STEP_EASE, -2.5F, 2.5F);
        if (Math.abs(this.stepOffset) < 0.01F) {
            this.stepOffset = 0;
        }
    }

    /** Client: eased step offset in blocks for this frame. */
    public float getStepOffset(float partialTick) {
        return Mth.lerp(partialTick, this.stepOffsetO, this.stepOffset);
    }

    /** The pilot rides with the eased step offset, so the camera glides over steps. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick).add(0, this.stepOffset, 0);
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
                .triggerableAnim("hit", HIT)
                .triggerableAnim("plasma_shot", PLASMA_SHOT)
                .triggerableAnim("tool_recoil", PLASMA_SHOT)); // same kick for the EM projector's push
        // Controller 3: the socket tool's own loop (drill spin, chainsaw chain) while it runs. It keys only
        // the tool's bones, so the arm keeps following walk/run and the aim.
        controllers.add(new AnimationController<>(this, "tool", 3, this::toolAnimation));
        // Controller 4: the left hand. Added last, so while it plays it owns the left arm (the gait's arm
        // swing pauses, like a person carrying a torch). Stops when there's nothing to hold or no power.
        controllers.add(new AnimationController<>(this, "left_hand", 4, this::leftHandAnimation)
                .triggerableAnim("use_item", USE_ITEM));
    }

    private PlayState movementAnimation(AnimationState<PowerArmorEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (!this.isPowered()) {
            state.getController().transitionLength(4);
            return state.setAndContinue(this.wasOffWhenLoaded ? POWERED_OFF : SHUTDOWN);
        }
        if (this.startupTicks > 0) {
            state.getController().transitionLength(2);
            return state.setAndContinue(STARTUP);
        }
        if (!this.isVehicle()) {
            return PlayState.STOP; // parked: engine idle, standing in its rest pose
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

    private PlayState toolAnimation(AnimationState<PowerArmorEntity> state) {
        if (this.entityData.get(TOOL_RUNNING) && this.getSocketStack().getItem() instanceof PowerTool tool && tool.runAnimation() != null) {
            return state.setAndContinue(tool.runAnimation());
        }
        return PlayState.STOP;
    }

    private PlayState leftHandAnimation(AnimationState<PowerArmorEntity> state) {
        if (this.isDeadOrDying() || !this.canOperate() || !(this.getControllingPassenger() instanceof Player pilot)
                || pilot.getMainHandItem().isEmpty()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_ITEM);
    }

    /** Client: the pilot swung their hand (placed a block, used an item on something): reach with the suit's hand.
     *  Swings of other players arrive as packets, so everyone sees it. */
    private void tickLeftHandSwing() {
        boolean swinging = this.getControllingPassenger() instanceof Player pilot && pilot.swinging
                && pilot.swingingArm == InteractionHand.MAIN_HAND;
        if (swinging && !this.pilotWasSwinging && this.canOperate()) {
            this.triggerAnim("left_hand", "use_item");
        }
        this.pilotWasSwinging = swinging;
    }

    /** Fixed rate per gait. GeckoLib scales the whole elapsed time by this, so changing it
     *  smoothly mid-loop would make the animation jump; constant values avoid that. */
    private double movementAnimationRate() {
        if (this.isDeadOrDying() || !this.isVehicle() || !this.canOperate() || this.jumped || this.jumpAnimTicks > 0
                || this.airTicks >= FALL_POSE_AFTER_TICKS || this.landTicks > 0) {
            return 1.0;
        }
        double water = this.isInWater() ? UNDERWATER_SPEED : 1.0; // slow-motion steps on the bottom
        if (!this.isWalking()) {
            return (this.realigning ? 0.6 : 1.0) * water; // turning on the spot: a slower step cycle
        }
        return (this.isRunning() ? RUN_ANIM_RATE : WALK_ANIM_RATE) * water;
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
