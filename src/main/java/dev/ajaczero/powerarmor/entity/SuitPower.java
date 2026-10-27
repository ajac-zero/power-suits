package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.SuitSeal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's engine and its energy (design sec. 4b): what burns, how fast, when the suit has power, flooding, free power
 * (a wet tidal engine, a star reactor) and the lightning knock-out. Works like a furnace: one fuel item burns at a time
 * ({@code burnLeft} of {@code burnTotal}), the next one is lit when it runs out.
 *
 * One of the suit's parts (see PowerArmorEntity): a plain object the suit owns ({@code suit.power()}), so the energy rules
 * live in one file. The server runs it ({@link #tick}); both sides can ask the questions answered from synced data
 * (flooded, free power, burn left...). The synced values themselves are declared in PowerArmorEntity, the one table of
 * everything the suit sends to clients.
 */
public final class SuitPower {
    /** Height above the suit's feet and distance behind its middle of the backpack's centre (backpack box y 31.6-38.6 px,
     *  z 2-7 px): where water floods the engine. */
    private static final double ENGINE_UP = 2.2, ENGINE_BACK = 0.53;

    private final PowerArmorEntity suit;
    /** Server: energy left in the burning item (fractional, idle drains 0.25 per tick), and what it started with. Only
     *  {@link #setBurnState} writes them (it also syncs them). */
    private float burnLeft;
    private int burnTotal;
    /** Server: the engine seen last tick, to notice a swap (the half-burnt item goes with the old engine). */
    @Nullable
    private SuitEngine lastEngine;
    private boolean engineSeen;
    /** Server: ticks since a tidal engine was last wet (it runs free for a short grace after leaving the water). */
    private int dryTicks = Integer.MAX_VALUE / 2;
    /** Server: ticks left of a lightning knock-out (0 = none). */
    private int stunTicks;

    SuitPower(PowerArmorEntity suit) {
        this.suit = suit;
    }

    private SimpleContainer slots() {
        return this.suit.getInventory();
    }

    private ItemStack fuelSlot() {
        return this.slots().getItem(PowerArmorEntity.FUEL_SLOT);
    }

    // ---- Server: one tick of the engine ---------------------------------------------------------------------------

    /** Server: one tick of the engine. A suit with fuel runs while it's piloted, or while it wears its helmet (then its
     *  machine mind drives it): to park a suit, take its helmet off (or the fuel out). */
    void tick() {
        Player pilot = this.suit.getControllingPassenger() instanceof Player p ? p : null;
        if (this.stunTicks > 0 && --this.stunTicks == 0) {
            this.suit.getEntityData().set(PowerArmorEntity.STUNNED, false); // back on: setPowered below plays the startup
        }
        SuitEngine engine = this.suit.getEngine();
        if (this.engineSeen && engine != this.lastEngine) {
            // Engine swapped: the half-burnt item went out with the old one (design sec. 4b).
            this.setBurnState(0, 0);
        }
        if (engine != this.lastEngine || !this.engineSeen) {
            // The mind's paths: a firebox would drown in deep water, so its suit keeps out of water altogether (vanilla's
            // default cost for water is 8: walkable, avoided when there's a dry way round).
            this.suit.setPathfindingMalus(PathType.WATER, engine == SuitEngine.FIREBOX ? -1F : 8F);
        }
        this.lastEngine = engine;
        this.engineSeen = true;
        boolean underWater = this.engineCoveredByWater();
        this.suit.getEntityData().set(PowerArmorEntity.ENGINE_UNDER_WATER, underWater);
        this.dryTicks = underWater || this.isEngineInRain() ? 0 : Math.min(this.dryTicks + 1, Integer.MAX_VALUE / 2);
        if (engine == SuitEngine.FIREBOX && underWater && !this.suit.isRogue()) {
            this.floodFirebox();
        }
        boolean free = this.runsFree();
        this.suit.getEntityData().set(PowerArmorEntity.FREE_POWER, free);
        if (this.stunTicks == 0 && !this.suit.isRogue() && !free && this.shouldRun(pilot)) { // a knocked-out engine burns nothing
            if (this.burnLeft <= 0) {
                this.igniteNextItem();
            }
            if (this.burnLeft > 0) {
                float drain = pilot != null ? drainPerTick(pilot) : this.aiDrainPerTick();
                if (pilot != null && SuitSeal.feedsAir(pilot)) {
                    drain += PowerArmorConfig.AIR_ENERGY_PER_TICK.get().floatValue(); // the pilot breathes the suit's air
                }
                this.setBurnState(Math.max(0, this.burnLeft - drain), this.burnTotal);
                if (this.burnLeft <= 0) {
                    this.igniteNextItem(); // hand over to the next item without a gap, like a furnace
                }
            }
        }
        this.setPowered(this.shouldRun(pilot) && this.stunTicks == 0);
    }

    /** Load on the engine, from the pilot's keys (pilotInput). */
    private static float drainPerTick(Player pilot) {
        boolean moving = PowerArmorEntity.pilotInput(pilot).lengthSqr() > 1e-4;
        if (!moving) {
            return PowerArmorConfig.DRAIN_IDLE.get().floatValue();
        }
        return (pilot.isSprinting() ? PowerArmorConfig.DRAIN_SPRINT : PowerArmorConfig.DRAIN_WALK).get().floatValue();
    }

    /** The same load for the machine mind, judged by how fast it really moves. */
    private float aiDrainPerTick() {
        // getDeltaMovement() already holds the ground drag for the next tick (block friction 0.6 x 0.91 on normal
        // ground): it reads ~0.55 of the distance really moved, so undo that to compare with blocks/s.
        double blocksPerSecond = this.suit.getDeltaMovement().horizontalDistance() * 20 / 0.546;
        if (blocksPerSecond < 0.7) {
            return PowerArmorConfig.DRAIN_IDLE.get().floatValue();
        }
        return (blocksPerSecond > PowerArmorEntity.RUN_THRESHOLD ? PowerArmorConfig.DRAIN_SPRINT : PowerArmorConfig.DRAIN_WALK)
                .get().floatValue();
    }

    /** Takes one item from the fuel slot and starts burning it. Lava and water leave their bucket behind, water bottles
     *  their glass bottle. */
    private void igniteNextItem() {
        ItemStack stack = this.fuelSlot();
        int energy = SuitFuel.energyOf(this.suit.getEngine(), stack, this.suit.level());
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
            this.slots().setItem(PowerArmorEntity.FUEL_SLOT, remainder);
        } else {
            this.slots().setChanged();
            if (!remainder.isEmpty() && this.suit.level() instanceof ServerLevel level) {
                this.suit.spawnAtLocation(level, remainder);
            }
        }
        this.setBurnState(this.burnLeft + energy, energy);
    }

    /** Server: turns the synced "powered" flag on or off, with the sounds and (for the pilot) why it went off. */
    void setPowered(boolean powered) {
        if (powered == this.suit.getEntityData().get(PowerArmorEntity.POWERED)) {
            return;
        }
        this.suit.getEntityData().set(PowerArmorEntity.POWERED, powered);
        if (powered) {
            this.suit.playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
            this.suit.playSound(SoundEvents.PISTON_EXTEND, 0.8F, 0.5F);
        } else {
            this.suit.playSound(SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.6F);
            this.suit.playSound(SoundEvents.IRON_GOLEM_DAMAGE, 0.6F, 0.5F);
            if (this.suit.getControllingPassenger() instanceof Player pilot) {
                SuitEngine engine = this.suit.getEngine();
                pilot.sendOverlayMessage(Component.translatable(this.stunTicks > 0 ? "message.powerarmor.lightning"
                        : engine == null ? "message.powerarmor.no_engine"
                        : this.isFlooded() ? "message.powerarmor.flooded"
                        : engine == SuitEngine.TIDAL ? "message.powerarmor.tidal_dry"
                        : "message.powerarmor.out_of_fuel"));
            }
        }
    }

    /** Server: lightning tripped the electrics: off for {@code ticks}, then it restarts by itself (only a suit with
     *  something to run on: there's nothing to knock out otherwise). Returns whether it was knocked out. */
    boolean knockOut(int ticks) {
        if (ticks <= 0 || !this.hasEnergy()) {
            return false;
        }
        this.stunTicks = ticks;
        this.suit.getEntityData().set(PowerArmorEntity.STUNNED, true);
        this.setPowered(false);
        return true;
    }

    // ---- The engine and water ---------------------------------------------------------------------------------------

    /** Both sides: water covers the engine (the backpack's middle is under the surface). */
    public boolean isEngineUnderWater() {
        return this.suit.getEntityData().get(PowerArmorEntity.ENGINE_UNDER_WATER);
    }

    /** Both sides: a firebox under water: it can't burn, and the suit can't be packed up. */
    public boolean isFlooded() {
        return this.suit.getEngine() == SuitEngine.FIREBOX && this.isEngineUnderWater() && !this.suit.isRogue();
    }

    /** Both sides: the engine runs without burning anything right now (wet tidal engine, star reactor). */
    public boolean hasFreePower() {
        return this.suit.getEntityData().get(PowerArmorEntity.FREE_POWER);
    }

    /** Server: where the engine sits in the world: the backpack's middle, behind the body. */
    private Vec3 enginePosition() {
        float yaw = this.suit.yBodyRot * Mth.DEG_TO_RAD;
        // Minecraft's forward for a yaw is (-sin, cos); the backpack is the other way.
        return this.suit.position().add(Mth.sin(yaw) * ENGINE_BACK, ENGINE_UP, -Mth.cos(yaw) * ENGINE_BACK);
    }

    /** Server: is that point below a water surface? (A fluid's height inside its block: a full source is 8/9 high.) */
    private boolean engineCoveredByWater() {
        Vec3 p = this.enginePosition();
        BlockPos pos = BlockPos.containing(p);
        var fluid = this.suit.level().getFluidState(pos);
        return fluid.is(FluidTags.WATER) && p.y < pos.getY() + fluid.getHeight(this.suit.level(), pos);
    }

    /** Server: rain reaches the suit's top (vanilla's own rain test for entities, which is private). */
    private boolean isEngineInRain() {
        return this.suit.level().isRainingAt(BlockPos.containing(this.suit.getX(), this.suit.getBoundingBox().maxY, this.suit.getZ()));
    }

    /** Server: does the engine run without burning anything right now? A tidal engine while wet (and for a short grace
     *  after leaving the water), a reactor with its Nether Star. */
    private boolean runsFree() {
        SuitEngine engine = this.suit.getEngine();
        if (engine == SuitEngine.TIDAL) {
            return this.dryTicks <= PowerArmorConfig.TIDAL_DRY_GRACE.get();
        }
        return SuitFuel.powersForever(engine, this.fuelSlot());
    }

    /** Server: water got into the firebox: the fire dies with a hiss and a puff of steam, and the half-burnt item is
     *  lost. Fuel in the slot can't light while the backpack is under water ({@link #hasEnergy}). */
    private void floodFirebox() {
        if ((this.burnLeft > 0 || this.suit.isPowered()) && this.suit.level() instanceof ServerLevel level) {
            this.suit.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.0F, 0.6F);
            Vec3 p = this.enginePosition();
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.x, p.y, p.z, 30, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.4, p.z, 12, 0.3, 0.3, 0.3, 0.05);
        }
        this.setBurnState(0, 0);
    }

    // ---- Energy -----------------------------------------------------------------------------------------------------

    /** Should the engine run: energy on board and someone to drive, the pilot or the mind (which needs the helmet). */
    boolean shouldRun(@Nullable Player pilot) {
        return this.hasEnergy() && (pilot != null || this.suit.hasHelmet());
    }

    /** Energy available right now: the burning item, or an item waiting in the slot. */
    boolean hasEnergy() {
        // A rogue runs on nothing (design sec. 8: unlimited fuel and ammo, it never sits slumped waiting to be looted).
        if (this.suit.isRogue()) {
            return true;
        }
        SuitEngine engine = this.suit.getEngine();
        if (engine == null || this.isFlooded()) {
            return false; // no engine, or a firebox under water: nothing can burn
        }
        return this.runsFree() || this.burnLeft > 0 || SuitFuel.energyOf(engine, this.fuelSlot(), this.suit.level()) > 0;
    }

    /** Server: is there this much energy on board (burning item + fuel slot)? */
    public boolean hasEnergyFor(int amount) {
        if (this.suit.isRogue() || (this.runsFree() && this.hasEnergy())) {
            return true; // a wet tidal engine or a star reactor pays for everything
        }
        if (this.isFlooded()) {
            return false;
        }
        ItemStack fuel = this.fuelSlot();
        return this.burnLeft + (float) fuel.getCount() * SuitFuel.energyOf(this.suit.getEngine(), fuel, this.suit.level()) >= amount;
    }

    /** Server: take energy for a tool if there's enough (burning item + fuel slot). */
    public boolean tryUseEnergy(int amount) {
        if (!this.hasEnergyFor(amount)) {
            return false;
        }
        this.consume(amount);
        return true;
    }

    /** Server: take energy, lighting more fuel items if the burning one hasn't got enough left. */
    public void consume(int amount) {
        if (this.suit.isRogue() || this.runsFree()) {
            return;
        }
        while (this.burnLeft < amount && SuitFuel.isFuel(this.suit.getEngine(), this.fuelSlot()) && !this.isFlooded()) {
            this.igniteNextItem();
        }
        this.setBurnState(Math.max(0, this.burnLeft - amount), this.burnTotal);
    }

    /** Both sides: energy in the burning item (rounded up, synced). */
    public int getBurnLeft() {
        return this.suit.getEntityData().get(PowerArmorEntity.BURN_LEFT);
    }

    /** Both sides: what the burning item started with (synced, for the gauge). */
    public int getBurnTotal() {
        return this.suit.getEntityData().get(PowerArmorEntity.BURN_TOTAL);
    }

    /** Both sides: all energy on board, burning item + everything in the fuel slot. */
    public int getTotalEnergy() {
        ItemStack fuel = this.suit.getFuelStack();
        return this.getBurnLeft() + fuel.getCount() * this.suit.getEntityData().get(PowerArmorEntity.FUEL_ENERGY);
    }

    public boolean isLowOnFuel() {
        // A wet tidal engine or a reactor isn't burning anything, so it can't run low (a tidal engine on land can).
        return !this.hasFreePower() && this.getTotalEnergy() < PowerArmorConfig.LOW_FUEL.get();
    }

    /** Server/tests: the burning item's exact energy left. */
    public float getBurnLeftExact() {
        return this.burnLeft;
    }

    /** Server/tests: set the burning item's energy directly. */
    public void setBurn(float left, int total) {
        this.setBurnState(left, total);
    }

    /** The one place the burn changes: sets it and copies it into the synced data at once (the gauge reads it on
     *  clients). Before, each change had to call sync() itself, and a test setter that forgot left a stale gauge. */
    private void setBurnState(float left, int total) {
        this.burnLeft = left;
        this.burnTotal = total;
        this.suit.getEntityData().set(PowerArmorEntity.BURN_LEFT, Mth.ceil(left));
        this.suit.getEntityData().set(PowerArmorEntity.BURN_TOTAL, total);
    }

    // ---- Saving, packing ----------------------------------------------------------------------------------------------

    /** The slots were just filled from a save or a packed item: whatever engine is in is the one that was running (not a
     *  swap, so the half-burnt item stays). */
    void slotsRestored() {
        this.lastEngine = SuitEngine.of(this.slots().getItem(PowerArmorEntity.ENGINE_SLOT));
        this.engineSeen = true;
    }

    void load(ValueInput tag) {
        this.dryTicks = tag.getIntOr("TidalDry", Integer.MAX_VALUE / 2);
        this.suit.getEntityData().set(PowerArmorEntity.ENGINE_UNDER_WATER, tag.getBooleanOr("EngineUnderWater", false));
        this.setBurnState(tag.getFloatOr("BurnLeft", 0F), tag.getIntOr("BurnTotal", 0));
        this.slotsRestored();
    }

    void save(ValueOutput tag) {
        tag.putInt("TidalDry", this.dryTicks);
        tag.putBoolean("EngineUnderWater", this.isEngineUnderWater());
        tag.putFloat("BurnLeft", this.burnLeft);
        tag.putInt("BurnTotal", this.burnTotal);
    }
}
