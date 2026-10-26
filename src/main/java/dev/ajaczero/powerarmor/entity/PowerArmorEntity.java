package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.EmProjectorItem;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.item.SuitLoadout;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import dev.ajaczero.powerarmor.item.HelmetStyle;
import dev.ajaczero.powerarmor.item.SuitHelmetItem;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;

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
    // ./gradlew runAutopilot: 42.14 on 1.21.1; on 26.3 the same speed attribute went 2.4% further (0.11865 gave
    // 5.12 blocks/s instead of 5.00), so x43.15.
    private static final double BLOCKS_PER_SECOND_PER_SPEED = 43.15;
    // A heavy walk and a heavy sprint (2026-10-14 animation overhaul). The suit used to jog at 5 blocks/s and sprint at
    // 10, but legs this long can't cover 5 blocks/s walking: the walk was really a run (both feet off the ground each
    // step) with the hips dropped 3.6 px and the knees bent to 128 degrees. From 2026-10-19 the game walked at the walk
    // animation's own speed (walk v6: 1.0 s cycle, 22 px stance = 2.218 blocks/s); since 2026-10-20 the step rate follows
    // the real speed (movementAnimationRate), so the walk is a game choice: 3.0 (user: half a player's 4.3 was too slow to
    // get anywhere; the walk steps 1.35x its authored cadence). The sprint stays at the run's own 6.25.
    private static final double WALK_ANIM_AUTHORED = 2.218;
    /** The walk on flat ground, blocks/s (tests compare against it). */
    public static final double WALK_BLOCKS_PER_SECOND = 3.0;
    private static final double WALK_SPEED = WALK_BLOCKS_PER_SECOND / BLOCKS_PER_SECOND_PER_SPEED; // 0.0695 (a player walks at 4.3)
    /** Sprint / walk: 6.25 blocks/s sprint (a player sprints at 5.6). */
    public static final float SPRINT_MULTIPLIER = 2.083F;
    /** Ground speed (blocks/s) between the walk and the sprint: the mind, which has no sprint key, runs above it. */
    private static final double RUN_THRESHOLD = 4.5;

    // The ground speed each Blockbench animation was authored for (blocks/s, measured from the
    // planted foot). Playing them at (real speed along the legs) / authoredSpeed keeps the feet from sliding.
    private static final double RUN_ANIM_AUTHORED = 6.249;
    /** Step-rate limits: below, a near-stop would freeze mid-step; above, a walk would flail before the run takes over. */
    private static final double MIN_STEP_RATE = 0.25, MAX_STEP_RATE = 2.2;
    /** Smoothed speed along the legs below this (blocks/s, backwards) plays the walk in reverse. */
    private static final double BACKING_UP_SPEED = -0.3;

    /** Average hip height (model px) of the walk / run cycles: yHip mean in a_walk.js / a_run.js. */
    private static final float WALK_HIP_MEAN = -0.29F;
    private static final float RUN_HIP_MEAN = -0.3F;

    // ---- Terrain ----------------------------------------------------------------------------
    /** Walking off a ledge up to this high keeps the suit planted (steps down instead of falling).
     *  2 blocks: a 3-block-tall mech steps down mountain terraces; the step easing smooths it. */
    private static final double STEP_DOWN = 2.1;

    // ---- Water (Fallout-style: the suit sinks and walks along the bottom) ----------------------
    /** Speed on the bottom relative to dry land. */
    private static final float UNDERWATER_SPEED = 0.5F;
    /** Lava is thicker: the suit wades through it (and its hull burns) at a third of its speed. */
    private static final float LAVA_SPEED = 0.35F;
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
    /** The suit's slots. The helmet (2026-10-19) seals the suit and lets its machine mind run: without one the suit
     *  still runs with a pilot inside, unsealed, and an empty one stands powered down. */
    public static final int FUEL_SLOT = 0, SOCKET_SLOT = 1, HELMET_SLOT = 2;
    /** The engine (the backpack, design sec. 4b, 2026-10-21): what powers the suit and what the fuel slot burns. */
    public static final int ENGINE_SLOT = 3;
    /** Ammunition for the socket tool (2026-10-25, the TNT launcher's TNT; generic so later tools can draw from it too).
     *  What fits is the item tag powerarmor:suit_ammo. Suits saved before it simply have it empty. */
    public static final int AMMO_SLOT = 4;
    /** What the ammo slot takes: data, so packs (and later tools) can add to it. */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> AMMO = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath(dev.ajaczero.powerarmor.PowerArmor.MODID, "suit_ammo"));
    /** Height above the suit's feet and distance behind its middle of the backpack's centre (backpack box y 31.6-38.6 px,
     *  z 6-11 px): water above that point covers the engine. */
    private static final double ENGINE_UP = 2.2, ENGINE_BACK = 0.53;
    // Drain per tick (idle / walking / sprinting), energy per jump and the low-fuel warning level are balance
    // knobs in PowerArmorConfig. Walking = 1 per tick, so SuitFuel values read as ticks of walking.
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
    /** True while the pilot is using the socket tool (button held, tool installed, power): the tool arm is raised. */
    private static final EntityDataAccessor<Boolean> TOOL_ACTIVE =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** The socket tool's spin (chaingun barrels, 0..1) and heat (0..1), and a counter bumped on every shot: clients
     *  turn them into the barrel spin, heat glow, motor whine, recoil and muzzle bursts. */
    private static final EntityDataAccessor<Float> TOOL_SPIN =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TOOL_HEAT =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TOOL_SHOTS =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** True while a lightning strike has knocked the suit out (it restarts by itself): the slump starts with sparks. */
    /** Who the machine mind works for (SuitMind ordinal): the visor shows it. */
    private static final EntityDataAccessor<Byte> MIND =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** Iron nuggets in the hidden ammo store (synced: the menu and the pilot's HUD show it). */
    private static final EntityDataAccessor<Integer> NUGGETS =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** Held in another suit's EM field (synced: the pilot's client moves a piloted suit, so it must know). */
    private static final EntityDataAccessor<Boolean> FIELD_HELD =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STUNNED =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Copies of the two slots, so clients can show the fuel left and (Phase 2) draw the socket tool. */
    private static final EntityDataAccessor<ItemStack> FUEL_ITEM =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Energy one item in the fuel slot gives the installed engine. Worked out on the server (the firebox's burn times
     *  are furnace data that only the server has, see SuitFuel), so the clients' gauge can count the slot. */
    private static final EntityDataAccessor<Integer> FUEL_ENERGY =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> SOCKET_ITEM =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.ITEM_STACK);
    /** The ammo slot's contents: the HUD counts the TNT left. */
    private static final EntityDataAccessor<ItemStack> AMMO_ITEM =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Is a helmet in its slot: clients draw it (or not) and know whether the suit is sealed. */
    private static final EntityDataAccessor<Boolean> HELMET =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** The worn helmet's style (HelmetStyle ordinal, -1 = the standard helmet or none): clients draw its bone. */
    private static final EntityDataAccessor<Byte> HELMET_STYLE =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** The plates' paint: a dye's id (DyeColor.getId, 0..15), or -1 for bare steel. Synced: clients pick the texture by it. */
    private static final EntityDataAccessor<Byte> PAINT =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** The installed engine (SuitEngine ordinal, -1 = none): clients draw its backpack, smoke and gauge. */
    private static final EntityDataAccessor<Byte> ENGINE =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** Water covers the engine (a firebox is flooded, a tidal engine runs). */
    private static final EntityDataAccessor<Boolean> ENGINE_UNDER_WATER =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** The engine runs without burning anything right now (a wet tidal engine, a star reactor): the gauge shows it. */
    private static final EntityDataAccessor<Boolean> FREE_POWER =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);

    /** The suit's own slots: fuel, the left-arm socket, the helmet, the engine and the ammo. Real contents only exist on
     *  the server. Old saves load fine with fewer: each saved item carries its slot number. */
    private final SimpleContainer inventory = new SimpleContainer(5) {
        @Override
        public void setChanged() {
            super.setChanged();
            // Whenever the slots change on the server, copy them into the synced data for clients.
            PowerArmorEntity.this.syncSlots();
        }
    };
    /** Server: energy left in the burning item (fractional, idle drains 0.25 per tick). */
    private float burnLeft;
    private int burnTotal;
    /** Server: the engine seen last tick, to notice a swap (the half-burnt item goes with the old engine). */
    @Nullable
    private SuitEngine lastEngine;
    private boolean engineSeen;
    /** Server: ticks since a tidal engine was last wet (it runs free for a short grace after leaving the water). */
    private int dryTicks = Integer.MAX_VALUE / 2;
    /** Both sides: power transitions seen, for animations, sounds and smoke. */
    private boolean seenPower;
    private boolean lastPowered;
    /** The suit was already off when we first saw it: hold the slumped pose instead of playing the slump. */
    private boolean wasOffWhenLoaded;
    private int startupTicks;
    /** Server: the pilot is holding the tool's button (right click, sent by the client, see ToolInput / ToolTriggerPayload). */
    private boolean triggerHeld;
    /** Server: ticks until the socket tool may be used again. */
    private int toolCooldown;
    /** Server: how many times the socket tool has been used (tests and dev logs). */
    private int toolUses;
    /** Server: the continuous tool currently held down (to release it when anything changes). */
    @Nullable
    private PowerTool activeContinuousTool;
    /** Server: who started it (a pilot taking over from the mind mid-use interrupts it). */
    @Nullable
    private LivingEntity activeOperator;
    /** Server: a tool's per-suit state (items are shared by every stack, so state can't live on the item). */
    @Nullable
    private Object toolData;
    /** Client: the pilot's arm was mid-swing last tick (to catch the start of each swing). */
    private boolean pilotWasSwinging;
    /** Server: the tool was in use last tick (for the raise sound). */
    private boolean toolWasActive;
    /** Client: the action controller was already playing a shot last tick (for the pilot's own muzzle burst). */
    private int seenShots;
    /** Client: chaingun barrel angle (degrees, this and last tick) and the recoil kick (1 right after a shot, decays). The
     *  TNT launcher's drum uses the same angle (one tool at a time): drumTurn is how much of its quarter turns is left. */
    private float barrelAngle, barrelAngleO, recoil, recoilO, drumTurn;
    /** Degrees per tick the TNT launcher's drum turns after a shot (a quarter turn in 5 ticks). */
    private static final float DRUM_SPEED = 18F;
    /** Client: 0..1, how much the torso follows the pilot's view (eases out on shutdown, in after startup). */
    private float aimBlend;
    /** Client: 0 = tool arm in its hip carry, 1 = raised to aim (follows TOOL_ACTIVE, like raising a shield). */
    private float toolRaise, toolRaiseO;
    /** Client: the lights (SuitGlowLayer). Fire 0..1; visor brightness 0..1 and colour; ticks the visor stays red after a hit. */
    private float fireGlow, visorLevel;
    /** Client: the EM projector's redstone, 0..1: the lens + vents (core) and the prong tips (arc). */
    private float emCoreGlow, emArcGlow;
    /** Client: the flamethrower's pilot light, 0 (out) .. 1 (spraying). */
    private float pilotGlow;
    /** Client: the tidal engine's / star reactor's glass, 0 (dark) .. 1 (running), eased like the fire. */
    private float engineGlow;
    private final float[] visorColor = {1F, 0.68F, 0.22F};
    /** Server: the machine mind's owner (the last pilot, or who deployed it) and home (where the pilot got out). Not
     *  synced: only the server-side AI uses them. */
    @Nullable
    private java.util.UUID ownerId;
    @Nullable
    private BlockPos home;
    /** Server: the machine mind holds the tool's trigger (its goals aim the suit first). */
    private boolean aiTrigger;
    @Nullable
    private Vec3 aiAim;
    /** Server: ticks left of a lightning knock-out (0 = none). */
    private int stunTicks;
    /** Server: when the seal last paid for keeping fire/frost out (SuitSeal: once per half second, like hit immunity). */
    public int lastSealTick = -100;
    /** Client: ticks since the lightning knock-out began (sparks fly for the first second). */
    private int sparkTicks = -1;

    /** Ticks to stay around after dying so the death animation (2.68 s) can finish. */
    private static final int DEATH_TICKS = 60;

    // ---- Animations (names from the exported Blockbench animation file) --------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.power_armor.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.power_armor.walk");
    /** The walk with its keys reversed in time (made in Blockbench from the walk): backing up steps backwards. */
    private static final RawAnimation WALK_BACK = RawAnimation.begin().thenLoop("animation.power_armor.walk_back");
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
    // Struck by lightning: convulse (the electrified loop, played once) and then slump like an empty tank. thenPlay would
    // use the JSON's loop setting ("loop": true for electrified) and convulse forever; PLAY_ONCE moves on to the slump.
    private static final RawAnimation ZAPPED = RawAnimation.begin()
            .then("animation.power_armor.electrified", com.geckolib.animation.object.LoopType.PLAY_ONCE)
            .thenPlayAndHold("animation.power_armor.shutdown");
    private static final RawAnimation TOOL_RECOIL = RawAnimation.begin().thenPlay("animation.power_armor.tool_recoil");
    // Right hand (a_hand.js): carry pose while the pilot holds something, quick reach on every use.
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
    /** Client only: the same along the legs (where the boots point; negative = backing up), for the step rate. */
    private double visualForward;
    /** Client only: vertical offset (blocks) that eases the camera and model over step-ups/downs. */
    private final StepSmoothing steps = new StepSmoothing(STEP_DOWN);
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
        // A new suit comes without an engine (2026-10-23, user): craft one and put it in the engine slot. Suits from
        // before the engines still get the firebox they had built in (readAdditionalSaveData, applyLoadout).
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(JUMP_COUNTER, 0);
        builder.define(POWERED, false);
        builder.define(BURN_LEFT, 0);
        builder.define(BURN_TOTAL, 0);
        builder.define(FUEL_ITEM, ItemStack.EMPTY);
        builder.define(FUEL_ENERGY, 0);
        builder.define(BEAM_TARGET, -1);
        builder.define(TOOL_RUNNING, false);
        builder.define(TOOL_ACTIVE, false);
        builder.define(TOOL_SPIN, 0F);
        builder.define(TOOL_HEAT, 0F);
        builder.define(TOOL_SHOTS, 0);
        builder.define(SOCKET_ITEM, ItemStack.EMPTY);
        builder.define(AMMO_ITEM, ItemStack.EMPTY);
        builder.define(HELMET, false);
        builder.define(HELMET_STYLE, (byte) -1);
        builder.define(STUNNED, false);
        builder.define(MIND, (byte) SuitMind.FRIENDLY.ordinal());
        builder.define(NUGGETS, 0);
        builder.define(FIELD_HELD, false);
        builder.define(PAINT, (byte) -1);
        builder.define(ENGINE, (byte) -1);
        builder.define(ENGINE_UNDER_WATER, false);
        builder.define(FREE_POWER, false);
    }

    /** The machine mind's goals (see SuitGoals). Vanilla calls this once, from Mob's constructor. */
    @Override
    protected void registerGoals() {
        dev.ajaczero.powerarmor.entity.ai.SuitGoals.register(this, this.goalSelector, this.targetSelector);
    }

    /** Base stats. The single source of truth: also re-applied to suits loaded from old saves. */
    private static final Map<Holder<Attribute>, Double> BASE_STATS = Map.of(
            Attributes.MAX_HEALTH, (double) MAX_HEALTH,
            Attributes.ARMOR, 12.0,
            Attributes.ARMOR_TOUGHNESS, 4.0,
            Attributes.KNOCKBACK_RESISTANCE, 1.0,
            Attributes.MOVEMENT_SPEED, WALK_SPEED,
            Attributes.FOLLOW_RANGE, 48.0, // how far the mind's paths may reach (vanilla's 16 lost an owner who walked on)
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
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        BASE_STATS.forEach((attribute, value) -> {
            AttributeInstance instance = this.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        });
        this.inventory.clearContent();
        // Since 1.21.6 entities save through ValueInput/ValueOutput (a view on the NBT) instead of raw CompoundTags.
        // Same layout as before, so suits saved by the 1.21.1 version still load.
        ContainerHelper.loadAllItems(tag.childOrEmpty("Inventory"), this.inventory.getItems());
        if (!tag.getBooleanOr("HasEngineSlot", false) && this.inventory.getItem(ENGINE_SLOT).isEmpty()) {
            // Saved before the engines (2026-10-21): it was running on its built-in firebox all along.
            this.inventory.setItem(ENGINE_SLOT, new ItemStack(dev.ajaczero.powerarmor.registry.ModItems.FIREBOX.get()));
        }
        this.dryTicks = tag.getIntOr("TidalDry", Integer.MAX_VALUE / 2);
        this.entityData.set(ENGINE_UNDER_WATER, tag.getBooleanOr("EngineUnderWater", false));
        this.lastEngine = SuitEngine.of(this.inventory.getItem(ENGINE_SLOT));
        this.engineSeen = true; // loading isn't a swap: keep the half-burnt item
        this.burnLeft = tag.getFloatOr("BurnLeft", 0F);
        this.burnTotal = tag.getIntOr("BurnTotal", 0);
        this.setMind(SuitMind.byName(tag.getStringOr("Mind", ""))); // suits from before the AI: "" -> friendly
        this.ownerId = tag.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        this.home = tag.read("Home", BlockPos.CODEC).orElse(null);
        this.setStoredNuggets(tag.getIntOr("Nuggets", 0));
        this.rogueAge = tag.getIntOr("RogueAge", 0);
        this.setPaint(tag.read("Paint", DyeColor.CODEC).orElse(null)); // suits from before paint: bare
        this.syncSlots();
        this.syncBurn();
        this.entityData.set(POWERED, this.shouldRun(null)); // a pilot (if any) boards after loading: next tick knows
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        // Slot numbers are saved with each item, so an empty fuel slot doesn't shift the socket tool into it.
        ContainerHelper.saveAllItems(tag.child("Inventory"), this.inventory.getItems());
        tag.putBoolean("HasEngineSlot", true); // an empty engine slot is then really empty (taken out), not an old save
        tag.putInt("TidalDry", this.dryTicks);
        tag.putBoolean("EngineUnderWater", this.isEngineUnderWater());
        tag.putFloat("BurnLeft", this.burnLeft);
        tag.putInt("BurnTotal", this.burnTotal);
        tag.putString("Mind", this.getMind().name());
        tag.putInt("Nuggets", this.getStoredNuggets());
        tag.putInt("RogueAge", this.rogueAge);
        tag.storeNullable("Owner", net.minecraft.core.UUIDUtil.CODEC, this.ownerId); // int array, as before
        tag.storeNullable("Home", BlockPos.CODEC, this.home);
        tag.storeNullable("Paint", DyeColor.CODEC, this.getPaint()); // the dye's name, e.g. "red"
    }

    // =========================================================================================
    // The machine mind (design sec. 8): drives the suit while it's powered and nobody is inside
    // =========================================================================================

    /** Both sides: the mind is in control right now: powered, booted, not knocked out, no pilot. */
    public boolean isAiDriven() {
        return this.isAlive() && this.canOperate() && !this.isStunned() && !this.isVehicle();
    }

    /** Both sides: someone drives the suit, the pilot or the mind. The model then aims the torso and raises the tool. */
    public boolean isDriven() {
        return this.isVehicle() || this.isAiDriven();
    }

    public SuitMind getMind() {
        return SuitMind.byOrdinal(this.entityData.get(MIND));
    }

    public void setMind(SuitMind mind) {
        this.entityData.set(MIND, (byte) mind.ordinal());
    }

    public boolean isRogue() {
        return this.getMind() == SuitMind.ROGUE;
    }

    /** Server: the owner if they're online in this dimension (not spectating), else null. */
    @Nullable
    public Player getOwner() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        Player owner = level.getPlayerByUUID(this.ownerId);
        return owner != null && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }

    /** Both sides: a chaingun in the socket (the friendly job below, or a rogue's weapon). */
    public boolean hasChaingun() {
        return this.getSocketStack().getItem() instanceof dev.ajaczero.powerarmor.item.ChaingunItem;
    }

    /** Both sides: an EM projector in the socket (the guard job, or a rogue's weapon). */
    public boolean hasEmProjector() {
        return this.getSocketStack().getItem() instanceof EmProjectorItem;
    }

    /** Both sides: a flamethrower in the socket (the same job as the chaingun's, at close range; or a rogue's weapon). */
    public boolean hasFlamethrower() {
        return this.getSocketStack().getItem() instanceof dev.ajaczero.powerarmor.item.FlamethrowerItem;
    }

    /** Both sides: a weapon the mind fights with by aiming and holding the trigger (chaingun, flamethrower). */
    public boolean hasGun() {
        return this.hasChaingun() || this.hasFlamethrower();
    }

    /** Both sides: the chaingun job (design sec. 8): a friendly mind with a chaingun (or a flamethrower, Phase 11)
     *  follows its owner and defends them. */
    public boolean followsOwner() {
        return !this.isRogue() && this.hasGun();
    }

    /** Both sides: the EM guard job (design sec. 8): a friendly mind with an EM projector guards the area round home. */
    public boolean guardsHome() {
        return !this.isRogue() && this.getSocketStack().getItem() instanceof EmProjectorItem;
    }

    /** Both sides: the drill job: a friendly mind with the drill clears an open hall round home (SuitWork). */
    public boolean digsHall() {
        return !this.isRogue() && this.getSocketStack().getItem() instanceof dev.ajaczero.powerarmor.item.DrillItem;
    }

    /** Both sides: the chainsaw job: a friendly mind with the chainsaw fells the natural trees round home. */
    public boolean fellsTrees() {
        return !this.isRogue() && this.getSocketStack().getItem() instanceof dev.ajaczero.powerarmor.item.ChainsawItem;
    }

    /** Paths may drop this far. The drilling mind never steps down at all ("never where it would fall"): its hall is
     *  flat, and a step down there is a hole in the floor. */
    @Override
    public int getMaxFallDistance() {
        return this.digsHall() && this.getControllingPassenger() == null ? 0 : super.getMaxFallDistance();
    }

    /** Server: bullets for the mind's chaingun (its hidden store). The flamethrower runs on fuel alone. */
    public boolean hasAmmo() {
        return this.isRogue() || this.hasFlamethrower() || this.getStoredNuggets() > 0;
    }

    /** Server: the mind's bare-handed blow (no bullets left): a pilot's punch, 1 + the melee bonus, with a shove. */
    public void punch(LivingEntity target) {
        float damage = 1F + PowerArmorConfig.MELEE_BONUS.get().floatValue();
        this.triggerAnim("right_hand", "use_item"); // the right arm reaches out (the hand's use animation)
        this.swingForAttack(InteractionHand.MAIN_HAND);
        DamageSource blow = this.damageSources().mobAttack(this);
        if (target.level() instanceof ServerLevel level && target.hurtServer(level, blow, damage)) {
            Vec3 push = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
            target.knockback(0.6, -push.x, -push.z, blow, damage);
            this.setLastHurtMob(target);
        }
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.8F);
    }

    /** Server: the player the mind works for (null: nobody yet, e.g. a rebooted wild suit). */
    @Nullable
    public java.util.UUID getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(@Nullable java.util.UUID owner) {
        this.ownerId = owner;
    }

    /** Server: the block the mind's jobs are anchored to (null: none, e.g. spawned by a command). */
    @Nullable
    public BlockPos getHome() {
        return this.home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home == null ? null : home.immutable();
    }

    /** Server: further than {@code slack} blocks (horizontally) from home. */
    public boolean isAwayFromHome(double slack) {
        if (this.home == null) {
            return false;
        }
        double dx = this.getX() - (this.home.getX() + 0.5), dz = this.getZ() - (this.home.getZ() + 0.5);
        return dx * dx + dz * dz > slack * slack || Math.abs(this.getY() - this.home.getY()) > 1.5;
    }

    /** Server: the mind pulls or releases the tool's trigger (it aims the suit's head first: setAiAim). */
    public void setAiTrigger(boolean down) {
        this.aiTrigger = down;
    }

    public boolean isAiTriggerHeld() {
        return this.aiTrigger;
    }

    /** Server: the point the mind aims the head (and so the torso and the tool) at, or null to let the look goals
     *  wander. A machine: it snaps onto the target instead of easing round like an animal. */
    public void setAiAim(@Nullable Vec3 target) {
        this.aiAim = target;
    }

    /** Server/dev: where the mind aims right now (autopilot logs), or null. */
    @Nullable
    public Vec3 getAiAim() {
        return this.aiAim;
    }

    /** Vanilla's look control resets the pitch and eases the head back toward the body every tick unless something
     *  asks it to look somewhere. This runs after the goals (which may have asked for something else) and before the
     *  look control, so an aim set by the mind wins; the tool fires later in the same tick (tickTool). */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAiDriven()) {
            this.aiAim = null;
        } else if (this.aiAim != null) {
            this.getLookControl().setLookAt(this.aiAim.x, this.aiAim.y, this.aiAim.z, 180F, 180F);
        }
    }

    /** Server/dev: the mind's running goals, e.g. "ReturnHome/LookAtPlayerGoal" (autopilot logs). */
    public String runningGoalNames() {
        return this.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                .map(g -> g.getGoal().getClass().getSimpleName().isEmpty() ? g.getGoal().getClass().getSuperclass().getSimpleName() : g.getGoal().getClass().getSimpleName())
                .reduce((a, b) -> a + "/" + b).orElse("-");
    }

    /** The head (and so the torso, which follows it) may turn as far from the legs as the model's torso twists. */
    @Override
    public int getMaxHeadYRot() {
        return (int) MAX_TWIST;
    }

    /**
     * Both sides: who works the socket tool right now: the pilot, or the suit itself while its mind drives (its head
     * aims, its own store feeds the gun, kills are credited to it). Null: nobody (parked, off).
     */
    @Nullable
    public LivingEntity toolOperator() {
        if (this.getControllingPassenger() instanceof Player pilot) {
            return pilot;
        }
        return this.isAiDriven() ? this : null;
    }

    /**
     * Server: the player a block is broken as. Breaking needs a player: claim mods veto by who breaks (BreakEvent), and
     * loot tables and XP want one. With the mind driving, that's a FakePlayer (NeoForge's stand-in player that never
     * joins the game) carrying the owner's UUID, so claims treat the suit like its owner.
     */
    public Player blockBreaker(LivingEntity operator) {
        if (operator instanceof Player player) {
            return player;
        }
        java.util.UUID id = this.ownerId != null ? this.ownerId : WILD_SUIT_ID;
        Player fake = net.neoforged.neoforge.common.util.FakePlayerFactory.get((ServerLevel) this.level(),
                new com.mojang.authlib.GameProfile(id, "[Power Armor]"));
        fake.snapTo(this.getX(), this.getY(), this.getZ(), this.getYHeadRot(), this.getXRot()); // mods may check where it stands
        return fake;
    }

    /** The FakePlayer UUID for suits nobody owns. */
    private static final java.util.UUID WILD_SUIT_ID = java.util.UUID.fromString("6d1c1b8e-3f0a-4c55-9d7e-5a2f0b9c1e01");

    /** The first pilot to climb in owns the suit (and every later pilot takes it over: the mind works for the last one). */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide() && passenger instanceof Player player) {
            this.ownerId = player.getUUID();
            this.aiTrigger = false;
            this.getNavigation().stop();
        }
    }

    /** The pilot got out: the mind takes over and makes this block its home. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide() && passenger instanceof Player player && this.isAlive()) {
            this.ownerId = player.getUUID();
            this.home = this.blockPosition();
        }
    }

    /** Vanilla switches the goals' controls on and off every few ticks (for mobs ridden by mobs); we hand them to the
     *  mind only while it drives, so a pilot climbing in stops every goal at once. */
    @Override
    protected void updateControlFlags() {
        boolean ai = this.isAiDriven();
        this.goalSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, ai);
        this.goalSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP, ai);
        this.goalSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK, ai);
        this.targetSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET, ai);
    }

    // =========================================================================================
    // Fuel, power, inventory
    // =========================================================================================

    /** Server: one tick of the engine. A suit with fuel runs while it's piloted, or while it wears its helmet (then its
     *  machine mind drives it): to park a suit, take its helmet off (or the fuel out). */
    private void tickFuel() {
        Player pilot = this.getControllingPassenger() instanceof Player p ? p : null;
        if (this.stunTicks > 0 && --this.stunTicks == 0) {
            this.entityData.set(STUNNED, false); // back on: setPowered below plays the startup
        }
        if (this.isRogue() && this.inventory.getItem(HELMET_SLOT).isEmpty()) {
            // Rogues always wear one (/summon, or a rogue from before the helmet existed): its mind lives in it. A
            // defeated rogue still drops only its tool; one rebooted by lightning keeps it.
            ItemStack helmet = new ItemStack(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get());
            if (this.random.nextInt(4) == 0) {
                // one rogue in four wears a styled helmet (design sec. 4c): only a look, and not loot (rogues drop their tool only)
                HelmetStyle[] styles = HelmetStyle.values();
                helmet.set(dev.ajaczero.powerarmor.registry.ModDataComponents.HELMET_STYLE.get(), styles[this.random.nextInt(styles.length)]);
            }
            this.inventory.setItem(HELMET_SLOT, helmet);
        }
        if (this.isRogue() && this.inventory.getItem(ENGINE_SLOT).isEmpty()) {
            // Suits come without an engine now, and a rogue needs a backpack to look the part (it runs on nothing
            // anyway). Not loot either (a defeated rogue drops its tool only), but one rebooted by lightning keeps it,
            // so its coal can burn.
            this.inventory.setItem(ENGINE_SLOT, new ItemStack(dev.ajaczero.powerarmor.registry.ModItems.FIREBOX.get()));
        }
        SuitEngine engine = this.getEngine();
        if (this.engineSeen && engine != this.lastEngine) {
            // Engine swapped: the half-burnt item went out with the old one (design sec. 4b).
            this.burnLeft = 0;
            this.burnTotal = 0;
        }
        if (engine != this.lastEngine || !this.engineSeen) {
            // The mind's paths: a firebox would drown in deep water, so its suit keeps out of water altogether (vanilla's
            // default cost for water is 8: walkable, avoided when there's a dry way round).
            this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, engine == SuitEngine.FIREBOX ? -1F : 8F);
        }
        this.lastEngine = engine;
        this.engineSeen = true;
        boolean underWater = this.engineCoveredByWater();
        this.entityData.set(ENGINE_UNDER_WATER, underWater);
        this.dryTicks = underWater || this.isEngineInRain() ? 0 : Math.min(this.dryTicks + 1, Integer.MAX_VALUE / 2);
        if (engine == SuitEngine.FIREBOX && underWater && !this.isRogue()) {
            this.floodFirebox();
        }
        boolean free = this.runsFree();
        this.entityData.set(FREE_POWER, free);
        if (this.stunTicks == 0 && !this.isRogue() && !free && this.shouldRun(pilot)) { // a knocked-out engine burns nothing
            if (this.burnLeft <= 0) {
                this.igniteNextItem();
            }
            if (this.burnLeft > 0) {
                float drain = pilot != null ? drainPerTick(pilot) : this.aiDrainPerTick();
                if (pilot != null && dev.ajaczero.powerarmor.SuitSeal.feedsAir(pilot)) {
                    drain += PowerArmorConfig.AIR_ENERGY_PER_TICK.get().floatValue(); // the pilot breathes the suit's air
                }
                this.burnLeft = Math.max(0, this.burnLeft - drain);
                if (this.burnLeft <= 0) {
                    this.igniteNextItem(); // hand over to the next item without a gap, like a furnace
                }
            }
        }
        this.setPowered(this.shouldRun(pilot) && this.stunTicks == 0);
        this.syncBurn();
    }

    // ---- Engines (design sec. 4b) ------------------------------------------------------------------------

    /** Both sides: the installed engine, or null (the engine slot is empty: no power). */
    public @Nullable SuitEngine getEngine() {
        return SuitEngine.byId(this.entityData.get(ENGINE));
    }

    /** Both sides: water covers the engine (the backpack's middle is under the surface). */
    public boolean isEngineUnderWater() {
        return this.entityData.get(ENGINE_UNDER_WATER);
    }

    /** Both sides: a firebox under water: it can't burn, and the suit can't be packed up. */
    public boolean isFlooded() {
        return this.getEngine() == SuitEngine.FIREBOX && this.isEngineUnderWater() && !this.isRogue();
    }

    /** Both sides: the engine runs without burning anything right now (wet tidal engine, star reactor). */
    public boolean hasFreePower() {
        return this.entityData.get(FREE_POWER);
    }

    /** Server: where the engine sits in the world: the backpack's middle, behind the body. */
    private Vec3 enginePosition() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        // Minecraft's forward for a yaw is (-sin, cos); the backpack is the other way.
        return this.position().add(Mth.sin(yaw) * ENGINE_BACK, ENGINE_UP, -Mth.cos(yaw) * ENGINE_BACK);
    }

    /** Server: is that point below a water surface? (A fluid's height inside its block: a full source is 8/9 high.) */
    private boolean engineCoveredByWater() {
        Vec3 p = this.enginePosition();
        BlockPos pos = BlockPos.containing(p);
        var fluid = this.level().getFluidState(pos);
        return fluid.is(net.minecraft.tags.FluidTags.WATER) && p.y < pos.getY() + fluid.getHeight(this.level(), pos);
    }

    /** Server: rain reaches the suit's top (vanilla's own rain test for entities, which is private). */
    private boolean isEngineInRain() {
        return this.level().isRainingAt(BlockPos.containing(this.getX(), this.getBoundingBox().maxY, this.getZ()));
    }

    /** Server: does the engine run without burning anything right now? A tidal engine while wet (and for a short grace
     *  after leaving the water), a reactor with its Nether Star. */
    private boolean runsFree() {
        SuitEngine engine = this.getEngine();
        if (engine == SuitEngine.TIDAL) {
            return this.dryTicks <= PowerArmorConfig.TIDAL_DRY_GRACE.get();
        }
        return SuitFuel.powersForever(engine, this.inventory.getItem(FUEL_SLOT));
    }

    /** Server: water got into the firebox: the fire dies with a hiss and a puff of steam, and the half-burnt item is
     *  lost. Fuel in the slot can't light while the backpack is under water ({@link #hasEnergy}). */
    private void floodFirebox() {
        if ((this.burnLeft > 0 || this.isPowered()) && this.level() instanceof ServerLevel level) {
            this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.0F, 0.6F);
            Vec3 p = this.enginePosition();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, p.x, p.y, p.z, 30, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, p.x, p.y + 0.4, p.z, 12, 0.3, 0.3, 0.3, 0.05);
        }
        this.burnLeft = 0;
        this.burnTotal = 0;
    }

    /** Should the engine run: energy on board and someone to drive, the pilot or the mind (which needs the helmet). */
    private boolean shouldRun(@Nullable Player pilot) {
        return this.hasEnergy() && (pilot != null || this.hasHelmet());
    }

    private void syncBurn() {
        this.entityData.set(BURN_LEFT, Mth.ceil(this.burnLeft));
        this.entityData.set(BURN_TOTAL, this.burnTotal);
    }

    /**
     * The pilot's WASD as (strafe, 0, forward), each -1..1 (+x = left, +z = forward, like the player's xxa / zza).
     * The pilot's own client moves the suit with its xxa / zza. The server learns the keys from the input packet vanilla
     * sends for every passenger (that's how a boat knows you're paddling); since 1.21.2 that packet no longer fills the
     * server player's xxa / zza, only its "last client input" (keys pressed). GameTests' mock players set xxa / zza.
     */
    public static Vec3 pilotInput(Player pilot) {
        if (pilot instanceof net.minecraft.server.level.ServerPlayer sp) {
            var keys = sp.getLastClientInput();
            float strafe = keys.left() == keys.right() ? 0F : keys.left() ? 1F : -1F;
            float forward = keys.forward() == keys.backward() ? 0F : keys.forward() ? 1F : -1F;
            if (strafe != 0F || forward != 0F) {
                return new Vec3(strafe, 0, forward);
            }
        }
        return new Vec3(pilot.xxa, 0, pilot.zza);
    }

    /** Load on the engine, from the pilot's keys (pilotInput). */
    private static float drainPerTick(Player pilot) {
        boolean moving = pilotInput(pilot).lengthSqr() > 1e-4;
        if (!moving) {
            return PowerArmorConfig.DRAIN_IDLE.get().floatValue();
        }
        return (pilot.isSprinting() ? PowerArmorConfig.DRAIN_SPRINT : PowerArmorConfig.DRAIN_WALK).get().floatValue();
    }

    /** The same load for the machine mind, judged by how fast it really moves. */
    private float aiDrainPerTick() {
        // getDeltaMovement() already holds the ground drag for the next tick (block friction 0.6 x 0.91 on normal
        // ground): it reads ~0.55 of the distance really moved, so undo that to compare with blocks/s.
        double blocksPerSecond = this.getDeltaMovement().horizontalDistance() * 20 / 0.546;
        if (blocksPerSecond < 0.7) {
            return PowerArmorConfig.DRAIN_IDLE.get().floatValue();
        }
        return (blocksPerSecond > RUN_THRESHOLD ? PowerArmorConfig.DRAIN_SPRINT : PowerArmorConfig.DRAIN_WALK).get().floatValue();
    }

    /** Takes one item from the fuel slot and starts burning it. Lava and water leave their bucket behind, water bottles
     *  their glass bottle. */
    private void igniteNextItem() {
        ItemStack stack = this.inventory.getItem(FUEL_SLOT);
        int energy = SuitFuel.energyOf(this.getEngine(), stack, this.level());
        if (energy <= 0 || this.isFlooded()) {
            return;
        }
        // What's left of a burnt item (the bucket of a lava bucket), like a furnace. A potion has no crafting remainder
        // (drinking one leaves the bottle by other means), so the water bottle's glass is named here.
        var template = stack.getCraftingRemainder();
        ItemStack remainder = SuitFuel.isWaterBottle(stack) ? new ItemStack(Items.GLASS_BOTTLE)
                : template == null ? ItemStack.EMPTY : template.create();
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.inventory.setItem(FUEL_SLOT, remainder);
        } else {
            this.inventory.setChanged();
            if (!remainder.isEmpty() && this.level() instanceof ServerLevel level) {
                this.spawnAtLocation(level, remainder);
            }
        }
        this.burnLeft += energy;
        this.burnTotal = energy;
    }

    /** Energy available right now: the burning item, or an item waiting in the slot. */
    private boolean hasEnergy() {
        // A rogue runs on nothing (design sec. 8: unlimited fuel and ammo, it never sits slumped waiting to be looted).
        if (this.isRogue()) {
            return true;
        }
        SuitEngine engine = this.getEngine();
        if (engine == null || this.isFlooded()) {
            return false; // no engine, or a firebox under water: nothing can burn
        }
        return this.runsFree() || this.burnLeft > 0 || SuitFuel.energyOf(engine, this.inventory.getItem(FUEL_SLOT), this.level()) > 0;
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
                pilot.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(this.stunTicks > 0 ? "message.powerarmor.lightning"
                        : this.getEngine() == null ? "message.powerarmor.no_engine"
                        : this.isFlooded() ? "message.powerarmor.flooded"
                        : this.getEngine() == SuitEngine.TIDAL ? "message.powerarmor.tidal_dry"
                        : "message.powerarmor.out_of_fuel"));
            }
        }
    }

    private void syncSlots() {
        if (!this.level().isClientSide()) {
            this.entityData.set(FUEL_ITEM, this.inventory.getItem(FUEL_SLOT).copy());
            this.entityData.set(FUEL_ENERGY, SuitFuel.energyOf(SuitEngine.of(this.inventory.getItem(ENGINE_SLOT)),
                    this.inventory.getItem(FUEL_SLOT), this.level()));
            this.entityData.set(SOCKET_ITEM, this.inventory.getItem(SOCKET_SLOT).copy());
            this.entityData.set(AMMO_ITEM, this.inventory.getItem(AMMO_SLOT).copy());
            this.entityData.set(HELMET, !this.inventory.getItem(HELMET_SLOT).isEmpty());
            HelmetStyle style = SuitHelmetItem.style(this.inventory.getItem(HELMET_SLOT));
            this.entityData.set(HELMET_STYLE, (byte) (style == null ? -1 : style.ordinal()));
            SuitEngine engine = SuitEngine.of(this.inventory.getItem(ENGINE_SLOT));
            this.entityData.set(ENGINE, (byte) (engine == null ? -1 : engine.ordinal()));
        }
    }

    /** Both sides: the engine has energy (synced from the server). */
    public boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    /** Both sides: a helmet sits in the helmet slot. */
    public boolean hasHelmet() {
        return this.entityData.get(HELMET);
    }

    /** Both sides: the worn helmet's style (design sec. 4c), null for the standard one (or no helmet). */
    public @Nullable HelmetStyle getHelmetStyle() {
        return HelmetStyle.byId(this.entityData.get(HELMET_STYLE));
    }

    /** Both sides: running with its helmet on, so fire, frost, harmful effects and water are kept out (SuitSeal). */
    public boolean isSealed() {
        return this.isPowered() && this.hasHelmet();
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

    /** Both sides: the ammo slot's contents (a synced copy on clients). */
    public ItemStack getAmmoStack() {
        return this.entityData.get(AMMO_ITEM);
    }

    /** Server: use up one item of ammo that {@code fits} (from the ammo slot). False if there is none. */
    public boolean takeAmmo(java.util.function.Predicate<ItemStack> fits) {
        ItemStack ammo = this.inventory.getItem(AMMO_SLOT);
        if (ammo.isEmpty() || !fits.test(ammo)) {
            return false;
        }
        ammo.shrink(1);
        this.inventory.setChanged(); // shrinking a stack in place doesn't tell the container: sync the HUD's count
        return true;
    }

    /** Both sides: all energy on board, burning item + everything in the fuel slot. */
    public int getTotalEnergy() {
        ItemStack fuel = this.getFuelStack();
        return this.getBurnLeft() + fuel.getCount() * this.entityData.get(FUEL_ENERGY);
    }

    public boolean isLowOnFuel() {
        // A wet tidal engine or a reactor isn't burning anything, so it can't run low (a tidal engine on land can).
        return !this.hasFreePower() && this.getTotalEnergy() < PowerArmorConfig.LOW_FUEL.get();
    }

    // ---- Socket tool ---------------------------------------------------------------------------

    /** Server: the pilot pressed or released the tool's button (right click). Anyone else (or a forged packet) is ignored. */
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
        // The pilot works the tool with their trigger; with nobody inside, the machine mind with its own.
        LivingEntity operator = this.toolOperator();
        boolean trigger = operator instanceof Player ? this.triggerHeld : this.aiTrigger;
        if (tool != null) {
            tool.serverTick(this);
        }
        boolean active = operator != null && trigger && tool != null && this.canOperate();
        this.entityData.set(TOOL_RUNNING, tool != null && tool.isContinuous() && (active || tool.keepsRunning(this)));
        this.entityData.set(TOOL_ACTIVE, active);
        if (active && !this.toolWasActive) {
            // The arm swings up into the aiming pose (PowerArmorModel.raiseTool): a heavy mechanical clunk and a hiss.
            this.playSound(SoundEvents.PISTON_CONTRACT, 0.5F, 0.7F);
            this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.15F, 1.9F);
        }
        this.toolWasActive = active;
        if (this.activeContinuousTool != null && (this.activeContinuousTool != tool || !active)) {
            boolean interrupted = this.activeContinuousTool != tool || operator == null || operator != this.activeOperator
                    || !this.canOperate();
            this.activeContinuousTool.release(this, operator == this.activeOperator ? operator : null, interrupted);
            this.activeContinuousTool = null;
            this.activeOperator = null;
        }
        if (!(operator instanceof Player)) {
            this.triggerHeld = false; // the pilot left with the button down
        }
        if (operator != this) {
            this.aiTrigger = false; // the mind lost control (pilot, power) with its trigger down
        }
        if (!active) {
            return;
        }
        if (tool.isContinuous()) {
            this.activeContinuousTool = tool;
            this.activeOperator = operator;
            tool.holdTick(this, operator, this.inventory.getItem(SOCKET_SLOT));
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
        if (tool.use(this, operator, this.inventory.getItem(SOCKET_SLOT))) {
            this.consumeEnergy(cost);
            this.toolUses++;
        }
        this.toolCooldown = tool.cooldownTicks(); // also after a dry click: one click per cooldown
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
            this.activeOperator = null;
        }
        super.remove(reason);
    }

    @Nullable
    private PowerTool getSocketTool() {
        return this.inventory.getItem(SOCKET_SLOT).getItem() instanceof PowerTool tool ? tool : null;
    }

    /** Server: is there this much energy on board (burning item + fuel slot)? */
    public boolean hasEnergyFor(int amount) {
        if (this.isRogue() || (this.runsFree() && this.hasEnergy())) {
            return true; // a wet tidal engine or a star reactor pays for everything
        }
        if (this.isFlooded()) {
            return false;
        }
        ItemStack fuel = this.inventory.getItem(FUEL_SLOT);
        return this.burnLeft + (float) fuel.getCount() * SuitFuel.energyOf(this.getEngine(), fuel, this.level()) >= amount;
    }

    /** Server: take energy, lighting more fuel items if the burning one hasn't got enough left. */
    private void consumeEnergy(int amount) {
        if (this.isRogue() || this.runsFree()) {
            return;
        }
        while (this.burnLeft < amount && SuitFuel.isFuel(this.getEngine(), this.inventory.getItem(FUEL_SLOT)) && !this.isFlooded()) {
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

    /** What a packed suit item keeps: its slots, the half-burnt item and the ammo. */
    public SuitLoadout getLoadout() {
        return new SuitLoadout(this.inventory.getItem(FUEL_SLOT).copy(), this.inventory.getItem(SOCKET_SLOT).copy(),
                Mth.ceil(this.burnLeft), this.burnTotal, this.getStoredNuggets(), this.inventory.getItem(HELMET_SLOT).copy(),
                java.util.Optional.of(this.inventory.getItem(ENGINE_SLOT).copy()), this.inventory.getItem(AMMO_SLOT).copy());
    }

    public void applyLoadout(SuitLoadout loadout) {
        this.inventory.setItem(FUEL_SLOT, loadout.fuel().copy());
        this.inventory.setItem(SOCKET_SLOT, loadout.socket().copy());
        this.inventory.setItem(HELMET_SLOT, loadout.helmet().copy());
        this.inventory.setItem(AMMO_SLOT, loadout.ammo().copy());
        // Items packed (or crafted) before the engines carry none: they had their firebox built in.
        this.inventory.setItem(ENGINE_SLOT, loadout.engine().map(ItemStack::copy)
                .orElseGet(() -> new ItemStack(dev.ajaczero.powerarmor.registry.ModItems.FIREBOX.get())));
        this.lastEngine = SuitEngine.of(this.inventory.getItem(ENGINE_SLOT));
        this.engineSeen = true; // not a swap: keep the half-burnt item
        this.burnLeft = loadout.burnLeft();
        this.burnTotal = loadout.burnTotal();
        this.setStoredNuggets(loadout.nuggets());
        this.syncBurn();
        this.entityData.set(POWERED, this.shouldRun(null));
    }

    /** Server: turn this parked suit back into an item (with its fuel and tool) for the player. Refused while its
     *  firebox is flooded (design sec. 4b): drain the water or swap the engine first. Returns whether it packed. */
    public boolean packUp(Player player) {
        if (this.isFlooded()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.powerarmor.flooded_pack"));
            this.playSound(SoundEvents.IRON_DOOR_CLOSE, 0.6F, 0.5F);
            return false;
        }
        ItemStack stack = PowerArmorItem.fromSuit(this);
        this.inventory.clearContent(); // they're inside the item now; don't drop them too
        if (!player.addItem(stack) && this.level() instanceof ServerLevel level) {
            this.spawnAtLocation(level, stack);
        }
        this.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.8F);
        this.discard();
        return true;
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
        if (this.isRogue()) {
            return InteractionResult.PASS; // a rogue mind won't open up: no boarding, no menu
        }
        ItemStack held = player.getItemInHand(hand);
        DyeColor dye = held.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null && !player.isSecondaryUseActive()) {
            return this.paintWith(player, held, dye); // like dyeing a sheep: a dye in hand paints, it doesn't climb in
        }
        if (held.is(Items.WET_SPONGE) && !player.isSecondaryUseActive()) {
            return this.washPaint(player);
        }
        if (held.is(Items.IRON_NUGGET) && !player.isSecondaryUseActive()) {
            return this.loadNuggets(player, held); // feeding it ammo, like feeding a horse: no climbing in
        }
        if (held.is(dev.ajaczero.powerarmor.registry.ModItems.SUIT_HELMET.get()) && !player.isSecondaryUseActive() && !this.hasHelmet()) {
            return this.putOnHelmet(player, held); // like saddling a horse: right-click it with the helmet
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
        return InteractionResult.SUCCESS;
    }

    // ---- Paint (2026-10-20): the suit is bare steel; a dye paints its armour plates ---------------------------------

    /** Both sides: the plates' paint, or null for bare steel. */
    public @Nullable DyeColor getPaint() {
        byte id = this.entityData.get(PAINT);
        return id < 0 ? null : DyeColor.byId(id);
    }

    public void setPaint(@Nullable DyeColor dye) {
        this.entityData.set(PAINT, dye == null ? (byte) -1 : (byte) dye.getId());
    }

    /**
     * Right-click with a dye: the plates take its colour (a new dye paints over the old one) and the dye is used up, like
     * dyeing a sheep. The same colour again does nothing (and keeps the dye). The paint is only a texture: the renderer
     * picks {@code textures/entity/paint/power_armor_<dye>.png} (tools/make-paint-textures.py).
     */
    private InteractionResult paintWith(Player player, ItemStack held, DyeColor dye) {
        if (dye == this.getPaint()) {
            return InteractionResult.SUCCESS; // nothing to do, but holding a dye still never climbs in
        }
        // playSound(player, ...) plays for everyone near except that player, whose own client plays it right away.
        this.level().playSound(player, this, SoundEvents.DYE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F);
        if (!this.level().isClientSide()) {
            this.setPaint(dye);
            held.consume(1, player); // consume() leaves creative players' stacks alone
        }
        return InteractionResult.SUCCESS;
    }

    /** Right-click with a wet sponge: wash the paint off, back to bare steel. The sponge stays wet (it isn't used up). */
    private InteractionResult washPaint(Player player) {
        if (this.getPaint() == null) {
            return InteractionResult.SUCCESS;
        }
        this.level().playSound(player, this, SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.3F);
        if (this.level() instanceof ServerLevel server) {
            this.setPaint(null);
            // a splash on the plates (server particles reach every client near the suit)
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, this.getX(), this.getY() + 1.6, this.getZ(),
                    40, 0.5, 0.6, 0.5, 0.1);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- The hidden ammo store (no slot: one tool uses it) ------------------------------------------------

    /** Right-click with iron nuggets: the whole stack goes into the store, up to its size (config). */
    /** Right-click with a helmet on a suit without one: put it on (the menu's helmet slot does the same). */
    private InteractionResult putOnHelmet(Player player, ItemStack held) {
        if (!this.level().isClientSide()) {
            this.inventory.setItem(HELMET_SLOT, held.copyWithCount(1));
            held.consume(1, player);
            this.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.9F);
            this.playSound(SoundEvents.IRON_DOOR_CLOSE, 0.6F, 1.3F); // the visor clicks shut
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult loadNuggets(Player player, ItemStack held) {
        int room = PowerArmorConfig.NUGGET_STORE_MAX.get() - this.getStoredNuggets();
        if (room <= 0) {
            if (!this.level().isClientSide()) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.powerarmor.nuggets_full",
                        this.getStoredNuggets()));
            }
            return InteractionResult.SUCCESS;
        }
        if (!this.level().isClientSide()) {
            int n = Math.min(room, held.getCount());
            this.setStoredNuggets(this.getStoredNuggets() + n);
            if (!player.hasInfiniteMaterials()) {
                held.shrink(n);
            }
            this.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0F, 0.8F);
            this.playSound(SoundEvents.CHAIN_PLACE, 0.8F, 0.7F);
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.powerarmor.nuggets_loaded",
                    n, this.getStoredNuggets()));
        }
        return InteractionResult.SUCCESS;
    }

    /** Both sides: nuggets in the hidden store. */
    public int getStoredNuggets() {
        return this.entityData.get(NUGGETS);
    }

    public void setStoredNuggets(int nuggets) {
        this.entityData.set(NUGGETS, Math.max(0, nuggets));
    }

    /** Server: take one nugget from the store, if there is one. */
    public boolean takeStoredNugget() {
        if (this.isRogue()) {
            return true; // unlimited ammo
        }
        int n = this.getStoredNuggets();
        if (n <= 0) {
            return false;
        }
        this.setStoredNuggets(n - 1);
        return true;
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
        // A/D: full speed since 2026-10-20 (was half). The legs turn to face the way the suit walks, so a strafe is a
        // walk sideways with the torso twisted toward the view; at half speed its steps played in slow motion.
        float strafe = player.xxa;
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

        if (this.isLocalInstanceAuthoritative() && this.pendingJump > 0) {
            if (this.launchDelay > 0) {
                this.launchDelay--;
            } else {
                if (this.onGround()) {
                    Vec3 motion = this.getDeltaMovement();
                    double launch = (0.42 + 0.3 * this.pendingJump) * (this.isInWater() ? UNDERWATER_JUMP_SCALE : 1.0);
                    this.setDeltaMovement(motion.x, launch, motion.z); // up to ~2.3 blocks on land
                    this.needsSync = true; // (was hasImpulse) send the new motion right away
                    this.playSound(SoundEvents.PISTON_EXTEND, 0.8F, 0.6F);
                }
                this.pendingJump = 0;
            }
        }
    }

    /**
     * Vanilla calls this every tick (server and clients) to turn the body. For a driven suit (pilot or mind) we
     * turn the legs ourselves: toward the direction of travel while moving (or away from it when
     * backing up), and in small catch-up shuffles when standing still. The renderer then twists
     * the torso by (look - legs), so the upper body always faces where the pilot (or the mind) looks.
     * The mind used vanilla's mob rule until 2026-10-19: it lets the head get past the torso's 90 degree twist before
     * the body follows (the suit drilled a block while facing away from it), and each client works a mob's body yaw out
     * from tiny position changes (the torso swung from side to side while it chased and punched). This rule depends
     * only on the synced head yaw and the movement, so every side agrees.
     */
    @Override
    protected void tickHeadTurn(float movementYaw) {
        if (!this.isVehicle() && !this.isAiDriven()) {
            this.realigning = false;
            super.tickHeadTurn(movementYaw);
            return;
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
    }

    /** Client/dev: ticks spent airborne (animation bookkeeping). */
    public int getAirTicks() {
        return this.airTicks;
    }

    /** Client/dev: name of the animation the movement controller is playing, or "-". */
    public String getMovementAnimationName() {
        var manager = this.animationCache.getManagerForId(this.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("movement");
        var current = controller == null ? null : controller.getCurrentAnimationPoint();
        return current == null ? "-" : current.animation().name().replace("animation.power_armor.", "");
    }

    /** Both sides: the pilot is using the socket tool (synced): the tool arm raises. */
    public boolean isToolActive() {
        return this.entityData.get(TOOL_ACTIVE);
    }

    /** Client: how far the tool arm is raised, 0 (hip carry) .. 1 (aiming), eased, for this frame. */
    public float getToolRaise(float partial) {
        float t = Mth.lerp(partial, this.toolRaiseO, this.toolRaise);
        return t * t * (3 - 2 * t);
    }

    // ---- Lights (client): the firebox and the visor, drawn by SuitGlowLayer -----------------------------

    private static final float[] VISOR_GREEN = {0.35F, 1F, 0.45F}, VISOR_AMBER = {1F, 0.68F, 0.22F}, VISOR_RED = {1F, 0.12F, 0.08F};

    /** Client, every tick: ease the lights toward what the suit is doing. The fire flares up quickly and dies down
     *  slowly (embers); the visor shows who is in control: amber = a pilot (whatever they do), green = the friendly
     *  machine mind, red = a rogue mind (only rogues: so a red visor always means danger), dark = powered off. */
    private void tickLights() {
        boolean powered = this.isPowered();
        float fireTarget = powered ? (0.6F + 0.4F * Math.min(1F, this.engineLoad())) * (this.isLowOnFuel() ? 0.7F : 1F) : 0F;
        this.fireGlow += (fireTarget - this.fireGlow) * (fireTarget > this.fireGlow ? 0.2F : 0.03F);
        float[] target = visorColorFor(this.isVehicle(), this.getMind());
        for (int i = 0; i < 3; i++) {
            this.visorColor[i] += (target[i] - this.visorColor[i]) * 0.25F;
        }
        float levelTarget = powered ? 1F : 0F;
        this.visorLevel += (levelTarget - this.visorLevel) * (powered ? 0.3F : 0.08F);
        // EM projector: the core idles at a faint glow while mounted in a powered suit, brightens while the button
        // is held (searching for something to grab) and runs full while the beam holds something; the prong tips
        // only light up while the beam holds something (that's when they spark). Quick to light, slower to fade.
        boolean em = powered && this.getSocketStack().getItem() instanceof EmProjectorItem;
        boolean holding = em && this.getBeamTarget() != null;
        float coreTarget = !em ? 0F : holding ? 1F : this.isToolActive() ? 0.6F : 0.25F;
        float arcTarget = holding ? 1F : 0F;
        this.emCoreGlow += (coreTarget - this.emCoreGlow) * (coreTarget > this.emCoreGlow ? 0.35F : 0.1F);
        this.emArcGlow += (arcTarget - this.emArcGlow) * (arcTarget > this.emArcGlow ? 0.5F : 0.15F);
        // Flamethrower: the pilot light burns whenever the suit is powered with it fitted, brighter while it sprays; under
        // water it goes out.
        boolean flamer = powered && this.getSocketStack().getItem() instanceof dev.ajaczero.powerarmor.item.FlamethrowerItem
                && !this.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        float pilotTarget = !flamer ? 0F : this.isToolActive() ? 1F : 0.7F;
        this.pilotGlow += (pilotTarget - this.pilotGlow) * (pilotTarget > this.pilotGlow ? 0.4F : 0.1F);
        // The tidal engine's and the reactor's glass light up while they run (the firebox has its fire above).
        float engineTarget = powered && this.getEngine() != SuitEngine.FIREBOX ? 1F : 0F;
        this.engineGlow += (engineTarget - this.engineGlow) * (engineTarget > this.engineGlow ? 0.15F : 0.05F);
    }

    /** Client: the Heart of the Sea in the tidal engine's porthole: a slow shimmer, like light through water. */
    public float getTidalGlow(float time) {
        if (this.getEngine() != SuitEngine.TIDAL) {
            return 0F;
        }
        return this.engineGlow * (0.82F + 0.1F * Mth.sin(time * 0.21F + this.getId()) + 0.08F * Mth.sin(time * 0.57F));
    }

    /** Client: the star reactor's energy: a slow, deep pulse that never quite goes out. */
    public float getReactorGlow(float time) {
        if (this.getEngine() != SuitEngine.REACTOR) {
            return 0F;
        }
        float pulse = Mth.sin(time * 0.12F + this.getId());
        return this.engineGlow * (0.78F + 0.22F * pulse * pulse);
    }

    /** Client: the EM projector's lens and vents at this moment, 0..1: a slow breathing pulse, and a fast uneven
     *  throb while the beam holds something (time in ticks, with the partial tick). */
    public float getEmCoreGlow(float time) {
        float g = this.emCoreGlow;
        float pulse = this.emArcGlow > 0.5F ? 0.8F + 0.12F * Mth.sin(time * 1.7F) + 0.08F * Mth.sin(time * 4.3F)
                : 0.85F + 0.15F * Mth.sin(time * 0.25F);
        return g * pulse;
    }

    /** Client: the EM projector's prong tips at this moment, 0..1: they crackle (flicker at random-looking times). */
    public float getEmArcGlow(float time) {
        float crackle = Mth.sin(time * 5.1F) * Mth.sin(time * 2.9F + this.getId()) > 0.5F ? 0.55F : 1F;
        return this.emArcGlow * crackle;
    }

    /** Client: the flamethrower's pilot light at this moment, 0..1: a small flame, it wavers a little. */
    public float getPilotGlow(float time) {
        return this.pilotGlow * (0.88F + 0.08F * Mth.sin(time * 0.9F + this.getId()) + 0.04F * Mth.sin(time * 2.3F));
    }

    /** Client: firebox glow, 0 (cold) .. 1 (roaring). */
    public float getFireGlow() {
        return this.fireGlow;
    }

    /** The visor colour each state eases toward: the pilot's amber beats the mind's colour. */
    public static float[] visorColorFor(boolean piloted, SuitMind mind) {
        return piloted ? VISOR_AMBER : mind == SuitMind.ROGUE ? VISOR_RED : VISOR_GREEN;
    }

    /** Client: the visor's colour (r, g, b, 0..1). */
    public float[] getVisorColor() {
        return this.visorColor;
    }

    /** Client: the visor's brightness at this moment: stutters while the engine starts or dies, and drops out now
     *  and then when the fuel runs low (time in ticks, with the partial tick). */
    public float getVisorFlicker(float time) {
        float level = this.visorLevel;
        boolean unsteady = this.startupTicks > 0 || (!this.isPowered() && level > 0.02F);
        if (unsteady && Mth.sin(time * 2.1F + this.getId()) + Mth.sin(time * 3.7F) < -0.4F) {
            return level * 0.2F;
        }
        if (this.isPowered() && this.isLowOnFuel() && Mth.sin(time * 0.9F) * Mth.sin(time * 2.3F) > 0.6F) {
            return level * 0.3F;
        }
        return level;
    }

    /** Both sides: a continuous socket tool is running (synced). */
    public boolean isToolRunning() {
        return this.entityData.get(TOOL_RUNNING);
    }

    /** Client: how hard the engine works, 0 idle .. 1 sprinting, +0.3 while a tool runs (engine sound). */
    public float engineLoad() {
        float load = this.isWalking() ? (this.isRunning() ? 1F : 0.55F) : 0F;
        return this.isToolRunning() ? load + 0.3F : load;
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
            case "walk", "walk_back" -> y - WALK_HIP_MEAN;
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
    /** Server: tick the suit was last held by an EM field (EmProjectorItem refreshes it every tick it holds us). */
    private int fieldHeldUntil;

    /** Server: another suit's EM field holds us this tick. Too heavy to lift, the suit fights the field: it can only
     *  crawl (a quarter of its speed) while held. */
    public void holdInField() {
        this.fieldHeldUntil = this.tickCount + 2;
        this.entityData.set(FIELD_HELD, true);
    }

    /** Both sides: held in an EM field right now. */
    public boolean isFieldHeld() {
        return this.entityData.get(FIELD_HELD);
    }

    /** A suit held in an EM field crawls: a quarter of its speed. */
    private static final float FIELD_HELD_SPEED = 0.25F;

    @Override
    public void travel(Vec3 travelVector) {
        if (!this.isVehicle() && !this.canOperate()) {
            travelVector = Vec3.ZERO; // off (or booting): the legs don't move, whatever the AI's controls still say
        } else if (!this.isVehicle() && travelVector.horizontalDistanceSqr() > 1.0E-6) {
            // Driven by the mind: vanilla's MoveControl passes its speed as the forward input (zza), and the speed is
            // applied again in moveRelative, so mobs move at speed squared. Pushing the stick all the way instead makes
            // the mind's suit walk exactly like a piloted one: 1.0 = the pilot's jog, 2.0 = sprint (walk animation sync).
            Vec3 flat = new Vec3(travelVector.x, 0, travelVector.z).normalize();
            travelVector = new Vec3(flat.x, travelVector.y, flat.z);
        }
        if (this.isFieldHeld()) {
            travelVector = travelVector.multiply(FIELD_HELD_SPEED, 1, FIELD_HELD_SPEED); // the pilot's or the mind's push
        }
        if ((this.isInWater() || this.isInLava()) && this.isLocalInstanceAuthoritative()) {
            this.travelUnderwater(travelVector); // lava too: vanilla's lava swimming crawled at 0.4 blocks/s
            return;
        }
        boolean wasOnGround = this.onGround();
        super.travel(travelVector);
        if (this.isVehicle() && this.isLocalInstanceAuthoritative() && wasOnGround && !this.onGround()
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
        float fluidSpeed = this.isInLava() ? LAVA_SPEED : UNDERWATER_SPEED;
        float speed = this.getSpeed() * fluidSpeed * (this.onGround() ? 1.0F : UNDERWATER_AIR_CONTROL);
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
        this.consumeEnergy(PowerArmorConfig.JUMP_ENERGY.get()); // nothing for a wet tidal engine or a reactor
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (dev.ajaczero.powerarmor.SuitSeal.absorbs(this, source, amount)) {
            return false; // fire, frost & co. kept out by the seal, paid in fuel
        }
        boolean damaged = super.hurtServer(level, source, amount);
        if (damaged && this.isAlive() && this.reactsToHits()) {
            this.triggerAnim("action", "hit"); // play the flinch on every client
        }
        return damaged;
    }

    /** Vanilla calls this on landing, with how far we fell. The suit takes no fall damage, but a big drop stomps:
     *  everything around the landing spot gets hurt, friend or foe. Runs on the server (it moves the suit too, from the
     *  pilot's position packets) and on the pilot's client, which only plays the sound. */
    /** Only a running suit flinches when hit. Switched off (empty, knocked out, still standing up) it can't react: and the
     *  flinch (on the "action" controller, which overrides the body's controller) would cut into the electrified
     *  convulsion, the slump or the startup, e.g. on every tick of fire after a lightning strike. */
    public boolean reactsToHits() {
        return this.canOperate();
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        if (fallDistance > 3) {
            this.playSound(SoundEvents.ANVIL_LAND, 0.6F, 0.5F); // heavy landing, no damage
        }
        if (this.level() instanceof ServerLevel level && fallDistance >= PowerArmorConfig.STOMP_MIN_FALL.get()) {
            this.lastStompVictims = SuitStomp.stomp(this, level, (float) fallDistance);
        }
        return false;
    }

    /** Server: how many things the last stomp hit (tests and dev logs). */
    public int lastStompVictims = -1;

    /** Struck by lightning (vanilla sets us on fire and hurts us first): the electrics trip. A suit with fuel shuts down,
     *  convulsing, and restarts by itself after a few seconds; until then it can't move, fight or keep fire out. */
    @Override
    public void thunderHit(ServerLevel level, net.minecraft.world.entity.LightningBolt lightning) {
        boolean wasPowered = this.isPowered();
        if (this.isRogue() && this.isAlive()) {
            // The rogue mind reboots friendly (design sec. 8): nobody's (the first pilot claims it), no home, and only the
            // fuel left in its firebox (one coal's worth) to restart with after the knock-out below.
            this.setMind(SuitMind.FRIENDLY);
            this.ownerId = null;
            this.home = null;
            this.setTarget(null);
            this.setBurn(1600, 1600);
            for (Player near : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32))) {
                near.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.powerarmor.rogue_rebooted"));
            }
        }
        // Knock it out first, so the strike's damage below finds it already off: no flinch over the convulsion.
        int ticks = PowerArmorConfig.LIGHTNING_STUN_TICKS.get();
        if (this.isAlive() && ticks > 0 && this.hasEnergy()) {
            this.stunTicks = ticks;
            this.entityData.set(STUNNED, true);
            this.setPowered(false);
            this.stopTriggeredAnim("action", "hit"); // a flinch from just before the strike mustn't play over it either
        }
        if (!wasPowered) {
            super.thunderHit(level, lightning); // no power, no resistance: it catches fire like anything else
        } else {
            // Running: the strike doesn't set the steel alight (vanilla would), it fries the electrics. Once knocked
            // out, the fire the bolt usually leaves at the suit's feet will catch it.
            this.hurt(this.damageSources().lightningBolt(), lightning.getDamage());
        }
    }

    // ---- Rogues: they leave after a while (design sec. 8), like the wandering trader ---------------------------

    /** Server: ticks this rogue has been around (saved). */
    private int rogueAge;

    /** Server, every tick: a rogue past its lifetime vanishes once no player is near enough to watch it go. */
    private void tickRogueLifetime() {
        if (!this.isRogue()) {
            this.rogueAge = 0;
            return;
        }
        this.rogueAge++;
        if (this.mayRogueLeave()) {
            if (this.level() instanceof ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5, this.getZ(), 20, 0.5, 1, 0.5, 0.02);
            }
            this.discard();
        }
    }

    /** Server: old enough, and no player within 64 blocks (checked once a second). */
    public boolean mayRogueLeave() {
        return this.isRogue() && this.rogueAge >= PowerArmorConfig.ROGUE_LIFETIME.get() && this.tickCount % 20 == 0
                && this.level().getNearestPlayer(this, 64) == null;
    }

    public void setRogueAge(int ticks) {
        this.rogueAge = ticks;
    }

    /** Death messages and the like say "Rogue Power Armor" for a rogue (synced mind: right on both sides). */
    @Override
    protected net.minecraft.network.chat.Component getTypeName() {
        return this.isRogue() ? net.minecraft.network.chat.Component.translatable("entity.powerarmor.power_armor.rogue") : super.getTypeName();
    }

    /** Both sides: knocked out by lightning (synced). */
    public boolean isStunned() {
        return this.entityData.get(STUNNED);
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
        if (this.isRogue()) {
            // Design sec. 8: a defeated rogue gives up its tool only, not a suit's worth of scrap (that's the lightning
            // reboot's reward: the whole suit).
            this.spawnAtLocation(level, this.inventory.getItem(SOCKET_SLOT).copy());
            this.inventory.clearContent();
            return;
        }
        this.spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 4 + this.random.nextInt(5)));
        for (ItemStack stack : this.inventory.removeAllItems()) {
            this.spawnAtLocation(level, stack); // the fuel, socket tool, helmet, engine and ammo survive the wreck
        }
        for (int left = this.getStoredNuggets(); left > 0; left -= 64) {
            this.spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, Math.min(64, left))); // and so does the ammo
        }
        this.setStoredNuggets(0);
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
        this.putOutFire(); // before baseTick, which decides whether clients see flames (see putOutFire)
        super.tick();
        if (!this.level().isClientSide() && this.isAlive()) {
            this.tickFuel();
            this.tickTool();
            this.tickRogueLifetime();
            if (this.isFieldHeld() && this.tickCount > this.fieldHeldUntil) {
                this.entityData.set(FIELD_HELD, false); // the field let go
            }
            this.putOutFire();
            if (this.getControllingPassenger() instanceof Player pilot) {
                dev.ajaczero.powerarmor.PilotHands.refreshBonuses(pilot);
                if (this.canOperate()) {
                    SuitSmash.tick(this, pilot);
                }
            }
        }
        this.tickPowerTransitions();
        if (this.level().isClientSide()) {
            this.putOutFire(); // the pilot's client moved us (and maybe into fire) during super.tick()
            // The torso follows the view of whoever drives: the pilot's, or the head of the mind (what it looks at).
            float aimTarget = this.isDriven() && this.canOperate() ? 1F : 0F;
            this.aimBlend += (aimTarget - this.aimBlend) * 0.2F;
            // Raise the tool arm in 3 ticks while the tool is used, lower it in 5 (heavier on the way down).
            this.toolRaiseO = this.toolRaise;
            this.toolRaise = this.isToolActive() && this.isDriven() ? Math.min(1F, this.toolRaise + 1F / 3F)
                    : Math.max(0F, this.toolRaise - 1F / 5F);
            this.tickLights();
            this.tickSparks();
            ExhaustSmoke.tick(this);
            EmBeamEffects.tick(this);
            this.tickHandSwing();
            this.tickShotEffects();
            // Measure how fast we actually move (blocks/s) and smooth it to avoid flicker
            // between idle and walk.
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            double speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
            this.visualSpeed += (speed - this.visualSpeed) * 0.4;
            float legs = this.yBodyRot * Mth.DEG_TO_RAD;
            double forward = (dx * -Mth.sin(legs) + dz * Mth.cos(legs)) * 20.0;
            this.visualForward += (forward - this.visualForward) * 0.4;
            tickStepSmoothing();
            tickAirborneAnimationState();
        }
    }

    /**
     * Both sides: a running suit doesn't burn. Whatever set it alight (a fire block, a flaming arrow, the lava it just
     * left) is put out at the start and the end of each tick. Server: vanilla's baseTick, early in the tick, turns "fire
     * ticks left" into the "on fire" flag other clients draw flames from, and a piloted suit also touches fire between
     * ticks (it's moved when the pilot's movement packet arrives). Client: the pilot's client moves the suit itself, sets
     * its own fire ticks when it touches a fire block, and draws flames from those. Contact with fire still costs fuel
     * (SuitSeal). Not fireImmune(): that would also make lava and fire blocks free. In lava it keeps burning; with no
     * power it burns like anything else.
     */
    private void putOutFire() {
        if (this.isAlive() && this.isPowered() && !this.isInLava()) {
            this.clearFire();
        }
    }

    /** Step-ups (and snapped step-downs) move us a whole block in one tick: ease that (see StepSmoothing). */
    private void tickStepSmoothing() {
        this.steps.tick(this.getY() - this.yo, this.onGround());
    }

    /** Client: eased step offset in blocks for this frame. */
    public float getStepOffset(float partialTick) {
        return this.steps.at(partialTick);
    }

    /** The pilot rides with the eased step offset, so the camera glides over steps. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick).add(0, this.steps.current(), 0);
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
            if (!this.isLocalInstanceAuthoritative()) {
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
                SuitStomp.landingDust(this, this.airTicks);
            }
            this.airTicks = 0;
            this.jumped = false;
        }
    }

    private boolean isRunning() {
        if (this.getControllingPassenger() instanceof Player pilot) {
            return pilot.isSprinting();
        }
        return this.visualSpeed > RUN_THRESHOLD; // the mind has no sprint key: between the walk and the sprint
    }

    private boolean isWalking() {
        return this.visualSpeed > 0.3;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Controller 1: full-body locomotion. 5 ticks of blending between animations.
        controllers.add(new AnimationController<>("movement", 5, this::movementAnimation));
        // Controller 2: one-shot upper-body reactions, started from the server with triggerAnim().
        // It is added after "movement", so for the bones it animates it wins; legs keep walking.
        controllers.add(new AnimationController<PowerArmorEntity>("action", 2, state -> PlayState.STOP)
                .triggerableAnim("hit", HIT)
                .triggerableAnim("tool_recoil", TOOL_RECOIL)); // the EM projector's push
        // Controller 3: the socket tool's own loop (drill spin, chainsaw chain) while it runs. It keys only
        // the tool's bones, so the arm keeps following walk/run and the aim.
        controllers.add(new AnimationController<>("tool", 3, this::toolAnimation));
        // Controller 4: the right hand. Added last, so while it plays it owns the right arm (the gait's arm
        // swing pauses, like a person carrying a torch). Stops when there's nothing to hold or no power.
        controllers.add(new AnimationController<>("right_hand", 4, this::handAnimation)
                .triggerableAnim("use_item", USE_ITEM));
    }

    private PlayState movementAnimation(AnimationTest<PowerArmorEntity> state) {
        // GeckoLib 5 advances the animation by (time passed x speed) every frame, so the speed may change any time.
        state.controller().setAnimationSpeed(this.movementAnimationRate());
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (!this.isPowered()) {
            state.controller().setTransitionTicks(this.isStunned() ? 1 : 4); // a strike jolts, it doesn't ease in
            return state.setAndContinue(this.wasOffWhenLoaded ? POWERED_OFF : this.isStunned() ? ZAPPED : SHUTDOWN);
        }
        if (this.startupTicks > 0) {
            state.controller().setTransitionTicks(2);
            return state.setAndContinue(STARTUP);
        }
        // Powered means driven: by the pilot, or by the machine mind (same gaits, picked from how fast it moves).
        // Jump states blend fast (the charge crouch ends exactly where the jump starts).
        boolean jumpState = this.jumpAnimTicks > 0 || this.jumped || (this.clientJumpCharge > 0 && this.onGround());
        state.controller().setTransitionTicks(jumpState ? 2 : 5);
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
            return state.setAndContinue(this.isRunning() ? RUN : this.isBackingUp() ? WALK_BACK : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private PlayState toolAnimation(AnimationTest<PowerArmorEntity> state) {
        if (this.isToolRunning() && this.getSocketStack().getItem() instanceof PowerTool tool && tool.runAnimation() != null) {
            return state.setAndContinue(tool.runAnimation());
        }
        return PlayState.STOP;
    }

    private PlayState handAnimation(AnimationTest<PowerArmorEntity> state) {
        if (this.isDeadOrDying() || !this.canOperate() || !(this.getControllingPassenger() instanceof Player pilot)
                || pilot.getMainHandItem().isEmpty()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_ITEM);
    }

    /** Client: sparks crawl over the suit for the first second of a lightning knock-out (while it convulses). Only on the
     *  legs and hips: particles near the cockpit camera turn into big blobs. */
    private void tickSparks() {
        if (!this.isStunned()) {
            this.sparkTicks = -1;
            return;
        }
        this.sparkTicks++;
        if (this.sparkTicks > 24) {
            return;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < 3; i++) {
            double side = (this.random.nextDouble() - 0.5) * 1.4, front = (this.random.nextDouble() - 0.5) * 0.8;
            double x = this.getX() + side * Mth.cos(yaw) - front * Mth.sin(yaw);
            double z = this.getZ() + side * Mth.sin(yaw) + front * Mth.cos(yaw);
            double y = this.getY() + 0.2 + this.random.nextDouble() * 1.1;
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    this.random.nextGaussian() * 0.15, this.random.nextGaussian() * 0.15, this.random.nextGaussian() * 0.15);
        }
        if (this.sparkTicks % 6 == 0) {
            this.level().playLocalSound(this.getX(), this.getY() + 1, this.getZ(), SoundEvents.COPPER_BULB_TURN_OFF,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.6F + this.random.nextFloat() * 0.6F, false);
        }
    }

    /** Client, every tick: follow the socket tool's synced spin and shots. The chaingun's barrels turn by the spin (up to
     *  60 degrees a tick, ten turns a second), the TNT launcher's drum a quarter turn after each shot; each new shot kicks
     *  the recoil (harder for the heavy slow shots) and, for our own pilot only, puffs the muzzle burst at the muzzle on
     *  their screen (the cockpit viewmodel's in first person, SocketAim.visualMuzzle). The server sends the burst to
     *  everyone else. */
    private void tickShotEffects() {
        float spin = this.getToolSpin();
        boolean launcher = this.getSocketStack().getItem() instanceof dev.ajaczero.powerarmor.item.TntLauncherItem;
        this.barrelAngleO = this.barrelAngle;
        this.barrelAngle += 60F * spin;
        if (this.drumTurn > 0F) {
            float step = Math.min(DRUM_SPEED, this.drumTurn);
            this.barrelAngle += step;
            this.drumTurn -= step;
            if (this.drumTurn <= 0F) {
                // the next round drops into line with the barrel: a hard little click
                this.level().playLocalSound(this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.7F, false);
            }
        }
        if (this.barrelAngle > 3600F) { // keep it small; a multiple of 90 so the square cluster doesn't jump
            this.barrelAngle -= 3600F;
            this.barrelAngleO -= 3600F;
        }
        this.recoilO = this.recoil;
        this.recoil *= 0.55F;
        int shots = this.entityData.get(TOOL_SHOTS);
        if (shots == this.seenShots) {
            return;
        }
        this.seenShots = shots;
        this.recoil = Math.max(this.recoil, 1F - 0.65F * spin);
        if (launcher) {
            this.drumTurn += 90F;
        }
        if (this.getControllingPassenger() instanceof Player pilot && pilot.isLocalPlayer()) {
            Vec3 muzzle = dev.ajaczero.powerarmor.item.SocketAim.visualMuzzle.apply(this, pilot);
            boolean firstPerson = muzzle.distanceTo(pilot.getEyePosition()) < 1.4;
            if (firstPerson) {
                // The viewmodel's muzzle is under a block from the camera, where particles turn into big blobs: burst a
                // little further out along the shot, and without the steam (it covered the target).
                muzzle = muzzle.add(pilot.getViewVector(1F).scale(1.4));
            }
            dev.ajaczero.powerarmor.item.ChaingunItem.Particles out = (type, x, y, z, n, spread, speed) -> {
                for (int i = 0; i < n; i++) {
                    this.level().addParticle(type, x + this.random.nextGaussian() * spread, y + this.random.nextGaussian() * spread,
                            z + this.random.nextGaussian() * spread, this.random.nextGaussian() * speed,
                            this.random.nextGaussian() * speed, this.random.nextGaussian() * speed);
                }
            };
            if (launcher) {
                dev.ajaczero.powerarmor.item.TntLauncherItem.muzzlePuff(out, muzzle, !firstPerson);
            } else {
                dev.ajaczero.powerarmor.item.ChaingunItem.muzzleBurst(out, muzzle, spin, !firstPerson);
            }
        }
    }

    /** Server: the socket tool's spin and heat, for every client. */
    public void setToolSpin(float spin, float heat) {
        this.entityData.set(TOOL_SPIN, spin);
        this.entityData.set(TOOL_HEAT, heat);
    }

    /** Server: a shot was fired (bumps the synced counter: recoil, muzzle burst). */
    public void countShot() {
        this.entityData.set(TOOL_SHOTS, this.entityData.get(TOOL_SHOTS) + 1);
    }

    /** Both sides: chaingun barrel spin, 0 (at rest) .. 1 (full speed). */
    public float getToolSpin() {
        return this.entityData.get(TOOL_SPIN);
    }

    /** Both sides: chaingun barrel heat, 0 (cold) .. 1 (overheated). */
    public float getToolHeat() {
        return this.entityData.get(TOOL_HEAT);
    }

    /** Client: the chaingun barrels' angle this frame, degrees. */
    public float getBarrelAngle(float partial) {
        return Mth.lerp(partial, this.barrelAngleO, this.barrelAngle);
    }

    /** Client: recoil kick this frame, 0..1. */
    public float getRecoil(float partial) {
        return Mth.lerp(partial, this.recoilO, this.recoil);
    }

    /** Client: the pilot swung their hand (placed a block, used an item on something): reach with the suit's hand.
     *  Swings of other players arrive as packets, so everyone sees it. */
    private void tickHandSwing() {
        boolean swinging = this.getControllingPassenger() instanceof Player pilot && pilot.isSwinging()
                && pilot.getCurrentSwing() != null && pilot.getCurrentSwing().hand() == InteractionHand.MAIN_HAND;
        if (swinging && !this.pilotWasSwinging && this.canOperate()) {
            this.triggerAnim("right_hand", "use_item");
        }
        this.pilotWasSwinging = swinging;
    }

    /** Client/dev: the movement animation's playback rate this tick (the gait log compares it with the ground speed). */
    public double getMovementAnimationRate() {
        return this.movementAnimationRate();
    }

    /**
     * The walk and run step at the speed the suit really moves along its legs, so a planted foot stays planted: strafing,
     * diagonals, slowing down, slopes, water, walls and the mind's own speeds all change the ground speed, and a fixed
     * rate slid the feet (runAutopilotGait measured 0.4-1.4 blocks/s of slide, 3.15 when backing up with the walk
     * playing forwards: a moonwalk). Backing up plays walk_back at the backward speed.
     */
    private double movementAnimationRate() {
        if (this.isDeadOrDying() || !this.isDriven() || !this.canOperate() || this.jumped || this.jumpAnimTicks > 0
                || this.airTicks >= FALL_POSE_AFTER_TICKS || this.landTicks > 0) {
            return 1.0;
        }
        if (!this.isWalking()) {
            double water = this.isInWater() ? UNDERWATER_SPEED : this.isInLava() ? LAVA_SPEED : 1.0;
            return (this.realigning ? 0.6 : 1.0) * water; // turning on the spot: a slower step cycle
        }
        double authored = this.isRunning() ? RUN_ANIM_AUTHORED : WALK_ANIM_AUTHORED;
        return Mth.clamp(Math.abs(this.visualForward) / authored, MIN_STEP_RATE, MAX_STEP_RATE);
    }

    /** Client/dev: the ground speed along the legs (blocks/s, negative = backwards) the gait the suit picks this tick
     *  steps at: a planted foot slides by the difference to the real speed (GaitScene). GeckoLib's controller still
     *  names the previous animation during the 5-tick blend, so the log asks this instead. */
    public double getSteppedSpeed() {
        if (!this.isDriven() || !this.canOperate() || !(this.isWalking() || this.realigning) || this.jumped || this.jumpAnimTicks > 0
                || this.airTicks >= FALL_POSE_AFTER_TICKS) {
            return 0;
        }
        double authored = this.isRunning() ? RUN_ANIM_AUTHORED : this.isBackingUp() ? -WALK_ANIM_AUTHORED : WALK_ANIM_AUTHORED;
        return authored * this.movementAnimationRate();
    }

    /** Client: moving backwards along the legs (the legs keep facing the view when the pilot backs up). */
    private boolean isBackingUp() {
        return this.visualForward < BACKING_UP_SPEED;
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
