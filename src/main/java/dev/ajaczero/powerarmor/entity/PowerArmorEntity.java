package dev.ajaczero.powerarmor.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import dev.ajaczero.powerarmor.PilotHands;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.SuitSeal;
import dev.ajaczero.powerarmor.entity.ai.SuitGoals;
import dev.ajaczero.powerarmor.item.HelmetStyle;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.item.SuitHelmetItem;
import dev.ajaczero.powerarmor.item.SuitLoadout;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The power armor suit. It is a living entity (it has health and armor and can die) that a player
 * can ride. While ridden, the player steers it like a horse, and the player's model is hidden.
 *
 * Minecraft runs every entity twice: on the server (the real game state) and on each client
 * (a copy used for rendering). {@code level().isClientSide()} tells you which one you are in.
 *
 * This class is the suit as Minecraft sees it: the vanilla hooks (riding, movement, damage, saving), the table of
 * synced data (everything the server sends to clients about a suit), the slots, and the tick that runs its parts in
 * order. The rules of each part live in its own class, one plain object per suit:
 * <ul>
 *   <li>{@link SuitDriving} ({@code driving()}): how it moves: keys, speeds, ledges, water, the jump, the legs' turn;</li>
 *   <li>{@link SuitPower} ({@code power()}): the engine, fuel, flooding, free power, the lightning knock-out;</li>
 *   <li>{@link SuitSocket} ({@code socket()}): the tool in the arm socket as the suit works it (trigger, cooldown);</li>
 *   <li>{@link SuitBrain} ({@code brain()}): the machine mind's state: owner, home, aim, jobs, rogues;</li>
 *   <li>client only: {@link SocketVisuals} (how the tool is drawn: raise, recoil, the tool's own ToolEffects),
 *       {@link SuitAnimator} (speed, jumps, steps, which animation plays) and {@link SuitLights} (fire, visor, sparks).</li>
 * </ul>
 * What a tool does lives in its item (item/PowerTool); what the mind decides, in entity/ai.
 */
public class PowerArmorEntity extends Mob implements GeoEntity, PlayerRideableJumping {
    public static final int MAX_HEALTH = 100;

    /** Synced to every client: bumped by the server each time the pilot releases jump, so other
     *  players see the jump animation start at the same moment as the pilot does. */
    static final EntityDataAccessor<Integer> JUMP_COUNTER =
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
    public static final TagKey<Item> AMMO = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_ammo"));
    // Drain per tick (idle / walking / sprinting), energy per jump and the low-fuel warning level are balance
    // knobs in PowerArmorConfig. Walking = 1 per tick, so SuitFuel values read as ticks of walking.
    /** Length of the startup animation (1.4 s): the suit can't move until it has stood up. */
    private static final int STARTUP_TICKS = 28;

    /** True while the engine has energy. Movement, jumping and (later) tools need it. */
    static final EntityDataAccessor<Boolean> POWERED =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Energy left in the item that is burning now, and what that item started with (for the gauge). */
    static final EntityDataAccessor<Integer> BURN_LEFT =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> BURN_TOTAL =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** Entity id of whatever the EM projector holds (-1: nothing), so every client can draw the beam. */
    static final EntityDataAccessor<Integer> BEAM_TARGET =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** True while a continuous socket tool runs (drill spinning, chainsaw cutting), for its animation. */
    static final EntityDataAccessor<Boolean> TOOL_RUNNING =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** True while the pilot is using the socket tool (button held, tool installed, power): the tool arm is raised. */
    static final EntityDataAccessor<Boolean> TOOL_ACTIVE =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** The socket tool's spin (chaingun barrels, 0..1) and heat (0..1), and a counter bumped on every shot: clients
     *  turn them into the barrel spin, heat glow, motor whine, recoil and muzzle bursts. */
    static final EntityDataAccessor<Float> TOOL_SPIN =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> TOOL_HEAT =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> TOOL_SHOTS =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** Where the rail cannon's last beam ended (world position): with the shot counter, every client draws the beam. */
    static final EntityDataAccessor<Vector3fc> RAIL_END =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.VECTOR3);
    /** Who the machine mind works for (SuitMind ordinal): the visor shows it. */
    private static final EntityDataAccessor<Byte> MIND =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** Iron nuggets in the hidden ammo store (synced: the menu and the pilot's HUD show it). */
    private static final EntityDataAccessor<Integer> NUGGETS =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.INT);
    /** Held in another suit's EM field (synced: the pilot's client moves a piloted suit, so it must know). */
    static final EntityDataAccessor<Boolean> FIELD_HELD =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** True while a lightning strike has knocked the suit out (it restarts by itself): the slump starts with sparks. */
    static final EntityDataAccessor<Boolean> STUNNED =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Copies of the two slots, so clients can show the fuel left and (Phase 2) draw the socket tool. */
    private static final EntityDataAccessor<ItemStack> FUEL_ITEM =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Energy one item in the fuel slot gives the installed engine. Worked out on the server (the firebox's burn times
     *  are furnace data that only the server has, see SuitFuel), so the clients' gauge can count the slot. */
    static final EntityDataAccessor<Integer> FUEL_ENERGY =
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
    static final EntityDataAccessor<Boolean> ENGINE_UNDER_WATER =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** The engine runs without burning anything right now (a wet tidal engine, a star reactor): the gauge shows it. */
    static final EntityDataAccessor<Boolean> FREE_POWER =
            SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);

    /** The engine and its energy (fuel, flooding, free power, the lightning knock-out): see SuitPower. */
    private final SuitPower power = new SuitPower(this);
    /** How it moves: the pilot's keys, speeds, ledges, water, the jump, the legs' turning: see SuitDriving. */
    private final SuitDriving driving = new SuitDriving(this);
    /** The socket tool as the suit works it (trigger, cooldown, synced spin/shots...): see SuitSocket. */
    private final SuitSocket socket = new SuitSocket(this);
    /** Client: how this client draws the socket tool (raise, recoil, the tool's ToolEffects): see SocketVisuals. */
    private final SocketVisuals socketVisuals = new SocketVisuals(this);
    /** Client: motion bookkeeping and the animation choice (gaits, jumps, landings, step easing): see SuitAnimator. */
    private final SuitAnimator animator = new SuitAnimator(this);
    /** Client: the fire, the visor, the engines' glass, lightning sparks: see SuitLights. */
    private final SuitLights lights = new SuitLights(this);
    /** Server: the machine mind's state (owner, home, aim, which job its tool gives it, a rogue's lifetime): see SuitBrain. */
    private final SuitBrain brain = new SuitBrain(this);
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
    /** Both sides: power transitions seen, for animations, sounds and smoke. */
    private boolean seenPower;
    private boolean lastPowered;
    /** The suit was already off when we first saw it: hold the slumped pose instead of playing the slump. */
    private boolean wasOffWhenLoaded;
    private int startupTicks;
    /** Server: when the seal last paid for keeping fire/frost out (SuitSeal: once per half second, like hit immunity). */
    public int lastSealTick = -100;

    /** Ticks to stay around after dying so the death animation (2.68 s) can finish. */
    private static final int DEATH_TICKS = 60;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

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
        builder.define(RAIL_END, new Vector3f());
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

    /** The goals every machine mind has (see SuitGoals). Vanilla calls this once, from Mob's constructor; the socket
     *  tool's own goals come and go with the tool (SuitBrain.updateToolGoals). */
    @Override
    protected void registerGoals() {
        SuitGoals.register(this, this.goalSelector, this.targetSelector);
    }

    /** Base stats. The single source of truth: also re-applied to suits loaded from old saves. */
    private static final Map<Holder<Attribute>, Double> BASE_STATS = Map.of(
            Attributes.MAX_HEALTH, (double) MAX_HEALTH,
            Attributes.ARMOR, 12.0,
            Attributes.ARMOR_TOUGHNESS, 4.0,
            Attributes.KNOCKBACK_RESISTANCE, 1.0,
            Attributes.MOVEMENT_SPEED, SuitDriving.WALK_SPEED,
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
            this.inventory.setItem(ENGINE_SLOT, new ItemStack(ModItems.FIREBOX.get()));
        }
        this.power.load(tag); // burn, tidal state (loading isn't an engine swap: the half-burnt item stays)
        this.setMind(SuitMind.byName(tag.getStringOr("Mind", ""))); // suits from before the AI: "" -> friendly
        this.brain.load(tag); // owner, home, a rogue's age
        this.setStoredNuggets(tag.getIntOr("Nuggets", 0));
        this.setPaint(tag.read("Paint", DyeColor.CODEC).orElse(null)); // suits from before paint: bare
        this.syncSlots();
        this.entityData.set(POWERED, this.power.shouldRun(null)); // a pilot (if any) boards after loading: next tick knows
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        // Slot numbers are saved with each item, so an empty fuel slot doesn't shift the socket tool into it.
        ContainerHelper.saveAllItems(tag.child("Inventory"), this.inventory.getItems());
        tag.putBoolean("HasEngineSlot", true); // an empty engine slot is then really empty (taken out), not an old save
        this.power.save(tag);
        tag.putString("Mind", this.getMind().name());
        tag.putInt("Nuggets", this.getStoredNuggets());
        this.brain.save(tag);
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

    /** Paths may drop this far. The drilling mind never steps down at all ("never where it would fall"): its hall is
     *  flat, and a step down there is a hole in the floor. */
    @Override
    public int getMaxFallDistance() {
        return this.brain.digsHall() && this.getControllingPassenger() == null ? 0 : super.getMaxFallDistance();
    }

    /** Vanilla's look control resets the pitch and eases the head back toward the body every tick unless something
     *  asks it to look somewhere. This runs after the goals (which may have asked for something else) and before the
     *  look control, so an aim set by the mind wins; the tool fires later in the same tick (tickTool). */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.brain.applyAim();
    }

    /** The head (and so the torso, which follows it) may turn as far from the legs as the model's torso twists. */
    @Override
    public int getMaxHeadYRot() {
        return (int) SuitDriving.MAX_TWIST;
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

    /** The first pilot to climb in owns the suit (and every later pilot takes it over: the mind works for the last one). */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide() && passenger instanceof Player player) {
            this.brain.pilotBoarded(player);
            this.socket.setAiTrigger(false);
            this.getNavigation().stop();
        }
    }

    /** The pilot got out: the mind takes over and makes this block its home. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide() && passenger instanceof Player player && this.isAlive()) {
            this.brain.pilotLeft(player);
        }
    }

    /** Vanilla switches the goals' controls on and off every few ticks (for mobs ridden by mobs); we hand them to the
     *  mind only while it drives, so a pilot climbing in stops every goal at once. */
    @Override
    protected void updateControlFlags() {
        boolean ai = this.isAiDriven();
        this.goalSelector.setControlFlag(Goal.Flag.MOVE, ai);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, ai);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, ai);
        this.targetSelector.setControlFlag(Goal.Flag.TARGET, ai);
    }

    // =========================================================================================
    // Power and inventory (the engine's rules: SuitPower)
    // =========================================================================================

    /** How the suit moves (keys, speeds, water, jump, legs). */
    public SuitDriving driving() {
        return this.driving;
    }

    /** The suit's engine and energy. */
    public SuitPower power() {
        return this.power;
    }

    /** The left-arm socket and the tool in it. */
    public SuitSocket socket() {
        return this.socket;
    }

    /** Client: how the socket tool is drawn on this client. */
    public SocketVisuals socketVisuals() {
        return this.socketVisuals;
    }

    /** Client: how the suit moves and animates on this client. */
    public SuitAnimator animator() {
        return this.animator;
    }

    /** Client: the suit's own lights. */
    public SuitLights lights() {
        return this.lights;
    }

    /** The machine mind's state and jobs. */
    public SuitBrain brain() {
        return this.brain;
    }

    /** Server: the mind's goals (SuitBrain adds and removes the tool's own; dev logs and tests read them). */
    public GoalSelector goals() {
        return this.goalSelector;
    }

    // ---- Engines (design sec. 4b) ------------------------------------------------------------------------

    /** Both sides: the installed engine, or null (the engine slot is empty: no power). */
    public @Nullable SuitEngine getEngine() {
        return SuitEngine.byId(this.entityData.get(ENGINE));
    }

    /**
     * Server: copies the slots into the synced data clients read (the HUD's counts, the drawn tool, helmet and engine).
     * The container calls it on every setItem / setChanged; {@link #checkSlotCopies} catches the rest.
     */
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

    /**
     * Server, every tick: a stack changed in place (shrink(), grow()) doesn't tell its container, so the synced copy would
     * stay stale until something else touched the slots (the HUD would count ammo that's gone). Every such change in the mod
     * also calls setChanged(); this backstop makes a forgotten one cost a tick, not a wrong HUD. Compares only, copies on a
     * difference.
     */
    private void checkSlotCopies() {
        if (!ItemStack.matches(this.entityData.get(FUEL_ITEM), this.inventory.getItem(FUEL_SLOT))
                || !ItemStack.matches(this.entityData.get(SOCKET_ITEM), this.inventory.getItem(SOCKET_SLOT))
                || !ItemStack.matches(this.entityData.get(AMMO_ITEM), this.inventory.getItem(AMMO_SLOT))) {
            this.syncSlots();
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
    public boolean takeAmmo(Predicate<ItemStack> fits) {
        ItemStack ammo = this.inventory.getItem(AMMO_SLOT);
        if (ammo.isEmpty() || !fits.test(ammo)) {
            return false;
        }
        ammo.shrink(1);
        this.inventory.setChanged(); // shrinking a stack in place doesn't tell the container: sync the HUD's count
        return true;
    }

    // ---- Socket tool ---------------------------------------------------------------------------

    /** Unloaded, packed or destroyed with something in the beam: let it go, or it would float forever. */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) {
            this.socket.onRemoved();
        }
        super.remove(reason);
    }

    /** Server: the real slots (used by the menu and tests). */
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    /** What a packed suit item keeps: its slots, the half-burnt item and the ammo. */
    public SuitLoadout getLoadout() {
        return new SuitLoadout(this.inventory.getItem(FUEL_SLOT).copy(), this.inventory.getItem(SOCKET_SLOT).copy(),
                Mth.ceil(this.power.getBurnLeftExact()), this.power.getBurnTotal(), this.getStoredNuggets(), this.inventory.getItem(HELMET_SLOT).copy(),
                Optional.of(this.inventory.getItem(ENGINE_SLOT).copy()), this.inventory.getItem(AMMO_SLOT).copy());
    }

    public void applyLoadout(SuitLoadout loadout) {
        this.inventory.setItem(FUEL_SLOT, loadout.fuel().copy());
        this.inventory.setItem(SOCKET_SLOT, loadout.socket().copy());
        this.inventory.setItem(HELMET_SLOT, loadout.helmet().copy());
        this.inventory.setItem(AMMO_SLOT, loadout.ammo().copy());
        // Items packed (or crafted) before the engines carry none: they had their firebox built in.
        this.inventory.setItem(ENGINE_SLOT, loadout.engine().map(ItemStack::copy)
                .orElseGet(() -> new ItemStack(ModItems.FIREBOX.get())));
        this.power.slotsRestored(); // not a swap: keep the half-burnt item
        this.power.setBurn(loadout.burnLeft(), loadout.burnTotal());
        this.setStoredNuggets(loadout.nuggets());
        this.entityData.set(POWERED, this.power.shouldRun(null));
    }

    /** Server: turn this parked suit back into an item (with its fuel and tool) for the player. Refused while its
     *  firebox is flooded (design sec. 4b): drain the water or swap the engine first. Returns whether it packed. */
    public boolean packUp(Player player) {
        if (this.power.isFlooded()) {
            player.sendOverlayMessage(Component.translatable("message.powerarmor.flooded_pack"));
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

    /** Both sides: ticks left of the stand-up after power returns (the suit can't move yet). */
    int startupTicks() {
        return this.startupTicks;
    }

    /** Both sides: the suit was already off when this side first saw it (hold the slumped pose, don't play the slump). */
    boolean wasOffWhenLoaded() {
        return this.wasOffWhenLoaded;
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
            this.driving.cancelJump();
        }
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
        DyeColor dye = held.get(DataComponents.DYE);
        if (dye != null && !player.isSecondaryUseActive()) {
            return this.paintWith(player, held, dye); // like dyeing a sheep: a dye in hand paints, it doesn't climb in
        }
        if (held.is(Items.WET_SPONGE) && !player.isSecondaryUseActive()) {
            return this.washPaint(player);
        }
        if (held.is(Items.IRON_NUGGET) && !player.isSecondaryUseActive()) {
            return this.loadNuggets(player, held); // feeding it ammo, like feeding a horse: no climbing in
        }
        if (held.is(ModItems.SUIT_HELMET.get()) && !player.isSecondaryUseActive() && !this.hasHelmet()) {
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
        this.level().playSound(player, this, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 0.9F);
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
        this.level().playSound(player, this, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (this.level() instanceof ServerLevel server) {
            this.setPaint(null);
            // a splash on the plates (server particles reach every client near the suit)
            server.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 1.6, this.getZ(),
                    40, 0.5, 0.6, 0.5, 0.1);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- The hidden ammo store (no slot: one tool uses it) ------------------------------------------------

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

    /** Right-click with iron nuggets: the whole stack goes into the store, up to its size (config). */
    private InteractionResult loadNuggets(Player player, ItemStack held) {
        int room = PowerArmorConfig.NUGGET_STORE_MAX.get() - this.getStoredNuggets();
        if (room <= 0) {
            if (!this.level().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.powerarmor.nuggets_full",
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
            player.sendOverlayMessage(Component.translatable("message.powerarmor.nuggets_loaded",
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
    // Driving: vanilla calls these while a player controls the entity (same hooks horses use); the rules are SuitDriving's
    // =========================================================================================

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return this.driving.riddenInput(player);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return this.driving.riddenSpeed(player);
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
        this.driving.launchPendingJump();
    }

    /** Vanilla turns the body every tick; a driven suit turns its legs by its own rule (SuitDriving.turnLegs). */
    @Override
    protected void tickHeadTurn(float movementYaw) {
        if (!this.driving.turnLegs()) {
            super.tickHeadTurn(movementYaw);
        }
    }

    /** Movement for the side that moves the suit: SuitDriving.travel (ledges, water, the mind's input, EM fields). */
    @Override
    public void travel(Vec3 travelVector) {
        this.driving.travel(travelVector);
    }

    /** Vanilla's own travel, for SuitDriving (a part can't call super). */
    void vanillaTravel(Vec3 travelVector) {
        super.travel(travelVector);
    }

    /** The suit itself is a sealed machine and never runs out of air (its pilot can, though). */
    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    /** Too heavy for currents and bubble columns to push around (NeoForge's per-fluid version of isPushedByFluid). */
    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    /** Lets the pilot sprint while inside (vanilla blocks sprinting on most vehicles). */
    @Override
    public boolean canSprint() {
        return true;
    }

    // Jumping uses the horse jump bar: hold space to charge (the suit crouches), release to jump (SuitDriving).
    // Called on the pilot's client when space is released.
    @Override
    public void onPlayerJump(int jumpPower) {
        this.driving.onPlayerJump(jumpPower);
    }

    @Override
    public boolean canJump() {
        return this.isAlive() && this.canOperate();
    }

    @Override
    public int getJumpCooldown() {
        return this.driving.jumpCooldown();
    }

    /** Called on the server when the pilot releases jump. */
    @Override
    public void handleStartJump(int jumpPower) {
        this.driving.jumpReleased();
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
        if (SuitSeal.absorbs(this, source, amount)) {
            return false; // fire, frost & co. kept out by the seal, paid in fuel
        }
        boolean damaged = super.hurtServer(level, source, amount);
        if (damaged && this.isAlive() && this.reactsToHits()) {
            this.triggerAnim("action", "hit"); // play the flinch on every client
        }
        return damaged;
    }

    /** Only a running suit flinches when hit. Switched off (empty, knocked out, still standing up) it can't react: and the
     *  flinch (on the "action" controller, which overrides the body's controller) would cut into the electrified
     *  convulsion, the slump or the startup, e.g. on every tick of fire after a lightning strike. */
    public boolean reactsToHits() {
        return this.canOperate();
    }

    /** Vanilla calls this on landing, with how far we fell. The suit takes no fall damage, but a big drop stomps:
     *  everything around the landing spot gets hurt, friend or foe. Runs on the server (it moves the suit too, from the
     *  pilot's position packets) and on the pilot's client, which only plays the sound. */
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
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        boolean wasPowered = this.isPowered();
        if (this.isRogue() && this.isAlive()) {
            // The rogue mind reboots friendly (design sec. 8): nobody's (the first pilot claims it), no home, and only the
            // fuel left in its firebox (one coal's worth) to restart with after the knock-out below.
            this.setMind(SuitMind.FRIENDLY);
            this.brain.forget();
            this.setTarget(null);
            this.power.setBurn(1600, 1600);
            for (Player near : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32))) {
                near.sendOverlayMessage(Component.translatable("message.powerarmor.rogue_rebooted"));
            }
        }
        // Knock it out first, so the strike's damage below finds it already off: no flinch over the convulsion.
        int ticks = PowerArmorConfig.LIGHTNING_STUN_TICKS.get();
        if (this.isAlive() && this.power.knockOut(ticks)) {
            this.stopTriggeredAnim("action", "hit"); // a flinch from just before the strike mustn't play over it either
        }
        if (!wasPowered) {
            super.thunderHit(level, lightning); // no power, no resistance: it catches fire like anything else
        } else {
            // Running: the strike doesn't set the steel alight (vanilla would), it fries the electrics. Once knocked
            // out, the fire the bolt usually leaves at the suit's feet will catch it.
            this.hurtServer(level, this.damageSources().lightningBolt(), lightning.getDamage());
        }
    }

    /** Death messages and the like say "Rogue Power Armor" for a rogue (synced mind: right on both sides). */
    @Override
    protected Component getTypeName() {
        return this.isRogue() ? Component.translatable("entity.powerarmor.power_armor.rogue") : super.getTypeName();
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
    // The tick: the parts in order (server: rogue gear, engine, tool, mind; client: lights, smoke, tool looks, motion)
    // =========================================================================================

    @Override
    public void tick() {
        this.putOutFire(); // before baseTick, which decides whether clients see flames (see putOutFire)
        super.tick();
        if (!this.level().isClientSide() && this.isAlive()) {
            this.brain.equipRogue();
            this.checkSlotCopies();
            this.power.tick();
            this.socket.tick();
            this.brain.tick();
            this.brain.updateToolGoals(); // the socket tool's own goals (PowerTool.mindGoals), after the goals ran this tick
            this.driving.tick(); // an EM field that stopped holding us lets go
            this.putOutFire();
            if (this.getControllingPassenger() instanceof Player pilot) {
                PilotHands.refreshBonuses(pilot);
                if (this.canOperate()) {
                    SuitSmash.tick(this, pilot);
                }
            }
        }
        this.tickPowerTransitions();
        if (this.level().isClientSide()) {
            this.putOutFire(); // the pilot's client moved us (and maybe into fire) during super.tick()
            this.lights.tick();
            ExhaustSmoke.tick(this);
            this.socketVisuals.tick(); // the tool arm's raise, recoil, and the tool's own looks (ToolEffects)
            this.animator.tick();      // speed, steps, jumps and landings, the aim blend: what the animations follow
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

    /** The pilot rides with the eased step offset, so the camera glides over steps. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return super.getPassengerAttachmentPoint(passenger, dimensions, partialTick).add(0, this.animator.stepNow(), 0);
    }

    /** GeckoLib asks once which animation controllers the suit has: see SuitAnimator. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        this.animator.registerControllers(controllers);
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
